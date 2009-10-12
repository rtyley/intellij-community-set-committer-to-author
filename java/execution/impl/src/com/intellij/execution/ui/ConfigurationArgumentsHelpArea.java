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
package com.intellij.execution.ui;

import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.PopupHandler;
import com.intellij.util.ui.EmptyClipboardOwner;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;

public class ConfigurationArgumentsHelpArea extends JPanel {
  private JTextArea myHelpArea;
  private JPanel myPanel;
  private JLabel myLabel;

  public ConfigurationArgumentsHelpArea() {
    super(new BorderLayout());
    myHelpArea.addMouseListener(
      new PopupHandler(){
        public void invokePopup(final Component comp,final int x,final int y){
          createPopupMenu().getComponent().show(comp,x,y);
        }
      }
    );
    add(myPanel);
  }

  private ActionPopupMenu createPopupMenu() {
    final DefaultActionGroup group = new DefaultActionGroup();
    group.add(new MyCopyAction());
    return ActionManager.getInstance().createActionPopupMenu(ActionPlaces.UNKNOWN,group);
  }

  public void updateText(final String text) {
    myHelpArea.setText(text);
  }

  public void setLabelText(final String text) {
    myLabel.setText(text);
  }

  public String getLabelText() {
    return myLabel.getText();
  }

  private class MyCopyAction extends AnAction {
    public MyCopyAction() {
      super(ExecutionBundle.message("run.configuration.arguments.help.panel.copy.action.name"));
    }

    public void actionPerformed(final AnActionEvent e) {
      try {
        final StringSelection contents = new StringSelection(myHelpArea.getText().trim());
        final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
        if (project == null) {
          final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
          clipboard.setContents(contents, EmptyClipboardOwner.INSTANCE);
        } else {
          CopyPasteManager.getInstance().setContents(contents);
        }
      } catch(Exception ex) {
      }
    }
  }


}
