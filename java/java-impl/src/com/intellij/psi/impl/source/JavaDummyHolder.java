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
package com.intellij.psi.impl.source;

import com.intellij.lang.StdLanguages;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class JavaDummyHolder extends DummyHolder implements PsiImportHolder {
  private static final Map<String,PsiClass> EMPTY = Collections.emptyMap();
  private Map<String, PsiClass> myPseudoImports = EMPTY;

  public JavaDummyHolder(@NotNull PsiManager manager, TreeElement contentElement, PsiElement context) {
    super(manager, contentElement, context, null, null, StdLanguages.JAVA);
  }

  public JavaDummyHolder(@NotNull PsiManager manager, CharTable table, boolean validity) {
    super(manager, null, null, table, Boolean.valueOf(validity), StdLanguages.JAVA);
  }

  public JavaDummyHolder(@NotNull PsiManager manager, PsiElement context) {
    super(manager, null, context, null, null, StdLanguages.JAVA);
  }

  public JavaDummyHolder(@NotNull PsiManager manager, TreeElement contentElement, PsiElement context, CharTable table) {
    super(manager, contentElement, context, table, null, StdLanguages.JAVA);
  }

  public JavaDummyHolder(@NotNull PsiManager manager, PsiElement context, CharTable table) {
    super(manager, null, context, table, null, StdLanguages.JAVA);
  }

  public JavaDummyHolder(@NotNull PsiManager manager, final CharTable table) {
    super(manager, null, null, table, null, StdLanguages.JAVA);
  }

  public boolean importClass(PsiClass aClass) {
    if (myContext != null) {
      final PsiClass resolved = JavaPsiFacade.getInstance(getProject()).getResolveHelper().resolveReferencedClass(aClass.getName(), myContext);
      if (resolved != null) {
        return getManager().areElementsEquivalent(aClass, resolved);
      }
    }

    String className = aClass.getName();
    if (!myPseudoImports.containsKey(className)) {
      if (myPseudoImports == EMPTY) {
        myPseudoImports = new LinkedHashMap<String, PsiClass>();
      }

      myPseudoImports.put(className, aClass);
      myManager.nonPhysicalChange(); // to clear resolve caches!
      return true;
    }
    else {
      return true;
    }
  }

  public boolean hasImports() {
    return !myPseudoImports.isEmpty();
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
    ElementClassHint classHint = processor.getHint(ElementClassHint.KEY);
    if (classHint == null || classHint.shouldProcess(ElementClassHint.DeclaractionKind.CLASS)) {
      final NameHint nameHint = processor.getHint(NameHint.KEY);
      final String name = nameHint != null ? nameHint.getName(state) : null;
      //"pseudo-imports"
      if (name != null) {
        PsiClass imported = myPseudoImports.get(name);
        if (imported != null) {
          if (!processor.execute(imported, state)) return false;
        }
      } else {
        for (PsiClass aClass : myPseudoImports.values()) {
          if (!processor.execute(aClass, state)) return false;
        }
      }

      if (myContext == null) {
        if (!JavaResolveUtil.processImplicitlyImportedPackages(processor, state, place, getManager())) return false;
      }
    }
    return true;
  }

  public boolean isSamePackage(PsiElement other) {
    if (other instanceof DummyHolder) {
      final PsiElement otherContext = ((DummyHolder)other).myContext;
      if (myContext == null) return otherContext == null;
      return JavaPsiFacade.getInstance(myContext.getProject()).arePackagesTheSame(myContext, otherContext);
    }
    if (other instanceof PsiJavaFile) {
      if (myContext != null) return JavaPsiFacade.getInstance(myContext.getProject()).arePackagesTheSame(myContext, other);
      final String packageName = ((PsiJavaFile)other).getPackageName();
      return "".equals(packageName);
    }
    return false;
  }

  public boolean isInPackage(PsiPackage aPackage) {
    if (myContext != null) return JavaPsiFacade.getInstance(myContext.getProject()).isInPackage(myContext, aPackage);
    return aPackage == null || "".equals(aPackage.getQualifiedName());
  }


  public void setOriginalFile(@NotNull final PsiFile originalFile) {
    super.setOriginalFile(originalFile);
    putUserData(PsiUtil.FILE_LANGUAGE_LEVEL_KEY, PsiUtil.getLanguageLevel(originalFile));
  }
}
