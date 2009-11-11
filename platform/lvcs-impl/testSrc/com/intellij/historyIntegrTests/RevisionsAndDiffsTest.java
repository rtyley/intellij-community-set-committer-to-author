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

package com.intellij.historyIntegrTests;


import com.intellij.history.Clock;
import com.intellij.history.LocalHistory;
import com.intellij.history.core.revisions.Difference;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.core.tree.Entry;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.List;

public class RevisionsAndDiffsTest extends IntegrationTestCase {
  public void testRevisions() throws Exception {
    VirtualFile f = createFile("file.txt", "old");
    setContent(f, "new");

    List<Revision> rr = getRevisionsFor(f);
    assertEquals(2, rr.size());
    assertContent("new", rr.get(0).getEntry());
    assertContent("old", rr.get(1).getEntry());
  }

  public void testNamedAndUnnamedCauseActions() throws Exception {
    getVcs().beginChangeSet();
    VirtualFile f = createFile("file.txt");
    getVcs().endChangeSet("name");

    setContent(f, "foo");

    List<Revision> rr = getRevisionsFor(f);
    assertEquals(2, rr.size());

    assertNull(rr.get(0).getChangeSetName());
    assertEquals("name", rr.get(1).getChangeSetName());
  }

  public void testIncludingCurrentVersionIntoRevisionsAfterPurge() throws Exception {
    Clock.setCurrentTimestamp(10);
    VirtualFile f = createFile("file.txt", "content");
    getVcs().getChangeListInTests().purgeObsolete(0);

    List<Revision> rr = getRevisionsFor(f);
    assertEquals(1, rr.size());

    Revision r = rr.get(0);
    assertNull(r.getLabel());
    assertNull(r.getChangeSetName());
    assertEquals(f.getTimeStamp(), r.getTimestamp());

    Entry e = r.getEntry();
    assertEquals("file.txt", e.getName());
    assertContent("content", e);
  }

  public void testCurrentRevisionForDirectoryAfterPurge() throws Exception {
    Clock.setCurrentTimestamp(10);
    VirtualFile f = createDirectory("dir");
    getVcs().getChangeListInTests().purgeObsolete(0);

    List<Revision> rr = getRevisionsFor(f);
    assertEquals(1, rr.size());

    Revision r = rr.get(0);
    assertNull(r.getLabel());
    assertNull(r.getChangeSetName());
    assertEquals(-1, r.getTimestamp()); // directory has no timestamp

    Entry e = r.getEntry();
    assertEquals("dir", e.getName());
  }

  public void testIncludingVersionBeforeFirstChangeAfterPurge() throws IOException {
    Clock.setCurrentTimestamp(10);
    VirtualFile f = createFile("file.txt", "one");
    Clock.setCurrentTimestamp(20);
    setContent(f, "two");

    getVcs().getChangeListInTests().purgeObsolete(5);

    List<Revision> rr = getRevisionsFor(f);
    assertEquals(2, rr.size());

    assertContent("two", rr.get(0).getEntry());
    assertContent("one", rr.get(1).getEntry());
  }

  public void testDoesNotIncludeRevisionsForAnotherEntries() throws IOException {
    getVcs().beginChangeSet();
    createFile("file1.txt");
    getVcs().endChangeSet("1");

    getVcs().beginChangeSet();
    VirtualFile f2 = createFile("file2.txt");
    getVcs().endChangeSet("2");

    List<Revision> rr = getRevisionsFor(f2);
    assertEquals(1, rr.size());
    assertEquals("2", rr.get(0).getChangeSetName());
  }

  public void testRevisionsTimestamp() throws IOException {
    Clock.setCurrentTimestamp(10);
    VirtualFile f = createFile("file1.txt");

    Clock.setCurrentTimestamp(20);
    setContent(f, "a");

    Clock.setCurrentTimestamp(30);
    setContent(f, "b");

    List<Revision> rr = getRevisionsFor(f);
    assertEquals(30L, rr.get(0).getTimestamp());
    assertEquals(20L, rr.get(1).getTimestamp());
    assertEquals(10L, rr.get(2).getTimestamp());
  }

  public void testTimestampForCurrentRevisionAfterPurgeFromCurrentTimestamp() throws IOException {
    VirtualFile f = createFile("file.txt");
    getVcs().getChangeListInTests().purgeObsolete(0);

    assertEquals(f.getTimeStamp(), getRevisionsFor(f).get(0).getTimestamp());
  }

