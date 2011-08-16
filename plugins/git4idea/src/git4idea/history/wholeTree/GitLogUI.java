/*
 * Copyright 2000-2010 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.history.wholeTree;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.CaptionIcon;
import com.intellij.openapi.diff.impl.patch.formove.FilePathComparator;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.MouseChecker;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser;
import com.intellij.openapi.vcs.changes.committed.RepositoryChangesBrowser;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkRenderer;
import com.intellij.openapi.vcs.changes.issueLinks.TableLinkMouseListener;
import com.intellij.openapi.vcs.ui.SearchFieldAction;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;
import com.intellij.ui.table.JBTable;
import com.intellij.util.Consumer;
import com.intellij.util.PairConsumer;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.AdjustComponentWhenShown;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.UIUtil;
import git4idea.GitVcs;
import git4idea.history.browser.*;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

/**
 * @author irengrig
 */
public class GitLogUI implements Disposable {
  private final static Logger LOG = Logger.getInstance("#git4idea.history.wholeTree.GitLogUI");
  public static final SimpleTextAttributes HIGHLIGHT_TEXT_ATTRIBUTES =
    new SimpleTextAttributes(SimpleTextAttributes.STYLE_SEARCH_MATCH, UIUtil.getTableForeground());
  public static final String GIT_LOG_TABLE_PLACE = "git log table";
  private final Project myProject;
  private BigTableTableModel myTableModel;
  private DetailsCache myDetailsCache;
  private final Mediator myMediator;
  private Splitter mySplitter;
  private GitTableScrollChangeListener myMyChangeListener;
  private List<VirtualFile> myRootsUnderVcs;
  private final Map<VirtualFile, SymbolicRefs> myRefs;
  private final SymbolicRefs myRecalculatedCommon;
  private UIRefresh myUIRefresh;
  private JBTable myJBTable;
  private RepositoryChangesBrowser myRepositoryChangesBrowser;
  final List<CommitI> myCommitsInRepositoryChangesBrowser;
  private boolean myDataBeingAdded;
  private CardLayout myRepoLayout;
  private JPanel myRepoPanel;
  private boolean myStarted;
  private String myPreviousFilter;
  private final CommentSearchContext myCommentSearchContext;
  private List<String> myUsersSearchContext;
  private String mySelectedBranch;
  private BranchSelectorAction myBranchSelectorAction;
  private final DescriptionRenderer myDescriptionRenderer;

  private GenericDetailsLoader<CommitI, GitCommit> myDetailsLoader;
  private GenericDetailsLoader<CommitI, List<String>> myBranchesLoader;
  private GitLogDetailsPanel myDetailsPanel;

  private StepType myState;
  private MoreAction myMoreAction;
  private UsersFilterAction myUsersFilterAction;
  private MyFilterUi myUserFilterI;
  private MyCherryPick myCherryPickAction;
  private MyRefreshAction myRefreshAction;
  private MyStructureFilter myStructureFilter;
  private StructureFilterAction myStructureFilterAction;
  private AnAction myCopyHashAction;
  // todo group somewhere??
  private Consumer<CommitI> myDetailsLoaderImpl;
  private Consumer<CommitI> myBranchesLoaderImpl;
  private final RequestsMerger mySelectionRequestsMerger;

  private final TableCellRenderer myAuthorRenderer;
  private MyRootsAction myRootsAction;

  public GitLogUI(Project project, final Mediator mediator) {
    myProject = project;
    myMediator = mediator;
    myCommentSearchContext = new CommentSearchContext();
    myUsersSearchContext = new ArrayList<String>();
    myRefs = new HashMap<VirtualFile, SymbolicRefs>();
    myRecalculatedCommon = new SymbolicRefs();
    myPreviousFilter = "";
    myDescriptionRenderer = new DescriptionRenderer();
    myCommentSearchContext.addHighlighter(myDescriptionRenderer.myInner.myWorker);
    myCommitsInRepositoryChangesBrowser = new ArrayList<CommitI>();

    mySelectionRequestsMerger = new RequestsMerger(new Runnable() {
      @Override
      public void run() {
        selectionChanged();
      }
    }, new Consumer<Runnable>() {
      @Override
      public void consume(Runnable runnable) {
        SwingUtilities.invokeLater(runnable);
      }
    });
    createTableModel();
    myState = StepType.CONTINUE;

    initUiRefresh();
    myAuthorRenderer = new HighLightingRenderer(HIGHLIGHT_TEXT_ATTRIBUTES,                                                                          SimpleTextAttributes.REGULAR_ATTRIBUTES);
  }

  private void initUiRefresh() {
    myUIRefresh = new UIRefresh() {
      @Override
      public void detailsLoaded() {
        tryRefreshDetails();
        fireTableRepaint();
      }

      @Override
      public void linesReloaded(boolean drawMore) {
        if ((! StepType.STOP.equals(myState)) && (! StepType.FINISHED.equals(myState))) {
          myState = drawMore ? StepType.PAUSE : StepType.CONTINUE;
        }
        fireTableRepaint();
        updateMoreVisibility();
      }

      @Override
      public void acceptException(Exception e) {
        LOG.info(e);
      }

      @Override
      public void finished() {
        myState = StepType.FINISHED;
        updateMoreVisibility();
      }

      @Override
      public void reportSymbolicRefs(VirtualFile root, SymbolicRefs symbolicRefs) {
        myRefs.put(root, symbolicRefs);

        myRecalculatedCommon.clear();
        if (myRefs.isEmpty()) return;

        final CheckSamePattern<String> currentUser = new CheckSamePattern<String>();
        final CheckSamePattern<String> currentBranch = new CheckSamePattern<String>();
        for (SymbolicRefs refs : myRefs.values()) {
          myRecalculatedCommon.addLocals(refs.getLocalBranches());
          myRecalculatedCommon.addRemotes(refs.getRemoteBranches());
          myRecalculatedCommon.addTags(refs.getTags());
          final String currentFromRefs = refs.getCurrent() == null ? null : refs.getCurrent().getFullName();
          currentBranch.iterate(currentFromRefs);
          currentUser.iterate(refs.getUsername());
        }
        if (currentBranch.isSame()) {
          myRecalculatedCommon.setCurrent(myRefs.values().iterator().next().getCurrent());
        }
        if (currentUser.isSame()) {
          final String username = currentUser.getSameValue();
          myRecalculatedCommon.setUsername(username);
          myUserFilterI.setMe(username);
        }

        myBranchSelectorAction.setSymbolicRefs(myRecalculatedCommon);
      }
    };
  }

