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
package com.intellij.application.options;

import com.intellij.ide.highlighter.XmlHighlighterFactory;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.xml.XmlCodeStyleSettings;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class CodeStyleXmlPanel extends CodeStyleAbstractPanel{
  private JTextField myKeepBlankLines;
  private JComboBox myWrapAttributes;
  private JCheckBox myAlignAttributes;
  private JCheckBox myKeepWhiteSpaces;

  private JPanel myPanel;
  private JPanel myPreviewPanel;

  private JCheckBox mySpacesAroundEquality;
  private JCheckBox mySpacesAfterTagName;
  private JCheckBox myKeepLineBreaks;
  private JCheckBox myInEmptyTag;
  private JCheckBox myWrapText;
  private JCheckBox myKeepLineBreaksInText;
  private JComboBox myWhiteSpaceAroundCDATA;
  private JCheckBox myKeepWhitespaceInsideCDATACheckBox;

  public CodeStyleXmlPanel(CodeStyleSettings settings) {
    super(settings);
    installPreviewPanel(myPreviewPanel);

    fillWrappingCombo(myWrapAttributes);

    addPanelToWatch(myPanel);
  }

  protected EditorHighlighter createHighlighter(final EditorColorsScheme scheme) {
    return XmlHighlighterFactory.createXMLHighlighter(scheme);
  }

  protected int getRightMargin() {
    return 60;
  }

  public void apply(CodeStyleSettings settings) {
    XmlCodeStyleSettings xmlSettings = settings.getCustomSettings(XmlCodeStyleSettings.class);
    xmlSettings.XML_KEEP_BLANK_LINES = getIntValue(myKeepBlankLines);
    xmlSettings.XML_KEEP_LINE_BREAKS = myKeepLineBreaks.isSelected();
    xmlSettings.XML_KEEP_LINE_BREAKS_IN_TEXT = myKeepLineBreaksInText.isSelected();
    xmlSettings.XML_ATTRIBUTE_WRAP = ourWrappings[myWrapAttributes.getSelectedIndex()];
    xmlSettings.XML_TEXT_WRAP = myWrapText.isSelected() ? CodeStyleSettings.WRAP_AS_NEEDED : CodeStyleSettings.DO_NOT_WRAP;
    xmlSettings.XML_ALIGN_ATTRIBUTES = myAlignAttributes.isSelected();
    xmlSettings.XML_KEEP_WHITESPACES = myKeepWhiteSpaces.isSelected();
    xmlSettings.XML_SPACE_AROUND_EQUALITY_IN_ATTRIBUTE = mySpacesAroundEquality.isSelected();
    xmlSettings.XML_SPACE_AFTER_TAG_NAME = mySpacesAfterTagName.isSelected();
    xmlSettings.XML_SPACE_INSIDE_EMPTY_TAG = myInEmptyTag.isSelected();
    xmlSettings.XML_WHITE_SPACE_AROUND_CDATA = myWhiteSpaceAroundCDATA.getSelectedIndex();
    xmlSettings.XML_KEEP_WHITE_SPACES_INSIDE_CDATA = myKeepWhitespaceInsideCDATACheckBox.isSelected();
  }

  private int getIntValue(JTextField keepBlankLines) {
    try {
      return Integer.parseInt(keepBlankLines.getText());
    }
    catch (NumberFormatException e) {
      return 0;
    }
  }

  protected void resetImpl(final CodeStyleSettings settings) {
    XmlCodeStyleSettings xmlSettings = settings.getCustomSettings(XmlCodeStyleSettings.class);
    myKeepBlankLines.setText(String.valueOf(xmlSettings.XML_KEEP_BLANK_LINES));
    myWrapAttributes.setSelectedIndex(getIndexForWrapping(xmlSettings.XML_ATTRIBUTE_WRAP));
    myAlignAttributes.setSelected(xmlSettings.XML_ALIGN_ATTRIBUTES);
    myKeepWhiteSpaces.setSelected(xmlSettings.XML_KEEP_WHITESPACES);
    mySpacesAfterTagName.setSelected(xmlSettings.XML_SPACE_AFTER_TAG_NAME);
    mySpacesAroundEquality.setSelected(xmlSettings.XML_SPACE_AROUND_EQUALITY_IN_ATTRIBUTE);
    myKeepLineBreaks.setSelected(xmlSettings.XML_KEEP_LINE_BREAKS);
    myKeepLineBreaksInText.setSelected(xmlSettings.XML_KEEP_LINE_BREAKS_IN_TEXT);
    myInEmptyTag.setSelected(xmlSettings.XML_SPACE_INSIDE_EMPTY_TAG);
    myWrapText.setSelected(wrapText(settings));
    myWhiteSpaceAroundCDATA.setSelectedIndex(xmlSettings.XML_WHITE_SPACE_AROUND_CDATA);
    myKeepWhitespaceInsideCDATACheckBox.setSelected(xmlSettings.XML_KEEP_WHITE_SPACES_INSIDE_CDATA);
  }

  public boolean isModified(CodeStyleSettings settings) {
    XmlCodeStyleSettings xmlSettings = settings.getCustomSettings(XmlCodeStyleSettings.class);
    if (myWrapText.isSelected() != wrapText(settings)) {
      return true;
    }
    if (xmlSettings.XML_KEEP_BLANK_LINES != getIntValue(myKeepBlankLines)) {
      return true;
    }
    if (xmlSettings.XML_ATTRIBUTE_WRAP != ourWrappings[myWrapAttributes.getSelectedIndex()]) {
      return true;
    }
    if (xmlSettings.XML_ALIGN_ATTRIBUTES != myAlignAttributes.isSelected()) {
      return true;
    }
    if (xmlSettings.XML_KEEP_WHITESPACES != myKeepWhiteSpaces.isSelected()) {
      return true;
    }

    if (xmlSettings.XML_SPACE_AROUND_EQUALITY_IN_ATTRIBUTE != mySpacesAroundEquality.isSelected()){
      return true;
    }

    if (xmlSettings.XML_SPACE_AFTER_TAG_NAME != mySpacesAfterTagName.isSelected()){
      return true;
    }

    if (xmlSettings.XML_KEEP_LINE_BREAKS != myKeepLineBreaks.isSelected()) {
      return true;
    }

    if (xmlSettings.XML_KEEP_LINE_BREAKS_IN_TEXT != myKeepLineBreaksInText.isSelected()) {
      return true;
    }

    if (xmlSettings.XML_SPACE_INSIDE_EMPTY_TAG != myInEmptyTag.isSelected()){
      return true;
    }
    if (xmlSettings.XML_WHITE_SPACE_AROUND_CDATA != myWhiteSpaceAroundCDATA.getSelectedIndex()) {
      return true;
    }
    if (xmlSettings.XML_KEEP_WHITE_SPACES_INSIDE_CDATA != this.myKeepWhitespaceInsideCDATACheckBox.isSelected()) {
      return true;
    }

    return false;
  }

  private boolean wrapText(final CodeStyleSettings settings) {
    XmlCodeStyleSettings xmlSettings = settings.getCustomSettings(XmlCodeStyleSettings.class);
    return xmlSettings.XML_TEXT_WRAP == CodeStyleSettings.WRAP_AS_NEEDED;
  }

  public JComponent getPanel() {
    return myPanel;
  }

  protected String getPreviewText() {
    return readFromFile(getClass(), "preview.xml.template");
  }

  @NotNull
  protected FileType getFileType() {
    return StdFileTypes.XML;
  }

  protected void prepareForReformat(final PsiFile psiFile) {
    //psiFile.putUserData(PsiUtil.FILE_LANGUAGE_LEVEL_KEY, LanguageLevel.HIGHEST);
  }
}
