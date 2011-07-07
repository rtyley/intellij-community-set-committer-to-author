/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.highlighting;

import com.intellij.openapi.diff.ex.DiffFragment;
import com.intellij.openapi.diff.impl.processing.DiffPolicy;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.BeforeAfter;
import com.intellij.util.diff.FilesTooBigForDiffException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author irengrig
 *         Date: 7/7/11
 *         Time: 12:49 PM
 */
public class PresetBlocksDiffPolicy implements DiffPolicy {
  // fragment _start_ offsets
  private List<BeforeAfter<TextRange>> myRanges;
  private final DiffPolicy myDelegate;

  public PresetBlocksDiffPolicy(DiffPolicy delegate) {
    myDelegate = delegate;
  }

  @Override
  public DiffFragment[] buildFragments(String text1, String text2) throws FilesTooBigForDiffException {
    final List<DiffFragment> fragments = new ArrayList<DiffFragment>();
    for (int i = 0; i < myRanges.size(); i++) {
      final BeforeAfter<TextRange> range = myRanges.get(i);
      fragments.addAll(Arrays.asList(myDelegate.buildFragments(new String(text1.substring(range.getBefore().getStartOffset(), range.getBefore().getEndOffset())),
                       new String(text2.substring(range.getAfter().getStartOffset(), range.getAfter().getEndOffset())))));
    }

    return fragments.toArray(new DiffFragment[fragments.size()]);
  }

  public void setRanges(List<BeforeAfter<TextRange>> ranges) {
    myRanges = ranges;
  }
}
