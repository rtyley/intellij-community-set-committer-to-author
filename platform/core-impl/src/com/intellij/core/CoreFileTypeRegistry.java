/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.core;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public class CoreFileTypeRegistry extends FileTypeRegistry {
  private final Map<String, FileType> myExtensionsMap = new HashMap<String, FileType>();
  private final List<FileType> myAllFileTypes = new ArrayList<FileType>();

  public CoreFileTypeRegistry() {
    myAllFileTypes.add(UnknownFileType.INSTANCE);
  }

  @Override
  public boolean isFileIgnored(@NonNls @NotNull VirtualFile file) {
    return false;
  }

  @Override
  public FileType[] getRegisteredFileTypes() {
    return myAllFileTypes.toArray(new FileType[myAllFileTypes.size()]);
  }

  @NotNull
  @Override
  public FileType getFileTypeByFile(@NotNull VirtualFile file) {
    return getFileTypeByFileName(file.getName());
  }

  @NotNull
  @Override
  public FileType getFileTypeByFileName(@NotNull @NonNls String fileName) {
    final String extension = FileUtil.getExtension(fileName);
    final FileType result = myExtensionsMap.get(extension);
    return result == null ? UnknownFileType.INSTANCE : result;
  }

  public void registerFileType(FileType fileType, String extension) {
    myAllFileTypes.add(fileType);
    myExtensionsMap.put(extension, fileType);
  }
}
