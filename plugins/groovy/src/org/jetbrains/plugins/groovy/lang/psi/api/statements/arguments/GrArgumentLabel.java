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

package org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;

/**
 * @author ilyas
 */
public interface GrArgumentLabel extends GroovyPsiElement, PsiReference {

  GrArgumentLabel[] EMPTY_ARRAY = new GrArgumentLabel[0];

  @NotNull
  PsiElement getNameElement();

  @Nullable
  String getName();

  @Nullable
  PsiType getExpectedArgumentType();

  @Nullable
  PsiType getLabelType();

  GrNamedArgument getNamedArgument();
}
