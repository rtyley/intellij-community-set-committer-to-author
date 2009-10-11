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

import com.intellij.history.core.LocalVcsTestCase;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.core.tree.RootEntry;
import org.junit.Test;

import java.util.List;

public class RootEntryRootsTest extends LocalVcsTestCase {
  private final Entry root = new RootEntry();

  @Test
  public void testCreatingRoots() {
    createDirectory(root, 1, "c:/dir/root");

    assertTrue(root.hasEntry("c:/dir/root"));
    assertFalse(root.hasEntry("c:/dir"));
  }

  @Test
  public void testCreatingEntriesUnderRoot() {
    createDirectory(root, 1, "c:/root");
    createFile(root, 2, "c:/root/entry", null, -1);

    assertTrue(root.hasEntry("c:/root/entry"));
  }

  @Test
  public void testMovingEntriesBetweenRoots() {
    createDirectory(root, 1, "c:/root1");
    createDirectory(root, 2, "c:/root2");

    createFile(root, 3, "c:/root1/file", null, -1);
    move(root, "c:/root1/file", "c:/root2");

    assertFalse(root.hasEntry("c:/root1/file"));
    assertTrue(root.hasEntry("c:/root2/file"));
  }

  @Test
  public void testCanNotCreateEntryNotUnderExistingRoots() {
    try {
      createFile(root, 1, "c:/non-existing-root/file", null, -1);
      fail();
    }
    catch (RuntimeException e) {
    }
  }

  @Test
  public void testRenamingRoots() {
    createDirectory(root, 1, "c:/dir/root");
    rename(root, "c:/dir/root", "newName");

    assertTrue(root.hasEntry("c:/dir/newName"));
    assertFalse(root.hasEntry("c:/dir/root"));
  }

  @Test
  public void testDeletingRoots() {
    createDirectory(root, 1, "c:/dir/root");

    delete(root, "c:/dir/root");
    assertFalse(root.hasEntry("c:/dir/root"));
  }

  @Test
  public void testGettingRoots() {
    createDirectory(root, 1, "c:/root1");
    createDirectory(root, 2, "c:/root2");
    createDirectory(root, 3, "c:/root2/dir");

    List<Entry> roots = root.getChildren();
    assertEquals(2, roots.size());

    assertEquals("c:/root1", roots.get(0).getName());
    assertEquals("c:/root2", roots.get(1).getName());
  }
}
