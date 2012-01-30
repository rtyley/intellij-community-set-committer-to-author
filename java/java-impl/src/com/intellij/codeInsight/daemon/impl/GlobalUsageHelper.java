/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * @author peter
 */
public abstract class GlobalUsageHelper {
  final Map<PsiClass,Boolean> unusedClassCache = new HashMap<PsiClass, Boolean>();

  public abstract boolean shouldCheckUsages(@NotNull PsiMember member);
  public boolean isLocallyUsed(@NotNull PsiNamedElement member) {
    return false;
  }

  public boolean shouldIgnoreUsagesInCurrentFile() {
    return false;
  }
}