  public void testTimestampForLastRevisionAfterPurge() throws IOException {
    Clock.setCurrentTimestamp(10);
    VirtualFile f = createFile("file1.txt");

    Clock.setCurrentTimestamp(20);
    setContent(f, "a");

    Clock.setCurrentTimestamp(30);
    setContent(f, "b");

    getVcs().getChangeListInTests().purgeObsolete(15);

    List<Revision> rr = getRevisionsFor(f);
    assertEquals(30L, rr.get(0).getTimestamp());
    assertEquals(20L, rr.get(1).getTimestamp());
    assertEquals(20L, rr.get(2).getTimestamp());
  }

  public void testRevisionsForFileCreatedWithSameNameAsDeletedOne() throws IOException {
    VirtualFile f = createFile("file.txt", "old");
    f.delete(this);
    f = createFile("file.txt", "new");

    List<Revision> rr = getRevisionsFor(f);
    assertEquals(2, rr.size());

    Entry e = rr.get(0).getEntry();
    assertEquals("file.txt", e.getName());
    assertContent("new", e);

    e = rr.get(1).getEntry();
    assertEquals("file.txt", e.getName());
    assertContent("old", e);
  }

  public void testRevisionForDirectoryWithTheSameNameAsDeletedOne() throws IOException {
    VirtualFile dir = createDirectory("dir");
    dir.delete(this);
    dir = createDirectory("dir");

    List<Revision> rr = getRevisionsFor(dir);
    assertEquals(2, rr.size());
  }

  public void testRevisionForRestoredDirectoryWithRestoreChildren() throws IOException {
    VirtualFile dir = createDirectory("dir");
    createFile("dir/f.txt");
    dir.delete(this);

    getVcs().beginChangeSet();
    dir = createDirectory("dir");
    VirtualFile f = createFile("dir/f.txt");
    getVcs().endChangeSet(null);

    List<Revision> rr = getRevisionsFor(dir);
    assertEquals(3, rr.size());
    assertEquals(1, rr.get(0).getEntry().getChildren().size());
    assertEquals(1, rr.get(1).getEntry().getChildren().size());
    assertEquals(0, rr.get(2).getEntry().getChildren().size());

    assertEquals(2, getRevisionsFor(f).size());
  }

  public void testRevisionsForFileThatWasCreatedAndDeletedInOneChangeSet() throws IOException {
    getVcs().beginChangeSet();
    VirtualFile f = createFile("f.txt");
    getVcs().endChangeSet("1");
    f.delete(this);

    getVcs().beginChangeSet();
    f = createFile("f.txt");
    f.delete(this);
    getVcs().endChangeSet("2");

    getVcs().beginChangeSet();
    f = createFile("f.txt");
    getVcs().endChangeSet("3");

    getVcs().beginChangeSet();
    f.delete(this);
    f = createFile("f.txt");
    getVcs().endChangeSet("4");

    List<Revision> rr = getRevisionsFor(f);
    assertEquals(3, rr.size());
    assertEquals("4", rr.get(0).getChangeSetName());
    assertEquals("3", rr.get(1).getChangeSetName());
    assertEquals("1", rr.get(2).getChangeSetName());
  }

  public void testRevisionsForFileCreatedInPlaceOfRenamedOne() throws IOException {
    VirtualFile f = createFile("file1.txt", "content1");
    f.rename("file1", "file2.txt");
    VirtualFile ff = createFile("file1.txt", "content2");

    List<Revision> rr = getRevisionsFor(ff);
    assertEquals(1, rr.size());

    Entry e = rr.get(0).getEntry();
    assertEquals("file1.txt", e.getName());
    assertContent("content2", e);

    rr = getRevisionsFor(f);
    assertEquals(2, rr.size());

    e = rr.get(0).getEntry();
    assertEquals("file2.txt", e.getName());
    assertContent("content1", e);

    e = rr.get(1).getEntry();
    assertEquals("file1.txt", e.getName());
    assertContent("content1", e);
  }

  public void testRevisionsIfSomeFilesWereDeletedDuringChangeSet() throws IOException {
    VirtualFile dir = createDirectory("dir");
    VirtualFile f = createFile("dir/f.txt");
    getVcs().beginChangeSet();
    f.delete(this);

    List<Revision> rr = getRevisionsFor(dir);
    assertEquals(3, rr.size());
  }

