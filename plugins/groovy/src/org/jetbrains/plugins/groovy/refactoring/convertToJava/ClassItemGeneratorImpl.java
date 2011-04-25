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
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrEnumConstantInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrConstructor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.impl.types.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.*;

import static org.jetbrains.plugins.groovy.refactoring.convertToJava.GenerationUtil.*;

/**
 * @author Maxim.Medvedev
 */
public class ClassItemGeneratorImpl implements ClassItemGenerator {
  private Project myProject;
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.refactoring.convertToJava.ClassItemGeneratorImpl");
  private ClassNameProvider classNameProvider;

  public ClassItemGeneratorImpl(Project project) {
    myProject = project;
    classNameProvider=new GeneratorClassNameProvider();
  }

  @Override
  public void writeEnumConstant(StringBuilder builder, GrEnumConstant constant) {
    builder.append(constant.getName());

    final GrArgumentList argumentList = constant.getArgumentList();
    final ExpressionContext context = new ExpressionContext(myProject);
    if (argumentList != null) {
      final GroovyResolveResult resolveResult = constant.resolveConstructorGenerics();
      GrClosureSignature signature = GrClosureSignatureUtil.createSignature(resolveResult);
      new ArgumentListGenerator(builder, context).generate(
        signature,
        argumentList.getExpressionArguments(),
        argumentList.getNamedArguments(),
        GrClosableBlock.EMPTY_ARRAY,
        constant
      );
    }

    final GrEnumConstantInitializer anonymousBlock = constant.getConstantInitializer();
    if (anonymousBlock != null) {
      builder.append("{\n");
      new ClassGenerator(myProject, classNameProvider, this).writeMembers(builder, anonymousBlock, true);
      builder.append("\n}");
    }

    //todo make something with context???
  }

  @Override
  public void writeConstructor(StringBuilder text, GrConstructor constructor, int skipParams, boolean isEnum) {
    writeMethod(text, constructor, skipParams);
  }

  @Override
  public void writeMethod(StringBuilder builder, PsiMethod method, int skipOptional) {
    if (method == null) return;
    String name = method.getName();

    boolean isAbstract = GenerationUtil.isAbstractInJava(method);

    PsiModifierList modifierList = method.getModifierList();

    if (!method.isConstructor()) {
      writeModifiers(builder, modifierList);
    }
    else {
      writeModifiers(builder, modifierList, CONSTRUCTOR_MODIFIERS);
    }


    if (method.hasTypeParameters()) {
      writeTypeParameters(builder, method, classNameProvider);
      builder.append(" ");
    }

    //append return type
    if (!method.isConstructor()) {
      PsiType retType = PsiUtil.getSmartReturnType(method);

      /*
      if (!method.hasModifierProperty(PsiModifier.STATIC)) {
        final List<MethodSignatureBackedByPsiMethod> superSignatures = method.findSuperMethodSignaturesIncludingStatic(true);
        for (MethodSignatureBackedByPsiMethod superSignature : superSignatures) {
          final PsiType superType = superSignature.getSubstitutor().substitute(superSignature.getMethod().getReturnType());
          if (superType != null &&
              !superType.isAssignableFrom(retType) &&
              !(PsiUtil.resolveClassInType(superType) instanceof PsiTypeParameter)) {
            retType = superType;
          }
        }
      }
      */

      writeType(builder, retType, method, classNameProvider);
      builder.append(" ");
    }
    builder.append(name);

    if (method instanceof GrMethod) {
      final ArrayList<GrParameter> actualParams = getActualParams((GrMethod)method, skipOptional);
      GenerationUtil.writeParameterList(builder, actualParams.toArray(new GrParameter[actualParams.size()]), classNameProvider);
    } else {
      LOG.assertTrue(skipOptional==0);
      GenerationUtil.writeParameterList(builder, method.getParameterList().getParameters(), classNameProvider);
    }


    GenerationUtil.writeThrowsList(builder, method.getThrowsList(), getMethodExceptions(method), classNameProvider);

    if (!isAbstract) {
      /************* body **********/
      if (method instanceof GrMethod) {
        ((GrMethod)method).accept(new CodeBlockGenerator(builder, myProject));
      }
      else if (method instanceof GrAccessorMethod) {
        writeAccessorBody(builder, method);
      }
      else {
        builder.append("{//todo\n}");
      }
    }
    else {
      builder.append(";");
    }
  }

  private static void writeAccessorBody(StringBuilder builder, PsiMethod method) {
    final String propName = ((GrAccessorMethod)method).getProperty().getName();
    if (((GrAccessorMethod)method).isSetter()) {
      final String paramName = method.getParameterList().getParameters()[0].getName();
      builder.append("{\n this.").append(propName).append(" = ").append(paramName).append(";\n}");
    }
    else {
      builder.append("{\n return ");
      builder.append(propName);
      builder.append(";\n}");
    }
  }

  private PsiClassType[] getMethodExceptions(PsiMethod method) {
    return method.getThrowsList().getReferencedTypes();

    //todo find method exceptions!
  }

  @Override
  public void writeVariableDeclarations(StringBuilder builder, GrVariableDeclaration variableDeclaration) {
    final ExpressionContext context = new ExpressionContext(myProject);
    GenerationUtil.writeSimpleVarDeclaration(variableDeclaration, builder, context);
    builder.append('\n');
  }

  @Override
  public Collection<PsiMethod> collectMethods(PsiClass typeDefinition, boolean classDef) {
    return Arrays.asList(typeDefinition.getMethods());
  }
}
