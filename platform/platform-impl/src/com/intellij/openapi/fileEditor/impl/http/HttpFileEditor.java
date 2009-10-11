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
package com.intellij.openapi.fileEditor.impl.http;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.fileEditor.impl.text.TextEditorState;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.beans.PropertyChangeListener;

/**
 * @author nik
 */
public class HttpFileEditor extends UserDataHolderBase implements FileEditor {
  private final HttpVirtualFile myVirtualFile;
  private final DownloadRemoteFilePanel myDownloadPanel;

  public HttpFileEditor(final Project project, final HttpVirtualFile virtualFile) {
    myVirtualFile = virtualFile;
    myDownloadPanel = new DownloadRemoteFilePanel(project, virtualFile);
  }

  @NotNull
  public JComponent getComponent() {
    return myDownloadPanel.getMainPanel();
  }

  public JComponent getPreferredFocusedComponent() {
    return myDownloadPanel.getMainPanel();
  }

  @NotNull
  public String getName() {
    return "Http";
  }

  @NotNull
  public FileEditorState getState(@NotNull final FileEditorStateLevel level) {
    return new TextEditorState();
  }

  public void setState(@NotNull final FileEditorState state) {
  }

  public boolean isModified() {
    return false;
  }

  public boolean isValid() {
    return true;
  }

  public void selectNotify() {
    myDownloadPanel.showNotify();
  }

  public void deselectNotify() {
    myDownloadPanel.hideNotify();
  }

  public void addPropertyChangeListener(@NotNull final PropertyChangeListener listener) {
  }

  public void removePropertyChangeListener(@NotNull final PropertyChangeListener listener) {
  }

  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    return null;
  }

  public FileEditorLocation getCurrentLocation() {
    return null;
  }

  public StructureViewBuilder getStructureViewBuilder() {
    return null;
  }

  public void dispose() {
    myDownloadPanel.dispose();
  }
}
