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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrClassSubstitutor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.formatter.GrControlStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.refactoring.DefaultGroovyVariableNameValidator;
import org.jetbrains.plugins.groovy.refactoring.GroovyNameSuggestionUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Maxim.Medvedev
 */
public class GenerationUtil {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.refactoring.convertToJava.GenerationUtil");
  public static final String[] JAVA_MODIFIERS = new String[]{
    PsiModifier.PUBLIC,
    PsiModifier.PROTECTED,
    PsiModifier.PRIVATE,
    PsiModifier.PACKAGE_LOCAL,
    PsiModifier.STATIC,
    PsiModifier.ABSTRACT,
    PsiModifier.FINAL,
    PsiModifier.NATIVE,
  };

  private GenerationUtil() {
  }

  public static void writeType(StringBuilder builder, PsiType type) {
    builder.append(type.getCanonicalText()); //todo make smarter.
  }

  public static void writeType(final StringBuilder builder,
                               final PsiType type,
                               final PsiElement context,
                               final ClassNameProvider classNameProvider) {
    if (type instanceof PsiPrimitiveType) {
      builder.append(type.getCanonicalText());
      return;
    }

    final boolean acceptEllipsis = isLastParameter(context);

    type.accept(new PsiTypeVisitor<Object>() {
      @Override
      public Object visitEllipsisType(PsiEllipsisType ellipsisType) {
        final PsiType componentType = ellipsisType.getComponentType();
        componentType.accept(this);
        if (acceptEllipsis) {
          builder.append("...");
        }
        else {
          builder.append("[]");
        }
        return this;
      }

      @Override
      public Object visitPrimitiveType(PsiPrimitiveType primitiveType) {
        if (classNameProvider.forStubs()) {
          builder.append(primitiveType.getCanonicalText());
          return this;
        }
        final PsiType boxed = TypesUtil.boxPrimitiveType(primitiveType, context.getManager(), context.getResolveScope());
        boxed.accept(this);
        return this;
      }

      @Override
      public Object visitArrayType(PsiArrayType arrayType) {
        arrayType.getComponentType().accept(this);
        builder.append("[]");
        return this;
      }

      @Override
      public Object visitClassType(PsiClassType classType) {
        final PsiType[] parameters = classType.getParameters();
        final PsiClass psiClass = classType.resolve();
        if (psiClass == null) {
          builder.append(classType.getClassName());
        }
        else {
          final String qname = classNameProvider.getQualifiedClassName(psiClass, context);
          builder.append(qname);
        }
        writeTypeParameters(builder, parameters);
        return this;
      }

      @Override
      public Object visitCapturedWildcardType(PsiCapturedWildcardType capturedWildcardType) {
        capturedWildcardType.getWildcard().accept(this);
        return this;
      }

      @Override
      public Object visitWildcardType(PsiWildcardType wildcardType) {
        builder.append("?");
        PsiType bound = wildcardType.getBound();
        if (bound == null) return this;
        if (wildcardType.isExtends()) {
          builder.append(" extends");
        }
        else {
          builder.append(" super ");
        }
        bound.accept(this);
        return this;
      }

      @Override
      public Object visitDisjunctionType(PsiDisjunctionType disjunctionType) {
        //todo
        throw new UnsupportedOperationException();
      }

      @Override
      public Object visitType(PsiType type) {
        //todo
        throw new UnsupportedOperationException();
      }
    });
  }

  private static boolean isLastParameter(PsiElement context) {
    final PsiElement parent = context.getParent();
    return context instanceof PsiParameter &&
           parent instanceof PsiParameterList &&
           ((PsiParameterList)parent).getParameterIndex((PsiParameter)context) == ((PsiParameterList)parent).getParametersCount() - 1;
  }

  private static void writeTypeParameters(StringBuilder builder, PsiType[] parameters) {
    if (parameters.length == 0) return;

    builder.append("<");
    for (PsiType parameter : parameters) {
      writeType(builder, parameter);
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
    writeTypeParameters(builder, referenceElement.getTypeArguments());
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

  public static boolean writeModifiers(StringBuilder text, PsiModifierList modifierList) {
    return writeModifiers(text, modifierList, JAVA_MODIFIERS);
  }

  public static boolean writeModifiers(StringBuilder text, PsiModifierList modifierList, String[] modifiers) {
    boolean wasAddedModifiers = false;
    for (String modifierType : modifiers) {
      if (modifierList.hasModifierProperty(modifierType)) {
        text.append(modifierType);
        text.append(" ");
        wasAddedModifiers = true;
      }
    }
    return wasAddedModifiers;
  }

  public static void writeClassModifiers(StringBuilder text,
                                         @Nullable PsiModifierList modifierList,
                                         boolean isInterface,
                                         boolean toplevel) {
    if (modifierList == null) {
      text.append("public ");
      return;
    }

    List<String> allowedModifiers = new ArrayList<String>();
    allowedModifiers.add(PsiModifier.PUBLIC);
    allowedModifiers.add(PsiModifier.FINAL);
    if (!toplevel) {
      allowedModifiers.addAll(Arrays.asList(PsiModifier.PROTECTED, PsiModifier.PRIVATE, PsiModifier.STATIC));
    }
    if (!isInterface) {
      allowedModifiers.add(PsiModifier.ABSTRACT);
    }

    writeModifiers(text, modifierList, allowedModifiers.toArray(new String[allowedModifiers.size()]));
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
}
