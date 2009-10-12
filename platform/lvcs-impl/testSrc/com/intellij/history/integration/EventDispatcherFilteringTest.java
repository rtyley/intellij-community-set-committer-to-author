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

import com.intellij.history.core.tree.Entry;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Before;
import org.junit.Test;

public class EventDispatcherFilteringTest extends EventDispatcherTestCase {
  @Override
  @Before
  public void setUp() {
    super.setUp();
    gateway.setFileFilter(new MyFileFilter());
  }

  @Test
  public void testRenamingFileToFilteredOne() {
    long timestamp = -1;
    vcs.createFile("allowed", null, timestamp, false);

    VirtualFile renamed = new TestVirtualFile("filtered", null, -1);
    fireRenamed(renamed, "allowed");

    assertFalse(vcs.hasEntry("allowed"));
    assertFalse(vcs.hasEntry("filtered"));
  }

  @Test
  public void testRenamingFileFromFilteredOne() {
    VirtualFile renamed = new TestVirtualFile("allowed", "content", 123L);
    fireRenamed(renamed, "filtered");

    Entry e = vcs.findEntry("allowed");
    assertNotNull(e);
    assertEquals(c("content"), e.getContent());
    assertEquals(123L, e.getTimestamp());
  }

  @Test
  public void testRenamingFileFromFilteredToFilteredOne() {
    VirtualFile renamed = new TestVirtualFile("filtered2", null, -1);
    fireRenamed(renamed, "filtered1");

    assertFalse(vcs.hasEntry("filtered1"));
    assertFalse(vcs.hasEntry("filtered2"));
  }

  @Test
  public void testRenamingDirectoryFromFilteredOne() {
    VirtualFile renamed = new TestVirtualFile("filtered2", null, -1);
    fireRenamed(renamed, "filtered1");

    assertFalse(vcs.hasEntry("filtered1"));
    assertFalse(vcs.hasEntry("filtered2"));
  }

  private class MyFileFilter extends FileFilter {
    public MyFileFilter() {
      super(null, null);
    }

    @Override
    public boolean isAllowedAndUnderContentRoot(VirtualFile f) {
      return f.getName().equals("allowed");
    }
  }
}