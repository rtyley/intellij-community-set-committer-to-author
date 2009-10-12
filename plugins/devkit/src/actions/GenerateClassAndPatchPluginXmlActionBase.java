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
package org.jetbrains.idea.devkit.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.idea.devkit.util.ComponentType;

import javax.swing.*;

/**
 * @author max
 */
public abstract class GenerateClassAndPatchPluginXmlActionBase extends GeneratePluginClassAction {
  public GenerateClassAndPatchPluginXmlActionBase(String text, String description, Icon icon) {
    super(text, description, icon);
  }

  protected abstract String getClassNamePrompt();
  protected abstract String getClassNamePromptTitle();

  protected PsiElement[] invokeDialogImpl(Project project, PsiDirectory directory) {
    MyInputValidator validator = new MyInputValidator(project, directory);
    Messages.showInputDialog(project, getClassNamePrompt(), getClassNamePromptTitle(), Messages.getQuestionIcon(), "", validator);
    return validator.getCreatedElements();
  }

  protected void checkBeforeCreate(String newName, PsiDirectory directory) throws IncorrectOperationException {
    JavaDirectoryService.getInstance().checkCreateClass(directory, newName);
    super.checkBeforeCreate(newName, directory);
  }

  protected abstract ComponentType getComponentType();

  public void patchPluginXml(XmlFile pluginXml, PsiClass klass) throws IncorrectOperationException {
    getComponentType().patchPluginXml(pluginXml, klass);
  }
}
