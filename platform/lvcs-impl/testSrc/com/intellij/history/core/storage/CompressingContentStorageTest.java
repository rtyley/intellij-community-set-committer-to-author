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

import com.intellij.history.core.LocalVcsTestCase;
import org.easymock.IAnswer;
import static org.easymock.classextension.EasyMock.*;
import org.junit.Test;

import java.io.*;

public class CompressingContentStorageTest extends LocalVcsTestCase {
  @Test
  public void testDelegation() throws IOException {
    IContentStorage subject = createStrictMock(IContentStorage.class);

    subject.remove(1);
    subject.close();

    replay(subject);

    CompressingContentStorage s = new CompressingContentStorage(subject);

    s.remove(1);
    s.close();

    verify(subject);
  }

  @Test
  public void testCompressionAndDecompression() throws Exception {
    final byte[][] compressed = new byte[1][];
    IContentStorage subject = createStoredBytesRecordingMock(compressed);

    byte[] original = "public void foo() {} public void foo() {}".getBytes();

    CompressingContentStorage s = new CompressingContentStorage(subject);
    assertEquals(1, s.store(original));

    assertTrue(compressed[0].length < original.length);

    reset(subject);
    expect(subject.load(2)).andReturn(compressed[0]);
    replay(subject);

    assertArrayEquals(original, s.load(2));
  }

  @Test
  public void testCompressionAndDecompressionOfEmptyContent() throws Exception {
    final byte[][] compressed = new byte[1][];

    IContentStorage mock = createMock(IContentStorage.class);
    expect(mock.store((byte[])anyObject())).andAnswer(new IAnswer<Integer>() {
      public Integer answer() throws Throwable {
        compressed[0] = (byte[])getCurrentArguments()[0];
        return 1;
      }
    });
    expect(mock.load(anyInt())).andAnswer(new IAnswer<byte[]>() {
      public byte[] answer() throws Throwable {
        return compressed[0];
      }
    });
    replay(mock);

    CompressingContentStorage s = new CompressingContentStorage(mock);
    s.store(new byte[0]);
    assertArrayEquals(new byte[0], s.load(1));
  }

  @Test
  public void testClosingOfInputAndOutputStreams() throws Exception {
    IContentStorage subject = createStrictMock(IContentStorage.class);
    expect(subject.store((byte[])anyObject())).andReturn(1);
    expect(subject.load(anyInt())).andReturn(new byte[0]);
    replay(subject);

    final boolean[] closeCalled = new boolean[2];

    CompressingContentStorage s = new CompressingContentStorage(subject) {
      @Override
      protected OutputStream createDeflaterOutputStream(OutputStream s) {
        return new DataOutputStream(s) {
          @Override
          public void close() throws IOException {
            closeCalled[0] = true;
            super.close();
          }
        };
      }

      @Override
      protected InputStream createInflaterOutputStream(byte[] content) {
        return new ByteArrayInputStream(content) {
          @Override
          public void close() throws IOException {
            closeCalled[1] = true;
            super.close();
          }
        };
      }
    };

    s.store(new byte[0]);
    s.load(0);

    assertTrue(closeCalled[0]);
    assertTrue(closeCalled[1]);
  }
  //
  //@Test
  //public void testCompressuonDecompressionOfEmptyArray() throws Exception {
  //  Inflater i = new Inflater();
  //  ByteArrayOutputStream output = new ByteArrayOutputStream();
  //  InflaterInputStream input = new InflaterInputStream(new ByteArrayInputStream(new byte[0]), i);
  //  FileUtil.copy(input, output);
  //}

  private IContentStorage createStoredBytesRecordingMock(final byte[][] compressed) throws Exception {
    IContentStorage subject = createMock(IContentStorage.class);

    expect(subject.store((byte[])anyObject())).andAnswer(new IAnswer<Integer>() {
      public Integer answer() throws Throwable {
        compressed[0] = (byte[])getCurrentArguments()[0];
        return 1;
      }
    });

    replay(subject);
    return subject;
  }
}
