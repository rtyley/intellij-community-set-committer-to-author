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

package com.intellij.history.integration.ui.models;

import com.intellij.history.core.LocalVcsTestCase;
import com.intellij.history.core.revisions.Difference;
import com.intellij.history.core.tree.DirectoryEntry;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.core.tree.FileEntry;
import org.junit.Test;


public class DirectoryChangeModelTest extends LocalVcsTestCase {
  @Test
  public void testNames() {
    Entry left = new DirectoryEntry(-1, "left");
    Entry right = new DirectoryEntry(-1, "right");

    Difference d = new Difference(false, left, right);
    DirectoryChangeModel m = createModelOn(d);

    assertEquals("left", m.getEntryName(0));
    assertEquals("right", m.getEntryName(1));
  }

  @Test
  public void testNamesForAbsentEntries() {
    Difference d = new Difference(false, null, null);
    DirectoryChangeModel m = createModelOn(d);

    assertEquals("", m.getEntryName(0));
    assertEquals("", m.getEntryName(1));
  }

  @Test
  public void testFileDifferenceModel() {
    Entry left = new FileEntry(-1, "left", c(""), 123L, false);
    Entry right = new FileEntry(-1, "right", c(""), 123L, false);

    Difference d = new Difference(false, left, right);
    DirectoryChangeModel dm = createModelOn(d);
    FileDifferenceModel m = dm.getFileDifferenceModel();

    assertTrue(m.getLeftTitle(new NullRevisionsProgress()).endsWith("left"));
    assertTrue(m.getRightTitle(new NullRevisionsProgress()).endsWith("right"));
  }

  @Test
  public void testCanShowFileDifference() {
    Entry left = new FileEntry(-1, "left", c(""), -1, false);
    Entry right = new FileEntry(-1, "right", c(""), -1, false);

    Difference d1 = new Difference(true, left, right);
    Difference d2 = new Difference(true, null, right);
    Difference d3 = new Difference(true, left, null);

    assertTrue(createModelOn(d1).canShowFileDifference());
    assertTrue(createModelOn(d2).canShowFileDifference());
    assertTrue(createModelOn(d3).canShowFileDifference());
  }

  @Test
  public void testCanNotShowFileDifferenceForDirectories() {
    Entry left = new DirectoryEntry(-1, "left");
    Entry right = new DirectoryEntry(-1, "right");

    Difference d = new Difference(false, left, right);
    assertFalse(createModelOn(d).canShowFileDifference());
  }

  private DirectoryChangeModel createModelOn(Difference d) {
    return new DirectoryChangeModel(d, null, false);
  }
}
