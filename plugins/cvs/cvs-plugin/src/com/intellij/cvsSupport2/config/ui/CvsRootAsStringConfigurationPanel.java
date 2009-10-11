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
package com.intellij.cvsSupport2.config.ui;

import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.cvsSupport2.cvsoperations.cvsEdit.ui.EditCvsConfigurationFieldByFieldDialog;
import com.intellij.cvsSupport2.ui.CvsRootChangeListener;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.BooleanValueHolder;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

/**
 * author: lesya
 */
public class CvsRootAsStringConfigurationPanel {
  private JTextField myCvsRoot;
  private JButton myEditFieldByFieldButton;
  private JLabel myRootLabel;
  private final BooleanValueHolder myIsInUpdating;
  private final Collection<CvsRootChangeListener> myCvsRootListeners = new ArrayList<CvsRootChangeListener>();
  private JPanel myPanel;

  public CvsRootAsStringConfigurationPanel(BooleanValueHolder isInUpdating) {
    myIsInUpdating = isInUpdating;
    myRootLabel.setLabelFor(myCvsRoot);
    myCvsRoot.getDocument().addDocumentListener(new DocumentAdapter() {
      public void textChanged(DocumentEvent event) {
        notifyListeners();
      }
    });

    myEditFieldByFieldButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        CvsRootConfiguration cvsRootConfiguration = CvsApplicationLevelConfiguration.createNewConfiguration(CvsApplicationLevelConfiguration.getInstance());
        saveTo(cvsRootConfiguration, false);
        EditCvsConfigurationFieldByFieldDialog dialog
          = new EditCvsConfigurationFieldByFieldDialog(myCvsRoot.getText());
        dialog.show();
        if (dialog.isOK()){
          myCvsRoot.setText(dialog.getConfiguration());
        }
      }
    });
  }

  protected void notifyListeners() {
    if (myIsInUpdating.getValue()) return;
    for (Iterator<CvsRootChangeListener> each = myCvsRootListeners.iterator(); each.hasNext();) {
      CvsRootChangeListener cvsRootChangeListener = each.next();
      cvsRootChangeListener.onCvsRootChanged();
    }
  }

  public JPanel getPanel() {
    return myPanel;
  }

  public void addCvsRootChangeListener(CvsRootChangeListener listener) {
    myCvsRootListeners.add(listener);
  }



  public void updateFrom(CvsRootConfiguration config) {
    myCvsRoot.setText(config.CVS_ROOT);
    myCvsRoot.selectAll();
    myCvsRoot.requestFocus();
  }

  public void saveTo(CvsRootConfiguration config, boolean checkParameters) {
    config.CVS_ROOT = myCvsRoot.getText().trim();
  }

  public String getCvsRoot() {
    return myCvsRoot.getText().trim();
  }

  public JComponent getPreferredFocusedComponent() {
    return myCvsRoot;
  }

  public void setReadOnly() {
    myCvsRoot.setEditable(false);
    myEditFieldByFieldButton.setEnabled(false);
  }

}
