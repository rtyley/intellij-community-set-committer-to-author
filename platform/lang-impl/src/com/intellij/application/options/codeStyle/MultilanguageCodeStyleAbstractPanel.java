/*
 * Copyright 2010 JetBrains s.r.o.
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
package com.intellij.application.options.codeStyle;

import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.ide.DataManager;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;

/**
 * Base class for code style settings panels supporting multiple programming languages.
 *
 * @author rvishnyakov
 */
public abstract class MultilanguageCodeStyleAbstractPanel extends CodeStyleAbstractPanel {

  private Language myLanguage;

  protected MultilanguageCodeStyleAbstractPanel(CodeStyleSettings settings) {
    super(settings);
  }

  /**
   * @return Always true for multilanguage panel.
   */
  @Override
  protected final boolean isMultilanguage() {
    return true;
  }

  public void setLanguage(Language language) {
    myLanguage = language;
    updatePreviewEditor();
  }

  protected abstract int getSettingsType();

  @Override
  protected String getPreviewText() {
    if (myLanguage == null) return "";
    return LanguageCodeStyleSettingsProvider.getCodeSample(myLanguage, getSettingsType());
  }

  @NotNull
  @Override
  protected final FileType getFileType() {
    if (myLanguage != null) {
      return myLanguage.getAssociatedFileType();
    }
    LanguageFileType availTypes[] = LanguageCodeStyleSettingsProvider.getLanguageFileTypes();
    if (availTypes.length > 0) {
      myLanguage = availTypes[0].getLanguage();
      return availTypes[0];
    }
    return StdFileTypes.JAVA;
  }

  protected EditorHighlighter createHighlighter(final EditorColorsScheme scheme) {
    Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext());
    if (project == null) {
      project = ProjectManager.getInstance().getDefaultProject();
    }
    if (getFileType() instanceof LanguageFileType) {
      return ((LanguageFileType)getFileType()).getEditorHighlighter(project, null, scheme);
    }
    return null;
  }
}
