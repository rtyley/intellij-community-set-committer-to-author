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

import com.intellij.history.Clock;
import com.intellij.history.core.changes.*;
import com.intellij.history.core.storage.ByteContent;
import com.intellij.history.core.storage.Content;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.TestVirtualFile;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public abstract class LocalVcsTestCase extends Assert {
  private Locale myDefaultLocale;
  private int myCurrentEntryId = 1;

  @Before
  public void setUpEnvironment() {
    myDefaultLocale = Locale.getDefault();
    Locale.setDefault(new Locale("ru", "RU"));
  }

  @After
  public void restoreEnvironment() {
    Locale.setDefault(myDefaultLocale);
  }

  protected int getNextId() {
    return myCurrentEntryId++;
  }

  protected static byte[] b(String s) {
    return s.getBytes();
  }

  protected static Content c(String data) {
    return new ByteContent(b(data));
  }

  public static ContentFactory cf(String data) {
    return createContentFactory(b(data));
  }

  public static ContentFactory bigContentFactory() {
    return createContentFactory(new byte[ContentFactory.MAX_CONTENT_LENGTH + 1]);
  }

  private static ContentFactory createContentFactory(final byte[] bytes) {
    return new ContentFactory() {
      @Override
      public byte[] getBytes() {
        return bytes;
      }

      @Override
      public long getLength() {
        return bytes.length;
      }
    };
  }

  public static <T> T[] array(T... objects) {
    return objects;
  }

  public static <T> List<T> list(T... objects) {
    return Arrays.asList(objects);
  }

  protected static IdPath idp(int... parts) {
    return new IdPath(parts);
  }

  protected static ChangeSet cs(Change... changes) {
    return cs(null, changes);
  }

  protected static ChangeSet cs(String name, Change... changes) {
    return cs(0, name, changes);
  }

  protected static ChangeSet cs(long timestamp, Change... changes) {
    return cs(timestamp, null, changes);
  }

  protected static ChangeSet cs(long timestamp, String name, Change... changes) {
    return new ChangeSet(timestamp, name, Arrays.asList(changes));
  }

  protected static void createFile(Entry r, int id, String path, Content c, long timestamp, boolean isReadOnly) {
    new CreateFileChange(id, path, c, timestamp, isReadOnly).applyTo(r);
  }

  protected static void createFile(Entry r, int id, String path, Content c, long timestamp) {
    createFile(r, id, path, c, timestamp, false);
  }

  protected void createFile(Entry r, String path, Content c, long timestamp) {
    createFile(r, getNextId(), path, c, timestamp);
  }

  protected static void createDirectory(Entry r, int id, String path) {
    new CreateDirectoryChange(id, path).applyTo(r);
  }

  protected void createDirectory(Entry r, String path) {
    createDirectory(r, getNextId(), path);
  }

  protected static void changeFileContent(Entry r, String path, Content c, long timestamp) {
    new ContentChange(path, c, timestamp).applyTo(r);
  }

  protected static void rename(Entry r, String path, String newName) {
    new RenameChange(path, newName).applyTo(r);
  }

  protected static void move(Entry r, String path, String newParent) {
    new MoveChange(path, newParent).applyTo(r);
  }

  protected static void delete(Entry r, String path) {
    new DeleteChange(path).applyTo(r);
  }

  protected static void setCurrentTimestamp(long t) {
    Clock.setCurrentTimestamp(t);
  }

  protected static void assertEquals(Object[] expected, Collection actual) {
    assertArrayEquals(expected, actual.toArray());
  }

  protected static TestVirtualFile testDir(String name) {
    return new TestVirtualFile(name);
  }

  protected static TestVirtualFile testFile(String name) {
    return testFile(name, "");
  }

  protected static TestVirtualFile testFile(String name, String content) {
    return testFile(name, content, -1);
  }

  protected static TestVirtualFile testFile(String name, String content, long timestamp) {
    return testFile(name, content, timestamp, false);
  }

  protected static TestVirtualFile testFile(String name, String content, long timestamp, boolean isReadOnly) {
    return new TestVirtualFile(name, content, timestamp, isReadOnly);
  }
}
