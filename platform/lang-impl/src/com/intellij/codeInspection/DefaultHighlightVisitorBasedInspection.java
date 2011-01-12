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

package com.intellij.codeInspection;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.impl.*;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.codeInspection.ex.JobDescriptor;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class DefaultHighlightVisitorBasedInspection extends GlobalInspectionTool {
  private static final JobDescriptor ANNOTATOR = new JobDescriptor(InspectionsBundle.message("inspection.processing.job.descriptor2"));
  private final boolean highlightErrorElements;
  private final boolean runAnnotators;

  public DefaultHighlightVisitorBasedInspection(boolean highlightErrorElements, boolean runAnnotators) {
    this.highlightErrorElements = highlightErrorElements;
    this.runAnnotators = runAnnotators;
  }

  public static class AnnotatorBasedInspection extends DefaultHighlightVisitorBasedInspection {
    public AnnotatorBasedInspection() {
      super(false, true);
    }
    @Nls
    @NotNull
    @Override
    public String getDisplayName() {
      return "Annotator";
    }

    @NotNull
    @Override
    public String getShortName() {
      return "Annotator";
    }

  }
  public static class SyntaxErrorInspection extends DefaultHighlightVisitorBasedInspection {
    public SyntaxErrorInspection() {
      super(true, false);
    }
    @Nls
    @NotNull
    @Override
    public String getDisplayName() {
      return "Syntax error";
    }

    @NotNull
    @Override
    public String getShortName() {
      return "SyntaxError";
    }
  }

  @Override
  public boolean isGraphNeeded() {
    return false;
  }

  @Override
  public JobDescriptor[] getAdditionalJobs() {
    return new JobDescriptor[]{ANNOTATOR};
  }

  @NotNull
  @Override
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  @Override
  public void runInspection(AnalysisScope scope,
                            InspectionManager manager,
                            GlobalInspectionContext globalContext,
                            ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    ANNOTATOR.setTotalAmount(scope.getFileCount());
    PsiElementVisitor visitor = new MyPsiElementVisitor(manager, globalContext, problemDescriptionsProcessor, highlightErrorElements,runAnnotators);
    scope.accept(visitor);
  }

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return GENERAL_GROUP_NAME;
  }

  private static class MyPsiElementVisitor extends PsiElementVisitor {
    private final InspectionManager myManager;
    private final GlobalInspectionContext myGlobalContext;
    private final ProblemDescriptionsProcessor myProblemDescriptionsProcessor;
    private final boolean highlightErrorElements;
    private final boolean runAnnotators;

    public MyPsiElementVisitor(final InspectionManager manager,
                               final GlobalInspectionContext globalContext,
                               final ProblemDescriptionsProcessor problemDescriptionsProcessor,
                               boolean highlightErrorElements,
                               boolean runAnnotators) {
      myManager = manager;
      myGlobalContext = globalContext;
      myProblemDescriptionsProcessor = problemDescriptionsProcessor;
      this.highlightErrorElements = highlightErrorElements;
      this.runAnnotators = runAnnotators;
    }

    @Override
    public void visitFile(PsiFile file) {
      final VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile == null) {
        return;
      }
      myGlobalContext.incrementJobDoneAmount(ANNOTATOR, ProjectUtil.calcRelativeToProjectPath(virtualFile, file.getProject()));

      final Project project = file.getProject();
      Document document = PsiDocumentManager.getInstance(project).getDocument(file);
      if (document == null) return;
      GeneralHighlightingPass pass =
        new GeneralHighlightingPass(project, file, document, 0, file.getTextLength(), true) {
          @NotNull
          @Override
          protected HighlightVisitor[] createHighlightVisitors() {
            return new HighlightVisitor[]{new DefaultHighlightVisitor(project, highlightErrorElements, runAnnotators)};
          }

          @Override
          protected HighlightInfoHolder createInfoHolder(final PsiFile file) {
            final HighlightInfoFilter[] filters = ApplicationManager.getApplication().getExtensions(HighlightInfoFilter.EXTENSION_POINT_NAME);
            return new HighlightInfoHolder(file, getColorsScheme(), filters){
                @Override
                public boolean add(@Nullable HighlightInfo info) {
                  if (info == null) return true;
                  if (info.severity == HighlightInfoType.INJECTED_FRAGMENT_SEVERITY) return true;
                  if (info.severity == HighlightSeverity.INFORMATION) return true;
                  ProblemHighlightType problemHighlightType = HighlightInfo.convertType(info.type);
                  GlobalInspectionUtil.createProblem(
                    file,
                    info.description,
                    problemHighlightType,
                    new TextRange(info.startOffset, info.endOffset),
                    myManager,
                    myProblemDescriptionsProcessor,
                    myGlobalContext
                  );
                  return true;
                }
            };
          }
        };
      DaemonProgressIndicator progress = new DaemonProgressIndicator();
      progress.start();
      pass.collectInformation(progress);
    }
  }
}
