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

package com.intellij.find.actions;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.find.FindManager;
import com.intellij.find.findUsages.AbstractFindUsagesDialog;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesManager;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.SearchScope;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.SpeedSearchBase;
import com.intellij.ui.TableScrollingUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.*;
import com.intellij.usages.impl.GroupNode;
import com.intellij.usages.impl.NullUsage;
import com.intellij.usages.impl.UsageNode;
import com.intellij.usages.impl.UsageViewImpl;
import com.intellij.usages.rules.UsageFilteringRuleProvider;
import com.intellij.util.Icons;
import com.intellij.util.Processor;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.Table;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

public class ShowUsagesAction extends AnAction {
  private final boolean showSettingsDialogBefore;

  private static final int USAGES_PAGE_SIZE = 100;
  private static final Comparator<Object> USAGE_COMPARATOR = new Comparator<Object>() {
    public int compare(Object c1, Object c2) {
      Usage o1 = ((UsageNode)c1).getUsage();
      Usage o2 = ((UsageNode)c2).getUsage();
      if (o1 == NullUsage.INSTANCE) return 1;
      if (o2 == NullUsage.INSTANCE) return -1;

      VirtualFile v1 = UsageListCellRenderer.getVirtualFile(o1);
      VirtualFile v2 = UsageListCellRenderer.getVirtualFile(o2);
      String name1 = v1 == null ? null : v1.getName();
      String name2 = v2 == null ? null : v2.getName();
      int i = Comparing.compare(name1, name2);
      if (i!=0) return i;

      if (o1 instanceof Comparable && o2 instanceof Comparable) {
        return ((Comparable)o1).compareTo(o2);
      }

      FileEditorLocation loc1 = o1.getLocation();
      FileEditorLocation loc2 = o2.getLocation();
      return Comparing.compare(loc1, loc2);
    }
  };
  private static final Runnable HIDE_HINTS_ACTION = new Runnable() {
    public void run() {
      hideHints();
    }
  };

  public ShowUsagesAction() {
    setInjectedContext(true);
    showSettingsDialogBefore = false;
  }

  public static class ShowSettings extends ShowUsagesAction {
    public ShowSettings() {
      super(true);
    }
  }

  private ShowUsagesAction(boolean showDialogBefore) {
    setInjectedContext(true);
    showSettingsDialogBefore = showDialogBefore;
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) return;
    hideHints();
    myWidth = -1;
    final RelativePoint popupPosition = JBPopupFactory.getInstance().guessBestPopupLocation(e.getDataContext());
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.goto.usages");

