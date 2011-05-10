/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.refactoring.convertToJava;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrClassSubstitutor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.formatter.GrControlStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.refactoring.DefaultGroovyVariableNameValidator;
import org.jetbrains.plugins.groovy.refactoring.GroovyNameSuggestionUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
public class GenerationUtil {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.refactoring.convertToJava.GenerationUtil");

  private GenerationUtil() {
  }

  public static void writeType(StringBuilder builder, PsiType type, PsiElement context) {
    writeType(builder, type, context, new GeneratorClassNameProvider());
  }

  public static void writeType(final StringBuilder builder,
                               @Nullable final PsiType type,
                               final PsiElement context,
                               final ClassNameProvider classNameProvider) {
    if (type instanceof PsiPrimitiveType) {
      builder.append(type.getCanonicalText());
      return;
    }

    if (type == null) {
      builder.append(CommonClassNames.JAVA_LANG_OBJECT);
      return;
    }

    final boolean acceptEllipsis = isLastParameter(context);

    type.accept(new TypeWriter(builder, classNameProvider, acceptEllipsis, context));
  }

  private static boolean isLastParameter(PsiElement context) {
    final PsiElement parent = context.getParent();
    return context instanceof PsiParameter &&
           parent instanceof PsiParameterList &&
           ((PsiParameterList)parent).getParameterIndex((PsiParameter)context) == ((PsiParameterList)parent).getParametersCount() - 1;
  }

  public static void writeTypeParameters(StringBuilder builder,
                                          PsiType[] parameters,
                                          PsiElement context,
                                          ClassNameProvider classNameProvider) {
    if (parameters.length == 0) return;

    builder.append("<");
    for (PsiType parameter : parameters) {
      if (parameter instanceof PsiPrimitiveType) {
        parameter = TypesUtil.boxPrimitiveType(parameter, context.getManager(), context.getResolveScope(), true);
      }
      writeType(builder, parameter, context, classNameProvider);
      builder.append(", ");
    }
    builder.replace(builder.length() - 2, builder.length(), ">");
  }

  static String suggestVarName(GrExpression expr, ExpressionContext expressionContext) {
    final DefaultGroovyVariableNameValidator nameValidator =
      new DefaultGroovyVariableNameValidator(expr, expressionContext.myUsedVarNames, true);
    final String[] varNames = GroovyNameSuggestionUtil.suggestVariableNames(expr, nameValidator);

    LOG.assertTrue(varNames.length > 0);
    expressionContext.myUsedVarNames.add(varNames[0]);
    return varNames[0];
  }

  static String suggestVarName(PsiType type, GroovyPsiElement context, ExpressionContext expressionContext) {
    final DefaultGroovyVariableNameValidator nameValidator =
      new DefaultGroovyVariableNameValidator(context, expressionContext.myUsedVarNames, true);
    final String[] varNames = GroovyNameSuggestionUtil.suggestVariableNameByType(type, nameValidator);

    LOG.assertTrue(varNames.length > 0);
    expressionContext.myUsedVarNames.add(varNames[0]);
    return varNames[0];
  }

  public static String validateName(String name, GroovyPsiElement context, ExpressionContext expressionContext) {
    return new DefaultGroovyVariableNameValidator(context, expressionContext.myUsedVarNames, true).validateName(name, true);
  }

  public static void writeCodeReferenceElement(StringBuilder builder, GrCodeReferenceElement referenceElement) {
    final GroovyResolveResult resolveResult = referenceElement.advancedResolve();
    final PsiElement resolved = resolveResult.getElement();
    if (resolved == null) {
      builder.append(referenceElement.getText());
      return;
    }
    LOG.assertTrue(resolved instanceof PsiClass || resolved instanceof PsiPackage);
    if (resolved instanceof PsiClass) {
      builder.append(((PsiClass)resolved).getQualifiedName());
    }
    else {
      builder.append(((PsiPackage)resolved).getQualifiedName());
    }
    writeTypeParameters(builder, referenceElement.getTypeArguments(), referenceElement, new GeneratorClassNameProvider());
  }

  public static void invokeMethodByName(GrExpression caller,
                                        String methodName,
                                        GrExpression[] exprs,
                                        GrNamedArgument[] namedArgs,
                                        GrClosableBlock[] closureArgs,
                                        ExpressionGenerator expressionGenerator,
                                        GroovyPsiElement psiContext) {
    final GroovyResolveResult call;

    final PsiType type = caller.getType();
    if (type == null) {
      call = GroovyResolveResult.EMPTY_RESULT;
    }
    else {
      final PsiType[] argumentTypes = PsiUtil.getArgumentTypes(namedArgs, exprs, closureArgs, false, null);
      final GroovyResolveResult[] candidates = ResolveUtil.getMethodCandidates(type, methodName, psiContext, argumentTypes);
      call = PsiImplUtil.extractUniqueResult(candidates);
    }
    invokeMethodByResolveResult(caller, call, methodName, exprs, namedArgs, closureArgs, expressionGenerator, psiContext);
  }

