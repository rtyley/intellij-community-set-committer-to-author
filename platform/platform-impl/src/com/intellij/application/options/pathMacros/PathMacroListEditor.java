package com.intellij.application.options.pathMacros;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurationException;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;

/**
 * @author dsl
 */
public class PathMacroListEditor {
  JPanel myPanel;
  JButton myAddButton;
  JButton myRemoveButton;
  JButton myEditButton;
  JScrollPane myScrollPane;
  private PathMacroTable myPathMacroTable;

  public PathMacroListEditor() {
    this(null);
  }

  public PathMacroListEditor(final Collection<String> undefinedMacroNames) {
    myPathMacroTable = undefinedMacroNames != null ? new PathMacroTable(undefinedMacroNames) : new PathMacroTable();
    myScrollPane.setViewportView(myPathMacroTable);
    myAddButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myPathMacroTable.addMacro();
      }
    });
    myRemoveButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myPathMacroTable.removeSelectedMacros();
      }
    });
    myEditButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myPathMacroTable.editMacro();
      }
    });
  }

  public void commit() throws ConfigurationException {
    final int count = myPathMacroTable.getRowCount();
    for (int idx = 0; idx < count; idx++) {
      String value = myPathMacroTable.getMacroValueAt(idx);
      if (value == null || value.length() == 0) {
        throw new ConfigurationException(
            ApplicationBundle.message("error.path.variable.is.undefined", myPathMacroTable.getMacroNameAt(idx)));
      }
    }
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        myPathMacroTable.commit();
      }
    });
  }

  public JComponent getPanel() {
    return myPanel;
  }

  public void reset() {
    myPathMacroTable.reset();
  }

  public boolean isModified() {
    return myPathMacroTable.isModified();
  }

  private void createUIComponents() {
  }
}
