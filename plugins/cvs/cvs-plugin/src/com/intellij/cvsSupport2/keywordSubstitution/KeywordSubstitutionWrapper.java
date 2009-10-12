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
package com.intellij.cvsSupport2.keywordSubstitution;

import org.netbeans.lib.cvsclient.command.KeywordSubstitution;

/**
 * author: lesya
 */
public class KeywordSubstitutionWrapper{

  private final KeywordSubstitution myKeywordSubstitution;
  private final String myDisplayName;

  public static final KeywordSubstitutionWrapper BINARY = new KeywordSubstitutionWrapper(KeywordSubstitution.BINARY, com.intellij.CvsBundle.message("keyword.substitution.binary"));
  public static final KeywordSubstitutionWrapper KEYWORD_COMPRESSION = new KeywordSubstitutionWrapper(KeywordSubstitution.KEYWORD_COMPRESSION, com.intellij.CvsBundle.message("keyword.substitution.compression"));
  public static final KeywordSubstitutionWrapper KEYWORD_EXPANSION = new KeywordSubstitutionWrapper(KeywordSubstitution.KEYWORD_EXPANSION, com.intellij.CvsBundle.message("keyword.substitution.expansion"));
  public static final KeywordSubstitutionWrapper KEYWORD_EXPANSION_LOCKER = new KeywordSubstitutionWrapper(KeywordSubstitution.KEYWORD_EXPANSION_LOCKER, com.intellij.CvsBundle.message("keyword.substitution.expansion.locker"));
  public static final KeywordSubstitutionWrapper NO_SUBSTITUTION = new KeywordSubstitutionWrapper(KeywordSubstitution.NO_SUBSTITUTION, com.intellij.CvsBundle.message("keyword.substitution.no.substitution"));
  public static final KeywordSubstitutionWrapper KEYWORD_REPLACEMENT = new KeywordSubstitutionWrapper(KeywordSubstitution.KEYWORD_REPLACEMENT, com.intellij.CvsBundle.message("keyword.substitution.replacement"));

  private KeywordSubstitutionWrapper(KeywordSubstitution keywordSubstitution, String displayName) {
    myKeywordSubstitution = keywordSubstitution;
    myDisplayName = displayName;
  }

  public KeywordSubstitution getSubstitution(){
    return myKeywordSubstitution;
  }

  public String toString() {
    return myDisplayName;
  }

  public static KeywordSubstitutionWrapper getValue(String substitution) {
    KeywordSubstitution keywordSubstitution = KeywordSubstitution.getValue(substitution);
    return getValue(keywordSubstitution);
  }

  public static KeywordSubstitutionWrapper getValue(KeywordSubstitution substitution) {
    if (substitution == KeywordSubstitution.BINARY) return KeywordSubstitutionWrapper.BINARY;
    if (substitution == KeywordSubstitution.KEYWORD_COMPRESSION) return KeywordSubstitutionWrapper.KEYWORD_COMPRESSION;
    if (substitution == KeywordSubstitution.KEYWORD_EXPANSION) return KeywordSubstitutionWrapper.KEYWORD_EXPANSION;
    if (substitution == KeywordSubstitution.KEYWORD_EXPANSION_LOCKER) return KeywordSubstitutionWrapper.KEYWORD_EXPANSION_LOCKER;
    if (substitution == KeywordSubstitution.NO_SUBSTITUTION) return KeywordSubstitutionWrapper.NO_SUBSTITUTION;
    if (substitution == KeywordSubstitution.KEYWORD_REPLACEMENT) return KeywordSubstitutionWrapper.KEYWORD_REPLACEMENT;
    return null;
  }

  public String getStringRepresentation(){
    if (myKeywordSubstitution == null) return null;
    return myKeywordSubstitution.toString();
  }
}
