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

package org.jetbrains.plugins.groovy.refactoring.introduceVariable;

import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.HelpID;
import com.intellij.ui.EditorComboBoxEditor;
import com.intellij.ui.EditorComboBoxRenderer;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.StringComboboxEditor;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.settings.GroovyApplicationSettings;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.refactoring.GroovyNamesUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

import javax.swing.*;
import javax.swing.event.EventListenerList;
import java.awt.event.*;
import java.util.EventListener;
import java.util.HashMap;

public class GroovyIntroduceVariableDialog extends DialogWrapper {

  private final Project myProject;
  private final GrExpression myExpression;
  private final PsiType myType;
  private final int myOccurrencesCount;
  private final GroovyIntroduceVariableBase.Validator myValidator;
  private HashMap<String, PsiType> myTypeMap = null;
  private final EventListenerList myListenerList = new EventListenerList();

  private static final String REFACTORING_NAME = GroovyRefactoringBundle.message("introduce.variable.title");

  private JPanel contentPane;
  private ComboBox myNameComboBox;
  private JCheckBox myCbIsFinal;
  private JCheckBox myCbReplaceAllOccurences;
  private JCheckBox myCbTypeSpec;
  private JComboBox myTypeComboBox;
  private JLabel myTypeLabel;
  private JLabel myNameLabel;
  private JButton buttonOK;
  public String myEnteredName;

  public GroovyIntroduceVariableDialog(Project project,
                                       GrExpression expression,
                                       PsiType psiType,
                                       int occurrencesCount,
                                       GroovyIntroduceVariableBase.Validator validator,
                                       String[] possibleNames) {
    super(project, true);
    myProject = project;
    myExpression = expression;
    myType = psiType;
    myOccurrencesCount = occurrencesCount;
    myValidator = validator;
    setUpNameComboBox(possibleNames);

    setModal(true);
    getRootPane().setDefaultButton(buttonOK);
    setTitle(REFACTORING_NAME);
    init();
    setUpDialog();
    updateOkStatus();
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return contentPane;
  }

  public JComponent getContentPane() {
    return contentPane;
  }

  @Nullable
  protected String getEnteredName() {
    if (myNameComboBox.getEditor().getItem() instanceof String &&
        ((String) myNameComboBox.getEditor().getItem()).length() > 0) {
      return (String) myNameComboBox.getEditor().getItem();
    } else {
      return null;
    }
  }

  protected boolean isReplaceAllOccurrences() {
    return myCbReplaceAllOccurences.isSelected();
  }

  private boolean isDeclareFinal() {
    return myCbIsFinal.isSelected();
  }

  private PsiType getSelectedType() {
    if (!myCbTypeSpec.isSelected() || !myCbTypeSpec.isEnabled()) {
      return null;
    } else {
      return myTypeMap.get(myTypeComboBox.getSelectedItem());
    }
  }

