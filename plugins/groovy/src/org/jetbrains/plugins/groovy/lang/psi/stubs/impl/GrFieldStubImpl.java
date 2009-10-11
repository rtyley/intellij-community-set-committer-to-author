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
package org.jetbrains.plugins.groovy.lang.psi.stubs.impl;

import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrFieldStub;

import java.util.Set;

/**
 * @author ilyas
 */
public class GrFieldStubImpl extends StubBase<GrField> implements GrFieldStub {

  private final boolean isEnumConstant;
  private final StringRef myName;
  private final String[] myAnnotations;
  @Nullable
  private final Set<String>[] myNamedParameters;

  public GrFieldStubImpl(StubElement parent, StringRef name, boolean isEnumConstant, final String[] annotations, @NotNull Set<String>[] namedParameters, final IStubElementType elemType) {
    super(parent, elemType);
    myName = name;
    this.isEnumConstant = isEnumConstant;
    myAnnotations = annotations;
    myNamedParameters = namedParameters;
  }

  public boolean isEnumConstant() {
    return isEnumConstant;
  }

  public String getName() {
    return StringRef.toString(myName);
  }

  public String[] getAnnotations() {
    return myAnnotations;
  }

  @NotNull
  public Set<String>[] getNamedParameters() {
    return myNamedParameters;
  }
}
