/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package git4idea.history.browser;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsListener;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.CalculateContinuation;
import com.intellij.util.CatchingConsumer;
import com.intellij.util.containers.HashMap;
import git4idea.GitBranch;
import git4idea.GitBranchesSearcher;
import git4idea.GitVcs;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class GitProjectLogManager implements ProjectComponent {
  private final static Logger LOG = Logger.getInstance("#git4idea.history.browser.GitProjectLogManager");
  private final Project myProject;
  private final ProjectLevelVcsManager myVcsManager;

  private final AtomicReference<Map<VirtualFile, Content>> myComponentsMap;
  private VcsListener myListener;

  public GitProjectLogManager(final Project project, final ProjectLevelVcsManager vcsManager) {
    myProject = project;
    myVcsManager = vcsManager;
    myComponentsMap = new AtomicReference<Map<VirtualFile, Content>>(new HashMap<VirtualFile, Content>());
    myListener = new VcsListener() {
      public void directoryMappingChanged() {
        recalculateWindows();
      }
    };
  }

  public static GitProjectLogManager getInstance(final Project project) {
    return project.getComponent(GitProjectLogManager.class);
  }

  public void projectClosed() {
    myVcsManager.removeVcsListener(myListener);
  }

  public void projectOpened() {
    StartupManager.getInstance(myProject).registerPostStartupActivity(new DumbAwareRunnable() {
      public void run() {
        myVcsManager.addVcsListener(myListener);
        recalculateWindows();
      }
    });
  }

  private void recalculateWindows() {
    final GitVcs vcs = GitVcs.getInstance(myProject);
    final VirtualFile[] roots = myVcsManager.getRootsUnderVcs(vcs);

    final Map<VirtualFile, Content> currentState = myComponentsMap.get();
    final Set<VirtualFile> currentKeys = new HashSet<VirtualFile>(currentState.keySet());
    currentKeys.removeAll(Arrays.asList(roots));

    final Map<VirtualFile, Content> newKeys = new HashMap<VirtualFile, Content>(currentState);

    final ChangesViewContentManager cvcm = ChangesViewContentManager.getInstance(myProject);

    final VirtualFile baseDir = myProject.getBaseDir();
    final ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
    for (final VirtualFile root : roots) {
      if (! currentState.containsKey(root)) {
        final GitLogTree tree = new GitLogTree(myProject, root);
        tree.setParentDisposable(myProject);
        tree.initView();
        final Content content = contentFactory.createContent(tree.getComponent(), getCaption(baseDir, root), false);
        content.setCloseable(false);
        cvcm.addContent(content);
        newKeys.put(root, content);
        
        new CalculateContinuation<String>().calculateAndContinue(new ThrowableComputable<String, Exception>() {
          public String compute() throws Exception {
            return getCaption(baseDir, root);
          }
        }, new CatchingConsumer<String, Exception>() {
          public void consume(Exception e) {
            //should not
          }
          public void consume(final String caption) {
            content.setDisplayName(caption);
          }
        });
      }
    }

    for (VirtualFile currentKey : currentKeys) {
      final Content content = newKeys.remove(currentKey);
      cvcm.removeContent(content);
    }

    myComponentsMap.set(newKeys);
  }

  private String getCaption(@Nullable VirtualFile baseDir, final VirtualFile root) {
    String result = root.getPresentableUrl();                                                                                      
    if (baseDir != null) {
      if (baseDir.equals(root)) {
        result = "<Project root>";
      } else {
        final String variant = VfsUtil.getRelativePath(baseDir, root, '/');
        if ((variant != null) && (variant.length() < result.length())) {
          result = variant;
        }
      }
    }

    GitBranchesSearcher searcher = null;
    try {
      searcher = new GitBranchesSearcher(myProject, root, false);
    }
    catch (VcsException e) {
      LOG.info(e);
    }

    if (searcher != null) {
      final GitBranch branch = searcher.getLocal();
      if (branch != null) {
        result += " (" + branch.getName() + ")";
      }
    }
    return "Log: " + result;
  }

  @NotNull
  public String getComponentName() {
    return "git4idea.history.browser.GitProjectLogManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }
}
