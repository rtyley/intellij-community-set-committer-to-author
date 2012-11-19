package org.jetbrains.jps.javac;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

/**
* @author Eugene Zhuravlev
*         Date: 11/18/12
*/
public final class BinaryContent {
  private final byte[] myBuffer;
  private final int myOffset;
  private final int myLength;

  public BinaryContent(byte[] buf) {
    this(buf, 0, buf.length);
  }

  public BinaryContent(byte[] buf, int off, int len) {
    myBuffer = buf;
    myOffset = off;
    myLength = len;
  }

  public byte[] getBuffer() {
    return myBuffer;
  }

  public int getOffset() {
    return myOffset;
  }

  public int getLength() {
    return myLength;
  }

  public byte[] toByteArray() {
    return Arrays.copyOfRange(myBuffer, myOffset, myOffset + myLength);
  }

  public void saveToFile(File file) throws IOException {
    try {
      _writeToFile(file, this);
    }
    catch (IOException e) {
      // assuming the reason is non-existing parent
      final File parentFile = file.getParentFile();
      if (parentFile == null) {
        throw e;
      }
      if (!parentFile.mkdirs()) {
        throw e;
      }
      // second attempt
      _writeToFile(file, this);
    }
  }

  private static void _writeToFile(final File file, BinaryContent content) throws IOException {
    final OutputStream stream = new FileOutputStream(file);
    try {
      stream.write(content.getBuffer(), content.getOffset(), content.getLength());
    }
    finally {
      stream.close();
    }
  }

}
