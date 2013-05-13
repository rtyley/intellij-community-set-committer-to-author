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
package com.intellij.openapi.diff;

import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class LineTokenizer {
  private final char[] myChars;
  private final String myText;

  private int myIndex = 0;
  @Nullable private String myLineSeparator = null;

  public LineTokenizer(@NotNull String text) {
    myChars = text.toCharArray();
    myText = text;
  }

  @NotNull
  public String[] execute() {
    List<String> lines = new ArrayList<String>();
    while (notAtEnd()) {
      int begin = myIndex;
      skipToEOL();
      int endIndex = myIndex;
      boolean appendNewLine = false;

      if (notAtEnd() && isAtEOL()) {
        if (myChars[endIndex] == '\n') {
          endIndex++;
        }
        else {
          appendNewLine = true;
        }
        skipEOL();
      }

      String line = myText.substring(begin, endIndex);
      if (appendNewLine) {
        line += "\n";
      }
      lines.add(line);
    }
    return ArrayUtil.toStringArray(lines);
  }

  private void skipEOL() {
    int eolStart = myIndex;
    boolean nFound = false;
    boolean rFound = false;
    while (notAtEnd()) {
      boolean n = myChars[myIndex] == '\n';
      boolean r = myChars[myIndex] == '\r';
      if (!n && !r) {
        break;
      }
      if ((nFound && n) || (rFound && r)) {
        break;
      }
      nFound |= n;
      rFound |= r;
      myIndex++;
    }
    if (myLineSeparator == null) {
      myLineSeparator = new String(myChars, eolStart, myIndex - eolStart);
    }
  }

  @Nullable
  public String getLineSeparator() {
    return myLineSeparator;
  }

  private void skipToEOL() {
    while (notAtEnd() && !isAtEOL()) {
      myIndex++;
    }
  }

  private boolean notAtEnd() {
    return myIndex < myChars.length;
  }

  private boolean isAtEOL() {
    return myChars[myIndex] == '\r' || myChars[myIndex] == '\n';
  }

  @NotNull
  public static String concatLines(@NotNull String[] lines) {
    StringBuilder buffer = new StringBuilder();
    for (String line : lines) {
      buffer.append(line);
    }
    return buffer.substring(0, buffer.length());
  }

  @NotNull
  public static String correctLineSeparators(@NotNull String text) {
    return concatLines(new LineTokenizer(text).execute());
  }

}
