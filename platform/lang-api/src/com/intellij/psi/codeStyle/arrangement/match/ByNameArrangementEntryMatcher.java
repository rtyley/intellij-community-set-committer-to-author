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
package com.intellij.psi.codeStyle.arrangement.match;

import com.intellij.psi.codeStyle.arrangement.ArrangementEntry;
import com.intellij.psi.codeStyle.arrangement.NameAwareArrangementEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Denis Zhdanov
 * @since 7/19/12 6:36 PM
 */
public class ByNameArrangementEntryMatcher extends AbstractRegexpArrangementMatcher {

  public ByNameArrangementEntryMatcher(@NotNull String pattern) {
    super(pattern);
  }

  @Nullable
  @Override
  protected String getTextToMatch(@NotNull ArrangementEntry entry) {
    if (entry instanceof NameAwareArrangementEntry) {
      return  ((NameAwareArrangementEntry)entry).getName();
    }
    else {
      return null;
    }
  }
}
