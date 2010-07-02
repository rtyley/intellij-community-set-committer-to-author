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
package com.intellij.openapi.editor.impl.softwrap;

import com.intellij.openapi.editor.TextChange;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Holds registered soft wraps and provides monitoring and management facilities for them.
 * <p/>
 * Not thread-safe.
 *
 * @author Denis Zhdanov
 * @since Jun 29, 2010 3:04:20 PM
 */
public class SoftWrapsStorage {

  private static final Comparator<TextChange> SOFT_WRAPS_BY_OFFSET_COMPARATOR = new Comparator<TextChange>() {
    public int compare(TextChange c1, TextChange c2) {
      return c1.getStart() - c2.getEnd();
    }
  };

  private final List<TextChange> myWraps = new ArrayList<TextChange>();
  private final List<TextChange> myWrapsView = Collections.unmodifiableList(myWraps);

  /**
   * @return    <code>true</code> if there is at least one soft wrap registered at the current storage; <code>false</code> otherwise
   */
  public boolean isEmpty() {
    return myWraps.isEmpty();
  }

  public boolean hasSoftWrapAt(int offset) {
    return getSoftWrapIndex(offset) >= 0;
  }

  @Nullable
  public TextChange getSoftWrap(int offset) {
    int i = getSoftWrapIndex(offset);
    return i >= 0 ? myWraps.get(i) : null;
  }

  /**
   * @return    view for registered soft wraps sorted by offset in ascending order if any; empty collection otherwise
   */
  @NotNull
  public List<TextChange> getSoftWraps() {
    return myWrapsView;
  }

  /**
   * Tries to find index of the target soft wrap stored at {@link #myWraps} collection. <code>'Target'</code> soft wrap is the one
   * that starts at the given offset.
   *
   * @param offset    target offset
   * @return          index that conforms to {@link Collections#binarySearch(List, Object)} contract, i.e. non-negative returned
   *                  index points to soft wrap that starts at the given offset; <code>'-(negative value) - 1'</code> points
   *                  to position at {@link #myWraps} collection where soft wrap for the given index should be inserted
   */
  public int getSoftWrapIndex(int offset) {
    TextChange searchKey = new TextChange("", offset);
    return Collections.binarySearch(myWraps, searchKey, SOFT_WRAPS_BY_OFFSET_COMPARATOR);
  }

  /**
   * Inserts given soft wrap to {@link #myWraps} collection at the given index.
   *
   * @param softWrap    soft wrap to store
   * @return            previous soft wrap object stored for the same offset if any; <code>null</code> otherwise
   */
  @Nullable
  public TextChange storeSoftWrap(TextChange softWrap) {
    int i = Collections.binarySearch(myWraps, softWrap, SOFT_WRAPS_BY_OFFSET_COMPARATOR);
    if (i >= 0) {
      return myWraps.set(i, softWrap);
    }

    i = -i - 1;
    myWraps.add(i, softWrap);
    return null;
  }

  /**
   * Asks current storage to remove soft wrap registered for the current index if any (soft wraps are stored at collection
   * ordered by soft wrap start offsets).
   *
   * @param index   target soft wrap index
   * @return        removed soft wrap if the one was found for the given index; <code>null</code> otherwise
   */
  @Nullable
  public TextChange removeByIndex(int index) {
    if (index < 0 || index >= myWraps.size()) {
      return null;
    }
    return myWraps.remove(index);
  }

  /**
   * Removes all soft wraps with offsets at [start; end] range registered at the current storage if any.
   *
   * @param start   start range offset (inclusive)
   * @param end     end range offset (exclusive)
   */
  public void removeInRange(int start, int end) {
    int startIndex = getSoftWrapIndex(start);
    if (startIndex < 0) {
      startIndex = -startIndex - 1;
    }
    int endIndex = startIndex;
    for (; endIndex < myWraps.size(); endIndex++) {
      TextChange softWrap = myWraps.get(endIndex);
      if (softWrap.getStart() >= end) {
        break;
      }
    }
    myWraps.subList(startIndex, endIndex).clear();
  }

  /**
   * Removes all soft wraps registered at the current storage.
   */
  public void removeAll() {
    myWraps.clear();
  }
}