  public void testGettingEntryFromRevisionInRenamedDir() throws IOException {
    VirtualFile dir = createDirectory("dir");
    VirtualFile f = createFile("dir/file.txt");
    dir.rename("dir", "newDir");
    setContent(f, "xxx");

    List<Revision> rr = getRevisionsFor(f);
    assertEquals(3, rr.size());

    assertEquals(myRoot.getPath() + "/newDir/file.txt", rr.get(0).getEntry().getPath());
    assertEquals(myRoot.getPath() + "/newDir/file.txt", rr.get(1).getEntry().getPath());
    assertEquals(myRoot.getPath() + "/dir/file.txt", rr.get(2).getEntry().getPath());
  }

  public void testGettingDifferenceBetweenRevisions() throws IOException {
    VirtualFile f = createFile("file.txt", "one");
    setContent(f, "two");

    List<Revision> rr = getRevisionsFor(f);

    Revision recent = rr.get(0);
    Revision prev = rr.get(1);

    List<Difference> dd = prev.getDifferencesWith(recent);
    assertEquals(1, dd.size());

    Difference d = dd.get(0);
    assertContent("one", d.getLeft());
    assertContent("two", d.getRight());
  }

  public void testDifferenceForDirectory() throws IOException {
    VirtualFile dir = createDirectory("dir");
    VirtualFile f = createFile("dir/file.txt");

    List<Revision> rr = getRevisionsFor(dir);
    assertEquals(2, rr.size());

    Revision recent = rr.get(0);
    Revision prev = rr.get(1);

    List<Difference> dd = prev.getDifferencesWith(recent);
    assertEquals(1, dd.size());

    Difference d = dd.get(0);
    assertNull(d.getLeft());
    assertEquals("file.txt", d.getRight().getName());
  }

  public void testNoDifferenceForDirectoryWithEqualContents() throws IOException {
    VirtualFile dir = createDirectory("dir");
    VirtualFile f = createFile("dir/file.txt");
    f.delete(this);

    List<Revision> rr = getRevisionsFor(dir);

    assertTrue(rr.get(0).getDifferencesWith(rr.get(2)).isEmpty());
  }

  public void testDoesNotIncludeNotModifiedDifferences() throws IOException {
    getVcs().beginChangeSet();
    VirtualFile dir = createDirectory("dir1");
    createFile("dir1/dir2/file.txt");
    createDirectory("dir1/dir3");
    getVcs().endChangeSet(null);

    createFile("dir1/dir3/file.txt");

    List<Revision> rr = getRevisionsFor(dir);
    Revision recent = rr.get(0);
    Revision prev = rr.get(1);

    List<Difference> dd = prev.getDifferencesWith(recent);
    assertEquals(1, dd.size());

    Difference d = dd.get(0);
    assertNull(d.getLeft());
    assertEquals(myRoot.getPath() + "/dir1/dir3/file.txt", d.getRight().getPath());
  }

  public void testFilteredRevisionsDoNotContainLabels() throws Exception {
    createFile("foo.txt");
    LocalHistory.getInstance().putSystemLabel(myProject, "1", -1);
    createFile("bar.txt");
    LocalHistory.getInstance().putSystemLabel(myProject, "2", -1);

    assertEquals(5, getRevisionsFor(myRoot).size());
    assertEquals(3, getRevisionsFor(myRoot, "*.txt").size());
  }

  public void testFilteredRevisionsIfNothingFound() throws Exception {
    createFile("foo.txt");
    assertEquals(2, getRevisionsFor(myRoot).size());
    assertEquals(1, getRevisionsFor(myRoot, "xxx").size());
  }

  public void testDoNotIncludeLabelsBeforeFirstRevision() throws Exception {
    LocalHistory.getInstance().putSystemLabel(myProject, "1", -1);
    VirtualFile f = createFile("foo.txt");
    LocalHistory.getInstance().putSystemLabel(myProject, "2", -1);
    assertEquals(2, getRevisionsFor(f).size());
  }
  
  public void testDoNotIncludeLabelsWhenFileDidNotExist() throws Exception {
    VirtualFile f = createFile("foo.txt");
    LocalHistory.getInstance().putSystemLabel(myProject, "1", -1);
    f.delete(this);
    LocalHistory.getInstance().putSystemLabel(myProject, "2", -1);
    f = createFile("foo.txt");
    LocalHistory.getInstance().putSystemLabel(myProject, "3", -1);

    assertEquals(4, getRevisionsFor(f).size());
  }
}