/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package com.intellij.openapi.roots.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.ArrayUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 *  @author dsl
 */
public class ContentEntryImpl extends RootModelComponentBase implements ContentEntry, ClonableContentEntry, Comparable<ContentEntryImpl> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.SimpleContentEntryImpl");
  @NotNull private final VirtualFilePointer myRoot;
  @NonNls public static final String ELEMENT_NAME = "content";
  private final Set<SourceFolder> mySourceFolders = new LinkedHashSet<SourceFolder>();
  private final Set<ExcludeFolder> myExcludeFolders = new TreeSet<ExcludeFolder>(ContentFolderComparator.INSTANCE);
  @NonNls public static final String URL_ATTRIBUTE = "url";

  ContentEntryImpl(@NotNull VirtualFile file, @NotNull RootModelImpl m) {
    this(file.getUrl(), m);
  }

  ContentEntryImpl(@NotNull String url, @NotNull RootModelImpl m) {
    super(m);
    myRoot = VirtualFilePointerManager.getInstance().create(url, this, null);
  }

  ContentEntryImpl(@NotNull Element e, @NotNull RootModelImpl m) throws InvalidDataException {
    this(getUrlFrom(e), m);
    initSourceFolders(e);
    initExcludeFolders(e);
  }

  private static String getUrlFrom(@NotNull Element e) throws InvalidDataException {
    LOG.assertTrue(ELEMENT_NAME.equals(e.getName()));

    String url = e.getAttributeValue(URL_ATTRIBUTE);
    if (url == null) throw new InvalidDataException();
    return url;
  }

  private void initSourceFolders(@NotNull Element e) throws InvalidDataException {
    for (Object child : e.getChildren(SourceFolderImpl.ELEMENT_NAME)) {
      addSourceFolder(new SourceFolderImpl((Element)child, this));
    }
  }

  private void initExcludeFolders(@NotNull Element e) throws InvalidDataException {
    for (Object child : e.getChildren(ExcludeFolderImpl.ELEMENT_NAME)) {
      ExcludeFolderImpl excludeFolder = new ExcludeFolderImpl((Element)child, this);
      addExcludeFolder(excludeFolder);
    }
  }

  @Override
  public VirtualFile getFile() {
    //assert !isDisposed();
    final VirtualFile file = myRoot.getFile();
    return file == null || !file.isDirectory() ? null : file;
  }

  @Override
  @NotNull
  public String getUrl() {
    return myRoot.getUrl();
  }

  @Override
  public SourceFolder[] getSourceFolders() {
    return mySourceFolders.toArray(new SourceFolder[mySourceFolders.size()]);
  }

  @Override
  @NotNull
  public VirtualFile[] getSourceFolderFiles() {
    assert !isDisposed();
    final SourceFolder[] sourceFolders = getSourceFolders();
    ArrayList<VirtualFile> result = new ArrayList<VirtualFile>(sourceFolders.length);
    for (SourceFolder sourceFolder : sourceFolders) {
      final VirtualFile file = sourceFolder.getFile();
      if (file != null) {
        result.add(file);
      }
    }
    return VfsUtilCore.toVirtualFileArray(result);
  }

  @Override
  public ExcludeFolder[] getExcludeFolders() {
    //assert !isDisposed();
    final ArrayList<ExcludeFolder> result = new ArrayList<ExcludeFolder>(myExcludeFolders);
    for (DirectoryIndexExcludePolicy excludePolicy : Extensions.getExtensions(DirectoryIndexExcludePolicy.EP_NAME, getRootModel().getProject())) {
      final VirtualFilePointer[] files = excludePolicy.getExcludeRootsForModule(getRootModel());
      for (VirtualFilePointer file : files) {
        addExcludeForOutputPath(file, result);
      }
    }
    if (getRootModel().isExcludeExplodedDirectory()) {
      addExcludeForOutputPath(getRootModel().myExplodedDirectoryPointer, result);
    }
    return result.toArray(new ExcludeFolder[result.size()]);
  }

  private void addExcludeForOutputPath(@Nullable final VirtualFilePointer outputPath, @NotNull ArrayList<ExcludeFolder> result) {
    if (outputPath == null) return;
    final VirtualFile outputPathFile = outputPath.getFile();
    final VirtualFile file = myRoot.getFile();
    if (outputPathFile != null && file != null /* TODO: ??? && VfsUtil.isAncestor(file, outputPathFile, false) */) {
      result.add(new ExcludedOutputFolderImpl(this, outputPath));
    }
  }

  @Override
  @NotNull
  public VirtualFile[] getExcludeFolderFiles() {
    assert !isDisposed();
    final ExcludeFolder[] excludeFolders = getExcludeFolders();
    ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();
    for (ExcludeFolder excludeFolder : excludeFolders) {
      final VirtualFile file = excludeFolder.getFile();
      if (file != null) {
        result.add(file);
      }
    }
    return VfsUtilCore.toVirtualFileArray(result);
  }

  @Override
  public SourceFolder addSourceFolder(@NotNull VirtualFile file, boolean isTestSource) {
    assertCanAddFolder(file);
    return addSourceFolder(new SourceFolderImpl(file, isTestSource, this));
  }

  @Override
  public SourceFolder addSourceFolder(@NotNull VirtualFile file, boolean isTestSource, @NotNull String packagePrefix) {
    assertCanAddFolder(file);
    return addSourceFolder(new SourceFolderImpl(file, isTestSource, packagePrefix, this));
  }

  @Override
  public SourceFolder addSourceFolder(@NotNull String url, boolean isTestSource) {
    assertFolderUnderMe(url);
    return addSourceFolder(new SourceFolderImpl(url, isTestSource, this));
  }

  private SourceFolder addSourceFolder(SourceFolderImpl f) {
    mySourceFolders.add(f);
    Disposer.register(this, f); //rewire source folder dispose parent from rootmodel to this content root
    return f;
  }

  @Override
  public void removeSourceFolder(@NotNull SourceFolder sourceFolder) {
    assert !isDisposed();
    assertCanRemoveFrom(sourceFolder, mySourceFolders);
    doRemove(sourceFolder);
  }

  private void doRemove(SourceFolder sourceFolder) {
    mySourceFolders.remove(sourceFolder);
    Disposer.dispose((Disposable)sourceFolder);
  }

  @Override
  public void clearSourceFolders() {
    assert !isDisposed();
    getRootModel().assertWritable();
    for (SourceFolder folder : mySourceFolders) {
      Disposer.dispose((Disposable)folder);
    }
    mySourceFolders.clear();
  }

  @Override
  public ExcludeFolder addExcludeFolder(@NotNull VirtualFile file) {
    assert !isDisposed();
    assertCanAddFolder(file);
    return addExcludeFolder(new ExcludeFolderImpl(file, this));
  }

  @Override
  public ExcludeFolder addExcludeFolder(@NotNull String url) {
    assert !isDisposed();
    assertCanAddFolder(url);
    return addExcludeFolder(new ExcludeFolderImpl(url, this));
  }

  private void assertCanAddFolder(@NotNull VirtualFile file) {
    assertCanAddFolder(file.getUrl());
  }

  private void assertCanAddFolder(@NotNull String url) {
    getRootModel().assertWritable();
    assertFolderUnderMe(url);
  }

  @Override
  public void removeExcludeFolder(@NotNull ExcludeFolder excludeFolder) {
    assert !isDisposed();
    assertCanRemoveFrom(excludeFolder, myExcludeFolders);
    myExcludeFolders.remove(excludeFolder);
    Disposer.dispose((Disposable)excludeFolder);
  }

  @Override
  public void clearExcludeFolders() {
    assert !isDisposed();
    getRootModel().assertWritable();
    for (ExcludeFolder excludeFolder : myExcludeFolders) {
      Disposer.dispose((Disposable)excludeFolder);
    }
    myExcludeFolders.clear();
  }

  private ExcludeFolder addExcludeFolder(ExcludeFolder f) {
    Disposer.register(this, (Disposable)f);
    myExcludeFolders.add(f);
    return f;
  }

  private <T extends ContentFolder> void assertCanRemoveFrom(T f, @NotNull Set<T> ff) {
    getRootModel().assertWritable();
    LOG.assertTrue(ff.contains(f));
  }

  private void assertFolderUnderMe(@NotNull String url) {
    final String path = VfsUtilCore.urlToPath(url);
    final String rootPath = VfsUtilCore.urlToPath(getUrl());
    if (!FileUtil.isAncestor(rootPath, path, false)) {
      LOG.error("The file '" + path + "' is not under content entry root '" + rootPath + "'");
    }
  }

  @Override
  public boolean isSynthetic() {
    return false;
  }

  @Override
  @NotNull
  public ContentEntry cloneEntry(@NotNull RootModelImpl rootModel) {
    assert !isDisposed();
    ContentEntryImpl cloned = new ContentEntryImpl(myRoot.getUrl(), rootModel);
    for (final SourceFolder sourceFolder : mySourceFolders) {
      if (sourceFolder instanceof ClonableContentFolder) {
        ContentFolder folder = ((ClonableContentFolder)sourceFolder).cloneFolder(cloned);
        cloned.addSourceFolder((SourceFolderImpl)folder);
      }
    }

    for (final ExcludeFolder excludeFolder : myExcludeFolders) {
      if (excludeFolder instanceof ClonableContentFolder) {
        ContentFolder folder = ((ClonableContentFolder)excludeFolder).cloneFolder(cloned);
        cloned.addExcludeFolder((ExcludeFolder)folder);
      }
    }

    return cloned;
  }

  public void writeExternal(@NotNull Element element) throws WriteExternalException {
    assert !isDisposed();
    LOG.assertTrue(ELEMENT_NAME.equals(element.getName()));
    element.setAttribute(URL_ATTRIBUTE, myRoot.getUrl());
    for (final SourceFolder sourceFolder : mySourceFolders) {
      if (sourceFolder instanceof SourceFolderImpl) {
        final Element subElement = new Element(SourceFolderImpl.ELEMENT_NAME);
        ((SourceFolderImpl)sourceFolder).writeExternal(subElement);
        element.addContent(subElement);
      }
    }

    for (final ExcludeFolder excludeFolder : myExcludeFolders) {
      if (excludeFolder instanceof ExcludeFolderImpl) {
        final Element subElement = new Element(ExcludeFolderImpl.ELEMENT_NAME);
        ((ExcludeFolderImpl)excludeFolder).writeExternal(subElement);
        element.addContent(subElement);
      }
    }
  }

  private static final class ContentFolderComparator implements Comparator<ContentFolder> {
    public static final ContentFolderComparator INSTANCE = new ContentFolderComparator();

    @Override
    public int compare(@NotNull ContentFolder o1, @NotNull ContentFolder o2) {
      return o1.getUrl().compareTo(o2.getUrl());
    }
  }

  @Override
  public int compareTo(@NotNull ContentEntryImpl other) {
    int i = getUrl().compareTo(other.getUrl());
    if (i != 0) return i;
    i = ArrayUtil.lexicographicCompare(getSourceFolders(), other.getSourceFolders());
    if (i != 0) return i;
    return ArrayUtil.lexicographicCompare(getExcludeFolders(), other.getExcludeFolders());
  }
}
