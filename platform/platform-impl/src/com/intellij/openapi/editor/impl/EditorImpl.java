/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl;

import com.intellij.Patches;
import com.intellij.application.options.OptionsConstants;
import com.intellij.codeInsight.hint.DocumentFragmentTooltipRenderer;
import com.intellij.codeInsight.hint.EditorFragmentComponent;
import com.intellij.codeInsight.hint.TooltipController;
import com.intellij.codeInsight.hint.TooltipGroup;
import com.intellij.concurrency.JobScheduler;
import com.intellij.ide.*;
import com.intellij.ide.dnd.DnDManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.*;
import com.intellij.openapi.editor.colors.*;
import com.intellij.openapi.editor.colors.impl.DelegateColorScheme;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.*;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.ex.util.EmptyEditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterClient;
import com.intellij.openapi.editor.impl.event.MarkupModelListener;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapDrawingType;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapHelper;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.options.FontSize;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeGlassPane;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.components.JBScrollBar;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.Alarm;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.Processor;
import com.intellij.util.Producer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.HashMap;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.ui.ButtonlessScrollBarUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.UiNotifyConnector;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIntHashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.ScrollBarUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.*;
import java.awt.font.TextHitInfo;
import java.awt.im.InputMethodRequests;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.text.CharacterIterator;
import java.util.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class EditorImpl extends UserDataHolderBase implements EditorEx, HighlighterClient, Queryable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.EditorImpl");
  private static final Key DND_COMMAND_KEY = Key.create("DndCommand");
  public static final Key<Boolean> DO_DOCUMENT_UPDATE_TEST = Key.create("DoDocumentUpdateTest");
  public static final Key<Pair<String, String>> EDITABLE_AREA_MARKER = Key.create("editable.area.marker");
  private final DocumentImpl myDocument;

  private final JPanel myPanel;
  private final JScrollPane myScrollPane;
  private final EditorComponentImpl myEditorComponent;
  private final EditorGutterComponentImpl myGutterComponent;

  static {
    ComplementaryFontsRegistry.getFontAbleToDisplay(' ', 0,0, UIManager.getFont("Label.font").getFamily()); // load costly font info
  }

  private final CommandProcessor myCommandProcessor;
  private final MyScrollBar myVerticalScrollBar;

  private final CopyOnWriteArrayList<EditorMouseListener> myMouseListeners = ContainerUtil.createEmptyCOWList();
  private final CopyOnWriteArrayList<EditorMouseMotionListener> myMouseMotionListeners;

  private int myCharHeight = -1;
  private int myLineHeight = -1;
  private int myDescent = -1;

  private boolean myIsInsertMode = true;

  private final CaretCursor myCaretCursor;
  private final ScrollingTimer myScrollingTimer = new ScrollingTimer();

  private final Key<Object> MOUSE_DRAGGED_GROUP = Key.create("MouseDraggedGroup");

  private final DocumentListener myEditorDocumentAdapter;

  private final SettingsImpl mySettings;

  private boolean isReleased = false;

  private MouseEvent myMousePressedEvent = null;
  private MouseEvent myMouseMovedEvent = null;

  private int mySavedSelectionStart = -1;
  private int mySavedSelectionEnd = -1;
  private int myLastColumnNumber = 0;

  private final PropertyChangeSupport myPropertyChangeSupport = new PropertyChangeSupport(this);
  private MyEditable myEditable;

  private EditorColorsScheme myScheme;
  private ArrowPainter myTabPainter;
  private final boolean myIsViewer;
  private final SelectionModelImpl mySelectionModel;
  private final EditorMarkupModelImpl myMarkupModel;
  private final FoldingModelImpl myFoldingModel;
  private final ScrollingModelImpl myScrollingModel;
  private final CaretModelImpl myCaretModel;
  private final SoftWrapModelImpl mySoftWrapModel;

  private static final RepaintCursorCommand ourCaretBlinkingCommand;
  private MessageBusConnection myConnection;

  private int myMouseSelectionState = MOUSE_SELECTION_STATE_NONE;
  private FoldRegion myMouseSelectedRegion = null;

  private static final int MOUSE_SELECTION_STATE_NONE = 0;
  private static final int MOUSE_SELECTION_STATE_WORD_SELECTED = 1;
  private static final int MOUSE_SELECTION_STATE_LINE_SELECTED = 2;

  private final MarkupModelListener myMarkupModelListener;

  private EditorHighlighter myHighlighter;
  private final TextDrawingCallback myTextDrawingCallback = new MyTextDrawingCallback();

  private int myScrollBarOrientation;
  private boolean myMousePressedInsideSelection;
  private FontMetrics myPlainFontMetrics;
  private FontMetrics myBoldFontMetrics;
  private FontMetrics myItalicFontMetrics;
  private FontMetrics myBoldItalicFontMetrics;

  private static final int CACHED_CHARS_BUFFER_SIZE = 300;

  private final ArrayList<CachedFontContent> myFontCache = new ArrayList<CachedFontContent>();
  private FontInfo myCurrentFontType = null;

  private final EditorSizeContainer mySizeContainer = new EditorSizeContainer();

  private boolean myUpdateCursor;
  private int myCaretUpdateVShift;
  
  @Nullable
  private final Project myProject;
  private long myMouseSelectionChangeTimestamp;
  private int mySavedCaretOffsetForDNDUndoHack;
  private final ArrayList<FocusChangeListener> myFocusListeners = new ArrayList<FocusChangeListener>();

  private MyInputMethodHandler myInputMethodRequestsHandler;
  private InputMethodRequests myInputMethodRequestsSwingWrapper;
  private boolean myIsOneLineMode;
  private boolean myIsRendererMode;
  private VirtualFile myVirtualFile;
  private boolean myIsColumnMode = false;
  private Color myForcedBackground = null;
  private Dimension myPreferredSize;
  private int myVirtualPageHeight;
  private Runnable myGutterSizeUpdater = null;
  private boolean myGutterNeedsUpdate = false;
  private Alarm myAppleRepaintAlarm;

  private final Alarm myMouseSelectionStateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  private Runnable myMouseSelectionStateResetRunnable;

  private boolean myEmbeddedIntoDialogWrapper;
  private CachedFontContent myLastCache;
  private boolean mySpacesHaveSameWidth;

  /**
   * There is a possible case that specific font is used for particular text drawing operation (e.g. for 'before' and 'after'
   * soft wraps drawings). Hence, even if {@link #mySpacesHaveSameWidth} is <code>true</code>, space size for that specific
   * font may be different. So, we define additional flag that should indicate that {@link #myLastCache} should be reset.
   */
  private boolean myForceRefreshFont;
  private boolean mySoftWrapsChanged;

  private Point myLastBackgroundPosition = null;
  private Color myLastBackgroundColor = null;
  private int myLastBackgroundWidth;
  private static final boolean ourIsUnitTestMode = ApplicationManager.getApplication().isUnitTestMode();
  private final JPanel myHeaderPanel;

  private MouseEvent myInitialMouseEvent;
  private boolean myIgnoreMouseEventsConsecutiveToInitial;

  private String myReleasedAt = null;

  private EditorDropHandler myDropHandler;

  private char[] myPrefixText;
  private TextAttributes myPrefixAttributes;
  private final IndentsModel myIndentsModel;
  
  @Nullable
  private CharSequence myPlaceholderText;
  private int myLastPaintedPlaceholderWidth;
  
  private boolean myStickySelection;
  private int myStickySelectionStart;
  private boolean myScrollToCaret = true;

  private boolean myPaintSelection;
  
  private final EditorSizeAdjustmentStrategy mySizeAdjustmentStrategy = new EditorSizeAdjustmentStrategy();

  static {
    ourCaretBlinkingCommand = new RepaintCursorCommand();
    ourCaretBlinkingCommand.start();
  }

  EditorImpl(@NotNull Document document, boolean viewer, @Nullable Project project) {
    myProject = project;
    myDocument = (DocumentImpl)document;
    myScheme = createBoundColorSchemeDelegate(null);
    initTabPainter();
    myIsViewer = viewer;
    mySettings = new SettingsImpl(this);

    mySelectionModel = new SelectionModelImpl(this);
    myMarkupModel = new EditorMarkupModelImpl(this);
    myFoldingModel = new FoldingModelImpl(this);
    myCaretModel = new CaretModelImpl(this);
    mySoftWrapModel = new SoftWrapModelImpl(this);
    mySizeContainer.reset();

    myCommandProcessor = CommandProcessor.getInstance();

    myEditorDocumentAdapter = new EditorDocumentAdapter();
    if (project != null) {
      myConnection = project.getMessageBus().connect();
      myConnection.subscribe(DocumentBulkUpdateListener.TOPIC, new EditorDocumentBulkUpdateAdapter());
    }
    myMouseMotionListeners = ContainerUtil.createEmptyCOWList();

    myMarkupModelListener = new MarkupModelListener() {
      public void afterAdded(@NotNull RangeHighlighterEx highlighter) {
        attributesChanged(highlighter);
      }

      public void beforeRemoved(@NotNull RangeHighlighterEx highlighter) {
        attributesChanged(highlighter);
      }

      public void attributesChanged(@NotNull RangeHighlighterEx highlighter) {
        int textLength = myDocument.getTextLength();
        if (textLength == 0) return;

        int start = Math.min(Math.max(highlighter.getAffectedAreaStartOffset(), 0), textLength - 1);
        int end = Math.min(Math.max(highlighter.getAffectedAreaEndOffset(), 0), textLength - 1);

        int startLine = myDocument.getLineNumber(start);
        int endLine = myDocument.getLineNumber(end);
        repaintLines(Math.max(0, startLine - 1), Math.min(endLine + 1, getDocument().getLineCount()));
        GutterIconRenderer renderer = highlighter.getGutterIconRenderer();

        // optimization: there is no need to repaint error stripe if the highlighter is invisible on it
        if (renderer != null || highlighter.getErrorStripeMarkColor() != null) {
          ((EditorMarkupModelImpl)getMarkupModel()).repaint(start, end);
        }

        if (renderer != null) {
          updateGutterSize();
        }
        updateCaretCursor();
      }
    };

    ((MarkupModelEx)DocumentMarkupModel.forDocument(myDocument, myProject, true)).addMarkupModelListener(myMarkupModelListener);
    ((MarkupModelEx)getMarkupModel()).addMarkupModelListener(myMarkupModelListener);

    myDocument.addDocumentListener(myFoldingModel);
    myDocument.addDocumentListener(myCaretModel);
    myDocument.addDocumentListener(mySelectionModel);
    myDocument.addDocumentListener(myEditorDocumentAdapter);
    myDocument.addDocumentListener(mySoftWrapModel);

    myFoldingModel.addListener(mySoftWrapModel);

    myIndentsModel = new IndentsModelImpl(this);
    myCaretModel.addCaretListener(new CaretListener() {
      private LightweightHint myCurrentHint = null;
      private IndentGuideDescriptor myCurrentCaretGuide = null;

      public void caretPositionChanged(CaretEvent e) {
        if (myStickySelection) {
          int selectionStart = Math.min(myStickySelectionStart, getDocument().getTextLength() - 1);
          mySelectionModel.setSelection(selectionStart, myCaretModel.getVisualPosition(), myCaretModel.getOffset());
        }
        
        final IndentGuideDescriptor newGuide = myIndentsModel.getCaretIndentGuide();
        if (!Comparing.equal(myCurrentCaretGuide, newGuide)) {
          repaintGuide(newGuide);
          repaintGuide(myCurrentCaretGuide);
          myCurrentCaretGuide = newGuide;

          if (myCurrentHint != null) {
            myCurrentHint.hide();
            myCurrentHint = null;
          }

          if (newGuide != null) {
            final Rectangle visibleArea = getScrollingModel().getVisibleArea();
            final int line = newGuide.startLine;
            if (logicalLineToY(line) < visibleArea.y) {
              TextRange textRange = new TextRange(myDocument.getLineStartOffset(line), myDocument.getLineEndOffset(line));

              myCurrentHint = EditorFragmentComponent.showEditorFragmentHint(EditorImpl.this, textRange, false, false);
            }
          }
        }
      }
    });

    myCaretCursor = new CaretCursor();

    myFoldingModel.flushCaretShift();
    myScrollBarOrientation = VERTICAL_SCROLLBAR_RIGHT;

    mySoftWrapModel.addSoftWrapChangeListener(new SoftWrapChangeListener() {
      @Override
      public void softWrapAdded(@NotNull SoftWrap softWrap) {
        mySoftWrapsChanged = true;
      }

      @Override
      public void softWrapsRemoved() {
        mySoftWrapsChanged = true;
      }
    });
    
    mySoftWrapModel.addVisualSizeChangeListener(new VisualSizeChangeListener() {
      @Override
      public void onLineWidthsChange(int startLine, int oldEndLine, int newEndLine, @NotNull TIntIntHashMap lineWidths) {
        mySizeContainer.update(startLine, newEndLine, oldEndLine);
        for (int i = startLine; i <= newEndLine; i++) {
          if (lineWidths.contains(i)) {
            int width = lineWidths.get(i);
            if (width >= 0) {
              mySizeContainer.updateLineWidthIfNecessary(i, width);
            }
          }
        }
      }
    });

    EditorHighlighter highlighter = new EmptyEditorHighlighter(myScheme.getAttributes(HighlighterColors.TEXT));
    setHighlighter(highlighter);

    myEditorComponent = new EditorComponentImpl(this);
    myScrollPane = new MyScrollPane();
    myPanel = new JPanel();

    myHeaderPanel = new MyHeaderPanel();
    myVerticalScrollBar = new MyScrollBar(Adjustable.VERTICAL);
    myGutterComponent = new EditorGutterComponentImpl(this);
    initComponent();

    myScrollingModel = new ScrollingModelImpl(this);
    
    if (mySettings.isUseSoftWraps()) {
      mySettings.setAdditionalColumnsCount(0);
    }

    myGutterComponent.updateSize();
    Dimension preferredSize = getPreferredSize();
    myEditorComponent.setSize(preferredSize);

    if (Patches.APPLE_BUG_ID_3716835) {
      myScrollingModel.addVisibleAreaListener(new VisibleAreaListener() {
        public void visibleAreaChanged(VisibleAreaEvent e) {
          if (myAppleRepaintAlarm == null) {
            myAppleRepaintAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
          }
          myAppleRepaintAlarm.cancelAllRequests();
          myAppleRepaintAlarm.addRequest(new Runnable() {
            public void run() {
              repaint(0, myDocument.getTextLength());
            }
          }, 50, ModalityState.stateForComponent(myEditorComponent));
        }
      });
    }

    updateCaretCursor();

    // This hacks context layout problem where editor appears scrolled to the right just after it is created.
    if (!ourIsUnitTestMode) {
      UiNotifyConnector.doWhenFirstShown(myEditorComponent, new Runnable() {
        public void run() {
          if (!isDisposed() && !myScrollingModel.isScrollingNow()) {
            myScrollingModel.disableAnimation();
            myScrollingModel.scrollHorizontally(0);
            myScrollingModel.enableAnimation();
          }
        }
      });
    }
  }

  public EditorColorsScheme createBoundColorSchemeDelegate(@Nullable final EditorColorsScheme customGlobalScheme) {
    return new MyColorSchemeDelegate(customGlobalScheme);
  }

  private void repaintGuide(IndentGuideDescriptor guide) {
    if (guide != null) {
      repaintLines(guide.startLine, guide.endLine);
    }
  }

  public void setPrefixTextAndAttributes(String prefixText, TextAttributes attributes) {
    myPrefixText = prefixText == null? null: prefixText.toCharArray();
    myPrefixAttributes = attributes;
  }

  public boolean isViewer() {
    return myIsViewer || myIsRendererMode;
  }

  public boolean isRendererMode() {
    return myIsRendererMode;
  }

  public void setRendererMode(boolean isRendererMode) {
    myIsRendererMode = isRendererMode;
  }

  public void setFile(VirtualFile vFile) {
    myVirtualFile = vFile;
    reinitSettings();
  }

  public VirtualFile getVirtualFile() {
    return myVirtualFile;
  }

  @Override
  public void setSoftWrapAppliancePlace(@NotNull SoftWrapAppliancePlaces place) {
    getSoftWrapModel().setPlace(place);
    mySettings.setSoftWrapAppliancePlace(place);
  }

  @NotNull
  public SelectionModel getSelectionModel() {
    return mySelectionModel;
  }

  @NotNull
  public MarkupModel getMarkupModel() {
    return myMarkupModel;
  }

  @NotNull
  public FoldingModelEx getFoldingModel() {
    return myFoldingModel;
  }

  @NotNull
  public CaretModelImpl getCaretModel() {
    return myCaretModel;
  }

  @NotNull
  public ScrollingModelEx getScrollingModel() {
    return myScrollingModel;
  }

  @NotNull
  public SoftWrapModelImpl getSoftWrapModel() {
    return mySoftWrapModel;
  }

  @NotNull
  public EditorSettings getSettings() {
    assertReadAccess();
    return mySettings;
  }

  public void reinitSettings() {
    assertIsDispatchThread();
    myCharHeight = -1;
    myLineHeight = -1;
    myDescent = -1;
    myPlainFontMetrics = null;

    boolean softWrapsUsedBefore = mySoftWrapModel.isSoftWrappingEnabled();

    mySoftWrapModel.reinitSettings();
    myCaretModel.reinitSettings();
    mySelectionModel.reinitSettings();
    mySettings.reinitSettings();
    ourCaretBlinkingCommand.setBlinkCaret(mySettings.isBlinkCaret());
    ourCaretBlinkingCommand.setBlinkPeriod(mySettings.getCaretBlinkPeriod());
    mySizeContainer.reset();
    myFoldingModel.rebuild();

    if (softWrapsUsedBefore ^ mySoftWrapModel.isSoftWrappingEnabled()) {
      mySizeContainer.reset();
      validateSize();
    }

    final EditorColorsScheme scheme =
      myScheme instanceof DelegateColorScheme? ((DelegateColorScheme)myScheme).getDelegate() : myScheme;
    if (scheme instanceof MyColorSchemeDelegate) {
      ((MyColorSchemeDelegate)scheme).updateGlobalScheme();
    }
    myHighlighter.setColorScheme(myScheme);
    myFoldingModel.refreshSettings();

    myGutterComponent.reinitSettings();
    myGutterComponent.revalidate();

    myEditorComponent.repaint();

    initTabPainter();
    updateCaretCursor();

    if (myInitialMouseEvent != null) {
      myIgnoreMouseEventsConsecutiveToInitial = true;
    }

    // There is a possible case that 'use soft wrap' setting value is changed and we need to repaint all affected lines then.
    repaintToScreenBottom(getCaretModel().getLogicalPosition().line);
    int y = getCaretModel().getVisualLineStart() * getLineHeight();
    myGutterComponent.repaint(0, y, myGutterComponent.getWidth(), myGutterComponent.getHeight() - y);
    getCaretModel().moveToOffset(getCaretModel().getOffset());
  }

  private void initTabPainter() {
    myTabPainter = new ArrowPainter(
      ColorProvider.byColorsScheme(myScheme, EditorColors.WHITESPACES_COLOR),
      new Computable<Integer>() {
        @Override
        public Integer compute() {
          return getCharHeight();
        }
      }
    );
  }

  public void release() {
    assertIsDispatchThread();
    if (isReleased) {
      LOG.error("Double release. First released at:  =====\n" + myReleasedAt+"\n======");
    }

    myReleasedAt = StringUtil.getThrowableText(new Throwable());

    isReleased = true;
    myDocument.removeDocumentListener(myHighlighter);
    myDocument.removeDocumentListener(myEditorDocumentAdapter);
    myDocument.removeDocumentListener(myFoldingModel);
    myDocument.removeDocumentListener(myCaretModel);
    myDocument.removeDocumentListener(mySelectionModel);
    myDocument.removeDocumentListener(mySoftWrapModel);

    myFoldingModel.removeListener(mySoftWrapModel);
    myFoldingModel.dispose();

    mySoftWrapModel.release();

    MarkupModelEx markupModel = (MarkupModelEx)DocumentMarkupModel.forDocument(myDocument, myProject, false);
    if (markupModel instanceof MarkupModelImpl) {
      markupModel.removeMarkupModelListener(myMarkupModelListener);
    }

    myMarkupModel.dispose();

    myLineHeight = -1;
    myCharHeight = -1;
    myDescent = -1;
    myPlainFontMetrics = null;
    myScrollingModel.dispose();
    myGutterComponent.dispose();
    myMousePressedEvent = null;
    myMouseMovedEvent = null;
    Disposer.dispose(myCaretModel);
    clearCaretThread();

    myFocusListeners.clear();

    if (myConnection != null) {
      myConnection.disconnect();
    }
  }

  private void clearCaretThread() {
    synchronized (ourCaretBlinkingCommand) {
      if (ourCaretBlinkingCommand.myEditor == this) {
        ourCaretBlinkingCommand.myEditor = null;
      }
    }
  }

  private void initComponent() {
//    myStatusBar = new EditorStatusBarImpl();

    //myPanel.setLayout(new BoxLayout(myPanel, BoxLayout.Y_AXIS));
    myPanel.setLayout(new BorderLayout());

    myPanel.add(myHeaderPanel, BorderLayout.NORTH);

    myGutterComponent.setOpaque(true);

    myScrollPane.setVerticalScrollBar(myVerticalScrollBar);
    myScrollPane.setViewportView(myEditorComponent);
    //myScrollPane.setBorder(null);
    myScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
    myScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);


    myScrollPane.setRowHeaderView(myGutterComponent);
    stopOptimizedScrolling();

    myEditorComponent.setTransferHandler(new MyTransferHandler());
    myEditorComponent.setAutoscrolls(true);

