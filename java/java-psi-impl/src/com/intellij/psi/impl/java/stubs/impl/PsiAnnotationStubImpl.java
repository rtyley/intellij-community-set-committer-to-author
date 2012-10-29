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
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiJavaParserFacade;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiAnnotationStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PatchedSoftReference;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author max
 */
public class PsiAnnotationStubImpl extends StubBase<PsiAnnotation> implements PsiAnnotationStub {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.java.stubs.impl.PsiAnnotationStubImpl");

  private final String myText;
  private PatchedSoftReference<PsiAnnotation> myParsedFromRepository;

  public PsiAnnotationStubImpl(final StubElement parent, final String text) {
    this(parent, text, null);
  }

  public PsiAnnotationStubImpl(final StubElement parent, final String text, @Nullable List<Pair<String, String>> attributes) {
    super(parent, JavaStubElementTypes.ANNOTATION);
    myText = text;
    if (attributes != null) {
      PsiAnnotationParameterListStubImpl list = new PsiAnnotationParameterListStubImpl(this);
      for (Pair<String, String> attribute : attributes) {
        new PsiNameValuePairStubImpl(list, StringRef.fromString(attribute.first), StringRef.fromString(attribute.second));
      }
    }
  }

  @Override
  public String getText() {
    return myText;
  }

  @Override
  public PsiAnnotation getPsiElement() {
    if (myParsedFromRepository != null) {
      PsiAnnotation annotation = myParsedFromRepository.get();
      if (annotation != null) {
        return annotation;
      }
    }

    final String text = getText();
    try {
      PsiJavaParserFacade facade = JavaPsiFacade.getInstance(getProject()).getParserFacade();
      PsiAnnotation annotation = facade.createAnnotationFromText(text, getPsi());
      myParsedFromRepository = new PatchedSoftReference<PsiAnnotation>(annotation);
      assert annotation != null : text;
      return annotation;
    }
    catch (IncorrectOperationException e) {
      LOG.error("Bad annotation in repository!", e);
      return null;
    }
  }

  @Override
  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "PsiAnnotationStub[" + myText + "]";
  }
}
