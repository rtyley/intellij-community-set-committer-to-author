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

/*
 * @author max
 */
package com.intellij.psi.impl.source.codeStyle;

import com.intellij.codeStyle.CodeStyleFacade;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CodeStyleFacadeImpl extends CodeStyleFacade {
  private final Project myProject;

  public CodeStyleFacadeImpl() {
    this(null);
  }

  public CodeStyleFacadeImpl(final Project project) {
    myProject = project;
  }

  public int getIndentSize(final FileType fileType) {
    return CodeStyleSettingsManager.getSettings(myProject).getIndentSize(fileType);
  }

  @Nullable
  public String getLineIndent(@NotNull final Document document, int offset) {
    if (myProject == null) return null;
    return CodeStyleManager.getInstance(myProject).getLineIndent(document, offset);
  }

  public String getLineSeparator() {
    return CodeStyleSettingsManager.getSettings(myProject).getLineSeparator();
  }

  public boolean projectUsesOwnSettings() {
    return myProject != null && CodeStyleSettingsManager.getInstance(myProject).USE_PER_PROJECT_SETTINGS;
  }

  public boolean isUnsuitableCodeStyleConfigurable(final Configurable c) {
    return false;
  }

  public int getRightMargin() {
    return CodeStyleSettingsManager.getSettings(myProject).RIGHT_MARGIN;
  }

  @Override
  public boolean isWrapWhenTypingReachesRightMargin() {
    return CodeStyleSettingsManager.getSettings(myProject).WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN;
  }

  public int getTabSize(final FileType fileType) {
    return CodeStyleSettingsManager.getSettings(myProject).getTabSize(fileType);
  }

  public boolean isSmartTabs(final FileType fileType) {
    return CodeStyleSettingsManager.getSettings(myProject).isSmartTabs(fileType);
  }

  public boolean useTabCharacter(final FileType fileType) {
    return CodeStyleSettingsManager.getSettings(myProject).useTabCharacter(fileType);
  }
}