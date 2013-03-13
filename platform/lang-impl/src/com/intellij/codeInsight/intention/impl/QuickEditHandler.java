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
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.editorActions.CopyPastePreProcessor;
import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.ReadonlyFragmentModificationHandler;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.EditorFactoryAdapter;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.impl.EditorWithProviderComposite;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringHash;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.impl.source.tree.injected.Place;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.DocumentUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.hash.LinkedHashMap;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
* @author Gregory Shrago
*/
public class QuickEditHandler extends DocumentAdapter implements Disposable {
  private final Project myProject;
  private final QuickEditAction myAction;

  private final Editor myEditor;
  private final Document myOrigDocument;

  private final Document myNewDocument;
  private final PsiFile myNewFile;
  private final LightVirtualFile myNewVirtualFile;

  private final long myOrigCreationStamp;
  private EditorWindow mySplittedWindow;
  private boolean myCommittingToOriginal;

  @Nullable
  private final PsiFile myInjectedFile;
  private final List<Trinity<RangeMarker, RangeMarker, SmartPsiElementPointer>> myMarkers =
    new LinkedList<Trinity<RangeMarker, RangeMarker, SmartPsiElementPointer>>();

  @Nullable
  private final RangeMarker myAltFullRange;
  private static final Key<String> REPLACEMENT_KEY = Key.create("REPLACEMENT_KEY");

  QuickEditHandler(Project project, PsiFile injectedFile, final PsiFile origFile, Editor editor, QuickEditAction action) {
    myProject = project;
    myEditor = editor;
    myAction = action;
    myOrigDocument = editor.getDocument();
    final Place shreds = InjectedLanguageUtil.getShreds(injectedFile);
    final FileType fileType = injectedFile.getFileType();
    final Language language = injectedFile.getLanguage();

    final PsiFileFactory factory = PsiFileFactory.getInstance(project);
    final String text = InjectedLanguageManager.getInstance(project).getUnescapedText(injectedFile);
    final String newFileName =
      StringUtil.notNullize(language.getDisplayName(), "Injected") + " Fragment " + "(" +
      origFile.getName() + ":" + shreds.get(0).getHost().getTextRange().getStartOffset() + ")" + "." + fileType.getDefaultExtension();
    myNewFile = factory.createFileFromText(newFileName, language, text, true, true);
    myNewVirtualFile = (LightVirtualFile)myNewFile.getVirtualFile();
    assert myNewVirtualFile != null;
    // suppress possible errors as in injected mode
    myNewFile.putUserData(InjectedLanguageUtil.FRANKENSTEIN_INJECTION,
                          injectedFile.getUserData(InjectedLanguageUtil.FRANKENSTEIN_INJECTION));
    final SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(project);
    myNewFile.putUserData(FileContextUtil.INJECTED_IN_ELEMENT, smartPointerManager.createSmartPsiElementPointer(origFile));
    myNewDocument = PsiDocumentManager.getInstance(project).getDocument(myNewFile);
    assert myNewDocument != null;
    EditorActionManager.getInstance().setReadonlyFragmentModificationHandler(myNewDocument, new MyQuietHandler());
    myOrigCreationStamp = myOrigDocument.getModificationStamp(); // store creation stamp for UNDO tracking
    myOrigDocument.addDocumentListener(this, this);
    myNewDocument.addDocumentListener(this, this);
    EditorFactory editorFactory = ObjectUtils.assertNotNull(EditorFactory.getInstance());
    // not FileEditorManager listener because of RegExp checker and alike
    editorFactory.addEditorFactoryListener(new EditorFactoryAdapter() {

      int myEditorCount;

      @Override
      public void editorCreated(@NotNull EditorFactoryEvent event) {
        if (event.getEditor().getDocument() != myNewDocument) return;
        myEditorCount ++;
        final EditorActionHandler editorEscape = EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_ESCAPE);
        new AnAction() {
          @Override
          public void update(AnActionEvent e) {
            Editor editor = PlatformDataKeys.EDITOR.getData(e.getDataContext());
            e.getPresentation().setEnabled(
              editor != null && LookupManager.getActiveLookup(editor) == null &&
              TemplateManager.getInstance(myProject).getActiveTemplate(editor) == null &&
              (editorEscape == null || !editorEscape.isEnabled(editor, e.getDataContext())));
          }

          @Override
          public void actionPerformed(AnActionEvent e) {
            closeEditor();
          }
        }.registerCustomShortcutSet(CommonShortcuts.ESCAPE, event.getEditor().getContentComponent());
      }

      @Override
      public void editorReleased(@NotNull EditorFactoryEvent event) {
        if (event.getEditor().getDocument() != myNewDocument) return;
        if (-- myEditorCount > 0) return;
        Disposer.dispose(QuickEditHandler.this);
      }
    }, this);

