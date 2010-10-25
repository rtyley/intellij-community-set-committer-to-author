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

package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.completion.CompletionLookupArranger;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.codeInsight.lookup.*;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.ListScrollingUtil;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBList;
import com.intellij.ui.plaf.beg.BegPopupMenuBorder;
import com.intellij.ui.popup.PopupIcons;
import com.intellij.util.CollectConsumer;
import com.intellij.util.SmartList;
import com.intellij.util.containers.SortedList;
import com.intellij.util.ui.AsyncProcessIcon;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

public class LookupImpl extends LightweightHint implements Lookup, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.lookup.impl.LookupImpl");
  private static final int MAX_PREFERRED_COUNT = 5;

  private static final LookupItem EMPTY_LOOKUP_ITEM = LookupItem.fromString("preselect");

  private final Project myProject;
  private final Editor myEditor;

  private int myMinPrefixLength;
  private int myPreferredItemsCount;
  private long myShownStamp = -1;
  private String myInitialPrefix;
  private LookupArranger myArranger;

  private RangeMarker myLookupStartMarker;
  private final JList myList = new JBList(new DefaultListModel());
  private final LookupCellRenderer myCellRenderer;
  private Boolean myPositionedAbove = null;

  private final ArrayList<LookupListener> myListeners = new ArrayList<LookupListener>();

  private boolean myShown = false;
  private boolean myDisposed = false;
  private boolean myHidden = false;
  private LookupElement myPreselectedItem = EMPTY_LOOKUP_ITEM;
  private String mySelectionInvariant = null;
  private boolean mySelectionTouched;
  private boolean myFocused = true;
  private String myAdditionalPrefix = "";
  private final AsyncProcessIcon myProcessIcon = new AsyncProcessIcon("Completion progress");
  private volatile boolean myCalculating;
  private final JLabel myAdComponent;
  private volatile String myAdText;
  private volatile int myLookupWidth = 50;
  private static final int LOOKUP_HEIGHT = Integer.getInteger("idea.lookup.height", 11).intValue();
  private boolean myReused;
  private boolean myChangeGuard;
  private LookupModel myModel = new LookupModel();

  public LookupImpl(Project project, Editor editor, @NotNull LookupArranger arranger){
    super(new JPanel(new BorderLayout()));
    setForceShowAsPopup(true);
    myProject = project;
    myEditor = editor;

    myProcessIcon.setVisible(false);
    myCellRenderer = new LookupCellRenderer(this);
    myList.setCellRenderer(myCellRenderer);

    myList.setFocusable(false);

    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myList.setBackground(LookupCellRenderer.BACKGROUND_COLOR);

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myList);
    scrollPane.setViewportBorder(new EmptyBorder(0, 0, 0, 0));
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    getComponent().add(scrollPane, BorderLayout.NORTH);
    scrollPane.setBorder(null);

    JPanel bottomPanel = new JPanel(new BorderLayout());

    bottomPanel.add(myProcessIcon, BorderLayout.EAST);
    myAdComponent = HintUtil.createAdComponent(null);
    bottomPanel.add(myAdComponent, BorderLayout.CENTER);
    getComponent().add(bottomPanel, BorderLayout.SOUTH);
    getComponent().setBorder(new BegPopupMenuBorder());

    final ListModel model = myList.getModel();
    addEmptyItem((DefaultListModel)model);
    updateListHeight(model);

    setArranger(arranger);

    addListeners();
  }

  public void setArranger(LookupArranger arranger) {
    myArranger = arranger;
    myModel.setArranger(arranger);
  }

  public boolean isFocused() {
    return myFocused;
  }

  public void setFocused(boolean focused) {
    myFocused = focused;
  }

  public boolean isCalculating() {
    return myCalculating;
  }

  public void setCalculating(final boolean calculating) {
    myCalculating = calculating;
    myProcessIcon.setVisible(calculating);
    if (calculating) {
      myProcessIcon.resume();
    } else {
      myProcessIcon.suspend();
    }
  }

  public int getPreferredItemsCount() {
    return myPreferredItemsCount;
  }

  public void markSelectionTouched() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      ApplicationManager.getApplication().assertIsDispatchThread();
    }
    mySelectionTouched = true;
    myPreselectedItem = null;
  }

  @TestOnly
  public void resort() {
    mySelectionTouched = false;
    myPreselectedItem = EMPTY_LOOKUP_ITEM;
    final List<LookupElement> items = myModel.getItems();
    myModel.clearItems();
    for (final LookupElement item : items) {
      addItem(item);
    }
    updateList();
  }

  public void addItem(LookupElement item) {
    myModel.addItem(item);

    updateLookupWidth(item);
    updateItemActions(item);
  }

  public void updateLookupWidth(LookupElement item) {
    final LookupElementPresentation presentation = renderItemApproximately(item);
    int maxWidth = myCellRenderer.updateMaximumWidth(presentation);
    myLookupWidth = Math.max(maxWidth, myLookupWidth);

    myModel.setItemPresentation(item, presentation);

  }

  public void updateItemActions(LookupElement item) {
    final CollectConsumer<LookupElementAction> consumer = new CollectConsumer<LookupElementAction>();
    for (LookupActionProvider provider : LookupActionProvider.EP_NAME.getExtensions()) {
      provider.fillActions(item, this, consumer);
    }
    myModel.setItemActions(item, consumer.getResult());
  }

  public Collection<LookupElementAction> getActionsFor(LookupElement element) {
    return myModel.getActionsFor(element);
  }

  public int getMinPrefixLength() {
    return myMinPrefixLength;
  }

  public JList getList() {
    return myList;
  }

  public List<LookupElement> getItems() {
    final ArrayList<LookupElement> result = new ArrayList<LookupElement>();
    final Object[] objects;
    synchronized (myList) {
      objects = ((DefaultListModel)myList.getModel()).toArray();
    }
    for (final Object object : objects) {
      if (!(object instanceof EmptyLookupItem)) {
        result.add((LookupElement) object);
      }
    }
    return result;
  }

  public void setAdvertisementText(@Nullable String text) {
    myAdText = text;
  }

  public String getAdvertisementText() {
    return myAdText;
  }


  public String getAdditionalPrefix() {
    return myAdditionalPrefix;
  }

  public void setAdditionalPrefix(final String additionalPrefix) {
    myAdditionalPrefix = additionalPrefix;
    myInitialPrefix = null;
    markSelectionTouched();
    refreshUi();
  }

  private void updateList() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      ApplicationManager.getApplication().assertIsDispatchThread();
    }

    if (myReused) {
      myModel.collectGarbage();
      myReused = false;
    }

    final Pair<LinkedHashSet<LookupElement>,List<List<LookupElement>>> snapshot = myModel.getModelSnapshot();
    final LinkedHashSet<LookupElement> items = snapshot.first;
    checkMinPrefixLengthChanges(items);

    LookupElement oldSelected = mySelectionTouched ? (LookupElement)myList.getSelectedValue() : null;
    String oldInvariant = mySelectionInvariant;
    boolean hasExactPrefixes;
    final boolean hasPreselectedItem;
    final boolean hasItems;
    DefaultListModel model = (DefaultListModel)myList.getModel();
    final LookupElement preselectedItem = myPreselectedItem;
    synchronized (myList) {
      model.clear();

      Set<LookupElement> firstItems = new THashSet<LookupElement>();

      hasExactPrefixes = addExactPrefixItems(model, firstItems, items);
      addMostRelevantItems(model, firstItems, snapshot.second);
      hasPreselectedItem = items.contains(preselectedItem) && addPreselectedItem(model, firstItems, preselectedItem);
      myPreferredItemsCount = firstItems.size();

      addRemainingItemsLexicographically(model, firstItems, items);

      hasItems = model.getSize() != 0;
      if (!hasItems) {
        addEmptyItem(model);
      }
    }

    updateListHeight(model);

    myAdComponent.setPreferredSize(null);
    myAdComponent.setText(myAdText);
    if (myAdText != null) {
      myAdComponent.setPreferredSize(new Dimension(myAdComponent.getPreferredSize().width, myProcessIcon.getPreferredSize().height));
    }

    if (hasItems) {
      myList.setFixedCellWidth(Math.max(myLookupWidth, myAdComponent.getPreferredSize().width));

      if (isFocused() && !hasExactPrefixes) {
        restoreSelection(oldSelected, hasPreselectedItem, oldInvariant);
      }
      else {
        ListScrollingUtil.selectItem(myList, 0);
      }
    }
  }

  private void checkMinPrefixLengthChanges(Collection<LookupElement> items) {
    int minPrefixLength = items.isEmpty() ? 0 : Integer.MAX_VALUE;
    for (final LookupElement item : items) {
      minPrefixLength = Math.min(item.getPrefixMatcher().getPrefix().length(), minPrefixLength);
    }

    if (myMinPrefixLength != minPrefixLength) {
      myLookupStartMarker = null;
    }
    myMinPrefixLength = minPrefixLength;
  }

  private void restoreSelection(@Nullable LookupElement oldSelected, boolean choosePreselectedItem, @Nullable String oldInvariant) {
    if (oldSelected != null) {
      if (oldSelected.isValid() && ListScrollingUtil.selectItem(myList, oldSelected)) {
        return;
      }

      if (oldInvariant != null) {
        for (LookupElement element : getItems()) {
          if (oldInvariant.equals(myModel.getItemPresentationInvariant(element)) && ListScrollingUtil.selectItem(myList, element)) {
            return;
          }
        }
      }
    }

    if (choosePreselectedItem) {
      ListScrollingUtil.selectItem(myList, myPreselectedItem);
    } else {
      selectMostPreferableItem();
    }

    if (myPreselectedItem != null) {
      myPreselectedItem = getCurrentItem();
    }
  }

  private void updateListHeight(ListModel model) {
    myList.setFixedCellHeight(myCellRenderer.getListCellRendererComponent(myList, model.getElementAt(0), 0, false, false).getPreferredSize().height);

    myList.setVisibleRowCount(Math.min(model.getSize(), LOOKUP_HEIGHT));
  }

  private void addEmptyItem(DefaultListModel model) {
    LookupItem<String> item = new EmptyLookupItem(myCalculating ? " " : LangBundle.message("completion.no.suggestions"));
    item.setPrefixMatcher(new CamelHumpMatcher(""));
    if (!myCalculating) {
      myList.setFixedCellWidth(Math.max(myCellRenderer.updateMaximumWidth(renderItemApproximately(item)), myLookupWidth));
    }

    model.addElement(item);
  }

  private static LookupElementPresentation renderItemApproximately(LookupElement item) {
    final LookupElementPresentation p = new LookupElementPresentation();
    item.renderElement(p);
    return p;
  }

  private void addRemainingItemsLexicographically(DefaultListModel model, Set<LookupElement> firstItems, Collection<LookupElement> allItems) {
    for (LookupElement item : allItems) {
      if (!firstItems.contains(item) && prefixMatches(item)) {
        model.addElement(item);
      }
    }
  }

  private boolean addPreselectedItem(DefaultListModel model, Set<LookupElement> firstItems, @Nullable final LookupElement preselectedItem) {
    final boolean hasPreselectedItem = !mySelectionTouched && preselectedItem != EMPTY_LOOKUP_ITEM && preselectedItem != null;
    if (hasPreselectedItem && !firstItems.contains(preselectedItem)) {
      firstItems.add(preselectedItem);
      model.addElement(preselectedItem);
    }
    return hasPreselectedItem;
  }

  private void addMostRelevantItems(DefaultListModel model, Set<LookupElement> firstItems, final Collection<List<LookupElement>> sortedItems) {
    for (final List<LookupElement> elements : sortedItems) {
      final List<LookupElement> suitable = new SmartList<LookupElement>();
      for (final LookupElement item : elements) {
        if (!firstItems.contains(item) && prefixMatches(item)) {
          suitable.add(item);
        }
      }

      if (firstItems.size() + suitable.size() > MAX_PREFERRED_COUNT) break;
      for (final LookupElement item : suitable) {
        firstItems.add(item);
        model.addElement(item);
      }
    }
  }

  private boolean addExactPrefixItems(DefaultListModel model, Set<LookupElement> firstItems, final Collection<LookupElement> elements) {
    List<LookupElement> sorted = new SortedList<LookupElement>(new Comparator<LookupElement>() {
      public int compare(LookupElement o1, LookupElement o2) {
        //noinspection unchecked
        return myArranger.getRelevance(o1).compareTo(myArranger.getRelevance(o2));
      }
    });
    for (final LookupElement item : elements) {
      if (isExactPrefixItem(item)) {
        sorted.add(item);

      }
    }
    for (final LookupElement item : sorted) {
      model.addElement(item);
      firstItems.add(item);
    }

    return !firstItems.isEmpty();
  }

  private boolean isExactPrefixItem(LookupElement item) {
    return item.getAllLookupStrings().contains(item.getPrefixMatcher().getPrefix() + myAdditionalPrefix);
  }

  private boolean prefixMatches(final LookupElement item) {
    if (myAdditionalPrefix.length() == 0) return item.isPrefixMatched();

    return item.getPrefixMatcher().cloneWithPrefix(item.getPrefixMatcher().getPrefix() + myAdditionalPrefix).prefixMatches(item);
  }

  /**
   * @return point in layered pane coordinate system.
   */
  public Point calculatePosition(){
    Dimension dim = getComponent().getPreferredSize();
    int lookupStart = getLookupStart();
    if (lookupStart < 0) {
      LOG.error(lookupStart + "; minprefix=" + myMinPrefixLength + "; offset=" + myEditor.getCaretModel().getOffset() + "; element=" +
                getPsiElement());
    }

    LogicalPosition pos = myEditor.offsetToLogicalPosition(lookupStart);
    Point location = myEditor.logicalPositionToXY(pos);
    location.y += myEditor.getLineHeight();
    JComponent editorComponent = myEditor.getComponent();
    JComponent internalComponent = myEditor.getContentComponent();
    final JRootPane rootPane = editorComponent.getRootPane();
    if (rootPane == null) {
      LOG.error(myArranger);
    }
    JLayeredPane layeredPane = rootPane.getLayeredPane();
    Point layeredPanePoint=SwingUtilities.convertPoint(internalComponent,location, layeredPane);
    layeredPanePoint.x -= myCellRenderer.getIconIndent();
    layeredPanePoint.x -= getComponent().getInsets().left;
    if (dim.width > layeredPane.getWidth()){
      dim.width = layeredPane.getWidth();
    }
    int wshift = layeredPane.getWidth() - (layeredPanePoint.x + dim.width);
    if (wshift < 0){
      layeredPanePoint.x += wshift;
    }

    int shiftLow = layeredPane.getHeight() - (layeredPanePoint.y + dim.height);
    int shiftHigh = layeredPanePoint.y - dim.height;
    if (!isPositionedAboveCaret()) {
      myPositionedAbove = shiftLow < 0 && shiftLow < shiftHigh ? Boolean.TRUE : Boolean.FALSE;
    }
    if (isPositionedAboveCaret()) {
      layeredPanePoint.y -= dim.height + myEditor.getLineHeight();
      if (pos.line == 0) {
        layeredPanePoint.y += 1;
        //otherwise the lookup won't intersect with the editor and every editor's resize (e.g. after typing in console) will close the lookup
      }
    }
    return layeredPanePoint;
  }

  public void finishLookup(final char completionChar) {
    if (myShownStamp > 0 && System.currentTimeMillis() - myShownStamp < 42 && !ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }

    final LookupElement item = (LookupElement)myList.getSelectedValue();
    doHide(false, true);
    if (item == null ||
        item instanceof EmptyLookupItem ||
        item.getObject() instanceof DeferredUserLookupValue &&
        item.as(LookupItem.class) != null &&
        !((DeferredUserLookupValue)item.getObject()).handleUserSelection(item.as(LookupItem.class), myProject)) {
      fireItemSelected(null, completionChar);
      return;
    }

    final PsiFile file = getPsiFile();
    if (file != null && !WriteCommandAction.ensureFilesWritable(myProject, Arrays.asList(file))) {
      return;
    }

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        EditorModificationUtil.deleteSelectedText(myEditor);
        final int caretOffset = myEditor.getCaretModel().getOffset();
        final String prefix = item.getPrefixMatcher().getPrefix();
        int lookupStart = caretOffset - prefix.length() - myAdditionalPrefix.length();

        final String lookupString = item.getLookupString();
        if (!StringUtil.startsWithConcatenationOf(lookupString, prefix, myAdditionalPrefix)) {
          FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.camelHumps");
        }

        myEditor.getDocument().replaceString(lookupStart, caretOffset, lookupString);

        int offset = lookupStart + lookupString.length();
        myEditor.getCaretModel().moveToOffset(offset);
        myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        myEditor.getSelectionModel().removeSelection();
      }
    });

    fireItemSelected(item, completionChar);
  }

  public int getLookupStart() {
    if (myLookupStartMarker == null) {
      final int start = calcLookupStart();
      myLookupStartMarker = myEditor.getDocument().createRangeMarker(start, start);
      myLookupStartMarker.setGreedyToLeft(true);
    }

    return myLookupStartMarker.getStartOffset();
  }

  public void performGuardedChange(Runnable change) {
    assert !myChangeGuard;
    myChangeGuard = true;
    try {
      change.run();
    }
    finally {
      myChangeGuard = false;
    }
  }

  public boolean isShown() {
    return myShown;
  }

  public void show(){
    ApplicationManager.getApplication().assertIsDispatchThread();
    LOG.assertTrue(!myDisposed, disposeTrace);
    LOG.assertTrue(!myShown);
    myShown = true;

    if (ApplicationManager.getApplication().isUnitTestMode()) return;

    getComponent().setBorder(null);

    Point p = calculatePosition();
    HintManagerImpl hintManager = HintManagerImpl.getInstanceImpl();
    hintManager.showEditorHint(this, myEditor, p, HintManagerImpl.HIDE_BY_ESCAPE | HintManagerImpl.UPDATE_BY_SCROLLING, 0, false);

    myShownStamp = System.currentTimeMillis();
  }

  private void addListeners() {
    myEditor.getDocument().addDocumentListener(new DocumentAdapter() {
      public void documentChanged(DocumentEvent e) {
        if (!myChangeGuard) {
          hide();
        }
      }
    }, this);

    final CaretListener caretListener = new CaretListener() {
      public void caretPositionChanged(CaretEvent e){
        if (!myChangeGuard) {
          hide();
        }
      }
    };
    final SelectionListener selectionListener = new SelectionListener() {
      public void selectionChanged(final SelectionEvent e) {
        if (!myChangeGuard) {
          hide();
        }
      }
    };
    final EditorMouseListener mouseListener = new EditorMouseAdapter() {
      public void mouseClicked(EditorMouseEvent e){
        e.consume();
        hide();
      }
    };

    myEditor.getCaretModel().addCaretListener(caretListener);
    myEditor.getSelectionModel().addSelectionListener(selectionListener);
    myEditor.addEditorMouseListener(mouseListener);
    Disposer.register(this, new Disposable() {
      @Override
      public void dispose() {
        myEditor.getCaretModel().removeCaretListener(caretListener);
        myEditor.getSelectionModel().removeSelectionListener(selectionListener);
        myEditor.removeEditorMouseListener(mouseListener);
      }
    });

    myList.addListSelectionListener(new ListSelectionListener() {
      private LookupElement oldItem = null;

      public void valueChanged(ListSelectionEvent e){
        LookupElement item = getCurrentItem();
        if (oldItem != item) {
          mySelectionInvariant = item == null ? null : myModel.getItemPresentationInvariant(item);
          fireCurrentItemChanged(item);
        }
        oldItem = item;
      }
    });

    myList.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e){
        setFocused(true);

        final Point point = e.getPoint();
        final int i = myList.locationToIndex(point);
        if (i >= 0) {
          final LookupElement selected = (LookupElement)myList.getModel().getElementAt(i);
          if (selected != null &&
              e.getClickCount() == 1 &&
              point.x >= myList.getCellBounds(i, i).width - PopupIcons.EMPTY_ICON.getIconWidth() &&
              ShowLookupActionsHandler.showItemActions(LookupImpl.this, selected)) {
            return;
          }
        }

        if (e.getClickCount() == 2){
          CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
            public void run() {
              finishLookup(NORMAL_SELECT_CHAR);
            }
          }, "", null);
        }
      }
    });
  }

  private int calcLookupStart() {
    int offset = myEditor.getSelectionModel().hasSelection()
                 ? myEditor.getSelectionModel().getSelectionStart()
                 : myEditor.getCaretModel().getOffset();
    return Math.max(offset - myMinPrefixLength - myAdditionalPrefix.length(), 0);
  }

  private void selectMostPreferableItem() {
    final List<LookupElement> sortedItems = getItems();
    final int index = doSelectMostPreferableItem(sortedItems);
    myList.setSelectedIndex(index);

    if (index >= 0 && index < myList.getModel().getSize()){
      ListScrollingUtil.selectItem(myList, index);
    }
    else if (!sortedItems.isEmpty()) {
      ListScrollingUtil.selectItem(myList, 0);
    }
  }

  @Nullable
  public LookupElement getCurrentItem(){
    LookupElement item = (LookupElement)myList.getSelectedValue();
    return item instanceof EmptyLookupItem ? null : item;
  }

  public void setCurrentItem(LookupElement item){
    ListScrollingUtil.selectItem(myList, item);
  }

  public void addLookupListener(LookupListener listener){
    myListeners.add(listener);
  }

  public void removeLookupListener(LookupListener listener){
    myListeners.remove(listener);
  }

  public Rectangle getCurrentItemBounds(){
    int index = myList.getSelectedIndex();
    Rectangle itmBounds = myList.getCellBounds(index, index);
    if (itmBounds == null){
      return null;
    }
    Point layeredPanePoint=SwingUtilities.convertPoint(myList,itmBounds.x,itmBounds.y,getComponent());
    itmBounds.x = layeredPanePoint.x;
    itmBounds.y = layeredPanePoint.y;
    return itmBounds;
  }

  public void fireItemSelected(final LookupElement item, char completionChar){
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    if (item != null) {
      myArranger.itemSelected(item, this);
    }

    if (!myListeners.isEmpty()){
      LookupEvent event = new LookupEvent(this, item, completionChar);
      LookupListener[] listeners = myListeners.toArray(new LookupListener[myListeners.size()]);
      for (LookupListener listener : listeners) {
        try {
          listener.itemSelected(event);
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
    }
  }

  private void fireLookupCanceled(final boolean explicitly) {
    if (!myListeners.isEmpty()){
      LookupEvent event = new LookupEvent(this, explicitly);
      LookupListener[] listeners = myListeners.toArray(new LookupListener[myListeners.size()]);
      for (LookupListener listener : listeners) {
        try {
          listener.lookupCanceled(event);
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
    }
  }

  private void fireCurrentItemChanged(LookupElement item){
    if (!myListeners.isEmpty()){
      LookupEvent event = new LookupEvent(this, item, (char)0);
      LookupListener[] listeners = myListeners.toArray(new LookupListener[myListeners.size()]);
      for (LookupListener listener : listeners) {
        listener.currentItemChanged(event);
      }
    }
  }

  private static int divideString(String lookupString, PrefixMatcher matcher) {
    for (int i = matcher.getPrefix().length(); i <= lookupString.length(); i++) {
      if (matcher.prefixMatches(lookupString.substring(0, i))) {
        return i;
      }
    }
    return -1;
  }

  public boolean fillInCommonPrefix(boolean explicitlyInvoked) {
    if (explicitlyInvoked) {
      setFocused(true);
    }

    if (explicitlyInvoked && myCalculating) return false;
    if (!explicitlyInvoked && mySelectionTouched) return false;

    ListModel listModel = myList.getModel();
    if (listModel.getSize() <= 1) return false;

    if (listModel.getSize() == 0) return false;

    final LookupElement firstItem = (LookupElement)listModel.getElementAt(0);
    if (listModel.getSize() == 1 && firstItem instanceof EmptyLookupItem) return false;

    final PrefixMatcher firstItemMatcher = firstItem.getPrefixMatcher();
    final String oldPrefix = firstItemMatcher.getPrefix();
    final String presentPrefix = oldPrefix + myAdditionalPrefix;
    final PrefixMatcher matcher = firstItemMatcher.cloneWithPrefix(presentPrefix);
    String lookupString = firstItem.getLookupString();
    int div = divideString(lookupString, matcher);
    if (div < 0) return false;

    String beforeCaret = lookupString.substring(0, div);
    String afterCaret = lookupString.substring(div);


    for (int i = 1; i < listModel.getSize(); i++) {
      LookupElement item = (LookupElement)listModel.getElementAt(i);
      if (!oldPrefix.equals(item.getPrefixMatcher().getPrefix())) return false;

      lookupString = item.getLookupString();
      div = divideString(lookupString, item.getPrefixMatcher().cloneWithPrefix(presentPrefix));
      if (div < 0) return false;

      String _afterCaret = lookupString.substring(div);
      if (beforeCaret != null) {
        if (div != beforeCaret.length() || !lookupString.startsWith(beforeCaret)) {
          beforeCaret = null;
        }
      }

      while (afterCaret.length() > 0) {
        if (_afterCaret.startsWith(afterCaret)) {
          break;
        }
        afterCaret = afterCaret.substring(0, afterCaret.length() - 1);
      }
      if (afterCaret.length() == 0) return false;
    }

    if (myAdditionalPrefix.length() == 0 && myInitialPrefix == null && !explicitlyInvoked) {
      myInitialPrefix = presentPrefix;
    }
    else {
      myInitialPrefix = null;
    }

    final String finalBeforeCaret = beforeCaret;
    final String finalAfterCaret = afterCaret;
    Runnable runnable = new Runnable() {
      public void run() {
        doInsertCommonPrefix(presentPrefix, finalBeforeCaret, finalAfterCaret);
      }
    };
    performGuardedChange(runnable);
    return true;
  }

  private void doInsertCommonPrefix(String presentPrefix, String beforeCaret, String afterCaret) {
    EditorModificationUtil.deleteSelectedText(myEditor);
    int offset = myEditor.getCaretModel().getOffset();
    if (beforeCaret != null) { // correct case, expand camel-humps
      final int start = offset - presentPrefix.length();
      myAdditionalPrefix = "";
      myEditor.getDocument().replaceString(start, offset, beforeCaret);
      presentPrefix = beforeCaret;
    }

    offset = myEditor.getCaretModel().getOffset();
    myEditor.getDocument().insertString(offset, afterCaret);

    final String newPrefix = presentPrefix + afterCaret;
    myModel.retainMatchingItems(newPrefix);
    myAdditionalPrefix = "";

    offset += afterCaret.length();
    myEditor.getCaretModel().moveToOffset(offset);
    refreshUi();
  }

  @Nullable
  public PsiFile getPsiFile() {
    return PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument());
  }

  public boolean isCompletion() {
    return myArranger instanceof CompletionLookupArranger;
  }

  public PsiElement getPsiElement() {
    PsiFile file = getPsiFile();
    if (file == null) return null;

    int offset = getLookupStart();
    if (offset > 0) return file.findElementAt(offset - 1);

    return file.findElementAt(0);
  }

  public Editor getEditor() {
    return myEditor;
  }

  public boolean isPositionedAboveCaret(){
    return myPositionedAbove != null && myPositionedAbove.booleanValue();
  }

  public void hide(){
    hideLookup(true);
  }

  public void hideLookup(boolean explicitly) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    if (myDisposed) return;

    doHide(true, explicitly);
  }

  private void doHide(final boolean fireCanceled, final boolean explicitly) {
    assert !myDisposed : disposeTrace;
    myHidden = true;

    try {
      super.hide();

      Disposer.dispose(this);
    }
    catch (Throwable e) {
      LOG.error(e);
    }

    assert myDisposed;

    if (fireCanceled) {
      fireLookupCanceled(explicitly);
    }
  }

  public void restorePrefix() {
    if (myInitialPrefix != null) {
      myEditor.getDocument().replaceString(getLookupStart(), myEditor.getCaretModel().getOffset(), myInitialPrefix);
    }
  }

  String disposeTrace = null;

  public void dispose() {
    assert ApplicationManager.getApplication().isDispatchThread();
    assert myHidden;
    assert !myDisposed : disposeTrace;

    Disposer.dispose(myProcessIcon);

    myDisposed = true;
    disposeTrace = DebugUtil.currentStackTrace();
  }

  private int doSelectMostPreferableItem(List<LookupElement> items) {
    if (items.isEmpty()) {
      return -1;
    }

    if (items.size() == 1) {
      return 0;
    }

    for (int i = 0; i < items.size(); i++) {
      LookupElement item = items.get(i);
      if (isExactPrefixItem(item)) {
        return i;
      }
    }

    final int index = myArranger.suggestPreselectedItem(items);
    assert index >= 0 && index < items.size();
    return index;
  }

  public void refreshUi() {
    updateList();

    if (isVisible() && !ApplicationManager.getApplication().isUnitTestMode()) {
      if (myEditor.getComponent().getRootPane() == null) {
        LOG.error("Null root pane");
      }

      Point point = calculatePosition();
      updateBounds(point.x,point.y);

      HintManagerImpl.adjustEditorHintPosition(this, myEditor, point);
    }
  }

  public LookupArranger getArranger() {
    return myArranger;
  }

  public void markReused() {
    myReused = true;
    myModel.clearItems();
    setAdvertisementText(null);
    myAdditionalPrefix = "";
    myPreselectedItem = null;
  }

  boolean isDisposed() {
    return myDisposed;
  }
}