  private void fireTableRepaint() {
    final TableSelectionKeeper keeper = new TableSelectionKeeper(myJBTable, myTableModel);
    keeper.put();
    myDataBeingAdded = true;
    myTableModel.fireTableDataChanged();
    keeper.restore();
    myDataBeingAdded = false;
    myJBTable.revalidate();
    myJBTable.repaint();
  }

  private void start() {
    myStarted = true;
    myMyChangeListener.start();
    rootsChanged(myRootsUnderVcs);
  }

  private static class TableSelectionKeeper {
    private final List<Pair<Integer, AbstractHash>> myData;
    private final JBTable myTable;
    private final BigTableTableModel myModel;
    private int[] mySelectedRows;

    private TableSelectionKeeper(final JBTable table, final BigTableTableModel model) {
      myTable = table;
      myModel = model;
      myData = new ArrayList<Pair<Integer,AbstractHash>>();
    }

    public void put() {
      mySelectedRows = myTable.getSelectedRows();
      for (int row : mySelectedRows) {
        final CommitI commitI = myModel.getCommitAt(row);
        if (commitI != null) {
          myData.add(new Pair<Integer, AbstractHash>(commitI.selectRepository(SelectorList.getInstance()), commitI.getHash()));
        }
      }
    }

    public void restore() {
      final int rowCount = myModel.getRowCount();
      final ListSelectionModel selectionModel = myTable.getSelectionModel();
      for (int row : mySelectedRows) {
        final CommitI commitI = myModel.getCommitAt(row);
        if (commitI != null) {
          final Pair<Integer, AbstractHash> pair =
            new Pair<Integer, AbstractHash>(commitI.selectRepository(SelectorList.getInstance()), commitI.getHash());
          if (myData.remove(pair)) {
            selectionModel.addSelectionInterval(row, row);
            if (myData.isEmpty()) return;
          }
        }
      }
      if (myData.isEmpty()) return;
      for (int i = 0; i < rowCount; i++) {
        final CommitI commitI = myModel.getCommitAt(i);
        if (commitI == null) continue;
        final Pair<Integer, AbstractHash> pair =
          new Pair<Integer, AbstractHash>(commitI.selectRepository(SelectorList.getInstance()), commitI.getHash());
        if (myData.remove(pair)) {
          selectionModel.addSelectionInterval(i, i);
          if (myData.isEmpty()) break;
        }
      }
    }
  }

  @Override
  public void dispose() {
  }

  public UIRefresh getUIRefresh() {
    return myUIRefresh;
  }

  public void createMe() {
    mySplitter = new Splitter(false, 0.7f);
    mySplitter.setDividerWidth(4);

    final JPanel wrapper = createMainTable();
    mySplitter.setFirstComponent(wrapper);

    final JComponent component = createRepositoryBrowserDetails();
    mySplitter.setSecondComponent(component);

    Disposer.register(this, new Disposable() {
      @Override
      public void dispose() {
        if (myMyChangeListener != null) {
          myMyChangeListener.stop();
        }
      }
    });

    createDetailLoaders();
  }

  private void createDetailLoaders() {
    myDetailsLoaderImpl = new Consumer<CommitI>() {
      @Override
      public void consume(final CommitI commitI) {
        if (commitI == null) return;
        final GitCommit gitCommit = fullCommitPresentation(commitI);

        if (gitCommit == null) {
          final MultiMap<VirtualFile, AbstractHash> question = new MultiMap<VirtualFile, AbstractHash>();
          question.putValue(commitI.selectRepository(myRootsUnderVcs), commitI.getHash());
          myDetailsCache.acceptQuestion(question);
        } else {
          myDetailsLoader.take(commitI, gitCommit);
        }
      }
    };
    myDetailsLoader = new GenericDetailsLoader<CommitI, GitCommit>(myDetailsLoaderImpl, new PairConsumer<CommitI, GitCommit>() {
      @Override
      public void consume(CommitI commitI, GitCommit commit) {
        myDetailsPanel.setData(commitI.selectRepository(myRootsUnderVcs), commit);
      }
    });

    myBranchesLoaderImpl = new Consumer<CommitI>() {
      private Processor<AbstractHash> myRecheck;

      {
        myRecheck = new Processor<AbstractHash>() {
          @Override
          public boolean process(AbstractHash abstractHash) {
            if (myBranchesLoader.getCurrentlySelected() == null) return false;
            return Comparing.equal(myBranchesLoader.getCurrentlySelected().getHash(), abstractHash);
          }
        };
      }

      @Override
      public void consume(final CommitI commitI) {
        if (commitI == null) return;
        final VirtualFile root = commitI.selectRepository(myRootsUnderVcs);
        final List<String> branches = myDetailsCache.getBranches(root, commitI.getHash());
        if (branches != null) {
          myBranchesLoader.take(commitI, branches);
          return;
        }

        myDetailsCache.loadAndPutBranches(root, commitI.getHash(), new Consumer<List<String>>() {
          @Override
          public void consume(List<String> strings) {
            if (myProject.isDisposed() || strings == null) return;
            myBranchesLoader.take(commitI, strings);
          }
        }, myRecheck);
      }
    };
    myBranchesLoader = new GenericDetailsLoader<CommitI, List<String>>(myBranchesLoaderImpl, new PairConsumer<CommitI, List<String>>() {
      @Override
      public void consume(CommitI commitI, List<String> strings) {
        myDetailsPanel.setBranches(strings);
      }
    });
  }

