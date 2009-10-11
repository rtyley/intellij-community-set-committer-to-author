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
package com.intellij.uiDesigner.binding;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.editor.UIFormEditor;
import com.intellij.uiDesigner.palette.ComponentItem;
import com.intellij.uiDesigner.palette.Palette;
import com.intellij.util.Icons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 28.10.2005
 * Time: 17:57:34
 * To change this template use File | Settings | File Templates.
 */
public class BoundIconRenderer extends GutterIconRenderer {
  private final PsiElement myElement;
  private Icon myIcon;

  public BoundIconRenderer(final PsiElement field) {
    myElement = field;
    if (myElement instanceof PsiField) {
      final PsiType type = ((PsiField)myElement).getType();
      if (type instanceof PsiClassType) {
        PsiClass componentClass = ((PsiClassType)type).resolve();
        if (componentClass != null) {
          String qName = componentClass.getQualifiedName();
          if (qName != null) {
            final ComponentItem item = Palette.getInstance(myElement.getProject()).getItem(qName);
            if (item != null) {
              myIcon = item.getIcon();
            }
          }
        }
      }
    }
  }

  @NotNull
  public Icon getIcon() {
    if (myIcon != null) {
      return myIcon;
    }
    return Icons.UI_FORM_ICON;
  }

  public boolean isNavigateAction() {
    return true;
  }

  @Nullable
  public AnAction getClickAction() {
    return new AnAction() {
      public void actionPerformed(AnActionEvent e) {
        List<PsiFile> formFiles = getBoundFormFiles();
        if (formFiles.size() > 0) {
          VirtualFile virtualFile = formFiles.get(0).getVirtualFile();
          Project project = myElement.getProject();
          FileEditor[] editors = FileEditorManager.getInstance(project).openFile(virtualFile, true);
          if (myElement instanceof PsiField) {
            for (FileEditor editor: editors) {
              if (editor instanceof UIFormEditor) {
                ((UIFormEditor)editor).selectComponent(((PsiField) myElement).getName());
              }
            }
          }
        }
      }
    };
  }

  @Nullable
  public String getTooltipText() {
    List<PsiFile> formFiles = getBoundFormFiles();

    if (formFiles.size() > 0) {
      return composeText(formFiles);
    }
    return super.getTooltipText();
  }

  private List<PsiFile> getBoundFormFiles() {
    List<PsiFile> formFiles = Collections.emptyList();
    PsiClass aClass;
    if (myElement instanceof PsiField) {
      aClass = ((PsiField) myElement).getContainingClass();
    }
    else {
      aClass = (PsiClass) myElement;
    }
    if (aClass != null && aClass.getQualifiedName() != null) {
      formFiles = FormClassIndex.findFormsBoundToClass(aClass);
    }
    return formFiles;
  }

  private static String composeText(final List<PsiFile> formFiles) {
    @NonNls StringBuilder result = new StringBuilder("<html><body>");
    result.append(UIDesignerBundle.message("ui.is.bound.header"));
    @NonNls String sep = "";
    for (PsiFile file: formFiles) {
      result.append(sep);
      sep = "<br>";
      result.append("&nbsp;&nbsp;&nbsp;&nbsp;");
      result.append(file.getName());
    }
    result.append("</body></html>");
    return result.toString();
  }
}
