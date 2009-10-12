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

package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public class CreateHtmlAction extends CreateElementActionBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.actions.CreateHtmlAction");

  private final FileType myFileType;
  private final String myTemplateName;

  public CreateHtmlAction() {
    this(StdFileTypes.HTML, FileTemplateManager.INTERNAL_HTML_TEMPLATE_NAME);
  }

  protected CreateHtmlAction(FileType fileType, String templateName) {
    super(IdeBundle.message("action.create.new.filetype", fileType.getName()),
          IdeBundle.message("action.description.create.new.file", fileType.getName()), fileType.getIcon());

    myFileType = fileType;
    myTemplateName = templateName;
  }

  @NotNull
  protected PsiElement[] invokeDialog(Project project, PsiDirectory directory) {
    CreateElementActionBase.MyInputValidator validator = new CreateElementActionBase.MyInputValidator(project, directory);
    Messages.showInputDialog(project, IdeBundle.message("prompt.enter.new.filetype.name", myFileType.getName()),
                             IdeBundle.message("title.new.filetype", myFileType.getName()), Messages.getQuestionIcon(), "", validator);
    return validator.getCreatedElements();

  }

  protected void checkBeforeCreate(String newName, PsiDirectory directory) throws IncorrectOperationException {
    newName = getName(newName);
    directory.checkCreateFile(newName);
  }

  public static boolean isExtensionOfType(@NotNull final String fileName, @NotNull final FileType type) {
    final int i = fileName.lastIndexOf('.');
    if (i != -1) {
      final String ext = fileName.substring(i + 1);
      if (type.getDefaultExtension().equals(ext)) {
        return true;
      }
    }

    return false;
  }

  private String getName(String oldName) {
    if (isExtensionOfType(oldName, myFileType)) return oldName;
    return oldName + "." + myFileType.getDefaultExtension();
  }

  @NotNull
  protected PsiElement[] create(String newName, PsiDirectory directory) throws Exception {
    final FileTemplate template = FileTemplateManager.getInstance().getInternalTemplate(myTemplateName);

    PsiElement element;
    try {
      element = FileTemplateUtil
        .createFromTemplate(template, getName(newName), FileTemplateManager.getInstance().getDefaultProperties(), directory);
      final PsiFile psiFile = element.getContainingFile();

      final VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile != null) {
        FileEditorManager.getInstance(directory.getProject()).openFile(virtualFile, true);
        return new PsiElement[]{psiFile};
      }
    }
    catch (IncorrectOperationException e) {
      throw e;
    }
    catch (Exception e) {
      LOG.error(e);
    }

    return PsiElement.EMPTY_ARRAY;
  }

  protected String getErrorTitle() {
    return IdeBundle.message("title.cannot.create.filetype", myFileType.getName());
  }

  protected String getCommandName() {
    return IdeBundle.message("command.name.create.new.file", myFileType.getName());
  }

  protected String getActionName(PsiDirectory directory, String newName) {
    return IdeBundle.message("progress.creating.filetype.in.directory", myFileType.getName(), newName, directory.getName());
  }

  public void update(final AnActionEvent e) {
    super.update(e);
    Presentation presentation = e.getPresentation();
    final FileTypeManager manager = FileTypeManager.getInstance();
    final FileType fileType = manager.getFileTypeByExtension(HtmlFileType.DOT_DEFAULT_EXTENSION);
    if (fileType == FileTypes.PLAIN_TEXT) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
    }
  }
}
