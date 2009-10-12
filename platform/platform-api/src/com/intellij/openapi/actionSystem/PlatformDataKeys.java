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
package com.intellij.openapi.actionSystem;

import com.intellij.ide.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.diff.DiffViewer;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.pom.Navigatable;
import com.intellij.ui.content.ContentManager;

import java.awt.*;

/**
 * @author yole
 */
@SuppressWarnings({"deprecation"})
public class PlatformDataKeys {
  public static final DataKey<Project> PROJECT = DataKey.create(DataConstants.PROJECT);
  public static final DataKey<Editor> EDITOR = DataKey.create(DataConstants.EDITOR);
  public static final DataKey<Editor> EDITOR_EVEN_IF_INACTIVE = DataKey.create(DataConstants.EDITOR_EVEN_IF_INACTIVE);
  public static final DataKey<Navigatable> NAVIGATABLE = DataKey.create(DataConstants.NAVIGATABLE);
  public static final DataKey<Navigatable[]> NAVIGATABLE_ARRAY = DataKey.create(DataConstants.NAVIGATABLE_ARRAY);
  public static final DataKey<VirtualFile> VIRTUAL_FILE = DataKey.create(DataConstants.VIRTUAL_FILE);
  public static final DataKey<VirtualFile[]> VIRTUAL_FILE_ARRAY = DataKey.create(DataConstants.VIRTUAL_FILE_ARRAY);
  public static final DataKey<FileEditor> FILE_EDITOR = DataKey.create(DataConstants.FILE_EDITOR);
  public static final DataKey<String> FILE_TEXT = DataKey.create(DataConstants.FILE_TEXT);
  public static final DataKey<Boolean> IS_MODAL_CONTEXT = DataKey.create(DataConstants.IS_MODAL_CONTEXT);
  public static final DataKey<DiffViewer> DIFF_VIEWER = DataKey.create(DataConstants.DIFF_VIEWER);
  public static final DataKey<String> HELP_ID = DataKey.create(DataConstants.HELP_ID);
  public static final DataKey<Project> PROJECT_CONTEXT = DataKey.create(DataConstants.PROJECT_CONTEXT);
  public static final DataKey<Component> CONTEXT_COMPONENT = DataKey.create(DataConstants.CONTEXT_COMPONENT);
  public static final DataKey<CopyProvider> COPY_PROVIDER = DataKey.create(DataConstants.COPY_PROVIDER);
  public static final DataKey<CutProvider> CUT_PROVIDER = DataKey.create(DataConstants.CUT_PROVIDER);
  public static final DataKey<PasteProvider> PASTE_PROVIDER = DataKey.create(DataConstants.PASTE_PROVIDER);
  public static final DataKey<DeleteProvider> DELETE_ELEMENT_PROVIDER = DataKey.create(DataConstants.DELETE_ELEMENT_PROVIDER);
  public static final DataKey<ContentManager> CONTENT_MANAGER = DataKey.create(DataConstantsEx.CONTENT_MANAGER);
  public static final DataKey<ToolWindow> TOOL_WINDOW = DataKey.create("TOOL_WINDOW");
  public static final DataKey<TreeExpander> TREE_EXPANDER = DataKey.create(DataConstantsEx.TREE_EXPANDER);
  public static final DataKey<ExporterToTextFile> EXPORTER_TO_TEXT_FILE = DataKey.create(DataConstants.EXPORTER_TO_TEXT_FILE);
  public static final DataKey<VirtualFile> PROJECT_FILE_DIRECTORY = DataKey.create(DataConstantsEx.PROJECT_FILE_DIRECTORY);
  public static final DataKey<Disposable> UI_DISPOSABLE = DataKey.create("ui.disposable");

}