  private JComponent createRepositoryBrowserDetails() {
    myRepoLayout = new CardLayout();
    myRepoPanel = new JPanel(myRepoLayout);
    myRepositoryChangesBrowser = new RepositoryChangesBrowser(myProject, Collections.<CommittedChangeList>emptyList(), Collections.<Change>emptyList(), null);
    myRepositoryChangesBrowser.getDiffAction().registerCustomShortcutSet(CommonShortcuts.getDiff(), myJBTable);
    myJBTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        mySelectionRequestsMerger.request();
      }
    });
    myRepoPanel.add("main", myRepositoryChangesBrowser);
    // todo loading circle
    myRepoPanel.add("loading", panelWithCenteredText("Loading..."));
    myRepoPanel.add("tooMuch", panelWithCenteredText("Too many rows selected"));
    myRepoPanel.add("empty", panelWithCenteredText("Nothing selected"));
    myRepoLayout.show(myRepoPanel, "empty");
    return myRepoPanel;
  }

  private void selectionChanged() {
    if (myDataBeingAdded) {
      mySelectionRequestsMerger.request();
      return;
    }
    final int[] rows = myJBTable.getSelectedRows();
    selectionChangedForDetails(rows);

    if (rows.length == 0) {
      myRepoLayout.show(myRepoPanel, "empty");
      myRepoPanel.repaint();
      return;
    } else if (rows.length >= 10) {
      myRepoLayout.show(myRepoPanel, "tooMuch");
      myRepoPanel.repaint();
      return;
    }
    if (! myDataBeingAdded && ! gatherNotLoadedData()) {
      myRepoLayout.show(myRepoPanel, "loading");
      myRepoPanel.repaint();
    }
  }

  private static class MeaningfulSelection {
    private CommitI myCommitI;
    private int myMeaningfulRows;

    private MeaningfulSelection(int[] rows, final BigTableTableModel tableModel) {
      myMeaningfulRows = 0;
      for (int row : rows) {
        myCommitI = tableModel.getCommitAt(row);
        if (!myCommitI.holdsDecoration()) {
          ++myMeaningfulRows;
          if (myMeaningfulRows > 1) break;
        }
      }
    }

    public CommitI getCommit() {
      return myCommitI;
    }

    public int getMeaningfulRows() {
      return myMeaningfulRows;
    }
  }

  private void tryRefreshDetails() {
    MeaningfulSelection meaningfulSelection = new MeaningfulSelection(myJBTable.getSelectedRows(), myTableModel);
    if (meaningfulSelection.getMeaningfulRows() == 1) {
      // still have one item selected which probably was not loaded
      final CommitI commit = meaningfulSelection.getCommit();
      myDetailsLoaderImpl.consume(commit);
      myBranchesLoaderImpl.consume(commit);
    }
  }

  private void selectionChangedForDetails(int[] rows) {
    MeaningfulSelection meaningfulSelection = new MeaningfulSelection(rows, myTableModel);
    int meaningfulRows = meaningfulSelection.getMeaningfulRows();
    CommitI commitAt = meaningfulSelection.getCommit();

    if (meaningfulRows == 0) {
      myDetailsPanel.nothingSelected();
      myDetailsLoader.updateSelection(null, false);
      myBranchesLoader.updateSelection(null, false);
    } else if (meaningfulRows == 1) {
      final GitCommit commit = fullCommitPresentation(commitAt);
      if (commit == null) {
        myDetailsPanel.loading(commitAt.selectRepository(myRootsUnderVcs));
      }
      myDetailsLoader.updateSelection(commitAt, false);
      myBranchesLoader.updateSelection(commitAt, false);
    } else {
      myDetailsPanel.severalSelected();
      myDetailsLoader.updateSelection(null, false);
      myBranchesLoader.updateSelection(null, false);
    }
  }

  private GitCommit fullCommitPresentation(CommitI commitAt) {
    return myDetailsCache.convert(commitAt.selectRepository(myRootsUnderVcs), commitAt.getHash());
  }

  private static JPanel panelWithCenteredText(final String text) {
    final JPanel jPanel = new JPanel(new BorderLayout());
    jPanel.setBackground(UIUtil.getTableBackground());
    final JLabel label = new JLabel(text, JLabel.CENTER);
    label.setUI(new MultiLineLabelUI());
    jPanel.add(label, BorderLayout.CENTER);
    jPanel.setBorder(BorderFactory.createLineBorder(UIUtil.getBorderColor()));
    return jPanel;
  }

  public void updateByScroll() {
    gatherNotLoadedData();
  }

  private boolean gatherNotLoadedData() {
    if (myDataBeingAdded) return false;
    final int[] rows = myJBTable.getSelectedRows();
    final List<GitCommit> commits = new ArrayList<GitCommit>();
    final List<CommitI> forComparison = new ArrayList<CommitI>();

    final MultiMap<VirtualFile,AbstractHash> missingHashes = new MultiMap<VirtualFile, AbstractHash>();
    for (int i = rows.length - 1; i >= 0; --i) {
      final int row = rows[i];
      final CommitI commitI = myTableModel.getCommitAt(row);
      if (commitI == null || commitI.holdsDecoration()) continue;
      final GitCommit details = fullCommitPresentation(commitI);
      if (details == null) {
        missingHashes.putValue(commitI.selectRepository(myRootsUnderVcs), commitI.getHash());
      } else if (missingHashes.isEmpty()) {   // no sense in collecting commits when s
        forComparison.add(commitI);
        commits.add(details);
      }
    }
    if (! missingHashes.isEmpty()) {
      myDetailsCache.acceptQuestion(missingHashes);
      return false;
    }
    if (Comparing.equal(myCommitsInRepositoryChangesBrowser, forComparison)) return true;
    myCommitsInRepositoryChangesBrowser.clear();
    myCommitsInRepositoryChangesBrowser.addAll(forComparison);

    final List<Change> changes = new ArrayList<Change>();
    for (GitCommit commit : commits) {
      changes.addAll(commit.getChanges());
    }
    final List<Change> zipped = CommittedChangesTreeBrowser.zipChanges(changes);
    myRepositoryChangesBrowser.setChangesToDisplay(zipped);
    myRepoLayout.show(myRepoPanel, "main");
    myRepoPanel.repaint();
    return true;
  }

  private JPanel createMainTable() {
    myJBTable = new JBTable(myTableModel) {
      @Override
      public TableCellRenderer getCellRenderer(int row, int column) {
        final TableCellRenderer custom = myTableModel.getColumnInfo(column).getRenderer(myTableModel.getValueAt(row, column));
        return custom == null ? super.getCellRenderer(row, column) : custom;
      }
    };
    final TableLinkMouseListener tableLinkListener = new TableLinkMouseListener() {
      @Override
      protected Object tryGetTag(MouseEvent e, JTable table, int row, int column) {
        myDescriptionRenderer.getTableCellRendererComponent(table, table.getValueAt(row, column), false, false, row, column);
        final Rectangle rc = table.getCellRect(row, column, false);
        int index = myDescriptionRenderer.myInner.findFragmentAt(e.getPoint().x - rc.x - myDescriptionRenderer.getCurrentWidth());
        if (index >= 0) {
          return myDescriptionRenderer.myInner.getFragmentTag(index);
        }
        return null;
      }
    };
    final ActionToolbar actionToolbar = createToolbar();
    tableLinkListener.install(myJBTable);
    myJBTable.getExpandableItemsHandler().setEnabled(false);
    myJBTable.setShowGrid(false);
    myJBTable.setModel(myTableModel);
    myJBTable.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        createContextMenu().getComponent().show(comp,x,y);
      }
    });

    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myJBTable);

    new AdjustComponentWhenShown() {
      @Override
      protected boolean init() {
        return adjustColumnSizes(scrollPane);
      }

      @Override
      protected boolean canExecute() {
        return myStarted;
      }
    }.install(myJBTable);

    myMyChangeListener = new GitTableScrollChangeListener(myJBTable, myDetailsCache, myTableModel, new Runnable() {
      @Override
      public void run() {
        updateByScroll();
      }
    });
    scrollPane.getViewport().addChangeListener(myMyChangeListener);

    final JPanel wrapper = new DataProviderPanel(new BorderLayout());
    wrapper.add(actionToolbar.getComponent(), BorderLayout.NORTH);
    final JPanel mainBorderWrapper = new JPanel(new BorderLayout());
    mainBorderWrapper.add(scrollPane, BorderLayout.CENTER);
    mainBorderWrapper.setBorder(BorderFactory.createLineBorder(UIUtil.getBorderColor()));
    wrapper.add(mainBorderWrapper, BorderLayout.CENTER);
    myDetailsPanel = new GitLogDetailsPanel(myProject, myDetailsCache, new Convertor<VirtualFile, SymbolicRefs>() {
      @Override
      public SymbolicRefs convert(VirtualFile o) {
        return myRefs.get(o);
      }
    });
    final JPanel borderWrapper = new JPanel(new BorderLayout());
    borderWrapper.setBorder(BorderFactory.createLineBorder(UIUtil.getBorderColor()));
    borderWrapper.add(myDetailsPanel.getComponent(), BorderLayout.CENTER);

    final Splitter splitter = new Splitter(true, 0.6f);
    splitter.setFirstComponent(wrapper);
    splitter.setSecondComponent(borderWrapper);
    splitter.setDividerWidth(4);
    return splitter;
  }

  private ActionPopupMenu createContextMenu() {
    final DefaultActionGroup group = new DefaultActionGroup();
    group.add(myCopyHashAction);
    final Point location = MouseInfo.getPointerInfo().getLocation();
    SwingUtilities.convertPointFromScreen(location, myJBTable);
    final int row = myJBTable.rowAtPoint(location);
    if (row >= 0) {
      final GitCommit commit = getCommitAtRow(row);
      if (commit != null) {
        myUsersFilterAction.setPreselectedUser(commit.getCommitter());
      }
    }
    group.add(myBranchSelectorAction.asTextAction());
    group.add(myUsersFilterAction.asTextAction());
    group.add(myStructureFilterAction.asTextAction());
    group.add(myCherryPickAction);
    group.add(ActionManager.getInstance().getAction("ChangesView.CreatePatchFromChanges"));
    group.add(myRefreshAction);
    return ActionManager.getInstance().createActionPopupMenu(GIT_LOG_TABLE_PLACE, group);
  }

  private ActionToolbar createToolbar() {
    final DefaultActionGroup group = new DefaultActionGroup();
    myBranchSelectorAction = new BranchSelectorAction(myProject, new Consumer<String>() {
      @Override
      public void consume(String s) {
        mySelectedBranch = s;
        reloadRequest();
      }
    });
    final Runnable reloadCallback = new Runnable() {
      @Override
      public void run() {
        reloadRequest();
      }
    };
    myUserFilterI = new MyFilterUi(reloadCallback);
    myUsersFilterAction = new UsersFilterAction(myProject, myUserFilterI);
    group.add(new MyTextFieldAction());
    group.add(myBranchSelectorAction);
    group.add(myUsersFilterAction);
    Getter<List<VirtualFile>> rootsGetter = new Getter<List<VirtualFile>>() {
      @Override
      public List<VirtualFile> get() {
        return myRootsUnderVcs;
      }
    };
    myStructureFilter = new MyStructureFilter(reloadCallback, rootsGetter);
    myStructureFilterAction = new StructureFilterAction(myProject, myStructureFilter);
    group.add(myStructureFilterAction);
    myCherryPickAction = new MyCherryPick();
    group.add(myCherryPickAction);
    group.add(ActionManager.getInstance().getAction("ChangesView.CreatePatchFromChanges"));
    myRefreshAction = new MyRefreshAction();
    myRootsAction = new MyRootsAction(rootsGetter, myJBTable);
    group.add(myRootsAction);
    group.add(myRefreshAction);
    myMoreAction = new MoreAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myMediator.continueLoading();
        myState = StepType.CONTINUE;
        updateMoreVisibility();
      }
    };
    group.add(myMoreAction);
    // just created here
    myCopyHashAction = new AnAction("Copy Hash") {
      @Override
      public void actionPerformed(AnActionEvent e) {
        final int[] selectedRows = myJBTable.getSelectedRows();
        final StringBuilder sb = new StringBuilder();
        for (int row : selectedRows) {
          final CommitI commitAt = myTableModel.getCommitAt(row);
          if (commitAt == null) continue;
          if (sb.length() > 0) {
            sb.append(' ');
          }
          sb.append(commitAt.getHash().getString());
        }
        CopyPasteManager.getInstance().setContents(new StringSelection(sb.toString()));
      }

      @Override
      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(myJBTable.getSelectedRowCount() > 0);
      }
    };
    return ActionManager.getInstance().createActionToolbar("Git log", group, true);
  }

  private class DataProviderPanel extends JPanel implements TypeSafeDataProvider {
    private DataProviderPanel(LayoutManager layout) {
      super(layout);
    }

    @Override
    public void calcData(DataKey key, DataSink sink) {
      if (VcsDataKeys.CHANGES.equals(key)) {
        final int[] rows = myJBTable.getSelectedRows();
        if (rows.length != 1) return;
        final List<Change> changes = new ArrayList<Change>();
        for (int row : rows) {
          final GitCommit gitCommit = getCommitAtRow(row);
          if (gitCommit == null) return;
          changes.addAll(gitCommit.getChanges());
        }
        sink.put(key, changes.toArray(new Change[changes.size()]));
      } else if (VcsDataKeys.PRESET_COMMIT_MESSAGE.equals(key)) {
        final int[] rows = myJBTable.getSelectedRows();
        if (rows.length != 1) return;
        final CommitI commitAt = myTableModel.getCommitAt(rows[0]);
        if (commitAt == null) return;
        final GitCommit gitCommit = fullCommitPresentation(commitAt);
        if (gitCommit == null) return;
        sink.put(key, gitCommit.getDescription());
      }
    }
  }

  @Nullable
  private GitCommit getCommitAtRow(int row) {
    final CommitI commitAt = myTableModel.getCommitAt(row);
    if (commitAt == null) return null;
    final GitCommit gitCommit = fullCommitPresentation(commitAt);
    if (gitCommit == null) return null;
    return gitCommit;
  }

  private boolean adjustColumnSizes(JScrollPane scrollPane) {
    if (myJBTable.getWidth() <= 0) return false;
    //myJBTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    final TableColumnModel columnModel = myJBTable.getColumnModel();
    final FontMetrics metrics = myJBTable.getFontMetrics(myJBTable.getFont());
    final int height = metrics.getHeight();
    myJBTable.setRowHeight((int) (height * 1.1) + 1);
    final int dateWidth = metrics.stringWidth("Yesterday 00:00:00  " + scrollPane.getVerticalScrollBar().getWidth()) + columnModel.getColumnMargin();
    final int nameWidth = metrics.stringWidth("Somelong W. UsernameToDisplay");
    int widthWas = 0;
    for (int i = 0; i < columnModel.getColumnCount(); i++) {
      widthWas += columnModel.getColumn(i).getWidth();
    }

    columnModel.getColumn(1).setWidth(nameWidth);
    columnModel.getColumn(1).setPreferredWidth(nameWidth);

    columnModel.getColumn(2).setWidth(dateWidth);
    columnModel.getColumn(2).setPreferredWidth(dateWidth);

    final int nullWidth = widthWas - dateWidth - nameWidth - columnModel.getColumnMargin() * 3;
    columnModel.getColumn(0).setWidth(nullWidth);
    columnModel.getColumn(0).setPreferredWidth(nullWidth);

    return true;
  }

  private static class CommentSearchContext {
    private final List<HighlightingRendererBase> myListeners;
    private final List<String> mySearchContext;

    private CommentSearchContext() {
      mySearchContext = new ArrayList<String>();
      myListeners = new ArrayList<HighlightingRendererBase>();
    }

    public void addHighlighter(final HighlightingRendererBase renderer) {
      myListeners.add(renderer);
    }

    public void clear() {
      mySearchContext.clear();
      for (HighlightingRendererBase listener : myListeners) {
        listener.setSearchContext(Collections.<String>emptyList());
      }
    }

    public String preparse(String previousFilter) {
      final String[] strings = previousFilter.split("[\\s]");
      StringBuilder sb = new StringBuilder();
      mySearchContext.clear();
      for (String string : strings) {
        if (string.trim().length() == 0) continue;
        mySearchContext.add(string.toLowerCase());
        final String word = StringUtil.escapeToRegexp(string);
        sb.append(word).append(".*");
      }
      new SubstringsFilter().doFilter(mySearchContext);
      for (HighlightingRendererBase listener : myListeners) {
        listener.setSearchContext(mySearchContext);
      }
      return sb.toString();
    }
  }

  public static class SubstringsFilter extends AbstractFilterChildren<String> {
    @Override
    protected boolean isAncestor(String parent, String child) {
      return parent.startsWith(child);
    }

    @Override
    protected void sortAscending(List<String> strings) {
      Collections.sort(strings, new ComparableComparator.Descending<String>());
    }
  }

  public JComponent getPanel() {
    return mySplitter;
  }

  public void rootsChanged(List<VirtualFile> rootsUnderVcs) {
    myRootsUnderVcs = rootsUnderVcs;
    final RootsHolder rootsHolder = new RootsHolder(rootsUnderVcs);
    myTableModel.setRootsHolder(rootsHolder);
    myDetailsCache.rootsChanged(rootsUnderVcs);
    if (myStarted) {
      reloadRequest();
    }
  }

  public UIRefresh getRefreshObject() {
    return myUIRefresh;
  }

  public BigTableTableModel getTableModel() {
    return myTableModel;
  }

  private void createTableModel() {
    myTableModel = new BigTableTableModel(columns(), new Runnable() {
      @Override
      public void run() {
        start();
      }
    });
  }

  List<ColumnInfo> columns() {
    initAuthor();
    return Arrays.asList((ColumnInfo)COMMENT, AUTHOR, DATE);
  }

  private final ColumnInfo<Object, Object> COMMENT = new ColumnInfo<Object, Object>("Comment") {
    private final TableCellRenderer mySimpleRenderer = new SimpleRenderer(SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES, true);

    @Override
    public Object valueOf(Object o) {
      if (o instanceof GitCommit) {
        return o;
      }
      if (BigTableTableModel.LOADING == o) return o;
      return o == null ? "" : o.toString();
    }

    @Override
    public TableCellRenderer getRenderer(Object o) {
      return o instanceof GitCommit ? myDescriptionRenderer : mySimpleRenderer;
    }
  };

  private class HighLightingRenderer extends ColoredTableCellRenderer {
    private final SimpleTextAttributes myHighlightAttributes;
    private final SimpleTextAttributes myUsualAttributes;
    private SimpleTextAttributes myUsualAttributesForRun;
    protected final HighlightingRendererBase myWorker;

    public HighLightingRenderer(SimpleTextAttributes highlightAttributes, SimpleTextAttributes usualAttributes) {
      myHighlightAttributes = highlightAttributes;
      myUsualAttributes = usualAttributes == null ? SimpleTextAttributes.REGULAR_ATTRIBUTES : usualAttributes;
      myUsualAttributesForRun = myUsualAttributes;
      myWorker = new HighlightingRendererBase() {
        @Override
        protected void usual(String s) {
          append(s, myUsualAttributesForRun);
        }

        @Override
        protected void highlight(String s) {
          append(s, SimpleTextAttributes.merge(myUsualAttributesForRun, myHighlightAttributes));
        }
      };
    }

    public HighlightingRendererBase getWorker() {
      return myWorker;
    }

    @Override
    protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
      setBackground(getLogicBackground(selected, row));
      if (BigTableTableModel.LOADING == value) {
        return;
      }
      final String text = value.toString();
      myUsualAttributesForRun = isCurrentUser(row, text) ?
                                SimpleTextAttributes.merge(myUsualAttributes, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES) : myUsualAttributes;
      if (myWorker.isEmpty()) {
        append(text, myUsualAttributesForRun);
        return;
      }
      myWorker.tryHighlight(text);
    }

    private boolean isCurrentUser(final int row, final String text) {
      final CommitI commitAt = myTableModel.getCommitAt(row);
      if (commitAt == null) return false;
      final SymbolicRefs symbolicRefs = myRefs.get(commitAt.selectRepository(myRootsUnderVcs));
      if (symbolicRefs == null) return false;
      return Comparing.equal(symbolicRefs.getUsername(), text);
    }
  }

  private class SimpleRenderer extends ColoredTableCellRenderer {
    private final SimpleTextAttributes myAtt;
    private final boolean myShowLoading;

    public SimpleRenderer(SimpleTextAttributes att, boolean showLoading) {
      myAtt = att;
      myShowLoading = showLoading;
    }

    @Override
    protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
      setBackground(getLogicBackground(selected, row));
      if (BigTableTableModel.LOADING == value) {
        if (myShowLoading) {
          append("Loading...");
        }
        return;
      }
      append(value.toString(), myAtt);
    }
  }

  private class DescriptionRenderer implements TableCellRenderer {
    private final Map<String, Icon> myTagMap;
    private final Map<String, Icon> myBranchMap;
    private final JPanel myPanel;
    private final Inner myInner;
    private int myCurrentWidth;

    private DescriptionRenderer() {
      myInner = new Inner();
      myTagMap = new HashMap<String, Icon>();
      myBranchMap = new HashMap<String, Icon>();
      myPanel = new JPanel();
      final BoxLayout layout = new BoxLayout(myPanel, BoxLayout.X_AXIS);
      myPanel.setLayout(layout);
      myCurrentWidth = 0;
    }

    public void resetIcons() {
      myBranchMap.clear();
      myTagMap.clear();
    }

    public int getCurrentWidth() {
      return myCurrentWidth;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      myCurrentWidth = 0;
      if (value instanceof GitCommit) {
        final GitCommit commit = (GitCommit)value;
        final int localSize = commit.getLocalBranches() == null ? 0 : commit.getLocalBranches().size();
        final int remoteSize = commit.getRemoteBranches() == null ? 0 : commit.getRemoteBranches().size();
        final int tagsSize = commit.getTags().size();

        if (localSize + remoteSize > 0) {
          final String branch = localSize == 0 ? (commit.getRemoteBranches().get(0)) : commit.getLocalBranches().get(0);

          Icon icon = myBranchMap.get(branch);
          if (icon == null) {
            final boolean plus = localSize + remoteSize + tagsSize > 1;
            final Color color = localSize == 0 ? Colors.remote : Colors.local;
            icon = new CaptionIcon(color, table.getFont().deriveFont((float) table.getFont().getSize() - 1), branch, table,
                                   CaptionIcon.Form.SQUARE, plus, branch.equals(commit.getCurrentBranch()));
            myBranchMap.put(branch, icon);
          }
          addOneIcon(table, value, isSelected, hasFocus, row, column, icon);
          return myPanel;
        }
        if ((localSize + remoteSize == 0) && (tagsSize > 0)) {
          final String tag = commit.getTags().get(0);
          Icon icon = myTagMap.get(tag);
          if (icon == null) {
            icon = new CaptionIcon(Colors.tag, table.getFont().deriveFont((float) table.getFont().getSize() - 1),
                                   tag, table, CaptionIcon.Form.ROUNDED, tagsSize > 1, false);
            myTagMap.put(tag, icon);
          }
          addOneIcon(table, value, isSelected, hasFocus, row, column, icon);
          return myPanel;
        }
      }
      myInner.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      return myInner;
    }

    private void addOneIcon(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column, Icon icon) {
      myCurrentWidth = icon.getIconWidth();
      myPanel.removeAll();
      myPanel.setBackground(getLogicBackground(isSelected, row));
      myPanel.add(new JLabel(icon));
      myInner.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      myPanel.add(myInner);
    }

    private class Inner extends HighLightingRenderer {
      private final IssueLinkRenderer myIssueLinkRenderer;
      private final Consumer<String> myConsumer;

      private Inner() {
        super(HIGHLIGHT_TEXT_ATTRIBUTES, null);
        myIssueLinkRenderer = new IssueLinkRenderer(myProject, this);
        myConsumer = new Consumer<String>() {
          @Override
          public void consume(String s) {
            myWorker.tryHighlight(s);
          }
        };
      }
      @Override
      protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
        setBackground(getLogicBackground(selected, row));
        if (value instanceof GitCommit) {
          final GitCommit gitCommit = (GitCommit)value;
          myIssueLinkRenderer.appendTextWithLinks(gitCommit.getDescription(), SimpleTextAttributes.REGULAR_ATTRIBUTES, myConsumer);
          //super.customizeCellRenderer(table, ((GitCommit) value).getDescription(), selected, hasFocus, row, column);
        } else {
          super.customizeCellRenderer(table, value, selected, hasFocus, row, column);
        }
      }
    }
  }

  private Color getLogicBackground(final boolean isSelected, final int row) {
    Color bkgColor;
    final CommitI commitAt = myTableModel.getCommitAt(row);
    GitCommit gitCommit = null;
    if (commitAt != null && (! commitAt.holdsDecoration())) {
      gitCommit = fullCommitPresentation(commitAt);
    }

    if (isSelected) {
      bkgColor = UIUtil.getTableSelectionBackground();
    } else {
      bkgColor = UIUtil.getTableBackground();
      if (gitCommit != null) {
        if (myDetailsCache.getStashName(commitAt.selectRepository(myRootsUnderVcs), gitCommit.getShortHash()) != null) {
          bkgColor = Colors.stashed;
        } else if (gitCommit.isOnLocal() && gitCommit.isOnTracked()) {
          bkgColor = Colors.commonThisBranch;
        } else if (gitCommit.isOnLocal()) {
          bkgColor = Colors.ownThisBranch;
        }
      }
    }
    return bkgColor;
  }

  private ColumnInfo<Object, String> AUTHOR;

  private void initAuthor() {
    AUTHOR = new ColumnInfo<Object, String>("Author") {
      @Override
      public String valueOf(Object o) {
        if (o instanceof GitCommit) {
          return ((GitCommit)o).getAuthor();
        }
        return "";
      }

      @Override
      public TableCellRenderer getRenderer(Object o) {
        return myAuthorRenderer;
      }
    };
  }

  private final ColumnInfo<Object, String> DATE = new ColumnInfo<Object, String>("Date") {
    private final TableCellRenderer myRenderer = new SimpleRenderer(SimpleTextAttributes.REGULAR_ATTRIBUTES, false);

    @Override
    public String valueOf(Object o) {
      if (o instanceof GitCommit) {
        return DateFormatUtil.formatPrettyDateTime(((GitCommit)o).getDate());
      }
      return "";
    }

    @Override
    public TableCellRenderer getRenderer(Object o) {
      return myRenderer;
    }
  };

  public void setDetailsCache(DetailsCache detailsCache) {
    myDetailsCache = detailsCache;
  }

  private class MyRefreshAction extends DumbAwareAction {
    private MyRefreshAction() {
      super("Refresh", "Refresh", IconLoader.getIcon("/actions/sync.png"));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      rootsChanged(myRootsUnderVcs);
    }
  }

  private class MyTextFieldAction extends SearchFieldAction {
    private MyTextFieldAction() {
      super("Find:");
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      checkIfFilterChanged();
    }

    private void checkIfFilterChanged() {
      final String newValue = getText().trim();
      if (! Comparing.equal(myPreviousFilter, newValue)) {
        myPreviousFilter = newValue;

        reloadRequest();
      }
    }
  }

  private void reloadRequest() {
    myState = StepType.CONTINUE;
    final int was = myTableModel.getRowCount();
    myDetailsCache.resetAsideCaches();
    final Collection<String> startingPoints = mySelectedBranch == null ? Collections.<String>emptyList() : Collections.singletonList(mySelectedBranch);
    myDescriptionRenderer.resetIcons();
    final boolean commentFilterEmpty = StringUtil.isEmptyOrSpaces(myPreviousFilter);
    myCommentSearchContext.clear();
    myUsersSearchContext.clear();

    if (commentFilterEmpty && (myUserFilterI.myFilter == null) && myStructureFilter.myAllSelected) {
      myUsersSearchContext.clear();
      myMediator.reload(new RootsHolder(myRootsUnderVcs), startingPoints, new GitLogFilters());
    } else {
      ChangesFilter.Comment comment = null;
      if (! commentFilterEmpty) {
        final String commentFilter = myCommentSearchContext.preparse(myPreviousFilter);
        comment = new ChangesFilter.Comment(commentFilter);
      }
      Set<ChangesFilter.Filter> userFilters = null;
      if (myUserFilterI.myFilter != null) {
        final String[] strings = myUserFilterI.myFilter.split(",");
        userFilters = new HashSet<ChangesFilter.Filter>();
        for (String string : strings) {
          string = string.trim();
          if (string.length() == 0) continue;
          myUsersSearchContext.add(string.toLowerCase());
          final String regexp = StringUtil.escapeToRegexp(string);
          userFilters.add(new ChangesFilter.Committer(regexp));
          userFilters.add(new ChangesFilter.Author(regexp));
        }
      }
      Map<VirtualFile, ChangesFilter.Filter> structureFilters = null;
      if (! myStructureFilter.myAllSelected) {
        structureFilters = new HashMap<VirtualFile, ChangesFilter.Filter>();
        final Collection<VirtualFile> selected = new ArrayList<VirtualFile>(myStructureFilter.getSelected());
        final ArrayList<VirtualFile> copy = new ArrayList<VirtualFile>(myRootsUnderVcs);
        Collections.sort(copy, FilePathComparator.getInstance());
        Collections.reverse(copy);
        for (VirtualFile root : copy) {
          final Collection<VirtualFile> selectedForRoot = new SmartList<VirtualFile>();
          final Iterator<VirtualFile> iterator = selected.iterator();
          while (iterator.hasNext()) {
            VirtualFile next = iterator.next();
            if (VfsUtil.isAncestor(root, next, false)) {
              selectedForRoot.add(next);
              iterator.remove();
            }
          }
          if (! selectedForRoot.isEmpty()) {
            final ChangesFilter.StructureFilter structureFilter = new ChangesFilter.StructureFilter();
            structureFilter.addFiles(selectedForRoot);
            structureFilters.put(root, structureFilter);
          }
        }
      }

      final List<String> possibleReferencies = commentFilterEmpty ? null : Arrays.asList(myPreviousFilter.split("[\\s]"));
      myMediator.reload(new RootsHolder(myRootsUnderVcs), startingPoints, new GitLogFilters(comment, userFilters, structureFilters,
                                                                                            possibleReferencies));
    }
    myCommentSearchContext.addHighlighter(myDetailsPanel.getHtmlHighlighter());
    updateMoreVisibility();
    mySelectionRequestsMerger.request();
    fireTableRepaint();
    myTableModel.fireTableRowsDeleted(0, was);
  }

  interface Colors {
    Color tag = new Color(241, 239, 158);
    Color remote = new Color(188,188,252);
    Color local = new Color(117,238,199);
    Color ownThisBranch = new Color(198,255,226);
    Color commonThisBranch = new Color(223,223,255);
    Color stashed = new Color(225,225,225);
  }

  private class MyCherryPick extends DumbAwareAction {
    private final Set<AbstractHash> myIdsInProgress;

    private MyCherryPick() {
      super("Cherry-pick", "Cherry-pick", IconLoader.getIcon("/icons/cherryPick.png"));
      myIdsInProgress = new HashSet<AbstractHash>();
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final MultiMap<VirtualFile, GitCommit> commits = getSelectedCommitsAndCheck();
      if (commits == null) return;
      final int result = Messages.showOkCancelDialog("You are going to cherry-pick changes into current branch. Continue?", "Cherry-pick",
                                                     Messages.getQuestionIcon());
      if (result != 0) return;
      for (GitCommit commit : commits.values()) {
        myIdsInProgress.add(commit.getShortHash());
      }

      final Application application = ApplicationManager.getApplication();
      application.executeOnPooledThread(new Runnable() {
        public void run() {
          for (VirtualFile file : commits.keySet()) {
            final List<GitCommit> part = (List<GitCommit>)commits.get(file);
            // earliest first!!!
            Collections.reverse(part);
            new CherryPicker(GitVcs.getInstance(myProject), part, new LowLevelAccessImpl(myProject, file)).execute();
          }

          application.invokeLater(new Runnable() {
            public void run() {
              for (GitCommit commit : commits.values()) {
                myIdsInProgress.remove(commit.getShortHash());
              }
            }
          });
        }
      });
    }

    // newest first
    @Nullable
    private MultiMap<VirtualFile, GitCommit> getSelectedCommitsAndCheck() {
      if (myJBTable == null) return null;
      final int[] rows = myJBTable.getSelectedRows();
      final MultiMap<VirtualFile, GitCommit> hashes = new MultiMap<VirtualFile, GitCommit>();

      for (int row : rows) {
        final CommitI commitI = myTableModel.getCommitAt(row);
        if (commitI == null) return null;
        if (commitI.holdsDecoration()) return null;
        if (myIdsInProgress.contains(commitI.getHash())) return null;
        final VirtualFile root = commitI.selectRepository(myRootsUnderVcs);
        final GitCommit gitCommit = myDetailsCache.convert(root, commitI.getHash());
        if (gitCommit == null) return null;
        hashes.putValue(root, gitCommit);
      }
      return hashes;
    }

    @Override
    public void update(AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(enabled());
    }

    private boolean enabled() {
      final MultiMap<VirtualFile, GitCommit> commitsAndCheck = getSelectedCommitsAndCheck();
      if (commitsAndCheck == null) return false;
      for (VirtualFile root : commitsAndCheck.keySet()) {
        final SymbolicRefs refs = myRefs.get(root);
        final String currentBranch = refs == null ? null : (refs.getCurrent() == null ? null : refs.getCurrent().getName());
        if (currentBranch == null) continue;
        final Collection<GitCommit> commits = commitsAndCheck.get(root);
        for (GitCommit commit : commits) {
          if (commit.getParentsHashes().size() > 1) return false;
          final List<String> branches = myDetailsCache.getBranches(root, commit.getShortHash());
          if (branches != null && branches.contains(currentBranch)) {
            return false;
          }
        }
      }
      return true;
    }
  }

  private void updateMoreVisibility() {
    if (StepType.PAUSE.equals(myState)) {
      myMoreAction.setEnabled(true);
      myMoreAction.setVisible(true);
    } else if (StepType.CONTINUE.equals(myState)) {
      myMoreAction.setVisible(true);
      myMoreAction.setEnabled(false);
    } else {
      myMoreAction.setVisible(false);
    }
  }

  private static class MyFilterUi implements UserFilterI {
    private boolean myMeIsKnown;
    private String myMe;
    private String myFilter;
    private final Runnable myReloadCallback;

    public MyFilterUi(Runnable reloadCallback) {
      myReloadCallback = reloadCallback;
    }

    @Override
    public void allSelected() {
      myFilter = null;
      myReloadCallback.run();
    }

    @Override
    public void meSelected() {
      myFilter = myMe;
      myReloadCallback.run();
    }

    @Override
    public void filter(String s) {
      myFilter = s;
      myReloadCallback.run();
    }

    @Override
    public boolean isMeKnown() {
      return myMeIsKnown;
    }

    @Override
    public String getMe() {
      return myMe;
    }

    public void setMe(final String me) {
      myMeIsKnown = ! StringUtil.isEmptyOrSpaces(me);
      myMe = me == null ? "" : me.trim();
    }
  }

  private static class MyStructureFilter implements StructureFilterI {
    private boolean myAllSelected;
    private final List<VirtualFile> myFiles;
    private final Runnable myReloadCallback;
    private final Getter<List<VirtualFile>> myGetter;

    private MyStructureFilter(Runnable reloadCallback, final Getter<List<VirtualFile>> getter) {
      myReloadCallback = reloadCallback;
      myGetter = getter;
      myFiles = new ArrayList<VirtualFile>();
      myAllSelected = true;
    }

    @Override
    public void allSelected() {
      if (myAllSelected) return;
      myAllSelected = true;
      myReloadCallback.run();
    }

    @Override
    public void select(Collection<VirtualFile> files) {
      myAllSelected = false;
      if (Comparing.haveEqualElements(files, myFiles)) return;
      myFiles.clear();
      myFiles.addAll(files);
      myReloadCallback.run();
    }

    @Override
    public Collection<VirtualFile> getSelected() {
      return myFiles;
    }

    @Override
    public List<VirtualFile> getRoots() {
      return myGetter.get();
    }
  }

  public void setProjectScope(boolean projectScope) {
    myRootsAction.setEnabled(! projectScope);
  }

  private static class MyRootsAction extends AnAction {
    private boolean myEnabled;
    private final Getter<List<VirtualFile>> myRootsGetter;
    private final JComponent myComponent;

    private MyRootsAction(final Getter<List<VirtualFile>> rootsGetter, final JComponent component) {
      super("Show roots", "Show roots", IconLoader.getIcon("/general/balloonInformation.png"));
      myRootsGetter = rootsGetter;
      myComponent = component;
      myEnabled = false;
    }

    public void setEnabled(boolean enabled) {
      myEnabled = enabled;
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myEnabled);
      e.getPresentation().setVisible(myEnabled);
      super.update(e);
    }

    @Override
    public void actionPerformed(final AnActionEvent e) {
      List<VirtualFile> virtualFiles = myRootsGetter.get();
      assert virtualFiles != null && virtualFiles.size() > 0;
      SortedListModel sortedListModel = new SortedListModel(null);
      final JBList jbList = new JBList(sortedListModel);
      sortedListModel.add("Roots:");
      for (VirtualFile virtualFile : virtualFiles) {
        sortedListModel.add(virtualFile.getPath());
      }

      JBPopup popup = JBPopupFactory.getInstance().createComponentPopupBuilder(jbList, jbList).createPopup();
      if (e.getInputEvent() instanceof MouseEvent) {
        popup.show(new RelativePoint((MouseEvent)e.getInputEvent()));
      } else {
        popup.showInBestPositionFor(e.getDataContext());
      }
    }
  }
}
