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
package com.intellij.psi.util.proximity;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.util.ProximityLocation;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
public class SamePackageWeigher extends ProximityWeigher {

  public Comparable weigh(@NotNull final PsiElement element, final ProximityLocation location) {
    Module elementModule = ModuleUtil.findModuleForPsiElement(element);
    if (location.getPositionModule() == elementModule) {
      final PsiPackage psiPackage = PsiTreeUtil.getContextOfType(location.getPosition(), PsiPackage.class, false);
      if (psiPackage != null && psiPackage.equals(PsiTreeUtil.getContextOfType(element, PsiPackage.class, false))) return true;
    }
    return false;
  }
}
