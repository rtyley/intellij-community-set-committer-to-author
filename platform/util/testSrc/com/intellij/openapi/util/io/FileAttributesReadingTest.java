/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.util.io;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.win32.FileInfo;
import com.intellij.openapi.util.io.win32.IdeaWin32;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;

import static com.intellij.openapi.util.io.IoTestUtil.assertTimestampsEqual;
import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

public class FileAttributesReadingTest {
  private final byte[] myTestData = new byte[]{'t', 'e', 's', 't'};
  private File myTempDirectory;

  @BeforeClass
  public static void checkMediator() throws Exception {
    final String expectedName = SystemInfo.isWindows ? "IdeaWin32" : "JnaUnix";
    assertEquals(expectedName, FileSystemUtil.getMediatorName());
  }

  @Before
  public void setUp() throws Exception {
    myTempDirectory = FileUtil.createTempDirectory(getClass().getSimpleName() + ".", ".tmp");
  }

  @After
  public void tearDown() throws Exception {
    if (myTempDirectory != null) {
      FileUtil.delete(myTempDirectory);
    }
  }

  @Test
  public void missingFile() throws Exception {
    final File file = FileUtil.createTempFile(myTempDirectory, "test.", ".txt", false);

    final FileAttributes attributes = FileSystemUtil.getAttributes(file);
    assertNull(attributes);

    final String target = FileSystemUtil.resolveSymLink(file);
    assertNull(target);
  }

  @Test
  public void regularFile() throws Exception {
    final File file = FileUtil.createTempFile(myTempDirectory, "test.", ".txt");
    FileUtil.writeToFile(file, myTestData);

    assertFileAttributes(file);

    final String target = FileSystemUtil.resolveSymLink(file);
    assertEquals(file.getPath(), target);
  }

  @Test
  public void directory() throws Exception {
    final File file = FileUtil.createTempDirectory(myTempDirectory, "test.", ".tmp");

    final FileAttributes attributes = getAttributes(file);
    assertEquals(FileAttributes.Type.DIRECTORY, attributes.type);
    assertEquals(0, attributes.flags);
    assertEquals(file.length(), attributes.length);
    assertTimestampsEqual(file.lastModified(), attributes.lastModified);
    assertTrue(attributes.isWritable());
    if (SystemInfo.isWindows) {
      assertDirectoriesEqual(file);
    }

    final String target = FileSystemUtil.resolveSymLink(file);
    assertEquals(file.getPath(), target);
  }

  @Test
  public void root() throws Exception {
    final File file = new File(SystemInfo.isWindows ? "C:\\" : "/");

    final FileAttributes attributes = getAttributes(file);
    assertEquals(FileAttributes.Type.DIRECTORY, attributes.type);
    if (SystemInfo.isWindows) {
      assertDirectoriesEqual(file);
    }
  }

  @Test
  public void badNames() throws Exception {
    final File file = FileUtil.createTempFile(myTempDirectory, "test.", ".txt");
    FileUtil.writeToFile(file, myTestData);

    assertFileAttributes(new File(file.getPath() + StringUtil.repeat(File.separator, 3)));
    assertFileAttributes(new File(file.getPath().replace(File.separator, StringUtil.repeat(File.separator, 3))));
    assertFileAttributes(new File(file.getPath().replace(File.separator, File.separator + "." + File.separator)));
    assertFileAttributes(new File(myTempDirectory, File.separator + ".." + File.separator + myTempDirectory.getName() + File.separator + file.getName()));

    if (SystemInfo.isUnix) {
      final File backSlashFile = FileUtil.createTempFile(myTempDirectory, "test\\", "\\txt");
      FileUtil.writeToFile(backSlashFile, myTestData);
      assertFileAttributes(backSlashFile);
    }
  }

  @Test
  public void special() throws Exception {
    assumeTrue(SystemInfo.isUnix);
    final File file = new File("/dev/null");

    final FileAttributes attributes = getAttributes(file);
    assertEquals(FileAttributes.Type.SPECIAL, attributes.type);
    assertEquals(0, attributes.flags);
    assertEquals(0, attributes.length);
    assertTrue(attributes.isWritable());

    final String target = FileSystemUtil.resolveSymLink(file);
    assertEquals(file.getPath(), target);
  }

  @Test
  public void linkToFile() throws Exception {
    assumeTrue(SystemInfo.areSymLinksSupported);

    final File file = FileUtil.createTempFile(myTempDirectory, "test.", ".txt");
    FileUtil.writeToFile(file, myTestData);
    assertTrue(file.setLastModified(file.lastModified() - 5000));
    assertTrue(file.setWritable(false, false));
    final File link = IoTestUtil.createSymLink(file.getPath(), new File(myTempDirectory, "link").getPath());

    final FileAttributes attributes = getAttributes(link);
    assertEquals(FileAttributes.Type.FILE, attributes.type);
    assertEquals(FileAttributes.SYM_LINK, attributes.flags);
    assertEquals(myTestData.length, attributes.length);
    assertTimestampsEqual(file.lastModified(), attributes.lastModified);
    assertFalse(attributes.isWritable());

    final String target = FileSystemUtil.resolveSymLink(link);
    assertEquals(file.getPath(), target);
  }

