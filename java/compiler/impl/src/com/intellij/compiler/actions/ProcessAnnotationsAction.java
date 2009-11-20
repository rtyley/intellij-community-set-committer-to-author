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
package com.intellij.compiler.actions;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.impl.FileSetCompileScope;
import com.intellij.compiler.impl.ModuleCompileScope;
import com.intellij.compiler.impl.javaCompiler.AnnotationProcessingCompiler;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.compiler.CompilerFilter;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ProcessAnnotationsAction extends CompileActionBase {

  protected void doAction(DataContext dataContext, Project project) {
    final Module module = LangDataKeys.MODULE_CONTEXT.getData(dataContext);
    final CompilerFilter filter = new CompilerFilter() {
      public boolean acceptCompiler(com.intellij.openapi.compiler.Compiler compiler) {
        return compiler instanceof AnnotationProcessingCompiler;
      }
    };
    if (module != null) {
      CompilerManager.getInstance(project).make(new ModuleCompileScope(module, false), filter, null);
    }
    else {
      VirtualFile[] files = getCompilableFiles(project, PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext));
      if (files.length > 0) {
        CompilerManager.getInstance(project).make(new FileSetCompileScope(Arrays.asList(files), Module.EMPTY_ARRAY), filter, null);
      }
    }
  }

  public void update(AnActionEvent event) {
    super.update(event);
    Presentation presentation = event.getPresentation();
    if (!presentation.isEnabled()) {
      return;
    }
    DataContext dataContext = event.getDataContext();
    presentation.setVisible(false);

    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }

    CompilerConfiguration compilerConfiguration = CompilerConfiguration.getInstance(project);
    final Module module = LangDataKeys.MODULE.getData(dataContext);
    final Module moduleContext = LangDataKeys.MODULE_CONTEXT.getData(dataContext);

    if (!compilerConfiguration.isAnnotationProcessorsEnabled() ||
        compilerConfiguration.getAnnotationProcessorsMap().isEmpty() ||
        module != null && StringUtil.isEmpty(compilerConfiguration.getAnotationProcessedModules().get(module))) {
      presentation.setEnabled(false);
      return;
    }

    presentation.setVisible(true);
    presentation.setText(createPresentationText(""), true);
    final VirtualFile[] files = getCompilableFiles(project, PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext));
    if (moduleContext == null && files.length == 0) {
      presentation.setEnabled(false);
      return;
    }

    String elementDescription = null;
    if (moduleContext != null) {
      elementDescription = CompilerBundle.message("action.compile.description.module", moduleContext.getName());
    }
    else {
      PsiPackage aPackage = null;
      if (files.length == 1) {
        final PsiDirectory directory = PsiManager.getInstance(project).findDirectory(files[0]);
        if (directory != null) {
          aPackage = JavaDirectoryService.getInstance().getPackage(directory);
        }
      }
      else {
        PsiElement element = LangDataKeys.PSI_ELEMENT.getData(dataContext);
        if (element instanceof PsiPackage) {
          aPackage = (PsiPackage)element;
        }
      }

      if (aPackage != null) {
        String name = aPackage.getQualifiedName();
        if (name.length() == 0) {
          //noinspection HardCodedStringLiteral
          name = "<default>";
        }
        elementDescription = "'" + name + "'";
      }
      else if (files.length == 1) {
        final VirtualFile file = files[0];
        FileType fileType = FileTypeManager.getInstance().getFileTypeByFile(file);
        if (CompilerManager.getInstance(project).isCompilableFileType(fileType)) {
          elementDescription = "'" + file.getName() + "'";
        }
        else {
          if (!ActionPlaces.MAIN_MENU.equals(event.getPlace())) {
            // the action should be invisible in popups for non-java files
            presentation.setEnabled(false);
            presentation.setVisible(false);
            return;
          }
        }
      }
      else {
        elementDescription = CompilerBundle.message("action.compile.description.selected.files");
      }
    }

    if (elementDescription == null) {
      presentation.setEnabled(false);
      return;
    }

    presentation.setText(createPresentationText(elementDescription), true);
    presentation.setEnabled(true);
  }

  private static String createPresentationText(final String elementDescription) {
    int length = elementDescription.length();
    String target = length > 23 ? (StringUtil.startsWithChar(elementDescription, '\'') ? "'..." : "...") + elementDescription.substring(length - 20, length) : elementDescription;
    return MessageFormat.format(ActionsBundle.actionText("RunAPT"), target);
  }

  private static VirtualFile[] getCompilableFiles(Project project, VirtualFile[] files) {
    if (files == null || files.length == 0) {
      return VirtualFile.EMPTY_ARRAY;
    }
    final PsiManager psiManager = PsiManager.getInstance(project);
    final FileTypeManager typeManager = FileTypeManager.getInstance();
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final CompilerManager compilerManager = CompilerManager.getInstance(project);
    final List<VirtualFile> filesToCompile = new ArrayList<VirtualFile>();
    for (final VirtualFile file : files) {
      if (!fileIndex.isInSourceContent(file)) {
        continue;
      }
      if (!file.isInLocalFileSystem()) {
        continue;
      }
      if (file.isDirectory()) {
        final PsiDirectory directory = psiManager.findDirectory(file);
        if (directory == null || JavaDirectoryService.getInstance().getPackage(directory) == null) {
          continue;
        }
      }
      else {
        FileType fileType = typeManager.getFileTypeByFile(file);
        if (!(compilerManager.isCompilableFileType(fileType))) {
          continue;
        }
      }
      filesToCompile.add(file);
    }
    return filesToCompile.size() > 0 ? filesToCompile.toArray(new VirtualFile[filesToCompile.size()]) : VirtualFile.EMPTY_ARRAY;
  }
}