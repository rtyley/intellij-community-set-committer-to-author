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
package com.intellij.lang.ant.config.impl;

import com.intellij.lang.ant.config.*;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.AntProject;
import com.intellij.lang.ant.psi.AntTarget;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class AntBuildModelImpl implements AntBuildModelBase {

  private final AntBuildFile myFile;

  public AntBuildModelImpl(final AntBuildFile buildFile) {
    myFile = buildFile;
  }

  @Nullable
  public String getDefaultTargetName() {
    final AntProject project = getAntProject();
    final AntTarget target = (project == null) ? null : project.getDefaultTarget();
    return (target == null) ? "" : target.getName();
  }

  @Nullable
  public String getName() {
    final AntProject project = getAntProject();
    return project != null? project.getName() : null;
  }

  public AntBuildTarget[] getTargets() {
    final List<AntBuildTargetBase> list = getTargetsList();
    return list.toArray(new AntBuildTargetBase[list.size()]);
  }

  public AntBuildTarget[] getFilteredTargets() {
    final List<AntBuildTargetBase> filtered = new ArrayList<AntBuildTargetBase>();
    for (final AntBuildTargetBase buildTarget : getTargetsList()) {
      if (myFile.isTargetVisible(buildTarget)) {
        filtered.add(buildTarget);
      }
    }
    return (filtered.size() == 0) ? AntBuildTargetBase.EMPTY_ARRAY : filtered.toArray(new AntBuildTargetBase[filtered.size()]);
  }

  @Nullable
  public String getDefaultTargetActionId() {
    if (getDefaultTargetName() == null) {
      return null;
    }
    final String modelName = getName();
    if (modelName == null || modelName.trim().length() == 0) {
      return null;
    }
    return AntConfiguration.getActionIdPrefix(getBuildFile().getProject()) + modelName;

  }

  public AntBuildFileBase getBuildFile() {
    return (AntBuildFileBase)myFile;
  }

  @Nullable
  public AntBuildTargetBase findTarget(final String name) {
    return ApplicationManager.getApplication().runReadAction(new Computable<AntBuildTargetBase>() {
      @Nullable
      public AntBuildTargetBase compute() {
        return findTargetImpl(name, AntBuildModelImpl.this);
      }
    });
  }

  @Nullable
  public BuildTask findTask(final String targetName, final String taskName) {
    final AntBuildTargetBase buildTarget = findTarget(targetName);
    return (buildTarget == null) ? null : buildTarget.findTask(taskName);
  }

  public AntProject getAntProject() {
    final AntFile antFile = (AntFile)getBuildFile().getAntFile();
    return antFile != null? antFile.getAntProject() : null;
  }

  public boolean hasTargetWithActionId(final String id) {
    final List<AntBuildTargetBase> targetsList = getTargetsList();
    for (AntBuildTargetBase buildTarget : targetsList) {
      if (id.equals(buildTarget.getActionId())) return true;
    }
    return false;
  }

  private List<AntBuildTargetBase> getTargetsList() {
    return ApplicationManager.getApplication().runReadAction(new Computable<List<AntBuildTargetBase>>() {
      public List<AntBuildTargetBase> compute() {
        return getTargetListImpl(AntBuildModelImpl.this);
      }
    });
  }

  @Nullable
  private static AntBuildTargetBase findTargetImpl(final String name, final AntBuildModelImpl model) {
    final List<AntBuildTargetBase> buildTargetBases = getTargetListImpl(model);
    for (AntBuildTargetBase targetBase : buildTargetBases) {
      if (Comparing.strEqual(targetBase.getName(), name)) {
        return targetBase;
      }
    }
    return null;
  }

  private static List<AntBuildTargetBase> getTargetListImpl(final AntBuildModelBase model) {
    final AntProject project = model.getAntProject();
    final AntTarget[] targets = (project == null) ? AntTarget.EMPTY_ARRAY : project.getTargets();
    final List<AntBuildTargetBase> list = new ArrayList<AntBuildTargetBase>(targets.length);
    final VirtualFile sourceFile = model.getBuildFile().getVirtualFile();
    final AntTarget defaultTarget = project != null? project.getDefaultTarget() : null;
    for (final AntTarget target : targets) {
      list.add(new AntBuildTargetImpl(target, model, sourceFile, false, target.equals(defaultTarget)));
    }
    if (project != null) {
      for (final AntTarget target : project.getImportedTargets()) {
        final PsiFile containingFile = target.getContainingFile();
        final AntTarget fileDefaultTarget;
        if (containingFile instanceof AntFile) {
          final AntProject antProject = ((AntFile)containingFile).getAntProject();
          fileDefaultTarget =  antProject != null? antProject.getDefaultTarget() : null;
        }
        else {
          fileDefaultTarget = null;
        }
        list.add(new AntBuildTargetImpl(target, model, containingFile != null? containingFile.getVirtualFile() : null, true, target.equals(fileDefaultTarget)));
      }
    }
    return list;
  }
}
