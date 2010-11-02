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
package com.intellij.openapi.vcs.ex;

import com.intellij.openapi.editor.Document;
import com.intellij.util.ArrayUtil;
import com.intellij.util.diff.Diff;

import java.util.LinkedList;
import java.util.List;

/**
 * author: lesya
 */

public class RangesBuilder {
  private List<Range> myRanges;

  public RangesBuilder(Document current, Document upToDate) {
    this(new DocumentWrapper(current).getLines(), new DocumentWrapper(upToDate).getLines(), 0, 0);
  }

  public RangesBuilder(List<String> current, List<String> upToDate, int shift, int uShift) {
    myRanges = new LinkedList<Range>();

    int shiftBefore = 0;

    int minSize = Math.min(upToDate.size(), current.size());

    for (int i = 0; i < minSize; i++) {
      if (upToDate.get(0).equals(current.get(0))) {
        upToDate.remove(0);
        current.remove(0);
        shiftBefore += 1;
      }
      else {
        break;
      }
    }

    minSize = Math.min(upToDate.size(), current.size());

    for (int i = 0; i < minSize; i++) {
      if (upToDate.get(upToDate.size() - 1).equals(current.get(current.size() - 1))) {
        upToDate.remove(upToDate.size() - 1);
        current.remove(current.size() - 1);
      }
      else {
        break;
      }
    }

    Diff.Change ch = Diff.buildChanges(ArrayUtil.toStringArray(upToDate), ArrayUtil.toStringArray(current));


    while (ch != null) {
      Range range = Range.createOn(ch, shift + shiftBefore, uShift + shiftBefore);
      myRanges.add(range);
      ch = ch.link;
    }

  }

  public List<Range> getRanges() {
    return myRanges;
  }

}
