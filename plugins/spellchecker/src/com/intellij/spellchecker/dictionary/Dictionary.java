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
package com.intellij.spellchecker.dictionary;

import com.intellij.spellchecker.trie.Action;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

public interface Dictionary {

  String getName();

  boolean contains(String word);

  boolean isEmpty();

  void addToDictionary(String word);

  void removeFromDictionary(String word);

  void addToDictionary(@Nullable Collection<String> words);

  void replaceAll(@Nullable Collection<String> words);

  void clear();

  void traverse(final Action action);

  @Nullable
  Set<String> getWords();

  @Nullable
  Set<String> getEditableWords();

  @Nullable
  Set<String> getNotEditableWords();


}
