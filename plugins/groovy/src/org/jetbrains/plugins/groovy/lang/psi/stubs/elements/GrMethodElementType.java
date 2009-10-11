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
package org.jetbrains.plugins.groovy.lang.psi.stubs.elements;

import com.intellij.lang.ASTNode;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrStubElementType;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members.GrMethodImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrMethodStub;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrStubUtils;
import org.jetbrains.plugins.groovy.lang.psi.stubs.impl.GrMethodStubImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrAnnotatedMemberIndex;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrMethodNameIndex;

import java.io.IOException;
import java.util.*;

/**
 * @author ilyas
 */
public class GrMethodElementType extends GrStubElementType<GrMethodStub, GrMethod> {

  public GrMethodElementType() {
    super("method definition");
  }

  public GrMethod createElement(ASTNode node) {
    return new GrMethodImpl(node);
  }

  public GrMethod createPsi(GrMethodStub stub) {
    return new GrMethodImpl(stub);
  }

  public GrMethodStub createStub(GrMethod psi, StubElement parentStub) {
    final GrModifierList modifiers = psi.getModifierList();
    final GrAnnotation[] annotations = modifiers.getAnnotations();
    String[] annNames = ContainerUtil.map(annotations, new Function<GrAnnotation, String>() {
      @Nullable
      public String fun(final GrAnnotation grAnnotation) {
        final GrCodeReferenceElement element = grAnnotation.getClassReference();
        if (element == null) return null;
        return element.getReferenceName();
      }
    }, new String[annotations.length]);

    Set<String>[] namedParametersArray;
    namedParametersArray = psi.getNamedParametersArray();

    return new GrMethodStubImpl(parentStub, StringRef.fromString(psi.getName()), annNames, namedParametersArray);
  }

  public void serialize(GrMethodStub stub, StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getName());
    final String[] annotations = stub.getAnnotations();
    dataStream.writeByte(annotations.length);
    for (String s : annotations) {
      dataStream.writeName(s);
    }
    final Set<String>[] namedParameters = stub.getNamedParameters();

    GrStubUtils.serializeCollectionsArray(dataStream, namedParameters);
  }
    
  public GrMethodStub deserialize(StubInputStream dataStream, StubElement parentStub) throws IOException {
    StringRef ref = dataStream.readName();
    final byte b = dataStream.readByte();
    final String[] annNames = new String[b];
    for (int i = 0; i < b; i++) {
      annNames[i] = dataStream.readName().toString();
    }
    final List<Set<String>> namedParametersSets = GrStubUtils.deserializeCollectionsArray(dataStream);

    return new GrMethodStubImpl(parentStub, ref, annNames, namedParametersSets.toArray(new HashSet[0]));
  }

  public void indexStub(GrMethodStub stub, IndexSink sink) {
    String name = stub.getName();
    if (name != null) {
      sink.occurrence(GrMethodNameIndex.KEY, name);
    }
    for (String annName : stub.getAnnotations()) {
      if (annName != null) {
        sink.occurrence(GrAnnotatedMemberIndex.KEY, annName);
      }
    }
  }
}
