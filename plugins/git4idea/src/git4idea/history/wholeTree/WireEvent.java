/*
 * Copyright 2000-2010 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.history.wholeTree;

import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nullable;

/**
* @author irengrig
 *
 * commits with 1 start and end just belongs to its wire
*/
public class WireEvent {
  private final int myCommitIdx;
  // wire # can be taken from commit
  @Nullable
  private final int[] myCommitsEnds;      // branch point   |/.       -1 here -> start of a wire
  @Nullable
  private int[] myWireEnds;
  private int[] myCommitsStarts;    // merge commit   |\  parents here. -1 here -> no parents, i.e. break

  public WireEvent(final int commitIdx, final int[] commitsEnds) {
    myCommitIdx = commitIdx;
    myCommitsEnds = commitsEnds;
    myCommitsStarts = ArrayUtil.EMPTY_INT_ARRAY;
    myWireEnds = null;
  }

  public int getCommitIdx() {
    return myCommitIdx;
  }

  public void addStart(final int idx) {
    myCommitsStarts = ArrayUtil.append(myCommitsStarts, idx);
  }

  public void addWireEnd(final int idx) {
    if (myWireEnds == null) {
      myWireEnds = new int[]{idx};
    } else {
      myWireEnds = ArrayUtil.append(myWireEnds, idx);
    }
  }

  @Nullable
  public int[] getWireEnds() {
    return myWireEnds;
  }

  @Nullable
  public int[] getCommitsEnds() {
    return myCommitsEnds;
  }

  public int[] getCommitsStarts() {
    return myCommitsStarts;
  }

  // no parent commit present in quantity or exists
  public boolean isEnd() {
    return myCommitsStarts.length == 1 && myCommitsStarts[0] == -1;
  }

  public boolean isStart() {
    return myCommitsEnds != null && myCommitsEnds.length == 1 && myCommitsEnds[0] == -1;
  }
}
