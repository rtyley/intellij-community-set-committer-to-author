/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.svn17.update;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vcs.update.*;
import org.jetbrains.idea.svn17.SvnConfiguration17;
import org.jetbrains.idea.svn17.SvnVcs17;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.util.Arrays;
import java.util.LinkedHashMap;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 2/16/12
 * Time: 5:05 PM
 */
public class AutoSvnUpdater extends AbstractCommonUpdateAction {
  private final Project myProject;
  private final FilePath[] myRoots;

  public AutoSvnUpdater(final Project project, final FilePath[] roots) {
    super(BlindUpdateAction.ourInstance, new AutoUpdateScope(roots), false);
    myProject = project;
    myRoots = roots;
  }

  @Override
  protected void actionPerformed(VcsContext context) {
    final SvnConfiguration17 configuration17 = SvnConfiguration17.getInstance(myProject);
    configuration17.FORCE_UPDATE = false;
    configuration17.UPDATE_LOCK_ON_DEMAND = false;
    configuration17.UPDATE_DEPTH = SVNDepth.INFINITY;
    final SvnVcs17 vcs = SvnVcs17.getInstance(myProject);
    for (FilePath root : myRoots) {
      final UpdateRootInfo info = configuration17.getUpdateRootInfo(root.getIOFile(), vcs);
      info.setRevision(SVNRevision.HEAD);
      info.setUpdateToRevision(false);
    }
    super.actionPerformed(context);
  }

  @Override
  protected boolean filterRootsBeforeAction() {
    return false;
  }

  private static class BlindUpdateAction implements ActionInfo {
    private final static BlindUpdateAction ourInstance = new BlindUpdateAction();

    @Override
    public boolean showOptions(Project project) {
      return false;
    }

    @Override
    public UpdateEnvironment getEnvironment(AbstractVcs vcs) {
      return vcs.getUpdateEnvironment();
    }

    @Override
    public UpdateOrStatusOptionsDialog createOptionsDialog(Project project,
                                                           LinkedHashMap<Configurable, AbstractVcs> envToConfMap,
                                                           String scopeName) {
      // should not be called
      return null;
    }

    @Override
    public String getActionName(String scopeName) {
      return ActionInfo.UPDATE.getActionName(scopeName);
    }

    @Override
    public String getActionName() {
      return ActionInfo.UPDATE.getActionName();
    }

    @Override
    public String getGroupName(FileGroup fileGroup) {
      return ActionInfo.UPDATE.getGroupName(fileGroup);
    }

    @Override
    public boolean canGroupByChangelist() {
      return ActionInfo.UPDATE.canGroupByChangelist();
    }

    @Override
    public boolean canChangeFileStatus() {
      return ActionInfo.UPDATE.canChangeFileStatus();
    }
  }

  private static class AutoUpdateScope implements ScopeInfo {
    private final FilePath[] myRoots;

    private AutoUpdateScope(final FilePath[] roots) {
      myRoots = roots;
    }

    @Override
    public FilePath[] getRoots(VcsContext context, ActionInfo actionInfo) {
      return myRoots;
    }

    @Override
    public String getScopeName(VcsContext dataContext, ActionInfo actionInfo) {
      return "Subversion";
    }

    @Override
    public boolean filterExistsInVcs() {
      return false;
    }
  }
}