  @Test
  public void doubleLink() throws Exception {
    assumeTrue(SystemInfo.areSymLinksSupported);

    final File file = FileUtil.createTempFile(myTempDirectory, "test.", ".txt");
    FileUtil.writeToFile(file, myTestData);
    assertTrue(file.setLastModified(file.lastModified() - 5000));
    assertTrue(file.setWritable(false, false));
    final File link1 = IoTestUtil.createSymLink(file.getPath(), new File(myTempDirectory, "link1").getPath());
    final File link2 = IoTestUtil.createSymLink(link1.getPath(), new File(myTempDirectory, "link2").getPath());

    final FileAttributes attributes = getAttributes(link2);
    assertEquals(FileAttributes.Type.FILE, attributes.type);
    assertEquals(FileAttributes.SYM_LINK, attributes.flags);
    assertEquals(myTestData.length, attributes.length);
    assertTimestampsEqual(file.lastModified(), attributes.lastModified);
    assertFalse(attributes.isWritable());

    final String target = FileSystemUtil.resolveSymLink(link2);
    assertEquals(file.getPath(), target);
  }

  @Test
  public void linkToDirectory() throws Exception {
    assumeTrue(SystemInfo.areSymLinksSupported);

    final File file = FileUtil.createTempDirectory(myTempDirectory, "test.", ".tmp");
    if (SystemInfo.isUnix) assertTrue(file.setWritable(false, false));
    assertTrue(file.setLastModified(file.lastModified() - 5000));
    final File link = IoTestUtil.createSymLink(file.getPath(), new File(myTempDirectory, "link").getPath());

    final FileAttributes attributes = getAttributes(link);
    assertEquals(FileAttributes.Type.DIRECTORY, attributes.type);
    assertEquals(FileAttributes.SYM_LINK, attributes.flags);
    assertEquals(file.length(), attributes.length);
    assertTimestampsEqual(file.lastModified(), attributes.lastModified);
    if (SystemInfo.isUnix) assertFalse(attributes.isWritable());

    final String target = FileSystemUtil.resolveSymLink(link);
    assertEquals(file.getPath(), target);
  }

  @Test
  public void missingLink() throws Exception {
    assumeTrue(SystemInfo.areSymLinksSupported);

    final File file = FileUtil.createTempFile(myTempDirectory, "test.", ".txt", false);
    final File link = IoTestUtil.createSymLink(file.getPath(), new File(myTempDirectory, "link").getPath(), false);

    final FileAttributes attributes = getAttributes(link);
    assertNull(attributes.type);
    assertEquals(FileAttributes.SYM_LINK, attributes.flags);
    assertEquals(0, attributes.length);

    final String target = FileSystemUtil.resolveSymLink(link);
    assertNull(target, target);
  }

  @Test
  public void selfLink() throws Exception {
    assumeTrue(SystemInfo.areSymLinksSupported);

    final File dir = FileUtil.createTempDirectory(myTempDirectory, "test.", ".dir");
    final File link = IoTestUtil.createSymLink(dir.getPath(), new File(dir, "link").getPath());

    final FileAttributes attributes = getAttributes(link);
    assertEquals(FileAttributes.Type.DIRECTORY, attributes.type);
    assertEquals(FileAttributes.SYM_LINK, attributes.flags);
    assertTimestampsEqual(dir.lastModified(), attributes.lastModified);

    final String target = FileSystemUtil.resolveSymLink(link);
    assertEquals(dir.getPath(), target);
  }

  @Test
  public void junction() throws Exception {
    assumeTrue(SystemInfo.isWindows);

    final File target = FileUtil.createTempDirectory(myTempDirectory, "temp.", ".dir");
    final File path = FileUtil.createTempFile(myTempDirectory, "junction.", ".dir", false);
    final File junction = IoTestUtil.createJunction(target.getPath(), path.getAbsolutePath());

    FileAttributes attributes = getAttributes(junction);
    assertEquals(FileAttributes.Type.DIRECTORY, attributes.type);
    assertEquals(0, attributes.flags);
    assertTrue(attributes.isWritable());

    final String resolved1 = FileSystemUtil.resolveSymLink(junction);
    assertEquals(target.getPath(), resolved1);

    FileUtil.delete(target);

    attributes = getAttributes(junction);
    assertEquals(FileAttributes.Type.DIRECTORY, attributes.type);
    assertEquals(0, attributes.flags);
    assertTrue(attributes.isWritable());

    final String resolved2 = FileSystemUtil.resolveSymLink(junction);
    assertNull(resolved2, resolved2);
  }

