/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.intellij.psi.impl;

import com.intellij.AppTopics;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.application.ApplicationAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.SettingsSavingComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentBulkUpdateListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerAdapter;
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.util.Processor;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

//todo listen & notifyListeners readonly events?
public class PsiDocumentManagerImpl extends PsiDocumentManagerBase implements SettingsSavingComponent {
  private final DocumentCommitThread myDocumentCommitThread;

  public PsiDocumentManagerImpl(@NotNull final Project project,
                                @NotNull PsiManager psiManager,
                                @NotNull SmartPointerManager smartPointerManager,
                                @NotNull EditorFactory editorFactory,
                                @NotNull MessageBus bus,
                                @NonNls @NotNull final DocumentCommitThread documentCommitThread) {
    super(project, psiManager, smartPointerManager, bus, documentCommitThread);
    myDocumentCommitThread = documentCommitThread;
    editorFactory.getEventMulticaster().addDocumentListener(this, project);
    bus.connect().subscribe(AppTopics.FILE_DOCUMENT_SYNC, new FileDocumentManagerAdapter() {
      @Override
      public void fileContentLoaded(@NotNull final VirtualFile virtualFile, @NotNull Document document) {
        PsiFile psiFile = ApplicationManager.getApplication().runReadAction(new Computable<PsiFile>() {
          @Override
          public PsiFile compute() {
            return getCachedPsiFile(virtualFile);
          }
        });
        fireDocumentCreated(document, psiFile);
      }
    });
    bus.connect().subscribe(DocumentBulkUpdateListener.TOPIC, new DocumentBulkUpdateListener.Adapter() {
      @Override
      public void updateFinished(@NotNull Document doc) {
        documentCommitThread.queueCommit(project, doc, "Bulk update finished");
      }
    });
    ApplicationManager.getApplication().addApplicationListener(new ApplicationAdapter() {
      @Override
      public void beforeWriteActionStart(Object action) {
        documentCommitThread.disable("Write action started: " + action);
      }

      @Override
      public void writeActionFinished(Object action) {
        documentCommitThread.enable("Write action finished: " + action);
      }
    }, project);
    documentCommitThread.enable("project open");
  }

  @Nullable
  @Override
  public PsiFile getPsiFile(@NotNull Document document) {
    final PsiFile psiFile = super.getPsiFile(document);
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      final VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);
      if (virtualFile != null && virtualFile.isValid()) {
        Collection<Project> projects = ProjectLocator.getInstance().getProjectsForFile(virtualFile);
        LOG.assertTrue(projects.isEmpty() || projects.contains(myProject), "Trying to get PSI for an alien project. VirtualFile=" +
                                                                           virtualFile +
                                                                           ";\n myProject=" +
                                                                           myProject +
                                                                           ";\n projects returned: " +
                                                                           projects);
      }
    }
    return psiFile;
  }

  @Override
  public void documentChanged(DocumentEvent event) {
    super.documentChanged(event);
    // avoid documents piling up during batch processing
    if (FileDocumentManagerImpl.areTooManyDocumentsInTheQueue(myUncommittedDocuments)) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        try {
          LOG.error("Too many uncommitted documents for " + myProject + ":\n" + myUncommittedDocuments);
        }
        finally {
          clearUncommittedDocuments();
        }
      }
      commitAllDocuments();
    }
  }

  @Override
  protected void beforeDocumentChangeOnUnlockedDocument(@NotNull final FileViewProvider viewProvider) {
    PostprocessReformattingAspect.getInstance(myProject).beforeDocumentChanged(viewProvider);
    super.beforeDocumentChangeOnUnlockedDocument(viewProvider);
  }

  @Override
  protected boolean finishCommitInWriteAction(@NotNull Document document,
                                              @NotNull List<Processor<Document>> finishProcessors,
                                              boolean synchronously) {
    EditorWindow.disposeInvalidEditors();  // in write action
    return super.finishCommitInWriteAction(document, finishProcessors, synchronously);
  }

  @Override
  public void commitOtherFilesAssociatedWithDocument(final Document document, final PsiFile psiFile) {
    super.commitOtherFilesAssociatedWithDocument(document, psiFile);
    final FileViewProvider viewProvider = getCachedViewProvider(document);
    if (viewProvider != null && viewProvider.getAllFiles().size() > 1) {
      PostprocessReformattingAspect.getInstance(myProject).disablePostprocessFormattingInside(new Runnable() {
        @Override
        public void run() {
          doCommit(document, psiFile);
        }
      });
    }
  }

  @Override
  public boolean isDocumentBlockedByPsi(@NotNull Document doc) {
    final FileViewProvider viewProvider = getCachedViewProvider(doc);
    return viewProvider != null && PostprocessReformattingAspect.getInstance(myProject).isViewProviderLocked(viewProvider);
  }

  @Override
  public void doPostponedOperationsAndUnblockDocument(@NotNull Document doc) {
    if (doc instanceof DocumentWindow) doc = ((DocumentWindow)doc).getDelegate();
    final PostprocessReformattingAspect component = myProject.getComponent(PostprocessReformattingAspect.class);
    final FileViewProvider viewProvider = getCachedViewProvider(doc);
    if (viewProvider != null) component.doPostponedFormatting(viewProvider);
  }

  @Override
  public void save() {
    // Ensure all documents are committed on save so file content dependent indices, that use PSI to build have consistent content.
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        try {
          commitAllDocuments();
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    });
  }

  @Override
  @TestOnly
  public void clearUncommittedDocuments() {
    super.clearUncommittedDocuments();
    myDocumentCommitThread.clearQueue();
  }

  @NonNls
  @Override
  public String toString() {
    return super.toString() + " for the project " + myProject + ".";
  }
}
