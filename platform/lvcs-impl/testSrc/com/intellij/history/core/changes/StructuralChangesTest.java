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
import org.junit.Before;
import org.junit.Test;

public class StructuralChangesTest extends LocalVcsTestCase {
  private Entry root;

  @Before
  public void setUp() {
    root = new RootEntry();
  }

  @Test
  public void testAffectedEntryForCreateFileChange() {
    createDirectory(root, 99, "dir");
    StructuralChange c = new CreateFileChange(1, "dir/name", null, -1, false);
    c.applyTo(root);

    assertEquals(array(idp(-1, 99, 1)), c.getAffectedIdPaths());

  }

  @Test
  public void testAffectedEntryForCreateDirectoryChange() {
    StructuralChange c = new CreateDirectoryChange(2, "name");
    c.applyTo(root);

    assertEquals(array(idp(-1, 2)), c.getAffectedIdPaths());
  }

  @Test
  public void testAffectedEntryForChangeFileContentChange() {
    createFile(root, 16, "file", c("content"), -1);

    StructuralChange c = new ContentChange("file", c("new content"), -1);
    c.applyTo(root);

    assertEquals(array(idp(-1, 16)), c.getAffectedIdPaths());
  }

  @Test
  public void testAffectedEntryForRenameChange() {
    createFile(root, 42, "name", null, -1);

    StructuralChange c = new RenameChange("name", "new name");
    c.applyTo(root);

    assertEquals(array(idp(-1, 42)), c.getAffectedIdPaths());
  }

  @Test
  public void testAffectedEntryForMoveChange() {
    createDirectory(root, 1, "dir1");
    createDirectory(root, 2, "dir2");
    createFile(root, 13, "dir1/file", null, -1);

    StructuralChange c = new MoveChange("dir1/file", "dir2");
    c.applyTo(root);

    assertEquals(array(idp(-1, 1, 13), idp(-1, 2, 13)), c.getAffectedIdPaths());
  }

  @Test
  public void testAffectedEntryForDeleteChange() {
    createDirectory(root, 99, "dir");
    createFile(root, 7, "dir/file", null, -1);

    StructuralChange c = new DeleteChange("dir/file");
    c.applyTo(root);

    assertEquals(array(idp(-1, 99, 7)), c.getAffectedIdPaths());
  }

  @Test
  public void testAffectsOnlyDoesNotTakeIntoAccountParentChanges() {
    createDirectory(root, 1, "dir");
    createDirectory(root, 2, "dir/dir2");
    createFile(root, 3, "dir/dir2/file", null, -1);

    Change c = new RenameChange("dir", "newDir");
    c.applyTo(root);

    assertFalse(c.affectsOnlyInside(root.getEntry("newDir/dir2")));
    assertTrue(c.affectsOnlyInside(root.getEntry("newDir")));
  }

  @Test
  public void testAffectsOnlyMove() {
    createDirectory(root, 1, "root");
    createDirectory(root, 2, "root/dir1");
    createDirectory(root, 3, "root/dir2");
    createFile(root, 4, "root/dir1/file", null, -1);

    Change c = new MoveChange("root/dir1/file", "root/dir2");
    c.applyTo(root);
    assertTrue(c.affectsOnlyInside(root.getEntry("root")));
    assertFalse(c.affectsOnlyInside(root.getEntry("root/dir1")));
    assertFalse(c.affectsOnlyInside(root.getEntry("root/dir2")));
  }

  @Test
  public void testAffectsOnlyMoveParent() {
    createDirectory(root, 1, "root");
    createDirectory(root, 2, "root/dir");
    createDirectory(root, 3, "root/dir/dir2");

    Change c = new MoveChange("root/dir/dir2", "root");
    c.applyTo(root);

    assertFalse(c.affectsOnlyInside(root.getEntry("root/dir2")));
    assertTrue(c.affectsOnlyInside(root.getEntry("root")));
  }

}
