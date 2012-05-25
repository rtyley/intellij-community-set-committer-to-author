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
package com.intellij.designer.propertyTable.editors;

import com.intellij.designer.model.RadComponent;
import com.intellij.designer.propertyTable.InplaceContext;
import com.intellij.designer.propertyTable.PropertyEditor;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Alexander Lobas
 */
public class TextEditor extends PropertyEditor {
  protected final JTextField myTextField = new JTextField();

  public TextEditor() {
    myTextField.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        fireValueCommitted(true, false);
      }
    });
    myTextField.getDocument().addDocumentListener(
      new DocumentAdapter() {
        protected void textChanged(final DocumentEvent e) {
          preferredSizeChanged();
        }
      }
    );
  }

  @NotNull
  @Override
  public JComponent getComponent(@NotNull RadComponent rootComponent,
                                 @Nullable RadComponent component,
                                 Object value,
                                 @Nullable InplaceContext inplaceContext) {
    setEditorValue(component, value);
    if (inplaceContext == null) {
      myTextField.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
    }
    else {
      myTextField.setBorder(UIUtil.getTextFieldBorder());
      if (inplaceContext.isStartChar()) {
        myTextField.setText(inplaceContext.getText(myTextField.getText()));
      }
    }
    return myTextField;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTextField;
  }

  @Override
  public Object getValue() throws Exception {
    return myTextField.getText();
  }

  protected void setEditorValue(@Nullable RadComponent component, Object value) {
    myTextField.setText(value == null ? "" : value.toString());
  }

  @Override
  public void updateUI() {
    SwingUtilities.updateComponentTreeUI(myTextField);
  }
}