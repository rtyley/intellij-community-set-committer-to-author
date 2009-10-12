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

package com.intellij.historyIntegrTests.ui;

import com.intellij.history.core.ContentFactory;
import com.intellij.history.integration.ui.models.EntireFileHistoryDialogModel;
import com.intellij.history.integration.ui.models.FileHistoryDialogModel;
import com.intellij.history.integration.ui.models.NullRevisionsProgress;
import com.intellij.history.integration.ui.models.RevisionProcessingProgress;
import com.intellij.history.integration.ui.views.FileHistoryDialog;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.DocumentContent;
import com.intellij.openapi.diff.SimpleContent;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.Date;

public class FileHistoryDialogTest extends LocalHistoryUITestCase {
  public void testDialogWorks() throws IOException {
    VirtualFile file = root.createChildData(null, "f.txt");

    FileHistoryDialog d = new FileHistoryDialog(gateway, file);
    d.close(0);
  }

  public void testTitles() throws IOException {
    long leftTime = new Date(2001, 01, 03, 12, 0).getTime();
    long rightTime = new Date(2002, 02, 04, 14, 0).getTime();

    VirtualFile f = root.createChildData(null, "old.txt");
    f.setBinaryContent("old".getBytes(), -1, leftTime);

    f.rename(null, "new.txt");
    f.setBinaryContent("new".getBytes(), -1, rightTime);

    f.setBinaryContent(new byte[0]); // to create current content to skip.

    FileHistoryDialogModel m = createFileModelAndSelectRevisions(f, 1, 3);
    assertEquals(f.getPath(), m.getDifferenceModel().getTitle());

    assertEquals("03.02.01 12:00 - old.txt", m.getDifferenceModel().getLeftTitle(new NullRevisionsProgress()));
    assertEquals("04.03.02 14:00 - new.txt", m.getDifferenceModel().getRightTitle(new NullRevisionsProgress()));
  }

  public void testTitlesForAnavailableContent() throws IOException {
    long leftTime = new Date(2001, 01, 03, 12, 0).getTime();
    long rightTime = new Date(2002, 02, 04, 14, 0).getTime();

    VirtualFile f = root.createChildData(null, "f.txt");
    f.setBinaryContent(new byte[ContentFactory.MAX_CONTENT_LENGTH + 1], -1, leftTime);
    f.setBinaryContent(new byte[ContentFactory.MAX_CONTENT_LENGTH + 1], -1, rightTime);

    f.setBinaryContent(new byte[0]); // to create current content to skip.

    FileHistoryDialogModel m = createFileModelAndSelectRevisions(f, 1, 2);

    assertEquals("03.02.01 12:00 - f.txt - File content is not available", m.getDifferenceModel().getLeftTitle(new NullRevisionsProgress()));
    assertEquals("04.03.02 14:00 - f.txt - File content is not available", m.getDifferenceModel().getRightTitle(new NullRevisionsProgress()));
  }

  public void testContent() throws IOException {
    VirtualFile f = root.createChildData(null, "f.txt");
    f.setBinaryContent("old".getBytes());
    f.setBinaryContent("new".getBytes());
    f.setBinaryContent("current".getBytes());

    FileHistoryDialogModel m = createFileModelAndSelectRevisions(f, 1, 2);

    assertDiffContents("old", "new", m);
  }

  public void testContentWhenOnlyOneRevisionSelected() throws IOException {
    VirtualFile f = root.createChildData(null, "f.txt");
    f.setBinaryContent("old".getBytes());
    f.setBinaryContent("new".getBytes());

    FileHistoryDialogModel m = createFileModelAndSelectRevisions(f, 1, 1);

    assertDiffContents("old", "new", m);
  }

  public void testContentForCurrentRevision() throws IOException {
    VirtualFile f = root.createChildData(null, "f.txt");
    f.setBinaryContent("old".getBytes());
    f.setBinaryContent("current".getBytes());

    FileHistoryDialogModel m = createFileModelAndSelectRevisions(f, 0, 1);

    assertDiffContents("old", "current", m);
    assertEquals(DocumentContent.class, getRightDiffContent(m).getClass());
  }

  public void testDiffContentIsEmptyForUnavailableCurrent() throws IOException {
    VirtualFile f = root.createChildData(null, "f.txt");
    f.setBinaryContent(new byte[ContentFactory.MAX_CONTENT_LENGTH + 1]);
    f.setBinaryContent(new byte[ContentFactory.MAX_CONTENT_LENGTH + 1]);

    FileHistoryDialogModel m = createFileModelAndSelectRevisions(f, 0, 1);

    assertDiffContents("", "", m);
    assertEquals(SimpleContent.class, getLeftDiffContent(m).getClass());
    assertEquals(SimpleContent.class, getRightDiffContent(m).getClass());
  }

  public void testRevertion() throws Exception {
    VirtualFile dir = root.createChildDirectory(null, "oldDir");
    VirtualFile f = dir.createChildData(null, "old.txt");
    f.rename(null, "new.txt");
    dir.rename(null, "newDir");

    FileHistoryDialogModel m = createFileModelAndSelectRevisions(f, 2, 2);
    m.createReverter().revert();

    assertEquals("old.txt", f.getName());
    assertEquals(f.getParent(), dir);
    assertEquals("newDir", dir.getName());
  }

  public void testChangeRevertion() throws Exception {
    VirtualFile dir = root.createChildDirectory(null, "oldDir");
    VirtualFile f = dir.createChildData(null, "old.txt");
    f.rename(null, "new.txt");
    dir.rename(null, "newDir");

    FileHistoryDialogModel m = createFileModel(f);
    m.selectChanges(1, 1);
    m.createReverter().revert();

    assertEquals("old.txt", f.getName());
    assertEquals("oldDir", dir.getName());
    assertNull(root.findChild("newDir"));
  }

  public void testRevertLabelChange() throws Exception {
    VirtualFile f = root.createChildDirectory(null, "f.txt");
    getVcs().putUserLabel("abc");

    FileHistoryDialogModel m = createFileModel(f);
    m.selectChanges(0, 0);
    m.createReverter().revert();

    assertNotNull(root.findChild("f.txt"));
  }

  private void assertDiffContents(String leftContent, String rightContent, FileHistoryDialogModel m) throws IOException {
    DiffContent left = getLeftDiffContent(m);
    DiffContent right = getRightDiffContent(m);

    assertEquals(leftContent, new String(left.getBytes()));
    assertEquals(rightContent, new String(right.getBytes()));
  }

  private DiffContent getLeftDiffContent(FileHistoryDialogModel m) {
    RevisionProcessingProgress p = new NullRevisionsProgress();
    return m.getDifferenceModel().getLeftDiffContent(p);
  }

  private DiffContent getRightDiffContent(FileHistoryDialogModel m) {
    RevisionProcessingProgress p = new NullRevisionsProgress();
    return m.getDifferenceModel().getRightDiffContent(p);
  }

  private FileHistoryDialogModel createFileModel(VirtualFile f) {
    return new EntireFileHistoryDialogModel(gateway, getVcs(), f);
  }

  private FileHistoryDialogModel createFileModelAndSelectRevisions(VirtualFile f, int first, int second) {
    FileHistoryDialogModel m = createFileModel(f);
    m.selectRevisions(first, second);
    return m;
  }
}
