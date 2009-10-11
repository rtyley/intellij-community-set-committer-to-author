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

/*
 * User: anna
 * Date: 04-Feb-2008
 */
package com.intellij.ide.navigationToolbar;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

public interface NavBarModelExtension {
  ExtensionPointName<NavBarModelExtension> EP_NAME = ExtensionPointName.create("com.intellij.navbar");

  @Nullable
  String getPresentableText(Object object);

  @Nullable
  PsiElement getParent(PsiElement psiElement);

  @Nullable
  PsiElement adjustElement(PsiElement psiElement);

}