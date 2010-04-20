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
package org.jetbrains.idea.maven.dom.refactorings.extract;

import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlElement;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.actions.BaseRefactoringAction;
import com.intellij.util.Function;
import com.intellij.util.containers.hash.HashSet;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomProjectProcessorUtils;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;

import java.util.Collections;
import java.util.Set;

public class ExtractDependenciesAction extends BaseRefactoringAction {

  public ExtractDependenciesAction() {
    setInjectedContext(true);
  }

  protected boolean isAvailableInEditorOnly() {
    return true;
  }

  protected boolean isEnabledOnElements(PsiElement[] elements) {
    return false;
  }

  @Override
  protected boolean isAvailableForLanguage(Language language) {
    return true;
  }

  protected RefactoringActionHandler getHandler(DataContext dataContext) {
    return new MyRefactoringActionHandler();

  }

  @Override
  protected boolean isAvailableForFile(PsiFile file) {
    return MavenDomUtil.isMavenFile(file);
  }

  private static class MyRefactoringActionHandler implements RefactoringActionHandler {
    public void invoke(@NotNull final Project project, final Editor editor, PsiFile file, DataContext dataContext) {
      final MavenDomDependency dependency =
        DomUtil.findDomElement(file.findElementAt(editor.getCaretModel().getOffset()), MavenDomDependency.class);

      if (dependency != null && !isManagedDependency(dependency)) {
        Function<MavenDomProjectModel, Set<MavenDomDependency>> funOccurrences = getOccurencesFunction(dependency);

        final Pair<MavenDomProjectModel, Set<MavenDomDependency>> processData =
          getProcessData(project, getParentProjects(project, file), funOccurrences);

        if (processData != null) {
          final MavenDomProjectModel model = processData.first;
          final Set<MavenDomDependency> usages = processData.getSecond();

          assert model != null;
          assert usages != null;

          new WriteCommandAction(project, getFiles(file, model, usages)) {
            @Override
            protected void run(Result result) throws Throwable {
              MavenDomDependency addedDependency = model.getDependencyManagement().getDependencies().addDependency();
              addedDependency.getGroupId().setStringValue(dependency.getGroupId().getStringValue());
              addedDependency.getArtifactId().setStringValue(dependency.getArtifactId().getStringValue());
              addedDependency.getVersion().setStringValue(dependency.getVersion().getStringValue());
              String typeValue = dependency.getType().getStringValue();
              if (typeValue != null) {
                addedDependency.getType().setStringValue(typeValue);
              }
              
              String classifier = dependency.getClassifier().getStringValue();
              if (classifier != null) {
                addedDependency.getClassifier().setStringValue(classifier);
              }

              String systemPath = dependency.getSystemPath().getStringValue();
              if (systemPath != null) {
                addedDependency.getSystemPath().setStringValue(systemPath);
              }

              dependency.getVersion().undefine();

              for (MavenDomDependency usage : usages) {
                usage.getVersion().undefine();
              }
            }
          }.execute();
        }
      }
    }

    private static PsiFile[] getFiles(@NotNull PsiFile file, @NotNull MavenDomProjectModel model, @NotNull Set<MavenDomDependency> usages) {
      Set<PsiFile> files = new HashSet<PsiFile>();

      files.add(file);
      XmlElement xmlElement = model.getXmlElement();
      if (xmlElement != null) files.add(xmlElement.getContainingFile());
      for (MavenDomDependency usage : usages) {
        XmlElement element = usage.getXmlElement();
        if (element != null) {
          files.add(element.getContainingFile());
        }
      }

      return files.toArray(new PsiFile[files.size()]);
    }

    @Nullable
    private static Pair<MavenDomProjectModel, Set<MavenDomDependency>> getProcessData(@NotNull Project project,
                                                                                      @NotNull Set<MavenDomProjectModel> models,
                                                                                      @NotNull Function<MavenDomProjectModel, Set<MavenDomDependency>> funOccurrences) {
      if (models.size() == 0) return null;

      if (models.size() == 1) {
        MavenDomProjectModel model = models.iterator().next();
        if (funOccurrences.fun(model).size() == 0) {
          return Pair.create(model, Collections.<MavenDomDependency>emptySet());
        }
      }

      SelectMavenProjectDialog dialog = new SelectMavenProjectDialog(project, models, funOccurrences);
      dialog.show();

      if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
        MavenDomProjectModel model = dialog.getSelectedProject();
        return Pair
          .create(model, dialog.isReplaceAllOccurrences() ? funOccurrences.fun(model) : Collections.<MavenDomDependency>emptySet());
      }

      return null;
    }

    private static Function<MavenDomProjectModel, Set<MavenDomDependency>> getOccurencesFunction(final MavenDomDependency dependency) {

      return new Function<MavenDomProjectModel, Set<MavenDomDependency>>() {
        public Set<MavenDomDependency> fun(MavenDomProjectModel model) {
          String groupId = dependency.getGroupId().getStringValue();
          String artifactId = dependency.getArtifactId().getStringValue();

          return MavenDomProjectProcessorUtils.searchDependencyUsages(model, groupId, artifactId, Collections.singleton(dependency));
        }
      };
    }

    @NotNull
    private Set<MavenDomProjectModel> getParentProjects(@NotNull Project project, @NotNull PsiFile file) {
      final MavenDomProjectModel model = MavenDomUtil.getMavenDomModel(file, MavenDomProjectModel.class);

      if (model == null) return Collections.emptySet();
      return MavenDomProjectProcessorUtils.collectParentProjects(model);
    }

    private static boolean isManagedDependency(@NotNull MavenDomDependency dependency) {
      return MavenDomProjectProcessorUtils.searchManagingDependency(dependency) != null;
    }

    public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    }

  }
}