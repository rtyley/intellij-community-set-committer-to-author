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
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.localVcs.UpToDateLineNumberProvider;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.annotate.*;
import com.intellij.openapi.vcs.changes.BackgroundFromStartOption;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.impl.BackgroundableActionEnabledHandler;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vcs.impl.UpToDateLineNumberProviderImpl;
import com.intellij.openapi.vcs.impl.VcsBackgroundableActions;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.SortedList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author: lesya
 * @author Konstantin Bulenkov
 */
public class AnnotateToggleAction extends ToggleAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.actions.AnnotateToggleAction");
  protected static final Key<Collection<ActiveAnnotationGutter>> KEY_IN_EDITOR = Key.create("Annotations");
  private final static Color[] BG_COLORS = {
    new Color(222, 241, 229),
    new Color(234, 255, 226),
    new Color(208, 229, 229),
    new Color(255, 226, 199),
    new Color(227, 226, 223),
    new Color(255, 213, 203),
    new Color(220, 204, 236),
    new Color(255, 191, 195),
    new Color(243, 223, 243),
    new Color(217, 228, 249),
    new Color(255, 251, 207),
    new Color(217, 222, 229),
    new Color(255, 204, 238),
    new Color(236, 236, 236)};

  public void update(AnActionEvent e) {
    final boolean enabled = isEnabled(VcsContextFactory.SERVICE.getInstance().createContextOn(e));
    e.getPresentation().setEnabled(enabled);
  }

  private static boolean isEnabled(final VcsContext context) {
    VirtualFile[] selectedFiles = context.getSelectedFiles();
    if (selectedFiles == null) return false;
    if (selectedFiles.length != 1) return false;
    VirtualFile file = selectedFiles[0];
    if (file.isDirectory()) return false;
    Project project = context.getProject();
    if (project == null || project.isDisposed()) return false;

    final ProjectLevelVcsManager plVcsManager = ProjectLevelVcsManager.getInstance(project);
    final BackgroundableActionEnabledHandler handler = ((ProjectLevelVcsManagerImpl)plVcsManager)
      .getBackgroundableActionHandler(VcsBackgroundableActions.ANNOTATE);
    if (handler.isInProgress(file.getPath())) return false;

    final AbstractVcs vcs = plVcsManager.getVcsFor(file);
    if (vcs == null) return false;
    final AnnotationProvider annotationProvider = vcs.getAnnotationProvider();
    if (annotationProvider == null) return false;
    final FileStatus fileStatus = FileStatusManager.getInstance(project).getStatus(file);
    if (fileStatus == FileStatus.UNKNOWN || fileStatus == FileStatus.ADDED || fileStatus == FileStatus.IGNORED) {
      return false;
    }
    return hasTextEditor(file);
  }

  private static boolean hasTextEditor(VirtualFile selectedFile) {
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    FileType fileType = fileTypeManager.getFileTypeByFile(selectedFile);
    return !fileType.isBinary() && fileType != StdFileTypes.GUI_DESIGNER_FORM;
  }

  public boolean isSelected(AnActionEvent e) {
    VcsContext context = VcsContextFactory.SERVICE.getInstance().createContextOn(e);
    Editor editor = context.getEditor();
    if (editor == null) return false;
    Collection annotations = editor.getUserData(KEY_IN_EDITOR);
    return annotations != null && !annotations.isEmpty();
  }

  public void setSelected(AnActionEvent e, boolean selected) {
    final VcsContext context = VcsContextFactory.SERVICE.getInstance().createContextOn(e);
    Editor editor = context.getEditor();
    if (!selected) {
      if (editor != null) {
        editor.getGutter().closeAllAnnotations();
      }
    }
    else {
      if (editor == null) {
        VirtualFile selectedFile = context.getSelectedFile();
        FileEditor[] fileEditors = FileEditorManager.getInstance(context.getProject()).openFile(selectedFile, false);
        for (FileEditor fileEditor : fileEditors) {
          if (fileEditor instanceof TextEditor) {
            editor = ((TextEditor)fileEditor).getEditor();
          }
        }
      }
      LOG.assertTrue(editor != null);
      doAnnotate(editor, context.getProject());
    }
  }

  private static void doAnnotate(final Editor editor, final Project project) {
    final VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
    if (project == null) return;
    final ProjectLevelVcsManager plVcsManager = ProjectLevelVcsManager.getInstance(project);
    final AbstractVcs vcs = plVcsManager.getVcsFor(file);

    if (vcs == null) return;

    final AnnotationProvider annotationProvider = vcs.getAnnotationProvider();

    final Ref<FileAnnotation> fileAnnotationRef = new Ref<FileAnnotation>();
    final Ref<VcsException> exceptionRef = new Ref<VcsException>();

    final BackgroundableActionEnabledHandler handler = ((ProjectLevelVcsManagerImpl)plVcsManager).getBackgroundableActionHandler(
      VcsBackgroundableActions.ANNOTATE);
    handler.register(file.getPath());

    final Task.Backgroundable annotateTask = new Task.Backgroundable(project,
                                                                     VcsBundle.message("retrieving.annotations"),
                                                                     true,
                                                                     BackgroundFromStartOption.getInstance()) {
      public void run(final @NotNull ProgressIndicator indicator) {
        try {
          fileAnnotationRef.set(annotationProvider.annotate(file));
        }
        catch (VcsException e) {
          exceptionRef.set(e);
        }
      }

      @Override
      public void onCancel() {
        onSuccess();
      }

      @Override
      public void onSuccess() {
        handler.completed(file.getPath());

        if (!exceptionRef.isNull()) {
          AbstractVcsHelper.getInstance(project).showErrors(Arrays.asList(exceptionRef.get()), VcsBundle.message("message.title.annotate"));
        }

        if (!fileAnnotationRef.isNull()) {
          doAnnotate(editor, project, file, fileAnnotationRef.get(), vcs);
        }
      }
    };
    ProgressManager.getInstance().run(annotateTask);
  }

  public static void doAnnotate(final Editor editor,
                                final Project project,
                                final VirtualFile file,
                                final FileAnnotation fileAnnotation,
                                final AbstractVcs vcs) {
    final String upToDateContent = fileAnnotation.getAnnotatedContent();

    final UpToDateLineNumberProvider getUpToDateLineNumber = new UpToDateLineNumberProviderImpl(editor.getDocument(), project, upToDateContent);
    editor.getGutter().closeAllAnnotations();

    // be careful, not proxies but original items are put there (since only their presence not behaviour is important)
    Collection<ActiveAnnotationGutter> annotations = editor.getUserData(KEY_IN_EDITOR);
    if (annotations == null) {
      annotations = new HashSet<ActiveAnnotationGutter>();
      editor.putUserData(KEY_IN_EDITOR, annotations);
    }

    final EditorGutterComponentEx editorGutter = (EditorGutterComponentEx)editor.getGutter();
    final HighlightAnnotationsActions highlighting = new HighlightAnnotationsActions(project, file, fileAnnotation, editorGutter);
    final List<AnnotationFieldGutter> gutters = new ArrayList<AnnotationFieldGutter>();
    final AnnotationSourceSwitcher switcher = fileAnnotation.getAnnotationSourceSwitcher();
    final AnnotationPresentation presentation;
    final List<AnAction> additionalActions = new ArrayList<AnAction>();
    if (vcs.getCommittedChangesProvider() != null) {
      additionalActions.add(new ShowDiffFromAnnotation(getUpToDateLineNumber, fileAnnotation, vcs, file));
    }
    additionalActions.add(new CopyRevisionNumberAction(fileAnnotation));
    presentation = new AnnotationPresentation(highlighting, switcher, editorGutter, gutters, additionalActions.toArray(new AnAction[additionalActions.size()]));

    for (AnAction action : additionalActions) {
      if (action instanceof LineNumberListener) {
         presentation.addLineNumberListener((LineNumberListener)action);
      }
    }

    final Map<String, Color> bgColorMap = Registry.is("vcs.show.colored.annotations") ? computeBgColors(fileAnnotation) : null;
    final Map<String, Integer> historyIds = Registry.is("vcs.show.history.numbers") ? computeLineNumbers(fileAnnotation) : null;

    if (switcher != null) {
      switcher.switchTo(switcher.getDefaultSource());
      final LineAnnotationAspect revisionAspect = switcher.getRevisionAspect();
      final CurrentRevisionAnnotationFieldGutter currentRevisionGutter =
        new CurrentRevisionAnnotationFieldGutter(fileAnnotation, editor, revisionAspect, presentation, bgColorMap);
      final MergeSourceAvailableMarkerGutter mergeSourceGutter =
        new MergeSourceAvailableMarkerGutter(fileAnnotation, editor, null, presentation, bgColorMap);

      presentation.addSourceSwitchListener(currentRevisionGutter);
      presentation.addSourceSwitchListener(mergeSourceGutter);

      currentRevisionGutter.consume(switcher.getDefaultSource());
      mergeSourceGutter.consume(switcher.getDefaultSource());

      gutters.add(currentRevisionGutter);
      gutters.add(mergeSourceGutter);
    }
    
    final LineAnnotationAspect[] aspects = fileAnnotation.getAspects();
    for (LineAnnotationAspect aspect : aspects) {
      final AnnotationFieldGutter gutter = new AnnotationFieldGutter(fileAnnotation, editor, aspect, presentation, bgColorMap);
      gutter.setAspectValueToBgColorMap(bgColorMap);
      gutters.add(gutter);
    }


    if (historyIds != null) {
      gutters.add(new HistoryIdColumn(fileAnnotation, editor, presentation, bgColorMap, historyIds));
    }
    gutters.add(new HighlightedAdditionalColumn(fileAnnotation, editor, null, presentation, highlighting, bgColorMap));
    presentation.addAction(new AnnotateActionGroup(gutters, editorGutter), 1);


    for (AnnotationFieldGutter gutter : gutters) {
      final AnnotationGutterLineConvertorProxy proxy = new AnnotationGutterLineConvertorProxy(getUpToDateLineNumber, gutter);
      if (gutter.isGutterAction()) {
        editor.getGutter().registerTextAnnotation(proxy, proxy);
      }
      else {
        editor.getGutter().registerTextAnnotation(proxy);
      }
      annotations.add(gutter);
    }
  }

  @Nullable
  private static Map<String, Integer> computeLineNumbers(FileAnnotation fileAnnotation) {
    final SortedList<VcsFileRevision> revisions = new SortedList<VcsFileRevision>(new Comparator<VcsFileRevision>() {
      @Override
      public int compare(VcsFileRevision o1, VcsFileRevision o2) {
        try {
          return o1.getRevisionDate().compareTo(o2.getRevisionDate());
        }
        catch (Exception e) {
          return 0;
        }
      }
    });
    final Map<String, Integer> numbers = new HashMap<String, Integer>();
    final List<VcsFileRevision> fileRevisionList = fileAnnotation.getRevisions();
    if (fileRevisionList != null) {
      revisions.addAll(fileRevisionList);
      for (VcsFileRevision revision : fileRevisionList) {
        final String revNumber = revision.getRevisionNumber().asString();

        if (!numbers.containsKey(revNumber)) {
          final int num = revisions.indexOf(revision);
          if (num != -1) {
            numbers.put(revNumber, num + 1);
          }
        }
      }
    }
    return numbers.size() < 2 ? null : numbers;
  }

  @Nullable
  private static Map<String, Color> computeBgColors(FileAnnotation fileAnnotation) {
    final Map<String, Color> bgColors = new HashMap<String, Color>();
    final Map<String, Color> revNumbers = new HashMap<String, Color>();
    final int length = BG_COLORS.length;
    final List<VcsFileRevision> fileRevisionList = fileAnnotation.getRevisions();
    if (fileRevisionList != null) {
      for (VcsFileRevision revision : fileRevisionList) {
        final String author = revision.getAuthor();
        final String revNumber = revision.getRevisionNumber().asString();
        if (author != null && !bgColors.containsKey(author)) {
          final int size = bgColors.size();
          bgColors.put(author, BG_COLORS[size < length ? size : size % length]);
        }
        if (!revNumbers.containsKey(revNumber)) {
          revNumbers.put(revNumber, bgColors.get(author));          
        }
    }
    }
    return bgColors.size() < 2 ? null : revNumbers;
  }
}
