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

package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.cache.RecordUtil;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.psi.impl.java.stubs.impl.PsiClassStubImpl;
import com.intellij.psi.impl.java.stubs.index.JavaAnonymousClassBaseRefOccurenceIndex;
import com.intellij.psi.impl.java.stubs.index.JavaFullClassNameIndex;
import com.intellij.psi.impl.java.stubs.index.JavaShortClassNameIndex;
import com.intellij.psi.impl.source.PsiAnonymousClassImpl;
import com.intellij.psi.impl.source.PsiClassImpl;
import com.intellij.psi.impl.source.PsiEnumConstantInitializerImpl;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.LightTreeUtil;
import com.intellij.psi.impl.source.tree.java.AnonymousClassElement;
import com.intellij.psi.impl.source.tree.java.EnumConstantInitializerElement;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/*
 * @author max
 */
public class JavaClassElementType extends JavaStubElementType<PsiClassStub, PsiClass> {
  public JavaClassElementType(@NotNull @NonNls final String id) {
    super(id);
  }

  public PsiClass createPsi(final PsiClassStub stub) {
    if (isCompiled(stub)) {
      return new ClsClassImpl(stub);
    }
    if (stub.isEnumConstantInitializer()) {
      return new PsiEnumConstantInitializerImpl(stub);
    }
    if (stub.isAnonymous()) {
      return new PsiAnonymousClassImpl(stub);
    }
    return new PsiClassImpl(stub);
  }

  public PsiClass createPsi(final ASTNode node) {
    if (node instanceof EnumConstantInitializerElement) {
      return new PsiEnumConstantInitializerImpl(node);
    }
    else if (node instanceof AnonymousClassElement) {
      return new PsiAnonymousClassImpl(node);
    }

    return new PsiClassImpl(node);
  }

  public PsiClassStub createStub(final PsiClass psi, final StubElement parentStub) {
    final boolean isAnonymous = psi instanceof PsiAnonymousClass;
    final boolean isEnumConst = psi instanceof PsiEnumConstantInitializer;

    byte flags = PsiClassStubImpl.packFlags(RecordUtil.isDeprecatedByDocComment(psi),
                                            psi.isInterface(),
                                            psi.isEnum(),
                                            isEnumConst,
                                            isAnonymous,
                                            psi.isAnnotationType(),
                                            isAnonymous && ((PsiAnonymousClass)psi).isInQualifiedNew(),
                                            RecordUtil.isDeprecatedByAnnotation(psi));

    String baseRef = isAnonymous ? ((PsiAnonymousClass)psi).getBaseClassReference().getText() : null;
    final JavaClassElementType type = typeForClass(isAnonymous, isEnumConst);
    return new PsiClassStubImpl(type, parentStub, psi.getQualifiedName(), psi.getName(), baseRef, flags);
  }

