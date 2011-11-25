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
package com.siyeh.ig.naming;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefPackage;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.ui.RegExFormatter;
import com.intellij.util.ui.RegExInputVerifier;
import com.intellij.util.ui.UIUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseGlobalInspection;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import javax.swing.text.InternationalFormatter;
import java.awt.*;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PackageNamingConventionInspection extends BaseGlobalInspection {

  private static final int DEFAULT_MIN_LENGTH = 3;
  private static final int DEFAULT_MAX_LENGTH = 16;
  /**
   * @noinspection PublicField
   */
  @NonNls
  public String m_regex = "[a-z]*";

  /**
   * @noinspection PublicField
   */
  public int m_minLength = DEFAULT_MIN_LENGTH;

  /**
   * @noinspection PublicField
   */
  public int m_maxLength = DEFAULT_MAX_LENGTH;

  private Pattern m_regexPattern = Pattern.compile(m_regex);

  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "package.naming.convention.display.name");
  }

  @Override
  @Nullable
  public CommonProblemDescriptor[] checkElement(
    RefEntity refEntity, AnalysisScope analysisScope,
    InspectionManager inspectionManager,
    GlobalInspectionContext globalInspectionContext) {
    if (!(refEntity instanceof RefPackage)) {
      return null;
    }
    @NonNls final String name = refEntity.getName();
    if (name == null || "default package".equals(name)) {
      return null;
    }

    final int length = name.length();
    if (length < m_minLength) {
      final String errorString =
        InspectionGadgetsBundle.message(
          "package.naming.convention.problem.descriptor.short",
          name);
      return new CommonProblemDescriptor[]{
        inspectionManager.createProblemDescriptor(errorString)};
    }
    if (length > m_maxLength) {
      final String errorString =
        InspectionGadgetsBundle.message("package.naming.convention.problem.descriptor.long", name);
      return new CommonProblemDescriptor[]{
        inspectionManager.createProblemDescriptor(errorString)};
    }
    final Matcher matcher = m_regexPattern.matcher(name);
    if (matcher.matches()) {
      return null;
    }
    else {
      final String errorString =
        InspectionGadgetsBundle.message("package.naming.convention.problem.descriptor.regex.mismatch", name, m_regex);
      return new CommonProblemDescriptor[]{
        inspectionManager.createProblemDescriptor(errorString)};
    }
  }

  @Override
  public void readSettings(Element element) throws InvalidDataException {
    super.readSettings(element);
    m_regexPattern = Pattern.compile(m_regex);
  }

  private static final int REGEX_COLUMN_COUNT = 25;

  @Override
  public JComponent createOptionsPanel() {
    final GridBagLayout layout = new GridBagLayout();
    final JPanel panel = new JPanel(layout);

    final JLabel patternLabel = new JLabel(InspectionGadgetsBundle.message("convention.pattern.option"));
    final JLabel minLengthLabel = new JLabel(InspectionGadgetsBundle.message("convention.min.length.option"));
    final JLabel maxLengthLabel = new JLabel(InspectionGadgetsBundle.message("convention.max.length.option"));

    final NumberFormat numberFormat = NumberFormat.getIntegerInstance();
    numberFormat.setParseIntegerOnly(true);
    numberFormat.setMinimumIntegerDigits(1);
    numberFormat.setMaximumIntegerDigits(2);
    final InternationalFormatter formatter =
      new InternationalFormatter(numberFormat);
    formatter.setAllowsInvalid(false);
    formatter.setCommitsOnValidEdit(true);

    final JFormattedTextField minLengthField =
      new JFormattedTextField(formatter);
    final Font panelFont = panel.getFont();
    minLengthField.setFont(panelFont);
    minLengthField.setValue(m_minLength);
    minLengthField.setColumns(2);
    UIUtil.fixFormattedField(minLengthField);

    final JFormattedTextField maxLengthField =
      new JFormattedTextField(formatter);
    maxLengthField.setFont(panelFont);
    maxLengthField.setValue(m_maxLength);
    maxLengthField.setColumns(2);
    UIUtil.fixFormattedField(maxLengthField);

    final JFormattedTextField regexField =
      new JFormattedTextField(new RegExFormatter());
    regexField.setFont(panelFont);
    regexField.setValue(m_regexPattern);
    regexField.setColumns(REGEX_COLUMN_COUNT);
    regexField.setInputVerifier(new RegExInputVerifier());
    regexField.setFocusLostBehavior(JFormattedTextField.COMMIT);
    UIUtil.fixFormattedField(regexField);
    final DocumentListener listener = new DocumentAdapter() {
      @Override
      public void textChanged(DocumentEvent e) {
        try {
          regexField.commitEdit();
          minLengthField.commitEdit();
          maxLengthField.commitEdit();
          m_regexPattern = (Pattern)regexField.getValue();
          m_regex = m_regexPattern.pattern();
          m_minLength = ((Number)minLengthField.getValue()).intValue();
          m_maxLength = ((Number)maxLengthField.getValue()).intValue();
        }
        catch (ParseException e1) {
          // No luck this time
        }
      }
    };
    final Document regexDocument = regexField.getDocument();
    regexDocument.addDocumentListener(listener);
    final Document minLengthDocument = minLengthField.getDocument();
    minLengthDocument.addDocumentListener(listener);
    final Document maxLengthDocument = maxLengthField.getDocument();
    maxLengthDocument.addDocumentListener(listener);

    final GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridx = 0;
    constraints.gridy = 0;
    constraints.weightx = 0.0;
    constraints.insets.right = UIUtil.DEFAULT_HGAP;
    constraints.anchor = GridBagConstraints.BASELINE_LEADING;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    panel.add(patternLabel, constraints);

    constraints.gridx = 1;
    constraints.gridy = 0;
    constraints.weightx = 1.0;
    constraints.insets.right = 0;
    panel.add(regexField, constraints);

    constraints.gridx = 0;
    constraints.gridy = 1;
    constraints.weightx = 0.0;
    constraints.insets.right = UIUtil.DEFAULT_HGAP;
    panel.add(minLengthLabel, constraints);

    constraints.gridx = 1;
    constraints.gridy = 1;
    constraints.weightx = 1;
    constraints.insets.right = 0;
    panel.add(minLengthField, constraints);

    constraints.gridx = 0;
    constraints.gridy = 2;
    constraints.weightx = 0;
    constraints.insets.right = UIUtil.DEFAULT_HGAP;
    panel.add(maxLengthLabel, constraints);

    constraints.gridx = 1;
    constraints.gridy = 2;
    constraints.weightx = 1;
    constraints.weighty = 1;
    constraints.insets.right = 0;
    panel.add(maxLengthField, constraints);

    return panel;
  }
}