  private void setUpDialog() {

    myCbReplaceAllOccurences.setMnemonic(KeyEvent.VK_A);
    myCbReplaceAllOccurences.setFocusable(false);
    myCbIsFinal.setMnemonic(KeyEvent.VK_F);
    myCbIsFinal.setFocusable(false);
    myCbTypeSpec.setMnemonic(KeyEvent.VK_T);
    myCbTypeSpec.setFocusable(false);
    myNameLabel.setLabelFor(myNameComboBox);
    myTypeLabel.setLabelFor(myTypeComboBox);

    // Type specification
    if (myType == null) {
      myCbTypeSpec.setSelected(false);
      myCbTypeSpec.setEnabled(false);
      myTypeComboBox.setEnabled(false);
    } else {
      final boolean specifyVarTypeExplicitly = GroovyApplicationSettings.getInstance().SPECIFY_VAR_TYPE_EXPLICITLY;
      myCbTypeSpec.setSelected(specifyVarTypeExplicitly);
      myTypeComboBox.setEnabled(specifyVarTypeExplicitly);
      myTypeMap = GroovyRefactoringUtil.getCompatibleTypeNames(myType);
      for (String typeName : myTypeMap.keySet()) {
        myTypeComboBox.addItem(typeName);
      }
    }

    myCbIsFinal.setSelected(GroovyApplicationSettings.getInstance().INTRODUCE_LOCAL_CREATE_FINALS);

    myCbTypeSpec.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent event) {
        myTypeComboBox.setEnabled(myCbTypeSpec.isSelected());
      }
    });

    // Replace occurences
    if (myOccurrencesCount > 1) {
      myCbReplaceAllOccurences.setSelected(false);
      myCbReplaceAllOccurences.setEnabled(true);
      myCbReplaceAllOccurences.setText(myCbReplaceAllOccurences.getText() + " (" + myOccurrencesCount + " occurrences)");
    } else {
      myCbReplaceAllOccurences.setSelected(false);
      myCbReplaceAllOccurences.setEnabled(false);
    }


    contentPane.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myTypeComboBox.requestFocus();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_Y, KeyEvent.ALT_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);

  }

  private void setUpNameComboBox(String[] possibleNames) {

    final EditorComboBoxEditor comboEditor = new StringComboboxEditor(myProject, GroovyFileType.GROOVY_FILE_TYPE, myNameComboBox);

    myNameComboBox.setEditor(comboEditor);
    myNameComboBox.setRenderer(new EditorComboBoxRenderer(comboEditor));

    myNameComboBox.setEditable(true);
    myNameComboBox.setMaximumRowCount(8);
    myListenerList.add(DataChangedListener.class, new DataChangedListener());

    myNameComboBox.addItemListener(
        new ItemListener() {
          public void itemStateChanged(ItemEvent e) {
            fireNameDataChanged();
          }
        }
    );

    ((EditorTextField) myNameComboBox.getEditor().getEditorComponent()).addDocumentListener(new DocumentListener() {
      public void beforeDocumentChange(DocumentEvent event) {
      }

      public void documentChanged(DocumentEvent event) {
        fireNameDataChanged();
      }
    }
    );

    contentPane.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myNameComboBox.requestFocus();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_N, KeyEvent.ALT_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);

    for (String possibleName : possibleNames) {
      myNameComboBox.addItem(possibleName);
    }
  }

  public JComponent getPreferredFocusedComponent() {
    return myNameComboBox;
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  protected void doOKAction() {
    if (!myValidator.isOK(this)) {
      return;
    }
    if (myCbTypeSpec.isEnabled()) {
      GroovyApplicationSettings.getInstance().SPECIFY_VAR_TYPE_EXPLICITLY = myCbTypeSpec.isSelected();
    }
    if (myCbIsFinal.isEnabled()) {
      GroovyApplicationSettings.getInstance().INTRODUCE_LOCAL_CREATE_FINALS = myCbIsFinal.isSelected();
    }
    super.doOKAction();
  }


  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.INTRODUCE_VARIABLE);
  }

  GrExpression getExpression() {
    return myExpression;
  }

  class DataChangedListener implements EventListener {
    void dataChanged() {
      updateOkStatus();
    }
  }

  private void updateOkStatus() {
    String text = getEnteredName();
    setOKActionEnabled(GroovyNamesUtil.isIdentifier(text));
  }

  private void fireNameDataChanged() {
    Object[] list = myListenerList.getListenerList();
    for (Object aList : list) {
      if (aList instanceof DataChangedListener) {
        ((DataChangedListener) aList).dataChanged();
      }
    }
  }

  public GroovyIntroduceVariableSettings getSettings() {
    return new MyGroovyIntroduceVariableSettings(this);
  }

  private static class MyGroovyIntroduceVariableSettings implements GroovyIntroduceVariableSettings {
    String myEnteredName;
    boolean myIsReplaceAllOccurrences;
    boolean myIsDeclareFinal;
    PsiType mySelectedType;

    public MyGroovyIntroduceVariableSettings(GroovyIntroduceVariableDialog dialog) {
      myEnteredName = dialog.getEnteredName();
      myIsReplaceAllOccurrences = dialog.isReplaceAllOccurrences();
      myIsDeclareFinal = dialog.isDeclareFinal();
      mySelectedType = dialog.getSelectedType();
    }

    public String getEnteredName() {
      return myEnteredName;
    }

    public boolean isReplaceAllOccurrences() {
      return myIsReplaceAllOccurrences;
    }

    public boolean isDeclareFinal() {
      return myIsDeclareFinal;
    }

    public PsiType getSelectedType() {
      return mySelectedType;
    }

  }
}
