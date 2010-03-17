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

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlainTextSplitter extends BaseSplitter {


  @NonNls
  private static final Pattern COMPLEX =
    Pattern.compile("([\\p{L}0-9\\.\\-\\_]+@([\\p{L}0-9\\-\\_]+\\.)+(com|net|[a-z]{2}))|((ftp|http|file|https)://([^/]+)(/.*)?(/.*))");


  private static final Pattern EXTENDED_WORD_AND_SPECIAL = Pattern.compile("[&#]?\\p{L}*'?\\p{L}(_*\\p{L})*");


  public List<CheckArea> split(@Nullable String text, @NotNull TextRange range) {
    if (text == null || StringUtil.isEmpty(text)) {
      return null;
    }

    List<TextRange> toCheck = excludeByPattern(text, range, COMPLEX, 0);

    if (toCheck == null) return null;

    Matcher matcher;
    List<CheckArea> results = new ArrayList<CheckArea>();
    final WordSplitter ws = SplitterFactory.getInstance().getWordSplitter();
    for (TextRange r : toCheck) {

      checkCancelled();

      matcher = EXTENDED_WORD_AND_SPECIAL.matcher(text.substring(r.getStartOffset(), r.getEndOffset()));
      while (matcher.find()) {
        TextRange found = matcherRange(r, matcher);
        final List<CheckArea> res = ws.split(text, found);
        if (res != null) {
          results.addAll(res);
        }
      }
    }

    return (results.size() == 0) ? null : results;
  }


}
