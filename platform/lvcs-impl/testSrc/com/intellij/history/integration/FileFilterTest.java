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

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.roots.FileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import static org.easymock.classextension.EasyMock.*;
import org.jetbrains.annotations.NotNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class FileFilterTest {
  private TestVirtualFile f1 = new TestVirtualFile("", null, -1);
  private TestVirtualFile f2 = new TestVirtualFile("", null, -1);

  private final FileType binary = createFileType(true);
  private final FileType nonBinary = createFileType(false);

  private final FileIndex fi = createMock(FileIndex.class);
  private final FileTypeManager tm = createMock(FileTypeManager.class);

  @Test
  public void testIsAllowedAndUnderContentRoot() {
    final boolean[] values = new boolean[2];
    FileFilter f = new FileFilter(null, null) {
      @Override
      public boolean isUnderContentRoot(VirtualFile f) {
        return values[0];
      }

      @Override
      public boolean isAllowed(VirtualFile f) {
        return values[1];
      }
    };

    values[0] = true;
    values[1] = true;
    assertTrue(f.isAllowedAndUnderContentRoot(null));

    values[0] = false;
    values[1] = true;
    assertFalse(f.isAllowedAndUnderContentRoot(null));

    values[0] = true;
    values[1] = false;
    assertFalse(f.isAllowedAndUnderContentRoot(null));
  }

  @Test
  public void testFilteringFileNotUnderContentRoot() {
    expect(fi.isInContent(f1)).andReturn(true);
    expect(fi.isInContent(f2)).andReturn(false);
    replay(fi);

    FileFilter f = new FileFilter(fi, tm);

    assertTrue(f.isUnderContentRoot(f1));
    assertFalse(f.isUnderContentRoot(f2));
  }

  @Test
  public void testFilteringFileNotUnderLocalFileSystemButUnderContentRoot() {
    TestVirtualFile file = new TestVirtualFile("file") {
      @Override
      @NotNull
      public VirtualFileSystem getFileSystem() {
        return createMock(VirtualFileSystem.class);
      }
    };

    expect(fi.isInContent(file)).andReturn(true);
    replay(fi);
    FileFilter f = new FileFilter(fi, tm);
    assertFalse(f.isUnderContentRoot(file));
  }

  @Test
  public void testFilteringFileOfUndesiredType() {
    expect(tm.isFileIgnored((String)anyObject())).andStubReturn(false);

    expect(tm.getFileTypeByFile(f1)).andStubReturn(nonBinary);
    expect(tm.getFileTypeByFile(f2)).andStubReturn(binary);
    replay(tm);

    FileFilter f = new FileFilter(fi, tm);

    assertTrue(f.isAllowed(f1));
    assertFalse(f.isAllowed(f2));
  }

  @Test
  public void testFilteringIgnoredFiles() {
    f1 = new TestVirtualFile("allowed", null, -1);
    f2 = new TestVirtualFile("filtered", null, -1);

    expect(tm.isFileIgnored("allowed")).andReturn(false);
    expect(tm.isFileIgnored("filtered")).andReturn(true);
    expect(tm.getFileTypeByFile((VirtualFile)anyObject())).andStubReturn(nonBinary);
    replay(tm);

    FileFilter f = new FileFilter(fi, tm);

    assertTrue(f.isAllowed(f1));
    assertFalse(f.isAllowed(f2));
  }

  @Test
  public void testFilteringIgnoredDirectories() {
    f1 = new TestVirtualFile("allowed");
    f2 = new TestVirtualFile("filtered");

    expect(tm.isFileIgnored("allowed")).andReturn(false);
    expect(tm.isFileIgnored("filtered")).andReturn(true);
    replay(tm);

    FileFilter f = new FileFilter(fi, tm);

    assertTrue(f.isAllowed(f1));
    assertFalse(f.isAllowed(f2));
  }

  @Test
  public void testDoesNotCheckFileTypeForDirectories() {
    f1 = new TestVirtualFile("dir");

    expect(tm.isFileIgnored("dir")).andReturn(false);
    replay(tm);
    FileFilter f = new FileFilter(fi, tm);

    assertTrue(f.isAllowed(f1));
    verify(tm);
  }

  private FileType createFileType(boolean isBinary) {
    FileType t = createMock(FileType.class);
    expect(t.isBinary()).andStubReturn(isBinary);
    replay(t);
    return t;
  }
}
