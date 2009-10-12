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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import org.jetbrains.annotations.NonNls;

public class StringKeyProvider implements ByteBufferMap.KeyProvider<String>{
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.io.StringKeyProvider");

  public static final StringKeyProvider INSTANCE = new StringKeyProvider();
  @NonNls public static final String UTF_8_CHARSET_NAME = "UTF-8";

  private StringKeyProvider() {
  }

  public int hashCode(String key) {
    return key.hashCode();
  }

  public void write(DataOutput out, String key) throws IOException {
    String keyString = (String)key;
    byte[] keyBytes = keyString.getBytes(UTF_8_CHARSET_NAME);
    out.writeInt(keyBytes.length);
    out.write(keyBytes);
  }

  public int length(String key) {
    try{
      String keyString = (String)key;
      byte[] keyBytes = keyString.getBytes(UTF_8_CHARSET_NAME);
      return 4 + keyBytes.length;
    }
    catch(UnsupportedEncodingException e){
      LOG.error(e);
      return 0;
    }
  }

  public String get(DataInput in) throws IOException {
    int length = in.readInt();
    byte[] bytes = new byte[length];
    in.readFully(bytes);
    try {
      return new String(bytes, UTF_8_CHARSET_NAME);
    }
    catch (UnsupportedEncodingException e) {
      LOG.error(e);
      return null;
    }
  }

  public boolean equals(DataInput in, String key) throws IOException {
    try {
      String keyString = (String)key;
      byte[] keyBytes = keyString.getBytes(UTF_8_CHARSET_NAME);

      int length = in.readInt();
      byte[] inputBytes = new byte[length];
      in.readFully(inputBytes);
      if (length != keyBytes.length) return false;
      for (int i = 0; i < length; i++) {
        if (keyBytes[i] != inputBytes[i]) return false;
      }

      return true;
    }
    catch (UnsupportedEncodingException e) {
      LOG.error(e);
      return false;
    }
  }
}
