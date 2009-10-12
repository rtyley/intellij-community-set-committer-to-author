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
package com.intellij.refactoring.ui;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.ui.EditorTextField;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

/**
 * @author dsl
 */
public class CodeFragmentTableCellRenderer implements TableCellRenderer {
  private final Project myProject;

  public CodeFragmentTableCellRenderer(Project project) {
    myProject = project;
  }

  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    PsiCodeFragment codeFragment = (PsiCodeFragment)value;

    if (codeFragment != null) {
      Document document = PsiDocumentManager.getInstance(myProject).getDocument(codeFragment);
      return new EditorTextField(document, myProject, StdFileTypes.JAVA) {
        protected boolean shouldHaveBorder() {
          return false;
        }
      };
    }
    else {
      return new EditorTextField("", myProject, StdFileTypes.JAVA) {
        protected boolean shouldHaveBorder() {
          return false;
        }        
      };
    }
  }
}