  public static void invokeMethodByResolveResult(GrExpression caller,
                                                 GroovyResolveResult resolveResult,
                                                 String methodName,
                                                 GrExpression[] exprs,
                                                 GrNamedArgument[] namedArgs,
                                                 GrClosableBlock[] closureArgs,
                                                 ExpressionGenerator expressionGenerator,
                                                 GroovyPsiElement psiContext) {
    final PsiElement resolved = resolveResult.getElement();
    if (resolved instanceof PsiMethod) {
      final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
      expressionGenerator.invokeMethodOn(((PsiMethod)resolved), caller, exprs, namedArgs, closureArgs, substitutor, psiContext);
      return;
    }
    //other case
    final StringBuilder builder = expressionGenerator.getBuilder();
    final ExpressionContext expressionContext = expressionGenerator.getContext();

    caller.accept(expressionGenerator);
    builder.append(".").append(methodName);
    final ArgumentListGenerator argumentListGenerator = new ArgumentListGenerator(builder, expressionContext);
    argumentListGenerator.generate(null, exprs, namedArgs, closureArgs, psiContext);
  }

  static void writeStatement(final StringBuilder codeBlockBuilder,
                             StringBuilder statementBuilder,
                             @Nullable GrStatement statement,
                             @Nullable ExpressionContext context) {
    final PsiElement parent = statement == null ? null : statement.getParent();

    final boolean addParentheses;
    if (statement == null) {
      addParentheses = context != null && context.shouldInsertCurlyBrackets();
    }
    else {
      addParentheses =
        context != null && (context.shouldInsertCurlyBrackets() || context.myStatements.size() > 0) && parent instanceof GrControlStatement;
    }

    if (addParentheses) {
      codeBlockBuilder.append("{\n");
    }

    if (context != null) {
      insertStatementFromContextBefore(codeBlockBuilder, context);
    }
    codeBlockBuilder.append(statementBuilder);
    if (addParentheses) {
      codeBlockBuilder.append("}\n");
    }
  }

  public static void insertStatementFromContextBefore(StringBuilder codeBlockBuilder, ExpressionContext context) {
    for (String st : context.myStatements) {
      codeBlockBuilder.append(st).append("\n");
    }
  }

  public static void writeStatement(final StringBuilder builder, ExpressionContext context, @Nullable GrStatement statement, StatementWriter writer) {
    StringBuilder statementBuilder = new StringBuilder();
    ExpressionContext statementContext = context.copy();
    writer.writeStatement(statementBuilder, statementContext);
    writeStatement(builder, statementBuilder, statement, statementContext);
  }

  @Nullable
  static PsiClass findAccessibleSuperClass(@NotNull PsiElement context, @NotNull PsiClass initialClass) {
    PsiClass curClass = initialClass;
    final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(context.getProject()).getResolveHelper();
    while (curClass != null && !resolveHelper.isAccessible(curClass, context, null)) {
      curClass = curClass.getSuperClass();
    }
    return curClass;
  }

  static PsiType findOutParameterType(PsiParameter parameter) {
    return parameter.getType(); //todo make smarter
  }

  static boolean isAbstractInJava(PsiMethod method) {
    if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return true;
    }