  @Override
  public PsiClassStub createStub(final LighterAST tree, final LighterASTNode node, final StubElement parentStub) {
    boolean isDeprecatedByComment = false;
    boolean isInterface = false;
    boolean isEnum = false;
    boolean isEnumConst = false;
    boolean isAnonymous = false;
    boolean isAnnotation = false;
    boolean isInQualifiedNew = false;
    boolean hasDeprecatedAnnotation = false;

    String qualifiedName = null;
    String name = null;
    String baseRef = null;

    if (node.getTokenType() == JavaElementType.ANONYMOUS_CLASS) {
      isAnonymous = true;
    }
    else if (node.getTokenType() == JavaElementType.ENUM_CONSTANT_INITIALIZER) {
      isAnonymous = isEnumConst = true;
      baseRef = ((PsiClassStub)parentStub.getParentStub()).getName();
    }

    for (final LighterASTNode child : tree.getChildren(node)) {
      final IElementType type = child.getTokenType();
      if (type == JavaDocElementType.DOC_COMMENT) {
        isDeprecatedByComment = RecordUtil.isDeprecatedByDocComment(tree, child);
      }
      else if (type == JavaElementType.MODIFIER_LIST) {
        hasDeprecatedAnnotation = RecordUtil.isDeprecatedByAnnotation(tree, child);
      }
      else if (type == JavaTokenType.AT) {
        isAnnotation = true;
      }
      else if (type == JavaTokenType.INTERFACE_KEYWORD) {
        isInterface = true;
      }
      else if (type == JavaTokenType.ENUM_KEYWORD) {
        isEnum = true;
      }
      else if (!isAnonymous && type == JavaTokenType.IDENTIFIER) {
        name = RecordUtil.intern(tree.getCharTable(), child);
      }
      else if (isAnonymous && !isEnumConst && type == JavaElementType.JAVA_CODE_REFERENCE) {
        baseRef = LightTreeUtil.toFilteredString(tree, child, null);
      }
    }

    if (name != null) {
      if (parentStub instanceof PsiJavaFileStub) {
        final String pkg = ((PsiJavaFileStub)parentStub).getPackageName();
        if (pkg.length() > 0) qualifiedName = pkg + '.' + name; else qualifiedName = name;
      }
      else if (parentStub instanceof PsiClassStub) {
        final String parentFqn = ((PsiClassStub)parentStub).getQualifiedName();
        qualifiedName = parentFqn != null ? parentFqn + '.' + name : null;
      }
    }

    if (isAnonymous) {
      final LighterASTNode parent = node.getParent();
      if (parent != null && parent.getTokenType() == JavaElementType.NEW_EXPRESSION) {
        isInQualifiedNew = (LightTreeUtil.firstChildOfType(tree, parent, JavaTokenType.DOT) != null);
      }
    }

    final byte flags = PsiClassStubImpl.packFlags(isDeprecatedByComment, isInterface, isEnum, isEnumConst, isAnonymous, isAnnotation,
                                                  isInQualifiedNew, hasDeprecatedAnnotation);
    final JavaClassElementType type = typeForClass(isAnonymous, isEnumConst);
    return new PsiClassStubImpl(type, parentStub, qualifiedName, name, baseRef, flags);
  }

  public static JavaClassElementType typeForClass(final boolean anonymous, final boolean enumConst) {
    return enumConst
           ? JavaStubElementTypes.ENUM_CONSTANT_INITIALIZER
           : anonymous ? JavaStubElementTypes.ANONYMOUS_CLASS : JavaStubElementTypes.CLASS;
  }

  public void serialize(final PsiClassStub stub, final StubOutputStream dataStream) throws IOException {
    dataStream.writeByte(((PsiClassStubImpl)stub).getFlags());
    if (!stub.isAnonymous()) {
      dataStream.writeName(stub.getName());
      dataStream.writeName(stub.getQualifiedName());
      dataStream.writeName(stub.getSourceFileName());
    }
    else {
      dataStream.writeName(stub.getBaseClassReferenceText());
    }
  }

  public PsiClassStub deserialize(final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    byte flags = dataStream.readByte();

    final boolean isAnonymous = PsiClassStubImpl.isAnonymous(flags);
    final boolean isEnumConst = PsiClassStubImpl.isEnumConstInitializer(flags);
    final JavaClassElementType type = typeForClass(isAnonymous, isEnumConst);

    if (!isAnonymous) {
      StringRef name = dataStream.readName();
      StringRef qname = dataStream.readName();
      final StringRef sourceFileName = dataStream.readName();
      final PsiClassStubImpl classStub = new PsiClassStubImpl(type, parentStub, qname, name, null, flags);
      classStub.setSourceFileName(sourceFileName);
      return classStub;
    }
    else {
      StringRef baseRef = dataStream.readName();
      return new PsiClassStubImpl(type, parentStub, null, null, baseRef, flags);
    }
  }

  public void indexStub(final PsiClassStub stub, final IndexSink sink) {
    boolean isAnonymous = stub.isAnonymous();
    if (isAnonymous) {
      String baseRef = stub.getBaseClassReferenceText();
      if (baseRef != null) {
        sink.occurrence(JavaAnonymousClassBaseRefOccurenceIndex.KEY, PsiNameHelper.getShortClassName(baseRef));
      }
    }
    else {
      final String shortName = stub.getName();
      if (shortName != null) {
        sink.occurrence(JavaShortClassNameIndex.KEY, shortName);
      }

      final String fqn = stub.getQualifiedName();
      if (fqn != null) {
        sink.occurrence(JavaFullClassNameIndex.KEY, fqn.hashCode());
      }
    }
  }

  public String getId(final PsiClassStub stub) {
    final String name = stub.getName();
    if (name != null) return name;

    return super.getId(stub);
  }
}