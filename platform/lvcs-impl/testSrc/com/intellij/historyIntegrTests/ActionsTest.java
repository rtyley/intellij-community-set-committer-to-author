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


import com.intellij.history.core.revisions.Revision;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.history.utils.RunnableAdapter;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.List;

public class ActionsTest extends IntegrationTestCase {
  public void testActions() throws Exception {
    VirtualFile f = root.createChildData(null, "f.txt");

    f.setBinaryContent(new byte[]{0});
    assertEquals(0, getVcsContentOf(f)[0]);

    setDocumentTextFor(f, new byte[]{1});

    assertEquals(0, getVcsContentOf(f)[0]);

    LocalHistoryAction a = LocalHistory.startAction(myProject, "name");
    assertEquals(1, getVcsContentOf(f)[0]);

    setDocumentTextFor(f, new byte[]{2});

    a.finish();
    assertEquals(2, getVcsContentOf(f)[0]);

    List<Revision> rr = getVcsRevisionsFor(f);
    assertEquals(4, rr.size());
    assertEquals("name", rr.get(0).getCauseChangeName());
  }

  public void testActionInsideCommand() throws Exception {
    // This is very important test. Mostly all actions are performed
    // inside surrounding command. Therefore we have to correctly
    // handle such situation.
    final VirtualFile f = root.createChildData(null, "f.txt");
    f.setBinaryContent(new byte[]{0});
    setDocumentTextFor(f, new byte[]{1});

    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        LocalHistoryAction a = LocalHistory.startAction(myProject, "action");
        setDocumentTextFor(f, new byte[]{2});
        a.finish();
      }
    }, "command", null);

    List<Revision> rr = getVcsRevisionsFor(f);
    assertEquals(4, rr.size());
    assertEquals("command", rr.get(0).getCauseChangeName());

    assertEquals(2, contentOf(rr.get(0))[0]);
    assertEquals(1, contentOf(rr.get(1))[0]);
    assertEquals(0, contentOf(rr.get(2))[0]);
    assertTrue(contentOf(rr.get(3)).length == 0);
  }

  public void testActionInsideCommandSurroundedWithSomeChanges() throws Exception {
    // see testActionInsideCommand comment
    final VirtualFile f = root.createChildData(null, "f.txt");

    CommandProcessor.getInstance().executeCommand(myProject, new RunnableAdapter() {
      @Override
      public void doRun() throws IOException {
        f.setBinaryContent(new byte[]{0});
        setDocumentTextFor(f, new byte[]{1});

        LocalHistoryAction a = LocalHistory.startAction(myProject, "action");
        setDocumentTextFor(f, new byte[]{2});
        a.finish();

        saveDocument(f);
        f.setBinaryContent(new byte[]{3});
      }
    }, "command", null);

    List<Revision> rr = getVcsRevisionsFor(f);
    assertEquals(3, rr.size());

    assertEquals(3, contentOf(rr.get(0))[0]);
    assertEquals(1, contentOf(rr.get(1))[0]);
    assertTrue(contentOf(rr.get(2)).length == 0);

    assertEquals("command", rr.get(0).getCauseChangeName());
    assertNull(rr.get(1).getCauseChangeName());
    assertNull(rr.get(2).getCauseChangeName());
  }

  private void saveDocument(VirtualFile f) {
    FileDocumentManager dm = FileDocumentManager.getInstance();
    dm.saveDocument(dm.getDocument(f));
  }

  private byte[] contentOf(Revision r) {
    return r.getEntry().getContent().getBytes();
  }
}