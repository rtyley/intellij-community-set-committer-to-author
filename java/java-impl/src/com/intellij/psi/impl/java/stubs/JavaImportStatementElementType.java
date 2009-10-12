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
 * @author max
 */
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiImportStaticStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.impl.java.stubs.impl.PsiImportStatementStubImpl;
import com.intellij.psi.impl.source.PsiImportStatementImpl;
import com.intellij.psi.impl.source.PsiImportStaticStatementImpl;
import com.intellij.psi.impl.source.tree.java.ImportStaticStatementElement;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.PersistentStringEnumerator;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class JavaImportStatementElementType extends JavaStubElementType<PsiImportStatementStub, PsiImportStatementBase> {
  public JavaImportStatementElementType(@NonNls @NotNull final String id) {
    super(id);
  }

  public PsiImportStatementBase createPsi(final PsiImportStatementStub stub) {
    assert !isCompiled(stub);
    if (stub.isStatic()) {
      return new PsiImportStaticStatementImpl(stub);
    }
    else {
      return new PsiImportStatementImpl(stub);
    }
  }

  public PsiImportStatementBase createPsi(final ASTNode node) {
    if (node instanceof ImportStaticStatementElement) {
      return new PsiImportStaticStatementImpl(node);
    }
    else {
      return new PsiImportStatementImpl(node);
    }
  }

  public PsiImportStatementStub createStub(final PsiImportStatementBase psi, final StubElement parentStub) {
    final byte flags = PsiImportStatementStubImpl.packFlags(psi.isOnDemand(), psi instanceof PsiImportStaticStatement);
    final PsiJavaCodeReferenceElement ref = psi.getImportReference();
    return new PsiImportStatementStubImpl(parentStub, ref != null ? ref.getCanonicalText() : null, flags);
  }

  public void serialize(final PsiImportStatementStub stub, final StubOutputStream dataStream)
      throws IOException {
    dataStream.writeByte(((PsiImportStatementStubImpl)stub).getFlags());
    dataStream.writeName(stub.getImportReferenceText());
  }

  public PsiImportStatementStub deserialize(final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    byte flags = dataStream.readByte();
    StringRef reftext = dataStream.readName();
    return new PsiImportStatementStubImpl(parentStub, reftext, flags);
  }

  public void indexStub(final PsiImportStatementStub stub, final IndexSink sink) {
  }
}