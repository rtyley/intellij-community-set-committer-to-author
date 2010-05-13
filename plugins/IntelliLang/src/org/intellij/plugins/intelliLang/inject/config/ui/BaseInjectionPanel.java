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
package org.intellij.plugins.intelliLang.inject.config.ui;

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.project.Project;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.ScrollPaneFactory;
import org.intellij.plugins.intelliLang.PatternBasedInjectionHelper;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.intellij.plugins.intelliLang.inject.config.InjectionPlace;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Gregory.Shrago
 */
public class BaseInjectionPanel extends AbstractInjectionPanel<BaseInjection> {

  // read by reflection
  LanguagePanel myLanguagePanel;
  JPanel myCenterPanel;
  EditorTextField myTextArea;
  AdvancedPanel myAdvancedPanel;

  private JPanel myRoot;

  public BaseInjectionPanel(BaseInjection injection, Project project) {
    super(injection, project);
    $$$setupUI$$$(); // see IDEA-9987
    final FileType groovy = FileTypeManager.getInstance().getFileTypeByExtension("groovy");
    myTextArea = new EditorTextField("", project, groovy == UnknownFileType.INSTANCE? FileTypes.PLAIN_TEXT : groovy) {
      @Override
      protected EditorEx createEditor() {
        final EditorEx ex = super.createEditor();
        ex.setOneLineMode(false);
        return ex;
      }
    };
    myCenterPanel.add(ScrollPaneFactory.createScrollPane(myTextArea), BorderLayout.CENTER);
    myTextArea.setFontInheritedFromLAF(false);
    //myTextArea.setFont(EditorColorsManager.getInstance().getGlobalScheme().getFont(EditorFontType.PLAIN));
    init(injection.copy());
  }

  protected void apply(BaseInjection other) {
    boolean enabled = true;
    final StringBuilder sb = new StringBuilder();
    final ArrayList<InjectionPlace> places = new ArrayList<InjectionPlace>();
    for (String s : myTextArea.getText().split("\\s*\n\\s*")) {
      if (s.startsWith("+")) {
        enabled = true;
        s = s.substring(1).trim();
      }
      else if (s.startsWith("-")) {
        enabled = false;
        s = s.substring(1).trim();
      }
      else {
        sb.append(s);
        continue;
      }
      if (sb.length() > 0) {
        final String text = sb.toString();
        places.add(new InjectionPlace(text, PatternBasedInjectionHelper.compileElementPattern(text, other.getSupportId()), enabled));
        sb.setLength(0);
      }
      sb.append(s);
    }
    if (sb.length() > 0) {
      final String text = sb.toString();
      places.add(new InjectionPlace(text, PatternBasedInjectionHelper.compileElementPattern(text, other.getSupportId()), enabled));
    }
    other.getInjectionPlaces().clear();
    other.getInjectionPlaces().addAll(places);
  }

  protected void resetImpl() {
    final List<InjectionPlace> places = myOrigInjection.getInjectionPlaces();
    final StringBuilder sb = new StringBuilder();
    for (InjectionPlace place : places) {
      sb.append(place.isEnabled()?"+ ":"- ").append(place.getText()).append("\n");
    }
    myTextArea.setText(sb.toString());
  }

  public JPanel getComponent() {
    return myRoot;
  }

  private void createUIComponents() {
    myLanguagePanel = new LanguagePanel(myProject, myOrigInjection);
    myAdvancedPanel = new AdvancedPanel(myProject, myOrigInjection);
  }

  private void $$$setupUI$$$() {
  }

}

