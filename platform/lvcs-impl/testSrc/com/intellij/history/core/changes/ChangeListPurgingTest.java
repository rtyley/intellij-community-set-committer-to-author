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

package com.intellij.history.core.changes;

import com.intellij.history.core.storage.Content;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class ChangeListPurgingTest extends ChangeListTestCase {
  int myIntervalBetweenActivities = 2;

  @Before
  public void setUp() {
    cl = new ChangeList() {
      @Override
      protected long getIntervalBetweenActivities() {
        return myIntervalBetweenActivities;
      }
    };
  }

  @Test
  public void testPurgeWithoutGapsBetweenChanges() {
    createChangesWithTimestamps(1, 2, 3);
    cl.purgeObsolete(2);
    assertRemainedChangesTimestamps(3, 2);
  }

  @Test
  public void testPurgeNothing() {
    createChangesWithTimestamps(1, 2, 3);
    cl.purgeObsolete(10);
    assertRemainedChangesTimestamps(3, 2, 1);
  }

  @Test
  public void testDoesNotPurgeTheOnlyChange() {
    createChangesWithTimestamps(1);
    cl.purgeObsolete(1);
    assertRemainedChangesTimestamps(1);
  }

  @Test
  public void testPurgeWithOneGap() {
    createChangesWithTimestamps(1, 2, 4);
    cl.purgeObsolete(2);
    assertRemainedChangesTimestamps(4, 2);
  }

  @Test
  public void testPurgeWithSeveralGaps() {
    createChangesWithTimestamps(1, 2, 4, 5, 7, 8);
    cl.purgeObsolete(5);
    assertRemainedChangesTimestamps(8, 7, 5, 4, 2);
  }

  @Test
  public void testPurgeWithLongGaps() {
    createChangesWithTimestamps(10, 20, 30, 40);
    cl.purgeObsolete(3);
    assertRemainedChangesTimestamps(40, 30, 20);
  }

  @Test
  public void testPurgeWithBifIntervalBetweenChanges() {
    myIntervalBetweenActivities = 100;

    createChangesWithTimestamps(110, 120, 130, 250, 260, 270);
    cl.purgeObsolete(40);
    assertRemainedChangesTimestamps(270, 260, 250, 130, 120);
  }

  @Test
  public void testPurgingEmptyListDoesNotThrowException() {
    cl.purgeObsolete(50);
  }

  @Test
  public void testChangesAfterPurge() {
    applyAndAdd(cs(1, new CreateFileChange(1, "file", null, -1, false)));
    applyAndAdd(cs(2, new ContentChange("file", null, -1)));
    applyAndAdd(cs(3, new ContentChange("file", null, -1)));

    assertEquals(3, cl.getChangesFor(r, "file").size());

    cl.purgeObsolete(2);

    assertEquals(2, cl.getChangesFor(r, "file").size());
  }

  @Test
  public void testReturningContentsToPurge() {
    createFile(r, 1, "f", c("one"), -1);

    applyAndAdd(cs(1, new ContentChange("f", c("two"), -1)));
    applyAndAdd(cs(2, new ContentChange("f", c("three"), -1)));

    ContentChange c1 = new ContentChange("f", c("four"), -1);
    ContentChange c2 = new ContentChange("f", c("five"), -1);
    applyAndAdd(cs(3, c1, c2));

    applyAndAdd(cs(4, new ContentChange("f", c("six"), -1)));

    List<Content> contents = cl.purgeObsolete(1);

    assertEquals(1, cl.getChanges().size());

    assertEquals(4, contents.size());
    assertEquals(c("one"), contents.get(0));
    assertEquals(c("two"), contents.get(1));
    assertEquals(c("three"), contents.get(2));
    assertEquals(c("four"), contents.get(3));
  }

  private void createChangesWithTimestamps(long... tt) {
    for (long t : tt) cl.addChange(new PutLabelChange(null, t));
  }

  private void assertRemainedChangesTimestamps(long... tt) {
    assertEquals(tt.length, cl.getChanges().size());
    for (int i = 0; i < tt.length; i++) {
      long t = tt[i];
      Change c = cl.getChanges().get(i);
      assertEquals(t, c.getTimestamp());
    }
  }
}
