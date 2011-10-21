/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.testFramework;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.vfs.DeprecatedVirtualFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.Charset;

/**
 * In-memory implementation of {@link VirtualFile}.
 */
public class LightVirtualFile extends VirtualFile {
  private FileType myFileType;
  private CharSequence myContent = "";
  private String myName = "";
  private long myModStamp = LocalTimeCounter.currentTime();
  private boolean myIsWritable = true;
  private boolean myValid = true;
  private Language myLanguage;
  private VirtualFile myOriginalFile;

  public LightVirtualFile() {
    this("");
  }

  public LightVirtualFile(@NonNls String name) {
    this(name, "");
  }

  public LightVirtualFile(@NonNls String name, CharSequence content) {
    this(name, null, content, LocalTimeCounter.currentTime());
  }

  public LightVirtualFile(final String name, final FileType fileType, final CharSequence text) {
    this(name, fileType, text, LocalTimeCounter.currentTime());
  }

  public LightVirtualFile(VirtualFile original, final CharSequence text, long modificationStamp) {
    this(original.getName(), original.getFileType(), text, modificationStamp);
    setCharset(original.getCharset());
  }

  public LightVirtualFile(final String name, final FileType fileType, final CharSequence text, final long modificationStamp) {
    this(name, fileType, text, charsetFromContent(fileType, text), modificationStamp);
  }

  private static Charset charsetFromContent(FileType fileType, CharSequence text) {
    if (fileType instanceof LanguageFileType) {
      return ((LanguageFileType)fileType).extractCharsetFromFileContent(null, null, text.toString());
    }
    return null;
  }

  public LightVirtualFile(final String name, final FileType fileType, final CharSequence text, Charset charset, final long modificationStamp) {
    myName = name;
    myFileType = fileType;
    setContent(text);
    myModStamp = modificationStamp;
    setCharset(charset);
  }

  public LightVirtualFile(final String name, final Language language, final CharSequence text) {
    myName = name;
    setContent(text);
    myModStamp = LocalTimeCounter.currentTime();
    setLanguage(language);
  }

  public Language getLanguage() {
    return myLanguage;
  }

  public void setLanguage(final Language language) {
    myLanguage = language;
    myFileType = language.getAssociatedFileType();
    if (myFileType == null) {
      myFileType = FileTypeRegistry.getInstance().getFileTypeByFileName(myName);
    }
  }

  public void setFileType(final FileType fileType) {
    myFileType = fileType;
  }

  private void setContent(CharSequence content) {
    //StringUtil.assertValidSeparators(content);
    myContent = content;
  }

  public VirtualFile getOriginalFile() {
    return myOriginalFile;
  }

  public void setOriginalFile(VirtualFile originalFile) {
    myOriginalFile = originalFile;
  }

  private static class MyVirtualFileSystem extends DeprecatedVirtualFileSystem {
    @NonNls private static final String PROTOCOL = "mock";

    @NotNull
    public String getProtocol() {
      return PROTOCOL;
    }

    @Nullable
    public VirtualFile findFileByPath(@NotNull String path) {
      return null;
    }

    public void refresh(boolean asynchronous) {
    }

    @Nullable
    public VirtualFile refreshAndFindFileByPath(@NotNull String path) {
      return null;
    }

    public void deleteFile(Object requestor, @NotNull VirtualFile vFile) throws IOException {
    }

    public void moveFile(Object requestor, @NotNull VirtualFile vFile, @NotNull VirtualFile newParent) throws IOException {
    }

    public VirtualFile copyFile(Object requestor, @NotNull VirtualFile vFile, @NotNull VirtualFile newParent, @NotNull final String copyName) throws IOException {
      throw new IOException("Cannot copy files");
    }

    public void renameFile(Object requestor, @NotNull VirtualFile vFile, @NotNull String newName) throws IOException {
    }

    public VirtualFile createChildFile(Object requestor, @NotNull VirtualFile vDir, @NotNull String fileName) throws IOException {
      throw new IOException("Cannot create files");
    }

    @NotNull
    public VirtualFile createChildDirectory(Object requestor, @NotNull VirtualFile vDir, @NotNull String dirName) throws IOException {
      throw new IOException("Cannot create directories");
    }
  }

  private static final MyVirtualFileSystem ourFileSystem = new MyVirtualFileSystem();

  @NotNull
  public VirtualFileSystem getFileSystem() {
    return ourFileSystem;
  }

  @Nullable
  public FileType getAssignedFileType() {
    return myFileType;
  }

  public String getPath() {
    return "/" + getName();
  }

  @NotNull
  public String getName() {
    return myName;
  }

  public boolean isWritable() {
    return myIsWritable;
  }

  public boolean isDirectory() {
    return false;
  }

  public boolean isValid() {
    return myValid;
  }

  public void setValid(boolean valid) {
    myValid = valid;
  }

  public VirtualFile getParent() {
    return null;
  }

  public VirtualFile[] getChildren() {
    return EMPTY_ARRAY;
  }

  public InputStream getInputStream() throws IOException {
    return VfsUtilCore.byteStreamSkippingBOM(contentsToByteArray(), this);
  }

  @NotNull
  public OutputStream getOutputStream(Object requestor, final long newModificationStamp, long newTimeStamp) throws IOException {
    return VfsUtilCore.outputStreamAddingBOM(new ByteArrayOutputStream() {
      public void close() {
        myModStamp = newModificationStamp;
        setContent(toString());
      }
    },this);
  }

  @NotNull
  public byte[] contentsToByteArray() throws IOException {
    final Charset charset = getCharset();
    final String s = getContent().toString();
    return charset != null ? s.getBytes(charset.name()) : s.getBytes();
  }

  public long getModificationStamp() {
    return myModStamp;
  }

  public long getTimeStamp() {
    return 0; // todo[max] : Add UnsupporedOperationException at better times.
  }

  public long getLength() {
    try {
      return contentsToByteArray().length;
    }
    catch (IOException e) {
      e.printStackTrace();
      assert false;
      return 0;
    }
  }

  public void refresh(boolean asynchronous, boolean recursive, Runnable postRunnable) {
  }

  public void setContent(Object requestor, CharSequence content, boolean fireEvent) {
    setContent(content);
    myModStamp = LocalTimeCounter.currentTime();
  }

  public void setWritable(boolean b) {
    myIsWritable = b;
  }

  public void rename(Object requestor, @NotNull String newName) throws IOException {
    myName = newName;
  }

  public CharSequence getContent() {
    return myContent;
  }

  @Override
  public String toString() {
    return "LightVirtualFile: " + getPresentableUrl();
  }
}