    final PsiClass psiClass = method.getContainingClass();
    return psiClass != null && GrClassSubstitutor.getSubstitutedClass(psiClass).isInterface();
  }

  static void writeTypeParameters(StringBuilder text,
                                  PsiTypeParameterListOwner typeParameterListOwner,
                                  final ClassNameProvider classNameProvider) {
    if (!typeParameterListOwner.hasTypeParameters()) return;

    text.append("<");
    PsiTypeParameter[] parameters = typeParameterListOwner.getTypeParameters();
    final PsiTypeParameterList typeParameterList = typeParameterListOwner.getTypeParameterList();
    for (int i = 0; i < parameters.length; i++) {
      if (i > 0) text.append(", ");
      PsiTypeParameter parameter = parameters[i];
      text.append(parameter.getName());
      PsiClassType[] extendsListTypes = parameter.getExtendsListTypes();
      if (extendsListTypes.length > 0) {
        text.append(" extends ");
        for (int j = 0; j < extendsListTypes.length; j++) {
          if (j > 0) text.append(" & ");
          writeType(text, extendsListTypes[j], typeParameterList, classNameProvider);
        }
      }
    }
    text.append(">");
  }

  static void writeParameterList(StringBuilder text, PsiParameter[] parameters, final ClassNameProvider classNameProvider) {
    text.append("(");

    //writes myParameters
    int i = 0;
    while (i < parameters.length) {
      PsiParameter parameter = parameters[i];
      if (parameter == null) continue;

      if (i > 0) text.append(", ");  //append ','
      writeType(text, findOutParameterType(parameter), parameter, classNameProvider);
      text.append(" ");
      text.append(parameter.getName());

      i++;
    }
    text.append(")");
    text.append(" ");
  }

  static void writeThrowsList(StringBuilder text,
                              PsiReferenceList throwsList,
                              PsiClassType[] exceptions,
                              final ClassNameProvider classNameProvider) {
    if (exceptions.length <= 0) return;

    text.append("throws ");
    for (int i = 0; i < exceptions.length; i++) {
      PsiClassType exception = exceptions[i];
      if (i != 0) {
        text.append(",");
      }
      writeType(text, exception, throwsList, classNameProvider);
      text.append(" ");
    }
  }

  static Set<String> getVarTypes(GrVariableDeclaration variableDeclaration) {
    GrVariable[] variables = variableDeclaration.getVariables();
    final GrTypeElement typeElement = variableDeclaration.getTypeElementGroovy();
    Set<String> types = new HashSet<String>(variables.length);
    if (typeElement == null) {
      if (variables.length > 1) {
        for (GrVariable variable : variables) {
          final GrExpression initializer = variable.getInitializerGroovy();
          if (initializer != null) {
            final PsiType varType = initializer.getType();
            if (varType != null) {
              types.add(getTypeText(varType, variableDeclaration));
            }
          }
        }
      }
    }
    return types;
  }

  static String getTypeText(PsiType varType, PsiElement context) {
    final StringBuilder builder = new StringBuilder();
    writeType(builder, varType, context);
    return builder.toString();
  }

  static ArrayList<GrParameter> getActualParams(GrMethod constructor, int skipOptional) {
    GrParameter[] parameterList = constructor.getParameters();
    return getActualParams(parameterList, skipOptional);
  }

  public static ArrayList<GrParameter> getActualParams(GrParameter[] parameters, int skipOptional) {
    final ArrayList<GrParameter> actual = new ArrayList<GrParameter>(Arrays.asList(parameters));
    if (skipOptional == 0) return actual;
    for (int i = parameters.length - 1; i >= 0; i--) {
      if (!actual.get(i).isOptional()) continue;

      actual.remove(i);
      skipOptional--;
      if (skipOptional == 0) break;
    }
    return actual;
  }

  public static PsiType getVarType(GrVariable variable) {
    PsiType type = variable.getDeclaredType();
    if (type == null) {
      type = variable.getTypeGroovy();
    }
    if (type == null) {
      type = variable.getType();
    }
    return type;
  }

  public static void writeSimpleVarDeclaration(GrVariableDeclaration variableDeclaration,
                                               StringBuilder builder,
                                               ExpressionContext expressionContext) {
    GrVariable[] variables = variableDeclaration.getVariables();

    //Set<String> types = getVarTypes(variableDeclaration);

    //if (types.size() > 1) {
      if (variableDeclaration.getParent() instanceof GrControlStatement) {
        expressionContext.setInsertCurlyBrackets();
      }
      for (GrVariable variable : variables) {
        writeVariableSeparately(variable, builder, expressionContext);
        builder.append(";\n");
      }
      builder.delete(builder.length() - 1, builder.length());
      /*return;
    }


    ModifierListGenerator.writeModifiers(builder, variableDeclaration.getModifierList());

    PsiType type = getVarType(variables[0]);
    writeType(builder, type, variableDeclaration);

    builder.append(" ");
    for (GrVariable variable : variables) {
      writeVariableWithoutType(builder, expressionContext, variable);
      builder.append(", ");
    }
    if (variables.length > 0) {
      builder.delete(builder.length() - 2, builder.length());
    }
    builder.append(";");*/
  }

  static void writeVariableWithoutType(StringBuilder builder,
                                       ExpressionContext expressionContext,
                                       GrVariable variable,
                                       boolean wrapped,
                                       PsiType original) {
    builder.append(variable.getName());
    final GrExpression initializer = variable.getInitializerGroovy();
    if (initializer != null) {
      builder.append(" = ");
      if (wrapped) {
        builder.append("new ").append(GroovyCommonClassNames.GROOVY_LANG_REFERENCE);
        if (original != null) {
          builder.append('<');
          writeType(builder, original, variable, new GeneratorClassNameProvider());
          builder.append('>');
        }
        builder.append('(');
      }
      initializer.accept(new ExpressionGenerator(builder, expressionContext));
      if (wrapped) {
        builder.append(')');
      }
    }
  }

  static void writeVariableSeparately(GrVariable variable, StringBuilder builder, ExpressionContext expressionContext) {
    PsiType type = getVarType(variable);
    ModifierListGenerator.writeModifiers(builder, variable.getModifierList());

    PsiType originalType = type;
    LocalVarAnalyzer.Result analyzedVars = expressionContext.analyzedVars;
    boolean wrapped = false;
    if (analyzedVars != null) {
      if (analyzedVars.toMakeFinal(variable) && !variable.hasModifierProperty(PsiModifier.FINAL)) {
        builder.append(PsiModifier.FINAL).append(' ');
      }
      else if (analyzedVars.toWrap(variable)) {
        builder.append(PsiModifier.FINAL).append(' ');
        type = JavaPsiFacade.getElementFactory(expressionContext.project).createTypeFromText(
          GroovyCommonClassNames.GROOVY_LANG_REFERENCE + "<" + getTypeText(type, variable) + ">", variable);
        wrapped = true;
      }
    }

    writeType(builder, type, variable);
    builder.append(" ");

    writeVariableWithoutType(builder, expressionContext, variable, wrapped, originalType);
  }
}
