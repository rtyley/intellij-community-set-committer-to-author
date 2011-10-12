/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.spellchecker.inspections;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextSplitter extends BaseSplitter {

  private static final Pattern EXTENDED_WORD_AND_SPECIAL = Pattern.compile("([&#]|0x[0-9]*)?\\p{L}+'?\\p{L}(_*\\p{L})*");

  @Override
  public void split(@Nullable String text, @NotNull TextRange range, Consumer<TextRange> consumer) {
    if (text == null || StringUtil.isEmpty(text)) {
      return;
    }
    doSplit(text, range, consumer);
  }

  protected void doSplit(@NotNull String text, @NotNull TextRange range, Consumer<TextRange> consumer) {
    final WordSplitter ws = SplitterFactory.getInstance().getWordSplitter();
    Matcher matcher = EXTENDED_WORD_AND_SPECIAL.matcher(range.substring(text));
    while (matcher.find()) {
      TextRange found = matcherRange(range, matcher);
      ws.split(text, found, consumer);
    }
  }
}