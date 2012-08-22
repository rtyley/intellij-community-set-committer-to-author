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
package com.intellij.util.xml.stubs;

import com.intellij.psi.stubs.ObjectStubSerializer;
import com.intellij.util.SmartList;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 8/2/12
 */
public class ElementStub extends DomStub {

  private final List<DomStub> myChildren = new SmartList<DomStub>();
  private final boolean myCustom;

  public ElementStub(@Nullable ElementStub parent, @NotNull StringRef name, StringRef namespace, boolean custom) {
    super(parent, name, namespace);
    myCustom = custom;
  }

  void addChild(DomStub child) {
    myChildren.add(child);
  }

  @Override
  public List<DomStub> getChildrenStubs() {
    return myChildren;
  }

  @Override
  public ObjectStubSerializer getStubType() {
    return ElementStubSerializer.INSTANCE;
  }

  @Override
  public String toString() {
    return getName();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    return obj instanceof ElementStub && id == ((ElementStub)obj).id && myLocalName.equals(((ElementStub)obj).myLocalName);
  }

  public boolean isCustom() {
    return myCustom;
  }
}
