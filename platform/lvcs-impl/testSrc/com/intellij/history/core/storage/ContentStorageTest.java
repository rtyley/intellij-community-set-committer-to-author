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

package com.intellij.history.core.storage;

import com.intellij.history.core.TempDirTestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

public class ContentStorageTest extends TempDirTestCase {
  private ContentStorage s;

  @Before
  public void setUp() throws Exception {
    s = createStorage();
  }

  private ContentStorage createStorage() throws Exception {
    return new ContentStorage(getStorageFile());
  }

  private File getStorageFile() {
    return new File(tempDir, "storage");
  }

  @After
  public void tearDown() {
    s.close();
  }

  @Test
  public void testStoring() throws Exception {
    byte[] c1 = new byte[]{1};
    byte[] c2 = new byte[]{22};

    int id1 = s.store(c1);
    int id2 = s.store(c2);

    assertArrayEquals(c1, s.load(id1));
    assertArrayEquals(c2, s.load(id2));
  }

  @Test
  public void testStoringBetweenSessions() throws Exception {
    byte[] c = new byte[]{1, 2, 3};

    int id = s.store(c);
    s.close();

    s = createStorage();
    assertArrayEquals(c, s.load(id));
  }

  @Test
  public void testSavingOnClose() throws Exception {
    byte[] c = new byte[]{1, 2, 3};

    int id = s.store(c);
    s.close();

    IContentStorage another = createStorage();
    try {
      assertArrayEquals(c, another.load(id));
    }
    finally {
      another.close();
    }
  }

  @Test
  public void testRemoving() throws Exception {
    int id = s.store(new byte[]{1});
    s.remove(id);
    assertEquals(id, s.store(new byte[]{1}));
  }

  @Test
  public void testThrowingExceptionWhenAskingForInvalidContent() throws BrokenStorageException {
    int id = s.store(new byte[]{1});
    s.remove(id);

    try {
      s.load(id);
      fail();
    }
    catch (BrokenStorageException e) {
    }
  }
}