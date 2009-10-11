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
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author max
 */
public class ByteBufferRADataInput implements RandomAccessDataInput {
  private static final Logger LOG = Logger.getInstance("com.intellij.util.io.ByteBufferRADataInput");

  private final MappedBufferWrapper myBuffer;

  public ByteBufferRADataInput(MappedBufferWrapper buffer) {
    myBuffer = buffer;
  }

  public void setPosition(int pos) {
    getBuffer().position(pos);
  }

  public int getPosition() {
    return getBuffer().position();
  }

  public void readFully(byte[] b) throws IOException {
    getBuffer().get(b);
  }

  public void readFully(byte[] b, int off, int len) throws IOException {
    getBuffer().get(b, off, len);
  }

  public int skipBytes(int n) throws IOException {
    int newPos = getPosition() + n;
    setPosition(newPos);
    return newPos;
  }

  public boolean readBoolean() throws IOException {
    return getBuffer().get() == 1;
  }

  public byte readByte() throws IOException {
    return getBuffer().get();
  }

  public int readUnsignedByte() throws IOException {
    return 0xFF & ((int)getBuffer().get());
  }

  public short readShort() throws IOException {
    return getBuffer().getShort();
  }

  public int readUnsignedShort() throws IOException {
    return 0xFFFF & ((int)getBuffer().getShort());
  }

  public char readChar() throws IOException {
    return getBuffer().getChar();
  }

  public int readInt() throws IOException {
    return getBuffer().getInt();
  }

  public long readLong() throws IOException {
    return getBuffer().getLong();
  }

  public float readFloat() throws IOException {
    return getBuffer().getFloat();
  }

  public double readDouble() throws IOException {
    return getBuffer().getDouble();
  }

  public String readLine() throws IOException {
    LOG.error("Not implemented");
    return null;
  }

  public String readUTF() throws IOException {
    return DataInputStream.readUTF(this);
  }

  public ByteBuffer getBuffer() {
    return myBuffer.buf();
  }
}
