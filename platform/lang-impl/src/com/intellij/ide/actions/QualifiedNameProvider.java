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

package com.intellij.ide.actions;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface QualifiedNameProvider {
  ExtensionPointName<QualifiedNameProvider> EP_NAME = ExtensionPointName.create("com.intellij.qualifiedNameProvider");

  @Nullable
  PsiElement adjustElementToCopy(PsiElement element);

  @Nullable
  String getQualifiedName(PsiElement element);

  PsiElement qualifiedNameToElement(final String fqn, final Project project);

  void insertQualifiedName(final String fqn, final PsiElement element, final Editor editor, final Project project);
}
