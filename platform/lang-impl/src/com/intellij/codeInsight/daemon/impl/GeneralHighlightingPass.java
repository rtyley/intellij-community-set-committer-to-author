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

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.daemon.DaemonBundle;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.analysis.DefaultHighlightVisitor;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightLevelUtil;
import com.intellij.codeInsight.highlighting.HighlightErrorFilter;
import com.intellij.codeInsight.problems.ProblemImpl;
import com.intellij.concurrency.JobUtil;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.injected.editor.DocumentWindowImpl;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageAnnotators;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.Problem;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.TodoItem;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class GeneralHighlightingPass extends ProgressableTextEditorHighlightingPass implements DumbAware {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.GeneralHighlightingPass");
  static final Icon IN_PROGRESS_ICON = IconLoader.getIcon("/general/errorsInProgress.png");
  static final String PRESENTABLE_NAME = DaemonBundle.message("pass.syntax");
  private static final Key<Boolean> HAS_ERROR_ELEMENT = Key.create("HAS_ERROR_ELEMENT");

  private final int myStartOffset;
  private final int myEndOffset;
  private final boolean myUpdateAll;

  private volatile Collection<HighlightInfo> myHighlights = Collections.emptyList();
  private final Map<TextRange,Collection<HighlightInfo>> myInjectedPsiHighlights = new HashMap<TextRange, Collection<HighlightInfo>>();

  protected volatile boolean myHasErrorElement;
  private volatile boolean myErrorFound;
  private static final Comparator<HighlightVisitor> VISITOR_ORDER_COMPARATOR = new Comparator<HighlightVisitor>() {
    public int compare(final HighlightVisitor o1, final HighlightVisitor o2) {
      return o1.order() - o2.order();
    }
  };
  private volatile UserDataHolder myProgress;

  public GeneralHighlightingPass(@NotNull Project project,
                                 @NotNull PsiFile file,
                                 @NotNull Document document,
                                 int startOffset,
                                 int endOffset,
                                 boolean updateAll) {
    super(project, document, IN_PROGRESS_ICON, PRESENTABLE_NAME, file, true);
    myStartOffset = startOffset;
    myEndOffset = endOffset;
    myUpdateAll = updateAll;

    LOG.assertTrue(file.isValid());
    setId(Pass.UPDATE_ALL);
    myHasErrorElement = !isWholeFileHighlighting() && Boolean.TRUE.equals(myFile.getUserData(HAS_ERROR_ELEMENT));
    FileStatusMap fileStatusMap = ((DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(myProject)).getFileStatusMap();
    myErrorFound = !isWholeFileHighlighting() && fileStatusMap.wasErrorFound(myDocument);
  }

  private static final Key<AtomicInteger> HIGHLIGHT_VISITOR_INSTANCE_COUNT = new Key<AtomicInteger>("HIGHLIGHT_VISITOR_INSTANCE_COUNT");
  @NotNull
  private HighlightVisitor[] createHighlightVisitors() {
    int oldCount = incVisitorUsageCount(1);
    HighlightVisitor[] highlightVisitors = Extensions.getExtensions(HighlightVisitor.EP_HIGHLIGHT_VISITOR, myProject);
    if (oldCount != 0) {
      HighlightVisitor[] clones = new HighlightVisitor[highlightVisitors.length];
      for (int i = 0; i < highlightVisitors.length; i++) {
        HighlightVisitor highlightVisitor = highlightVisitors[i];
        clones[i] = highlightVisitor.clone();
      }
      highlightVisitors = clones;
    }
    return highlightVisitors;
  }

  // returns old value
  private int incVisitorUsageCount(int delta) {
    AtomicInteger count = myProject.getUserData(HIGHLIGHT_VISITOR_INSTANCE_COUNT);
    if (count == null) {
      count = ((UserDataHolderEx)myProject).putUserDataIfAbsent(HIGHLIGHT_VISITOR_INSTANCE_COUNT, new AtomicInteger(0));
    }
    int old = count.getAndAdd(delta);
    assert old + delta >= 0 : old +";" + delta;
    return old;
  }

  protected void collectInformationWithProgress(final ProgressIndicator progress) {
    myProgress = (UserDataHolder)progress;
    final Collection<HighlightInfo> result = new THashSet<HighlightInfo>(100);
    DaemonCodeAnalyzer daemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(myProject);
    FileStatusMap fileStatusMap = ((DaemonCodeAnalyzerImpl)daemonCodeAnalyzer).getFileStatusMap();
    HighlightVisitor[] highlightVisitors = createHighlightVisitors();
    try {
      final FileViewProvider viewProvider = myFile.getViewProvider();
      final Set<Language> relevantLanguages = viewProvider.getLanguages();
      List<PsiElement> elements = null;
      for (Language language : relevantLanguages) {
        PsiElement psiRoot = viewProvider.getPsi(language);
        if (!HighlightLevelUtil.shouldHighlight(psiRoot)) continue;
        List<PsiElement> underRoot = CollectHighlightsUtil.getElementsInRange(psiRoot, myStartOffset, myEndOffset);
        if (elements == null) {
          elements = underRoot;
        }
        else {
          elements.addAll(underRoot);
        }
        if (underRoot.isEmpty()) {
          elements.add(psiRoot);
        }
      }
      if (elements != null) {
        result.addAll(collectHighlights(elements, highlightVisitors, progress));
        addInjectedPsiHighlights(elements, Extensions.getExtensions(DefaultHighlightVisitor.FILTER_EP_NAME, myProject));
      }

      if (!isDumbMode()) {
        result.addAll(highlightTodos(myFile, myDocument.getCharsSequence(), myStartOffset, myEndOffset));
      }

      if (myUpdateAll) {
        fileStatusMap.setErrorFoundFlag(myDocument, myErrorFound);
      }
    }
    finally {
      incVisitorUsageCount(-1);
    }
    myHighlights = result;
  }

  private void addInjectedPsiHighlights(final List<PsiElement> elements, final HighlightErrorFilter[] errorFilters) {
    List<DocumentWindow> injected = InjectedLanguageUtil.getCachedInjectedDocuments(myFile);
    Collection<PsiElement> hosts = new THashSet<PsiElement>(elements.size() + injected.size());

    // rehighlight all injected PSI regardless the range,
    // since change in one place can lead to invalidation of injected PSI in (completely) other place.
    for (DocumentWindow documentRange : injected) {
      if (!documentRange.isValid()) continue;
      PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(documentRange);
      if (file == null) continue;
      PsiElement context = file.getContext();
      if (context != null
          && context.isValid()
          && !file.getProject().isDisposed()
          && (myUpdateAll || new ProperTextRange(myStartOffset, myEndOffset).intersects(context.getTextRange()))) {
        hosts.add(context);
      }
    }
    hosts.addAll(elements);

    final Collection<PsiFile> injectedFiles = new THashSet<PsiFile>();
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    final TextAttributes injectedAttributes = scheme.getAttributes(EditorColors.INJECTED_LANGUAGE_FRAGMENT);

    for (PsiElement element : hosts) {
      InjectedLanguageUtil.enumerate(element, myFile, new PsiLanguageInjectionHost.InjectedPsiVisitor() {
        public void visit(@NotNull PsiFile injectedPsi, @NotNull List<PsiLanguageInjectionHost.Shred> places) {
          if (injectedFiles.add(injectedPsi)) { // for concatenations there can be many injection hosts with only one injected PSI
            for (PsiLanguageInjectionHost.Shred place : places) {
              TextRange textRange = place.getRangeInsideHost().shiftRight(place.host.getTextRange().getStartOffset());
              if (textRange.isEmpty()) continue;
              String desc = injectedPsi.getText();
              HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.INJECTED_LANGUAGE_FRAGMENT, textRange, null, desc, injectedAttributes);
              addHighlightInfo(textRange, info);
            }
          }
        }
      }, false);
    }
    if (injectedFiles.isEmpty()) return;
    final InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(myProject);

    JobUtil.invokeConcurrentlyUnderMyProgress(injectedFiles, new Processor<PsiFile>() {
      public boolean process(final PsiFile injectedPsi) {
        AnnotationHolderImpl annotationHolder = createAnnotationHolder();
        highlightInjectedIn(injectedPsi, annotationHolder, errorFilters, injectedLanguageManager);
        DocumentWindow documentWindow = (DocumentWindow)PsiDocumentManager.getInstance(myProject).getCachedDocument(injectedPsi);
        for (Annotation annotation : annotationHolder) {
          final TextRange fixedTextRange;
          final int startOffset = annotation.getStartOffset();
          TextRange textRange = documentWindow.getHostRange(startOffset);
          if (textRange == null) {
            // todo[cdr] check this fix. prefix/suffix code annotation case
            textRange = findNearestTextRange(documentWindow, startOffset);
            final boolean isBefore = startOffset < textRange.getStartOffset();
            fixedTextRange = new ProperTextRange(isBefore ? textRange.getStartOffset() - 1 : textRange.getEndOffset(),
                                           isBefore ? textRange.getStartOffset() : textRange.getEndOffset() + 1);
          }
          else {
            fixedTextRange = null;
          }
          if (((DocumentWindowImpl)documentWindow).isOneLine()) {
            annotation.setAfterEndOfLine(false);
          }
          final HighlightInfo highlightInfo = HighlightInfo.fromAnnotation(annotation, fixedTextRange);
          addHighlightInfo(textRange, highlightInfo);
        }

        if (!isDumbMode()) {
          for (HighlightInfo info : highlightTodos(injectedPsi, injectedPsi.getText(), 0, injectedPsi.getTextLength())) {
            List<TextRange> editables = injectedLanguageManager.intersectWithAllEditableFragments(injectedPsi, new ProperTextRange(info.startOffset, info.endOffset));
            for (TextRange editable : editables) {
              TextRange hostRange = documentWindow.injectedToHost(editable);

              HighlightInfo patched =
              new HighlightInfo(info.forcedTextAttributes, info.type, hostRange.getStartOffset(), hostRange.getEndOffset(), info.description, info.toolTip,info.type.getSeverity(null), false, null,
                                false);
              addHighlightInfo(hostRange, patched);
            }
          }
        }
        return true;
      }
    }, "Highlight injected language fragments");
  }

  private void addHighlightInfo(@NotNull TextRange textRange, @NotNull HighlightInfo highlightInfo) {
    synchronized (myInjectedPsiHighlights) {
      Collection<HighlightInfo> infos = myInjectedPsiHighlights.get(textRange);
      if (infos == null) {
        infos = new SmartList<HighlightInfo>();
        myInjectedPsiHighlights.put(textRange, infos);
      }
      infos.add(highlightInfo);
    }
  }

  // finds the first nearest text range
  private static TextRange findNearestTextRange(final DocumentWindow documentWindow, final int startOffset) {
    TextRange textRange = null;
    for (RangeMarker marker : documentWindow.getHostRanges()) {
      TextRange curRange = InjectedLanguageUtil.toTextRange(marker);
      if (curRange.getStartOffset() > startOffset && textRange != null) break;
      textRange = curRange;
    }
    assert textRange != null;
    return textRange;
  }

  private static void highlightInjectedIn(final PsiFile injectedPsi, final AnnotationHolderImpl annotationHolder, final HighlightErrorFilter[] errorFilters,
                                          final InjectedLanguageManager injectedLanguageManager) {
    final DocumentWindow documentRange = ((VirtualFileWindow)injectedPsi.getViewProvider().getVirtualFile()).getDocumentWindow();
    assert documentRange != null;
    assert documentRange.getText().equals(injectedPsi.getText());
    Language injectedLanguage = injectedPsi.getLanguage();
    final List<Annotator> annotators = LanguageAnnotators.INSTANCE.allForLanguage(injectedLanguage);
    final AnnotationHolderImpl fixingOffsetsHolder = new AnnotationHolderImpl() {
      public boolean add(final Annotation annotation) {
        return true; // we are going to hand off the annotation to the annotationHolder anyway
      }

      protected Annotation createAnnotation(TextRange range, HighlightSeverity severity, String message) {
        List<TextRange> editables = injectedLanguageManager.intersectWithAllEditableFragments(injectedPsi, range);
        Annotation firstAnnotation = null;
        for (TextRange editable : editables) {
          final TextRange patched = documentRange.injectedToHost(editable);
          Annotation annotation = super.createAnnotation(patched, severity, message);
          if (firstAnnotation == null) {
            firstAnnotation = annotation;
          }
          annotationHolder.add(annotation);
        }
        return firstAnnotation != null ? firstAnnotation :
        // fake
        super.createAnnotation(documentRange.injectedToHost(TextRange.from(0, 0)), severity, message);
      }
    };
    PsiElementVisitor visitor = new PsiRecursiveElementWalkingVisitor() {
      @Override public void visitElement(PsiElement element) {
        super.visitElement(element);
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < annotators.size(); i++) {
          Annotator annotator = annotators.get(i);
          annotator.annotate(element, fixingOffsetsHolder);
        }
      }

      @Override public void visitErrorElement(PsiErrorElement element) {
        for (final HighlightErrorFilter errorFilter : errorFilters) {
          if (!errorFilter.shouldHighlightErrorElement(element)) return;
        }

        HighlightInfo info = DefaultHighlightVisitor.createErrorElementInfo(element);
        Annotation error = fixingOffsetsHolder.createErrorAnnotation(new ProperTextRange(info.startOffset, info.endOffset), info.description);
        error.setAfterEndOfLine(info.isAfterEndOfLine);
        error.setTooltip(info.toolTip);
        if (info.quickFixActionRanges != null) {
          for (Pair<HighlightInfo.IntentionActionDescriptor, TextRange> o : info.quickFixActionRanges) {
            List<TextRange> editables = injectedLanguageManager.intersectWithAllEditableFragments(injectedPsi, o.second);
            for (TextRange fixEditable : editables) {
              error.registerFix(o.first.getAction(), documentRange.injectedToHost(fixEditable));
            }
          }
        }
      }
    };

    injectedPsi.accept(visitor);
    highlightInjectedSyntax(injectedLanguage, injectedPsi, annotationHolder);
  }

  private static void highlightInjectedSyntax(final Language injectedLanguage, final PsiFile injectedPsi, final AnnotationHolderImpl annotationHolder) {
    List<Trinity<IElementType, PsiLanguageInjectionHost, TextRange>> tokens = InjectedLanguageUtil.getHighlightTokens(injectedPsi);
    if (tokens == null) return;

    SyntaxHighlighter syntaxHighlighter =
      SyntaxHighlighterFactory.getSyntaxHighlighter(injectedLanguage, injectedPsi.getProject(), injectedPsi.getVirtualFile());
    EditorColorsScheme globalScheme = EditorColorsManager.getInstance().getGlobalScheme();
    final TextAttributes defaultAttrs = globalScheme.getAttributes(HighlighterColors.TEXT);

    for (Trinity<IElementType, PsiLanguageInjectionHost, TextRange> token : tokens) {
      IElementType tokenType = token.getFirst();
      PsiLanguageInjectionHost injectionHost = token.getSecond();
      TextRange textRange = token.getThird();
      TextAttributesKey[] keys = syntaxHighlighter.getTokenHighlights(tokenType);
      if (textRange.getLength() == 0) continue;

      Annotation annotation = annotationHolder.createInfoAnnotation(textRange.shiftRight(injectionHost.getTextRange().getStartOffset()), null);
      if (annotation == null) continue; // maybe out of highlightable range
      // force attribute colors to override host' ones

      TextAttributes attributes = null;
      for(TextAttributesKey key:keys) {
        TextAttributes attrs2 = globalScheme.getAttributes(key);
        if (attrs2 != null) {
          attributes = attributes != null ? TextAttributes.merge(attributes, attrs2):attrs2;
        }
      }
      if (attributes == null || attributes.isEmpty() || attributes.equals(defaultAttrs)) {
        annotation.setEnforcedTextAttributes(TextAttributes.ERASE_MARKER);
      }
      else {
        Color back = attributes.getBackgroundColor() == null ? globalScheme.getDefaultBackground() : attributes.getBackgroundColor();
        Color fore = attributes.getForegroundColor() == null ? globalScheme.getDefaultForeground() : attributes.getForegroundColor();
        TextAttributes forced = new TextAttributes(fore, back, attributes.getEffectColor(), attributes.getEffectType(), attributes.getFontType());
        annotation.setEnforcedTextAttributes(forced);
      }
    }
  }

  private boolean isWholeFileHighlighting() {
    return myUpdateAll && myStartOffset == 0 && myEndOffset == myFile.getTextLength();
  }

  private static final Key<List<GeneralHighlightingPass>> UPDATE_ALL_FINISHED = Key.create("UPDATE_ALL_FINISHED");
  protected void applyInformationWithProgress() {
    myFile.putUserData(HAS_ERROR_ELEMENT, myHasErrorElement);

    // highlights from both passes should be in the same layer 
    TextRange range = new TextRange(myStartOffset, myEndOffset);
    Collection<HighlightInfo> collection = myInjectedPsiHighlights.get(range);
    if (collection == null) {
      collection = new ArrayList<HighlightInfo>(myHighlights.size());
    }
    collection.addAll(myHighlights);
    myInjectedPsiHighlights.put(range, collection);
    myHighlights = Collections.emptyList();

    List<GeneralHighlightingPass> updateAllFinished = myProgress.getUserData(UPDATE_ALL_FINISHED);
    if (updateAllFinished == null || !updateAllFinished.contains(this)) {
      // prevent situation when visible pass finished after updateAll pass and tries to wipe out its results
      UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, myInjectedPsiHighlights, Pass.UPDATE_ALL);

      if (myUpdateAll) {
        if (updateAllFinished == null) {
          updateAllFinished = new ArrayList<GeneralHighlightingPass>();
          myProgress.putUserData(UPDATE_ALL_FINISHED, updateAllFinished);
        }
        updateAllFinished.add(this);
      }
    }

    if (myUpdateAll) {
      reportErrorsToWolf();
    }
  }

  @NotNull
  public Collection<HighlightInfo> getHighlights() {
    ArrayList<HighlightInfo> list = new ArrayList<HighlightInfo>(myHighlights);
    for (Collection<HighlightInfo> infos : myInjectedPsiHighlights.values()) {
      list.addAll(infos);
    }
    return list;
  }

  private Collection<HighlightInfo> collectHighlights(@NotNull final List<PsiElement> elements,
                                                      @NotNull final HighlightVisitor[] highlightVisitors,
                                                      @NotNull final ProgressIndicator progress) {
    final Set<PsiElement> skipParentsSet = new THashSet<PsiElement>();
    final Set<HighlightInfo> gotHighlights = new THashSet<HighlightInfo>();

    final List<HighlightVisitor> visitors = new ArrayList<HighlightVisitor>(highlightVisitors.length);
    List<HighlightVisitor> list = Arrays.asList(highlightVisitors);
    for (HighlightVisitor visitor : DumbService.getInstance(myProject).filterByDumbAwareness(list)) {
      if (visitor.suitableForFile(myFile)) visitors.add(visitor);
    }
    LOG.assertTrue(!visitors.isEmpty(), list);

    HighlightVisitor[] visitorArray = visitors.toArray(new HighlightVisitor[visitors.size()]);
    Arrays.sort(visitorArray, VISITOR_ORDER_COMPARATOR);

    final boolean forceHighlightParents = forceHighlightParents();

    final HighlightInfoHolder holder = createInfoHolder();
    holder.setWritable(true);
    final ProgressManager progressManager = ProgressManager.getInstance();
    setProgressLimit((long)elements.size() * visitorArray.length);

    final int chunkSize = Math.max(1, elements.size() / 100); // one percent precision is enough
    for (final HighlightVisitor visitor : visitorArray) {
      Runnable action = new Runnable() {
        public void run() {
          int nextLimit = chunkSize;
          for (int i = 0; i < elements.size(); i++) {
            PsiElement element = elements.get(i);
            ProgressManager.checkCanceled();

            if (element != myFile && !skipParentsSet.isEmpty() && element.getFirstChild() != null && skipParentsSet.contains(element)) {
              skipParentsSet.add(element.getParent());
              continue;
            }

            if (element instanceof PsiErrorElement) {
              myHasErrorElement = true;
            }
            holder.clear();

            visitor.visit(element, holder);
            if (i == nextLimit) {
              advanceProgress(chunkSize);
              nextLimit = i + chunkSize;
            }

            //noinspection ForLoopReplaceableByForEach
            for (int j = 0; j < holder.size(); j++) {
              HighlightInfo info = holder.get(j);
              assert info != null;
              // have to filter out already obtained highlights
              if (!gotHighlights.add(info)) continue;
              boolean isError = info.getSeverity() == HighlightSeverity.ERROR;
              if (isError) {
                if (!forceHighlightParents) {
                  skipParentsSet.add(element.getParent());
                }
                myErrorFound = true;
              }
            }
          }
          advanceProgress(elements.size() - (nextLimit-chunkSize));
        }
      };
      if (!visitor.analyze(action, myUpdateAll, myFile)) {
        cancelAndRestartDaemonLater(progress, myProject, this);
      }
    }

    return gotHighlights;
  }

  static Void cancelAndRestartDaemonLater(ProgressIndicator progress, final Project project, TextEditorHighlightingPass pass) {
    PassExecutorService.log(progress, pass, "Cancel and restart");
    progress.cancel();
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        try {
          Thread.sleep(new Random().nextInt(100));
        }
        catch (InterruptedException e) {
          LOG.error(e);
        }
        DaemonCodeAnalyzer.getInstance(project).restart();
      }
    }, project.getDisposed());
    throw new ProcessCanceledException();
  }

  private boolean forceHighlightParents() {
    boolean forceHighlightParents = false;
    for(HighlightRangeExtension extension: Extensions.getExtensions(HighlightRangeExtension.EP_NAME)) {
      if (extension.isForceHighlightParents(myFile)) {
        forceHighlightParents = true;
        break;
      }
    }
    return forceHighlightParents;
  }

  protected HighlightInfoHolder createInfoHolder() {
    final HighlightInfoFilter[] filters = ApplicationManager.getApplication().getExtensions(HighlightInfoFilter.EXTENSION_POINT_NAME);
    return new HighlightInfoHolder(myFile, filters);
  }
  protected AnnotationHolderImpl createAnnotationHolder() {
    return new AnnotationHolderImpl();
  }

  private static Collection<HighlightInfo> highlightTodos(PsiFile file, CharSequence text, int startOffset, int endOffset) {
    PsiManager psiManager = file.getManager();
    PsiSearchHelper helper = psiManager.getSearchHelper();
    TodoItem[] todoItems = helper.findTodoItems(file, startOffset, endOffset);
    if (todoItems.length == 0) return Collections.emptyList();

    List<HighlightInfo> list = new ArrayList<HighlightInfo>(todoItems.length);
    for (TodoItem todoItem : todoItems) {
      TextRange range = todoItem.getTextRange();
      String description = text.subSequence(range.getStartOffset(), range.getEndOffset()).toString();
      TextAttributes attributes = todoItem.getPattern().getAttributes().getTextAttributes();
      HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.TODO, range, description, description, attributes);
      assert info != null;
      list.add(info);
    }
    return list;
  }

  private void reportErrorsToWolf() {
    if (!myFile.getViewProvider().isPhysical()) return; // e.g. errors in evaluate expression
    Project project = myFile.getProject();
    if (!PsiManager.getInstance(project).isInProject(myFile)) return; // do not report problems in libraries
    VirtualFile file = myFile.getVirtualFile();
    if (file == null) return;

    List<Problem> problems = convertToProblems(getHighlights(), file, myHasErrorElement);
    WolfTheProblemSolver wolf = WolfTheProblemSolver.getInstance(project);

    List<HighlightInfo> errors = DaemonCodeAnalyzerImpl.getHighlights(getDocument(), HighlightSeverity.ERROR, project);
    if (errors.isEmpty() || isWholeFileHighlighting()) {
      wolf.reportProblems(file, problems);
    }
    else {
      wolf.weHaveGotProblems(file, problems);
    }
  }

  public double getProgress() {
    // do not show progress of visible highlighters update
    return myUpdateAll ? super.getProgress() : -1;
  }

  private static List<Problem> convertToProblems(final Collection<HighlightInfo> infos, final VirtualFile file,
                                                 final boolean hasErrorElement) {
    List<Problem> problems = new SmartList<Problem>();
    for (HighlightInfo info : infos) {
      if (info.getSeverity() == HighlightSeverity.ERROR) {
        Problem problem = new ProblemImpl(file, info, hasErrorElement);
        problems.add(problem);
      }
    }
    return problems;
  }

  @Override
  public String toString() {
    return super.toString() + " updateAll="+myUpdateAll+" range=("+myStartOffset+","+myEndOffset+")";
  }
}
