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
package org.jetbrains.plugins.groovy.lang.psi.stubs;

import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.NamedStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.io.StringRef;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

/**
 * @author ilyas
 */
public class GrTypeDefinitionStub extends StubBase<GrTypeDefinition> implements NamedStub<GrTypeDefinition> {
  private static final int ANONYMOUS = 0x01;
  private static final int INTERFACE = 0x02;
  private static final int ENUM = 0x04;
  private static final int ANNOTATION = 0x08;
  private static final int IS_IN_QUALIFIED_NEW = 0x10;

  private final StringRef myName;
  private final String[] mySuperClasses;
  private final StringRef myQualifiedName;
  private final String[] myAnnotations;
  private final byte myFlags;

  public GrTypeDefinitionStub(StubElement parent,
                                  final String name,
                                  final String[] supers,
                                  final IStubElementType elementType,
                                  final String qualifiedName,
                                  String[] annotations,
                                  byte flags) {
    super(parent, elementType);
    myAnnotations = annotations;
    myName = StringRef.fromString(name);
    mySuperClasses = supers;
    myQualifiedName = StringRef.fromString(qualifiedName);
    myFlags = flags;
  }

  public String[] getSuperClassNames() {
    return mySuperClasses;
  }

  public String getName() {
    return StringRef.toString(myName);
  }

  public String[] getAnnotations() {
    return myAnnotations;
  }

  public String getQualifiedName() {
    return StringRef.toString(myQualifiedName);
  }

  public boolean isAnnotationType() {
    return (myFlags & ANNOTATION) != 0;
  }

  public boolean isAnonymous() {
    return (myFlags & ANONYMOUS) != 0;
  }

  public boolean isAnonymousInQualifiedNew() {
    return (myFlags & IS_IN_QUALIFIED_NEW) != 0;
  }

  public boolean isInterface() {
    return (myFlags & INTERFACE) != 0;
  }

  public boolean isEnum() {
    return (myFlags & ENUM) != 0;
  }

  public byte getFlags() {
    return myFlags;
  }

  public static byte buildFlags(GrTypeDefinition typeDefinition) {
    byte flags = 0;
    if (typeDefinition.isAnonymous()) {
      flags |= ANONYMOUS;
      assert typeDefinition instanceof GrAnonymousClassDefinition;
      if (((GrAnonymousClassDefinition)typeDefinition).isInQualifiedNew()) {
        flags |= IS_IN_QUALIFIED_NEW;
      }
    }
    if (typeDefinition.isAnnotationType()) flags |= ANNOTATION;
    if (typeDefinition.isInterface()) flags |= INTERFACE;
    if (typeDefinition.isEnum()) flags |= ENUM;
    return flags;
  }

}
