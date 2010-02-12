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
package com.intellij.psi.presentation.java;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ClassPresentationUtil {
  private ClassPresentationUtil() {
  }

  public static String getNameForClass(@NotNull PsiClass aClass, boolean qualified) {
    if (aClass instanceof PsiAnonymousClass) {
      if (aClass instanceof PsiEnumConstantInitializer) {
        PsiEnumConstant enumConstant = ((PsiEnumConstantInitializer)aClass).getEnumConstant();
        String name = enumConstant.getName();
        return PsiBundle.message("enum.constant.context", name, getContextName(enumConstant, qualified));
      }
      return PsiBundle.message("anonymous.class.context.display", getContextName(aClass, qualified));
    }
    if (qualified){
      String qName = aClass.getQualifiedName();
      if (qName != null) return qName;
    }

    String className = aClass.getName();
    String contextName = getContextName(aClass, qualified);
    return contextName != null ? PsiBundle.message("class.context.display", className, contextName) : className;
  }

  private static String getNameForElement(@NotNull PsiElement element, boolean qualified) {
    if (element instanceof PsiClass){
      return getNameForClass((PsiClass)element, qualified);
    }
    else if (element instanceof PsiMethod){
      PsiMethod method = (PsiMethod)element;
      String methodName = method.getName();
      return PsiBundle.message("method.context.display", methodName, getContextName(method, qualified));
    }
    else if (element instanceof PsiClassOwner){
      return null;
    }
    else if (element instanceof PsiFile){
      return ((PsiFile)element).getName();
    }
    else{
      return null;
    }
  }

  private static String getContextName(@NotNull PsiElement element, boolean qualified) {
    PsiElement parent = PsiTreeUtil.getParentOfType(element, PsiMember.class, PsiFile.class);
    while(true){
      if (parent == null) return null;
      String name = getNameForElement(parent, qualified);
      if (name != null) return name;
      if (parent instanceof PsiFile) return null;
      parent = parent.getParent();
    }
  }

  public static ItemPresentation getPresentation(@NotNull final PsiClass psiClass) {
    if (psiClass instanceof PsiAnonymousClass) return null;
    return new ItemPresentation() {
      public String getPresentableText() {
        return getNameForClass(psiClass, false);
      }

      public String getLocationString() {
        PsiFile file = psiClass.getContainingFile();
        if (file instanceof PsiClassOwner) {
          PsiClassOwner classOwner = (PsiClassOwner)file;
          String packageName = classOwner.getPackageName();
          if (packageName.length() == 0) return null;
          return "(" + packageName + ")";
        }
        return null;
      }

      public TextAttributesKey getTextAttributesKey() {
        if (psiClass.isDeprecated()) {
          return CodeInsightColors.DEPRECATED_ATTRIBUTES;
        }
        return null;
      }

      public Icon getIcon(boolean open) {
        return psiClass.getIcon(Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS);
      }
    };
  }
}
