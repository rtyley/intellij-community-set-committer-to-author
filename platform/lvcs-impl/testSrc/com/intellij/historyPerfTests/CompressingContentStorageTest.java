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

import com.intellij.history.core.storage.CompressingContentStorage;
import com.intellij.history.core.storage.IContentStorage;
import com.intellij.history.core.storage.BrokenStorageException;
import com.intellij.history.utils.RunnableAdapter;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;

@Ignore
public class CompressingContentStorageTest extends PerformanceTestCase {
  CompressingContentStorage s;

  @Before
  public void setUp() {
    s = new CompressingContentStorage(new MyContentStorage());
  }

  @Test
  public void testCompression() throws IOException {
    assertExecutionTime(845, new RunnableAdapter() {
      public void doRun() throws Exception {
        for (int i = 0; i < 10000; i++) {
          s.store(bytesToStore());
        }
      }
    });
  }

  @Test
  public void testDecompression() throws Exception {
    s.store(bytesToStore());

    assertExecutionTime(330, new RunnableAdapter() {
      public void doRun() throws Exception {
        for (int i = 0; i < 10000; i++) {
          s.load(0);
        }
      }
    });
  }

  private byte[] bytesToStore() {
    return ("package com.intellij.historyPerfTests;\n" + "\n" + "import com.intellij.history.core.storage.CompressingContentStorage;\n" +
            "import com.intellij.history.core.storage.IContentStorage;\n" + "import com.intellij.history.utils.RunnableAdapter;\n" +
            "import com.intellij.idea.Bombed;\n" + "import org.junit.Before;\n" + "import org.junit.Test;\n" + "\n" +
            "import java.io.IOException;\n" + "import java.util.Calendar;\n" + "\n" +
            "@Bombed(month = Calendar.NOVEMBER, day = 31, user = \"anton\")\n" +
            "public class CompressingContentStorageTest extends PerformanceTestCase {\n" + "  CompressingContentStorage s;\n" + "\n" +
            "  @Before\n" + "  public void setUp() {\n" + "    s = new CompressingContentStorage(new MyContentStorage());\n" + "  }\n" +
            "\n" + "  @Test\n" + "  public void testCompression() throws IOException {\n" +
            "    assertExecutionTime(300, new RunnableAdapter() {\n" + "      public void doRun() throws Exception {\n" +
            "        for (int i = 0; i < 10000; i++) {\n" + "          s.store(bytesToStore());\n" + "        }\n" + "      }\n" +
            "    });\n" + "  }\n" + "\n" + "  @Test\n" + "  public void testDecompression() throws IOException {\n" +
            "    s.store(bytesToStore());\n" + "\n" + "    assertExecutionTime(140, new RunnableAdapter() {\n" +
            "      public void doRun() throws Exception {\n" + "        for (int i = 0; i < 10000; i++) {\n" + "          s.load(0);\n" +
            "        }\n" + "      }\n" + "    });\n" + "  }\n" + "\n" + "  private byte[] bytesToStore() {\n" +
            "    return \"hello, world\".getBytes();\n" + "  }\n" + "\n" + "  class MyContentStorage implements IContentStorage {\n" +
            "    byte[] myContent;\n" + "\n" + "    public int store(byte[] content) throws IOException {\n" +
            "      myContent = content;\n" + "      return 0;\n" + "    }\n" + "\n" +
            "    public byte[] load(int id) throws IOException {\n" + "      return myContent;\n" + "    }\n" + "\n" +
            "    public void close() {\n" + "      throw new UnsupportedOperationException();\n" + "    }\n" + "\n" +
            "    public void remove(int id) {\n" + "      throw new UnsupportedOperationException();\n" + "    }\n" + "\n" +
            "    public int getVersion() {\n" + "      throw new UnsupportedOperationException();\n" + "    }\n" + "\n" +
            "    public void setVersion(final int version) {\n" + "      throw new UnsupportedOperationException();\n" + "    }\n" +
            "  }\n" + "}").getBytes();
  }

  class MyContentStorage implements IContentStorage {
    byte[] myContent;

    public int store(byte[] content) throws BrokenStorageException {
      myContent = content;
      return 0;
    }

    public byte[] load(int id) throws BrokenStorageException {
      return myContent;
    }

    public void save() {
      throw new UnsupportedOperationException();
    }

    public void close() {
      throw new UnsupportedOperationException();
    }

    public void remove(int id) {
      throw new UnsupportedOperationException();
    }

    public int getVersion() {
      throw new UnsupportedOperationException();
    }

    public void setVersion(final int version) {
      throw new UnsupportedOperationException();
    }
  }
}