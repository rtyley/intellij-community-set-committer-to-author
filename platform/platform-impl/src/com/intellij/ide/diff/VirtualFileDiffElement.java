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
package com.intellij.ide.diff;

import com.intellij.ide.presentation.VirtualFilePresentation;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.Callable;

/**
 * @author Konstantin Bulenkov
 */
public class VirtualFileDiffElement extends DiffElement<VirtualFile> {
  private final VirtualFile myFile;
  private FileEditor myFileEditor;
  private FileEditorProvider myEditorProvider;

  public VirtualFileDiffElement(@NotNull VirtualFile file) {
    myFile = file;
  }

  @Override
  public String getPath() {
    return myFile.getPresentableUrl();
  }

  @NotNull
  @Override
  public String getName() {
    return myFile.getName();
  }

  @Override
  public String getPresentablePath() {
    return getPath();
  }

  @Override
  public long getSize() {
    return myFile.getLength();
  }

  @Override
  public long getTimeStamp() {
    return myFile.getTimeStamp();
  }

  @Override
  public boolean isContainer() {
    return myFile.isDirectory();
  }
  @Override
  public VirtualFileDiffElement[] getChildren() {
    final VirtualFile[] files = myFile.getChildren();
    final ArrayList<VirtualFileDiffElement> elements = new ArrayList<VirtualFileDiffElement>();
    for (VirtualFile file : files) {
      if (!FileTypeManager.getInstance().isFileIgnored(file)) {
        elements.add(new VirtualFileDiffElement(file));
      }
    }
    return elements.toArray(new VirtualFileDiffElement[elements.size()]);
  }

  @Override
  public byte[] getContent() throws IOException {
    return myFile.contentsToByteArray();
  }

  @Override
  public VirtualFile getValue() {
    return myFile;
  }

  @Override
  public Icon getIcon() {
    return isContainer() ? PlatformIcons.FOLDER_ICON : VirtualFilePresentation.getIcon(myFile);
  }

  @Override
  public Callable<DiffElement<VirtualFile>> getElementChooser(final Project project) {
    return new Callable<DiffElement<VirtualFile>>() {
      @Nullable
      @Override
      public DiffElement<VirtualFile> call() throws Exception {
        final FileChooserDescriptor descriptor = getChooserDescriptor();
        final VirtualFile[] result = FileChooserFactory.getInstance()
          .createFileChooser(descriptor, project).choose(getValue(), project);
        return result.length == 1 ? createElement(result[0]) : null;
      }
    };
  }

  @Nullable
  protected VirtualFileDiffElement createElement(VirtualFile file) {
    return new VirtualFileDiffElement(file);
  }

  protected FileChooserDescriptor getChooserDescriptor() {
    return new FileChooserDescriptor(false, true, false, false, false, false);
  }

  @Override
  protected JComponent getFromProviders(final Project project, DiffElement target) {
    final FileEditorProvider[] providers = FileEditorProviderManager.getInstance().getProviders(project, getValue());
    if (providers.length > 0) {
      myFileEditor = providers[0].createEditor(project, getValue());
      myEditorProvider = providers[0];
      return myFileEditor.getComponent();
    }
    return null;
  }

  @Override
  protected DiffRequest createRequestForBinaries(Project project, @NotNull VirtualFile src, @NotNull VirtualFile trg) {
    if (FileEditorProviderManager.getInstance().getProviders(project, src).length > 0
        && FileEditorProviderManager.getInstance().getProviders(project, trg).length > 0) {
      return super.createRequestForBinaries(project, src, trg);
    } else {
      return null;
    }
  }

  @Override
  public void disposeViewComponent() {
    super.disposeViewComponent();
    if (myFileEditor != null && myEditorProvider != null) {
      myEditorProvider.disposeEditor(myFileEditor);
      myFileEditor = null;
      myEditorProvider = null;
    }
  }

  @Override
  public DataProvider getDataProvider(final Project project) {
    return new DataProvider() {
      @Override
      public Object getData(@NonNls String dataId) {
        if (PlatformDataKeys.PROJECT.is(dataId)) {
          return project;
        }
        if (PlatformDataKeys.FILE_EDITOR.is(dataId)) {
          return myFileEditor;
        }
        return null;
      }
    };
  }

  @Override
  public boolean isOperationsEnabled() {
    return true;
  }

  @Override
  public boolean copyTo(DiffElement<VirtualFile> container, String relativePath) {
    try {
      final File src = new File(myFile.getPath());
      final File trg = new File(container.getValue().getPath() + relativePath + src.getName());
      FileUtil.copy(src, trg);
      //final VirtualFile targetRoot = container.getValue();
      //if (targetRoot != null && targetRoot.isDirectory()) {
      //  VirtualFile target = getSeparator().equals(relativePath) ? targetRoot : targetRoot.findFileByRelativePath(relativePath);
      //  final String path;
      //  if (target == null) {
      //    path = (targetRoot.getPath() + relativePath).replace('/', File.separatorChar);
      //    new File(path).mkdirs();
      //    target = targetRoot.findFileByRelativePath(removeSeparators(relativePath));          
      //  }
      //
      //    if (target != null && target.isDirectory()) {
      //      myFile.copy(this, target, myFile.getName());
      //    }
      //    else {
      //      assert false : "Can't find " + container.getPath() + relativePath;
      //    }
      //
      //}
    }
    catch (IOException e) {//
    }
    return false;
  }

  private String removeSeparators(String path) {
    final int start = path.startsWith(getSeparator()) ? getSeparator().length() : 0;
    final int end = path.endsWith(getSeparator()) ? path.length() - getSeparator().length() : path.length();
    return path.substring(start, end);
  }

  @Override
  public boolean delete() {
    try {
      myFile.delete(this);
    }
    catch (IOException e) {
      return false;
    }
    return true;
  }

  @Override
  public void refresh() {
    myFile.refresh(false, true);
  }
}
