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

package com.intellij.lang.parameterInfo;

import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public interface ParameterInfoHandlerWithTabActionSupport<ParameterOwner extends PsiElement, ParameterType, ActualParameterType extends PsiElement>
  extends ParameterInfoHandler<ParameterOwner, ParameterType> {

  @NotNull ActualParameterType[] getActualParameters(@NotNull ParameterOwner o);

  @NotNull IElementType getActualParameterDelimiterType();

  @NotNull
  IElementType getActualParametersRBraceType();

  @NotNull
  Set<Class> getArgumentListAllowedParentClasses();

  @NotNull
  Set<? extends Class> getArgListStopSearchClasses();

  @NotNull Class<ParameterOwner> getArgumentListClass();
}
