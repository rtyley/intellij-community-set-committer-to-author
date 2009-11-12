/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.actions.generate.constructors;

import com.intellij.codeInsight.generation.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * User: Dmitry.Krasilschikov
 * Date: 21.05.2008
 */
public class ConstructorGenerateHandler extends GenerateConstructorHandler {

  @Nullable
  protected ClassMember[] chooseOriginalMembers(PsiClass aClass, Project project) {
    final ClassMember[] classMembers = super.chooseOriginalMembers(aClass, project);

    if (classMembers == null) return null;

    List<ClassMember> res = new ArrayList<ClassMember>();
    final PsiElementFactory factory = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory();
    String text;

    for (ClassMember classMember : classMembers) {

      if (classMember instanceof PsiMethodMember) {
        PsiMethod constructorImpl;
        final PsiMethod method = ((PsiMethodMember) classMember).getElement();

        //TODO: rewrite it like fine java method
        text = method.getText();
        try {
          constructorImpl = factory.createMethodFromText(text, aClass);
          res.add(new PsiMethodMember(constructorImpl));
        } catch (IncorrectOperationException e) {
          e.printStackTrace();
        }

      } else if (classMember instanceof PsiFieldMember) {
        final PsiFieldMember fieldMember = (PsiFieldMember) classMember;
        PsiField fieldImpl;

        final PsiField field = fieldMember.getElement();
        try {
          fieldImpl = factory.createFieldFromText(field.getType().getCanonicalText() + " " + field.getName(), aClass);
          res.add(new PsiFieldMember(fieldImpl));
        } catch (IncorrectOperationException e) {
          e.printStackTrace();
        }
      }
    }

    return res.toArray(new ClassMember[res.size()]);
  }

  @NotNull
  protected List<? extends GenerationInfo> generateMemberPrototypes(PsiClass aClass, ClassMember[] members) throws IncorrectOperationException {
    final List<? extends GenerationInfo> list = super.generateMemberPrototypes(aClass, members);

    List<PsiGenerationInfo<GrMethod>> grConstructors = new ArrayList<PsiGenerationInfo<GrMethod>>();

    GrMethod grConstructor;
    for (GenerationInfo generationInfo : list) {
      final PsiMember constructorMember = generationInfo.getPsiMember();
      assert constructorMember instanceof PsiMethod;
      final PsiMethod constructor = (PsiMethod) constructorMember;

      final PsiCodeBlock block = constructor.getBody();
      assert block != null;

      final String constructorName = aClass.getName();
      final String body = block.getText();
      final PsiParameterList list1 = constructor.getParameterList();

      List<String> parametersNames = new ArrayList<String>();
      for (PsiParameter parameter : list1.getParameters()) {
        parametersNames.add(parameter.getName());
      }

      final String[] paramNames = ArrayUtil.toStringArray(parametersNames);
      assert constructorName != null;
      grConstructor = GroovyPsiElementFactory.getInstance(aClass.getProject()).createConstructorFromText(constructorName, null, paramNames, body);

      PsiUtil.shortenReferences(grConstructor);

      final PsiGenerationInfo<GrMethod> psiGenerationInfo = new GroovyGenerationInfo<GrMethod>(grConstructor);
      grConstructors.add(psiGenerationInfo);
    }

    return grConstructors;
  }

  public boolean startInWriteAction() {
    return true;
  }


}
