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
package com.intellij.psi.util;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public abstract class MethodSignatureBase implements MethodSignature {

  private final PsiSubstitutor mySubstitutor;
  private final PsiType[] myParameterTypes;
  protected final PsiTypeParameter[] myTypeParameters;

  protected MethodSignatureBase(PsiSubstitutor substitutor, @NotNull PsiType[] parameterTypes, @NotNull PsiTypeParameter[] typeParameters) {
    mySubstitutor = substitutor;
    myParameterTypes = parameterTypes.length == 0 ? PsiType.EMPTY_ARRAY : new PsiType[parameterTypes.length];
    for (int i = 0; i < parameterTypes.length; i++) {
      PsiType type = parameterTypes[i];
      if (type instanceof PsiEllipsisType) type = ((PsiEllipsisType) type).toArrayType();
      myParameterTypes[i] = substitutor.substitute(type);
    }
    myTypeParameters = typeParameters;
  }

  protected MethodSignatureBase(@NotNull PsiSubstitutor substitutor, PsiParameterList parameterList, @Nullable PsiTypeParameterList typeParameterList) {
    mySubstitutor = substitutor;
    if (parameterList != null) {
      final PsiParameter[] parameters = parameterList.getParameters();
      myParameterTypes = parameters.length == 0 ? PsiType.EMPTY_ARRAY : new PsiType[parameters.length];
      for (int i = 0; i < parameters.length; i++) {
        PsiType type = parameters[i].getType();
        if (type instanceof PsiEllipsisType) type = ((PsiEllipsisType)type).toArrayType();
        myParameterTypes[i] = substitutor.substitute(type);
      }
    }
    else {
      myParameterTypes = PsiType.EMPTY_ARRAY;
    }

    myTypeParameters = typeParameterList == null ? PsiTypeParameter.EMPTY_ARRAY : typeParameterList.getTypeParameters();
  }

  @NotNull
  public PsiType[] getParameterTypes() {
    return myParameterTypes;
  }

  @NotNull
  public PsiTypeParameter[] getTypeParameters() {
    return myTypeParameters;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof MethodSignature)) return false;

    final MethodSignature methodSignature = (MethodSignature)o;
    return MethodSignatureUtil.areSignaturesEqual(methodSignature, this);
  }

  public int hashCode() {
    int result = getName().hashCode();

    final PsiType[] parameterTypes = getParameterTypes();
    result += 37 * parameterTypes.length;
    return result;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    String s = getClass().getSimpleName() + ": ";
    final PsiTypeParameter[] typeParameters = getTypeParameters();
    if (typeParameters.length != 0) {
      String sep = "<";
      for (PsiTypeParameter typeParameter : typeParameters) {
        s += sep + typeParameter.getName();
        sep = ", ";
      }
      s += ">";
    }
    s += getName() + "(" + Arrays.asList(getParameterTypes()) + ")";
    return s;
  }

  @NotNull
  public PsiSubstitutor getSubstitutor() {
    return mySubstitutor;
  }

}
