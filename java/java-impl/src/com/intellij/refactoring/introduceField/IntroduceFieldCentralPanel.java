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
package com.intellij.refactoring.introduceField;

import com.intellij.codeInsight.TestUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.JavaVisibilityPanel;
import com.intellij.refactoring.ui.TypeSelectorManager;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.ui.StateRestoringCheckBox;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * User: anna
 * Date: 3/16/11
 */
public abstract class IntroduceFieldCentralPanel {
   protected static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.introduceField.IntroduceFieldDialog");

  protected static boolean ourLastCbFinalState = false;

  protected final PsiClass myParentClass;
  protected final PsiExpression myInitializerExpression;
  protected final PsiLocalVariable myLocalVariable;
  protected final boolean myIsCurrentMethodConstructor;
  protected final boolean myIsInvokedOnDeclaration;
  protected final boolean myWillBeDeclaredStatic;
  protected final int myOccurrencesCount;
  protected final boolean myAllowInitInMethod;
  protected final boolean myAllowInitInMethodIfAll;
  protected final TypeSelectorManager myTypeSelectorManager;


  private JCheckBox myCbReplaceAll;
  private StateRestoringCheckBox myCbDeleteVariable;
  private StateRestoringCheckBox myCbFinal;

  public IntroduceFieldCentralPanel(PsiClass parentClass,
                                    PsiExpression initializerExpression,
                                    PsiLocalVariable localVariable,
                                    boolean isCurrentMethodConstructor, boolean isInvokedOnDeclaration, boolean willBeDeclaredStatic,
                                    int occurrencesCount, boolean allowInitInMethod, boolean allowInitInMethodIfAll,
                                    TypeSelectorManager typeSelectorManager) {
    myParentClass = parentClass;
    myInitializerExpression = initializerExpression;
    myLocalVariable = localVariable;
    myIsCurrentMethodConstructor = isCurrentMethodConstructor;
    myIsInvokedOnDeclaration = isInvokedOnDeclaration;
    myWillBeDeclaredStatic = willBeDeclaredStatic;
    myOccurrencesCount = occurrencesCount;
    myAllowInitInMethod = allowInitInMethod;
    myAllowInitInMethodIfAll = allowInitInMethodIfAll;
    myTypeSelectorManager = typeSelectorManager;
  }

  protected abstract boolean setEnabledInitializationPlaces(PsiElement initializerPart, PsiElement initializer);
  public abstract BaseExpressionToFieldHandler.InitializationPlace getInitializerPlace();
  protected abstract void initializeInitializerPlace(PsiExpression initializerExpression,
                                                     BaseExpressionToFieldHandler.InitializationPlace ourLastInitializerPlace);
  protected abstract JComponent createInitializerPlacePanel(ItemListener itemListener, ItemListener finalUpdater);
  public abstract void setInitializeInFieldDeclaration();

  public abstract void setVisibility(String visibility);
  public abstract String getFieldVisibility();
  public abstract void addVisibilityListener(ChangeListener changeListener);

  protected void initializeControls(PsiExpression initializerExpression,
                                    BaseExpressionToFieldHandler.InitializationPlace ourLastInitializerPlace) {
    myCbFinal.setSelected(myCbFinal.isEnabled() && ourLastCbFinalState);
  }


  public boolean isReplaceAllOccurrences() {
    if (myIsInvokedOnDeclaration) return true;
    if (myOccurrencesCount <= 1) return false;
    return myCbReplaceAll.isSelected();
  }

  public boolean isDeleteVariable() {
    if (myIsInvokedOnDeclaration) return true;
    if (myCbDeleteVariable == null) return false;
    return myCbDeleteVariable.isSelected();
  }

  public boolean isDeclareFinal() {
    return myCbFinal.isSelected();
  }

