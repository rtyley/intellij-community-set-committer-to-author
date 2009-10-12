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

package org.jetbrains.plugins.groovy.extensions.completion;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;

/**
 * @author ilyas
 */
public interface ContextSpecificInsertHandler {

  boolean isAcceptable(InsertionContext context, int startOffset, LookupElement item);

  /**
   * Handler can be executed ONLY if {@link #isAcceptable(com.intellij.codeInsight.completion.InsertionContext, int, com.intellij.codeInsight.lookup.LookupElement)}
   * returns true.
   */
  void handleInsert(InsertionContext context, int startOffset, LookupElement item);
}
