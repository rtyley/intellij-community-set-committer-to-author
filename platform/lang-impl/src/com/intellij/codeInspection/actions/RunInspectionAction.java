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
package com.intellij.codeInspection.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.analysis.AnalysisUIOptions;
import com.intellij.analysis.BaseAnalysisActionDialog;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.GotoActionBase;
import com.intellij.ide.util.gotoByName.ChooseByNamePopup;
import com.intellij.ide.util.gotoByName.ChooseByNamePopupComponent;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Arrays;

/**
 * @author Konstantin Bulenkov
 */
public class RunInspectionAction extends GotoActionBase {
  public RunInspectionAction() {
    getTemplatePresentation().setText(IdeBundle.message("goto.inspection.action.text"));
  }

  @Override
  protected void gotoActionPerformed(final AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final Component component = e.getData(PlatformDataKeys.CONTEXT_COMPONENT);
    final PsiElement psiElement = LangDataKeys.PSI_ELEMENT.getData(e.getDataContext());
    final PsiFile psiFile = LangDataKeys.PSI_FILE.getData(e.getDataContext());
    final VirtualFile virtualFile = LangDataKeys.VIRTUAL_FILE.getData(e.getDataContext());
    if (project == null || virtualFile == null) return;
    FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.goto.inspection");

    final ChooseByNamePopup popup = ChooseByNamePopup.createPopup(project, new GotoInspectionModel(project), getPsiContext(e));
    popup.invoke(new ChooseByNamePopupComponent.Callback() {
      public void onClose() {
        if (RunInspectionAction.class.equals(myInAction)) {
          myInAction = null;
        }
      }

      public void elementChosen(Object element) {
        final InspectionProfileEntry profileEntry = (InspectionProfileEntry)element;
        runInspection(project, profileEntry, virtualFile, psiElement, psiFile);
      }
    }, ModalityState.current(), true);
  }

  private static void runInspection(@NotNull Project project,
                                    @NotNull InspectionProfileEntry profileEntry,
                                    @NotNull VirtualFile virtualFile,
                                    PsiElement psiElement, PsiFile psiFile) {
    final String shortName = profileEntry.getShortName();
    final InspectionManagerEx managerEx = (InspectionManagerEx)InspectionManagerEx.getInstance(project);
    final Module module = ModuleUtil.findModuleForFile(virtualFile, project);

    AnalysisScope analysisScope = null;
    if (psiFile != null) {
      analysisScope = new AnalysisScope(psiFile);
    } else {
      if (virtualFile.isDirectory()) {
        final PsiDirectory psiDirectory = PsiManager.getInstance(project).findDirectory(virtualFile);
        if (psiDirectory != null) {
          analysisScope = new AnalysisScope(psiDirectory);
        }
      }
      if (analysisScope == null) {
        analysisScope = new AnalysisScope(project, Arrays.asList(virtualFile));
      }
    }
    final BaseAnalysisActionDialog dlg = new BaseAnalysisActionDialog(
      AnalysisScopeBundle.message("specify.analysis.scope", InspectionsBundle.message("inspection.action.title")),
      AnalysisScopeBundle.message("analysis.scope.title", InspectionsBundle.message("inspection.action.noun")),
      project,
      analysisScope,
      module != null ? module.getName() : null,
      true,
      AnalysisUIOptions.getInstance(project),
      psiElement);
    AnalysisScope scope = analysisScope;
    dlg.show();
    if (!dlg.isOK()) return;
    final AnalysisUIOptions uiOptions = AnalysisUIOptions.getInstance(project);
    scope = dlg.getScope(uiOptions, scope, project, module);

    final InspectionProfileImpl profile = new InspectionProfileImpl(profileEntry.getDisplayName());
    final InspectionProfileImpl model = (InspectionProfileImpl)profile.getModifiableModel();
    final InspectionProfileEntry[] profileEntries = model.getInspectionTools(null);
    for (InspectionProfileEntry entry : profileEntries) {
      model.disableTool(entry.getShortName());
    }
    model.enableTool(shortName);
    try {
      Element element = new Element("toCopy");
      profileEntry.writeSettings(element);
      model.getInspectionTool(shortName).readSettings(element);
    }
    catch (Exception e) {
      //skip
    }
    model.setEditable(profileEntry.getDisplayName());
    final GlobalInspectionContextImpl inspectionContext = managerEx.createNewGlobalContext(false);
    inspectionContext.setExternalProfile(model);
    inspectionContext.doInspections(scope, managerEx);
  }
}
