
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
package com.intellij.psi.impl;

import com.intellij.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiNameHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiNameHelperImpl extends PsiNameHelper{
  private final JavaPsiFacade myManager;
  private Lexer myLexer;
  private LanguageLevel myLastLanguageLevel;
  private final Object LOCK = new Object();

  public PsiNameHelperImpl(JavaPsiFacade manager) {
    myManager = manager;
    myLastLanguageLevel = LanguageLevelProjectExtension.getInstance(manager.getProject()).getLanguageLevel();
    myLexer = new JavaLexer(myLastLanguageLevel);
  }

  private void updateLexer(LanguageLevel languageLevel){
    if (!myLastLanguageLevel.equals(languageLevel)){
      myLastLanguageLevel = languageLevel;
      myLexer = new JavaLexer(myLastLanguageLevel);
    }
  }

  public boolean isIdentifier(@Nullable String text) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    if (text == null) return false;

    synchronized (LOCK) {
      updateLexer(LanguageLevelProjectExtension.getInstance(myManager.getProject()).getLanguageLevel());
      myLexer.start(text);
      if (myLexer.getTokenType() != JavaTokenType.IDENTIFIER) return false;
      myLexer.advance();
      return myLexer.getTokenType() == null;
    }
  }

  public boolean isIdentifier(@Nullable String text, @NotNull LanguageLevel languageLevel) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    if (text == null) return false;

    synchronized (LOCK) {
      updateLexer(languageLevel);
      myLexer.start(text);
      if (myLexer.getTokenType() != JavaTokenType.IDENTIFIER) return false;
      myLexer.advance();
      return myLexer.getTokenType() == null;
    }
  }

  public boolean isKeyword(@Nullable String text) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    if (text == null) return false;

    synchronized (LOCK) {
      updateLexer(LanguageLevelProjectExtension.getInstance(myManager.getProject()).getLanguageLevel());
      myLexer.start(text);
      if (myLexer.getTokenType() == null || !JavaTokenType.KEYWORD_BIT_SET.contains(myLexer.getTokenType())) return false;
      myLexer.advance();
      return myLexer.getTokenType() == null;
    }
  }

  public boolean isQualifiedName(@Nullable String text){
    if (text == null) return false;
    int index = 0;
    while(true){
      int index1 = text.indexOf('.', index);
      if (index1 < 0) index1 = text.length();
      if (!isIdentifier(text.substring(index, index1))) return false;
      if (index1 == text.length()) return true;
      index = index1 + 1;
    }
  }
}
