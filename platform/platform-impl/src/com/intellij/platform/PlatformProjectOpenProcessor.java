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
package com.intellij.platform;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.projectImport.ProjectOpenProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;

/**
 * @author max
 */
public class PlatformProjectOpenProcessor extends ProjectOpenProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.platform.PlatformProjectOpenProcessor");

  public static PlatformProjectOpenProcessor getInstance() {
    PlatformProjectOpenProcessor projectOpenProcessor = getInstanceIfItExists();
    assert projectOpenProcessor != null;
    return projectOpenProcessor;
  }

  public static PlatformProjectOpenProcessor getInstanceIfItExists() {
    ProjectOpenProcessor[] processors = Extensions.getExtensions(EXTENSION_POINT_NAME);
    for(ProjectOpenProcessor processor: processors) {
      if (processor instanceof PlatformProjectOpenProcessor) {
        return (PlatformProjectOpenProcessor) processor;
      }
    }
    return null;
  }

  public boolean canOpenProject(final VirtualFile file) {
    return file.isDirectory();
  }

  @Override
  public boolean isProjectFile(VirtualFile file) {
    return false;
  }

  @Override
  public boolean lookForProjectsInDirectory() {
    return false;
  }

  @Nullable
  public Project doOpenProject(@NotNull final VirtualFile virtualFile, @Nullable final Project projectToClose, final boolean forceOpenInNewFrame) {
    return doOpenProject(virtualFile, projectToClose, forceOpenInNewFrame, -1);
  }

  @Nullable
  public static Project doOpenProject(@NotNull final VirtualFile virtualFile,
                                      final Project projectToClose,
                                      final boolean forceOpenInNewFrame,
                                      final int line) {
    VirtualFile baseDir = virtualFile;
    if (!baseDir.isDirectory()) {
      baseDir = virtualFile.getParent();
      while (baseDir != null) {
        if (new File(baseDir.getPath(), ".idea").exists()) {
          break;
        }
        baseDir = baseDir.getParent();
      }
      if (baseDir == null) {
        baseDir = virtualFile.getParent();
      }
    }

    final File projectDir = new File(baseDir.getPath(), ".idea");

    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    if (!forceOpenInNewFrame && openProjects.length > 0) {
      int exitCode = ProjectUtil.confirmOpenNewProject(false);
      if (exitCode == 0) { // this window option
        if (!ProjectUtil.closeAndDispose(projectToClose != null ? projectToClose : openProjects[openProjects.length - 1])) return null;
      }
      else if (exitCode != 1) { // not in a new window
        return null;
      }
    }
    
    final ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
    Project project = null;
    if (projectDir.exists()) {
      try {
        for (ProjectOpenProcessor processor : ProjectOpenProcessor.EXTENSION_POINT_NAME.getExtensions()) {
          processor.refreshProjectFiles(projectDir);
        }
        
        project = ((ProjectManagerImpl) projectManager).convertAndLoadProject(baseDir.getPath());
      }
      catch (Exception e) {
        // ignore
      }
    }
    else {
      projectDir.mkdirs();
    }

    if (project == null) {
      project = projectManager.newProject(projectDir.getParentFile().getName(), projectDir.getParent(), true, false);
    }

    if (project == null) return null;
    ProjectBaseDirectory.getInstance(project).setBaseDir(baseDir);
    for(DirectoryProjectConfigurator configurator: Extensions.getExtensions(DirectoryProjectConfigurator.EP_NAME)) {
      try {
        configurator.configureProject(project, baseDir);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }

    openFileFromCommandLine(project, virtualFile, line);
    projectManager.openProject(project);

    return project;
  }

  private static void openFileFromCommandLine(final Project project, final VirtualFile virtualFile, final int line) {
    StartupManager.getInstance(project).registerPostStartupActivity(new DumbAwareRunnable() {
      public void run() {
        ToolWindowManager.getInstance(project).invokeLater(new Runnable() {
          public void run() {
            ToolWindowManager.getInstance(project).invokeLater(new Runnable() {
              public void run() {
                if (!virtualFile.isDirectory()) {
                  if (line > 0) {
                    new OpenFileDescriptor(project, virtualFile, line-1, 0).navigate(true);
                  }
                  else {
                    new OpenFileDescriptor(project, virtualFile).navigate(true);
                  }
                }
              }
            });
          }
        });
      }
    });
  }

  @Nullable
  public Icon getIcon() {
    return null;
  }

  public String getName() {
    return "text editor";
  }
}