  protected JComponent createCenterPanel() {

    ItemListener itemListener = new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        if (myCbReplaceAll != null && myAllowInitInMethod) {
          updateInitializerSelection();
        }
        updateTypeSelector();
      }
    };
    ItemListener finalUpdater = new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        updateCbFinal();
      }
    };
    final JComponent initializerPlacePanel = createInitializerPlacePanel(itemListener, finalUpdater);
    final JPanel checkboxes = appendCheckboxes(itemListener);
    JPanel panel = composeWholePanel(initializerPlacePanel, checkboxes);

    updateTypeSelector();
    return panel;
  }

  protected abstract JPanel composeWholePanel(JComponent initializerPlacePanel, JPanel checkboxPanel);

  protected void updateInitializerSelection() {
  }

  private JPanel appendCheckboxes(ItemListener itemListener) {
    GridBagConstraints gbConstraints = new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1,1,0,0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(0,0,0,0), 0,0);
    JPanel panel = new JPanel(new GridBagLayout());
    myCbFinal = new StateRestoringCheckBox();
    myCbFinal.setFocusable(false);
    myCbFinal.setText(RefactoringBundle.message("declare.final"));
    myCbFinal.addItemListener(itemListener);
    gbConstraints.gridy++;
    panel.add(myCbFinal, gbConstraints);

    if (myOccurrencesCount > 1) {
      myCbReplaceAll = new NonFocusableCheckBox();
      myCbReplaceAll.setText(RefactoringBundle.message("replace.all.occurrences.of.expression.0.occurrences", myOccurrencesCount));
      gbConstraints.gridy++;
      panel.add(myCbReplaceAll, gbConstraints);
      myCbReplaceAll.addItemListener(itemListener);
      if (myIsInvokedOnDeclaration) {
        myCbReplaceAll.setEnabled(false);
        myCbReplaceAll.setSelected(true);
      }
    }

    if (myLocalVariable != null) {
      gbConstraints.gridy++;
      if (myCbReplaceAll != null) {
        gbConstraints.insets = new Insets(0, 8, 0, 0);
      }
      myCbDeleteVariable = new StateRestoringCheckBox();
      myCbDeleteVariable.setText(RefactoringBundle.message("delete.variable.declaration"));
      panel.add(myCbDeleteVariable, gbConstraints);
      if (myIsInvokedOnDeclaration) {
        myCbDeleteVariable.setEnabled(false);
        myCbDeleteVariable.setSelected(true);
      } else if (myCbReplaceAll != null) {
        updateCbDeleteVariable();
        myCbReplaceAll.addItemListener(
                new ItemListener() {
                  public void itemStateChanged(ItemEvent e) {
                    updateCbDeleteVariable();
                  }
                }
        );
      }
    }
    return panel;
  }

  private void updateTypeSelector() {
    if (myCbReplaceAll != null) {
      myTypeSelectorManager.setAllOccurences(myCbReplaceAll.isSelected());
    } else {
      myTypeSelectorManager.setAllOccurences(false);
    }
  }

  private void updateCbDeleteVariable() {
    if (!myCbReplaceAll.isSelected()) {
      myCbDeleteVariable.makeUnselectable(false);
    } else {
      myCbDeleteVariable.makeSelectable();
    }
  }

  private void updateCbFinal() {
    if (!allowFinal()) {
      myCbFinal.makeUnselectable(false);
    } else {
      myCbFinal.makeSelectable();
    }
  }

  protected boolean allowFinal() {
    return true;
  }

  public void addOccurrenceListener(ItemListener itemListener) {
    if (myCbReplaceAll != null) {
      myCbReplaceAll.addItemListener(itemListener);
    }
  }

  public void addFinalListener(ItemListener itemListener) {
    myCbFinal.addItemListener(itemListener);
  }

  public void setReplaceAllOccurrences(boolean replaceAllOccurrences) {
    if (myCbReplaceAll != null) {
      myCbReplaceAll.setSelected(replaceAllOccurrences);
    }
  }

  public void setCreateFinal(boolean createFinal) {
    myCbFinal.setSelected(createFinal);
  }

  protected void enableFinal(boolean enable){
    myCbFinal.setEnabled(enable);
  }




}