    if ("JAVA".equals(shreds.get(0).getHost().getLanguage().getID())) {
      myAltFullRange = myOrigDocument.createRangeMarker(
        shreds.get(0).getHostRangeMarker().getStartOffset(),
        shreds.get(shreds.size() - 1).getHostRangeMarker().getEndOffset());
      myAltFullRange.setGreedyToLeft(true);
      myAltFullRange.setGreedyToRight(true);

      initGuardedBlocks(shreds);
      myInjectedFile = null;
    }
    else {
      initMarkers(shreds);
      myAltFullRange = null;
      myInjectedFile = injectedFile;
    }
  }

  public boolean isValid() {
    return myNewVirtualFile.isValid() && (myAltFullRange == null && myInjectedFile.isValid() || myAltFullRange.isValid());
  }

  public void navigate(int injectedOffset) {
    if (myAction.isShowInBalloon()) {
      Ref<Balloon> ref = Ref.create(null);
      final JComponent component = myAction.createBalloonComponent(myNewFile, ref);
      if (component != null) {
        final Balloon balloon = JBPopupFactory.getInstance().createBalloonBuilder(component)
          .setShadow(true)
          .setAnimationCycle(0)
          .setHideOnClickOutside(true)
          .setHideOnKeyOutside(true)
          .setHideOnAction(false)
          .setFillColor(UIUtil.getControlColor())
          .createBalloon();
        ref.set(balloon);
        Disposer.register(myNewFile.getProject(), balloon);
        final Balloon.Position position = QuickEditAction.getBalloonPosition(myEditor);
        RelativePoint point = JBPopupFactory.getInstance().guessBestPopupLocation(myEditor);
        if (position == Balloon.Position.above) {
          final Point p = point.getPoint();
          point = new RelativePoint(point.getComponent(), new Point(p.x, p.y - myEditor.getLineHeight()));
        }
        balloon.show(point, position);
      }
    }
    else {
      final FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(myProject);
      final FileEditor[] editors = fileEditorManager.getEditors(myNewVirtualFile);
      if (editors.length == 0) {
        final EditorWindow curWindow = fileEditorManager.getCurrentWindow();
        mySplittedWindow = curWindow.split(SwingConstants.HORIZONTAL, false, myNewVirtualFile, true);
      }
      Editor editor = fileEditorManager.openTextEditor(new OpenFileDescriptor(myProject, myNewVirtualFile, injectedOffset), true);
      // fold missing values
      if (editor != null) {
        editor.putUserData(QuickEditAction.QUICK_EDIT_HANDLER, this);
        final FoldingModel foldingModel = editor.getFoldingModel();
        foldingModel.runBatchFoldingOperation(new Runnable() {
          @Override
          public void run() {
            for (RangeMarker o : ContainerUtil.reverse(((DocumentEx)myNewDocument).getGuardedBlocks())) {
              String replacement = o.getUserData(REPLACEMENT_KEY);
              FoldRegion region = foldingModel.addFoldRegion(o.getStartOffset(), o.getEndOffset(), replacement);
              region.setExpanded(false);
            }
          }
        });
      }
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
        }
      });

    }
  }

  @Override
  public void documentChanged(DocumentEvent e) {
    UndoManager undoManager = UndoManager.getInstance(myProject);
    boolean undoOrRedo = undoManager.isUndoInProgress() || undoManager.isRedoInProgress();
    if (undoOrRedo) {
      // allow undo/redo up until 'creation stamp' back in time
      // and check it after action is completed
      if (e.getDocument() == myOrigDocument) {
        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            if (myOrigCreationStamp > myOrigDocument.getModificationStamp()) {
              closeEditor();
            }
          }
        });
      }
    }
    else if (e.getDocument() == myNewDocument) {
      commitToOriginal();
    }
    else if (e.getDocument() == myOrigDocument) {
      if (myCommittingToOriginal || myAltFullRange != null && myAltFullRange.isValid()) return;
      closeEditor();
    }
  }

  private void closeEditor() {
    boolean unsplit = false;
    if (mySplittedWindow != null && !mySplittedWindow.isDisposed()) {
      final EditorWithProviderComposite[] editors = mySplittedWindow.getEditors();
      if (editors.length == 1 && Comparing.equal(editors[0].getFile(), myNewVirtualFile)) {
        unsplit = true;
      }
    }
    FileEditorManager.getInstance(myProject).closeFile(myNewVirtualFile);
    if (unsplit) {
      for (EditorWindow editorWindow : mySplittedWindow.findSiblings()) {
        editorWindow.unsplit(true);
      }
    }
  }

  public void initMarkers(Place shreds) {
    SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(myProject);
    for (PsiLanguageInjectionHost.Shred shred : shreds) {
      final RangeMarker rangeMarker = myNewDocument.createRangeMarker(
        shred.getRange().getStartOffset() + shred.getPrefix().length(),
        shred.getRange().getEndOffset() - shred.getSuffix().length());
      final TextRange rangeInsideHost = shred.getRangeInsideHost();
      PsiLanguageInjectionHost host = shred.getHost();
      // todo fixme: char literal update will throw exceptions
      boolean oneCharOnly = host.getText().startsWith("'") && "JAVA".equals(host.getLanguage().getID());
      RangeMarker origMarker = myOrigDocument.createRangeMarker(rangeInsideHost.shiftRight(host.getTextRange().getStartOffset()));
      SmartPsiElementPointer<PsiLanguageInjectionHost> elementPointer = smartPointerManager.createSmartPsiElementPointer(host);
      Trinity<RangeMarker, RangeMarker, SmartPsiElementPointer> markers =
        Trinity.<RangeMarker, RangeMarker, SmartPsiElementPointer>create(origMarker, rangeMarker, elementPointer);
      myMarkers.add(markers);
      markers.first.setGreedyToRight(true);
      markers.second.setGreedyToRight(true);
      markers.first.setGreedyToLeft(true);
      markers.second.setGreedyToLeft(true);
    }
    initGuardedBlocks(shreds);
  }

  private void initGuardedBlocks(Place shreds) {
    int origOffset = -1;
    int curOffset = 0;
    for (PsiLanguageInjectionHost.Shred shred : shreds) {
      Segment hostRangeMarker = shred.getHostRangeMarker();
      int start = shred.getRange().getStartOffset() + shred.getPrefix().length();
      int end = shred.getRange().getEndOffset() - shred.getSuffix().length();
      if (curOffset < start) {
        RangeMarker guard = myNewDocument.createGuardedBlock(curOffset, start);
        if (curOffset == 0 && shred == shreds.get(0)) guard.setGreedyToLeft(true);
        String padding = origOffset < 0 ? "" : myOrigDocument.getText().substring(origOffset, hostRangeMarker.getStartOffset());
        guard.putUserData(REPLACEMENT_KEY, fixQuotes(padding));
      }
      curOffset = end;
      origOffset = hostRangeMarker.getEndOffset();
    }
    if (curOffset < myNewDocument.getTextLength()) {
      RangeMarker guard = myNewDocument.createGuardedBlock(curOffset, myNewDocument.getTextLength());
      guard.setGreedyToRight(true);
      guard.putUserData(REPLACEMENT_KEY, "");
    }
  }


  private void commitToOriginal() {
    if (!isValid()) return;
    final PsiFile origFile = (PsiFile)myNewFile.getUserData(FileContextUtil.INJECTED_IN_ELEMENT).getElement();
    VirtualFile origFileVirtualFile = origFile != null? origFile.getVirtualFile() : null;
    myCommittingToOriginal = true;
    try {
      if (origFileVirtualFile == null || !ReadonlyStatusHandler.getInstance(myProject).ensureFilesWritable(origFileVirtualFile).hasReadonlyFiles()) {
        PostprocessReformattingAspect.getInstance(myProject).disablePostprocessFormattingInside(new Runnable() {
          @Override
          public void run() {
            if (myAltFullRange != null) {
              altCommitToOriginal();
              return;
            }
            commitToOriginalInner();
          }
        });
      }
    }
    finally {
      myCommittingToOriginal = false;
    }
  }

  private void commitToOriginalInner() {
    final String text = myNewDocument.getText();
    final Map<PsiLanguageInjectionHost, Set<Trinity<RangeMarker, RangeMarker, SmartPsiElementPointer>>> map = ContainerUtil
      .classify(myMarkers.iterator(),
                new Convertor<Trinity<RangeMarker, RangeMarker, SmartPsiElementPointer>, PsiLanguageInjectionHost>() {
                  @Override
                  public PsiLanguageInjectionHost convert(final Trinity<RangeMarker, RangeMarker, SmartPsiElementPointer> o) {
                    final PsiElement element = o.third.getElement();
                    return (PsiLanguageInjectionHost)element;
                  }
                });
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    documentManager.commitDocument(myOrigDocument); // commit here and after each manipulator update
    int localInsideFileCursor = 0;
    for (PsiLanguageInjectionHost host : map.keySet()) {
      if (host == null) continue;
      String hostText = host.getText();
      ProperTextRange insideHost = null;
      StringBuilder sb = new StringBuilder();
      for (Trinity<RangeMarker, RangeMarker, SmartPsiElementPointer> entry : map.get(host)) {
        RangeMarker origMarker = entry.first;
        int hostOffset = host.getTextRange().getStartOffset();
        ProperTextRange localInsideHost = new ProperTextRange(origMarker.getStartOffset() - hostOffset, origMarker.getEndOffset() - hostOffset);
        RangeMarker rangeMarker = entry.second;
        ProperTextRange localInsideFile = new ProperTextRange(Math.max(localInsideFileCursor, rangeMarker.getStartOffset()), rangeMarker.getEndOffset());
        if (insideHost != null) {
          //append unchanged inter-markers fragment
          sb.append(hostText.substring(insideHost.getEndOffset(), localInsideHost.getStartOffset()));
        }
        sb.append(localInsideFile.getEndOffset() <= text.length() && !localInsideFile.isEmpty()? localInsideFile.substring(text) : "");
        localInsideFileCursor = localInsideFile.getEndOffset();
        insideHost = insideHost == null ? localInsideHost : insideHost.union(localInsideHost);
      }
      assert insideHost != null;
      ElementManipulators.getManipulator(host).handleContentChange(host, insideHost, sb.toString());
      documentManager.commitDocument(myOrigDocument);
    }
  }

  private void altCommitToOriginal() {
    final PsiFile origPsiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(myOrigDocument);
    String newText = myNewDocument.getText();
    // prepare guarded blocks
    LinkedHashMap<String, String> replacementMap = new LinkedHashMap<String, String>();
    for (RangeMarker o : ContainerUtil.reverse(((DocumentEx)myNewDocument).getGuardedBlocks())) {
      String replacement = o.getUserData(REPLACEMENT_KEY);
      String tempText = "REPLACE"+Long.toHexString(StringHash.calc(replacement));
      newText = newText.substring(0, o.getStartOffset()) + tempText + newText.substring(o.getEndOffset());
      replacementMap.put(tempText, replacement);
    }
    // run preformat processors
    myEditor.getCaretModel().moveToOffset(myAltFullRange.getStartOffset());
    for (CopyPastePreProcessor preProcessor : Extensions.getExtensions(CopyPastePreProcessor.EP_NAME)) {
      newText = preProcessor.preprocessOnPaste(myProject, origPsiFile, myEditor, newText, null);
    }
    myOrigDocument.replaceString(myAltFullRange.getStartOffset(), myAltFullRange.getEndOffset(), newText);
    // replace temp strings for guarded blocks
    for (String tempText : replacementMap.keySet()) {
      int idx = CharArrayUtil.indexOf(myOrigDocument.getCharsSequence(), tempText, myAltFullRange.getStartOffset(), myAltFullRange.getEndOffset());
      myOrigDocument.replaceString(idx, idx + tempText.length(), replacementMap.get(tempText));
    }
    // JAVA: fix occasional char literal concatenation
    fixDocumentQuotes(myOrigDocument, myAltFullRange.getStartOffset() - 1);
    fixDocumentQuotes(myOrigDocument, myAltFullRange.getEndOffset());

    // reformat
    PsiDocumentManager.getInstance(myProject).commitDocument(myOrigDocument);
    Runnable task = new Runnable() {
      @Override
      public void run() {
        try {
          CodeStyleManager.getInstance(myProject).reformatRange(
            origPsiFile, myAltFullRange.getStartOffset(), myAltFullRange.getEndOffset(), true);
        }
        catch (IncorrectOperationException e) {
          //LOG.error(e);
        }
      }
    };
    DocumentUtil.executeInBulk(myOrigDocument, true, task);
    myEditor.getCaretModel().moveToOffset(myAltFullRange.getStartOffset());
    myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
  }

  private static String fixQuotes(String padding) {
    if (padding.isEmpty()) return padding;
    if (padding.startsWith("'")) padding = '\"' + padding.substring(1);
    if (padding.endsWith("'")) padding = padding.substring(0, padding.length() - 1) + "\"";
    return padding;
  }

  private static void fixDocumentQuotes(Document doc, int offset) {
    if (doc.getCharsSequence().charAt(offset) == '\'') {
      doc.replaceString(offset, offset+1, "\"");
    }
  }

  @Override
  public void dispose() {
    // noop
  }

  @TestOnly
  public PsiFile getNewFile() {
    return myNewFile;
  }

  public boolean changesRange(TextRange range) {
    if (myAltFullRange != null) {
       return range.intersects(myAltFullRange.getStartOffset(), myAltFullRange.getEndOffset());
    }
    else if (!myMarkers.isEmpty()) {
      TextRange hostRange = TextRange.create(myMarkers.get(0).first.getStartOffset(),
                                             myMarkers.get(myMarkers.size()-1).first.getEndOffset());
      return range.intersects(hostRange);
    }
    return false;
  }

  private static class MyQuietHandler implements ReadonlyFragmentModificationHandler {
    @Override
    public void handle(final ReadOnlyFragmentModificationException e) {
      //nothing
    }
  }
}
