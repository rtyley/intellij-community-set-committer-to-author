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
package com.intellij.application.options.codeStyle.arrangement;

import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementSettingsFilter;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Generic GUI for showing standard arrangement settings.
 * 
 * @author Denis Zhdanov
 * @since 8/6/12 12:43 PM
 */
public abstract class ArrangementSettingsPanel extends CodeStyleAbstractPanel {
  
  @NotNull private final ArrangementSettingsFilter myFilter;
  
  public ArrangementSettingsPanel(@NotNull CodeStyleSettings settings, @NotNull ArrangementSettingsFilter filter) {
    super(settings);
    myFilter = filter;
  }

  @Override
  protected EditorHighlighter createHighlighter(EditorColorsScheme scheme) {
    // TODO den implement 
    return null;
  }

  @Override
  public boolean isModified(CodeStyleSettings settings) {
    // TODO den implement 
    return false;
  }

  @Override
  public void apply(CodeStyleSettings settings) {
    // TODO den implement 
  }

  @Override
  protected void resetImpl(CodeStyleSettings settings) {
    // TODO den implement 
  }

  @Override
  public JComponent getPanel() {
    // TODO den implement 
    return null;
  }
}
