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
import com.intellij.openapi.vfs.VirtualFile;
import gnu.trove.TObjectIntHashMap;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class FileKeyProvider implements ByteBufferMap.KeyProvider<VirtualFile>{
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.io.FileKeyProvider");

  private final VirtualFile[] myFileIndex;
  private final TObjectIntHashMap<VirtualFile> myFileToIndexMap;

  public FileKeyProvider(VirtualFile[] fileIndex, TObjectIntHashMap<VirtualFile> fileToIndexMap) {
    myFileIndex = fileIndex;
    myFileToIndexMap = fileToIndexMap;
  }

  public int hashCode(VirtualFile key) {
    int index = myFileToIndexMap.get(key) - 1;
    return index;
  }

  public void write(DataOutput out, VirtualFile key) throws IOException {
    int index = myFileToIndexMap.get(key) - 1;
    LOG.assertTrue(index >= 0);
    out.writeInt(index);
  }

  public int length(VirtualFile key) {
    return 4;
  }

  public VirtualFile get(DataInput in) throws IOException {
    int index = in.readInt();
    return myFileIndex[index];
  }

  public boolean equals(DataInput in, VirtualFile key) throws IOException {
    int index = in.readInt();
    return key.equals(myFileIndex[index]);
  }
}
