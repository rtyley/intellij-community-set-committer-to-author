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

package com.intellij.history.core;

import com.intellij.history.core.revisions.Revision;
import com.intellij.history.core.tree.Entry;
import org.junit.Test;

import java.util.List;

public class LocalVcsChangeSetsTest extends LocalVcsTestCase {
  LocalVcs vcs = new InMemoryLocalVcs();

  @Test
  public void testApplyingChangesRightAfterChange() {
    long timestamp = -1;
    vcs.createFile("file", cf("content"), timestamp, false);
    assertEquals(c("content"), vcs.getEntry("file").getContent());

    vcs.changeFileContent("file", cf("new content"), -1);
    assertEquals(c("new content"), vcs.getEntry("file").getContent());
  }

  @Test
  public void testAskingForNewFileContentDuringChangeSet() {
    vcs.beginChangeSet();
    long timestamp = -1;
    vcs.createFile("file", cf("content"), timestamp, false);

    Entry e = vcs.findEntry("file");

    assertNotNull(e);
    assertEquals(c("content"), e.getContent());
  }

  @Test
  public void testTreatingSeveralChangesDuringChangeSetAsOne() {
    vcs.beginChangeSet();
    vcs.createDirectory("dir");
    long timestamp = -1;
    vcs.createFile("dir/one", null, timestamp, false);
    long timestamp1 = -1;
    vcs.createFile("dir/two", null, timestamp1, false);
    vcs.endChangeSet(null);

    assertEquals(1, vcs.getRevisionsFor("dir").size());
  }

  @Test
  public void testTreatingSeveralChangesOutsideOfChangeSetAsSeparate() {
    vcs.createDirectory("dir");
    long timestamp3 = -1;
    vcs.createFile("dir/one", null, timestamp3, false);
    long timestamp = -1;
    vcs.createFile("dir/two", null, timestamp, false);

    vcs.beginChangeSet();
    vcs.endChangeSet(null);

    long timestamp1 = -1;
    vcs.createFile("dir/three", null, timestamp1, false);
    long timestamp2 = -1;
    vcs.createFile("dir/four", null, timestamp2, false);

    assertEquals(5, vcs.getRevisionsFor("dir").size());
  }

  @Test
  public void testIgnoringInnerChangeSets() {
    vcs.beginChangeSet();
    vcs.createDirectory("dir");
    vcs.beginChangeSet();
    long timestamp1 = -1;
    vcs.createFile("dir/one", null, timestamp1, false);
    vcs.endChangeSet("inner");
    long timestamp = -1;
    vcs.createFile("dir/two", null, timestamp, false);
    vcs.endChangeSet("outer");

    List<Revision> rr = vcs.getRevisionsFor("dir");
    assertEquals(1, rr.size());
    assertEquals("outer", rr.get(0).getCauseChangeName());
  }

  @Test
  public void testIgnoringEmptyChangeSets() {
    vcs.beginChangeSet();
    vcs.createDirectory("dir");
    vcs.endChangeSet(null);

    assertEquals(1, vcs.getChangeList().getChanges().size());

    vcs.beginChangeSet();
    vcs.endChangeSet(null);

    assertEquals(1, vcs.getChangeList().getChanges().size());
  }
}