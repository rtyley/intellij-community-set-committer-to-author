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

import com.intellij.history.core.storage.ContentStorage;
import com.intellij.history.utils.RunnableAdapter;
import com.intellij.util.io.storage.Storage;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Ignore
public class ContentStorageTest extends PerformanceTestCase {
  int ITERATIONS_COUNT = 1000;
  int MAX_RECORD_SIZE = 20 * 1024;

  File file;
  ContentStorage s;

  @Before
  public void setUp() throws Exception {
    file = new File(tempDir, "s");
    s = new ContentStorage(file);
  }

  @After
  public void tearDown() {
    s.close();
  }

  @Test
  public void testWriting() throws Exception {
    assertExecutionTime(80, new RunnableAdapter() {
      public void doRun() throws Exception {
        createContentsOfDifferentSize();
      }
    });
  }

  @Test
  public void testReading() throws Exception {
    final List<Integer> cc = createContentsOfDifferentSize();
    assertExecutionTime(50, new RunnableAdapter() {
      public void doRun() throws Exception {
        readContentsRandomly(cc);
      }
    });
  }

  @Test
  public void testDeletion() throws Exception {
    final List<Integer> cc = createContentsOfDifferentSize();
    assertExecutionTime(15, new RunnableAdapter() {
      public void doRun() throws Exception {
        deleteHalfOfContentsRandomly(cc);
      }
    });
  }

  @Test
  public void testWritingAfterDeletion() throws Exception {
    List<Integer> cc = createContentsOfDifferentSize();
    deleteHalfOfContentsRandomly(cc);

    assertExecutionTime(150, new RunnableAdapter() {
      public void doRun() throws Exception {
        createContentsOfDifferentSize();
      }
    });
  }

  @Test
  public void testReadingAfterManyModifications() throws Exception {
    final List<Integer> cc = modifyStorageManyTimes();

    assertExecutionTime(30, new RunnableAdapter() {
      public void doRun() throws Exception {
        readContentsRandomly(cc);
      }
    });
  }

  @Test
  public void testSizeAfterManyModifications() throws Exception {
    modifyStorageManyTimes();
    s.close();
    long indexLength = new File(file.getPath() + Storage.INDEX_EXTENSION).length();
    long dataLength = new File(file.getPath() + Storage.DATA_EXTENSION).length();
    assertEquals(19, (int)(indexLength + dataLength) / (1024 * 1024));
  }

  private List<Integer> createContentsOfDifferentSize() throws Exception {
    List<Integer> result = new ArrayList<Integer>();
    for (int i = 0; i < ITERATIONS_COUNT; i++) {
      result.add(s.store(arrayOfSize(randomSize())));
    }
    return result;
  }

  private byte[] arrayOfSize(int size) {
    byte[] bb = new byte[size];
    for (int i = 0; i < size; i++) bb[i] = (byte)i;
    return bb;
  }

  private void readContentsRandomly(List<Integer> cc) throws Exception {
    for (int i = 0; i < ITERATIONS_COUNT; i++) {
      s.load(randomItem(cc));
    }
  }

  private void deleteHalfOfContentsRandomly(List<Integer> cc) throws IOException {
    int half = cc.size() / 2;
    for (int i = 0; i < half; i++) {
      Integer item = randomItem(cc);
      s.remove(item);
      cc.remove(item);
    }
  }

  private List<Integer> modifyStorageManyTimes() throws Exception {
    List<Integer> cc = createContentsOfDifferentSize();
    deleteHalfOfContentsRandomly(cc);
    cc.addAll(createContentsOfDifferentSize());
    return cc;
  }

  private int randomSize() {
    return rand(MAX_RECORD_SIZE) + 1;
  }

  private Integer randomItem(List<Integer> rr) {
    return rr.get(rand(rr.size()));
  }
}
