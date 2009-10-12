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

package com.intellij.historyPerfTests;

import com.intellij.history.core.LocalVcs;
import com.intellij.history.core.storage.Storage;
import com.intellij.history.integration.TestVirtualFile;
import org.junit.After;
import org.junit.Before;

public class LocalVcsPerformanceTestCase extends PerformanceTestCase {
  protected LocalVcs vcs;
  private Storage storage;
  protected static final long VCS_ENTRIES_TIMESTAMP = 1L;

  @Before
  public void initVcs() {
    closeStorage();
    storage = new Storage(tempDir);
    final Storage s = storage;
    vcs = new LocalVcs(s);
  }

  @After
  public void closeStorage() {
    if (storage != null) {
      storage.close();
      storage = null;
    }
  }

  protected void buildVcsTree() {
    vcs.beginChangeSet();
    vcs.createDirectory("root");
    createChildren("root", 5);
    vcs.endChangeSet(null);
  }

  protected void createChildren(String parent, int depth) {
    if (depth == 0) return;

    for (int i = 0; i < 10; i++) {
      String filePath = parent + "/file" + i;
      long timestamp = VCS_ENTRIES_TIMESTAMP;
      vcs.createFile(filePath, cf(String.valueOf(timestamp + i)), timestamp, false);

      String dirPath = parent + "/dir" + i;
      vcs.createDirectory(dirPath);
      createChildren(dirPath, depth - 1);
    }
  }

  protected TestVirtualFile buildVFSTree(long timestamp) {
    TestVirtualFile root = new TestVirtualFile("root");
    createVFSChildren(root, timestamp, 5);
    return root;
  }

  private void createVFSChildren(TestVirtualFile parent, long timestamp, int countdown) {
    if (countdown == 0) return;

    for (int i = 0; i < 10; i++) {
      parent.addChild(new TestVirtualFile("file" + i, String.valueOf(timestamp + i), timestamp));

      TestVirtualFile dir = new TestVirtualFile("dir" + i);
      parent.addChild(dir);
      createVFSChildren(dir, timestamp, countdown - 1);
    }
  }
}