  @Test
  public void wellHiddenFile() throws Exception {
    assumeTrue(SystemInfo.isWindows);
    final File file = new File("C:\\Documents and Settings\\desktop.ini");
    assumeTrue(file.exists());

    final FileAttributes attributes = getAttributes(file, false);
    assertEquals(FileAttributes.Type.FILE, attributes.type);
    assertEquals(FileAttributes.HIDDEN, attributes.flags);
    assertEquals(file.length(), attributes.length);
    assertTimestampsEqual(file.lastModified(), attributes.lastModified);
  }

  @Test
  public void extraLongName() throws Exception {
    final String prefix = StringUtil.repeatSymbol('a', 128) + ".";
    final File dir = FileUtil.createTempDirectory(
      FileUtil.createTempDirectory(
        FileUtil.createTempDirectory(
          FileUtil.createTempDirectory(
            myTempDirectory, prefix, ".dir"),
          prefix, ".dir"),
        prefix, ".dir"),
      prefix, ".dir");
    final File file = FileUtil.createTempFile(dir, prefix, ".txt");
    assertTrue(file.exists());
    FileUtil.writeToFile(file, myTestData);

    assertFileAttributes(file);
    if (SystemInfo.isWindows) {
      assertDirectoriesEqual(dir);
    }

    final String target = FileSystemUtil.resolveSymLink(file);
    assertEquals(file.getPath(), target);
  }

  @Test
  public void subst() throws Exception {
    assumeTrue(SystemInfo.isWindows);

    FileUtil.createTempFile(myTempDirectory, "test.", ".txt");  // just to populate a directory
    final File substRoot = IoTestUtil.createSubst(myTempDirectory.getPath());
    try {
      final FileAttributes attributes = getAttributes(substRoot);
      assertEquals(FileAttributes.Type.DIRECTORY, attributes.type);
      assertDirectoriesEqual(substRoot);

      final File[] children = substRoot.listFiles();
      assertNotNull(children);
      assertEquals(1, children.length);
      final File file = children[0];
      final String target = FileSystemUtil.resolveSymLink(file);
      assertEquals(file.getPath(), target);
    }
    finally {
      IoTestUtil.deleteSubst(substRoot.getPath());
    }
  }

  @Test
  public void hardLink() throws Exception {
    final File target = FileUtil.createTempFile(myTempDirectory, "test.", ".txt");
    final File link = IoTestUtil.createHardLink(target.getPath(), myTempDirectory.getPath() + "/link");

    FileAttributes attributes = getAttributes(link, SystemInfo.areSymLinksSupported);  // ignore XP
    assertEquals(FileAttributes.Type.FILE, attributes.type);
    assertEquals(target.length(), attributes.length);
    assertTimestampsEqual(target.lastModified(), attributes.lastModified);

    FileUtil.writeToFile(target, myTestData);
    assertTrue(target.setLastModified(attributes.lastModified - 5000));
    assertTrue(target.length() > 0);
    assertTimestampsEqual(attributes.lastModified - 5000, target.lastModified());

    attributes = getAttributes(link, SystemInfo.areSymLinksSupported);  // ignore XP
    assertEquals(FileAttributes.Type.FILE, attributes.type);
    assertEquals(target.length(), attributes.length);
    assertTimestampsEqual(target.lastModified(), attributes.lastModified);

    final String resolved = FileSystemUtil.resolveSymLink(link);
    assertEquals(link.getPath(), resolved);
  }

  @NotNull
  private static FileAttributes getAttributes(@NotNull final File file) {
    return getAttributes(file, true);
  }

  @NotNull
  private static FileAttributes getAttributes(@NotNull final File file, final boolean checkList) {
    final FileAttributes attributes = FileSystemUtil.getAttributes(file);
    assertNotNull(attributes);
    System.out.println(attributes + ": " + file);

    if (SystemInfo.isWindows && checkList) {
      final String parent = file.getParent();
      if (parent != null) {
        final FileInfo[] infos = IdeaWin32.getInstance().listChildren(parent);
        assertNotNull(infos);
        for (FileInfo info : infos) {
          if (file.getName().equals(info.getName())) {
            assertEquals(attributes, info.toFileAttributes());
            return attributes;
          }
        }
        fail(file + " not listed");
      }
    }

    return attributes;
  }

  private static void assertFileAttributes(@NotNull final File file) {
    final FileAttributes attributes = getAttributes(file);
    assertEquals(FileAttributes.Type.FILE, attributes.type);
    assertEquals(0, attributes.flags);
    assertEquals(file.length(), attributes.length);
    assertTimestampsEqual(file.lastModified(), attributes.lastModified);
    assertTrue(attributes.isWritable());
  }

  private static void assertDirectoriesEqual(@NotNull final File dir) {
    final String[] list1 = dir.list();
    assertNotNull(list1);
    final FileInfo[] list2 = IdeaWin32.getInstance().listChildren(dir.getPath());
    assertNotNull(list2);
    if (list1.length + 2 != list2.length) {
      assertEquals(Arrays.toString(list1), Arrays.toString(list2));
    }
  }
}
