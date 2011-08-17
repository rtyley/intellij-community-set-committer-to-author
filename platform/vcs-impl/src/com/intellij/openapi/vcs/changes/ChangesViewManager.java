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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 11.07.2006
 * Time: 15:29:25
 */
package com.intellij.openapi.vcs.changes;

import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.TreeExpander;
import com.intellij.ide.actions.ContextHelpAction;
import com.intellij.lifecycle.PeriodicalTasksCloser;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.event.EditorEventMulticaster;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.actions.IgnoredSettingsAction;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import com.intellij.util.PairConsumer;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.SLRUMap;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.vcsUtil.UIVcsUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ChangesViewManager implements ChangesViewI, JDOMExternalizable, ProjectComponent {
  public static final int UNVERSIONED_MAX_SIZE = 50;
  public static final Icon detailsIcon = IconLoader.getIcon("/vcs/volute.png");
  private boolean SHOW_FLATTEN_MODE = true;
  private boolean SHOW_IGNORED_MODE = false;

  private final ChangesListView myView;
  private JLabel myProgressLabel;

  private final Alarm myRepaintAlarm;

  private boolean myDisposed = false;

  private final ChangeListListener myListener = new MyChangeListListener();
  private final Project myProject;
  private final ChangesViewContentManager myContentManager;
  private final VcsChangeDetailsManager myVcsChangeDetailsManager;

  @NonNls private static final String ATT_FLATTENED_VIEW = "flattened_view";
  @NonNls private static final String ATT_SHOW_IGNORED = "show_ignored";
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.ChangesViewManager");
  private Splitter mySplitter;
  private DetailsPanel myDetailsPanel;
  private GenericDetailsLoader<Change, Pair<RefreshablePanel, Disposable>> myDetailsLoader;
  private boolean myDetailsOn;
  private ChangesViewManager.MyFileListener myFileListener;
  private final SLRUMap<FilePath, Pair<RefreshablePanel, Disposable>> myDetailsCache;
  private FilePath myDetailsFilePath;
  private final MyDocumentListener myDocumentListener;
  private ZipperUpdater myDetailsUpdater;
  private Runnable myUpdateDetails;
  private MessageBusConnection myConnection;
  private ChangesViewManager.ToggleDetailsAction myToggleDetailsAction;
  private PairConsumer<Change,Pair<RefreshablePanel, Disposable>> myDetailsConsumer;
  private final TreeSelectionListener myTsl;

  public static ChangesViewI getInstance(Project project) {
    return PeriodicalTasksCloser.getInstance().safeGetComponent(project, ChangesViewI.class);
  }

  public ChangesViewManager(Project project, ChangesViewContentManager contentManager, final VcsChangeDetailsManager vcsChangeDetailsManager) {
    myProject = project;
    myContentManager = contentManager;
    myVcsChangeDetailsManager = vcsChangeDetailsManager;
    myView = new ChangesListView(project);

    Disposer.register(project, myView);
    myRepaintAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, project);
    myFileListener = new MyFileListener();
    myDocumentListener = new MyDocumentListener();
    myDetailsCache = new SLRUMap<FilePath, Pair<RefreshablePanel, Disposable>>(10, 10) {
      @Override
      protected void onDropFromCache(FilePath key, Pair<RefreshablePanel, Disposable> value) {
        if (value.getSecond() != null) {
          Disposer.dispose(value.getSecond());
        }
      }
    };
    myDetailsUpdater = new ZipperUpdater(300, Alarm.ThreadToUse.SWING_THREAD, myProject);
    myUpdateDetails = new Runnable() {
      @Override
      public void run() {
        changeDetails();
      }
    };
    myTsl = new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        changeDetails();
      }
    };
  }

  public void projectOpened() {
    final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    changeListManager.addChangeListListener(myListener);
    Disposer.register(myProject, new Disposable() {
      public void dispose() {
        changeListManager.removeChangeListListener(myListener);
      }
    });
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) return;
    final Content content = ContentFactory.SERVICE.getInstance().createContent(createChangeViewComponent(), "Local", false);
    content.setCloseable(false);
    myContentManager.addContent(content);

    scheduleRefresh();
    myConnection = myProject.getMessageBus().connect(myProject);
    myConnection.subscribe(RemoteRevisionsCache.REMOTE_VERSION_CHANGED, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            refreshView();
          }
        }, ModalityState.NON_MODAL, myProject.getDisposed());
      }
    });
  }

  public void projectClosed() {
    if (myToggleDetailsAction.isSelected(null)) {
      VirtualFileManager.getInstance().removeVirtualFileListener(myFileListener);
      EditorFactory.getInstance().getEventMulticaster().removeDocumentListener(myDocumentListener);
    }
    myDetailsPanel.clear();
    myView.removeTreeSelectionListener(myTsl);
    myConnection.disconnect();
    myDetailsCache.clear();
    myDisposed = true;
    myRepaintAlarm.cancelAllRequests();
  }

  @NonNls @NotNull
  public String getComponentName() {
    return "ChangesViewManager";
  }

  private JComponent createChangeViewComponent() {
    SimpleToolWindowPanel panel = new SimpleToolWindowPanel(false, true);

    DefaultActionGroup group = (DefaultActionGroup) ActionManager.getInstance().getAction("ChangesViewToolbar");

    ActionManager.getInstance().getAction("ChangesView.Refresh").registerCustomShortcutSet(CommonShortcuts.getRerun(), panel);
    ActionManager.getInstance().getAction("ChangesView.NewChangeList").registerCustomShortcutSet(CommonShortcuts.getNew(), panel);
    ActionManager.getInstance().getAction("ChangesView.RemoveChangeList").registerCustomShortcutSet(CommonShortcuts.DELETE, panel);
    ActionManager.getInstance().getAction("ChangesView.Move").registerCustomShortcutSet(CommonShortcuts.getMove(), panel);
    ActionManager.getInstance().getAction("ChangesView.Rename").registerCustomShortcutSet(CommonShortcuts.getRename(), panel);
    ActionManager.getInstance().getAction("ChangesView.SetDefault").registerCustomShortcutSet(
      new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_U, KeyEvent.ALT_DOWN_MASK | ctrlMask())), panel);

    final CustomShortcutSet diffShortcut =
      new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_D, ctrlMask()));
    ActionManager.getInstance().getAction("ChangesView.Diff").registerCustomShortcutSet(diffShortcut, panel);

    JPanel toolbarPanel = new JPanel(new BorderLayout());
    toolbarPanel.add(createToolbarComponent(group), BorderLayout.WEST);

    DefaultActionGroup visualActionsGroup = new DefaultActionGroup();
    final Expander expander = new Expander();
    visualActionsGroup.add(CommonActionsManager.getInstance().createExpandAllAction(expander, panel));
    visualActionsGroup.add(CommonActionsManager.getInstance().createCollapseAllAction(expander, panel));

    ToggleShowFlattenAction showFlattenAction = new ToggleShowFlattenAction();
    showFlattenAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_P,
                                                                                             ctrlMask())),
                                                panel);
    visualActionsGroup.add(showFlattenAction);
    visualActionsGroup.add(ActionManager.getInstance().getAction(IdeActions.ACTION_COPY));                                              
    visualActionsGroup.add(new ToggleShowIgnoredAction());
    visualActionsGroup.add(new IgnoredSettingsAction());
    myToggleDetailsAction = new ToggleDetailsAction();
    visualActionsGroup.add(myToggleDetailsAction);
    visualActionsGroup.add(new ContextHelpAction(ChangesListView.ourHelpId));
    toolbarPanel.add(createToolbarComponent(visualActionsGroup), BorderLayout.CENTER);


    DefaultActionGroup menuGroup = (DefaultActionGroup) ActionManager.getInstance().getAction("ChangesViewPopupMenu");
    myView.setMenuActions(menuGroup);

    myView.setShowFlatten(SHOW_FLATTEN_MODE);

    myProgressLabel = new JLabel();

    panel.setToolbar(toolbarPanel);

    final JPanel content = new JPanel(new BorderLayout());
    mySplitter = new Splitter(false, 0.5f);
    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myView);
    final JPanel wrapper = new JPanel(new BorderLayout());
    wrapper.setBorder(BorderFactory.createLineBorder(UIUtil.getBorderColor()));
    wrapper.add(scrollPane, BorderLayout.CENTER);
    mySplitter.setShowDividerControls(true);
    mySplitter.setFirstComponent(wrapper);
    content.add(mySplitter, BorderLayout.CENTER);
    content.add(myProgressLabel, BorderLayout.SOUTH);
    panel.setContent(content);

    myDetailsPanel = new DetailsPanel();
    initDetailsLoader();

    myView.installDndSupport(ChangeListManagerImpl.getInstanceImpl(myProject));
    myView.addTreeSelectionListener(myTsl);
    return panel;
  }

  private void initDetailsLoader() {
    final PairConsumer<Change, Pair<RefreshablePanel, Disposable>> cacheConsumer = new PairConsumer<Change, Pair<RefreshablePanel, Disposable>>() {
      @Override
      public void consume(Change change, Pair<RefreshablePanel, Disposable> pair) {
        final FilePath filePath = ChangesUtil.getFilePath(change);
        final Pair<RefreshablePanel, Disposable> old = myDetailsCache.get(filePath);
        if (old == null) {
          myDetailsCache.put(filePath, pair);
        } else if (old != pair) {
          if (pair.getSecond() != null) {
            Disposer.dispose(pair.getSecond());
          }
        }
      }
    };
    myDetailsConsumer = new PairConsumer<Change, Pair<RefreshablePanel, Disposable>>() {
      @Override
      public void consume(Change change, Pair<RefreshablePanel, Disposable> pair) {
        cacheConsumer.consume(change, pair);
        pair.getFirst().refresh();
        myDetailsPanel.data(pair.getFirst().getPanel());
        myDetailsPanel.layout();
      }
    };
    myDetailsLoader = new GenericDetailsLoader<Change, Pair<RefreshablePanel, Disposable>>(new Consumer<Change>() {
      @Override
      public void consume(Change change) {
        final FilePath filePath = ChangesUtil.getFilePath(change);
        Pair<RefreshablePanel, Disposable> details = myDetailsCache.get(filePath);
        if (details != null) {
          myDetailsConsumer.consume(change, details);
        } else if (myVcsChangeDetailsManager.getPanel(change, myDetailsLoader)) {
          myDetailsPanel.loading();
          myDetailsPanel.layout();
        }
      }
    }, myDetailsConsumer);
    myDetailsLoader.setCacheConsumer(cacheConsumer);
  }

  private void changeDetails() {
    if (! myDetailsOn) {
      if (mySplitter.getSecondComponent() != null) {
        setChangeDetailsPanel(null);
      }
    } else {
      setDetails();
      myDetailsPanel.layout();

      if (mySplitter.getSecondComponent() == null) {
        setChangeDetailsPanel(myDetailsPanel.myPanel);
      }
    }
  }

  private void setDetails() {
    final Change[] selectedChanges = myView.getSelectedChanges();
    if (selectedChanges.length == 0) {
      myDetailsPanel.nothingSelected();
    } else {
      final String freezed = ChangeListManager.getInstance(myProject).isFreezed();
      if (freezed != null) {
        myDetailsPanel.data(UIVcsUtil.errorPanel(freezed, false));
        return;
      }

      myDetailsPanel.notAvailable();
      for (Change change : selectedChanges) {
        if (change.getBeforeRevision() instanceof FakeRevision || change.getAfterRevision() instanceof FakeRevision) {
          myDetailsPanel.loadingInitial();
          return;
        }
        if (myVcsChangeDetailsManager.canComment(change)) {
          myDetailsFilePath = ChangesUtil.getFilePath(change);
          myDetailsLoader.updateSelection(change, true);
          return;
        }
      }

      myDetailsPanel.notAvailable();
    }
  }

  private void setChangeDetailsPanel(@Nullable final JComponent component) {
    mySplitter.setSecondComponent(component);
    mySplitter.revalidate();
    mySplitter.repaint();
  }

  private int ctrlMask() {
    return SystemInfo.isMac ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;
  }

  private static JComponent createToolbarComponent(final DefaultActionGroup group) {
    final ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.CHANGES_VIEW_TOOLBAR, group, false);
    return actionToolbar.getComponent();
  }

  public void updateProgressText(final String text, final boolean isError) {
    if (myProgressLabel != null) {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          myProgressLabel.setText(text);
          myProgressLabel.setForeground(isError ? Color.red : UIUtil.getLabelForeground());
        }
      });
    }
  }

  @Override
  public void scheduleRefresh() {
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) return;
    if (myProject == null || myProject.isDisposed()) { return; }
    int was = myRepaintAlarm.cancelAllRequests();
    if (LOG.isDebugEnabled()) {
      LOG.debug("schedule refresh, was " + was);
    }
    myRepaintAlarm.addRequest(new Runnable() {
      public void run() {
        refreshView();
      }
    }, 100, ModalityState.NON_MODAL);
  }

  void refreshView() {
    if (myDisposed || ! myProject.isInitialized() || ApplicationManager.getApplication().isUnitTestMode()) return;
    if (! ProjectLevelVcsManager.getInstance(myProject).hasActiveVcss()) return;

    ChangeListManagerImpl changeListManager = ChangeListManagerImpl.getInstanceImpl(myProject);

    final Pair<Integer, Integer> unv = changeListManager.getUnversionedFilesSize();
    final boolean manyUnversioned = unv.getFirst() > UNVERSIONED_MAX_SIZE;
    final Trinity<List<VirtualFile>, Integer, Integer> unversionedPair =
      new Trinity<List<VirtualFile>, Integer, Integer>(manyUnversioned ? Collections.<VirtualFile>emptyList() : changeListManager.getUnversionedFiles(), unv.getFirst(),
                                                       unv.getSecond());

    if (LOG.isDebugEnabled()) {
      LOG.debug("refresh view, unversioned collections size: " + unversionedPair.getFirst().size() + " unv size passed: " +
      unversionedPair.getSecond() + " dirs: " + unversionedPair.getThird());
    }
    myView.updateModel(changeListManager.getChangeListsCopy(), unversionedPair,
                       changeListManager.getDeletedFiles(),
                       changeListManager.getModifiedWithoutEditing(),
                       changeListManager.getSwitchedFilesMap(),
                       changeListManager.getSwitchedRoots(),
                       SHOW_IGNORED_MODE ? changeListManager.getIgnoredFiles() : null, changeListManager.getLockedFolders(),
                       changeListManager.getLogicallyLockedFolders());
  }

  public void readExternal(Element element) throws InvalidDataException {
    SHOW_FLATTEN_MODE = Boolean.valueOf(element.getAttributeValue(ATT_FLATTENED_VIEW)).booleanValue();
    SHOW_IGNORED_MODE = Boolean.valueOf(element.getAttributeValue(ATT_SHOW_IGNORED)).booleanValue();
  }

  public void writeExternal(Element element) throws WriteExternalException {
    element.setAttribute(ATT_FLATTENED_VIEW, String.valueOf(SHOW_FLATTEN_MODE));
    element.setAttribute(ATT_SHOW_IGNORED, String.valueOf(SHOW_IGNORED_MODE));
  }

  @Override
  public void selectFile(final VirtualFile vFile) {
    if (vFile == null) return;
    Change change = ChangeListManager.getInstance(myProject).getChange(vFile);
    Object objectToFind = change != null ? change : vFile;

    DefaultMutableTreeNode root = (DefaultMutableTreeNode)myView.getModel().getRoot();
    DefaultMutableTreeNode node = TreeUtil.findNodeWithObject(root, objectToFind);
    if (node != null) {
      TreeUtil.selectNode(myView, node);
    }
  }

  @Override
  public void refreshChangesViewNodeAsync(final VirtualFile file) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        if (myProject.isDisposed()) return;
        refreshChangesViewNode(file);
      }
    });
  }

  private void refreshChangesViewNode(final VirtualFile file) {
    DefaultMutableTreeNode root = (DefaultMutableTreeNode)myView.getModel().getRoot();
    Object userObject;
    final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    if (changeListManager.isUnversioned(file)) {
      userObject = file;
    }
    else {
      userObject = changeListManager.getChange(file);
    }
    if (userObject != null) {
      final DefaultMutableTreeNode node = TreeUtil.findNodeWithObject(root, userObject);
      if (node != null) {
        myView.getModel().nodeChanged(node);
      }
    }
  }

  private class MyChangeListListener extends ChangeListAdapter {

    public void changeListAdded(ChangeList list) {
      scheduleRefresh();
    }

    public void changeListRemoved(ChangeList list) {
      scheduleRefresh();
    }

    public void changeListRenamed(ChangeList list, String oldName) {
      scheduleRefresh();
    }

    public void changesMoved(Collection<Change> changes, ChangeList fromList, ChangeList toList) {
      scheduleRefresh();
    }

    public void defaultListChanged(final ChangeList oldDefaultList, ChangeList newDefaultList) {
      scheduleRefresh();
    }

    public void changeListUpdateDone() {
      scheduleRefresh();
      ChangeListManagerImpl changeListManager = ChangeListManagerImpl.getInstanceImpl(myProject);
      VcsException updateException = changeListManager.getUpdateException();
      if (updateException == null) {
        updateProgressText("", false);
      }
      else {
        updateProgressText(VcsBundle.message("error.updating.changes", updateException.getMessage()), true);
      }
    }
  }

  private class Expander implements TreeExpander {
    public void expandAll() {
      TreeUtil.expandAll(myView);
    }

    public boolean canExpand() {
      return true;
    }

    public void collapseAll() {
      TreeUtil.collapseAll(myView, 2);
      TreeUtil.expand(myView, 1);
    }

    public boolean canCollapse() {
      return true;
    }
  }

  public class ToggleShowFlattenAction extends ToggleAction implements DumbAware {
    public ToggleShowFlattenAction() {
      super(VcsBundle.message("changes.action.show.directories.text"),
            VcsBundle.message("changes.action.show.directories.description"),
            PlatformIcons.DIRECTORY_CLOSED_ICON);
    }

    public boolean isSelected(AnActionEvent e) {
      return !SHOW_FLATTEN_MODE;
    }

    public void setSelected(AnActionEvent e, boolean state) {
      SHOW_FLATTEN_MODE = !state;
      myView.setShowFlatten(SHOW_FLATTEN_MODE);
      refreshView();
    }
  }

  public class ToggleShowIgnoredAction extends ToggleAction implements DumbAware {
    public ToggleShowIgnoredAction() {
      super(VcsBundle.message("changes.action.show.ignored.text"),
            VcsBundle.message("changes.action.show.ignored.description"),
            IconLoader.getIcon("/actions/showHiddens.png"));
    }

    public boolean isSelected(AnActionEvent e) {
      return SHOW_IGNORED_MODE;
    }

    public void setSelected(AnActionEvent e, boolean state) {
      SHOW_IGNORED_MODE = state;
      refreshView();
    }
  }

  @Override
  public void disposeComponent() {
  }

  @Override
  public void initComponent() {
  }

  private class ToggleDetailsAction extends ToggleAction implements DumbAware {
    private ToggleDetailsAction() {
      super("Change details", "Change details", detailsIcon);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myDetailsOn;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      final VirtualFileManager manager = VirtualFileManager.getInstance();
      final EditorEventMulticaster multicaster = EditorFactory.getInstance().getEventMulticaster();
      if (myDetailsOn) {
        manager.removeVirtualFileListener(myFileListener);
        multicaster.removeDocumentListener(myDocumentListener);
      } else {
        manager.addVirtualFileListener(myFileListener);
        multicaster.addDocumentListener(myDocumentListener);
      }
      myDetailsOn = ! myDetailsOn;
      changeDetails();
    }
  }

  private class MyFileListener extends VirtualFileAdapter {
    @Override
    public void contentsChanged(VirtualFileEvent event) {
      impl(event.getFile());
    }

    @Override
    public void fileCreated(VirtualFileEvent event) {
      impl(event.getFile());
    }

    @Override
    public void fileDeleted(VirtualFileEvent event) {
      impl(event.getFile());
    }
  }

  private void impl(final VirtualFile vf) {
    final boolean wasInCache = myDetailsCache.remove(new FilePathImpl(vf));
    if (wasInCache || (myDetailsFilePath != null && myDetailsFilePath.getVirtualFile() != null && myDetailsFilePath.getVirtualFile().equals(vf))) {
      myDetailsUpdater.queue(myUpdateDetails);
    }
  }

  private class MyDocumentListener implements DocumentListener {
    private final FileDocumentManager myFileDocumentManager;

    public MyDocumentListener() {
      myFileDocumentManager = FileDocumentManager.getInstance();
    }

    @Override
    public void beforeDocumentChange(DocumentEvent event) {
    }

    @Override
    public void documentChanged(DocumentEvent event) {
      final VirtualFile vf = myFileDocumentManager.getFile(event.getDocument());
      if (vf != null) {
        impl(vf);
      }
    }
  }

  private static class DetailsPanel {
    private CardLayout myLayout;
    private JPanel myPanel;
    private JPanel myDataPanel;
    private Layer myCurrentLayer;

    private DetailsPanel() {
      myPanel = new JPanel();
      myLayout = new CardLayout();
      myPanel.setLayout(myLayout);
      myDataPanel = new JPanel(new BorderLayout());

      myPanel.add(UIVcsUtil.errorPanel("No details available", false), Layer.notAvailable.name());
      myPanel.add(UIVcsUtil.errorPanel("Nothing selected", false), Layer.nothingSelected.name());
      myPanel.add(UIVcsUtil.errorPanel("Changes content is not loaded yet", false), Layer.notLoadedInitial.name());
      myPanel.add(UIVcsUtil.errorPanel("Loading...", false), Layer.loading.name());
      myPanel.add(myDataPanel, Layer.data.name());
    }

    public void nothingSelected() {
      myCurrentLayer = Layer.nothingSelected;
    }

    public void notAvailable() {
      myCurrentLayer = Layer.notAvailable;
    }

    public void loading() {
      myCurrentLayer = Layer.loading;
    }

    public void loadingInitial() {
      myCurrentLayer = Layer.notLoadedInitial;
    }

    public void data(final JPanel panel) {
      myCurrentLayer = Layer.data;
      myPanel.add(panel, Layer.data.name());
    }

    public void layout() {
      myLayout.show(myPanel, myCurrentLayer.name());
    }

    public void clear() {
      myPanel.removeAll();
    }

    private static enum Layer {
      notAvailable,
      nothingSelected,
      notLoadedInitial,
      loading,
      data,
    }
  }
}
