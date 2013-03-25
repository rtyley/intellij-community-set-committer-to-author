/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiModifierListStub;
import com.intellij.psi.impl.java.stubs.PsiParameterStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author max
 */
public class PsiParameterStubImpl extends StubBase<PsiParameter> implements PsiParameterStub {
  private StringRef myName;
  private final TypeInfo myType;
  private final boolean myIsEllipsis;

  public PsiParameterStubImpl(StubElement parent, @NotNull String name, @NotNull TypeInfo type, boolean isEllipsis) {
    this(parent, StringRef.fromString(name), type, isEllipsis);
  }

  public PsiParameterStubImpl(StubElement parent, @NotNull StringRef name, @NotNull TypeInfo type, boolean isEllipsis) {
    super(parent, JavaStubElementTypes.PARAMETER);
    myName = name;
    myType = type;
    myIsEllipsis = isEllipsis;
  }

  @Override
  public boolean isParameterTypeEllipsis() {
    return myIsEllipsis;
  }

  @Override
  public boolean isReceiver() {
    return PsiKeyword.THIS.equals(getName());
  }

  @Override
  @NotNull
  public TypeInfo getType(boolean doResolve) {
    return doResolve ? myType.applyAnnotations(this) : myType;
  }

  @Override
  public PsiModifierListStub getModList() {
    for (StubElement child : getChildrenStubs()) {
      if (child instanceof PsiModifierListStub) {
        return (PsiModifierListStub)child;
      }
    }
    return null;
  }

  @NotNull
  @Override
  public String getName() {
    return StringRef.toString(myName);
  }

  public void setName(String name) {
    myName = StringRef.fromString(name);
  }

  public boolean isAutoGeneratedName() {
    final List children = getParentStub().getChildrenStubs();
    int paramIndex = 0;
    for (Object o : children) {
      if (o instanceof PsiParameterStub) {
        paramIndex++;
        if (o == this) break;
      }
    }
    return ("p" + paramIndex).equals(getName());
  }

  @Override
  public String toString() {
    return "PsiParameterStub[" + myName + ':' + myType + ']';
  }
}