    UsageTarget[] usageTargets = e.getData(UsageView.USAGE_TARGETS_KEY);
    final Editor editor = e.getData(PlatformDataKeys.EDITOR);
    if (usageTargets == null) {
      FindUsagesAction.chooseAmbiguousTargetAndPerform(project, editor, new PsiElementProcessor<PsiElement>() {
        public boolean execute(final PsiElement element) {
          startFindUsages(element, popupPosition, editor, USAGES_PAGE_SIZE);
          return false;
        }
      });
    }
    else {
      PsiElement element = ((PsiElementUsageTarget)usageTargets[0]).getElement();
      if (element != null) {
        startFindUsages(element, popupPosition, editor, USAGES_PAGE_SIZE);
      }
    }
  }

  private static void hideHints() {
    HintManager.getInstance().hideHints(HintManager.HIDE_BY_ANY_KEY, false, false);
  }

  private void startFindUsages(@NotNull PsiElement element, RelativePoint popupPosition, Editor editor, int maxUsages) {
    Project project = element.getProject();
    FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(project)).getFindUsagesManager();
    FindUsagesHandler handler = findUsagesManager.getFindUsagesHandler(element, false);
    if (handler == null) return;
    if (showSettingsDialogBefore) {
      showDialogAndFindUsages(handler, popupPosition, editor, maxUsages);
      return;
    }
    showElementUsages(handler, editor, popupPosition, maxUsages);
  }

  private void showElementUsages(@NotNull FindUsagesHandler handler, final Editor editor, final RelativePoint popupPosition, final int maxUsages) {
    UsageViewPresentation presentation = new UsageViewPresentation();
    presentation.setDetachedMode(true);

    final UsageViewSettings usageViewSettings = UsageViewSettings.getInstance();
    final UsageViewSettings save = new UsageViewSettings();

    save.loadState(usageViewSettings);
    usageViewSettings.GROUP_BY_FILE_STRUCTURE = false;
    usageViewSettings.GROUP_BY_MODULE = false;
    usageViewSettings.GROUP_BY_PACKAGE = false;
    usageViewSettings.GROUP_BY_USAGE_TYPE = false;

    UsageViewManager manager = UsageViewManager.getInstance(handler.getProject());
    final UsageViewImpl usageView = (UsageViewImpl)manager.createUsageView(UsageTarget.EMPTY_ARRAY, Usage.EMPTY_ARRAY, presentation, null);

    Disposer.register(usageView, new Disposable() {
      public void dispose() {
        usageViewSettings.GROUP_BY_FILE_STRUCTURE = save.GROUP_BY_FILE_STRUCTURE;
        usageViewSettings.GROUP_BY_MODULE = save.GROUP_BY_MODULE;
        usageViewSettings.GROUP_BY_PACKAGE = save.GROUP_BY_PACKAGE;
        usageViewSettings.GROUP_BY_USAGE_TYPE = save.GROUP_BY_USAGE_TYPE;
      }
    });

    final List<Usage> usages = new ArrayList<Usage>();
    final Set<UsageNode> visibleNodes = new LinkedHashSet<UsageNode>();
    Processor<Usage> collect = new Processor<Usage>() {
      public boolean process(@NotNull Usage usage) {
        synchronized (usages) {
          if (visibleNodes.size() > maxUsages) return false;
          UsageNode node = usageView.doAppendUsage(usage);
          if (node != null) {
            if (visibleNodes.size() == maxUsages) {
              usageView.removeUsage(usage);
              visibleNodes.add(UsageViewImpl.NULL_NODE);
              return false;
            }
            visibleNodes.add(node);
          }
          usages.add(usage);
        }
        return true;
      }
    };
    FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(handler.getProject())).getFindUsagesManager();
    presentation = findUsagesManager.processUsages(handler, collect);
    if (presentation == null) {
      Disposer.dispose(usageView);
      return;
    }
    final String title = presentation.getTabText();

    JBPopup popup = createUsagePopup(usages, visibleNodes, title, handler, editor, popupPosition, maxUsages, usageView);
    if (popup != null) {
      popup.show(popupPosition);
    }
  }

  private void showHint(String text, final Editor editor, final RelativePoint popupPosition, FindUsagesHandler handler, int maxUsages) {
    JComponent label = createHintComponent(text, handler, popupPosition, editor, HIDE_HINTS_ACTION, maxUsages);
    if (editor == null) {
      HintManager.getInstance().showHint(label, popupPosition, HintManager.HIDE_BY_ANY_KEY |
                                                               HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING, 0);
    }
    else {
      HintManager.getInstance().showInformationHint(editor, label);
    }
  }

  private JComponent createHintComponent(String text, final FindUsagesHandler handler, final RelativePoint popupPosition, final Editor editor,
                                         final Runnable cancelAction,
                                         int maxUsages) {
    JLabel label = HintUtil.createInformationLabel(text);
    InplaceButton button = createSettingsButton(handler, popupPosition, editor, maxUsages, cancelAction);
    JPanel panel = new JPanel(new BorderLayout());
    button.setBackground(label.getBackground());
    panel.setBackground(label.getBackground());
    label.setOpaque(false);
    label.setBorder(null);
    panel.setBorder(HintUtil.createHintBorder());
    panel.add(label, BorderLayout.CENTER);
    panel.add(button, BorderLayout.EAST);
    return panel;
  }

  private InplaceButton createSettingsButton(final FindUsagesHandler handler, final RelativePoint popupPosition,
                                             final Editor editor,
                                             final int maxUsages,
                                             final Runnable cancelAction) {
    String shortcutText = "";
    KeyboardShortcut shortcut = getSettingsShortcut();
    if (shortcut != null) {
      shortcutText = "(" + KeymapUtil.getShortcutText(shortcut) + ")";
    }
    return new InplaceButton("Options..." + shortcutText, IconLoader.getIcon("/general/ideOptions.png"), new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            showDialogAndFindUsages(handler, popupPosition, editor, maxUsages);
          }
        });
        cancelAction.run();
      }
    });
  }

  private void showDialogAndFindUsages(FindUsagesHandler handler, RelativePoint popupPosition, Editor editor, int maxUsages) {
    AbstractFindUsagesDialog dialog = handler.getFindUsagesDialog(false, false, false);
    dialog.show();
    if (dialog.isOK()) {
      dialog.calcFindUsagesOptions();
      showElementUsages(handler, editor, popupPosition, maxUsages);
    }
  }

  private static String searchScopePresentableName(final FindUsagesHandler handler) {
    SearchScope searchScope = FindUsagesManager.getCurrentSearchScope(handler);
    if (searchScope == null) searchScope = ProjectScope.getAllScope(handler.getProject());
    return searchScope.getDisplayName();
  }

  private JBPopup createUsagePopup(final List<Usage> usages,
                                   Set<UsageNode> visibleNodes,
                                   final String title,
                                   final FindUsagesHandler handler,
                                   final Editor editor,
                                   final RelativePoint popupPosition,
                                   final int maxUsages,
                                   final UsageViewImpl usageView) {
    boolean hasMore = visibleNodes.remove(UsageViewImpl.NULL_NODE);

    final Project project = handler.getProject();

    if (visibleNodes.isEmpty()) {
      if (usages.isEmpty()) {
        String text = UsageViewBundle.message("no.usages.found.in", searchScopePresentableName(handler));
        showHint(text, editor, popupPosition, handler, maxUsages);
        Disposer.dispose(usageView);
        return null;
      }
      else {
        // all usages filtered out
      }
    }
    if (visibleNodes.size() == 1 && usages.size() == 1) {
      //the only usage
      Usage usage = visibleNodes.iterator().next().getUsage();
      navigateAndHint(usage, UsageViewBundle.message("show.usages.only.usage", searchScopePresentableName(handler)), handler, popupPosition,
                      maxUsages);
      Disposer.dispose(usageView);
      return null;
    }
    if (visibleNodes.size() == 1 && usages.size() >= 1) {
      // usage view can filter usages down to one
      Usage usage = visibleNodes.iterator().next().getUsage();
      navigateAndHint(usage, UsageViewBundle.message("all.usages.are.in.this.line", usages.size(), searchScopePresentableName(handler)),
                      handler, popupPosition, maxUsages);
      Disposer.dispose(usageView);
      return null;
    }

    if (hasMore) {
      usages.add(NullUsage.INSTANCE);
      visibleNodes.add(UsageViewImpl.NULL_NODE);
    }
    addUsageNodes(usageView.getRoot(), usageView, new ArrayList<UsageNode>());


    final JTable table = new MyTable();
    TableScrollingUtil.installActions(table);
    final Vector<Object> data = new Vector<Object>();
    setModel(table, usages, visibleNodes, usageView, data);

    final Runnable navigateRunnable = new Runnable() {
      public void run() {
        int[] selected = table.getSelectedRows();
        for (int i : selected) {
          Object value = table.getValueAt(i,0);
          if (value instanceof UsageNode) {
            Usage usage = ((UsageNode)value).getUsage();
            if (usage == NullUsage.INSTANCE) {
              appendMoreUsages(editor, popupPosition, handler, maxUsages);
              return;
            }
            navigateAndHint(usage, null, handler, popupPosition, maxUsages);
          }
        }
      }
    };

    SpeedSearchBase<JTable> speedSearch = new SpeedSearchBase<JTable>(table) {
      protected int getSelectedIndex() {
        return table.getSelectedRow();
      }

      protected Object[] getAllElements() {
        return data.toArray(new Object[data.size()]);
      }

      protected String getElementText(Object element) {
        if (!(element instanceof UsageNode)) return element.toString();
        UsageNode node = (UsageNode)element;
        GroupNode group = (GroupNode)node.getParent();
        return node.getUsage().getPresentation().getPlainText() + group.toString();
      }

      protected void selectElement(Object element, String selectedText) {
        int i = data.indexOf(element);
        if (i == -1) return;
        table.getSelectionModel().setSelectionInterval(i, i);
      }
    };
    speedSearch.setComparator(new SpeedSearchBase.SpeedSearchComparator(false));

    PopupChooserBuilder builder = new PopupChooserBuilder(table);
    if (title != null) {
      String s;
      if (hasMore) {
        s = "<html><body><b>Some</b> " + title + " " + "<b>(Only " + (visibleNodes.size() - 1) + " usages shown)</b></body></html>";
      }
      else {
        s = title + " (" + usages.size() + " usages found)";
      }
      builder.setTitle(s);
    }

    builder.setMovable(true).setResizable(true);
    builder.setItemChoosenCallback(navigateRunnable);
    final JBPopup[] popup = new JBPopup[1];
    ActionListener editSettings = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        popup[0].cancel();
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            showDialogAndFindUsages(handler, popupPosition, editor, maxUsages);
          }
        });
      }
    };



    KeyboardShortcut shortcut = getSettingsShortcut();
    if (shortcut != null) {
      builder.registerKeyboardAction(shortcut.getFirstKeyStroke(), editSettings);
    }

    InplaceButton button = createSettingsButton(handler, popupPosition, editor, maxUsages, new Runnable() {
      public void run() {
        popup[0].cancel();
      }
    });
    builder.setCommandButton(button);

    DefaultActionGroup filters = new DefaultActionGroup();
    usageView.addFilteringActions(filters);

    filters.add(new AnAction("Open Find Usages Toolwindow", "Show all usages in a separate toolwindow", IconLoader.getIcon("/general/toolWindowFind.png")) {
      {
        AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_USAGES);
        setShortcutSet(action.getShortcutSet());
      }
      @Override
      public void actionPerformed(AnActionEvent e) {
        hideHints();
        popup[0].cancel();
        FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(project)).getFindUsagesManager();
        FindUsagesManager.SearchData data = new FindUsagesManager.SearchData();
        data.myOptions = handler.getFindUsagesOptions();
        SmartPsiElementPointer<PsiElement> pointer =
          SmartPointerManager.getInstance(project).createSmartPsiElementPointer(handler.getPsiElement());
        data.myElements = new SmartPsiElementPointer[]{pointer};
        findUsagesManager.rerunAndRecallFromHistory(data);
      }
    });

    ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.USAGE_VIEW_TOOLBAR, filters, true);
    actionToolbar.setReservePlaceAutoPopupIcon(false);
    final JComponent toolBar = actionToolbar.getComponent();
    toolBar.setOpaque(false);
    builder.setSettingButton(toolBar);

    popup[0] = builder.createPopup();
    Disposer.register(popup[0], usageView);
    for (AnAction action : filters.getChildren(null)) {
      action.unregisterCustomShortcutSet(usageView.getComponent());
      action.registerCustomShortcutSet(action.getShortcutSet(), popup[0].getContent());
    }

    final MessageBusConnection messageBusConnection = project.getMessageBus().connect(usageView);
    messageBusConnection.subscribe(UsageFilteringRuleProvider.RULES_CHANGED, new Runnable() {
      public void run() {
        rebuildPopup(usageView, usages, table, popup[0]);
      }
    });

    return popup[0];
  }

  private static int setModel(JTable table, List<Usage> usages, Collection<UsageNode> visibleNodes, UsageViewImpl usageView,
                                         final Vector<Object> data) {
    if (visibleNodes.isEmpty()) {
      data.add(UsageViewBundle.message("usages.were.filtered.out", usages.size()));
    }
    else {
      data.addAll(visibleNodes);
    }
    Collections.sort(data, USAGE_COMPARATOR);
    AbstractTableModel model = new AbstractTableModel() {
      public int getRowCount() {
        return data.size();
      }

      public int getColumnCount() {
        return data.get(0) instanceof UsageNode ? 3 : 1;
      }

      public Object getValueAt(int rowIndex, int columnIndex) {
        return data.get(rowIndex);
      }
    };
    table.setModel(model);

    table.setRowHeight(Icons.CLASS_ICON.getIconHeight()+2);
    table.setShowGrid(false);
    table.setShowVerticalLines(false);
    table.setShowHorizontalLines(false);
    table.setTableHeader(null);
    table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
    ShowUsagesTableCellRenderer renderer = new ShowUsagesTableCellRenderer(usageView);
    for (int i=0;i<table.getColumnModel().getColumnCount();i++) {
      TableColumn column = table.getColumnModel().getColumn(i);
      column.setCellRenderer(renderer);
    }
    table.setIntercellSpacing(new Dimension(0, 0));

    int colsNum = table.getColumnModel().getColumnCount();

    int totalWidth = 0;
    for (int col = 0; col < colsNum -1; col++) {
      TableColumn column = table.getColumnModel().getColumn(col);
      int preferred = column.getPreferredWidth();
      int width = Math.max(preferred, calcMaxWidth(table, col));
      totalWidth += width;
      column.setMinWidth(width);
      column.setMaxWidth(width);
      column.setWidth(width);
      column.setPreferredWidth(width);
    }

    totalWidth += calcMaxWidth(table, colsNum - 1);

    Dimension dimension = new Dimension(totalWidth, table.getRowHeight() * data.size());
    table.setMinimumSize(dimension);
    table.setSize(dimension);
    table.setPreferredSize(dimension);
    table.setMaximumSize(dimension);
    table.setPreferredScrollableViewportSize(dimension);

    return totalWidth;
  }

  private static int calcMaxWidth(JTable table, int col) {
    TableColumn column = table.getColumnModel().getColumn(col);
    int width = 0;
    for (int row = 0; row < table.getRowCount(); row++) {
      Component component = table.prepareRenderer(column.getCellRenderer(), row, col);

      int rendererWidth = component.getPreferredSize().width;
      width = Math.max(width, rendererWidth + table.getIntercellSpacing().width);
    }
    return width;
  }

  private int myWidth;
  private void rebuildPopup(final UsageViewImpl usageView, final List<Usage> usages, final JTable table, final JBPopup popup) {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        JComponent content = popup.getContent();
        Window window = SwingUtilities.windowForComponent(content);
        Dimension d = window.getSize();

        final List<UsageNode> nodes = new ArrayList<UsageNode>();
        addUsageNodes(usageView.getRoot(), usageView, nodes);

        int old = table.getModel().getRowCount();
        Vector<Object> data = new Vector<Object>();
        int width = setModel(table, usages, nodes, usageView, data);


        if (myWidth == -1) myWidth = width;
        Dimension newDim = new Dimension(Math.max(width, d.width + width - myWidth), d.height + (data.size() - old) * table.getRowHeight());
        myWidth = width;
        window.setSize(newDim);
        window.validate();
        window.repaint();
        table.revalidate();
        table.repaint();
      }
    });
  }

  private void appendMoreUsages(Editor editor, RelativePoint popupPosition, FindUsagesHandler handler, int maxUsages) {
    showElementUsages(handler,  editor, popupPosition, maxUsages+USAGES_PAGE_SIZE);
  }

  private static KeyboardShortcut getSettingsShortcut() {
    AnAction action = ActionManager.getInstance().getAction("ShowUsagesSettings");
    final ShortcutSet shortcutSet = action.getShortcutSet();
    final Shortcut[] shortcuts = shortcutSet.getShortcuts();
    for (final Shortcut shortcut : shortcuts) {
      KeyboardShortcut kb = (KeyboardShortcut)shortcut;
      if (kb.getSecondKeyStroke() == null) {
        return (KeyboardShortcut)shortcut;
      }
    }

    return null;
  }


  private static void addUsageNodes(GroupNode root, final UsageViewImpl usageView, List<UsageNode> outNodes) {
    for (UsageNode node : root.getUsageNodes()) {
      Usage usage = node.getUsage();
      if (usageView.isVisible(usage)) {
        node.setParent(root);
        outNodes.add(node);
      }
    }
    for (GroupNode groupNode : root.getSubGroups()) {
      groupNode.setParent(root);
      addUsageNodes(groupNode, usageView, outNodes);
    }
  }

  public void update(AnActionEvent e){
    FindUsagesInFileAction.updateFindUsagesAction(e);
  }

  private void navigateAndHint(Usage usage, final String hint, final FindUsagesHandler handler,
                               final RelativePoint popupPosition,
                               final int maxUsages) {
    usage.navigate(true);
    if (hint == null) return;
    FileEditorLocation location = usage.getLocation();
    FileEditor newFileEditor = location == null ? null : location.getEditor();
    final Editor newEditor = newFileEditor instanceof TextEditor ? ((TextEditor)newFileEditor).getEditor() : null;
    if (newEditor != null) {
      final Project project = handler.getProject();
      //opening editor is performing in invokeLater
      IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(new Runnable() {
        public void run() {
          newEditor.getScrollingModel().runActionOnScrollingFinished(new Runnable() {
            public void run() {
              // after new editor created, some editor resizing events are still bubbling. To prevent hiding hint, invokeLater this
              IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(new Runnable() {
                public void run() {
                  showHint(hint, newEditor, popupPosition, handler, maxUsages);
                }
              });
            }
          });
        }
      });
    }
  }

  static class MyTable extends Table implements DataProvider {
    @Override
    public boolean getScrollableTracksViewportWidth() {
      return true;
    }

    public Object getData(@NonNls String dataId) {
      if (LangDataKeys.PSI_ELEMENT.is(dataId)) {
        final int[] selected = getSelectedRows();
        if (selected.length == 1) {
          final Object at = getValueAt(selected[0], 0);
          if (at instanceof UsageNode) {
            final Usage usage = ((UsageNode)at).getUsage();
            if (usage instanceof UsageInfo2UsageAdapter) {
              final PsiElement element = ((UsageInfo2UsageAdapter)usage).getElement();
              if (element != null) {
                return element.getContainingFile();
              }
            }
          }
        }
      }
      return null;
    }
  }
}