/*  Default mode till 1.4.0
 *   myScrollPane.getViewport().setScrollMode(JViewport.BLIT_SCROLL_MODE);
 */

    if (mayShowToolbar()) {
      JLayeredPane layeredPane = new JLayeredPane() {
        @Override
        public void doLayout() {
          final Component[] components = getComponents();
          final Rectangle r = getBounds();
          for (Component c : components) {
            if (c instanceof JScrollPane) {
              c.setBounds(0, 0, r.width, r.height);
            }
            else {
              final Dimension d = c.getPreferredSize();
              final MyScrollBar scrollBar = getVerticalScrollBar();
              c.setBounds(r.width - d.width - scrollBar.getWidth() - 30, 20, d.width, d.height);
            }
          }
        }
      };

      layeredPane.add(myScrollPane, JLayeredPane.DEFAULT_LAYER);
      myPanel.add(layeredPane);

      new ContextMenuImpl(layeredPane, myScrollPane, this);
    }
    else {
      myPanel.add(myScrollPane);
    }

    myEditorComponent.addKeyListener(new KeyAdapter() {
      public void keyTyped(KeyEvent event) {
        if (Patches.APPLE_BUG_ID_3337563) return; // Everything is going through InputMethods under MacOS X in JDK releases earlier than 1.4.2_03-117.1
        if (event.isConsumed()) {
          return;
        }
        if (processKeyTyped(event)) {
          event.consume();
        }
      }
    });

    MyMouseAdapter mouseAdapter = new MyMouseAdapter();
    myEditorComponent.addMouseListener(mouseAdapter);
    myGutterComponent.addMouseListener(mouseAdapter);

    MyMouseMotionListener mouseMotionListener = new MyMouseMotionListener();
    myEditorComponent.addMouseMotionListener(mouseMotionListener);
    myGutterComponent.addMouseMotionListener(mouseMotionListener);

    myEditorComponent.addFocusListener(new FocusAdapter() {
      public void focusGained(FocusEvent e) {
        myCaretCursor.activate();
        int caretLine = getCaretModel().getLogicalPosition().line;
        repaintLines(caretLine, caretLine);
        fireFocusGained();
        if (myGutterNeedsUpdate) {
          updateGutterSize();
        }
      }

      public void focusLost(FocusEvent e) {
        clearCaretThread();
        int caretLine = getCaretModel().getLogicalPosition().line;
        repaintLines(caretLine, caretLine);
        fireFocusLost();
      }
    });

    try {
      final DropTarget dropTarget = myEditorComponent.getDropTarget();
      if (dropTarget != null) { // might be null in headless environment
        dropTarget.addDropTargetListener(new DropTargetAdapter() {
          public void drop(DropTargetDropEvent e) {
          }

          public void dragOver(DropTargetDragEvent e) {
            Point location = e.getLocation();

            moveCaretToScreenPos(location.x, location.y);
            getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
          }
        });
      }
    }
    catch (TooManyListenersException e) {
      LOG.error(e);
    }

    myPanel.addComponentListener(new ComponentAdapter() {
      public void componentResized(ComponentEvent e) {
        myMarkupModel.repaint(0, myDocument.getTextLength());
      }
    });
  }

  private boolean mayShowToolbar() {
    return !isEmbeddedIntoDialogWrapper() && !isOneLineMode() && ContextMenuImpl.mayShowToolbar(myDocument);
  }

  public void setFontSize(final int fontSize) {
    int oldFontSize = myScheme.getEditorFontSize();
    myScheme.setEditorFontSize(fontSize);
    myPropertyChangeSupport.firePropertyChange(PROP_FONT_SIZE, oldFontSize, fontSize);
    // Update vertical scroll bar bounds if necessary (we had a problem that use increased editor font size and it was not possible
    // to scroll to the bottom of the document).
    myScrollPane.getViewport().invalidate();
  }

  public ActionCallback type(final String text) {
    final ActionCallback result = new ActionCallback();

    Application app = ApplicationManager.getApplication();
    if (!app.isWriteAccessAllowed()) {
      result.setRejected();
    } else {
      app.runWriteAction(new Runnable() {
        public void run() {
          for (int i = 0; i < text.length(); i++) {
            if (!processKeyTyped(text.charAt(i))) {
              result.setRejected();
              return;
            }
          }

          result.setDone();
        }
      });
    }

    return result;
  }

  private boolean processKeyTyped(char c) {
    // [vova] This is patch for Mac OS X. Under Mac "input methods"
    // is handled before our EventQueue consume upcoming KeyEvents.
    IdeEventQueue queue = IdeEventQueue.getInstance();
    if (queue.shouldNotTypeInEditor() || ProgressManager.getInstance().hasModalProgressIndicator()) {
      return false;
    }
    ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
    DataContext dataContext = getDataContext();
    actionManager.fireBeforeEditorTyping(c, dataContext);
    EditorActionManager.getInstance().getTypedAction().actionPerformed(this, c, dataContext);

    return true;
  }

  private void fireFocusLost() {
    FocusChangeListener[] listeners = getFocusListeners();
    for (FocusChangeListener listener : listeners) {
      listener.focusLost(this);
    }
  }

  private FocusChangeListener[] getFocusListeners() {
    return myFocusListeners.toArray(new FocusChangeListener[myFocusListeners.size()]);
  }

  private void fireFocusGained() {
    FocusChangeListener[] listeners = getFocusListeners();
    for (FocusChangeListener listener : listeners) {
      listener.focusGained(this);
    }
  }

  public void setHighlighter(@NotNull EditorHighlighter highlighter) {
    assertIsDispatchThread();
    final Document document = getDocument();
    if (myHighlighter != null) {
      document.removeDocumentListener(myHighlighter);
    }

    document.addDocumentListener(highlighter);
    highlighter.setEditor(this);
    highlighter.setText(document.getCharsSequence());
    myHighlighter = highlighter;
    EditorHighlighterCache.rememberEditorHighlighterForCachesOptimization(document, highlighter);

    if (myPanel != null) {
      reinitSettings();
    }
  }

  public EditorHighlighter getHighlighter() {
    assertReadAccess();
    return myHighlighter;
  }

  @NotNull
  public EditorComponentImpl getContentComponent() {
    return myEditorComponent;
  }

  public EditorGutterComponentEx getGutterComponentEx() {
    return myGutterComponent;
  }

  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
    myPropertyChangeSupport.addPropertyChangeListener(listener);
  }

  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
    myPropertyChangeSupport.removePropertyChangeListener(listener);
  }

  public void setInsertMode(boolean mode) {
    assertIsDispatchThread();
    boolean oldValue = myIsInsertMode;
    myIsInsertMode = mode;
    myPropertyChangeSupport.firePropertyChange(PROP_INSERT_MODE, oldValue, mode);
    //Repaint the caret line by moving caret to the same place
    LogicalPosition caretPosition = getCaretModel().getLogicalPosition();
    getCaretModel().moveToLogicalPosition(caretPosition);
  }

  public boolean isInsertMode() {
    return myIsInsertMode;
  }

  public void setColumnMode(boolean mode) {
    assertIsDispatchThread();
    boolean oldValue = myIsColumnMode;
    myIsColumnMode = mode;
    myPropertyChangeSupport.firePropertyChange(PROP_COLUMN_MODE, oldValue, mode);
  }

  public boolean isColumnMode() {
    return myIsColumnMode;
  }

  private int yPositionToVisibleLine(int y) {
    return y / getLineHeight();
  }

  int yPositionToLogicalLine(int y) {
    int line = yPositionToVisibleLine(y);
    if (line <= 0) {
      return 0;
    }
    LogicalPosition logicalPosition = visualToLogicalPosition(new VisualPosition(line, 0));
    return logicalPosition.line;
  }

  @NotNull
  public VisualPosition xyToVisualPosition(@NotNull Point p) {
    int line = yPositionToVisibleLine(p.y);
    int px = p.x;
    if (line == 0 && myPrefixText != null) {
      for (char c : myPrefixText) {
        px -= EditorUtil.charWidth(c, myPrefixAttributes.getFontType(), this);
      }
    }

    int textLength = myDocument.getTextLength();
    LogicalPosition logicalPosition = visualToLogicalPosition(new VisualPosition(line, 0));
    int offset = logicalPositionToOffset(logicalPosition);

    if (offset >= textLength) return new VisualPosition(line, EditorUtil.columnsNumber(p.x, EditorUtil.getSpaceWidth(Font.PLAIN, this)));

    // There is a possible case that starting logical line is split by soft-wraps and it's part after the split should be drawn.
    // We mark that we're under such circumstances then.
    boolean activeSoftWrapProcessed = logicalPosition.softWrapLinesOnCurrentLogicalLine <= 0;

    int column = 0;
    int prevX = 0;
    CharSequence text = myDocument.getCharsNoThreadCheck();
    char c = ' ';

    LogicalPosition endLogicalPosition = visualToLogicalPosition(new VisualPosition(line+1, 0));
    int endOffset = logicalPositionToOffset(endLogicalPosition);

    IterationState state = new IterationState(this, offset, endOffset, false);

    int fontType = state.getMergedAttributes().getFontType();
    int spaceSize = EditorUtil.getSpaceWidth(fontType, this);

    int x = 0;
    int charWidth;
    boolean onSoftWrapDrawing = false;
    outer:
    while (true) {
      charWidth = -1;
      if (offset >= textLength) {
        break;
      }

      if (offset >= state.getEndOffset()) {
        state.advance();
        fontType = state.getMergedAttributes().getFontType();
      }

      SoftWrap softWrap = mySoftWrapModel.getSoftWrap(offset);
      if (softWrap != null) {
        if (activeSoftWrapProcessed) {
          prevX = x;
          charWidth = getSoftWrapModel().getMinDrawingWidthInPixels(SoftWrapDrawingType.BEFORE_SOFT_WRAP_LINE_FEED);
          x += charWidth;
          if (x >= px) {
            onSoftWrapDrawing = true;
          }
          else {
            column++;
          }
          break outer;
        }
        else {
          CharSequence softWrapText = softWrap.getText();
          for (int i = 1/*Assuming line feed is located at the first position*/; i < softWrapText.length(); i++) {
            c = softWrapText.charAt(i);
            prevX = x;
            charWidth = charToVisibleWidth(c, fontType, x);
            x += charWidth;
            if (x >= px) {
              break outer;
            }
            column += EditorUtil.columnsNumber(c, x, prevX, spaceSize);
          }

          // Process 'after soft wrap' sign.
          prevX = x;
          charWidth = mySoftWrapModel.getMinDrawingWidthInPixels(SoftWrapDrawingType.AFTER_SOFT_WRAP);
          x += charWidth;
          if (x >= px) {
            onSoftWrapDrawing = true;
            break outer;
          }
          column++;
          activeSoftWrapProcessed = true;
        }
      }
      FoldRegion region = state.getCurrentFold();
      if (region != null) {
        char[] placeholder = region.getPlaceholderText().toCharArray();
        for (char aPlaceholder : placeholder) {
          c = aPlaceholder;
          x += EditorUtil.charWidth(c, fontType, this);
          if (x >= px) {
            break outer;
          }
          column++;
        }
        offset = region.getEndOffset();
      }
      else {
        prevX = x;
        c = text.charAt(offset);
        charWidth = charToVisibleWidth(c, fontType, x);
        if (charWidth == 0) {
          break;
        }
        x += charWidth;

        if (x >= px) {
          break;
        }
        column += EditorUtil.columnsNumber(c, x, prevX, spaceSize);

        offset++;
      }
    }

    if (charWidth < 0) {
      charWidth = EditorUtil.charWidth(c, fontType, this);
    }
    
    if (charWidth < 0) {
      charWidth = spaceSize;
    }

    if (x >= px && c == '\t' && !onSoftWrapDrawing) {
      if (mySettings.isCaretInsideTabs()) {
        column += (px - prevX) / spaceSize;
        if ((px - prevX) % spaceSize > spaceSize / 2) column++;
      }
      else if ((x - px) * 2 < x - prevX) {
        column += EditorUtil.columnsNumber(c, x, prevX, spaceSize);
      }
    }
    else {
      if (x >= px) {
        if ((x - px) * 2 < charWidth) column++;
      }
      else {
        int diff = px - x;
        column += diff / spaceSize;
        if (diff % spaceSize * 2 >= spaceSize) {
          column++;
        }
      }
    }

    return new VisualPosition(line, column);
  }

  /**
   * Allows to answer how much width requires given char to be represented on a screen.
   *
   * @param c           target character
   * @param fontType    font type to use for representation of the given character
   * @param currentX    current <code>'x'</code> position on a line where given character should be displayed
   * @return            width required to represent given char with the given settings on a screen;
   *                    <code>'0'</code> if given char is a line break
   */
  private int charToVisibleWidth(char c, int fontType, int currentX) {
    if (c == '\n') {
      return 0;
    }

    if (c == '\t') {
      return EditorUtil.nextTabStop(currentX, this) - currentX;
    }
    return EditorUtil.charWidth(c, fontType, this);
  }

  @NotNull
  public VisualPosition offsetToVisualPosition(int offset) {
    return logicalToVisualPosition(offsetToLogicalPosition(offset));
  }

  @NotNull
  public LogicalPosition offsetToLogicalPosition(int offset) {
    return offsetToLogicalPosition(offset, true);
  }

  @NotNull
  @Override
  public LogicalPosition offsetToLogicalPosition(int offset, boolean softWrapAware) {
    if (softWrapAware) {
      return mySoftWrapModel.offsetToLogicalPosition(offset);
    }
    int line = offsetToLogicalLine(offset, false);
    int column = calcColumnNumber(offset, line, false);
    return new LogicalPosition(line, column);
  }

  @TestOnly
  public void setCaretActive() {
    synchronized (ourCaretBlinkingCommand) {
      ourCaretBlinkingCommand.myEditor = this;
    }
  }

  // optimization: do not do column calculations here since we are interested in line number only
  public int offsetToVisualLine(int offset) {
    int textLength = getDocument().getTextLength();
    if (offset >= textLength) {
      int result = Math.max(0, getVisibleLineCount() - 1); // lines are 0 based
      if (textLength > 0 && getDocument().getCharsSequence().charAt(textLength - 1) == '\n') {
        result++;
      }
      return result;
    }
    int line = offsetToLogicalLine(offset);
    int lineStartOffset = line >= myDocument.getLineCount() ? myDocument.getTextLength() : myDocument.getLineStartOffset(line);

    int result = logicalToVisualLine(line);
    
    // There is a possible case that logical line that contains target offset is soft-wrapped (represented in more than one visual
    // line). Hence, we need to perform necessary adjustments to the visual line that is used to show logical line start if necessary.
    int i = getSoftWrapModel().getSoftWrapIndex(lineStartOffset);
    if (i < 0) {
      i = -i - 1;
    }
    List<? extends SoftWrap> softWraps = getSoftWrapModel().getRegisteredSoftWraps();
    for (; i < softWraps.size(); i++) {
      SoftWrap softWrap = softWraps.get(i);
      if (softWrap.getStart() > offset) {
        break;
      }
      result++; // Assuming that every soft wrap contains only one virtual line feed symbol
    }
    return result;
  }
  private int logicalToVisualLine(int line) {
    assertReadAccess();
    return logicalToVisualPosition(new LogicalPosition(line, 0)).line;
  }

  @NotNull
  public LogicalPosition xyToLogicalPosition(@NotNull Point p) {
    Point pp = p.x >= 0 && p.y >= 0 ? p : new Point(Math.max(p.x, 0), Math.max(p.y, 0));

    return visualToLogicalPosition(xyToVisualPosition(pp));
  }

  private int logicalLineToY(int line) {
    VisualPosition visible = logicalToVisualPosition(new LogicalPosition(line, 0));
    return visibleLineToY(visible.line);
  }

  @NotNull
  public Point logicalPositionToXY(@NotNull LogicalPosition pos) {
    VisualPosition visible = logicalToVisualPosition(pos);
    return visualPositionToXY(visible);
  }

  @NotNull
  public Point visualPositionToXY(@NotNull VisualPosition visible) {
    int y = visibleLineToY(visible.line);
    LogicalPosition logical = visualToLogicalPosition(new VisualPosition(visible.line, 0));
    int logLine = logical.line;

    int lineStartOffset = -1;
    int reserved = 0;
    int column = visible.column;

    if (logical.softWrapLinesOnCurrentLogicalLine > 0) {
      int linesToSkip = logical.softWrapLinesOnCurrentLogicalLine;
      List<? extends SoftWrap> softWraps = getSoftWrapModel().getSoftWrapsForLine(logLine);
      for (SoftWrap softWrap : softWraps) {
        if (myFoldingModel.isOffsetCollapsed(softWrap.getStart()) && myFoldingModel.isOffsetCollapsed(softWrap.getStart() - 1)) {
          continue;
        }
        linesToSkip--; // Assuming here that every soft wrap has exactly one line feed
        if (linesToSkip > 0) {
          continue;
        }
        lineStartOffset = softWrap.getStart();
        int widthInColumns = softWrap.getIndentInColumns();
        int widthInPixels = softWrap.getIndentInPixels();
        if (widthInColumns <= column) {
          column -= widthInColumns;
          reserved = widthInPixels;
        }
        else {
          char[] softWrapChars = softWrap.getChars();
          int i = CharArrayUtil.lastIndexOf(softWrapChars, '\n', 0, softWrapChars.length);
          int start = 0;
          if (i >= 0) {
            start = i + 1;
          }
          return new Point(EditorUtil.textWidth(this, softWrap.getText(), start, column + 1, Font.PLAIN, 0), y);
        }
        break;
      }
    }

    if (logLine < 0) {
      lineStartOffset = 0;
    }
    else if (lineStartOffset < 0) {
      if (logLine >= myDocument.getLineCount()) {
        lineStartOffset = myDocument.getTextLength();
      }
      else {
        lineStartOffset = myDocument.getLineStartOffset(logLine);
      }
    }

    int x = getTabbedTextWidth(lineStartOffset, column, reserved);
    return new Point(x, y);
  }

  private int getTabbedTextWidth(int startOffset, int length, int xOffset) {
    int x = xOffset;
    if (startOffset == 0 && myPrefixText != null) {
      for (char c : myPrefixText) {
        x += EditorUtil.charWidth(c, myPrefixAttributes.getFontType(), this);
      }
    }
    if (length <= 0) return x;
    int offset = startOffset;
    CharSequence text = myDocument.getCharsNoThreadCheck();
    int textLength = myDocument.getTextLength();
    IterationState state = new IterationState(this, startOffset, startOffset + length, false);
    int fontType = state.getMergedAttributes().getFontType();
    int spaceSize = EditorUtil.getSpaceWidth(fontType, this);

    int column = 0;
    outer:
    while (column < length) {
      if (offset >= textLength) break;

      if (offset >= state.getEndOffset()) {
        state.advance();
        fontType = state.getMergedAttributes().getFontType();
      }
      // We need to consider 'before soft wrap drawing'.
      SoftWrap softWrap = getSoftWrapModel().getSoftWrap(offset);
      if (softWrap != null && offset > startOffset) {
        column++;
        x += getSoftWrapModel().getMinDrawingWidthInPixels(SoftWrapDrawingType.BEFORE_SOFT_WRAP_LINE_FEED);
        // Assuming that first soft wrap symbol is line feed or all soft wrap symbols before the first line feed are spaces.
        break;
      }

      FoldRegion region = state.getCurrentFold();

      if (region != null) {
        char[] placeholder = region.getPlaceholderText().toCharArray();
        for (char aPlaceholder : placeholder) {
          x += EditorUtil.charWidth(aPlaceholder, fontType, this);
          column++;
          if (column >= length) break outer;
        }
        offset = region.getEndOffset();
      }
      else {
        char c = text.charAt(offset);
        if (c == '\n') {
          break;
        }
        if (c == '\t') {
          int prevX = x;
          x = EditorUtil.nextTabStop(x, this);
          int columnDiff = (x - prevX) / spaceSize;
          if ((x - prevX) % spaceSize > 0) {
            // There is a possible case that tabulation symbol takes more than one visual column to represent and it's shown at
            // soft-wrapped line. Soft wrap sign width may be not divisible by space size, hence, part of tabulation symbol represented
            // as a separate visual column may take less space than space width.
            columnDiff++;
          }
          column += columnDiff;
        }
        else {
          x += EditorUtil.charWidth(c, fontType, this);
          column++;
        }
        offset++;
      }
    }

    if (column != length) {
      x += EditorUtil.getSpaceWidth(fontType, this) * (length - column);
    }

    return x;
  }

  public int visibleLineToY(int line) {
    if (line < 0) throw new IndexOutOfBoundsException("Wrong line: " + line);
    return line * getLineHeight();
  }

  public void repaint(final int startOffset, final int endOffset) {
    if (!isShowing() || myScrollPane == null || myDocument.isInBulkUpdate()) {
      return;
    }

    int startOffsetToUse = startOffset;
    int endOffsetToUse = Math.min(endOffset, myDocument.getTextLength());
    assertIsDispatchThread();

    // We do repaint in case of equal offsets because there is a possible case that there is a soft wrap at the same offset and
    // it does occupy particular amount of visual space that may be necessary to repaint.
    if (startOffsetToUse <= endOffsetToUse) {
      int startLine = myDocument.getLineNumber(startOffsetToUse);
      int endLine = myDocument.getLineNumber(endOffsetToUse);
      repaintLines(startLine, endLine);
    }
  }

  private boolean isShowing() {
    return myGutterComponent != null && myGutterComponent.isShowing();
  }

  private void repaintToScreenBottom(int startLine) {
    Rectangle visibleArea = getScrollingModel().getVisibleArea();
    int yStartLine = logicalLineToY(startLine);
    int yEndLine = visibleArea.y + visibleArea.height;

    myEditorComponent.repaintEditorComponent(visibleArea.x, yStartLine, visibleArea.x + visibleArea.width, yEndLine - yStartLine);
    myGutterComponent.repaint(0, yStartLine, myGutterComponent.getWidth(), yEndLine - yStartLine);
  }

  /**
   * Asks to repaint all logical lines from the given <code>[start; end]</code> range.
   *
   * @param startLine     start logical line to repaint (inclusive)
   * @param endLine       end logical line to repaint (inclusive)
   */
  public void repaintLines(int startLine, int endLine) {
    if (!isShowing()) return;

    Rectangle visibleArea = getScrollingModel().getVisibleArea();
    int yStartLine = logicalLineToY(startLine);
    int endVisLine;
    if (myDocument.getTextLength() <= 0) {
      endVisLine = 0;
    }
    else {
      endVisLine = offsetToVisualLine(myDocument.getLineEndOffset(Math.min(myDocument.getLineCount() - 1, endLine)));
    }
    int height = endVisLine * getLineHeight() - yStartLine + getLineHeight() + WAVE_HEIGHT;

    myEditorComponent.repaintEditorComponent(visibleArea.x, yStartLine, visibleArea.x + visibleArea.width, height);
    myGutterComponent.repaint(0, yStartLine, myGutterComponent.getWidth(), height);
  }

  private void bulkUpdateStarted() {
  }

  private void bulkUpdateFinished() {
    if (myScrollPane == null) {
      return;
    }

    stopOptimizedScrolling();
    mySelectionModel.removeBlockSelection();
    
    mySizeContainer.reset();
    validateSize();

    updateGutterSize();
    repaintToScreenBottom(0);
    updateCaretCursor();
  }
  
  private void beforeChangedUpdate(DocumentEvent e) {
    if (isStickySelection()) {
      setStickySelection(false);
    }
    if (myDocument.isInBulkUpdate()) {
      // Assuming that the job is done at bulk listener callback methods.
      return;
    }

    Rectangle visibleArea = getScrollingModel().getVisibleArea();
    Point pos = visualPositionToXY(getCaretModel().getVisualPosition());
    myCaretUpdateVShift = pos.y - visibleArea.y;

    // We assume that size container is already notified with the visual line widths during soft wraps processing
    if (!mySoftWrapModel.isSoftWrappingEnabled()) {
      mySizeContainer.beforeChange(e);
    }
  }

  private void changedUpdate(DocumentEvent e) {
    if (myScrollPane == null || myDocument.isInBulkUpdate()) return;

    stopOptimizedScrolling();
    mySelectionModel.removeBlockSelection();

    // We assume that size container is already notified with the visual line widths during soft wraps processing
    if (!mySoftWrapModel.isSoftWrappingEnabled()) {
      mySizeContainer.changedUpdate(e);
    }
    validateSize();

    int startLine = offsetToLogicalLine(e.getOffset());
    int endLine = offsetToLogicalLine(e.getOffset() + e.getNewLength());

    boolean painted = false;
    if (myDocument.getTextLength() > 0) {
      int startDocLine = myDocument.getLineNumber(e.getOffset());
      int endDocLine = myDocument.getLineNumber(e.getOffset() + e.getNewLength());
      if (e.getOldLength() > e.getNewLength() || startDocLine != endDocLine) {
        updateGutterSize();
      }

      if (countLineFeeds(e.getOldFragment()) != countLineFeeds(e.getNewFragment())) {
        // Lines removed. Need to repaint till the end of the screen
        repaintToScreenBottom(startLine);
        painted = true;
      }
    }

    updateCaretCursor();
    if (!painted) {
      repaintLines(startLine, endLine);
    }

    if (!myDocument.isInBulkUpdate()) {
      Point caretLocation = visualPositionToXY(getCaretModel().getVisualPosition());
      int scrollOffset = caretLocation.y - myCaretUpdateVShift;
      getScrollingModel().scrollVertically(scrollOffset);
    }
  }

  public boolean isScrollToCaret() {
    return myScrollToCaret;
  }

  public void setScrollToCaret(boolean scrollToCaret) {
    myScrollToCaret = scrollToCaret;
  }

  private static int countLineFeeds(CharSequence c) {
    return StringUtil.countNewLines(c);
  }

  private void updateGutterSize() {
    if (myGutterSizeUpdater != null) return;
    myGutterSizeUpdater = new Runnable() {
      public void run() {
        if (!isDisposed()) {
          if (isShowing()) {
            myGutterComponent.updateSize();
            myGutterNeedsUpdate = false;
          }
          else {
            myGutterNeedsUpdate = true;
          }
        }
        myGutterSizeUpdater = null;
      }
    };

    SwingUtilities.invokeLater(myGutterSizeUpdater);
  }

  void validateSize() {
    Dimension dim = getPreferredSize();

    if (!dim.equals(myPreferredSize) && !myDocument.isInBulkUpdate()) {
      dim = mySizeAdjustmentStrategy.adjust(dim, myPreferredSize, this);
      if (dim == null) {
        return;
      } 
      myPreferredSize = dim;

      stopOptimizedScrolling();
      myGutterComponent.setLineNumberAreaWidth(new Convertor<Integer, Integer>() {
        @Override
        public Integer convert(Integer lineNumber) {
          return getFontMetrics(Font.PLAIN).stringWidth(Integer.toString(lineNumber + 2)) + 6;
        }
      });

      myEditorComponent.setSize(dim);
      myEditorComponent.fireResized();

      myMarkupModel.repaint(0, myDocument.getTextLength());
    }
  }

  void recalculateSizeAndRepaint() {
    mySizeContainer.reset();
    validateSize();
    myEditorComponent.repaintEditorComponent();
  }

  @NotNull
  public Document getDocument() {
    return myDocument;
  }

  @NotNull
  public JComponent getComponent() {
    return myPanel;
  }

  public void addEditorMouseListener(@NotNull EditorMouseListener listener) {
    myMouseListeners.add(listener);
  }

  public void removeEditorMouseListener(@NotNull EditorMouseListener listener) {
    boolean success = myMouseListeners.remove(listener);
    LOG.assertTrue(success);
  }

  public void addEditorMouseMotionListener(@NotNull EditorMouseMotionListener listener) {
    myMouseMotionListeners.add(listener);
  }

  public void removeEditorMouseMotionListener(@NotNull EditorMouseMotionListener listener) {
    boolean success = myMouseMotionListeners.remove(listener);
    LOG.assertTrue(success);
  }

  @Override
  public boolean isStickySelection() {
    return myStickySelection;
  }

  @Override
  public void setStickySelection(boolean enable) {
    myStickySelection = enable;
    if (enable) {
      myStickySelectionStart = getCaretModel().getOffset();
    }
    else {
      mySelectionModel.removeSelection();
    }
  }

  public boolean isDisposed() {
    return isReleased;
  }

  void paint(Graphics g) {
    startOptimizedScrolling();

    if (myUpdateCursor) {
      setCursorPosition();
      myUpdateCursor = false;
    }

    Rectangle clip = g.getClipBounds();

    if (clip == null) {
      return;
    }

    Rectangle visibleArea = getScrollingModel().getVisibleArea();
    if (visibleArea == null) {
      return;
    }

    if (isReleased) {
      g.setColor(new Color(128, 255, 128));
      g.fillRect(clip.x, clip.y, clip.width, clip.height);
      return;
    }
    if (myProject != null && myProject.isDisposed()) return;

    VisualPosition clipStartVisualPos = xyToVisualPosition(new Point(0, clip.y));
    LogicalPosition clipStartPosition = visualToLogicalPosition(clipStartVisualPos);
    int clipStartOffset = logicalPositionToOffset(clipStartPosition);
    LogicalPosition clipEndPosition = xyToLogicalPosition(new Point(0, clip.y + clip.height + getLineHeight()));
    int clipEndOffset = logicalPositionToOffset(clipEndPosition);
    paintBackgrounds(g, clip, clipStartPosition, clipStartVisualPos, clipStartOffset, clipEndOffset);
    paintRectangularSelection(g);
    paintRightMargin(g, clip);
    paintCustomRenderers((Graphics2D)g, clipStartOffset, clipEndOffset);
    MarkupModelEx docMarkup = (MarkupModelEx)DocumentMarkupModel.forDocument(myDocument, myProject, true);
    paintLineMarkersSeparators(g, clip, docMarkup, clipStartOffset, clipEndOffset);
    paintLineMarkersSeparators(g, clip, myMarkupModel, clipStartOffset, clipEndOffset);
    paintText(g, clip, clipStartPosition, clipStartOffset, clipEndOffset);
    paintPlaceholderText(g, clip);
    paintSegmentHighlightersBorderAndAfterEndOfLine(g, clip, clipStartOffset, clipEndOffset, docMarkup);
    BorderEffect borderEffect = new BorderEffect(this, g, clipStartOffset, clipEndOffset);
    borderEffect.paintHighlighters(getHighlighter());
    borderEffect.paintHighlighters(docMarkup);
    borderEffect.paintHighlighters(myMarkupModel);
    paintCaretCursor(g);
    
    paintComposedTextDecoration((Graphics2D)g);
  }

  private void paintCustomRenderers(final Graphics2D g, final int clipStartOffset, final int clipEndOffset) {
    myMarkupModel.processRangeHighlightersOverlappingWith(clipStartOffset, clipEndOffset, new Processor<RangeHighlighterEx>() {
      @Override
      public boolean process(RangeHighlighterEx highlighter) {
        final CustomHighlighterRenderer customRenderer = highlighter.getCustomRenderer();
        if (customRenderer != null && clipStartOffset < highlighter.getEndOffset() && highlighter.getStartOffset() < clipEndOffset) {
          customRenderer.paint(EditorImpl.this, highlighter, g);
        }
        return true;
      }
    });
  }

  public IndentsModel getIndentsModel() {
    return myIndentsModel;
  }

  public void setHeaderComponent(JComponent header) {
    myHeaderPanel.removeAll();
    if (header != null) {
      myHeaderPanel.add(header);
    }

    myHeaderPanel.revalidate();
  }

  public boolean hasHeaderComponent() {
    return myHeaderPanel.getComponentCount() > 0;
  }

  @Nullable
  public JComponent getHeaderComponent() {
    if (hasHeaderComponent()) {
      return (JComponent)myHeaderPanel.getComponent(0);
    }
    return null;
  }

  public void setBackgroundColor(Color color) {
    myScrollPane.setBackground(color);

    if (getBackgroundIgnoreForced().equals(color)) {
      myForcedBackground = null;
      return;
    }
    myForcedBackground = color;
  }

  public Color getForegroundColor() {
    return myScheme.getDefaultForeground();
  }

  public Color getBackgroundColor() {
    if (myForcedBackground != null) return myForcedBackground;

    return getBackgroundIgnoreForced();
  }

  @Override
  public TextDrawingCallback getTextDrawingCallback() {
    return myTextDrawingCallback;
  }

  @Override
  public void setPlaceholder(@Nullable CharSequence text) {
    myPlaceholderText = text;
  }

  private Color getBackgroundColor(final TextAttributes attributes) {
    final Color attrColor = attributes.getBackgroundColor();
    return Comparing.equal(attrColor, myScheme.getDefaultBackground()) ? getBackgroundColor() : attrColor;
  }

  private Color getBackgroundIgnoreForced() {
    Color color = myScheme.getDefaultBackground();
    if (myDocument.isWritable()) {
      return color;
    }
    Color readOnlyColor = myScheme.getColor(EditorColors.READONLY_BACKGROUND_COLOR);
    return readOnlyColor != null ? readOnlyColor : color;
  }

  private void paintComposedTextDecoration(Graphics2D g) {
    if (myInputMethodRequestsHandler != null && myInputMethodRequestsHandler.composedText != null) {
      VisualPosition visStart =
        offsetToVisualPosition(Math.min(myInputMethodRequestsHandler.composedTextStart, myDocument.getTextLength()));
      int y = visibleLineToY(visStart.line) + getLineHeight() - getDescent() + 1;
      Point p1 = visualPositionToXY(visStart);
      Point p2 =
        logicalPositionToXY(offsetToLogicalPosition(Math.min(myInputMethodRequestsHandler.composedTextEnd, myDocument.getTextLength())));

      Stroke saved = g.getStroke();
      BasicStroke dotted = new BasicStroke(1, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{0, 2, 0, 2}, 0);
      g.setStroke(dotted);
      UIUtil.drawLine(g, p1.x, y, p2.x, y);
      g.setStroke(saved);
    }
  }

  private void paintRightMargin(Graphics g, Rectangle clip) {
    Color rightMargin = myScheme.getColor(EditorColors.RIGHT_MARGIN_COLOR);
    if (!mySettings.isRightMarginShown() || rightMargin == null) {
      return;
    }
    int x = mySettings.getRightMargin(myProject) * EditorUtil.getSpaceWidth(Font.PLAIN, this);
    if (x >= clip.x && x < clip.x + clip.width) {
      g.setColor(rightMargin);
      UIUtil.drawLine(g, x, clip.y, x, clip.y + clip.height);
    }
  }

  private void paintSegmentHighlightersBorderAndAfterEndOfLine(final Graphics g,
                                                               Rectangle clip,
                                                               int clipStartOffset,
                                                               int clipEndOffset, MarkupModelEx docMarkup) {
    if (myDocument.getLineCount() == 0) return;
    final int startLine = yPositionToVisibleLine(clip.y);
    final int endLine = yPositionToVisibleLine(clip.y + clip.height) + 1;

    Processor<RangeHighlighterEx> paintProcessor = new Processor<RangeHighlighterEx>() {
      @Override
      public boolean process(RangeHighlighterEx highlighter) {
        paintSegmentHighlighterAfterEndOfLine(g, highlighter, startLine, endLine);
        return true;
      }
    };
    docMarkup.processRangeHighlightersOverlappingWith(clipStartOffset, clipEndOffset, paintProcessor);
    myMarkupModel.processRangeHighlightersOverlappingWith(clipStartOffset, clipEndOffset, paintProcessor);
  }

  private void paintSegmentHighlighterAfterEndOfLine(Graphics g,
                                                     RangeHighlighterEx segmentHighlighter,
                                                     int startLine,
                                                     int endLine) {
    if (!segmentHighlighter.isAfterEndOfLine()) {
      return;
    }
    int startOffset = segmentHighlighter.getStartOffset();
    int visibleStartLine = offsetToVisualLine(startOffset);

    if (getFoldingModel().isOffsetCollapsed(startOffset)) {
      return;
    }
    if (visibleStartLine >= startLine && visibleStartLine <= endLine) {
      int logStartLine = offsetToLogicalLine(startOffset);
      if (logStartLine >= myDocument.getLineCount()) {
        return;
      }
      LogicalPosition logPosition = offsetToLogicalPosition(myDocument.getLineEndOffset(logStartLine));
      Point end = logicalPositionToXY(logPosition);
      int charWidth = EditorUtil.getSpaceWidth(Font.PLAIN, this);
      int lineHeight = getLineHeight();
      TextAttributes attributes = segmentHighlighter.getTextAttributes();
      if (attributes != null && getBackgroundColor(attributes) != null) {
        g.setColor(getBackgroundColor(attributes));
        g.fillRect(end.x, end.y, charWidth, lineHeight);
      }
      if (attributes != null && attributes.getEffectColor() != null) {
        int y = visibleLineToY(visibleStartLine) + getLineHeight() - getDescent() + 1;
        g.setColor(attributes.getEffectColor());
        if (attributes.getEffectType() == EffectType.WAVE_UNDERSCORE) {
          drawWave(g, end.x, end.x + charWidth - 1, y);
        }
        else if (attributes.getEffectType() == EffectType.BOLD_DOTTED_LINE) {
          final int dottedAt = SystemInfo.isMac ? y - 1 : y;
          UIUtil.drawBoldDottedLine((Graphics2D)g, end.x, end.x + charWidth - 1, dottedAt,
                                    getBackgroundColor(attributes), attributes.getEffectColor(), false);
        }
        else if (attributes.getEffectType() == EffectType.STRIKEOUT) {
          int y1 = y - getCharHeight() / 2 - 1;
          UIUtil.drawLine(g, end.x, y1, end.x + charWidth - 1, y1);
        }
        else if (attributes.getEffectType() == EffectType.BOLD_LINE_UNDERSCORE) {
          UIUtil.drawLine(g, end.x, y - 1, end.x + charWidth - 1, y - 1);
          UIUtil.drawLine(g, end.x, y, end.x + charWidth - 1, y);
        }
        else if (attributes.getEffectType() != EffectType.BOXED) {
          UIUtil.drawLine(g, end.x, y, end.x + charWidth - 1, y);
        }
      }
    }
  }

  public int getMaxWidthInRange(int startOffset, int endOffset) {
    int width = 0;
    int start = offsetToVisualLine(startOffset);
    int end = offsetToVisualLine(endOffset);

    for (int i = start; i <= end; i++) {
      int lastColumn = EditorUtil.getLastVisualLineColumnNumber(this, i) + 1;
      int lineWidth = visualPositionToXY(new VisualPosition(i, lastColumn)).x;

      if (lineWidth > width) {
        width = lineWidth;
      }
    }

    return width;
  }

  private void paintBackgrounds(Graphics g,
                                Rectangle clip,
                                LogicalPosition clipStartPosition,
                                VisualPosition clipStartVisualPos,
                                int clipStartOffset, int clipEndOffset) {
    Color defaultBackground = getBackgroundColor();
    g.setColor(defaultBackground);
    g.fillRect(clip.x, clip.y, clip.width, clip.height);

    int lineHeight = getLineHeight();

    int visibleLine = yPositionToVisibleLine(clip.y);

    Point position = new Point(0, visibleLine * lineHeight);
    if (clipStartVisualPos.line == 0 && myPrefixText != null) {
      position.x = drawBackground(g, myPrefixAttributes.getBackgroundColor(), new String(myPrefixText), position, myPrefixAttributes.getFontType(),
                                  defaultBackground, clip);
    }

    if (clipStartPosition.line >= myDocument.getLineCount() || clipStartPosition.line < 0) {
      if (position.x > 0) flushBackground(g, clip);
      return;
    }

    myLastBackgroundPosition = null;
    myLastBackgroundColor = null;
    
    boolean locateBeforeSoftWrap = !SoftWrapHelper.isCaretAfterSoftWrap(this);
    int start = clipStartOffset;
    int end = clipEndOffset;
    getSoftWrapModel().registerSoftWrapsIfNecessary();
    IterationState iterationState = new IterationState(this, start, end, isPaintSelection());

    LineIterator lIterator = createLineIterator();
    lIterator.start(start);
    if (lIterator.atEnd()) {
      return;
    }

    TextAttributes attributes = iterationState.getMergedAttributes();
    Color backColor = getBackgroundColor(attributes);
    int fontType = attributes.getFontType();
    CharSequence text = myDocument.getCharsNoThreadCheck();
    int lastLineIndex = Math.max(0, myDocument.getLineCount() - 1);

    // There is a possible case that we need to draw background from the start of soft wrap-introduced visual line. Given position
    // has valid 'y' coordinate then at it shouldn't be affected by soft wrap that corresponds to the visual line start offset.
    // Hence, we store information about soft wrap to be skipped for further processing and adjust 'x' coordinate value if necessary.
    TIntHashSet softWrapsToSkip = new TIntHashSet();
    SoftWrap softWrap = getSoftWrapModel().getSoftWrap(start);
    if (softWrap != null) {
      softWrapsToSkip.add(softWrap.getStart());
      Color color = null;
      if (backColor != null && !backColor.equals(defaultBackground)) {
        color = backColor;
      }

      // There is a possible case that target clip points to soft wrap-introduced visual line and that it's an active
      // line (caret cursor is located on it). We want to draw corresponding 'caret line' background for soft wraps-introduced
      // virtual space then.
      if (color == null && position.y == getCaretModel().getVisualPosition().line * getLineHeight()) {
        color = getColorsScheme().getColor(EditorColors.CARET_ROW_COLOR);
      }
      
      if (color != null) {
        drawBackground(g, color, softWrap.getIndentInPixels(), position, defaultBackground, clip);
      }
      position.x = softWrap.getIndentInPixels();
    }
    
    // There is a possible case that caret is located at soft-wrapped line. We don't need to paint caret row background
    // on a last visual line of that soft-wrapped line then. Below is a holder for the flag that indicates if caret row
    // background is already drawn.
    boolean[] caretRowPainted = new boolean[1];

    while (!iterationState.atEnd() && !lIterator.atEnd()) {
      int hEnd = iterationState.getEndOffset();
      int lEnd = lIterator.getEnd();

      if (hEnd >= lEnd) {
        FoldRegion collapsedFolderAt = myFoldingModel.getCollapsedRegionAtOffset(start);
        if (collapsedFolderAt == null) {
          position.x = drawSoftWrapAwareBackground(g, backColor, text, start, lEnd - lIterator.getSeparatorLength(), position, fontType,
                                      defaultBackground, clip, softWrapsToSkip, caretRowPainted);

          if (lIterator.getLineNumber() < lastLineIndex) {
            if (backColor != null && !backColor.equals(defaultBackground)) {
              g.setColor(backColor);
              g.fillRect(position.x, position.y, clip.x + clip.width - position.x, lineHeight);
            }
          }
          else {
            paintAfterFileEndBackground(iterationState,
                                        g,
                                        position, clip,
                                        lineHeight, defaultBackground, caretRowPainted);
            break;
          }

          position.x = 0;
          if (position.y > clip.y + clip.height) break;
          position.y += lineHeight;
          start = lEnd;
        }

        lIterator.advance();
      }
      else {
        FoldRegion collapsedFolderAt = iterationState.getCurrentFold();
        if (collapsedFolderAt != null) {
          softWrap = mySoftWrapModel.getSoftWrap(collapsedFolderAt.getStartOffset());
          if (softWrap != null) {
            position.x = drawSoftWrapAwareBackground(
              g, backColor, text, collapsedFolderAt.getStartOffset(), collapsedFolderAt.getStartOffset(), position, fontType,
              defaultBackground, clip, softWrapsToSkip, caretRowPainted
            );
          }
          position.x = drawBackground(g, backColor, collapsedFolderAt.getPlaceholderText(), position, fontType, defaultBackground, clip);
        }
        else {
          if (hEnd > lEnd - lIterator.getSeparatorLength()) {
            position.x = drawSoftWrapAwareBackground(
              g, backColor, text, start, lEnd - lIterator.getSeparatorLength(), position, fontType,
              defaultBackground, clip, softWrapsToSkip, caretRowPainted
            );
          }
          else {
            position.x = drawSoftWrapAwareBackground(
              g, backColor, text, start, hEnd, position, fontType, defaultBackground, clip, softWrapsToSkip, caretRowPainted
            );
          }
        }

        iterationState.advance();
        attributes = iterationState.getMergedAttributes();
        backColor = getBackgroundColor(attributes);
        fontType = attributes.getFontType();
        start = iterationState.getStartOffset();
      }
    }

    flushBackground(g, clip);

    if (lIterator.getLineNumber() >= lastLineIndex && position.y <= clip.y + clip.height) {
      paintAfterFileEndBackground(iterationState, g, position, clip, lineHeight, defaultBackground, caretRowPainted);
    }

    // Perform additional activity if soft wrap is added or removed during repainting.
    if (mySoftWrapsChanged) {
      mySoftWrapsChanged = false;
      validateSize();

      // Repaint editor to the bottom in order to ensure that its content is shown correctly after new soft wrap introduction.
      repaintToScreenBottom(yPositionToLogicalLine(position.y));

      // Repaint gutter at all space that is located after active clip in order to ensure that line numbers are correctly redrawn
      // in accordance with the newly introduced soft wrap(s).
      myGutterComponent.repaint(0, clip.y, myGutterComponent.getWidth(), myGutterComponent.getHeight() - clip.y);

      // Ask caret model to update visual caret position.
      getCaretModel().moveToOffset(getCaretModel().getOffset(), locateBeforeSoftWrap);
    }
  }

  private void paintRectangularSelection(Graphics g) {
    final SelectionModel model = getSelectionModel();
    if (!model.hasBlockSelection()) return;
    final LogicalPosition blockStart = model.getBlockStart();
    final LogicalPosition blockEnd = model.getBlockEnd();
    assert blockStart != null;
    assert blockEnd != null;

    final Point start = logicalPositionToXY(blockStart);
    final Point end = logicalPositionToXY(blockEnd);
    g.setColor(myScheme.getColor(EditorColors.SELECTION_BACKGROUND_COLOR));
    final int y;
    final int height;
    if (start.y <= end.y) {
      y = start.y;
      height = end.y - y + getLineHeight();
    }
    else {
      y = end.y;
      height = start.y - end.y + getLineHeight();
    }
    final int x = Math.min(start.x, end.x);
    final int width = Math.max(2, Math.abs(end.x - start.x));
    g.fillRect(x, y, width, height);
  }

  private void paintAfterFileEndBackground(IterationState iterationState,
                                                  Graphics g,
                                                  Point position,
                                                  Rectangle clip,
                                                  int lineHeight,
                                                  final Color defaultBackground,
                                                  boolean[] caretRowPainted) {
    Color backColor = iterationState.getPastFileEndBackground();
    if (backColor == null || backColor.equals(defaultBackground)) {
      return;
    }
    if (caretRowPainted[0] && backColor.equals(getColorsScheme().getColor(EditorColors.CARET_ROW_COLOR))) {
        return;
    }
    g.setColor(backColor);
    g.fillRect(position.x, position.y, clip.x + clip.width - position.x, lineHeight);
  }

  private int drawSoftWrapAwareBackground(Graphics g, Color backColor, CharSequence text, int start, int end, Point position,
                                          int fontType, Color defaultBackground, Rectangle clip, TIntHashSet softWrapsToSkip,
                                          boolean[] caretRowPainted)
  {
    int startToUse = start;
    // Given 'end' offset is exclusive though SoftWrapModel.getSoftWrapsForRange() uses inclusive end offset.
    // Hence, we decrement it if necessary. Please note that we don't do that if start is equal to end. That is the case,
    // for example, for soft-wrapped collapsed fold region - we need to draw soft wrap before it.
    int softWrapRetrievalEndOffset = end;
    if (end > start) {
      softWrapRetrievalEndOffset--;
    }
    List<? extends SoftWrap> softWraps = getSoftWrapModel().getSoftWrapsForRange(start, softWrapRetrievalEndOffset);
    for (SoftWrap softWrap : softWraps) {
      int softWrapStart = softWrap.getStart();
      if (softWrapsToSkip.contains(softWrapStart)) {
        continue;
      }
      if (startToUse < softWrapStart) {
        position.x = drawBackground(g, backColor, text.subSequence(startToUse, softWrapStart), position, fontType, defaultBackground, clip);
      }
      boolean drawCustomBackgroundAtSoftWrapVirtualSpace = 
        !Comparing.equal(backColor, defaultBackground) && (softWrapStart > start || Comparing.equal(myLastBackgroundColor, backColor));
      drawSoftWrap(
        g, softWrap, position, fontType, backColor, drawCustomBackgroundAtSoftWrapVirtualSpace, defaultBackground, clip, caretRowPainted
      );
      startToUse = softWrapStart;
    }

    if (startToUse < end) {
      position.x = drawBackground(g, backColor, text.subSequence(startToUse, end), position, fontType, defaultBackground, clip);
    }
    return position.x;
  }

  private void drawSoftWrap(Graphics g, SoftWrap softWrap, Point position, int fontType, Color backColor,
                            boolean drawCustomBackgroundAtSoftWrapVirtualSpace, Color defaultBackground, Rectangle clip,
                            boolean[] caretRowPainted) 
  {
    // The main idea is to to do the following:
    //     *) update given drawing position coordinates in accordance with the current soft wrap;
    //     *) draw background at soft wrap-introduced virtual space if necessary;

    CharSequence softWrapText = softWrap.getText();
    int activeRowY = getCaretModel().getVisualPosition().line * getLineHeight();
    int afterSoftWrapWidth = clip.x + clip.width - position.x;
    if (drawCustomBackgroundAtSoftWrapVirtualSpace && backColor != null) {
      drawBackground(g, backColor, afterSoftWrapWidth, position, defaultBackground, clip);
    }
    else if (position.y == activeRowY) {
      // Draw 'active line' background after soft wrap.
      Color caretRowColor = getColorsScheme().getColor(EditorColors.CARET_ROW_COLOR);
      drawBackground(g, caretRowColor, afterSoftWrapWidth, position, defaultBackground, clip);
      caretRowPainted[0] = true;
    }

    paintSelectionOnFirstSoftWrapLineIfNecessary(g, position, clip, defaultBackground, fontType);
    
    int i = CharArrayUtil.lastIndexOf(softWrapText, "\n", softWrapText.length()) + 1;
    int width = getTextSegmentWidth(softWrapText.subSequence(i, softWrapText.length()), 0, fontType, clip)
                + getSoftWrapModel().getMinDrawingWidthInPixels(SoftWrapDrawingType.AFTER_SOFT_WRAP);
    position.x = 0;
    position.y += getLineHeight();
    
    if (drawCustomBackgroundAtSoftWrapVirtualSpace && backColor != null) {
      drawBackground(g, backColor, width, position, defaultBackground, clip);
    }
    else if (position.y == activeRowY) {
      // Draw 'active line' background for the soft wrap-introduced virtual space.
      Color caretRowColor = getColorsScheme().getColor(EditorColors.CARET_ROW_COLOR);
      drawBackground(g, caretRowColor, width, position, defaultBackground, clip);
    }
    
    position.x = 0;
    paintSelectionOnSecondSoftWrapLineIfNecessary(g, position, clip, defaultBackground, fontType, softWrap);
    position.x = width;
  }

  /**
   * End user is allowed to perform selection by visual coordinates (e.g. by dragging mouse with left button hold). There is a possible
   * case that such a move intersects with soft wrap introduced virtual space. We want to draw corresponding selection background
   * there then.
   * <p/>
   * This method encapsulates functionality of drawing selection background on the first soft wrap line (e.g. on a visual line where
   * it is applied).
   * 
   * @param g                   graphics to draw on
   * @param position            current position (assumed to be position of soft wrap appliance)
   * @param clip                target drawing area boundaries
   * @param defaultBackground   default background
   * @param fontType            current font type
   */
  private void paintSelectionOnFirstSoftWrapLineIfNecessary(Graphics g, Point position, Rectangle clip, Color defaultBackground,
                                                            int fontType) 
  {
    // There is a possible case that the user performed selection at soft wrap virtual space. We need to paint corresponding background
    // there then.
    VisualPosition selectionStartPosition = getSelectionModel().getSelectionStartPosition();
    VisualPosition selectionEndPosition = getSelectionModel().getSelectionEndPosition();
    if (selectionStartPosition == null || selectionEndPosition == null || selectionStartPosition.equals(selectionEndPosition)) {
      return;
    }
    
    int currentVisualLine = position.y / getLineHeight();
    int lastColumn = EditorUtil.getLastVisualLineColumnNumber(this, currentVisualLine);
    
    // Check if the first soft wrap line is within the visual selection.
    if (currentVisualLine < selectionStartPosition.line || currentVisualLine > selectionEndPosition.line 
        || currentVisualLine == selectionEndPosition.line && selectionEndPosition.column <= lastColumn) {
      return;
    }
    
    // Adjust 'x' if selection starts at soft wrap virtual space.
    final int columnsToSkip = selectionStartPosition.column - lastColumn;
    if (columnsToSkip > 0) {
      position.x += getSoftWrapModel().getMinDrawingWidthInPixels(SoftWrapDrawingType.BEFORE_SOFT_WRAP_LINE_FEED);
      position.x += (columnsToSkip - 1) * EditorUtil.getSpaceWidth(Font.PLAIN, this);
    }
    
    // Calculate selection width.
    final int width;
    if (selectionEndPosition.line > currentVisualLine) {
      width = clip.x + clip.width - position.x;
    }
    else if (selectionStartPosition.line < currentVisualLine || selectionStartPosition.column <= lastColumn) {
      width = getSoftWrapModel().getMinDrawingWidthInPixels(SoftWrapDrawingType.BEFORE_SOFT_WRAP_LINE_FEED) 
              + (selectionEndPosition.column - lastColumn - 1) * EditorUtil.getSpaceWidth(fontType, this);
    }
    else {
      width = (selectionEndPosition.column - selectionStartPosition.column) * EditorUtil.getSpaceWidth(fontType, this);
    }

    drawBackground(g, getColorsScheme().getColor(EditorColors.SELECTION_BACKGROUND_COLOR), width, position, defaultBackground, clip);
  }

  /**
   * End user is allowed to perform selection by visual coordinates (e.g. by dragging mouse with left button hold). There is a possible
   * case that such a move intersects with soft wrap introduced virtual space. We want to draw corresponding selection background
   * there then.
   * <p/>
   * This method encapsulates functionality of drawing selection background on the second soft wrap line (e.g. on a visual line after
   * the one where it is applied).
   *
   * @param g                   graphics to draw on
   * @param position            current position (assumed to be position of soft wrap appliance)
   * @param clip                target drawing area boundaries
   * @param defaultBackground   default background
   * @param fontType            current font type
   * @param softWrap            target soft wrap which second line virtual space may contain selection
   */
  private void paintSelectionOnSecondSoftWrapLineIfNecessary(Graphics g, Point position, Rectangle clip, Color defaultBackground,
                                                             int fontType, SoftWrap softWrap) 
  {
    // There is a possible case that the user performed selection at soft wrap virtual space. We need to paint corresponding background
    // there then.
    VisualPosition selectionStartPosition = getSelectionModel().getSelectionStartPosition();
    VisualPosition selectionEndPosition = getSelectionModel().getSelectionEndPosition();
    if (selectionStartPosition == null || selectionEndPosition == null || selectionStartPosition.equals(selectionEndPosition)) {
      return;
    }

    int currentVisualLine = position.y / getLineHeight();
    
    // Check if the second soft wrap line is within the visual selection.
    if (currentVisualLine < selectionStartPosition.line || currentVisualLine > selectionEndPosition.line 
        || currentVisualLine == selectionStartPosition.line && selectionStartPosition.column >= softWrap.getIndentInColumns()) {
      return;
    }

    // Adjust 'x' if selection starts at soft wrap virtual space.
    if (selectionStartPosition.line == currentVisualLine && selectionStartPosition.column > 0) {
      position.x += selectionStartPosition.column * EditorUtil.getSpaceWidth(fontType, this);
    }

    // Calculate selection width.
    final int width;
    if (selectionEndPosition.line > currentVisualLine || selectionEndPosition.column >= softWrap.getIndentInColumns()) {
      width = softWrap.getIndentInPixels() - position.x;
    }
    else {
      width = selectionEndPosition.column * EditorUtil.getSpaceWidth(fontType, this) - position.x;
    }

    drawBackground(g, getColorsScheme().getColor(EditorColors.SELECTION_BACKGROUND_COLOR), width, position, defaultBackground, clip);
  }
  
  private int drawBackground(Graphics g, Color backColor, CharSequence text, Point position, int fontType, Color defaultBackground,
                             Rectangle clip)
  {
    int width = getTextSegmentWidth(text, position.x, fontType, clip);
    return drawBackground(g, backColor, width, position, defaultBackground, clip);
  }

  private int drawBackground(Graphics g,
                             Color backColor,
                             int width,
                             Point position,
                             Color defaultBackground,
                             Rectangle clip) {
    if (backColor != null && !backColor.equals(defaultBackground) && clip.intersects(position.x, position.y, width, getLineHeight())) {
      if (backColor.equals(myLastBackgroundColor) && myLastBackgroundPosition.y == position.y &&
        myLastBackgroundPosition.x + myLastBackgroundWidth == position.x) {
        myLastBackgroundWidth += width;
      }
      else {
        flushBackground(g, clip);
        myLastBackgroundColor = backColor;
        myLastBackgroundPosition = new Point(position);
        myLastBackgroundWidth = width;
      }
    }

    return position.x + width;
  }

  private void flushBackground(Graphics g, final Rectangle clip) {
    if (myLastBackgroundColor != null) {
      final Point position = myLastBackgroundPosition;
      final int w = myLastBackgroundWidth;
      final int height = getLineHeight();
      if (clip.intersects(position.x, position.y, w, height)) {
        g.setColor(myLastBackgroundColor);
        g.fillRect(position.x, position.y, w, height);
      }
      myLastBackgroundColor = null;
    }
  }

  private LineIterator createLineIterator() {
    return myDocument.createLineIterator();
  }

  private void paintText(Graphics g,
                         Rectangle clip,
                         LogicalPosition clipStartPosition,
                         int clipStartOffset, int clipEndOffset) {
    myCurrentFontType = null;
    myLastCache = null;
    final int plainSpaceWidth = EditorUtil.getSpaceWidth(Font.PLAIN, this);
    final int boldSpaceWidth = EditorUtil.getSpaceWidth(Font.BOLD, this);
    final int italicSpaceWidth = EditorUtil.getSpaceWidth(Font.ITALIC, this);
    final int boldItalicSpaceWidth = EditorUtil.getSpaceWidth(Font.BOLD | Font.ITALIC, this);
    mySpacesHaveSameWidth =
      plainSpaceWidth == boldSpaceWidth && plainSpaceWidth == italicSpaceWidth && plainSpaceWidth == boldItalicSpaceWidth;

    int lineHeight = getLineHeight();

    int visibleLine = clip.y / lineHeight;

    // The main idea is that there is a possible case that we need to perform painting starting from soft-wrapped logical line.
    // We may want to skip necessary number of visual lines then. Hence, we remember logical position that corresponds to the starting
    // visual line in order to use it for further processing. As soon as necessary number of visual lines is skipped, logical
    // position is expected to be set to null as an indication that no soft wrap-introduced visual lines should be skipped on
    // current painting iteration.
    Ref<LogicalPosition> logicalPosition = new Ref<LogicalPosition>(clipStartPosition);
    int startLine = logicalPosition.get().line;
    int start = clipStartOffset;

    Point position = new Point(0, visibleLine * lineHeight);
    if (startLine == 0 && myPrefixText != null) {
      position.x = drawStringWithSoftWraps(g, myPrefixText, 0, myPrefixText.length, position, clip,
                                           myPrefixAttributes.getEffectColor(), myPrefixAttributes.getEffectType(),
                                           myPrefixAttributes.getFontType(), myPrefixAttributes.getForegroundColor(), logicalPosition);
    }
    if (startLine >= myDocument.getLineCount() || startLine < 0) {
      if (position.x > 0) flushCachedChars(g);
      return;
    }

    IterationState iterationState = new IterationState(this, start, clipEndOffset, isPaintSelection());
    LineIterator lIterator = createLineIterator();
    lIterator.start(start);
    if (lIterator.atEnd()) {
      return;
    }

    TextAttributes attributes = iterationState.getMergedAttributes();
    Color currentColor = attributes.getForegroundColor();
    if (currentColor == null) {
      currentColor = getForegroundColor();
    }
    Color effectColor = attributes.getEffectColor();
    EffectType effectType = attributes.getEffectType();
    int fontType = attributes.getFontType();
    g.setColor(currentColor);

    final char[] chars = myDocument.getRawChars();

    while (!iterationState.atEnd() && !lIterator.atEnd()) {
      int hEnd = iterationState.getEndOffset();
      int lEnd = lIterator.getEnd();
      if (hEnd >= lEnd) {
        FoldRegion collapsedFolderAt = myFoldingModel.getCollapsedRegionAtOffset(start);
        if (collapsedFolderAt == null) {
          drawStringWithSoftWraps(g, chars, start, lEnd - lIterator.getSeparatorLength(), position, clip, effectColor,
                                  effectType, fontType, currentColor, logicalPosition);
          position.x = 0;
          if (position.y > clip.y + clip.height) {
            break;
          }
          position.y += lineHeight;
          start = lEnd;
        }

//        myBorderEffect.eolReached(g, this);
        lIterator.advance();
      }
      else {
        FoldRegion collapsedFolderAt = iterationState.getCurrentFold();
        if (collapsedFolderAt != null) {
          SoftWrap softWrap = mySoftWrapModel.getSoftWrap(collapsedFolderAt.getStartOffset());
          if (softWrap != null) {
            position.x = drawStringWithSoftWraps(
              g, chars, collapsedFolderAt.getStartOffset(), collapsedFolderAt.getStartOffset(), position, clip, effectColor, effectType,
              fontType, currentColor, logicalPosition
            );
          }
          int foldingXStart = position.x;
          position.x = drawString(
            g, collapsedFolderAt.getPlaceholderText(), position, clip, effectColor, effectType, fontType, currentColor
          );
          //drawStringWithSoftWraps(g, collapsedFolderAt.getPlaceholderText(), position, clip, effectColor, effectType,
          //                        fontType, currentColor, logicalPosition);
          BorderEffect.paintFoldedEffect(g, foldingXStart, position.y, position.x, getLineHeight(), effectColor, effectType);

        }
        else {
          position.x = drawStringWithSoftWraps(g, chars, start, Math.min(hEnd, lEnd - lIterator.getSeparatorLength()) , position, clip,
                                               effectColor, effectType, fontType, currentColor, logicalPosition);
        }

        iterationState.advance();
        attributes = iterationState.getMergedAttributes();

        currentColor = attributes.getForegroundColor();
        if (currentColor == null) {
          currentColor = getForegroundColor();
        }

        effectColor = attributes.getEffectColor();
        effectType = attributes.getEffectType();
        fontType = attributes.getFontType();

        start = iterationState.getStartOffset();
      }
    }

    FoldRegion collapsedFolderAt = iterationState.getCurrentFold();
    if (collapsedFolderAt != null) {
      int foldingXStart = position.x;
      int foldingXEnd =
        drawStringWithSoftWraps(g, collapsedFolderAt.getPlaceholderText(), position, clip, effectColor, effectType,
                                fontType, currentColor, logicalPosition);
      BorderEffect.paintFoldedEffect(g, foldingXStart, position.y, foldingXEnd, getLineHeight(), effectColor, effectType);
//      myBorderEffect.collapsedFolderReached(g, this);
    }

    flushCachedChars(g);
  }

  private void paintPlaceholderText(Graphics g, Rectangle clip) {
    CharSequence hintText = myPlaceholderText;
    if (myDocument.getTextLength() > 0 || hintText == null || hintText.length() == 0) {
      return;
    }

    if (KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() == myEditorComponent) {
      // There is a possible case that placeholder text was painted and the editor gets focus now. We want to over-paint previuosly
      // used placeholder text then.
      myLastBackgroundColor = getBackgroundColor();
      myLastBackgroundPosition = new Point(0, 0);
      myLastBackgroundWidth = myLastPaintedPlaceholderWidth;
      flushBackground(g, clip);
    }
    else {
      myLastPaintedPlaceholderWidth = drawString(
        g, CharArrayUtil.fromSequence(hintText), 0, hintText.length(), new Point(0, 0), clip, null, null, Font.PLAIN,
        myFoldingModel.getPlaceholderAttributes().getForegroundColor()
      );
      flushCachedChars(g);
    }
  }

  public boolean isPaintSelection() {
    return myPaintSelection || !isOneLineMode() || IJSwingUtilities.hasFocus(getContentComponent());
  }

  public void setPaintSelection(boolean paintSelection) {
    myPaintSelection = paintSelection;
  }

  private class CachedFontContent {
    final char[][] data = new char[CACHED_CHARS_BUFFER_SIZE][];
    final int[] starts = new int[CACHED_CHARS_BUFFER_SIZE];
    final int[] ends = new int[CACHED_CHARS_BUFFER_SIZE];
    final int[] x = new int[CACHED_CHARS_BUFFER_SIZE];
    final int[] y = new int[CACHED_CHARS_BUFFER_SIZE];
    final Color[] color = new Color[CACHED_CHARS_BUFFER_SIZE];

    int myCount = 0;
    final FontInfo myFontType;

    private char[] myLastData;

    private CachedFontContent(FontInfo fontInfo) {
      myFontType = fontInfo;
    }

    private void flushContent(Graphics g) {
      if (myCount != 0) {
        if (myCurrentFontType != myFontType) {
          myCurrentFontType = myFontType;
          g.setFont(myFontType.getFont());
        }
        Color currentColor = null;
        for (int i = 0; i < myCount; i++) {
          if (!Comparing.equal(color[i], currentColor)) {
            currentColor = color[i];
            g.setColor(currentColor != null ? currentColor : Color.black);
          }

          drawChars(g, data[i], starts[i], ends[i], x[i], y[i]);
          color[i] = null;
          data[i] = null;
        }

        myCount = 0;
        myLastData = null;
      }
    }

    private void addContent(Graphics g, char[] _data, int _start, int _end, int _x, int _y, @Nullable Color _color) {
      final int count = myCount;
      if (count > 0) {
        final int lastCount = count - 1;
        final Color lastColor = color[lastCount];
        if (_data == myLastData && _start == ends[lastCount] && (_color == null || lastColor == null || _color.equals(lastColor))
          && _y == y[lastCount] /* there is a possible case that vertical position is adjusted because of soft wrap */)
        {
          ends[lastCount] = _end;
          if (lastColor == null) color[lastCount] = _color;
          return;
        }
      }

      myLastData = _data;
      data[count] = _data;
      x[count] = _x;
      y[count] = _y;
      starts[count] = _start;
      ends[count] = _end;
      color[count] = _color;

      myCount++;
      if (count >= CACHED_CHARS_BUFFER_SIZE - 1) {
        flushContent(g);
      }
    }
  }

  private void flushCachedChars(Graphics g) {
    for (CachedFontContent cache : myFontCache) {
      cache.flushContent(g);
    }
    myLastCache = null;
  }

  private void paintCaretCursor(Graphics g) {
    // There is a possible case that visual caret position is changed because of newly added or removed soft wraps.
    // We check if that's the case and ask caret model to recalculate visual position if necessary.

    myCaretCursor.paint(g);
  }

  private void paintLineMarkersSeparators(final Graphics g,
                                          final Rectangle clip,
                                          @NotNull MarkupModelEx markupModel,
                                          int clipStartOffset,
                                          int clipEndOffset) {
    markupModel.processRangeHighlightersOverlappingWith(clipStartOffset, clipEndOffset, new Processor<RangeHighlighterEx>() {
      @Override
      public boolean process(RangeHighlighterEx lineMarker) {
        paintLineMarkerSeparator(lineMarker, clip, g);
        return true;
      }
    });
  }

  private void paintLineMarkerSeparator(RangeHighlighter marker, Rectangle clip, Graphics g) {
    Color separatorColor = marker.getLineSeparatorColor();
    if (separatorColor == null) {
      return;
    }
    int line = marker.getLineSeparatorPlacement() == SeparatorPlacement.TOP ? marker.getDocument()
      .getLineNumber(marker.getStartOffset()) : marker.getDocument().getLineNumber(marker.getEndOffset());
    if (line < 0 || line >= myDocument.getLineCount()) {
      return;
    }

    // There is a possible case that particular logical line occupies more than one visual line (because of soft wraps processing),
    // hence, we need to consider that during calculating 'y' position for the last visual line used for the target logical
    // line representation.
    int y;
    SeparatorPlacement placement = marker.getLineSeparatorPlacement();
    if (placement == SeparatorPlacement.TOP) {
      y = visibleLineToY(logicalToVisualLine(line));
    }
    else {
      if (line + 1 >= myDocument.getLineCount()) {
        y = visibleLineToY(offsetToVisualLine(myDocument.getTextLength()));
      }
      else {
        y = logicalLineToY(line + 1);
      }
    }

    if (y < clip.y || y > clip.y + clip.height) return;

    int endShift = clip.x + clip.width;
    g.setColor(separatorColor);

    if (mySettings.isRightMarginShown() && myScheme.getColor(EditorColors.RIGHT_MARGIN_COLOR) != null) {
      endShift = Math.min(endShift, mySettings.getRightMargin(myProject) * EditorUtil.getSpaceWidth(Font.PLAIN, this));
    }

    final LineSeparatorRenderer lineSeparatorRenderer = marker.getLineSeparatorRenderer();
    if (lineSeparatorRenderer != null) {
      lineSeparatorRenderer.drawLine(g, 0, endShift, y - 1);
    }
    else {
      UIUtil.drawLine(g, 0, y - 1, endShift, y - 1);
    }
  }

  private int drawStringWithSoftWraps(Graphics g,
                                      final String text,
                                      Point position,
                                      Rectangle clip,
                                      Color effectColor,
                                      EffectType effectType,
                                      int fontType,
                                      Color fontColor,
                                      Ref<LogicalPosition> startDrawingLogicalPosition)
  {
    return drawStringWithSoftWraps(g, text.toCharArray(), 0, text.length(), position, clip, effectColor, effectType,
                                   fontType, fontColor, startDrawingLogicalPosition);
  }

  private int drawStringWithSoftWraps(Graphics g,
                                      final char[] text,
                                      final int start,
                                      final int end,
                                      Point position,
                                      Rectangle clip,
                                      Color effectColor,
                                      EffectType effectType,
                                      int fontType,
                                      Color fontColor,
                                      Ref<LogicalPosition> startDrawingLogicalPosition) {
    int startToUse = start;

    // There is a possible case that starting logical line is split by soft-wraps and it's part after the split should be drawn.
    // We need to skip necessary number of visual lines then.
    int softWrapLinesToSkip = 0;
    if (startDrawingLogicalPosition.get() != null) {
      softWrapLinesToSkip = startDrawingLogicalPosition.get().softWrapLinesOnCurrentLogicalLine;
    }
    SoftWrap lastSkippedSoftWrap = null;
    if (softWrapLinesToSkip > 0) {
      List<? extends SoftWrap> softWraps = getSoftWrapModel().getSoftWrapsForLine(startDrawingLogicalPosition.get().line);
      for (SoftWrap softWrap : softWraps) {
        softWrapLinesToSkip--; // Assuming that soft wrap has a single line feed all the time
        if (softWrapLinesToSkip <= 0) {
          lastSkippedSoftWrap = softWrap;
          startToUse = softWrap.getStart();
          break;
        }
      }

    }
    startToUse = Math.max(startToUse, start);

    if (startToUse >= end && getSoftWrapModel().getSoftWrap(startToUse) == null) {
      return position.x;
    }
    startDrawingLogicalPosition.set(null);

    // Given 'end' offset is exclusive though SoftWrapModel.getSoftWrapsForRange() uses inclusive end offset.
    // Hence, we decrement it if necessary. Please note that we don't do that if start is equal to end. That is the case,
    // for example, for soft-wrapped collapsed fold region - we need to draw soft wrap before it.
    int softWrapRetrievalEndOffset = end;
    if (startToUse < end) {
      softWrapRetrievalEndOffset--;
    }
    
    outer:
    for (SoftWrap softWrap : getSoftWrapModel().getSoftWrapsForRange(startToUse, softWrapRetrievalEndOffset)) {
      char[] softWrapChars = softWrap.getChars();

      if (softWrap.equals(lastSkippedSoftWrap)) {
        // If we are here that means that we are located on soft wrap-introduced visual line just after soft wrap. Hence, we need
        // to draw soft wrap indent if any and 'after soft wrap' sign.
        int i = CharArrayUtil.lastIndexOf(softWrapChars, '\n', 0, softWrapChars.length);
        if (i < softWrapChars.length - 1) {
          position.x = 0; // Soft wrap starts new visual line
          position.x = drawString(
            g, softWrapChars, i + 1, softWrapChars.length, position, clip, null, null, fontType, fontColor
          );
        }
        position.x += mySoftWrapModel.paint(g, SoftWrapDrawingType.AFTER_SOFT_WRAP, position.x, position.y, getLineHeight());
        myForceRefreshFont = true;
        continue;
      }

      // Draw token text before the wrap.
      if (softWrap.getStart() > startToUse) {
        position.x = drawString(
          g, text, startToUse, softWrap.getStart(), position, clip, null, null, fontType, fontColor
        );
      }

      startToUse = softWrap.getStart();

      // We don't draw every soft wrap symbol one-by-one but whole visual line. Current variable holds index that points
      // to the first soft wrap symbol that is not drawn yet.
      int softWrapSegmentStartIndex = 0;
      for (int i = 0; i < softWrapChars.length; i++) {
        // Delay soft wraps symbols drawing until EOL is found.
        if (softWrapChars[i] != '\n') {
          continue;
        }

        // Draw soft wrap symbols on current visual line if any.
        if (i - softWrapSegmentStartIndex > 0) {
          drawString(
            g, softWrapChars, softWrapSegmentStartIndex, i, position, clip, null, null, fontType, fontColor
          );
        }
        mySoftWrapModel.paint(g, SoftWrapDrawingType.BEFORE_SOFT_WRAP_LINE_FEED, position.x, position.y, getLineHeight());
        myForceRefreshFont = true;

        // Reset 'x' coordinate because of new line start.
        position.x = 0;

        // Stop the processing if we drew the whole clip.
        if (position.y > clip.y + clip.height) {
          break outer;
        }
        position.y += getLineHeight();
        softWrapSegmentStartIndex = i + 1;
      }

      // Draw remaining soft wrap symbols from its last line if any.
      if (softWrapSegmentStartIndex < softWrapChars.length) {
        position.x += drawString(
          g, softWrapChars, softWrapSegmentStartIndex, softWrapChars.length, position, clip, null, null, fontType, fontColor
        );
      }
      position.x += mySoftWrapModel.paint(g, SoftWrapDrawingType.AFTER_SOFT_WRAP, position.x, position.y, getLineHeight());
      myForceRefreshFont = true;
    }
    return position.x = drawString(g, text, startToUse, end, position, clip, effectColor, effectType, fontType, fontColor);
  }

  private int drawString(Graphics g,
                         final char[] text,
                         int start,
                         int end,
                         Point position,
                         Rectangle clip,
                         @Nullable Color effectColor,
                         @Nullable EffectType effectType,
                         int fontType,
                         Color fontColor) {
    if (start >= end) return position.x;

    boolean isInClip = getLineHeight() + position.y >= clip.y && position.y <= clip.y + clip.height;

    if (!isInClip) return position.x;

    int y = getLineHeight() - getDescent() + position.y;
    int x = position.x;
    return drawTabbedString(g, text, start, end, x, y, effectColor, effectType, fontType, fontColor, clip);
  }

  private int drawString(Graphics g,
                         String text,
                         Point position,
                         Rectangle clip,
                         Color effectColor,
                         EffectType effectType,
                         int fontType,
                         Color fontColor) {
    boolean isInClip = getLineHeight() + position.y >= clip.y && position.y <= clip.y + clip.height;

    if (!isInClip) return position.x;

    int y = getLineHeight() - getDescent() + position.y;
    int x = position.x;

    return drawTabbedString(g, text.toCharArray(), 0, text.length(), x, y, effectColor, effectType, fontType, fontColor, clip);
  }

  private int drawTabbedString(Graphics g,
                               char[] text,
                               int start,
                               int end,
                               int x,
                               int y,
                               Color effectColor,
                               EffectType effectType,
                               int fontType,
                               Color fontColor,
                               final Rectangle clip) {
    int xStart = x;

    for (int i = start; i < end; i++) {
      if (text[i] != '\t') continue;

      x = drawTablessString(text, start, i, g, x, y, fontType, fontColor, clip);

      int x1 = EditorUtil.nextTabStop(x, this);
      drawTabPlacer(g, y, x, x1);
      x = x1;
      start = i + 1;
    }

    x = drawTablessString(text, start, end, g, x, y, fontType, fontColor, clip);

    if (effectColor != null) {
      final Color savedColor = g.getColor();

//      myBorderEffect.flushIfCantProlong(g, this, effectType, effectColor);
      int xEnd = x;
      if (xStart < clip.x && xEnd < clip.x || xStart > clip.x + clip.width && xEnd > clip.x + clip.width) {
        return x;
      }

      if (xEnd > clip.x + clip.width) {
        xEnd = clip.x + clip.width;
      }
      if (xStart < clip.x) {
        xStart = clip.x;
      }

      if (effectType == EffectType.LINE_UNDERSCORE) {
        g.setColor(effectColor);
        UIUtil.drawLine(g, xStart, y + 1, xEnd, y + 1);
        g.setColor(savedColor);
      }
      else if (effectType == EffectType.BOLD_LINE_UNDERSCORE) {
        g.setColor(effectColor);
        UIUtil.drawLine(g, xStart, y, xEnd, y);
        UIUtil.drawLine(g, xStart, y + 1, xEnd, y + 1);
        g.setColor(savedColor);
      }
      else if (effectType == EffectType.STRIKEOUT) {
        g.setColor(effectColor);
        int y1 = y - getCharHeight() / 2;
        UIUtil.drawLine(g, xStart, y1, xEnd, y1);
        g.setColor(savedColor);
      }
      else if (effectType == EffectType.WAVE_UNDERSCORE) {
        g.setColor(effectColor);
        drawWave(g, xStart, xEnd, y + 1);
        g.setColor(savedColor);
      }
      else if (effectType == EffectType.BOLD_DOTTED_LINE) {
        final Color bgColor = getBackgroundColor();
        final int dottedAt = SystemInfo.isMac ? y : y + 1;
        UIUtil.drawBoldDottedLine((Graphics2D)g, xStart, xEnd, dottedAt, bgColor, effectColor, false);
      }
    }

    return x;
  }

  private int drawTablessString(final char[] text,
                                int start,
                                final int end,
                                final Graphics g,
                                int x,
                                final int y,
                                final int fontType,
                                final Color fontColor,
                                final Rectangle clip) {
    int endX = x;
    if (start < end) {
      FontInfo font = EditorUtil.fontForChar(text[start], fontType, this);
      for (int j = start; j < end; j++) {
        final char c = text[j];
        FontInfo newFont = EditorUtil.fontForChar(c, fontType, this);
        if (font != newFont || endX > clip.x + clip.width) {
          if (!(x < clip.x && endX < clip.x || x > clip.x + clip.width && endX > clip.x + clip.width)) {
            drawCharsCached(g, text, start, j, x, y, fontType, fontColor);
          }
          start = j;
          x = endX;
          font = newFont;
        }
        if (x < clip.x && endX < clip.x) {
          start = j;
          x = endX;
          font = newFont;
        }
        else if (x > clip.x + clip.width) {
          return endX;
        }
        endX += font.charWidth(c, myEditorComponent);
      }

      if (!(x < clip.x && endX < clip.x || x > clip.x + clip.width && endX > clip.x + clip.width)) {
        drawCharsCached(g, text, start, end, x, y, fontType, fontColor);
      }
    }

    return endX;
  }

  private void drawTabPlacer(Graphics g, int y, int start, int stop) {
    if (mySettings.isWhitespacesShown()) {
      myTabPainter.paint(g, y, start, stop);
    }
  }

  private void drawCharsCached(Graphics g, char[] data, int start, int end, int x, int y, int fontType, Color color) {
    if (!myForceRefreshFont && mySpacesHaveSameWidth && myLastCache != null && spacesOnly(data, start, end)) {
      myLastCache.addContent(g, data, start, end, x, y, null);
    }
    else {
      myForceRefreshFont = false;
      FontInfo fnt = EditorUtil.fontForChar(data[start], fontType, this);
      drawCharsCached(g, data, start, end, x, y, fnt, color);
    }
  }

  private void drawCharsCached(Graphics g, char[] data, int start, int end, int x, int y, FontInfo fnt, Color color) {
    CachedFontContent cache = null;
    for (CachedFontContent fontCache : myFontCache) {
      if (fontCache.myFontType == fnt) {
        cache = fontCache;
        break;
      }
    }
    if (cache == null) {
      cache = new CachedFontContent(fnt);
      myFontCache.add(cache);
    }

    myLastCache = cache;
    cache.addContent(g, data, start, end, x, y, color);
  }

  private static boolean spacesOnly(char[] chars, int start, int end) {
    for (int i = start; i < end; i++) {
      if (chars[i] != ' ') return false;
    }
    return true;
  }

  private void drawChars(Graphics g, char[] data, int start, int end, int x, int y) {
    g.drawChars(data, start, end - start, x, y);

    if (mySettings.isWhitespacesShown()) {
      Color oldColor = g.getColor();
      g.setColor(myScheme.getColor(EditorColors.WHITESPACES_COLOR));
      final FontMetrics metrics = g.getFontMetrics();
      int halfSpaceWidth = metrics.charWidth(' ') / 2;
      for (int i = start; i < end; i++) {
        if (data[i] == ' ') {
          g.fillRect(x + halfSpaceWidth, y, 1, 1);
        }
        x += metrics.charWidth(data[i]);
      }
      g.setColor(oldColor);
    }
  }

  private static final int WAVE_HEIGHT = 2;
  private static final int WAVE_SEGMENT_LENGTH = 4;

  private static void drawWave(Graphics g, int xStart, int xEnd, int y) {
    int startSegment = xStart / WAVE_SEGMENT_LENGTH;
    int endSegment = xEnd / WAVE_SEGMENT_LENGTH;
    for (int i = startSegment; i < endSegment; i++) {
      drawWaveSegment(g, WAVE_SEGMENT_LENGTH * i, y);
    }

    int x = WAVE_SEGMENT_LENGTH * endSegment;
    UIUtil.drawLine(g, x, y + WAVE_HEIGHT, x + WAVE_SEGMENT_LENGTH / 2, y);
  }

  private static void drawWaveSegment(Graphics g, int x, int y) {
    UIUtil.drawLine(g, x, y + WAVE_HEIGHT, x + WAVE_SEGMENT_LENGTH / 2, y);
    UIUtil.drawLine(g, x + WAVE_SEGMENT_LENGTH / 2, y, x + WAVE_SEGMENT_LENGTH, y + WAVE_HEIGHT);
  }

  private int getTextSegmentWidth(CharSequence text, int xStart, int fontType, Rectangle clip) {
    int x = xStart;

    final int textLength = text.length();
    for (int i = 0; i < textLength && xStart < clip.x + clip.width; i++) {
      if (text.charAt(i) == '\t') {
        x = EditorUtil.nextTabStop(x, this);
      }
      else {
        x += EditorUtil.charWidth(text.charAt(i), fontType, this);
      }
      if (x > clip.x + clip.width) {
        break;
      }
    }
    return x - xStart;
  }

  public int getLineHeight() {
    assertReadAccess();
    int lineHeight = myLineHeight;
    if (lineHeight == -1) {
      FontMetrics fontMetrics = myEditorComponent.getFontMetrics(myScheme.getFont(EditorFontType.PLAIN));
      lineHeight = (int)(fontMetrics.getHeight() * (isOneLineMode() ? 1 : myScheme.getLineSpacing()));
      if (lineHeight == 0) {
        lineHeight = fontMetrics.getHeight();
        if (lineHeight == 0) {
          lineHeight = 12;
        }
      }
      myLineHeight = lineHeight;
    }
    return lineHeight;
  }

  int getDescent() {
    if (myDescent != -1) {
      return myDescent;
    }
    FontMetrics fontMetrics = myEditorComponent.getFontMetrics(myScheme.getFont(EditorFontType.PLAIN));
    myDescent = fontMetrics.getDescent();
    return myDescent;
  }

  FontMetrics getFontMetrics(int fontType) {
    if (myPlainFontMetrics == null) {
      assertIsDispatchThread();
      myPlainFontMetrics = myEditorComponent.getFontMetrics(myScheme.getFont(EditorFontType.PLAIN));
      myBoldFontMetrics = myEditorComponent.getFontMetrics(myScheme.getFont(EditorFontType.BOLD));
      myItalicFontMetrics = myEditorComponent.getFontMetrics(myScheme.getFont(EditorFontType.ITALIC));
      myBoldItalicFontMetrics = myEditorComponent.getFontMetrics(myScheme.getFont(EditorFontType.BOLD_ITALIC));
    }

    if (fontType == Font.PLAIN) return myPlainFontMetrics;
    if (fontType == Font.BOLD) return myBoldFontMetrics;
    if (fontType == Font.ITALIC) return myItalicFontMetrics;
    if (fontType == Font.BOLD + Font.ITALIC) return myBoldItalicFontMetrics;

    LOG.error("Unknown font type: " + fontType);

    return myPlainFontMetrics;
  }

  private int getCharHeight() {
    if (myCharHeight == -1) {
      assertIsDispatchThread();
      FontMetrics fontMetrics = myEditorComponent.getFontMetrics(myScheme.getFont(EditorFontType.PLAIN));
      myCharHeight = fontMetrics.charWidth('a');
    }
    return myCharHeight;
  }

  public int getPreferredHeight() {
    if (ourIsUnitTestMode && getUserData(DO_DOCUMENT_UPDATE_TEST) == null) {
      return 1;
    }

    return getHeightWithoutCaret();
  }

  public Dimension getPreferredSize() {
    if (ourIsUnitTestMode && getUserData(DO_DOCUMENT_UPDATE_TEST) == null) {
      return new Dimension(1, 1);
    }

    final Dimension draft = getSizeWithoutCaret();
    final int additionalSpace = mySettings.getAdditionalColumnsCount() * EditorUtil.getSpaceWidth(Font.PLAIN, this);

    if (!myDocument.isInBulkUpdate() && getCaretModel().isUpToDate()) {
      int caretX = visualPositionToXY(getCaretModel().getVisualPosition()).x;
      draft.width = Math.max(caretX, draft.width) + additionalSpace;
    }
    else {
      draft.width += additionalSpace;
    }
    return draft;
  }

  private Dimension getSizeWithoutCaret() {
    Dimension size = mySizeContainer.getContentSize();
    if (isOneLineMode()) return new Dimension(size.width, getLineHeight());
    if (mySettings.isAdditionalPageAtBottom()) {
      int lineHeight = getLineHeight();
      int visibleAreaHeight = getScrollingModel().getVisibleArea().height;
      int virtualPageHeight;
      // There is a possible case that user with 'show additional page at bottom' scrolls to that virtual page; switched to another
      // editor (another tab); and then returns to the previously used editor (the one scrolled to virtual page). We want to preserve
      // correct view size then because viewport position is set to the end of the original text otherwise.
      if (visibleAreaHeight <= 0 && myVirtualPageHeight > 0) {
        virtualPageHeight = myVirtualPageHeight;
      }
      else {
        myVirtualPageHeight = virtualPageHeight = Math.max(visibleAreaHeight - 2 * lineHeight, lineHeight);
      }
      
      if (myVirtualPageHeight > 0) {
        return new Dimension(size.width, size.height + virtualPageHeight);
      }
      else {
        return size;
      }
    }

    return getContentSize();
  }

  private int getHeightWithoutCaret() {
    if (isOneLineMode()) return getLineHeight();
    int size = mySizeContainer.getContentHeight();
    if (mySettings.isAdditionalPageAtBottom()) {
      int lineHeight = getLineHeight();
      return size + Math.max(getScrollingModel().getVisibleArea().height - 2 * lineHeight, lineHeight);
    }

    return size + mySettings.getAdditionalLinesCount() * getLineHeight();
  }

  public Dimension getContentSize() {
    Dimension size = mySizeContainer.getContentSize();
    return new Dimension(size.width, size.height + mySettings.getAdditionalLinesCount() * getLineHeight());
  }

  public JScrollPane getScrollPane() {
    return myScrollPane;
  }

  public void setBorder(Border border) {
    myScrollPane.setBorder(border);
  }

  public int logicalPositionToOffset(@NotNull LogicalPosition pos) {
    assertIsDispatchThread();
    if (myDocument.getLineCount() == 0) return 0;

    if (pos.line < 0) throw new IndexOutOfBoundsException("Wrong line: " + pos.line);
    if (pos.column < 0) throw new IndexOutOfBoundsException("Wrong column:" + pos.column);

    if (pos.line >= myDocument.getLineCount()) {
      return myDocument.getTextLength();
    }

    int start = myDocument.getLineStartOffset(pos.line);
    if (pos.column == 0) return start;
    int end = myDocument.getLineEndOffset(pos.line);

    CharSequence text = myDocument.getCharsNoThreadCheck();

    return EditorUtil.calcOffset(this, text, start, end, pos.column, EditorUtil.getTabSize(this));
  }

  public void setLastColumnNumber(int val) {
    assertIsDispatchThread();
    myLastColumnNumber = val;
  }

  public int getLastColumnNumber() {
    assertReadAccess();
    return myLastColumnNumber;
  }

  /**
   * @return    information about total number of lines that can be viewed by user. I.e. this is a number of all document
   *            lines (considering that single logical document line may be represented on multiple visual lines because of
   *            soft wraps appliance) minus number of folded lines
   */
  int getVisibleLineCount() {
    return getVisibleLogicalLinesCount() + getSoftWrapModel().getSoftWrapsIntroducedLinesNumber();
  }

  /**
   * @return    number of visible logical lines. Generally, that is a total logical lines number minus number of folded lines
   */
  private int getVisibleLogicalLinesCount() {
    return getDocument().getLineCount() - myFoldingModel.getFoldedLinesCountBefore(getDocument().getTextLength() + 1);
  }

  @Override
  @NotNull
  public VisualPosition logicalToVisualPosition(@NotNull LogicalPosition logicalPos) {
    return logicalToVisualPosition(logicalPos, true);
  }

  @Override
  @NotNull
  public VisualPosition logicalToVisualPosition(@NotNull LogicalPosition logicalPos, boolean softWrapAware) {
    assertReadAccess();
    if (!myFoldingModel.isFoldingEnabled() && !mySoftWrapModel.isSoftWrappingEnabled()) {
      return new VisualPosition(logicalPos.line, logicalPos.column);
    }

    int offset = logicalPositionToOffset(logicalPos);

    FoldRegion outermostCollapsed = myFoldingModel.getCollapsedRegionAtOffset(offset);
    if (outermostCollapsed != null && offset > outermostCollapsed.getStartOffset()) {
      if (offset < getDocument().getTextLength()) {
        offset = outermostCollapsed.getStartOffset();
        LogicalPosition foldStart = offsetToLogicalPosition(offset);
        return logicalToVisualPosition(foldStart);
      }
      else {
        offset = outermostCollapsed.getEndOffset() + 3;  // WTF?
      }
    }

    int line = logicalPos.line;
    int column = logicalPos.column;

    line -= myFoldingModel.getFoldedLinesCountBefore(offset);

    FoldRegion[] topLevel = myFoldingModel.fetchTopLevel();
    LogicalPosition anchorFoldingPosition = logicalPos;
    for (int idx = myFoldingModel.getLastTopLevelIndexBefore(offset); idx >= 0; idx--) {
      FoldRegion region = topLevel[idx];
      if (region.isValid()) {
        if (region.getDocument().getLineNumber(region.getEndOffset()) == anchorFoldingPosition.line && region.getEndOffset() <= offset) {
          LogicalPosition foldStart = offsetToLogicalPosition(region.getStartOffset());
          LogicalPosition foldEnd = offsetToLogicalPosition(region.getEndOffset());
          column += foldStart.column + region.getPlaceholderText().length() - foldEnd.column;
          offset = region.getStartOffset();
          anchorFoldingPosition = foldStart;
        }
        else {
          break;
        }
      }
    }

    LOG.assertTrue(line >= 0);

    VisualPosition softWrapUnawarePosition = new VisualPosition(line, Math.max(0, column));
    if (softWrapAware) {
      return mySoftWrapModel.adjustVisualPosition(logicalPos, softWrapUnawarePosition);
    }
    return softWrapUnawarePosition;
  }

  @Nullable
  private FoldRegion getLastCollapsedBeforePosition(VisualPosition visualPos) {
    FoldRegion[] topLevelCollapsed = myFoldingModel.fetchTopLevel();

    if (topLevelCollapsed == null) return null;

    int start = 0;
    int end = topLevelCollapsed.length - 1;
    int i = 0;

    while (start <= end) {
      i = (start + end) / 2;
      FoldRegion region = topLevelCollapsed[i];
      int regionVisualLine = offsetToVisualLine(region.getEndOffset() - 1);
      if (regionVisualLine < visualPos.line) {
        start = i + 1;
      }
      else {
        if (regionVisualLine > visualPos.line) {
          end = i - 1;
        }
        else {
          VisualPosition visFoldEnd = offsetToVisualPosition(region.getEndOffset() - 1);
          if (visFoldEnd.column < visualPos.column) {
            start = i + 1;
          }
          else {
            if (visFoldEnd.column > visualPos.column) {
              end = i - 1;
            }
            else {
              i--;
              break;
            }
          }
        }
      }
    }

    while (i >= 0 && i < topLevelCollapsed.length) {
      if (topLevelCollapsed[i].isValid()) break;
      i--;
    }

    if (i >= 0 && i < topLevelCollapsed.length) {
      FoldRegion region = topLevelCollapsed[i];
      VisualPosition visFoldEnd = offsetToVisualPosition(region.getEndOffset() - 1);
      if (visFoldEnd.line > visualPos.line || visFoldEnd.line == visualPos.line && visFoldEnd.column > visualPos.column) {
        i--;
        if (i >= 0) {
          return topLevelCollapsed[i];
        }
        return null;
      }
      return region;
    }

    return null;
  }

  @NotNull
  public LogicalPosition visualToLogicalPosition(@NotNull VisualPosition visiblePos) {
    return visualToLogicalPosition(visiblePos, true);
  }

  @NotNull
  public LogicalPosition visualToLogicalPosition(@NotNull VisualPosition visiblePos, boolean softWrapAware) {
    assertReadAccess();
    if (softWrapAware) {
      return mySoftWrapModel.visualToLogicalPosition(visiblePos);
    }
    if (!myFoldingModel.isFoldingEnabled()) return new LogicalPosition(visiblePos.line, visiblePos.column);

    int line = visiblePos.line;
    int column = visiblePos.column;

    FoldRegion lastCollapsedBefore = getLastCollapsedBeforePosition(visiblePos);

    if (lastCollapsedBefore != null) {
      int logFoldEndLine = offsetToLogicalLine(lastCollapsedBefore.getEndOffset(), false);
      int visFoldEndLine = logicalToVisualLine(logFoldEndLine);

      line = logFoldEndLine + visiblePos.line - visFoldEndLine;
      if (visFoldEndLine == visiblePos.line) {
        LogicalPosition logFoldEnd = offsetToLogicalPosition(lastCollapsedBefore.getEndOffset(), false);
        VisualPosition visFoldEnd = logicalToVisualPosition(logFoldEnd, false);
        if (visiblePos.column >= visFoldEnd.column) {
          column = logFoldEnd.column + visiblePos.column - visFoldEnd.column;
        }
        else {
          return offsetToLogicalPosition(lastCollapsedBefore.getStartOffset(), false);
        }
      }
    }

    if (column < 0) column = 0;

    return new LogicalPosition(line, column);
  }

  int offsetToLogicalLine(int offset) {
    return offsetToLogicalLine(offset, true);
  }

  private int offsetToLogicalLine(int offset, boolean softWrapAware) {
    int textLength = myDocument.getTextLength();
    if (textLength == 0) return 0;

    if (offset > textLength || offset < 0) {
      throw new IndexOutOfBoundsException("Wrong offset: " + offset + " textLength: " + textLength);
    }

    int lineIndex = myDocument.getLineNumber(offset);
    LOG.assertTrue(lineIndex >= 0 && lineIndex < myDocument.getLineCount());

    if (softWrapAware && getSoftWrapModel().isSoftWrappingEnabled()) {
      int column = calcColumnNumber(offset, lineIndex, false);
      return mySoftWrapModel.adjustLogicalPosition(new LogicalPosition(lineIndex, column), offset).line;
    }
    else {
      return lineIndex;
    }
  }

  public int calcColumnNumber(int offset, int lineIndex) {
    return calcColumnNumber(offset, lineIndex, true);
  }

  public int calcColumnNumber(int offset, int lineIndex, boolean softWrapAware) {
    if (myDocument.getTextLength() == 0) return 0;

    int start = myDocument.getLineStartOffset(lineIndex);
    if (start == offset) return 0;
    CharSequence text = myDocument.getCharsSequence();
    int column = EditorUtil.calcColumnNumber(this, text, start, offset, EditorUtil.getTabSize(this));

    if (softWrapAware) {
      int line = offsetToLogicalLine(offset, false);
      return mySoftWrapModel.adjustLogicalPosition(new LogicalPosition(line, column), offset).column;
    }
    else {
      return column;
    }
  }

  private void moveCaretToScreenPos(int x, int y) {
    if (x < 0) {
      x = 0;
    }

    LogicalPosition pos = xyToLogicalPosition(new Point(x, y));

    int column = pos.column;
    int line = pos.line;
    int softWrapLinesBeforeTargetLogicalLine = pos.softWrapLinesBeforeCurrentLogicalLine;
    int softWrapLinesOnTargetLogicalLine = pos.softWrapLinesOnCurrentLogicalLine;
    int softWrapColumns = pos.softWrapColumnDiff;

    if (line < 0) {
      line = 0;
      column = 0;
      softWrapLinesBeforeTargetLogicalLine = 0;
      softWrapLinesOnTargetLogicalLine = 0;
      softWrapColumns = 0;
    }

    final int totalLines = myDocument.getLineCount();
    if (totalLines <= 0) {
      getCaretModel().moveToOffset(0);
      return;
    }

    if (line >= totalLines && totalLines > 0) {
      int visibleLineCount = getVisibleLineCount();
      int newY = visibleLineCount > 0 ? visibleLineToY(visibleLineCount - 1) : 0;
      if (newY > 0 && newY == y) {
        newY = visibleLineToY(getVisibleLogicalLinesCount());
      }
      moveCaretToScreenPos(x, newY);
      return;
    }

    if (!mySettings.isVirtualSpace() && !mySelectionModel.hasBlockSelection()) {
      int lineEndOffset = myDocument.getLineEndOffset(line);
      int lineEndColumn = calcColumnNumber(lineEndOffset, line);
      if (column > lineEndColumn) {
        column = lineEndColumn;
        if (softWrapColumns != 0) {
          softWrapColumns -= column - lineEndColumn;
        }
      }
    }

    if (!mySettings.isCaretInsideTabs()) {
      int offset = logicalPositionToOffset(new LogicalPosition(line, column));
      CharSequence text = myDocument.getCharsSequence();
      if (offset >= 0 && offset < myDocument.getTextLength()) {
        if (text.charAt(offset) == '\t') {
          column = calcColumnNumber(offset, line);
        }
      }
    }
    LogicalPosition pos1 = new LogicalPosition(
      line, column, softWrapLinesBeforeTargetLogicalLine, softWrapLinesOnTargetLogicalLine, softWrapColumns,
      pos.foldedLines, pos.foldingColumnDiff
    );
    getCaretModel().moveToLogicalPosition(pos1);
  }

  private boolean checkIgnore(MouseEvent e, boolean isFinalCheck) {
    if (!myIgnoreMouseEventsConsecutiveToInitial) {
      myInitialMouseEvent = null;
      return false;
    }

    if (e.getComponent() != myInitialMouseEvent.getComponent() || !e.getPoint().equals(myInitialMouseEvent.getPoint())) {
      myIgnoreMouseEventsConsecutiveToInitial = false;
      myInitialMouseEvent = null;
      return false;
    }

    if (isFinalCheck) {
      myIgnoreMouseEventsConsecutiveToInitial = false;
      myInitialMouseEvent = null;
    }

    e.consume();

    return true;
  }

  private void processMouseReleased(MouseEvent e) {
    if (checkIgnore(e, true)) return;

    if (e.getSource() == myGutterComponent) {
      myGutterComponent.mouseReleased(e);
    }

    if (getMouseEventArea(e) != EditorMouseEventArea.EDITING_AREA || e.getY() < 0 || e.getX() < 0) {
      return;
    }

//    if (myMousePressedInsideSelection) getSelectionModel().removeSelection();
    final FoldRegion region = getFoldingModel().getFoldingPlaceholderAt(e.getPoint());
    if (e.getX() >= 0 && e.getY() >= 0 && region != null && region == myMouseSelectedRegion) {
      getFoldingModel().runBatchFoldingOperation(new Runnable() {
        public void run() {
          myFoldingModel.flushCaretShift();
          region.setExpanded(true);
        }
      });

      // The call below is performed because gutter's height is not updated sometimes, i.e. it sticks to the value that corresponds
      // to the situation when fold region is collapsed. That causes bottom of the gutter to not be repainted and that looks really ugly.
      getGutterComponentEx().invalidate();
    }

    // The general idea is to check if the user performed 'caret position change click' (left click most of the time) inside selection
    // and, in the case of the positive answer, clear selection. Please note that there is a possible case that mouse click
    // is performed inside selection but it triggers context menu. We don't want to drop the selection then.
    if (myMousePressedEvent != null && myMousePressedEvent.getClickCount() == 1 && myMousePressedInsideSelection
        && !myMousePressedEvent.isShiftDown() && !myMousePressedEvent.isPopupTrigger())
    {
      getSelectionModel().removeSelection();
    }
  }

  public DataContext getDataContext() {
    return getProjectAwareDataContext(DataManager.getInstance().getDataContext(getContentComponent()));
  }

  private DataContext getProjectAwareDataContext(final DataContext original) {
    if (PlatformDataKeys.PROJECT.getData(original) == myProject) return original;

    return new DataContext() {
      public Object getData(String dataId) {
        if (PlatformDataKeys.PROJECT.is(dataId)) {
          return myProject;
        }
        return original.getData(dataId);
      }
    };
  }


  public EditorMouseEventArea getMouseEventArea(@NotNull MouseEvent e) {
    if (myGutterComponent != e.getSource()) return EditorMouseEventArea.EDITING_AREA;

    int x = myGutterComponent.convertX(e.getX());

    if (x >= myGutterComponent.getLineNumberAreaOffset() &&
      x < myGutterComponent.getLineNumberAreaOffset() + myGutterComponent.getLineNumberAreaWidth()) {
      return EditorMouseEventArea.LINE_NUMBERS_AREA;
    }

    if (x >= myGutterComponent.getAnnotationsAreaOffset() &&
      x <= myGutterComponent.getAnnotationsAreaOffset() + myGutterComponent.getAnnotationsAreaWidth()) {
      return EditorMouseEventArea.ANNOTATIONS_AREA;
    }

    if (x >= myGutterComponent.getLineMarkerAreaOffset() &&
      x < myGutterComponent.getLineMarkerAreaOffset() + myGutterComponent.getLineMarkerAreaWidth()) {
      return EditorMouseEventArea.LINE_MARKERS_AREA;
    }

    if (x >= myGutterComponent.getFoldingAreaOffset() &&
      x < myGutterComponent.getFoldingAreaOffset() + myGutterComponent.getFoldingAreaWidth()) {
      return EditorMouseEventArea.FOLDING_OUTLINE_AREA;
    }

    return null;
  }

  private void requestFocus() {
    final IdeFocusManager focusManager = IdeFocusManager.getInstance(myProject);
    if (focusManager.getFocusOwner() != myEditorComponent) { //IDEA-64501
      focusManager.requestFocus(myEditorComponent, true);
    }
  }

  private void validateMousePointer(MouseEvent e) {
    if (e.getSource() == myGutterComponent) {
      FoldRegion foldingAtCursor = myGutterComponent.findFoldingAnchorAt(e.getX(), e.getY());
      myGutterComponent.setActiveFoldRegion(foldingAtCursor);
      if (foldingAtCursor != null) {
        myGutterComponent.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      }
      else {
        myGutterComponent.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
      }
    }
    else {
      myGutterComponent.setActiveFoldRegion(null);
      if (getSelectionModel().hasSelection() && (e.getModifiersEx() & (InputEvent.BUTTON1_DOWN_MASK | InputEvent.BUTTON2_DOWN_MASK)) == 0) {
        int offset = logicalPositionToOffset(xyToLogicalPosition(e.getPoint()));
        if (getSelectionModel().getSelectionStart() <= offset && offset < getSelectionModel().getSelectionEnd()) {
          myEditorComponent.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
          return;
        }
      }
      myEditorComponent.setCursor(UIUtil.getTextCursor(getBackgroundColor()));
    }
  }

  private void runMouseDraggedCommand(final MouseEvent e) {
    if (myCommandProcessor == null || myMousePressedEvent != null && myMousePressedEvent.isConsumed()) {
      return;
    }
    myCommandProcessor.executeCommand(myProject, new Runnable() {
      public void run() {
        processMouseDragged(e);
      }
    }, "", MOUSE_DRAGGED_GROUP, UndoConfirmationPolicy.DEFAULT, getDocument());
  }

  private void processMouseDragged(MouseEvent e) {
    if (SwingUtilities.isRightMouseButton(e)) {
      return;
    }
    Rectangle visibleArea = getScrollingModel().getVisibleArea();

    int x = e.getX();

    if (e.getSource() == myGutterComponent) {
      x = 0;
    }

    int dx = 0;
    if (x < visibleArea.x && visibleArea.x > 0) {
      dx = x - visibleArea.x;
    }
    else {
      if (x > visibleArea.x + visibleArea.width) {
        dx = x - visibleArea.x - visibleArea.width;
      }
    }

    int dy = 0;
    int y = e.getY();
    if (y < visibleArea.y && visibleArea.y > 0) {
      dy = y - visibleArea.y;
    }
    else {
      if (y > visibleArea.y + visibleArea.height) {
        dy = y - visibleArea.y - visibleArea.height;
      }
    }
    if (dx == 0 && dy == 0) {
      myScrollingTimer.stop();

      SelectionModel selectionModel = getSelectionModel();
      int oldSelectionStart = selectionModel.getLeadSelectionOffset();
      VisualPosition oldVisLeadSelectionStart = selectionModel.getLeadSelectionPosition();
      int oldCaretOffset = getCaretModel().getOffset();
      LogicalPosition oldLogicalCaret = getCaretModel().getLogicalPosition();
      moveCaretToScreenPos(x, y);
      getScrollingModel().scrollToCaret(ScrollType.RELATIVE);

      int newCaretOffset = getCaretModel().getOffset();
      VisualPosition newVisualCaret = getCaretModel().getVisualPosition();
      int caretShift = newCaretOffset - mySavedSelectionStart;

      if (myMousePressedEvent != null && getMouseEventArea(myMousePressedEvent) != EditorMouseEventArea.EDITING_AREA &&
        getMouseEventArea(myMousePressedEvent) != EditorMouseEventArea.LINE_NUMBERS_AREA) {
        selectionModel.setSelection(oldSelectionStart, newCaretOffset);
      }
      else {
        if (isColumnMode() || e.isAltDown()) {
          final LogicalPosition blockStart = selectionModel.hasBlockSelection() ? selectionModel.getBlockStart() : oldLogicalCaret;
          selectionModel.setBlockSelection(blockStart, getCaretModel().getLogicalPosition());
        }
        else {
          if (getMouseSelectionState() != MOUSE_SELECTION_STATE_NONE) {
            if (caretShift < 0) {
              int newSelection = newCaretOffset;
              if (getMouseSelectionState() == MOUSE_SELECTION_STATE_WORD_SELECTED) {
                newSelection = mySelectionModel.getWordAtCaretStart();
              }
              else {
                if (getMouseSelectionState() == MOUSE_SELECTION_STATE_LINE_SELECTED) {
                  newSelection =
                    logicalPositionToOffset(visualToLogicalPosition(new VisualPosition(getCaretModel().getVisualPosition().line, 0)));
                }
              }
              if (newSelection < 0) newSelection = newCaretOffset;
              selectionModel.setSelection(mySavedSelectionEnd, newSelection);
              getCaretModel().moveToOffset(newSelection);
            }
            else {
              int newSelection = newCaretOffset;
              if (getMouseSelectionState() == MOUSE_SELECTION_STATE_WORD_SELECTED) {
                newSelection = mySelectionModel.getWordAtCaretEnd();
              }
              else {
                if (getMouseSelectionState() == MOUSE_SELECTION_STATE_LINE_SELECTED) {
                  newSelection =
                    logicalPositionToOffset(visualToLogicalPosition(new VisualPosition(getCaretModel().getVisualPosition().line + 1, 0)));
                }
              }
              if (newSelection < 0) newSelection = newCaretOffset;
              selectionModel.setSelection(mySavedSelectionStart, newSelection);
              getCaretModel().moveToOffset(newSelection);
            }
            cancelAutoResetForMouseSelectionState();
            return;
          }

          if (!myMousePressedInsideSelection) {
            if (oldVisLeadSelectionStart != null) {
              selectionModel.setSelection(oldVisLeadSelectionStart, oldSelectionStart, newVisualCaret, newCaretOffset);
            }
            else {
              selectionModel.setSelection(oldSelectionStart, newCaretOffset);
            }
            cancelAutoResetForMouseSelectionState();
          }
          else {
            if (caretShift != 0) {
              if (myMousePressedEvent != null) {
                if (mySettings.isDndEnabled()) {
                  boolean isCopy = UIUtil.isControlKeyDown(e) || isViewer() || !getDocument().isWritable();
                  mySavedCaretOffsetForDNDUndoHack = oldCaretOffset;
                  getContentComponent().getTransferHandler()
                    .exportAsDrag(getContentComponent(), e, isCopy ? TransferHandler.COPY : TransferHandler.MOVE);
                }
                else {
                  selectionModel.removeSelection();
                }
              }
            }
          }
        }
      }
    }
    else {
      myScrollingTimer.start(dx, dy);
    }
  }

  private static class RepaintCursorCommand implements Runnable {
    private long mySleepTime = 500;
    private boolean myIsBlinkCaret = true;
    private EditorImpl myEditor = null;
    private final MyRepaintRunnable myRepaintRunnable;
    private ScheduledFuture<?> mySchedulerHandle;

    private RepaintCursorCommand() {
      myRepaintRunnable = new MyRepaintRunnable();
    }

    private class MyRepaintRunnable implements Runnable {
      public void run() {
        if (myEditor != null) {
          myEditor.myCaretCursor.repaint();
        }
      }
    }

    public void start() {
      if (mySchedulerHandle != null) {
        mySchedulerHandle.cancel(false);
      }
      mySchedulerHandle = JobScheduler.getScheduler().scheduleAtFixedRate(this, mySleepTime, mySleepTime, TimeUnit.MILLISECONDS);
    }

    private void setBlinkPeriod(int blinkPeriod) {
      mySleepTime = blinkPeriod > 10 ? blinkPeriod : 10;
      start();
    }

    private void setBlinkCaret(boolean value) {
      myIsBlinkCaret = value;
    }

    public void run() {
      if (myEditor != null) {
        CaretCursor activeCursor = myEditor.myCaretCursor;

        long time = System.currentTimeMillis();
        time -= activeCursor.myStartTime;

        if (time > mySleepTime) {
          boolean toRepaint = true;
          if (myIsBlinkCaret) {
            activeCursor.myIsShown = !activeCursor.myIsShown;
          }
          else {
            toRepaint = !activeCursor.myIsShown;
            activeCursor.myIsShown = true;
          }

          if (toRepaint) {
            SwingUtilities.invokeLater(myRepaintRunnable);
          }
        }
      }
    }
  }

  void updateCaretCursor() {
    if (!ourIsUnitTestMode && !IJSwingUtilities.hasFocus(getContentComponent())) {
      stopOptimizedScrolling();
    }

    myUpdateCursor = true;
  }

  private void setCursorPosition() {
    VisualPosition caretPosition = getCaretModel().getVisualPosition();
    Point pos1 = visualPositionToXY(caretPosition);
    Point pos2 = visualPositionToXY(new VisualPosition(caretPosition.line, caretPosition.column + 1));
    myCaretCursor.setPosition(pos1, pos2.x - pos1.x);
  }

  public boolean setCaretVisible(boolean b) {
    boolean old = myCaretCursor.isActive();
    if (b) {
      myCaretCursor.activate();
    }
    else {
      myCaretCursor.passivate();
    }
    return old;
  }

  public boolean setCaretEnabled(boolean enabled) {
    boolean old = myCaretCursor.isEnabled();
    myCaretCursor.setEnabled(enabled);
    return old;
  }

  public void addFocusListener(@NotNull FocusChangeListener listener) {
    myFocusListeners.add(listener);
  }

  @Nullable
  public Project getProject() {
    return myProject;
  }

  public boolean isOneLineMode() {
    return myIsOneLineMode;
  }

  public boolean isEmbeddedIntoDialogWrapper() {
    return myEmbeddedIntoDialogWrapper;
  }

  public void setEmbeddedIntoDialogWrapper(boolean b) {
    assertIsDispatchThread();

    myEmbeddedIntoDialogWrapper = b;
    myScrollPane.setFocusable(!b);
    myEditorComponent.setFocusCycleRoot(!b);
    myEditorComponent.setFocusable(b);
  }

  public void setOneLineMode(boolean isOneLineMode) {
    myIsOneLineMode = isOneLineMode;
    getScrollPane().setInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, null);
    reinitSettings();
  }

  public void stopOptimizedScrolling() {
    //myEditorComponent.setOpaque(false);
  }

  private void startOptimizedScrolling() {
    //myEditorComponent.setOpaque(true);
  }

  private class CaretCursor {
    private Point myLocation;
    private int myWidth;
    private boolean myEnabled;

    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    private boolean myIsShown = false;
    private long myStartTime = 0;

    private CaretCursor() {
      myLocation = new Point(0, 0);
      setEnabled(true);
    }

    public boolean isEnabled() {
      return myEnabled;
    }

    public void setEnabled(boolean enabled) {
      myEnabled = enabled;
    }

    private void activate() {
      final boolean blink = mySettings.isBlinkCaret();
      final int blinkPeriod = mySettings.getCaretBlinkPeriod();
      synchronized (ourCaretBlinkingCommand) {
        ourCaretBlinkingCommand.myEditor = EditorImpl.this;
        ourCaretBlinkingCommand.setBlinkCaret(blink);
        ourCaretBlinkingCommand.setBlinkPeriod(blinkPeriod);
        myIsShown = true;
      }
    }

    public boolean isActive() {
      synchronized (ourCaretBlinkingCommand) {
        return myIsShown;
      }
    }

    private void passivate() {
      synchronized (ourCaretBlinkingCommand) {
        myIsShown = false;
      }
    }

    private void setPosition(Point location, int width) {
      myStartTime = System.currentTimeMillis();
      myLocation = location;
      myWidth = Math.max(width, 2);
      myIsShown = true;
      repaint();
    }

    private void repaint() {
      myEditorComponent.repaintEditorComponent(myLocation.x, myLocation.y, myWidth, getLineHeight());
    }

    private void paint(Graphics g) {
      if (!isEnabled() || !myIsShown || !IJSwingUtilities.hasFocus(getContentComponent()) || isRendererMode()) return;

      int x = myLocation.x;
      int lineHeight = getLineHeight();
      int y = myLocation.y;

      Rectangle viewRectangle = getScrollingModel().getVisibleArea();
      if (x - viewRectangle.x < 0) {
        return;
      }


      g.setColor(myScheme.getColor(EditorColors.CARET_COLOR));

      if (myIsInsertMode != mySettings.isBlockCursor()) {
        for (int i = 0; i < mySettings.getLineCursorWidth(); i++) {
          UIUtil.drawLine(g, x + i, y, x + i, y + lineHeight - 1);
        }
      }
      else {
        Color background = myScheme.getColor(EditorColors.CARET_ROW_COLOR);
        if (background == null) background = getBackgroundColor();
        g.setXORMode(background);

        g.fillRect(x, y, myWidth, lineHeight - 1);

        g.setPaintMode();
      }
    }
  }

  private class ScrollingTimer {
    Timer myTimer;
    private static final int TIMER_PERIOD = 100;
    private static final int CYCLE_SIZE = 20;
    private int myXCycles;
    private int myYCycles;
    private int myDx;
    private int myDy;
    private int xPassedCycles = 0;
    private int yPassedCycles = 0;

    private void start(int dx, int dy) {
      myDx = 0;
      myDy = 0;
      if (dx > 0) {
        myXCycles = CYCLE_SIZE / dx + 1;
        myDx = 1 + dx / CYCLE_SIZE;
      }
      else {
        if (dx < 0) {
          myXCycles = -CYCLE_SIZE / dx + 1;
          myDx = -1 + dx / CYCLE_SIZE;
        }
      }

      if (dy > 0) {
        myYCycles = CYCLE_SIZE / dy + 1;
        myDy = 1 + dy / CYCLE_SIZE;
      }
      else {
        if (dy < 0) {
          myYCycles = -CYCLE_SIZE / dy + 1;
          myDy = -1 + dy / CYCLE_SIZE;
        }
      }

      if (myTimer != null) {
        return;
      }


      myTimer = UIUtil.createNamedTimer("Editor scroll timer",TIMER_PERIOD, new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          myCommandProcessor.executeCommand(myProject, new DocumentRunnable(myDocument, myProject) {
            public void run() {
              // We experienced situation when particular editor was disposed but the timer was still on.
              if (isDisposed()) {
                myTimer.stop();
                return;
              }
              int oldSelectionStart = mySelectionModel.getLeadSelectionOffset();
              VisualPosition caretPosition = getCaretModel().getVisualPosition();
              int column = caretPosition.column;
              xPassedCycles++;
              if (xPassedCycles >= myXCycles) {
                xPassedCycles = 0;
                column += myDx;
              }

              int line = caretPosition.line;
              yPassedCycles++;
              if (yPassedCycles >= myYCycles) {
                yPassedCycles = 0;
                line += myDy;
              }

              line = Math.max(0, line);
              column = Math.max(0, column);
              VisualPosition pos = new VisualPosition(line, column);
              getCaretModel().moveToVisualPosition(pos);
              getScrollingModel().scrollToCaret(ScrollType.RELATIVE);

              int newCaretOffset = getCaretModel().getOffset();
              int caretShift = newCaretOffset - mySavedSelectionStart;

              if (getMouseSelectionState() != MOUSE_SELECTION_STATE_NONE) {
                if (caretShift < 0) {
                  int newSelection = newCaretOffset;
                  if (getMouseSelectionState() == MOUSE_SELECTION_STATE_WORD_SELECTED) {
                    newSelection = mySelectionModel.getWordAtCaretStart();
                  }
                  else {
                    if (getMouseSelectionState() == MOUSE_SELECTION_STATE_LINE_SELECTED) {
                      newSelection =
                        logicalPositionToOffset(visualToLogicalPosition(new VisualPosition(getCaretModel().getVisualPosition().line, 0)));
                    }
                  }
                  if (newSelection < 0) newSelection = newCaretOffset;
                  mySelectionModel.setSelection(validateOffset(mySavedSelectionEnd), newSelection);
                  getCaretModel().moveToOffset(newSelection);
                }
                else {
                  int newSelection = newCaretOffset;
                  if (getMouseSelectionState() == MOUSE_SELECTION_STATE_WORD_SELECTED) {
                    newSelection = mySelectionModel.getWordAtCaretEnd();
                  }
                  else {
                    if (getMouseSelectionState() == MOUSE_SELECTION_STATE_LINE_SELECTED) {
                      newSelection = logicalPositionToOffset(
                        visualToLogicalPosition(new VisualPosition(getCaretModel().getVisualPosition().line + 1, 0)));
                    }
                  }
                  if (newSelection < 0) newSelection = newCaretOffset;
                  mySelectionModel.setSelection(validateOffset(mySavedSelectionStart), newSelection);
                  getCaretModel().moveToOffset(newSelection);
                }
                return;
              }

              if (mySelectionModel.hasBlockSelection()) {
                mySelectionModel.setBlockSelection(mySelectionModel.getBlockStart(), getCaretModel().getLogicalPosition());
              }
              else {
                mySelectionModel.setSelection(oldSelectionStart, getCaretModel().getOffset());
              }
            }
          }, EditorBundle.message("move.cursor.command.name"), DocCommandGroupId.noneGroupId(getDocument()), UndoConfirmationPolicy.DEFAULT, getDocument());
        }
      });
      myTimer.start();
    }

    private void stop() {
      if (myTimer != null) {
        myTimer.stop();
        myTimer = null;
      }
    }

    private int validateOffset(int offset) {
      if (offset < 0) return 0;
      if (offset > myDocument.getTextLength()) return myDocument.getTextLength();
      return offset;
    }
  }

  private static final Field decrButtonField;
  private static final Field incrButtonField;
  static {
    try {
      decrButtonField = BasicScrollBarUI.class.getDeclaredField("decrButton");
      decrButtonField.setAccessible(true);

      incrButtonField = BasicScrollBarUI.class.getDeclaredField("incrButton");
      incrButtonField.setAccessible(true);
    }
    catch (NoSuchFieldException e) {
      throw new IllegalStateException(e);
    }
  }

  class MyScrollBar extends JBScrollBar implements IdeGlassPane.TopComponent {
    @NonNls private static final String APPLE_LAF_AQUA_SCROLL_BAR_UI_CLASS = "apple.laf.AquaScrollBarUI";
    private ScrollBarUI myPersistentUI;

    private MyScrollBar(int orientation) {
      super(orientation);
    }

    void setPersistentUI(ScrollBarUI ui) {
      myPersistentUI = ui;
      setUI(ui);
    }

    @Override
    public boolean canBePreprocessed(MouseEvent e) {
      return JBScrollPane.canBePreprocessed(e, this);
    }

    @Override
    public void setUI(ScrollBarUI ui) {
      if (myPersistentUI == null) myPersistentUI = ui;
      super.setUI(myPersistentUI);
    }

    /**
     * This is helper method. It returns height of the top (decrease) scroll bar
     * button. Please note, that it's possible to return real height only if scroll bar
     * is instance of BasicScrollBarUI. Otherwise it returns fake (but good enough :) )
     * value.
     */
    int getDecScrollButtonHeight() {
      ScrollBarUI barUI = getUI();
      Insets insets = getInsets();
      if (barUI instanceof ButtonlessScrollBarUI) {
        return insets.top + ((ButtonlessScrollBarUI)barUI).getDecrButtonHeight();
      }
      else if (barUI instanceof BasicScrollBarUI) {
        try {
          JButton decrButtonValue = (JButton)decrButtonField.get(barUI);
          LOG.assertTrue(decrButtonValue != null);
          return insets.top + decrButtonValue.getHeight();
        }
        catch (Exception exc) {
          throw new IllegalStateException(exc);
        }
      }
      else {
        return insets.top + 15;
      }
    }

    /**
     * This is helper method. It returns height of the bottom (increase) scroll bar
     * button. Please note, that it's possible to return real height only if scroll bar
     * is instance of BasicScrollBarUI. Otherwise it returns fake (but good enough :) )
     * value.
     */
    int getIncScrollButtonHeight() {
      ScrollBarUI barUI = getUI();
      Insets insets = getInsets();
      if (barUI instanceof ButtonlessScrollBarUI) {
        return insets.top + ((ButtonlessScrollBarUI)barUI).getIncrButtonHeight();
      }
      else if (barUI instanceof BasicScrollBarUI) {
        try {
          JButton incrButtonValue = (JButton)incrButtonField.get(barUI);
          LOG.assertTrue(incrButtonValue != null);
          return insets.bottom + incrButtonValue.getHeight();
        }
        catch (Exception exc) {
          throw new IllegalStateException(exc);
        }
      }
      else if (APPLE_LAF_AQUA_SCROLL_BAR_UI_CLASS.equals(barUI.getClass().getName())) {
        return insets.bottom + 30;
      }
      else {
        return insets.bottom + 15;
      }
    }

    public int getUnitIncrement(int direction) {
      JViewport vp = myScrollPane.getViewport();
      Rectangle vr = vp.getViewRect();
      return myEditorComponent.getScrollableUnitIncrement(vr, SwingConstants.VERTICAL, direction);
    }

    public int getBlockIncrement(int direction) {
      JViewport vp = myScrollPane.getViewport();
      Rectangle vr = vp.getViewRect();
      return myEditorComponent.getScrollableBlockIncrement(vr, SwingConstants.VERTICAL, direction);
    }
  }

  private MyEditable getViewer() {
    if (myEditable == null) {
      myEditable = new MyEditable();
    }
    return myEditable;
  }

  public CopyProvider getCopyProvider() {
    return getViewer();
  }

  public CutProvider getCutProvider() {
    return getViewer();
  }

  public PasteProvider getPasteProvider() {

    return getViewer();
  }

  public DeleteProvider getDeleteProvider() {
    return getViewer();
  }

  private class MyEditable implements CutProvider, CopyProvider, PasteProvider, DeleteProvider {
    public void performCopy(DataContext dataContext) {
      executeAction(IdeActions.ACTION_EDITOR_COPY, dataContext);
    }

    public boolean isCopyEnabled(DataContext dataContext) {
      return true;
    }

    public boolean isCopyVisible(DataContext dataContext) {
      return getSelectionModel().hasSelection() || getSelectionModel().hasBlockSelection();
    }

    public void performCut(DataContext dataContext) {
      executeAction(IdeActions.ACTION_EDITOR_CUT, dataContext);
    }

    public boolean isCutEnabled(DataContext dataContext) {
      return !isViewer();
    }

    public boolean isCutVisible(DataContext dataContext) {
      return getSelectionModel().hasSelection() || getSelectionModel().hasBlockSelection();
    }

    public void performPaste(DataContext dataContext) {
      executeAction(IdeActions.ACTION_EDITOR_PASTE, dataContext);
    }

    public boolean isPastePossible(DataContext dataContext) {
      // Copy of isPasteEnabled. See interface method javadoc.
      return !isViewer();
    }

    public boolean isPasteEnabled(DataContext dataContext) {
      return !isViewer();
    }

    public void deleteElement(DataContext dataContext) {
      executeAction(IdeActions.ACTION_EDITOR_DELETE, dataContext);
    }

    public boolean canDeleteElement(DataContext dataContext) {
      return !isViewer();
    }

    private void executeAction(String actionId, DataContext dataContext) {
      EditorAction action = (EditorAction)ActionManager.getInstance().getAction(actionId);
      if (action != null) {
        action.actionPerformed(EditorImpl.this, dataContext);
      }
    }
  }

  public void setColorsScheme(@NotNull EditorColorsScheme scheme) {
    assertIsDispatchThread();
    myScheme = scheme;
    reinitSettings();
  }

  @NotNull
  public EditorColorsScheme getColorsScheme() {
    assertReadAccess();
    return myScheme;
  }

  void assertIsDispatchThread() {
    ApplicationManagerEx.getApplicationEx().assertIsDispatchThread(myEditorComponent);
  }
  private static void assertReadAccess() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
  }

  public void setVerticalScrollbarOrientation(int type) {
    assertIsDispatchThread();
    int currentHorOffset = myScrollingModel.getHorizontalScrollOffset();
    myScrollBarOrientation = type;
    if (type == VERTICAL_SCROLLBAR_LEFT) {
      myScrollPane.setLayout(new LeftHandScrollbarLayout());
    }
    else {
      myScrollPane.setLayout(new ScrollPaneLayout());
    }
    ((JBScrollPane)myScrollPane).setupCorners();
    myScrollingModel.scrollHorizontally(currentHorOffset);
  }

  public void setVerticalScrollbarVisible(boolean b) {
    if (b) {
      myScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
    }
    else {
      myScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
    }
  }

  public void setHorizontalScrollbarVisible(boolean b) {
    if (b) {
      myScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    }
    else {
      myScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    }
  }

  int getVerticalScrollbarOrientation() {
    return myScrollBarOrientation;
  }

  MyScrollBar getVerticalScrollBar() {
    return myVerticalScrollBar;
  }

  private int getMouseSelectionState() {
    return myMouseSelectionState;
  }

  private void setMouseSelectionState(int mouseSelectionState) {
    if (getMouseSelectionState() == mouseSelectionState) return;

    myMouseSelectionState = mouseSelectionState;
    myMouseSelectionChangeTimestamp = System.currentTimeMillis();

    myMouseSelectionStateAlarm.cancelAllRequests();
    if (myMouseSelectionState != MOUSE_SELECTION_STATE_NONE) {
      if (myMouseSelectionStateResetRunnable == null) {
        myMouseSelectionStateResetRunnable = new Runnable() {
          @Override
          public void run() {
            resetMouseSelectionState(null);
          }
        };
      }
      myMouseSelectionStateAlarm.addRequest(myMouseSelectionStateResetRunnable, Registry.intValue("editor.mouseSelectionStateResetTimeout"),
                                           ModalityState.stateForComponent(myEditorComponent));
    }
  }

  private void resetMouseSelectionState(@Nullable MouseEvent event) {
    setMouseSelectionState(MOUSE_SELECTION_STATE_NONE);

    MouseEvent e = event != null ? event : myMouseMovedEvent;
    if (e != null) {
      validateMousePointer(e);
    }
  }

  private void cancelAutoResetForMouseSelectionState() {
    myMouseSelectionStateAlarm.cancelAllRequests();
  }

  void replaceInputMethodText(InputMethodEvent e) {
    getInputMethodRequests();
    myInputMethodRequestsHandler.replaceInputMethodText(e);
  }

  void inputMethodCaretPositionChanged(InputMethodEvent e) {
    getInputMethodRequests();
    myInputMethodRequestsHandler.setInputMethodCaretPosition(e);
  }

  InputMethodRequests getInputMethodRequests() {
    if (myInputMethodRequestsHandler == null) {
      myInputMethodRequestsHandler = new MyInputMethodHandler();
      myInputMethodRequestsSwingWrapper = new MyInputMethodHandleSwingThreadWrapper(myInputMethodRequestsHandler);
    }
    return myInputMethodRequestsSwingWrapper;
  }

  public boolean processKeyTyped(@NotNull KeyEvent e) {
    if (e.getID() != KeyEvent.KEY_TYPED) return false;
    char c = e.getKeyChar();
    if (UIUtil.isReallyTypedEvent(e)) { // Hack just like in javax.swing.text.DefaultEditorKit.DefaultKeyTypedAction
      processKeyTyped(c);
      return true;
    }
    else {
      return false;
    }
  }

  void beforeModalityStateChanged() {
    myScrollingModel.beforeModalityStateChanged();
  }

  public EditorDropHandler getDropHandler() {
    return myDropHandler;
  }

  public void setDropHandler(EditorDropHandler dropHandler) {
    myDropHandler = dropHandler;
  }

  private static class MyInputMethodHandleSwingThreadWrapper implements InputMethodRequests {
    private final InputMethodRequests myDelegate;

    private MyInputMethodHandleSwingThreadWrapper(InputMethodRequests delegate) {
      myDelegate = delegate;
    }

    public Rectangle getTextLocation(final TextHitInfo offset) {
      if (ApplicationManager.getApplication().isDispatchThread()) return myDelegate.getTextLocation(offset);

      final Rectangle[] r = new Rectangle[1];
      try {
        GuiUtils.invokeAndWait(new Runnable() {
          public void run() {
            r[0] = myDelegate.getTextLocation(offset);
          }
        });
      }
      catch (InterruptedException e) {
        LOG.error(e);
      }
      catch (InvocationTargetException e) {
        LOG.error(e);
      }
      return r[0];
    }

    public TextHitInfo getLocationOffset(final int x, final int y) {
      if (ApplicationManager.getApplication().isDispatchThread()) return myDelegate.getLocationOffset(x, y);

      final TextHitInfo[] r = new TextHitInfo[1];
      try {
        GuiUtils.invokeAndWait(new Runnable() {
          public void run() {
            r[0] = myDelegate.getLocationOffset(x, y);
          }
        });
      }
      catch (InterruptedException e) {
        LOG.error(e);
      }
      catch (InvocationTargetException e) {
        LOG.error(e);
      }
      return r[0];
    }

    public int getInsertPositionOffset() {
      if (ApplicationManager.getApplication().isDispatchThread()) return myDelegate.getInsertPositionOffset();

      final int[] r = new int[1];
      try {
        GuiUtils.invokeAndWait(new Runnable() {
          public void run() {
            r[0] = myDelegate.getInsertPositionOffset();
          }
        });
      }
      catch (InterruptedException e) {
        LOG.error(e);
      }
      catch (InvocationTargetException e) {
        LOG.error(e);
      }
      return r[0];
    }

    public AttributedCharacterIterator getCommittedText(final int beginIndex,
                                                        final int endIndex,
                                                        final AttributedCharacterIterator.Attribute[] attributes) {
      if (ApplicationManager.getApplication().isDispatchThread()) {
        return myDelegate.getCommittedText(beginIndex, endIndex, attributes);
      }
      final AttributedCharacterIterator[] r = new AttributedCharacterIterator[1];
      try {
        GuiUtils.invokeAndWait(new Runnable() {
          public void run() {
            r[0] = myDelegate.getCommittedText(beginIndex, endIndex, attributes);
          }
        });
      }
      catch (InterruptedException e) {
        LOG.error(e);
      }
      catch (InvocationTargetException e) {
        LOG.error(e);
      }
      return r[0];
    }

    public int getCommittedTextLength() {
      if (ApplicationManager.getApplication().isDispatchThread()) return myDelegate.getCommittedTextLength();
      final int[] r = new int[1];
      try {
        GuiUtils.invokeAndWait(new Runnable() {
          public void run() {
            r[0] = myDelegate.getCommittedTextLength();
          }
        });
      }
      catch (InterruptedException e) {
        LOG.error(e);
      }
      catch (InvocationTargetException e) {
        LOG.error(e);
      }
      return r[0];
    }

    @Nullable
    public AttributedCharacterIterator cancelLatestCommittedText(AttributedCharacterIterator.Attribute[] attributes) {
      return null;
    }

    public AttributedCharacterIterator getSelectedText(final AttributedCharacterIterator.Attribute[] attributes) {
      if (ApplicationManager.getApplication().isDispatchThread()) return myDelegate.getSelectedText(attributes);

      final AttributedCharacterIterator[] r = new AttributedCharacterIterator[1];
      try {
        GuiUtils.invokeAndWait(new Runnable() {
          public void run() {
            r[0] = myDelegate.getSelectedText(attributes);
          }
        });
      }
      catch (InterruptedException e) {
        LOG.error(e);
      }
      catch (InvocationTargetException e) {
        LOG.error(e);
      }
      return r[0];
    }
  }

  private class MyInputMethodHandler implements InputMethodRequests {
    private String composedText;
    private int composedTextStart;
    private int composedTextEnd;

    public Rectangle getTextLocation(TextHitInfo offset) {
      Point caret = logicalPositionToXY(getCaretModel().getLogicalPosition());
      Rectangle r = new Rectangle(caret, new Dimension(1, getLineHeight()));
      Point p = getContentComponent().getLocationOnScreen();
      r.translate(p.x, p.y);

      return r;
    }

    @Nullable
    public TextHitInfo getLocationOffset(int x, int y) {
      if (composedText != null) {
        Point p = getContentComponent().getLocationOnScreen();
        p.x = x - p.x;
        p.y = y - p.y;
        int pos = logicalPositionToOffset(xyToLogicalPosition(p));
        if (pos >= composedTextStart && pos <= composedTextEnd) {
          return TextHitInfo.leading(pos - composedTextStart);
        }
      }
      return null;
    }

    public int getInsertPositionOffset() {
      int composedStartIndex = 0;
      int composedEndIndex = 0;
      if (composedText != null) {
        composedStartIndex = composedTextStart;
        composedEndIndex = composedTextEnd;
      }

      int caretIndex = getCaretModel().getOffset();

      if (caretIndex < composedStartIndex) {
        return caretIndex;
      }
      else {
        if (caretIndex < composedEndIndex) {
          return composedStartIndex;
        }
        else {
          return caretIndex - (composedEndIndex - composedStartIndex);
        }
      }
    }

    private String getText(int startIdx, int endIdx) {
      if (startIdx >= 0 && endIdx > startIdx) {
        CharSequence chars = getDocument().getCharsSequence();
        return chars.subSequence(startIdx, endIdx).toString();
      }

      return "";
    }

    public AttributedCharacterIterator getCommittedText(int beginIndex, int endIndex, AttributedCharacterIterator.Attribute[] attributes) {
      int composedStartIndex = 0;
      int composedEndIndex = 0;
      if (composedText != null) {
        composedStartIndex = composedTextStart;
        composedEndIndex = composedTextEnd;
      }

      String committed;
      if (beginIndex < composedStartIndex) {
        if (endIndex <= composedStartIndex) {
          committed = getText(beginIndex, endIndex - beginIndex);
        }
        else {
          int firstPartLength = composedStartIndex - beginIndex;
          committed = getText(beginIndex, firstPartLength) + getText(composedEndIndex, endIndex - beginIndex - firstPartLength);
        }
      }
      else {
        committed = getText(beginIndex + composedEndIndex - composedStartIndex, endIndex - beginIndex);
      }

      return new AttributedString(committed).getIterator();
    }

    public int getCommittedTextLength() {
      int length = getDocument().getTextLength();
      if (composedText != null) {
        length -= composedText.length();
      }
      return length;
    }

    @Nullable
    public AttributedCharacterIterator cancelLatestCommittedText(AttributedCharacterIterator.Attribute[] attributes) {
      return null;
    }

    @Nullable
    public AttributedCharacterIterator getSelectedText(AttributedCharacterIterator.Attribute[] attributes) {
      String text = getSelectionModel().getSelectedText();
      return text == null ? null : new AttributedString(text).getIterator();
    }

    private void createComposedString(int composedIndex, AttributedCharacterIterator text) {
      StringBuffer strBuf = new StringBuffer();

      // create attributed string with no attributes
      for (char c = text.setIndex(composedIndex); c != CharacterIterator.DONE; c = text.next()) {
        strBuf.append(c);
      }

      composedText = new String(strBuf);
    }

    private void setInputMethodCaretPosition(InputMethodEvent e) {
      if (composedText != null) {
        int dot = composedTextStart;

        TextHitInfo caretPos = e.getCaret();
        if (caretPos != null) {
          dot += caretPos.getInsertionIndex();
        }

        getCaretModel().moveToOffset(dot);
        getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      }
    }

    private void runUndoTransparent(final Runnable runnable) {
      CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
        public void run() {
          CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
            public void run() {
              ApplicationManager.getApplication().runWriteAction(runnable);
            }
          }, "", getDocument(), UndoConfirmationPolicy.DEFAULT, getDocument());
        }
      });
    }

    private void replaceInputMethodText(InputMethodEvent e) {
      int commitCount = e.getCommittedCharacterCount();
      AttributedCharacterIterator text = e.getText();

      // old composed text deletion
      final Document doc = getDocument();

      if (composedText != null) {
        if (!isViewer() && doc.isWritable()) {
          runUndoTransparent(new Runnable() {
            public void run() {
              doc.deleteString(Math.max(0, composedTextStart), Math.min(composedTextEnd, doc.getTextLength()));
            }
          });
        }
        composedText = null;
      }

      if (text != null) {
        text.first();

        // committed text insertion
        if (commitCount > 0) {
          //noinspection ForLoopThatDoesntUseLoopVariable
          for (char c = text.current(); commitCount > 0; c = text.next(), commitCount--) {
            if (c >= 0x20 && c != 0x7F) { // Hack just like in javax.swing.text.DefaultEditorKit.DefaultKeyTypedAction
              processKeyTyped(c);
            }
          }
        }

        // new composed text insertion
        if (!isViewer() && doc.isWritable()) {
          int composedTextIndex = text.getIndex();
          if (composedTextIndex < text.getEndIndex()) {
            createComposedString(composedTextIndex, text);

            runUndoTransparent(new Runnable() {
              public void run() {
                EditorModificationUtil.insertStringAtCaret(EditorImpl.this, composedText, false, false);
              }
            });

            composedTextStart = getCaretModel().getOffset();
            composedTextEnd = getCaretModel().getOffset() + composedText.length();
          }
        }
      }
    }
  }

  private class MyMouseAdapter extends MouseAdapter {

    private boolean mySelectionTweaked;

    public void mousePressed(MouseEvent e) {
      requestFocus();
      runMousePressedCommand(e);
    }

    public void mouseReleased(MouseEvent e) {
      runMouseReleasedCommand(e);
      if (!e.isConsumed() && myMousePressedEvent != null && !myMousePressedEvent.isConsumed() &&
        Math.abs(e.getX() - myMousePressedEvent.getX()) < EditorUtil.getSpaceWidth(Font.PLAIN, EditorImpl.this) &&
        Math.abs(e.getY() - myMousePressedEvent.getY()) < getLineHeight()) {
        runMouseClickedCommand(e);
      }
    }

    public void mouseEntered(MouseEvent e) {
      runMouseEnteredCommand(e);
    }

    public void mouseExited(MouseEvent e) {
      runMouseExitedCommand(e);
      EditorMouseEvent event = new EditorMouseEvent(EditorImpl.this, e, getMouseEventArea(e));
      if (event.getArea() == EditorMouseEventArea.LINE_MARKERS_AREA) {
        myGutterComponent.mouseExited(e);
      }

      TooltipController.getInstance().cancelTooltip(FOLDING_TOOLTIP_GROUP, e, true);
    }
    private void runMousePressedCommand(final MouseEvent e) {
      mySelectionTweaked = false;
      myMousePressedEvent = e;
      EditorMouseEvent event = new EditorMouseEvent(EditorImpl.this, e, getMouseEventArea(e));

      for (EditorMouseListener mouseListener : myMouseListeners) {
        mouseListener.mousePressed(event);
      }

      // On some systems (for example on Linux) popup trigger is MOUSE_PRESSED event.
      // But this trigger is always consumed by popup handler. In that case we have to
      // also move caret.
      if (event.isConsumed() && !(event.getMouseEvent().isPopupTrigger() || event.getArea() == EditorMouseEventArea.EDITING_AREA)) {
        return;
      }

      if (myCommandProcessor != null) {
        Runnable runnable = new Runnable() {
          public void run() {
            if (processMousePressed(e) && myProject != null && !myProject.isDefault()) {
              IdeDocumentHistory.getInstance(myProject).includeCurrentCommandAsNavigation();
            }
          }
        };
        myCommandProcessor.executeCommand(myProject, runnable, "", DocCommandGroupId.noneGroupId(getDocument()), UndoConfirmationPolicy.DEFAULT, getDocument());
      }
      else {
        processMousePressed(e);
      }
    }

    private void runMouseClickedCommand(final MouseEvent e) {
      EditorMouseEvent event = new EditorMouseEvent(EditorImpl.this, e, getMouseEventArea(e));
      for (EditorMouseListener listener : myMouseListeners) {
        listener.mouseClicked(event);
        if (event.isConsumed()) {
          e.consume();
          return;
        }
      }
    }

    private void runMouseReleasedCommand(final MouseEvent e) {
      if (!mySelectionTweaked) {
        tweakSelectionIfNecessary(e);
      }
      if (e.isConsumed()) {
        return;
      }

      myScrollingTimer.stop();
      EditorMouseEvent event = new EditorMouseEvent(EditorImpl.this, e, getMouseEventArea(e));
      for (EditorMouseListener listener : myMouseListeners) {
        listener.mouseReleased(event);
        if (event.isConsumed()) {
          e.consume();
          return;
        }
      }

      if (myCommandProcessor != null) {
        Runnable runnable = new Runnable() {
          public void run() {
            processMouseReleased(e);
          }
        };
        myCommandProcessor.executeCommand(myProject, runnable, "", DocCommandGroupId.noneGroupId(getDocument()), UndoConfirmationPolicy.DEFAULT, getDocument());
      }
      else {
        processMouseReleased(e);
      }
    }

    private void runMouseEnteredCommand(MouseEvent e) {
      EditorMouseEvent event = new EditorMouseEvent(EditorImpl.this, e, getMouseEventArea(e));
      for (EditorMouseListener listener : myMouseListeners) {
        listener.mouseEntered(event);
        if (event.isConsumed()) {
          e.consume();
          return;
        }
      }
    }

    private void runMouseExitedCommand(MouseEvent e) {
      EditorMouseEvent event = new EditorMouseEvent(EditorImpl.this, e, getMouseEventArea(e));
      for (EditorMouseListener listener : myMouseListeners) {
        listener.mouseExited(event);
        if (event.isConsumed()) {
          e.consume();
          return;
        }
      }
    }

    private boolean processMousePressed(MouseEvent e) {
      myInitialMouseEvent = e;

      if (myMouseSelectionState != MOUSE_SELECTION_STATE_NONE && System.currentTimeMillis() - myMouseSelectionChangeTimestamp > Registry.intValue(
        "editor.mouseSelectionStateResetTimeout")) {
        resetMouseSelectionState(e);
      }

      int x = e.getX();
      int y = e.getY();

      if (x < 0) x = 0;
      if (y < 0) y = 0;

      final EditorMouseEventArea eventArea = getMouseEventArea(e);
      boolean isNavigation = false;
      if (eventArea == EditorMouseEventArea.FOLDING_OUTLINE_AREA) {
        final FoldRegion range = myGutterComponent.findFoldingAnchorAt(x, y);
        if (range != null) {
          final boolean expansion = !range.isExpanded();

          int scrollShift = y - getScrollingModel().getVerticalScrollOffset();
          Runnable processor = new Runnable() {
            public void run() {
              myFoldingModel.flushCaretShift();
              range.setExpanded(expansion);
            }
          };
          getFoldingModel().runBatchFoldingOperation(processor);
          y = myGutterComponent.getHeadCenterY(range);
          getScrollingModel().scrollVertically(y - scrollShift);
          myGutterComponent.updateSize();
          return isNavigation;
        }
      }

      if (e.getSource() == myGutterComponent) {
        if (eventArea == EditorMouseEventArea.LINE_MARKERS_AREA || eventArea == EditorMouseEventArea.ANNOTATIONS_AREA || eventArea == EditorMouseEventArea.LINE_NUMBERS_AREA) {
          if (tweakSelectionIfNecessary(e)) {
            mySelectionTweaked = true;
          }
          else {
            myGutterComponent.mousePressed(e);
          }
          if (e.isConsumed()) return isNavigation;
        }
        x = 0;
      }

      int oldSelectionStart = mySelectionModel.getLeadSelectionOffset();

      int oldStart = mySelectionModel.getSelectionStart();
      int oldEnd = mySelectionModel.getSelectionEnd();

      moveCaretToScreenPos(x, y);

      if (e.isPopupTrigger()) return isNavigation;

      requestFocus();

      int caretOffset = getCaretModel().getOffset();

      int newStart = mySelectionModel.getSelectionStart();
      int newEnd = mySelectionModel.getSelectionEnd();
      if (oldStart != newStart && oldEnd != newEnd) {
        if (oldStart == oldEnd && newStart == newEnd) {
          isNavigation = true;
        }
      }

      myMouseSelectedRegion = myFoldingModel.getFoldingPlaceholderAt(new Point(x, y));
      myMousePressedInsideSelection = mySelectionModel.hasSelection() && caretOffset >= mySelectionModel.getSelectionStart() &&
        caretOffset <= mySelectionModel.getSelectionEnd();

      if (!myMousePressedInsideSelection && mySelectionModel.hasBlockSelection()) {
        int[] starts = mySelectionModel.getBlockSelectionStarts();
        int[] ends = mySelectionModel.getBlockSelectionEnds();
        for (int i = 0; i < starts.length; i++) {
          if (caretOffset >= starts[i] && caretOffset < ends[i]) {
            myMousePressedInsideSelection = true;
            break;
          }
        }
      }

      if (getMouseEventArea(e) == EditorMouseEventArea.LINE_NUMBERS_AREA && e.getClickCount() == 1) {
        mySelectionModel.selectLineAtCaret();
        setMouseSelectionState(MOUSE_SELECTION_STATE_LINE_SELECTED);
        mySavedSelectionStart = mySelectionModel.getSelectionStart();
        mySavedSelectionEnd = mySelectionModel.getSelectionEnd();
        return isNavigation;
      }

      if (e.isShiftDown() && !e.isControlDown() && !e.isAltDown()) {
        if (getMouseSelectionState() != MOUSE_SELECTION_STATE_NONE) {
          if (caretOffset < mySavedSelectionStart) {
            mySelectionModel.setSelection(mySavedSelectionEnd, caretOffset);
          }
          else {
            mySelectionModel.setSelection(mySavedSelectionStart, caretOffset);
          }
        }
        else {
          mySelectionModel.setSelection(oldSelectionStart, caretOffset);
        }
      }
      else {
        if (!myMousePressedInsideSelection && (getSelectionModel().hasSelection() || getSelectionModel().hasBlockSelection())) {
          setMouseSelectionState(MOUSE_SELECTION_STATE_NONE);
          mySelectionModel.setSelection(caretOffset, caretOffset);
        }
        else {
          if (!e.isPopupTrigger()) {
            switch (e.getClickCount()) {
              case 2:
                selectWordAtCaret(mySettings.isMouseClickSelectionHonorsCamelWords());
                break;

              case 3:
                mySelectionModel.selectLineAtCaret();
                setMouseSelectionState(MOUSE_SELECTION_STATE_LINE_SELECTED);
                mySavedSelectionStart = mySelectionModel.getSelectionStart();
                mySavedSelectionEnd = mySelectionModel.getSelectionEnd();
                break;
            }
          }
        }
      }

      return isNavigation;
    }
  }

  private void selectWordAtCaret(boolean honorCamelCase) {
    mySelectionModel.selectWordAtCaret(honorCamelCase);
    setMouseSelectionState(MOUSE_SELECTION_STATE_WORD_SELECTED);
    mySavedSelectionStart = mySelectionModel.getSelectionStart();
    mySavedSelectionEnd = mySelectionModel.getSelectionEnd();
    getCaretModel().moveToOffset(mySavedSelectionEnd);
  }

  /**
   * Allows to answer if given event should tweak editor selection.
   *
   * @param e   event for occurred mouse action
   * @return    <code>true</code> if action that produces given event will trigger editor selection change; <code>false</code> otherwise
   */
  private boolean tweakSelectionEvent(@NotNull MouseEvent e) {
    return getSelectionModel().hasSelection() && e.getButton() == MouseEvent.BUTTON1 && e.isShiftDown()
           && e.getComponent() == getGutterComponentEx();
  }

  /**
   * Checks if editor selection should be changed because of click at the given point at gutter and proceeds if necessary.
   * <p/>
   * The main idea is that selection can be changed during left mouse clicks with hold <code>Shift</code> button and we want
   * to distinguish that situation from <code>'close line numbers gutter'</code> action (activated by <code>'Shift+click'</code>
   * on gutter area).
   *
   * @param e   event for mouse click on gutter area
   * @return    <code>true</code> if editor's selection is changed because of the click; <code>false</code> otherwise
   */
  private boolean tweakSelectionIfNecessary(@NotNull MouseEvent e) {
    if (!tweakSelectionEvent(e)) {
      return false;
    }

    int startSelectionOffset = getSelectionModel().getSelectionStart();
    int startVisLine = offsetToVisualLine(startSelectionOffset);

    int endSelectionOffset = getSelectionModel().getSelectionEnd();
    int endVisLine = offsetToVisualLine(endSelectionOffset - 1);

    int clickVisLine = yPositionToVisibleLine(e.getPoint().y);

    if (clickVisLine < startVisLine) {
      // Expand selection at backward direction.
      int startOffset = logicalPositionToOffset(visualToLogicalPosition(new VisualPosition(clickVisLine, 0)));
      getSelectionModel().setSelection(startOffset, endSelectionOffset);
      getCaretModel().moveToOffset(startOffset);
    }
    else if (clickVisLine > endVisLine) {
      // Expand selection at forward direction.
      int endLineOffset = EditorUtil.getVisualLineEndOffset(this, clickVisLine);
      getSelectionModel().setSelection(getSelectionModel().getSelectionStart(), endLineOffset);
      getCaretModel().moveToOffset(endLineOffset, true);
    }
    else if (startVisLine == endVisLine) {
      // Remove selection
      getSelectionModel().removeSelection();
    }
    else {
      // Reduce selection in backward direction.
      if (getSelectionModel().getLeadSelectionOffset() == endSelectionOffset) {
        if (clickVisLine == startVisLine) {
          clickVisLine++;
        }
        int startOffset = logicalPositionToOffset(visualToLogicalPosition(new VisualPosition(clickVisLine, 0)));
        getSelectionModel().setSelection(startOffset, endSelectionOffset);
        getCaretModel().moveToOffset(startOffset);
      }
      else {
        // Reduce selection is forward direction.
        if (clickVisLine == endVisLine) {
          clickVisLine--;
        }
        int endLineOffset = EditorUtil.getVisualLineEndOffset(this, clickVisLine);
        getSelectionModel().setSelection(startSelectionOffset, endLineOffset);
        getCaretModel().moveToOffset(endLineOffset);
      }
    }
    e.consume();
    return true;
  }

  private static final TooltipGroup FOLDING_TOOLTIP_GROUP = new TooltipGroup("FOLDING_TOOLTIP_GROUP", 10);

  private class MyMouseMotionListener implements MouseMotionListener {
    public void mouseDragged(MouseEvent e) {
      validateMousePointer(e);
      runMouseDraggedCommand(e);
      EditorMouseEvent event = new EditorMouseEvent(EditorImpl.this, e, getMouseEventArea(e));
      if (event.getArea() == EditorMouseEventArea.LINE_MARKERS_AREA) {
        myGutterComponent.mouseDragged(e);
      }

      for (EditorMouseMotionListener listener : myMouseMotionListeners) {
        listener.mouseDragged(event);
      }
    }

    public void mouseMoved(MouseEvent e) {
      if (getMouseSelectionState() != MOUSE_SELECTION_STATE_NONE) {
        if (myMousePressedEvent != null && myMousePressedEvent.getComponent() == e.getComponent()) {
          Point lastPoint = myMousePressedEvent.getPoint();
          Point point = e.getPoint();
          int deadZone = Registry.intValue("editor.mouseSelectionStateResetDeadzone");
          if (Math.abs(lastPoint.x - point.x) >= deadZone || Math.abs(lastPoint.y - point.y) >= deadZone) {
            resetMouseSelectionState(e);
          }
        } else {
          validateMousePointer(e);
        }
      } else {
        validateMousePointer(e);
      }

      myMouseMovedEvent = e;

      EditorMouseEvent event = new EditorMouseEvent(EditorImpl.this, e, getMouseEventArea(e));
      if (e.getSource() == myGutterComponent) {
        myGutterComponent.mouseMoved(e);
      }

      if (event.getArea() == EditorMouseEventArea.EDITING_AREA) {
        FoldRegion fold = myFoldingModel.getFoldingPlaceholderAt(e.getPoint());
        TooltipController controller = TooltipController.getInstance();
        if (fold != null && !fold.shouldNeverExpand()) {
          DocumentFragment range = createDocumentFragment(fold);
          final Point p =
            SwingUtilities.convertPoint((Component)e.getSource(), e.getPoint(), getComponent().getRootPane().getLayeredPane());
          controller.showTooltip(EditorImpl.this, p, new DocumentFragmentTooltipRenderer(range), false, FOLDING_TOOLTIP_GROUP);
        }
        else {
          controller.cancelTooltip(FOLDING_TOOLTIP_GROUP, e, true);
        }
      }

      for (EditorMouseMotionListener listener : myMouseMotionListeners) {
        listener.mouseMoved(event);
      }
    }

    private DocumentFragment createDocumentFragment(FoldRegion fold) {
      final FoldingGroup group = fold.getGroup();
      final int foldStart = fold.getStartOffset();
      if (group != null) {
        final int endOffset = myFoldingModel.getEndOffset(group);
        if (offsetToVisualLine(endOffset) == offsetToVisualLine(foldStart)) {
          return new DocumentFragment(myDocument, foldStart, endOffset);
        }
      }

      final int oldEnd = fold.getEndOffset();
      return new DocumentFragment(myDocument, foldStart, oldEnd);
    }
  }

  private class MyColorSchemeDelegate implements EditorColorsScheme {
    private final HashMap<TextAttributesKey, TextAttributes> myOwnAttributes = new HashMap<TextAttributesKey, TextAttributes>();
    private final HashMap<ColorKey, Color> myOwnColors = new HashMap<ColorKey, Color>();
    private final EditorColorsScheme myCustomGlobalScheme;
    private Map<EditorFontType, Font> myFontsMap = null;
    private int myMaxFontSize = OptionsConstants.MAX_EDITOR_FONT_SIZE;
    private int myFontSize = -1;
    private String myFaceName = null;
    private EditorColorsScheme myGlobalScheme;

    private MyColorSchemeDelegate(@Nullable final EditorColorsScheme globalScheme) {
      myCustomGlobalScheme = globalScheme;
      updateGlobalScheme();
    }

    private EditorColorsScheme getGlobal() {
      return myGlobalScheme;
    }

    public String getName() {
      return getGlobal().getName();
    }


    protected void initFonts() {
      String editorFontName = getEditorFontName();
      int editorFontSize = getEditorFontSize();

      myFontsMap = new EnumMap<EditorFontType, Font>(EditorFontType.class);

      Font plainFont = new Font(editorFontName, Font.PLAIN, editorFontSize);
      Font boldFont = new Font(editorFontName, Font.BOLD, editorFontSize);
      Font italicFont = new Font(editorFontName, Font.ITALIC, editorFontSize);
      Font boldItalicFont = new Font(editorFontName, Font.BOLD + Font.ITALIC, editorFontSize);

      myFontsMap.put(EditorFontType.PLAIN, plainFont);
      myFontsMap.put(EditorFontType.BOLD, boldFont);
      myFontsMap.put(EditorFontType.ITALIC, italicFont);
      myFontsMap.put(EditorFontType.BOLD_ITALIC, boldItalicFont);

      reinitSettings();
    }

    public void setName(String name) {
      getGlobal().setName(name);
    }

    public TextAttributes getAttributes(TextAttributesKey key) {
      if (myOwnAttributes.containsKey(key)) return myOwnAttributes.get(key);
      return getGlobal().getAttributes(key);
    }

    public void setAttributes(TextAttributesKey key, TextAttributes attributes) {
      myOwnAttributes.put(key, attributes);
    }

    public Color getDefaultBackground() {
      return getGlobal().getDefaultBackground();
    }

    public Color getDefaultForeground() {
      return getGlobal().getDefaultForeground();
    }

    public Color getColor(ColorKey key) {
      if (myOwnColors.containsKey(key)) return myOwnColors.get(key);
      return getGlobal().getColor(key);
    }

    public void setColor(ColorKey key, Color color) {
      myOwnColors.put(key, color);

      // These two are here because those attributes are cached and I do not whant the clients to call editor's reinit
      // settings in this case.
      myCaretModel.reinitSettings();
      mySelectionModel.reinitSettings();
    }

    public int getEditorFontSize() {
      if (myFontSize == -1) {
        return getGlobal().getEditorFontSize();
      }
      return myFontSize;
    }

    public void setEditorFontSize(int fontSize) {
      if (fontSize < 8) fontSize = 8;
      if (fontSize > myMaxFontSize) fontSize = myMaxFontSize;
      myFontSize = fontSize;
      initFonts();
    }

    @Override
    public FontSize getQuickDocFontSize() {
      return myGlobalScheme.getQuickDocFontSize();
    }

    @Override
    public void setQuickDocFontSize(@NotNull FontSize fontSize) {
      myGlobalScheme.setQuickDocFontSize(fontSize);
    }

    public String getEditorFontName() {
      if (myFaceName == null) {
        return getGlobal().getEditorFontName();
      }
      return myFaceName;
    }

    public void setEditorFontName(String fontName) {
      myFaceName = fontName;
      initFonts();
    }

    public Font getFont(EditorFontType key) {
      if (myFontsMap != null) {
        Font font = myFontsMap.get(key);
        if (font != null) return font;
      }
      return getGlobal().getFont(key);
    }

    public void setFont(EditorFontType key, Font font) {
      if (myFontsMap == null) {
        initFonts();
      }
      myFontsMap.put(key, font);
      reinitSettings();
    }

    public float getLineSpacing() {
      return getGlobal().getLineSpacing();
    }

    public void setLineSpacing(float lineSpacing) {
      getGlobal().setLineSpacing(lineSpacing);
    }

    @Nullable
    public Object clone() {
      return null;
    }

    public void readExternal(Element element) throws InvalidDataException {
    }

    public void writeExternal(Element element) throws WriteExternalException {
    }

    public void updateGlobalScheme() {
      myGlobalScheme = myCustomGlobalScheme != null ? myCustomGlobalScheme
                                                    :  EditorColorsManager.getInstance().getGlobalScheme();
      int globalFontSize = getGlobal().getEditorFontSize();
      myMaxFontSize = Math.max(OptionsConstants.MAX_EDITOR_FONT_SIZE, globalFontSize);
    }

    @Override
    public String getConsoleFontName() {
      return getGlobal().getConsoleFontName();
    }

    @Override
    public void setConsoleFontName(String fontName) {
      getGlobal().setConsoleFontName(fontName);
    }

    @Override
    public int getConsoleFontSize() {
      return getGlobal().getConsoleFontSize();
    }

    @Override
    public void setConsoleFontSize(int fontSize) {
      getGlobal().setConsoleFontSize(fontSize);
    }

    @Override
    public float getConsoleLineSpacing() {
      return getGlobal().getConsoleLineSpacing();
    }

    @Override
    public void setConsoleLineSpacing(float lineSpacing) {
      getGlobal().setConsoleLineSpacing(lineSpacing);
    }
  }

  private static class MyTransferHandler extends TransferHandler {
    private RangeMarker myDraggedRange = null;

    private static Editor getEditor(JComponent comp) {
      EditorComponentImpl editorComponent = (EditorComponentImpl)comp;
      return editorComponent.getEditor();
    }

    public boolean importData(final JComponent comp, final Transferable t) {
      final EditorImpl editor = (EditorImpl)getEditor(comp);

      final EditorDropHandler dropHandler = editor.getDropHandler();
      if (dropHandler != null && dropHandler.canHandleDrop(t.getTransferDataFlavors())) {
        dropHandler.handleDrop(t, editor.getProject());
        return true;
      }

      final int caretOffset = editor.getCaretModel().getOffset();
      if (myDraggedRange != null && myDraggedRange.getStartOffset() <= caretOffset && caretOffset < myDraggedRange.getEndOffset()) {
        return false;
      }

      if (myDraggedRange != null) {
        editor.getCaretModel().moveToOffset(editor.mySavedCaretOffsetForDNDUndoHack);
      }

      CommandProcessor.getInstance().executeCommand(editor.myProject, new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              try {
                editor.getSelectionModel().removeSelection();
                final int offset;
                if (myDraggedRange != null) {
                  editor.getCaretModel().moveToOffset(caretOffset);
                  offset = caretOffset;
                }
                else {
                  offset = editor.getCaretModel().getOffset();
                }
                if (editor.getDocument().getRangeGuard(offset, offset) != null) return;

                EditorActionHandler pasteHandler = EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_PASTE);

                editor.putUserData(LAST_PASTED_REGION, null);

                LOG.assertTrue(pasteHandler instanceof EditorTextInsertHandler);

                EditorTextInsertHandler handler = (EditorTextInsertHandler)pasteHandler;
                handler.execute(editor, editor.getDataContext(), new Producer<Transferable>() {
                  @Override
                  public Transferable produce() {
                    return t;
                  }
                });

                TextRange range = editor.getUserData(LAST_PASTED_REGION);
                if (range != null) {
                  editor.getCaretModel().moveToOffset(range.getStartOffset());
                  editor.getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
                }
              }
              catch (Exception exception) {
                LOG.error(exception);
              }
            }
          });
        }
      }, EditorBundle.message("paste.command.name"), DND_COMMAND_KEY, UndoConfirmationPolicy.DEFAULT, editor.getDocument());

      return true;
    }

    public boolean canImport(JComponent comp, DataFlavor[] transferFlavors) {
      Editor editor = getEditor(comp);
      final EditorDropHandler dropHandler = ((EditorImpl)editor).getDropHandler();
      if (dropHandler != null && dropHandler.canHandleDrop(transferFlavors)) {
        return true;
      }
      if (editor.isViewer()) return false;

      int offset = editor.getCaretModel().getOffset();
      if (editor.getDocument().getRangeGuard(offset, offset) != null) return false;

      for (DataFlavor transferFlavor : transferFlavors) {
        if (transferFlavor.equals(DataFlavor.stringFlavor)) return true;
      }

      return false;
    }

    @Nullable
    protected Transferable createTransferable(JComponent c) {
      Editor editor = getEditor(c);
      String s = editor.getSelectionModel().getSelectedText();
      if (s == null) return null;
      int selectionStart = editor.getSelectionModel().getSelectionStart();
      int selectionEnd = editor.getSelectionModel().getSelectionEnd();
      myDraggedRange = editor.getDocument().createRangeMarker(selectionStart, selectionEnd);

      return new StringSelection(s);
    }

    public int getSourceActions(JComponent c) {
      return COPY_OR_MOVE;
    }

    protected void exportDone(final JComponent source, Transferable data, int action) {
      if (data == null) return;

      final Component last = DnDManager.getInstance().getLastDropHandler();

      if (last != null && !(last instanceof EditorComponentImpl)) return;

      final Editor editor = getEditor(source);
      if (action == MOVE && !editor.isViewer()) {
        if (!FileDocumentManager.getInstance().requestWriting(editor.getDocument(), editor.getProject())) {
          return;
        }
        CommandProcessor.getInstance().executeCommand(((EditorImpl)editor).myProject, new Runnable() {
          public void run() {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              public void run() {
                Document doc = editor.getDocument();
                doc.startGuardedBlockChecking();
                try {
                  doc.deleteString(myDraggedRange.getStartOffset(), myDraggedRange.getEndOffset());
                }
                catch (ReadOnlyFragmentModificationException e) {
                  EditorActionManager.getInstance().getReadonlyFragmentModificationHandler(doc).handle(e);
                }
                finally {
                  doc.stopGuardedBlockChecking();
                }
              }
            });
          }
        }, EditorBundle.message("move.selection.command.name"), DND_COMMAND_KEY, UndoConfirmationPolicy.DEFAULT, editor.getDocument());
      }

      myDraggedRange = null;
    }
  }

  class EditorDocumentAdapter implements PrioritizedDocumentListener {
    public void beforeDocumentChange(DocumentEvent e) {
      beforeChangedUpdate(e);
    }

    public void documentChanged(DocumentEvent e) {
      changedUpdate(e);
    }

    public int getPriority() {
      return EditorDocumentPriorities.EDITOR_DOCUMENT_ADAPTER;
    }
  }

  class EditorDocumentBulkUpdateAdapter implements DocumentBulkUpdateListener {
    @Override
    public void updateStarted(Document doc) {
      bulkUpdateStarted();
    }

    @Override
    public void updateFinished(Document doc) {
      bulkUpdateFinished();
    }
  }
  
  @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
  private class EditorSizeContainer {

    /** Holds logical line widths in pixels. */
    private TIntArrayList myLineWidths;

    /** Holds value that indicates if line widths recalculation should be performed. */
    private volatile boolean myIsDirty;

    /** Holds number of the last logical line affected by the last document change. */
    private int myOldEndLine;

    private Dimension mySize;
    private int myMaxWidth = -1;

    public synchronized void reset() {
      int lineCount = getDocument().getLineCount();
      myLineWidths = new TIntArrayList(lineCount + 300);
      int[] values = new int[lineCount];
      Arrays.fill(values, -1);
      myLineWidths.add(values);
      myIsDirty = true;
    }

    @SuppressWarnings({"NonPrivateFieldAccessedInSynchronizedContext"})
    public synchronized void beforeChange(DocumentEvent e) {
      if (myDocument.isInBulkUpdate()) {
        myMaxWidth = mySize != null ? mySize.width : -1;
      }

      myOldEndLine = offsetToLogicalLine(e.getOffset() + e.getOldLength());
    }

    /**
     * Notifies current size container about document content change.
     * <p/>
     * Every change is assumed to be identified by three characteristics - start, ole end and new end lines.
     * <b>Example:</b>
     * <pre>
     * <ol>
     *   <li>
     *      Consider that we have the following document initially:
     *      <pre>
     *        line 1
     *        line 2
     *        line 3
     *      </pre>
     *   </li>
     *   <li>
     *      Let's assume that the user selected the last two lines and typed 'new line' (that effectively removed selected text).
     *      Current document state:
     *      <pre>
     *        line 1
     *        new line
     *      </pre>
     *   </li>
     *   <li>
     *      Current method is expected to be called with the following parameters:
     *          <ul>
     *            <li><b>startLine</b> is 1'</li>
     *            <li><b>oldEndLine</b> is 2'</li>
     *            <li><b>newEndLine</b> is 1'</li>
     *          </ul>
     *   </li>
     * </ol>
     </pre>
     * 
     * @param startLine     logical line that contains changed fragment start offset
     * @param newEndLine    logical line that contains changed fragment end
     * @param oldEndLine    logical line that contained changed fragment end
     */
    public synchronized void update(int startLine, int newEndLine, int oldEndLine) {
      final int lineWidthSize = myLineWidths.size();
      if (lineWidthSize == 0 || myDocument.getTextLength() <= 0) {
        reset();
      }
      else {
        final int min = Math.min(oldEndLine, newEndLine);
        final boolean toAddNewLines = min >= lineWidthSize;

        if (toAddNewLines) {
          final int[] delta = new int[min - lineWidthSize + 1];
          myLineWidths.insert(lineWidthSize, delta);
        }

        for (int i = startLine; i <= min; i++) myLineWidths.set(i, -1);
        if (newEndLine > oldEndLine) {
          int[] delta = new int[newEndLine - oldEndLine];
          Arrays.fill(delta, -1);
          myLineWidths.insert(oldEndLine + 1, delta);
        }
        else if (oldEndLine > newEndLine && !toAddNewLines && newEndLine + 1 < lineWidthSize) {
          myLineWidths.remove(newEndLine + 1, Math.min(oldEndLine, lineWidthSize) - newEndLine - 1);
        }
        myIsDirty = true;
      }
    }

    /**
     * Notifies current container about visual width change of the target logical line.
     * <p/>
     * Please note that there is a possible case that particular logical line is represented in more than one visual lines,
     * hence, this method may be called multiple times with the same logical line argument but different with values. Current
     * container is expected to store max of the given values then.
     * 
     * @param logicalLine     logical line which visual width is changed
     * @param widthInPixels   visual width of the given logical line
     */
    public synchronized void updateLineWidthIfNecessary(int logicalLine, int widthInPixels) {
      if (logicalLine < myLineWidths.size()) {
        int currentWidth = myLineWidths.get(logicalLine);
        if (widthInPixels > currentWidth) {
          myLineWidths.set(logicalLine, widthInPixels);
        }
        if (widthInPixels > myMaxWidth) {
          myMaxWidth = widthInPixels;
        }
      }
    }

    public synchronized void changedUpdate(DocumentEvent e) {
      int startLine = e.getOldLength() == 0 ? myOldEndLine : myDocument.getLineNumber(e.getOffset());
      int newEndLine = e.getNewLength() == 0 ? startLine : myDocument.getLineNumber(e.getOffset() + e.getNewLength());
      int oldEndLine = myOldEndLine;

      update(startLine, newEndLine, oldEndLine);
    }

    @SuppressWarnings({"NonPrivateFieldAccessedInSynchronizedContext", "AssignmentToForLoopParameter"})
    private void validateSizes() {
      if (!myIsDirty) return;

      synchronized (this) {
        if (!myIsDirty) return;
        int lineCount = Math.min(myLineWidths.size(), myDocument.getLineCount());

        if (myMaxWidth != -1 && myDocument.isInBulkUpdate()) {
          mySize = new Dimension(myMaxWidth, getLineHeight() * lineCount);
          myIsDirty = false;
          return;
        }

        final CharSequence text = myDocument.getCharsNoThreadCheck();
        int documentLength = myDocument.getTextLength();
        int x = 0;
        boolean lastLineLengthCalculated = false;
        final int fontSize = myScheme.getEditorFontSize();
        final String fontName = myScheme.getEditorFontName();

        List<? extends SoftWrap> softWraps = getSoftWrapModel().getRegisteredSoftWraps();
        int softWrapsIndex = -1;

        for (int line = 0; line < lineCount; line++) {
          if (myLineWidths.getQuick(line) != -1) continue;
          if (line == lineCount - 1) {
            lastLineLengthCalculated = true;
          }
          
          x = 0;
          int offset = myDocument.getLineStartOffset(line);

          if (offset >= myDocument.getTextLength()) {
            myLineWidths.set(line, 0);
            break;
          }

          if (softWrapsIndex < 0) {
            softWrapsIndex = getSoftWrapModel().getSoftWrapIndex(offset);
            if (softWrapsIndex < 0) {
              softWrapsIndex = -softWrapsIndex - 1;
            }
          }

          int endLine;
          for (endLine=line+1; endLine<lineCount;endLine++) {
            if (myLineWidths.getQuick(endLine) != -1) {
              break;
            }
          }
          int endOffset = endLine==lineCount ? documentLength : myDocument.getLineEndOffset(endLine);
          for (
            FoldRegion region = myFoldingModel.getCollapsedRegionAtOffset(endOffset);
            region != null && endOffset < myDocument.getTextLength();
            region = myFoldingModel.getCollapsedRegionAtOffset(endOffset))
          {
            final int lineNumber = myDocument.getLineNumber(region.getEndOffset());
            endOffset = myDocument.getLineEndOffset(lineNumber);
          }
          if (endOffset > myDocument.getTextLength()) {
            break;
          } 
          
          IterationState state = new IterationState(EditorImpl.this, offset, endOffset, false);
          int fontType = state.getMergedAttributes().getFontType();

          int maxPreviousSoftWrappedWidth = -1;

          while (offset < documentLength && line < lineCount) {
            char c = text.charAt(offset);
            if (offset >= state.getEndOffset()) {
              state.advance();
              fontType = state.getMergedAttributes().getFontType();
            }

            while (softWrapsIndex < softWraps.size() && line < lineCount) {
              SoftWrap softWrap = softWraps.get(softWrapsIndex);
              if (softWrap.getStart() > offset) {
                break;
              }
              softWrapsIndex++;
              if (softWrap.getStart() == offset) {
                maxPreviousSoftWrappedWidth = Math.max(maxPreviousSoftWrappedWidth, x);
                x = softWrap.getIndentInPixels();
              }
            }

            FoldRegion collapsed = state.getCurrentFold();
            if (collapsed != null) {
              String placeholder = collapsed.getPlaceholderText();
              for (int i = 0; i < placeholder.length(); i++) {
                x += EditorUtil.charWidth(placeholder.charAt(i), fontType, EditorImpl.this);
              }
              offset = collapsed.getEndOffset();
              line = myDocument.getLineNumber(offset);
            }
            else {
              if (c == '\t') {
                x = EditorUtil.nextTabStop(x, EditorImpl.this);
                offset++;
              }
              else {
                if (c == '\n') {
                  myLineWidths.set(line, Math.max(x, maxPreviousSoftWrappedWidth));
                  if (line + 1 >= lineCount || myLineWidths.getQuick(line + 1) != -1) break;
                  offset++;
                  x = 0;
                  //noinspection AssignmentToForLoopParameter
                  line++;
                  if (line == lineCount - 1) {
                     lastLineLengthCalculated = true;
                  }
                }
                else {
                  x += ComplementaryFontsRegistry.getFontAbleToDisplay(c, fontSize, fontType, fontName).charWidth(c, myEditorComponent);
                  offset++;
                }
              }
            }
          }
        }

        if (lineCount > 0 && lastLineLengthCalculated) {
          myLineWidths.set(lineCount - 1,
                           x);    // Last line can be non-zero length and won't be caught by in-loop procedure since latter only react on \n's
        }

        // There is a following possible situation:
        //   1. Big document is opened at editor;
        //   2. Soft wraps are calculated for the current visible area;
        //   2. The user scrolled down;
        //   3. The user significantly reduced visible area width (say, reduced it twice);
        //   4. Soft wraps are calculated for the current visible area;
        // We need to consider only the widths for the logical lines that are completely shown at the current visible area then.
        // I.e. we shouldn't use widths of the lines that are not shown for max width calculation because previous widths are calculated
        // for another visible area width.
        int startToUse = 0;
        int endToUse = Math.min(lineCount, myLineWidths.size());
        if (endToUse > 0 && getSoftWrapModel().isSoftWrappingEnabled()) {
          Rectangle visibleArea = getScrollingModel().getVisibleArea();
          startToUse = yPositionToLogicalLine(visibleArea.getLocation().y);
          endToUse = Math.min(endToUse, yPositionToLogicalLine(visibleArea.y + visibleArea.height));
        }
        int maxWidth = 0;
        for (int i = startToUse; i < endToUse; i++) {
          maxWidth = Math.max(maxWidth, myLineWidths.getQuick(i));
        }

        mySize = new Dimension(maxWidth, getLineHeight() * Math.max(getVisibleLineCount(), 1));

        myIsDirty = false;
      }
    }

    private Dimension getContentSize() {
      validateSizes();
      return mySize;
    }

    private int getContentHeight() {
      return getVisibleLineCount() * getLineHeight();
    }
  }

  @NotNull
  public EditorGutter getGutter() {
    return getGutterComponentEx();
  }

  public int calcColumnNumber(@NotNull CharSequence text, int start, int offset, int tabSize) {
    IterationState state = new IterationState(this, start, start+offset, false);
    int fontType = state.getMergedAttributes().getFontType();
    int column = 0;
    int x = 0;
    int spaceSize = EditorUtil.getSpaceWidth(fontType, this);
    for (int i = start; i < offset; i++) {
      if (i >= state.getEndOffset()) {
        state.advance();
        fontType = state.getMergedAttributes().getFontType();
      }

      SoftWrap softWrap = getSoftWrapModel().getSoftWrap(i);
      if (softWrap != null) {
        column++; // For 'after soft wrap' drawing.
        x = getSoftWrapModel().getMinDrawingWidthInPixels(SoftWrapDrawingType.AFTER_SOFT_WRAP);
      }

      char c = text.charAt(i);
      if (c == '\t') {
        int prevX = x;
        x = EditorUtil.nextTabStop(x, this);
        column += EditorUtil.columnsNumber(c, x, prevX, spaceSize);
      }
      else {
        x += EditorUtil.charWidth(c, fontType, this);
        column++;
      }
    }

    return column;
  }

  public void putInfo(Map<String, String> info) {
    final VisualPosition visual = getCaretModel().getVisualPosition();
    info.put("caret", visual.getLine() + ":" + visual.getColumn());
  }

  private class MyScrollPane extends JBScrollPane {


    private MyScrollPane() {
      setViewportBorder(new EmptyBorder(0, 0, 0, 0));
    }

    protected void processMouseWheelEvent(MouseWheelEvent e) {
      if (mySettings.isWheelFontChangeEnabled()) {
        if (EditorUtil.isChangeFontSize(e)) {
          setFontSize(myScheme.getEditorFontSize() - e.getWheelRotation());
          return;
        }
      }

      super.processMouseWheelEvent(e);
    }

    @Override
    public JScrollBar createVerticalScrollBar() {
      return new MyScrollBar(Adjustable.VERTICAL);
    }

    @Override
    public void setupCorners() {
      super.setupCorners();
      setCorner(getVerticalScrollbarOrientation() == EditorEx.VERTICAL_SCROLLBAR_LEFT ?
                LOWER_RIGHT_CORNER :
                LOWER_LEFT_CORNER, new JPanel() {
        @Override
        public void paint(Graphics g) {
          final Rectangle bounds = getBounds();
          int width = bounds.width;
          int height = bounds.height;

          g.setColor(ButtonlessScrollBarUI.TRACK_BACKGROUND);
          g.fillRect(0, 0, width, height);

          int shortner = 0;
          if (myGutterComponent.isFoldingOutlineShown()) {
            shortner = myGutterComponent.getFoldingAreaWidth() / 2;
          }

          g.setColor(myGutterComponent.getBackground());
          g.fillRect(0, 0, width - shortner, height);

          g.setColor(ButtonlessScrollBarUI.TRACK_BORDER);
          g.drawLine(width - 1 - shortner, 0, width - 1 - shortner, height);
          g.drawLine(0, 0, width - 1, 0);
        }
      });
    }
  }

  private class MyHeaderPanel extends JPanel {
    private int myOldHeight = 0;

    private MyHeaderPanel() {
      super(new BorderLayout());
    }

    public void revalidate() {
      myOldHeight = getHeight();
      super.revalidate();
    }

    protected void validateTree() {
      int height = myOldHeight;
      super.validateTree();
      height -= getHeight();

      if (height != 0) {
        myVerticalScrollBar.setValue(myVerticalScrollBar.getValue() - height);
      }
      myOldHeight = getHeight();
    }
  }

  private class MyTextDrawingCallback implements TextDrawingCallback {
    @Override
    public void drawChars(Graphics g, char[] data, int start, int end, int x, int y, Color color, FontInfo fontInfo) {
      drawCharsCached(g, data, start, end, x, y, fontInfo, color);
    }
  }
}
