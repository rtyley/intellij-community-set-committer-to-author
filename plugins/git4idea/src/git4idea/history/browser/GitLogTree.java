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
package git4idea.history.browser;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vcs.ObjectsConvertor;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.committed.AbstractCalledLater;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser;
import com.intellij.openapi.vcs.changes.committed.RepositoryChangesBrowser;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.*;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.text.DateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class GitLogTree implements GitTreeViewI {
  private final JPanel myPanel;
  private JList myCommitsList;
  private final MyCompoundCellRenderer myListCellRenderer;
  private final ManageGitTreeView myController;
  private final JLabel myStateLabel;
  private boolean myRefreshInProgress;

  private final MyErrorRefresher myErrorRefresher;
  private final Project myProject;
  private Disposable myParentDisposable;

  private final Splitter myMainSplitter;
  private final Splitter myFiltersSplitter;

  private JComponent myFiltersPane;
  private JComponent myInheritancePane;

  private CommonOnOff myFiltersOnOff;
  private CommonOnOff myInheritanceOnOff;

  private final List<Runnable> myInitWaiters;

  private final static DataKey<ManageGitTreeView> MANAGER_KEY = DataKey.create("MANAGER_KEY");
  private final static DataKey<OnOff> FILTERS_ON_OFF = DataKey.create("FILTERS_ON_OFF");
  private final static DataKey<OnOff> INHERITANCE_ON_OFF = DataKey.create("INHERITANCE_ON_OFF");
  private Set<SHAHash> myHighlightedIds;
  private TravelTicket myTicket;
  private Splitter myListWithDetails;

  private final Alarm myWaitAlarm;
  private RepositoryChangesBrowser myRepositoryChangesBrowser;

  public GitLogTree(final Project project, final VirtualFile root) {
    myProject = project;
    myWaitAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
    myPanel = new MyPanel(new BorderLayout());
    myListCellRenderer = new MyCompoundCellRenderer();
    myErrorRefresher = new MyErrorRefresher(project);
    myController = new GitTreeController(project, root, this);
    myStateLabel = new JLabel();

    myMainSplitter = new Splitter(false, 0.2f);
    myFiltersSplitter = new Splitter(false, 0.5f);
    myMainSplitter.setDividerWidth(3);
    myFiltersSplitter.setDividerWidth(3);

    myInitWaiters = new LinkedList<Runnable>();
    myCommitsList = new JList();
    myController.init();
  }

  public JComponent getFocusTarget() {
    return myCommitsList;
  }

  public void setParentDisposable(Disposable parentDisposable) {
    myParentDisposable = parentDisposable;
  }

  private class MyPanel extends JPanel implements TypeSafeDataProvider {
    private MyPanel() {
    }

    private MyPanel(boolean isDoubleBuffered) {
      super(isDoubleBuffered);
    }

    private MyPanel(LayoutManager layout) {
      super(layout);
    }

    private MyPanel(LayoutManager layout, boolean isDoubleBuffered) {
      super(layout, isDoubleBuffered);
    }

    public void calcData(DataKey key, DataSink sink) {
      if (MANAGER_KEY.equals(key)) {
        sink.put(MANAGER_KEY, myController);
      } else if (FILTERS_ON_OFF.equals(key)) {
        sink.put(FILTERS_ON_OFF, myFiltersOnOff);
      } else if (INHERITANCE_ON_OFF.equals(key)) {
        sink.put(INHERITANCE_ON_OFF, myInheritanceOnOff);
      }
    }
  }

  public void controllerReady() {
    new AbstractCalledLater(myProject, ModalityState.NON_MODAL) {
      public void run() {
        for (Runnable initWaiter : myInitWaiters) {
          initWaiter.run();
        }
      }
    }.callMe();
  }

  public void refreshView(@NotNull final List<GitCommit> commitsToShow, final TravelTicket ticket) {
    new AbstractCalledLater(myProject, ModalityState.NON_MODAL) {
      public void run() {
        final boolean wasSelected = myCommitsList.getSelectedIndices().length == 0;
        myCommitsList.setListData(ArrayUtil.toObjectArray(commitsToShow));
        if ((! commitsToShow.isEmpty()) && (wasSelected || (! Comparing.equal(myTicket, ticket)))) {
          myCommitsList.setSelectedIndex(0);
        }
        myTicket = ticket;
        myCommitsList.revalidate();
        myCommitsList.repaint();
        
        myListCellRenderer.clear();
      }
    }.callMe();
  }

  public void showStatusMessage(@NotNull final String message) {
    new AbstractCalledLater(myProject, ModalityState.NON_MODAL) {
      public void run() {
        myStateLabel.setText(message);
      }
    }.callMe();
  }

  public void refreshStarted() {
    new AbstractCalledLater(myProject, ModalityState.NON_MODAL) {
      public void run() {
        myRefreshInProgress = true;
        myStateLabel.setText("Refreshing...");
      }
    }.callMe();
  }

  public void refreshFinished() {
    new AbstractCalledLater(myProject, ModalityState.NON_MODAL) {
      public void run() {
        myRefreshInProgress = false;
        myStateLabel.setText("");
      }
    }.callMe();
  }

  public void initView() {
    myHighlightedIds = Collections.emptySet();
    
    final JPanel actionsPanel = new MyPanel(new BorderLayout());
    final DefaultActionGroup group = new DefaultActionGroup();
    final MyOpenCloseFilters openFilters = new MyOpenCloseFilters();
    group.add(openFilters);

    openFilters.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_F, KeyEvent.ALT_DOWN_MASK | KeyEvent.CTRL_DOWN_MASK)), myPanel);

    final MyOpenCloseInheritance openShow = new MyOpenCloseInheritance();
    group.add(openShow);
    openShow.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_E, KeyEvent.ALT_DOWN_MASK | KeyEvent.CTRL_DOWN_MASK)), myPanel);
    final MyPreviousAction previousAction = new MyPreviousAction();
    group.add(previousAction);
    previousAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, KeyEvent.ALT_DOWN_MASK | KeyEvent.CTRL_DOWN_MASK)), myPanel);

    final MyNextAction nextAction = new MyNextAction();
    group.add(nextAction);
    nextAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, KeyEvent.ALT_DOWN_MASK | KeyEvent.CTRL_DOWN_MASK)), myPanel);
    final MyCherryPick cp = new MyCherryPick();
    group.add(cp);
    cp.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_P, KeyEvent.ALT_DOWN_MASK | KeyEvent.CTRL_DOWN_MASK)), myPanel);
    group.add(new MyRefreshAction());

    final ActionManager am = ActionManager.getInstance();
    final ActionToolbar tb = am.createActionToolbar("GitLogTree", group, true);
    actionsPanel.add(tb.getComponent(), BorderLayout.WEST);
    actionsPanel.add(myStateLabel, BorderLayout.CENTER);

    final ActionPopupMenu popup = am.createActionPopupMenu("GitLogTree", group);
    myCommitsList.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        popup.getComponent().show(comp, x, y);
      }
    });

    myPanel.add(actionsPanel, BorderLayout.NORTH);

    myCommitsList.setCellRenderer(myListCellRenderer);

    myMainSplitter.setFirstComponent(myFiltersSplitter);
    final JScrollPane sp = new JScrollPane(myCommitsList, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

    myRepositoryChangesBrowser = new RepositoryChangesBrowser(myProject, Collections.<CommittedChangeList>emptyList(), Collections.<Change>emptyList(), null);
    myRepositoryChangesBrowser.getDiffAction().registerCustomShortcutSet(CommonShortcuts.getDiff(), myCommitsList);

    myListWithDetails = new Splitter(false, 0.7f);
    myListWithDetails.setFirstComponent(sp);
    myListWithDetails.setSecondComponent(myRepositoryChangesBrowser);
    myListWithDetails.setDividerWidth(2);

    myCommitsList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        final Object[] selected = myCommitsList.getSelectedValues();
        myRepositoryChangesBrowser.setChangesToDisplay(Collections.<Change>emptyList());
        if (selected != null && selected.length > 0) {
          myWaitAlarm.addRequest(new Runnable() {
          public void run() {
            final Object[] values = myCommitsList.getSelectedValues();
            if (Comparing.equal(selected, values)) {
              final List<SHAHash> hashes = new ArrayList<SHAHash>(values.length);
              for (Object value : values) {
                hashes.add(((GitCommit) value).getHash());
              }
              myController.getDetails(hashes);
            }
          }
        }, 100);
        }
      }
    });

    myMainSplitter.setSecondComponent(myListWithDetails);

    final JPanel filtersPanel = new JPanel(new BorderLayout());
    
    final MyFiltersTree filterControl =
      new MyFiltersTree(myProject, myController.getFiltering(), myController, myParentDisposable, "Filter", null, myCommitsList);
    filtersPanel.add(filterControl.getComponent(), BorderLayout.CENTER);

    myFiltersPane = filtersPanel;
    final JPanel inheritancePanel = new JPanel(new BorderLayout());
    final MyFiltersTree showControl =
      new MyFiltersTree(myProject, myController.getHighlighting(), myController, myParentDisposable, "Emphasize", null, myCommitsList);
    inheritancePanel.add(showControl.getComponent(), BorderLayout.CENTER);
    myInheritancePane = inheritancePanel;
    
    myFiltersOnOff = new CommonOnOff(myFiltersPane, true, filterControl.getTree());
    myInheritanceOnOff = new CommonOnOff(myInheritancePane, false, showControl.getTree());
    myFiltersOnOff.setPeer(myInheritanceOnOff);
    myInheritanceOnOff.setPeer(myFiltersOnOff);

    myPanel.add(myMainSplitter, BorderLayout.CENTER);

    new ListSpeedSearch(myCommitsList, new Function<Object, String>() {
      public String fun(Object o) {
        if (o instanceof GitCommit) {
          final GitCommit gc = (GitCommit)o;
          return gc.getDescription() + gc.getHash() + gc.getCommitter();
        }
        return null;
      }
    });
  }

  private class CommonOnOff implements OnOff {
    private boolean myState;
    private final JComponent myComponent;
    private OnOff myPeer;
    private final boolean myIsFirst;
    private final JComponent myFocusTarget;

    protected CommonOnOff(JComponent component, boolean isFirst, JComponent focusTarget) {
      myComponent = component;
      myIsFirst = isFirst;
      myFocusTarget = focusTarget;
    }

    public void setPeer(final OnOff peer) {
      myPeer = peer;
    }

    public boolean isOn() {
      return myState;
    }

    public void on() {
      if (isOn()) return;

      if (myIsFirst) {
        myFiltersSplitter.setFirstComponent(myComponent);
      } else {
        myFiltersSplitter.setSecondComponent(myComponent);
      }
      if (! myPeer.isOn()) {
        myMainSplitter.setFirstComponent(myFiltersSplitter);
        myMainSplitter.doLayout();
      }
      myFiltersSplitter.doLayout();
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          IdeFocusManager.getInstance(myProject).requestFocus(myFocusTarget, true);
        }
      });
      myState = true;
    }

    public void off() {
      if (! isOn()) return;

      if (myIsFirst) {
        myFiltersSplitter.setFirstComponent(null);
      } else {
        myFiltersSplitter.setSecondComponent(null);
      }
      myFiltersSplitter.doLayout();
      myState = false;
      if (! myPeer.isOn()) {
        myMainSplitter.setFirstComponent(null);
        myMainSplitter.doLayout();
      }
    }
  }

  public JPanel getComponent() {
    return myPanel;
  }

  // todo details?
  public void acceptError(final String text) {
    myErrorRefresher.setText(text);
    myErrorRefresher.callMe();
  }

  public void acceptHighlighted(final Set<SHAHash> ids) {
    new AbstractCalledLater(myProject, ModalityState.NON_MODAL) {
      public void run() {
        myHighlightedIds = ids;
        myCommitsList.revalidate();
        myCommitsList.repaint();
      }
    }.callMe();
  }

  public void clearHighlighted() {
    new AbstractCalledLater(myProject, ModalityState.NON_MODAL) {
      public void run() {
        myHighlightedIds = Collections.emptySet();
        myCommitsList.revalidate();
        myCommitsList.repaint();
      }
    }.callMe();
  }

  private class MyOpenCloseFilters extends ToggleAction {
    private MyOpenCloseFilters() {
      super("Open Filters", "Open Filters", IconLoader.getIcon("/icons/filter.png"));
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      final OnOff onoff = myFiltersOnOff;
      if (onoff == null) {
        return false;
      }
      return onoff.isOn();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      final OnOff onoff = myFiltersOnOff;
      if (onoff == null) {
        return;
      }
      if (state) {
        onoff.on();
      } else {
        onoff.off();
      }
    }

    @Override
    public void update(AnActionEvent e) {
      super.update(e);
      final Presentation presentation = e.getPresentation();
      presentation.setEnabled(myController.isInitialized());
      final String text = (myFiltersOnOff == null) || myFiltersOnOff.isOn() ? "Close Filters" : "Open Filters";
      presentation.setText(text);
      presentation.setDescription(text);
    }
  }

  private class MyOpenCloseInheritance extends ToggleAction {
    private MyOpenCloseInheritance() {
      super("Open Emphasize", "Open Emphasize", IconLoader.getIcon("/icons/show.png"));
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      final OnOff onoff = myInheritanceOnOff;
      if (onoff == null) {
        return false;
      }
      return onoff.isOn();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      final OnOff onoff = myInheritanceOnOff;
      if (onoff == null) {
        return;
      }
      if (state) {
        onoff.on();
      } else {
        onoff.off();
      }
    }
    @Override
    public void update(AnActionEvent e) {
      super.update(e);
      final Presentation presentation = e.getPresentation();
      presentation.setEnabled(myController.isInitialized());
      final String text = (myInheritanceOnOff == null) || myInheritanceOnOff.isOn() ? "Close Emphasize" : "Open Emphasize";
      presentation.setText(text);
      presentation.setDescription(text);
    }
  }

  private class MyNextAction extends AnAction {
    private MyNextAction() {
      super("Next Page", "Next Page", IconLoader.getIcon("/actions/nextfile.png"));
    }

    @Override
    public void update(AnActionEvent e) {
      super.update(e);

      final ManageGitTreeView manager = myController;
      e.getPresentation().setEnabled((! myRefreshInProgress) && manager != null && manager.hasNext(myTicket));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final ManageGitTreeView manager = myController;
      if (manager == null || (! manager.hasNext(myTicket))) {
        return;
      }
      manager.next(myTicket);
    }
  }

  private class MyPreviousAction extends AnAction {
    private MyPreviousAction() {
      super("Previous Page", "Previous Page", IconLoader.getIcon("/actions/prevfile.png"));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final ManageGitTreeView manager = myController;
      if (manager == null || (! manager.hasPrevious(myTicket))) {
        return;
      }
      manager.previous(myTicket);
    }
    
    @Override
    public void update(AnActionEvent e) {
      super.update(e);

      final ManageGitTreeView manager = myController;
      e.getPresentation().setEnabled((! myRefreshInProgress) && manager != null && manager.hasPrevious(myTicket));
    }
  }

  private class MyRefreshAction extends AnAction {
    private MyRefreshAction() {
      super("Refresh", "Refresh", IconLoader.getIcon("/actions/sync.png"));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myController.refresh();
    }
  }

  private class MyCherryPick extends AnAction {
    private final Set<SHAHash> myIdsInProgress;

    private MyCherryPick() {
      super("Cherry-pick", "Cherry-pick", IconLoader.getIcon("/icons/cherryPick.png"));
      myIdsInProgress = new HashSet<SHAHash>();
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final List<GitCommit> commits = getSelectedCommitsAndCheck();
      // earliest first!!!
      Collections.reverse(commits);
      if (commits == null) return;

      for (GitCommit commit : commits) {
        myIdsInProgress.add(commit.getHash());
      }

      final Application application = ApplicationManager.getApplication();
      application.executeOnPooledThread(new Runnable() {
        public void run() {
          myController.cherryPick(ObjectsConvertor.convert(commits, new Convertor<GitCommit, SHAHash>() {
            public SHAHash convert(GitCommit o) {
              return o.getHash();
            }
          }));

          application.invokeLater(new Runnable() {
            public void run() {
              for (GitCommit commit : commits) {
                myIdsInProgress.remove(commit.getHash());
              }
            }
          });
        }
      });
    }

    // newest first
    @Nullable
    private List<GitCommit> getSelectedCommitsAndCheck() {
      final Object[] selectedCommits = myCommitsList.getSelectedValues();
      if (selectedCommits.length > 0) {
        final List<GitCommit> result = new ArrayList<GitCommit>(selectedCommits.length);
        for (Object o : selectedCommits) {
          final GitCommit commit = (GitCommit)o;
          if (commit.getParentsHashes().size() > 1) {
            return null;
          }
          if (myIdsInProgress.contains(commit.getHash())) {
            return null;
          }
          result.add(commit);
        }
        return result;
      }
      return null;
    }

    @Override
    public void update(AnActionEvent e) {
      super.update(e);
      final boolean enabled = getSelectedCommitsAndCheck() != null;
      e.getPresentation().setEnabled(enabled);
    }
  }

  private static class MyErrorRefresher extends AbstractCalledLater {
    private final AtomicReference<String> myText;

    private MyErrorRefresher(final Project project) {
      super(project, ModalityState.NON_MODAL);
      myText = new AtomicReference<String>();
    }

    public void setText(final String value) {
      myText.set(value);
    }

    public void clear() {
      // todo null?
      myText.set("");
    }

    public void run() {
      // todo
    }
  }

  private final static DateFormat ourDateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);

  private class MyCompoundCellRenderer implements ListCellRenderer {
    private final JPanel myPanel;
    private final MyTextPartRenderer myTextPartRenderer;
    private final Map<String, Icon> myTagMap;
    private final Map<String, Icon> myBranchMap;

    public void clear() {
      myTagMap.clear();
      myBranchMap.clear();
    }

    private MyCompoundCellRenderer() {
      myPanel = new JPanel(new GridBagLayout());
      myTextPartRenderer = new MyTextPartRenderer();
      myTagMap = new HashMap<String, Icon>();
      myBranchMap = new HashMap<String, Icon>();
    }

    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      if (value instanceof GitCommit) {
        final GitCommit gc = (GitCommit)value;

        final List<String> branches = gc.getBranches();
        final List<String> tags = gc.getTags();
        if (branches.isEmpty() && tags.isEmpty()) {
          myTextPartRenderer.setLeftPartWidth(0);
          return myTextPartRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        }

        final GridBagConstraints gb =
          new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0);
        gb.anchor = GridBagConstraints.WEST;
        myPanel.removeAll();
        myPanel.setBackground(isSelected ? UIUtil.getListSelectionBackground() : list.getBackground());
        int leftWidth = 0;
        for (String branch : branches) {
          Icon icon = myBranchMap.get(branch);
          if (icon == null) {
            icon = new CaptionIcon(Colors.ourBranch, list.getFont(), branch, list);
            myBranchMap.put(branch, icon);
          }
          leftWidth += icon.getIconWidth();
          myPanel.add(new JLabel(icon), gb);
          ++ gb.gridx;
        }
        for (String tag : tags) {
          Icon icon = myTagMap.get(tag);
          if (icon == null) {
            icon = new CaptionIcon(Colors.ourTag, list.getFont(), tag, list);
            myTagMap.put(tag, icon);
          }
          leftWidth += icon.getIconWidth();
          myPanel.add(new JLabel(icon), gb);
          ++ gb.gridx;
        }
        myTextPartRenderer.setLeftPartWidth(leftWidth);
        gb.anchor = GridBagConstraints.NORTHWEST;
        gb.fill = GridBagConstraints.HORIZONTAL;
        gb.weightx = 1;
        myPanel.add(myTextPartRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus), gb);
        return myPanel;
      }
      return null;
    }
  }

  private class MyTextPartRenderer extends FictiveTableCellRenderer {
    private int myLeftPartWidth;

    private MyTextPartRenderer() {
      myLeftPartWidth = 0;
    }

    @Override
    protected boolean willRender(Object value) {
      return value instanceof GitCommit;
    }

    @Nullable
    @Override
    protected Color getBgrndColor(JList list, Object value, int index, boolean selected, boolean hasFocus) {
      if (value instanceof GitCommit) {
        final GitCommit commit = (GitCommit) value;
        if (selected) {
          return UIUtil.getListSelectionBackground();
        } else {
          if (myHighlightedIds.contains(commit.getHash())) {
            return Colors.ourHighlighting;
          } else {
            return UIUtil.getListBackground();
          }
        }
      }
      return null;
    }

    @Override
    protected int getParentWidth(JList list) {
      return list.getParent().getWidth() - myLeftPartWidth;
    }

    @Override
    protected Description getDescription(Object value) {
      final GitCommit node = (GitCommit)value;
      final List<Pair<String, SimpleTextAttributes>> list = new ArrayList<Pair<String, SimpleTextAttributes>>(4);
      list.add(new Pair<String, SimpleTextAttributes>(node.getHash().getValue().substring(0, 8) + " ", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES));
      list.add(new Pair<String, SimpleTextAttributes>(node.getDescription() + " ", SimpleTextAttributes.REGULAR_ATTRIBUTES));
      list.add(new Pair<String, SimpleTextAttributes>(node.getAuthor() + ", ", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES));
      // 7-8
      list.add(new Pair<String, SimpleTextAttributes>(ourDateFormat.format(node.getDate()), SimpleTextAttributes.REGULAR_ATTRIBUTES));
      return new Description(1, list) {
        @Override
        public String getMaxString(int idx) {
          if (idx == 0) {
            return "wwwwwwww";
          }
          return null;
        }
      };
    }

    @Override
    protected Trinity<String, SimpleTextAttributes, Object> getMoreTag() {
      //return new Trinity<String, SimpleTextAttributes, Object>("todo", SimpleTextAttributes.LINK_ATTRIBUTES, new Object());
      return new Trinity<String, SimpleTextAttributes, Object>(" ...", SimpleTextAttributes.REGULAR_ATTRIBUTES, new Object());
    }

    public void setLeftPartWidth(int leftPartWidth) {
      myLeftPartWidth = leftPartWidth;
    }
  }

  private class MyColoredListCellRenderer extends ColoredListCellRenderer {
    @Override
    protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
      if (value instanceof GitCommit) {
        final GitCommit commit = (GitCommit) value;

        Color usualColor;
        if (selected) {
          usualColor = UIUtil.getListSelectionBackground();
        } else {
          if (myHighlightedIds.contains(commit.getHash())) {
            usualColor = UIUtil.getToolTipBackground().darker();
          } else {
            usualColor = UIUtil.getListBackground();
          }
          /*usualColor = myHighlightedIds.contains(commit.getHash()) ? UIUtil.getToolTipBackground() : UIUtil.getListBackground();
          if ((! selected) && (index % 3 == 0)) {
            usualColor = getSecondStripeColor(usualColor);
          }*/
        }
        setBackground(usualColor);

        drawRefs(commit.getBranches(), Color.green);
        drawRefs(commit.getTags(), Color.blue);

        append(" ");
        appendAlign(60);
        String descr = commit.getDescription();
        descr = (descr.length() > 50) ? descr.substring(0, 47) + "..." : descr;
        append(descr + " ");
        
        append(commit.getAuthor(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        append(" " + commit.getDate());
      } else if (value instanceof String) {
        append((String)value);
      }
    }
    
    private void drawRefs(final List<String> referencies, final Color color) {
      if (referencies != null && (! referencies.isEmpty())) {
        final SimpleTextAttributes attrs =
          new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD | SimpleTextAttributes.STYLE_UNDERLINE, color);
        for (String reference : referencies) {
          append(reference, attrs);
          append(" ");
        }
      }
    }
  }


  private static Color getSecondStripeColor(final Color bg) {
    int delta = - 15;
    /*if (bg.getRed() < 25 || bg.getGreen() < 25 || bg.getBlue() < 25) {
      delta = 15;
    }*/
    return new Color(bg.getRed() + delta, bg.getGreen() + delta, bg.getBlue() + delta);
  }

  /*private static class BranchFilterComponent {
    private final ManageGitTreeView myController;
    private final JPanel myPanel;
    private final JList myInclude;
    //private final JList myExclude;
    private final List<String> myIncludedData;
    private int myTagsStartIdx;

    private BranchFilterComponent(final ManageGitTreeView controller, final List<Runnable> initWaiters) {
      myController = controller;

      myPanel = new JPanel(new BorderLayout());
      myInclude = new JList();
      myIncludedData = new LinkedList<String>();
      //myExclude = new JList();

      initView(initWaiters);
    }

    private void initView(final List<Runnable> initWaiters) {
      //BorderFactory.createEmptyBorder(1,1,1,1)
      final Border border = BorderFactory.createTitledBorder("Only branches/tags:");
      myPanel.setBorder(border);
      myInclude.setListData(new Object[0]);
      //myPanel.add(new JLabel("Only branches/tags:"), BorderLayout.NORTH);

      final DefaultActionGroup group = new DefaultActionGroup();
      final MyAddAction addAction = new MyAddAction();
      addAction.registerCustomShortcutSet(CommonShortcuts.INSERT, myPanel);
      group.add(addAction);
      final MyRemoveAction removeAction = new MyRemoveAction();
      removeAction.registerCustomShortcutSet(CommonShortcuts.DELETE, myPanel);
      group.add(removeAction);
      final ActionToolbar tb = ActionManager.getInstance().createActionToolbar("branchFilter", group, true);
      myPanel.add(tb.getComponent(), BorderLayout.NORTH);

      //final JList selectedList = new JList();
      //selectedList.setPreferredSize(new Dimension());
      final JScrollPane listScroll = new JScrollPane(myInclude) {
        @Override
        public Dimension getPreferredSize() {
          return super.getPreferredSize();
        }
      };
      myPanel.add(listScroll, BorderLayout.CENTER);

    }

    public JPanel getPanel() {
      return myPanel;
    }
  }*/

  public void acceptDetails(List<CommittedChangeList> changeList) {
    final List<Change> changes = CommittedChangesTreeBrowser.collectChanges(changeList, false);

    myListWithDetails.doLayout();
    myRepositoryChangesBrowser.setChangesToDisplay(changes);
    myRepositoryChangesBrowser.repaint();
  }

  private static class MyFiltersTree {
    private final JPanel myPanel;
    private final Project myProject;
    private final GitTreeFiltering myFiltering;
    private final RepositoryCommonData myCommonData;
    private final Disposable myParentDisposable;
    private final String myTitle;
    private MyTreeStructure myStructure;
    private JTree myTree;
    private AbstractTreeBuilder myAtb;
    private final Icon myChildrenIcon;
    private final JList myRefreshComponent;

    private MyFiltersTree(final Project project, final GitTreeFiltering filtering, final RepositoryCommonData commonData,
                          final Disposable parentDisposable, String title, Icon childrenIcon, final JList refreshComponent) {
      myProject = project;
      myFiltering = filtering;
      myCommonData = commonData;
      myParentDisposable = parentDisposable;
      myTitle = title;
      myChildrenIcon = childrenIcon;
      myRefreshComponent = refreshComponent;
      myPanel = new JPanel(new BorderLayout());
      initView(childrenIcon);
    }

    public JComponent getComponent() {
      return myPanel;
    }

    public JComponent getTree() {
      return myTree;
    }

    private static class MyTreeStructure extends AbstractTreeStructure {
      private final MyRootNode myRoot;

      private MyTreeStructure(final RepositoryCommonData commonData, final GitTreeFiltering filtering, final Project project,
                              final Icon childrenIcon) {
        myRoot = new MyRootNode(project);
        myRoot.addChild(new BranchHeader(commonData, filtering, project, myRoot, childrenIcon));
        myRoot.addChild(new TagHeader(commonData, filtering, project, myRoot, childrenIcon));
        myRoot.addChild(new UserHeader(commonData, filtering, project, myRoot, childrenIcon));
      }

      @Override
      public void commit() {
      }
      @Override
      public Object getRootElement() {
        return myRoot;
      }
      @Override
      public Object[] getChildElements(Object element) {
        if (element instanceof MyAbstractNode) {
          // todo remove convertion???
          final List children = ((MyAbstractNode)element).getChildren();
          return children.toArray(new Object[children.size()]);
        }
        throw new IllegalStateException();
      }
      @Override
      public Object getParentElement(Object element) {
        if (element instanceof MyAbstractNode) {
          return ((MyAbstractNode)element).getParent();
        }
        throw new IllegalStateException();
      }
      @NotNull
      @Override
      public NodeDescriptor createDescriptor(Object element, NodeDescriptor parentDescriptor) {
        if (element instanceof MyAbstractNode) {
          return ((MyAbstractNode)element).getDescriptor();
        }
        throw new IllegalStateException();
      }
      @Override
      public boolean hasSomethingToCommit() {
        return false;
      }
    }

    private void initView(Icon childrenIcon) {
      myStructure = new MyTreeStructure(myCommonData, myFiltering, myProject, childrenIcon);
      myTree = new JTree();

      myAtb = new AbstractTreeBuilder(myTree, new DefaultTreeModel(new DefaultMutableTreeNode()), myStructure, null);
      myAtb.initRootNode();
      Disposer.register(myParentDisposable, myAtb);
      myTree.setDragEnabled(false);
      myTree.setShowsRootHandles(true);
      myTree.setRootVisible(false);
      myTree.setCellRenderer(new NodeRenderer());

      final DefaultActionGroup group = new DefaultActionGroup();
      final MyAddAction addAction = new MyAddAction();
      addAction.registerCustomShortcutSet(CommonShortcuts.INSERT, myPanel);
      group.add(addAction);
      final MyRemoveAction removeAction = new MyRemoveAction();
      removeAction.registerCustomShortcutSet(CommonShortcuts.DELETE, myPanel);
      group.add(removeAction);
      final ActionManager am = ActionManager.getInstance();
      final ActionPopupMenu actionPopupMenu = am.createActionPopupMenu("branchFilter", group);

      myTree.addMouseListener(new PopupHandler() {
        @Override
        public void invokePopup(Component comp, int x, int y) {
          actionPopupMenu.getComponent().show(comp, x, y);
        }
      });
//      myPanel.add(tb.getComponent(), BorderLayout.NORTH);
      final JLabel titleLabel = new JLabel(myTitle);
      titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
      myPanel.add(titleLabel, BorderLayout.NORTH);

      myPanel.add(new JScrollPane(myTree), BorderLayout.CENTER);

      //tree.set
    }

    /*@Nullable
    private Object[] getSelectedNodes() {
      //final TreePath[] paths = myTree.getSelectionPaths();
      final Set<Object> selectedElements = myAtb.getSelectedElements();
      if (selectedElements == null) return null;
      final Object[] result = new Object[selectedElements.length];
      for (int i = 0; i < paths.length; i++) {
        final TreePath path = paths[i];
        result[i] = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
      }
      return result;
    }*/

    @Nullable
    private Object getFirstSelected() {
      final Set<Object> selected = myAtb.getSelectedElements();
      if (selected.isEmpty()) return null;
      return selected.iterator().next();
    }

    private boolean nodesAreOfSameType() {
      final Set<Object> selectedValues = myAtb.getSelectedElements();
      if (selectedValues.size() == 0) return false;
      NodeType type = null;
      for (Object selectedValue : selectedValues) {
        if (! (selectedValue instanceof MyAbstractNode)) {
          return false;
        }
        final MyAbstractNode node = (MyAbstractNode) selectedValue;
        if ((type != null) && (! type.equals(node.getNodeType()))) {
          return false;
        }
        type = node.getNodeType();
      }
      return true;
    }

    private class MyAddAction extends AnAction {
      private final Consumer<Object> myAfterAction;
      //private TreeState myState;

      private MyAddAction() {
        super("Add to filter", "Add to filter", IconLoader.getIcon("/general/add.png"));
        myAfterAction = new Consumer<Object>() {
          public void consume(final Object object) {
            myAtb.addSubtreeToUpdate((DefaultMutableTreeNode) myTree.getModel().getRoot(), new Runnable() {
              public void run() {
                myAtb.expand(object, null);
                SwingUtilities.invokeLater(new Runnable() {
                  public void run() {
                    myRefreshComponent.revalidate();
                    myRefreshComponent.repaint();
                  }
                });
              }
            });
          }
        };
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        if (! nodesAreOfSameType()) return;
        final Object selectedValue = getFirstSelected();
        if (! ((MyAbstractNode) selectedValue).canInsert()) return;

        final MyAbstractNode node = (MyAbstractNode)selectedValue;
        node.onInsert(e.getDataContext(), myAfterAction);
      }

      @Override
      public void update(AnActionEvent e) {
        super.update(e);
        e.getPresentation().setEnabled(nodesAreOfSameType() && ((MyAbstractNode) getFirstSelected()).canInsert());
      }
    }

    private class MyRemoveAction extends AnAction {
      private final Runnable myAfterAction;

      private MyRemoveAction() {
        super("Remove from filter", "Remove from filter", IconLoader.getIcon("/general/remove.png"));
        myAfterAction = new Runnable() {
          public void run() {
            myAtb.addSubtreeToUpdate((DefaultMutableTreeNode) myTree.getModel().getRoot());
            SwingUtilities.invokeLater(new Runnable() {
              public void run() {
                myRefreshComponent.revalidate();
                myRefreshComponent.repaint();
              }
            });
          }
        };
      }

      @Override
      public void update(AnActionEvent e) {
        super.update(e);

        e.getPresentation().setEnabled(canDelete());
      }

      private boolean canDelete() {
        if (! nodesAreOfSameType()) return false;
        final Set<Object> selectedElements = myAtb.getSelectedElements();
        for (Object object : selectedElements) {
          if (! ((MyAbstractNode) object).canBeDeleted()) return false;
        }
        return true;
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        if (! canDelete()) return;

        final Set<Object> selectedElements = myAtb.getSelectedElements();
        for (Object value : selectedElements) {
          ((MyAbstractNode) value).onDelete(myAfterAction);
        }
        myAfterAction.run();
//        myAtb.addSubtreeToUpdate(myAtb.getRootNode());
        //myAtb.addSubtreeToUpdate((DefaultMutableTreeNode) myTree.getModel().getRoot());

        /*((DefaultTreeModel) myTree.getModel()).reload();
        myTree.revalidate();
        myTree.repaint();*/
      }
    }

    /*private static class MyFilterCellRenderer extends ColoredTreeCellRenderer {
      @Override
      public void customizeCellRenderer(JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {
        if (value instanceof MyAbstractNode) {
          final MyAbstractNode abstractNode = (MyAbstractNode) value;
          abstractNode.renderItself(selected, expanded, leaf, row, hasFocus, this);
        }
      }
    }*/

    private static class BranchNode extends MyAbstractNode<BranchHeader, Object> {
      private BranchNode(RepositoryCommonData commonData,
                         GitTreeFiltering filtering,
                         Project project, BranchHeader branchHeader,
                         String text) {
        super(false, commonData, filtering, project, Color.green, branchHeader, text, NodeType.branch, null);
      }

      @Override
      boolean canBeDeleted() {
        return true;
      }
      @Override
      NodeType getNodeType() {
        return NodeType.branch;
      }
      @Override
      boolean canInsert() {
        return true;
      }
      @Override
      void onInsert(DataContext dc, final Consumer<Object> after) {
        final List<String> list = myCommonData.getAllBranchesOrdered();

        SelectGitBranchesPopup.showMe(new Consumer<Object[]>() {
          public void consume(Object[] objects) {
            for (Object o : objects) {
              final String branchName = ((String) o);

              final BranchNode newNode = new BranchNode(myCommonData, myFiltering, myProject, getParent(), branchName);
              getParent().addChild(newNode);

              // put into controller
              myFiltering.addStartingPoint(branchName);
              after.consume(getParent());
            }
          }
        }, list, false, dc);
      }

      @Override
      void onDelete(Runnable after) {
        myFiltering.removeStartingPoint(getText());
        getParent().removeChild(this);
      }
    }

    private static class TagNode extends MyAbstractNode<TagHeader, Object> {
      private TagNode(RepositoryCommonData commonData, GitTreeFiltering filtering, Project project, TagHeader tagHeader, String text,
                      final Icon icon) {
        super(false, commonData, filtering, project, Color.blue, tagHeader, text, NodeType.tag, icon);
      }

      @Override
      boolean canBeDeleted() {
        return true;
      }
      @Override
      boolean canInsert() {
        return true;
      }
      @Override
      void onInsert(DataContext dc, final Consumer<Object> after) {
        final List<String> list = myCommonData.getAllTagsOrdered();

        SelectGitBranchesPopup.showMe(new Consumer<Object[]>() {
          public void consume(Object[] objects) {
            for (Object o : objects) {
              final String branchName = ((String) o);

              final TagNode newNode = new TagNode(myCommonData, myFiltering, myProject, myParent, branchName, myIcon);
              getParent().addChild(newNode);

              // put into controller
              myFiltering.addStartingPoint(branchName);
              after.consume(getParent());
            }
          }
        }, list, true, dc);
      }

      @Override
      void onDelete(Runnable after) {
        myFiltering.removeStartingPoint(getText());
        getParent().removeChild(this);
      }
    }

    private static class BranchHeader extends MyAbstractNode<MyRootNode, BranchNode> {
      private final Icon myChildrenIcon;

      private BranchHeader(RepositoryCommonData commonData,
                           GitTreeFiltering filtering,
                           Project project,
                           MyRootNode parent,
                           Icon childrenIcon) {
        super(true, commonData, filtering, project, null, parent, "Branches", NodeType.branchesHead, null);
        myChildrenIcon = childrenIcon;
      }

      @Override
      boolean canBeDeleted() {
        return false;
      }
      @Override
      boolean canInsert() {
        return true;
      }
      @Override
      void onInsert(DataContext dc, final Consumer<Object> after) {
        final List<String> list = myCommonData.getAllBranchesOrdered();

        SelectGitBranchesPopup.showMe(new Consumer<Object[]>() {
          public void consume(Object[] objects) {
            for (Object o : objects) {
              final String branchName = ((String) o);

              final BranchNode newNode = new BranchNode(myCommonData, myFiltering, myProject, BranchHeader.this, branchName);
              addChild(newNode);

              // put into controller
              myFiltering.addStartingPoint(branchName);

              after.consume(BranchHeader.this);
            }
          }
        }, list, false, dc);
      }

      @Override
      void onDelete(Runnable after) {
      }
    }

    private static class TagHeader extends MyAbstractNode<MyRootNode, TagNode> {
      private final Icon myChildrenIcon;

      private TagHeader(RepositoryCommonData commonData, GitTreeFiltering filtering, Project project, MyRootNode parent, Icon childrenIcon) {
        super(true, commonData, filtering, project, null, parent, "Tags", NodeType.tagsHead, null);
        myChildrenIcon = childrenIcon;
      }

      @Override
      boolean canBeDeleted() {
        return false;
      }
      @Override
      boolean canInsert() {
        return true;
      }
      @Override
      void onInsert(DataContext dc, final Consumer<Object> after) {
        final List<String> list = myCommonData.getAllTagsOrdered();

        SelectGitBranchesPopup.showMe(new Consumer<Object[]>() {
          public void consume(Object[] objects) {
            for (Object o : objects) {
              final String branchName = ((String) o);

              final TagNode newNode = new TagNode(myCommonData, myFiltering, myProject, TagHeader.this, branchName, myChildrenIcon);
              addChild(newNode);

              // put into controller
              myFiltering.addStartingPoint(branchName);
              after.consume(TagHeader.this);
            }
          }
        }, list, true, dc);
      }

      @Override
      void onDelete(Runnable after) {
      }
    }

    private static class UserNode extends MyAbstractNode<UserHeader, Object> {
      private final ChangesFilter.Filter[] myFilters;

      private UserNode(RepositoryCommonData commonData,
                       GitTreeFiltering filtering,
                       Project project,
                       UserHeader userHeader,
                       String text, ChangesFilter.Filter[] filters, Icon icon) {
        super(false, commonData, filtering, project, UIUtil.getTextAreaForeground(), userHeader, text, NodeType.user, icon);
        myFilters = filters;
      }
      @Override
      boolean canBeDeleted() {
        return true;
      }
      @Override
      boolean canInsert() {
        return true;
      }

      @Override
      void onInsert(DataContext dc, final Consumer<Object> after) {
        UsersPopup.showUsersPopup(myCommonData.getKnownUsers(), new Consumer<String>() {
          public void consume(String name) {
            final ChangesFilter.Committer committer = new ChangesFilter.Committer(name);
            myFiltering.addFilter(committer);
            final ChangesFilter.Author author = new ChangesFilter.Author(name);
            myFiltering.addFilter(author);

            final UserNode newNode = new UserNode(myCommonData, myFiltering, myProject, getParent(), name,
                                                  new ChangesFilter.Filter[] {committer, author}, myIcon);
            getParent().addChild(newNode);

            after.consume(getParent());
          }
        }, dc);
      }

      @Override
      void onDelete(Runnable after) {
        for (ChangesFilter.Filter filter : myFilters) {
          myFiltering.removeFilter(filter);
        }
        getParent().removeChild(this);
      }
    }

    private static class UserHeader extends MyAbstractNode<MyRootNode, UserNode> {
      private final Icon myChildrenIcon;

      private UserHeader(RepositoryCommonData commonData, GitTreeFiltering filtering, Project project, MyRootNode parent, Icon childrenIcon) {
        super(true, commonData, filtering, project, null, parent, "Users", NodeType.userHead, null);
        myChildrenIcon = childrenIcon;
      }

      @Override
      boolean canBeDeleted() {
        return false;
      }
      @Override
      boolean canInsert() {
        return true;
      }
      @Override
      void onInsert(DataContext dc, final Consumer<Object> after) {
        UsersPopup.showUsersPopup(myCommonData.getKnownUsers(), new Consumer<String>() {
          public void consume(String name) {
            final ChangesFilter.Committer committer = new ChangesFilter.Committer(name);
            myFiltering.addFilter(committer);
            final ChangesFilter.Author author = new ChangesFilter.Author(name);
            myFiltering.addFilter(author);

            final UserNode newNode = new UserNode(myCommonData, myFiltering, myProject, UserHeader.this, name,
                                                  new ChangesFilter.Filter[] {committer, author}, myChildrenIcon);
            addChild(newNode);

            after.consume(UserHeader.this);
          }
        }, dc);
      }

      @Override
      void onDelete(Runnable after) {
      }
    }

    private static abstract class MyAbstractNode<Parent extends MyAbstractNode, Child> {
      protected final Project myProject;
      protected final GitTreeFiltering myFiltering;
      private final boolean myAllowsChildren;
      protected final RepositoryCommonData myCommonData;
      @Nullable
      private final Color myColor;

      protected final Parent myParent;
      private List<Child> myChildren;

      private final String myText;
      private final NodeType myType;
      @Nullable protected final Icon myIcon;
      private final NodeDescriptor<MyAbstractNode> myDescriptor;

      protected MyAbstractNode(boolean allowsChildren,
                               RepositoryCommonData commonData,
                               GitTreeFiltering filtering,
                               Project project, @Nullable Color color, final Parent parent, final String text, NodeType type,
                               @Nullable final Icon icon) {
        myAllowsChildren = allowsChildren;
        myCommonData = commonData;
        myFiltering = filtering;
        myProject = project;
        myColor = color;
        myParent = parent;
        myText = text;
        myType = type;
        myIcon = icon;
        myChildren = myAllowsChildren ? new LinkedList<Child>() : Collections.<Child>emptyList();
        myDescriptor = new PresentableNodeDescriptor(myProject, myParent == null ? null : myParent.getDescriptor()) {
          @Override
          public PresentableNodeDescriptor getChildToHighlightAt(int index) {
            return null;
          }
          @Override
          protected void update(PresentationData presentation) {
            presentation.setClosedIcon(icon);
            presentation.setOpenIcon(icon);
            presentation.setPresentableText(myText);
          }

          @Override
          public Object getElement() {
            return MyAbstractNode.this;
          }
        };
      }

      public NodeDescriptor getDescriptor() {
        return myDescriptor;
      }

      void addChild(final Child child) {
        assert myAllowsChildren;
        myChildren.add(child);
      }

      public Parent getParent() {
        return myParent;
      }

      boolean isAllowsChildren() {
        return myAllowsChildren;
      }

      public String getText() {
        return myText;
      }

      public Color getColor() {
        return myColor;
      }

      public List<Child> getChildren() {
        return myChildren;
      }
      public void removeChild(final Child child) {
        myChildren.remove(child);
      }

      NodeType getNodeType() {
        return myType;
      }

      abstract boolean canInsert(); // peer or child
      abstract boolean canBeDeleted(); //self
      abstract void onInsert(DataContext dc, Consumer<Object> after);
      abstract void onDelete(Runnable after);
      //abstract void renderItself(boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus, final SimpleColoredComponent coloredComponent);


      @Override
      public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MyAbstractNode that = (MyAbstractNode)o;

        if (myText != null ? !myText.equals(that.myText) : that.myText != null) return false;
        if (myType != that.myType) return false;

        return true;
      }

      @Override
      public int hashCode() {
        int result = myText != null ? myText.hashCode() : 0;
        result = 31 * result + myType.hashCode();
        return result;
      }
    }

    private static class MyRootNode extends MyAbstractNode<MyRootNode, MyAbstractNode> {
      private MyRootNode(final Project project) {
        super(true, null, null, project, null, null, "Root", NodeType.root, null);
      }
      @Override
      boolean canBeDeleted() {
        return false;
      }
      @Override
      boolean canInsert() {
        return false;
      }
      @Override
      void onInsert(DataContext dc, Consumer<Object> after) {
      }
      @Override
      void onDelete(Runnable after) {
      }
    }

    private static enum NodeType {
      root,
      branchesHead,
      tagsHead,
      branch,
      tag,
      userHead,
      user,
      before,
      after,
      selectedHeader,
      selectedNode
    }
  }

  private interface Colors {    
    Color ourHighlighting = new Color(255, 255, 200);
    Color ourBranch = new Color(226, 255, 174);
    Color ourTag = new Color(193, 223, 247);
  }
}
