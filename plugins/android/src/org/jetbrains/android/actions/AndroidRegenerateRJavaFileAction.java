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
package org.jetbrains.android.actions;

import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.compiler.AndroidAptCompiler;
import org.jetbrains.android.compiler.AndroidCompileUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidRegenerateRJavaFileAction extends AnAction {
  public AndroidRegenerateRJavaFileAction() {
    super(AndroidBundle.message("android.actions.regenerate.r.java.file.title"), null, AndroidUtils.ANDROID_ICON);
  }

  @Override
  public void update(AnActionEvent e) {
    final Module module = e.getData(DataKeys.MODULE);
    final Project project = e.getData(DataKeys.PROJECT);
    e.getPresentation().setEnabled(isAvailable(module, project));
  }

  private static boolean isAvailable(Module module, Project project) {
    if (module != null) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null) {
        return AndroidAptCompiler.isToCompileModule(module, facet.getConfiguration());
      }
    }
    else if (project != null) {
      List<AndroidFacet> facets = ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID);
      for (AndroidFacet facet : facets) {
        if (AndroidAptCompiler.isToCompileModule(facet.getModule(), facet.getConfiguration())) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Module module = e.getData(DataKeys.MODULE);
    final Project project = e.getData(DataKeys.PROJECT);
    ApplicationManager.getApplication().saveAll();
    ProgressManager.getInstance().run(
      new Task.Backgroundable(project, AndroidBundle.message("android.compile.messages.generating.r.java"), true) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          doRun(indicator, module, project);
        }
      });
  }

  private static void doRun(ProgressIndicator indicator, Module module, Project project) {
    if (module != null) {
      if (indicator.isCanceled()) {
        return;
      }
      AndroidCompileUtil.generate(module, new AndroidAptCompiler(), false);
      return;
    }
    assert project != null;
    List<AndroidFacet> facets = ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID);
    List<Module> modulesToProcess = new ArrayList<Module>();
    for (AndroidFacet facet : facets) {
      if (indicator.isCanceled()) {
        return;
      }
      module = facet.getModule();
      if (AndroidAptCompiler.isToCompileModule(module, facet.getConfiguration())) {
        modulesToProcess.add(module);
      }
    }
    if (modulesToProcess.size() == 0) {
      return;
    }
    if (modulesToProcess.size() == 1) {
      AndroidCompileUtil.generate(modulesToProcess.get(0), new AndroidAptCompiler(), false);
      return;
    }
    double step = 1.0 / modulesToProcess.size();
    double progress = 0.0;
    indicator.setText(AndroidBundle.message("android.compile.messages.generating.r.java"));
    indicator.setFraction(progress);
    for (int i = 0, n = modulesToProcess.size(); i < n; i++) {
      AndroidCompileUtil.generate(modulesToProcess.get(i), new AndroidAptCompiler(), false);
      progress = i < n - 1 ? progress + step : 1.0;
      indicator.setFraction(progress);
    }
  }
}
