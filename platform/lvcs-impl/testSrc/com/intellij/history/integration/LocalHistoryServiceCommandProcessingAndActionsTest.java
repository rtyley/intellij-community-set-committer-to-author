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

package com.intellij.history.integration;

import com.intellij.history.core.revisions.Revision;
import com.intellij.history.LocalHistoryAction;
import org.junit.Test;

import java.util.List;

public class LocalHistoryServiceCommandProcessingAndActionsTest extends LocalHistoryServiceTestCase {
  @Test
  public void testActions() {
    TestVirtualFile dir = new TestVirtualFile("dir");
    fileManager.fireFileCreated(dir);

    TestVirtualFile one = new TestVirtualFile("one", null, -1);
    TestVirtualFile two = new TestVirtualFile("two", null, -1);
    dir.addChild(one);
    dir.addChild(two);

    LocalHistoryAction a = service.startAction("name");
    fileManager.fireFileCreated(one);
    fileManager.fireFileCreated(two);
    a.finish();

    assertTrue(vcs.hasEntry("dir/one"));
    assertTrue(vcs.hasEntry("dir/two"));

    List<Revision> rr = vcs.getRevisionsFor("dir");
    assertEquals(2, rr.size());
    assertEquals("name", rr.get(0).getCauseChangeName());
  }

  @Test
  public void testProcessingCommand() {
    commandProcessor.executeCommand(new Runnable() {
      public void run() {
        fileManager.fireFileCreated(new TestVirtualFile("file", "abc", -1));
        fileManager.fireContentChanged(new TestVirtualFile("file", "def", -1));
      }
    }, "command", null);

    assertTrue(vcs.hasEntry("file"));
    assertEquals(c("def"), vcs.getEntry("file").getContent());

    List<Revision> rr = vcs.getRevisionsFor("file");
    assertEquals(1, rr.size());
    assertEquals("command", rr.get(0).getCauseChangeName());
  }

  @Test
  public void testUnregisteringCommandListenerOnShutdown() {
    assertTrue(commandProcessor.hasListener());
    service.shutdown();
    assertFalse(commandProcessor.hasListener());
  }
}