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

package com.intellij.packageDependencies.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.analysis.PerformAnalysisInBackgroundOption;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.packageDependencies.DependenciesBuilder;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.packageDependencies.DependencyValidationManagerImpl;
import com.intellij.packageDependencies.ForwardDependenciesBuilder;
import com.intellij.packageDependencies.ui.DependenciesPanel;
import com.intellij.psi.PsiFile;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;

import javax.swing.*;
import java.util.*;

public class AnalyzeDependenciesHandler {
  private final Project myProject;
  private final List<AnalysisScope> myScopes;
  private final int myTransitiveBorder;
  private final Set<PsiFile> myExcluded;

  public AnalyzeDependenciesHandler(Project project, List<AnalysisScope> scopes, int transitiveBorder, Set<PsiFile> excluded) {
    myProject = project;
    myScopes = scopes;
    myTransitiveBorder = transitiveBorder;
    myExcluded = excluded;
  }

  public AnalyzeDependenciesHandler(final Project project, final AnalysisScope scope, final int transitiveBorder) {
    this(project, Collections.singletonList(scope), transitiveBorder, new HashSet<PsiFile>());
  }

  public void analyze() {
    final List<DependenciesBuilder> builders = new ArrayList<DependenciesBuilder>();
    for (AnalysisScope scope : myScopes) {
      builders.add(new ForwardDependenciesBuilder(myProject, scope, myTransitiveBorder));
    }
    final Runnable process = new Runnable() {
      public void run() {
        for (final DependenciesBuilder builder : builders) {
          builder.analyze();
        }
      }
    };
    final Runnable successRunnable = new Runnable() {
      public void run() {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            DependenciesPanel panel = new DependenciesPanel(myProject, builders, myExcluded);
            Content content = ContentFactory.SERVICE.getInstance().createContent(panel, AnalysisScopeBundle.message(
              "package.dependencies.toolwindow.title", builders.get(0).getScope().getDisplayName()), false);
            content.setDisposer(panel);
            panel.setContent(content);
            ((DependencyValidationManagerImpl)DependencyValidationManager.getInstance(myProject)).addContent(content);
          }
        });
      }
    };
    ProgressManager.getInstance().runProcessWithProgressAsynchronously(myProject,
                                                                       AnalysisScopeBundle.message("package.dependencies.progress.title"),
                                                                       process, successRunnable, null,
                                                                       new PerformAnalysisInBackgroundOption(myProject));
  }
}