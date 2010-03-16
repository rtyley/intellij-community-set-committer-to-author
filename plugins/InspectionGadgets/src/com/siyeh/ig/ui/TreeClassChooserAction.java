/*
 * Copyright 2010 Bas Leijdekkers
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
package com.siyeh.ig.ui;

import com.intellij.ide.DataManager;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.siyeh.InspectionGadgetsBundle;

import javax.swing.AbstractAction;
import javax.swing.ListSelectionModel;
import java.awt.EventQueue;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;

public class TreeClassChooserAction extends AbstractAction {

    private final IGTable table;
    private final String chooserTitle;

    public TreeClassChooserAction(IGTable table, String chooserTitle) {
        this.table = table;
        this.chooserTitle = chooserTitle;
        putValue(NAME, InspectionGadgetsBundle.message("button.add"));
    }

    public void actionPerformed(ActionEvent e) {
        final DataManager dataManager = DataManager.getInstance();
        final DataContext dataContext = dataManager.getDataContext();
        final Project project = DataKeys.PROJECT.getData(dataContext);
        final TreeClassChooserFactory chooserFactory =
                TreeClassChooserFactory.getInstance(project);
        final TreeClassChooser classChooser =
                chooserFactory.createAllProjectScopeChooser(chooserTitle);
        classChooser.showDialog();
        final PsiClass selectedClass = classChooser.getSelectedClass();
        if (selectedClass == null) {
            return;
        }
        final String qualifiedName = selectedClass.getQualifiedName();
        final ListWrappingTableModel tableModel = table.getModel();
        final int index = tableModel.indexOf(qualifiedName, 0);
        final int rowIndex;
        if (index < 0) {
            tableModel.addRow(qualifiedName);
            rowIndex = tableModel.getRowCount() - 1;
        } else {
            rowIndex = index;
        }
        final ListSelectionModel selectionModel =
                table.getSelectionModel();
        selectionModel.setSelectionInterval(rowIndex, rowIndex);
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                final Rectangle rectangle =
                        table.getCellRect(rowIndex, 0, true);
                table.scrollRectToVisible(rectangle);
            }
        });
    }
}
