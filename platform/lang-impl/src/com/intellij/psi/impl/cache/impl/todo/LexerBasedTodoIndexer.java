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

package com.intellij.psi.impl.cache.impl.todo;

import com.intellij.psi.impl.cache.impl.BaseFilterLexerUtil;
import com.intellij.psi.impl.cache.impl.IdAndToDoScannerBasedOnFilterLexer;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileContent;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 20, 2008
 */
public abstract class LexerBasedTodoIndexer implements DataIndexer<TodoIndexEntry, Integer, FileContent>,
                                                       IdAndToDoScannerBasedOnFilterLexer {
  @Override
  @NotNull
  public Map<TodoIndexEntry,Integer> map(final FileContent inputData) {
    return BaseFilterLexerUtil.scanContent(inputData, this).todoMap;
  }
}
