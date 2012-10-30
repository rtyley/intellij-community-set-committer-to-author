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

package com.intellij.execution.impl;

import com.intellij.execution.*;
import com.intellij.execution.configuration.ConfigurationFactoryEx;
import com.intellij.execution.configurations.*;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.*;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IconUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.config.StorageAccessors;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.EditableModel;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import gnu.trove.THashSet;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.execution.impl.RunConfigurable.NodeKind.*;

class RunConfigurable extends BaseConfigurable {

  private static final Icon ADD_ICON = IconUtil.getAddIcon();
  private static final Icon REMOVE_ICON = IconUtil.getRemoveIcon();
  private static final Icon SHARED_ICON = IconLoader.getTransparentIcon(AllIcons.Nodes.Symlink, .6f);
  private static final Icon NON_SHARED_ICON = EmptyIcon.ICON_16;
  @NonNls private static final String DIVIDER_PROPORTION = "dividerProportion";
  @NonNls private static final Object DEFAULTS = new Object();

  private volatile boolean isDisposed = false;

  private final Project myProject;
  private RunDialogBase myRunDialog;
  @NonNls private final DefaultMutableTreeNode myRoot = new DefaultMutableTreeNode("Root");
  private final MyTreeModel myTreeModel = new MyTreeModel(myRoot);
  private final Tree myTree = new Tree(myTreeModel);
  private final JPanel myRightPanel = new JPanel(new BorderLayout());
  private final Splitter mySplitter = new Splitter(false);
  private JPanel myWholePanel;
  private final StorageAccessors myProperties = StorageAccessors.createGlobal("RunConfigurable");
  private Configurable mySelectedConfigurable = null;
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.impl.RunConfigurable");
  private final JTextField myRecentsLimit = new JTextField("5", 2);
  private final JCheckBox myConfirmation = new JCheckBox(ExecutionBundle.message("rerun.confirmation.checkbox"), true);
  private final List<Pair<UnnamedConfigurable, JComponent>> myAdditionalSettings = new ArrayList<Pair<UnnamedConfigurable, JComponent>>();
  private Map<ConfigurationFactory, Configurable> myStoredComponents = new HashMap<ConfigurationFactory, Configurable>();
  private ToolbarDecorator myToolbarDecorator;

  public RunConfigurable(final Project project) {
    this(project, null);
  }

  public RunConfigurable(final Project project, @Nullable final RunDialogBase runDialog) {
    myProject = project;
    myRunDialog = runDialog;
  }

  public String getDisplayName() {
    return ExecutionBundle.message("run.configurable.display.name");
  }

  private void initTree() {
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    UIUtil.setLineStyleAngled(myTree);
    TreeUtil.installActions(myTree);
    new TreeSpeedSearch(myTree, new Convertor<TreePath, String>() {
      @Override
      public String convert(TreePath o) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)o.getLastPathComponent();
        final Object userObject = node.getUserObject();
        if (userObject instanceof RunnerAndConfigurationSettingsImpl) {
          return ((RunnerAndConfigurationSettingsImpl)userObject).getName();
        }
        else if (userObject instanceof SingleConfigurationConfigurable) {
          return ((SingleConfigurationConfigurable)userObject).getNameText();
        }
        else {
          if (userObject instanceof ConfigurationType) {
            return ((ConfigurationType)userObject).getDisplayName();
          }
          else if (userObject instanceof String) {
            return (String)userObject;
          }
        }
        return o.toString();
      }
    });
    myTree.setCellRenderer(new ColoredTreeCellRenderer() {
      public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row,
                                        boolean hasFocus) {
        if (value instanceof DefaultMutableTreeNode) {
          final DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
          final DefaultMutableTreeNode parent = (DefaultMutableTreeNode)node.getParent();
          final Object userObject = node.getUserObject();
          Boolean shared = null;
          if (userObject instanceof ConfigurationType) {
            final ConfigurationType configurationType = (ConfigurationType)userObject;
            append(configurationType.getDisplayName(), parent.isRoot() ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
            setIcon(configurationType.getIcon());
          }
          else if (userObject == DEFAULTS) {
            append("Defaults", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
            setIcon(AllIcons.General.Settings);
          }
          else if (userObject instanceof String) {//Folders
            append((String)userObject, SimpleTextAttributes.REGULAR_ATTRIBUTES);
            setIcon(AllIcons.Nodes.Folder);
          }
          else if (userObject instanceof ConfigurationFactory) {
            append(((ConfigurationFactory)userObject).getName());
            setIcon(((ConfigurationFactory)userObject).getIcon());
          }
          else {
            final RunManagerImpl runManager = getRunManager();
            RunConfiguration configuration = null;
            String name = null;
            if (userObject instanceof SingleConfigurationConfigurable) {
              final SingleConfigurationConfigurable<?> settings = (SingleConfigurationConfigurable)userObject;
              RunnerAndConfigurationSettings snapshot;
              snapshot = settings.getSettings();
              configuration = settings.getConfiguration();
              name = settings.getNameText();
              shared = runManager.isConfigurationShared(snapshot);
              setIcon(ProgramRunnerUtil.getConfigurationIcon(snapshot, !settings.isValid(), runManager.isTemporary(configuration)));
            }
            else if (userObject instanceof RunnerAndConfigurationSettingsImpl) {
              RunnerAndConfigurationSettings settings = (RunnerAndConfigurationSettings)userObject;
              shared = runManager.isConfigurationShared(settings);
              setIcon(RunManagerEx.getInstanceEx(myProject).getConfigurationIcon(settings));
              configuration = settings.getConfiguration();
              name = configuration.getName();
            }
            if (configuration != null) {
              append(name, runManager.isTemporary(configuration)
                           ? SimpleTextAttributes.GRAY_ATTRIBUTES
                           : SimpleTextAttributes.REGULAR_ATTRIBUTES);
            }
          }
          if (shared != null) {
            Icon icon = getIcon();
            LayeredIcon layeredIcon = new LayeredIcon(2);
            layeredIcon.setIcon(icon, 0, 0, 0);
            layeredIcon.setIcon(shared ? SHARED_ICON : NON_SHARED_ICON, 1, 8, 0);
            setIcon(layeredIcon);
            setIconTextGap(0);
          } else {
            setIconTextGap(2);
          }
        }
      }
    });
    final RunManagerEx manager = getRunManager();
    final ConfigurationType[] factories = manager.getConfigurationFactories();
    for (ConfigurationType type : factories) {
      final RunnerAndConfigurationSettings[] configurations = manager.getConfigurationSettings(type);
      if (configurations.length > 0) {
        final DefaultMutableTreeNode typeNode = new DefaultMutableTreeNode(type);
        myRoot.add(typeNode);
        List<String> folders = manager.getFolders(type);
        Map<String, DefaultMutableTreeNode> folderMapping = new HashMap<String, DefaultMutableTreeNode>();
        for (String folder : folders) {
          DefaultMutableTreeNode folderNode = new DefaultMutableTreeNode(folder);
          typeNode.add(folderNode);
          folderMapping.put(folder, folderNode);
        }
        for (RunnerAndConfigurationSettings configuration : configurations) {
          if (configuration.getFolderName() != null) {
            folderMapping.get(configuration.getFolderName()).add(new DefaultMutableTreeNode(configuration));
          } else {
            typeNode.add(new DefaultMutableTreeNode(configuration));
          }
        }
      }
    }

    // add defaults
    final DefaultMutableTreeNode defaults = new DefaultMutableTreeNode(DEFAULTS);
    final ConfigurationType[] configurationTypes = RunManagerImpl.getInstanceImpl(myProject).getConfigurationFactories();
    for (final ConfigurationType type : configurationTypes) {
      if (!(type instanceof UnknownConfigurationType)) {
        ConfigurationFactory[] configurationFactories = type.getConfigurationFactories();
        DefaultMutableTreeNode typeNode = new DefaultMutableTreeNode(type);
        defaults.add(typeNode);
        if (configurationFactories.length != 1) {
          for (ConfigurationFactory factory : configurationFactories) {
            typeNode.add(new DefaultMutableTreeNode(factory));
          }
        }
      }
    }
    if (defaults.getChildCount() > 0) myRoot.add(defaults);

    myTree.addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        final TreePath selectionPath = myTree.getSelectionPath();
        if (selectionPath != null) {
          DefaultMutableTreeNode node = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
          final Object userObject = node.getUserObject();
          if (userObject instanceof RunnerAndConfigurationSettingsImpl) {
            final SingleConfigurationConfigurable<RunConfiguration> configurationConfigurable =
                SingleConfigurationConfigurable.editSettings((RunnerAndConfigurationSettings)userObject, null);
            installUpdateListeners(configurationConfigurable);
            node.setUserObject(configurationConfigurable);
            updateRightPanel(configurationConfigurable);
          }
          else if (userObject instanceof SingleConfigurationConfigurable) {
            updateRightPanel((SingleConfigurationConfigurable<RunConfiguration>)userObject);
          }
          else if (userObject instanceof String && userObject != DEFAULTS) {
            showFolderField(getSelectedConfigurationType(), node, (String)userObject);
          }
          else {
            if (userObject instanceof ConfigurationType || userObject == DEFAULTS) {
              final DefaultMutableTreeNode parent = (DefaultMutableTreeNode)node.getParent();
              if (parent.isRoot()) {
                drawPressAddButtonMessage(userObject instanceof String || userObject == DEFAULTS ? null : (ConfigurationType)userObject);
              } else {
                final ConfigurationType type = (ConfigurationType)userObject;
                ConfigurationFactory[] factories = type.getConfigurationFactories();
                if (factories.length == 1) {
                  final ConfigurationFactory factory = factories[0];
                  showTemplateConfigurable(factory);
                }
                else {
                  drawPressAddButtonMessage((ConfigurationType)userObject);
                }
              }
            }
            else if (userObject instanceof ConfigurationFactory) {
              showTemplateConfigurable((ConfigurationFactory)userObject);
            }
          }
        }
        updateDialog();
      }
    });
    myTree.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        clickDefaultButton();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_FOCUSED);
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        if (isDisposed) return;

        myTree.requestFocusInWindow();
        final RunnerAndConfigurationSettings settings = manager.getSelectedConfiguration();
        if (settings != null) {
          final Enumeration enumeration = myRoot.breadthFirstEnumeration();
          while (enumeration.hasMoreElements()) {
            final DefaultMutableTreeNode node = (DefaultMutableTreeNode)enumeration.nextElement();
            final Object userObject = node.getUserObject();
            if (userObject instanceof RunnerAndConfigurationSettingsImpl) {
              final RunnerAndConfigurationSettings runnerAndConfigurationSettings = (RunnerAndConfigurationSettings)userObject;
              final ConfigurationType configurationType = settings.getType();
              if (configurationType != null &&
                  Comparing.strEqual(runnerAndConfigurationSettings.getConfiguration().getType().getId(), configurationType.getId()) &&
                  Comparing.strEqual(runnerAndConfigurationSettings.getConfiguration().getName(), settings.getName())) {
                TreeUtil.selectInTree(node, true, myTree);
                return;
              }
            }
          }
        }
        else {
          mySelectedConfigurable = null;
        }
        TreeUtil.selectInTree(defaults, true, myTree);
        drawPressAddButtonMessage(null);
      }
    });
    sortTopLevelBranches();
    ((DefaultTreeModel)myTree.getModel()).reload();
  }

  private void showTemplateConfigurable(ConfigurationFactory factory) {
    Configurable configurable = myStoredComponents.get(factory);
    if (configurable == null){
      configurable = new TemplateConfigurable(RunManagerImpl.getInstanceImpl(myProject).getConfigurationTemplate(factory));
      myStoredComponents.put(factory, configurable);
      configurable.reset();
    }
    updateRightPanel(configurable);
  }

  private void showFolderField(final ConfigurationType type, final DefaultMutableTreeNode node, final String folderName) {
    myRightPanel.removeAll();
    JPanel p = new JPanel(new MigLayout("ins " + myToolbarDecorator.getActionsPanel().getHeight() + " 10 0 0, flowx"));
    final JButton renameButton = new JButton(ExecutionBundle.message("run.configuration.rename.folder", folderName));
    renameButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        String name = editFolderName(folderName, type);
        if (name != null) {
          node.setUserObject(name);
          for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)node.getChildAt(i);
            if (treeNode.getUserObject() instanceof RunnerAndConfigurationSettings) {
              ((RunnerAndConfigurationSettings)treeNode.getUserObject()).setFolderName(name);
            }
            else {
              if (treeNode.getUserObject() instanceof SingleConfigurationConfigurable) {
                ((SingleConfigurationConfigurable)treeNode.getUserObject()).setFolderName(name);
              }
            }
            getRunManager().renameFolder(type, folderName, name);
          }
          myTreeModel.nodeChanged(node);
          myTree.revalidate();
          myTree.repaint();
          renameButton.setText(ExecutionBundle.message("run.configuration.rename.folder", name));
        }
      }
    });
    p.add(renameButton);

    myRightPanel.add(p);
    myRightPanel.revalidate();
    myRightPanel.repaint();
  }

  public void setRunDialog(final RunDialogBase runDialog) {
    myRunDialog = runDialog;
  }

  private void updateRightPanel(final Configurable configurable) {
    myRightPanel.removeAll();
    mySelectedConfigurable = configurable;

    final JBScrollPane scrollPane = new JBScrollPane(configurable.createComponent());
    scrollPane.setBorder(null);
    myRightPanel.add(scrollPane, BorderLayout.CENTER);

    if (configurable instanceof SingleConfigurationConfigurable) {
      RunManagerEx.getInstanceEx(myProject)
        .invalidateConfigurationIcon((RunnerAndConfigurationSettings)((SingleConfigurationConfigurable)configurable).getSettings());
    }

    setupDialogBounds();
  }

  private void sortTopLevelBranches() {
    TreeUtil.sort(myRoot, new Comparator() {
      public int compare(final Object o1, final Object o2) {
        final Object userObject1 = ((DefaultMutableTreeNode)o1).getUserObject();
        final Object userObject2 = ((DefaultMutableTreeNode)o2).getUserObject();
        if (userObject1 instanceof ConfigurationType && userObject2 instanceof ConfigurationType) {
          return ((ConfigurationType)userObject1).getDisplayName().compareTo(((ConfigurationType)userObject2).getDisplayName());
        }
        else if (userObject1 == DEFAULTS && userObject2 instanceof ConfigurationType) {
          return 1;
        }

        return 0;
      }
    });
  }

  private void update() {
    updateDialog();
    final TreePath selectionPath = myTree.getSelectionPath();
    if (selectionPath != null) {
      final DefaultMutableTreeNode node = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
      myTreeModel.reload(node);
    }
  }

  private void installUpdateListeners(final SingleConfigurationConfigurable<RunConfiguration> info) {
    final boolean[] changed = new boolean[]{false};
    info.getEditor().addSettingsEditorListener(new SettingsEditorListener<RunnerAndConfigurationSettings>() {
      public void stateChanged(final SettingsEditor<RunnerAndConfigurationSettings> editor) {
        update();
        final RunConfiguration configuration = info.getConfiguration();
        if (configuration instanceof LocatableConfiguration) {
          final LocatableConfiguration runtimeConfiguration = (LocatableConfiguration)configuration;
          if (runtimeConfiguration.isGeneratedName() && !changed[0]) {
            try {
              final LocatableConfiguration snapshot = (LocatableConfiguration)editor.getSnapshot().getConfiguration();
              final String generatedName = snapshot instanceof RuntimeConfiguration? ((RuntimeConfiguration)snapshot).getGeneratedName() : snapshot.suggestedName();
              if (generatedName != null && generatedName.length() > 0) {
                info.setNameText(generatedName);
                changed[0] = false;
              }
            }
            catch (ConfigurationException ignore) {
            }
          }
        }
        setupDialogBounds();
      }
    });

    info.addNameListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        changed[0] = true;
        update();
      }
    });
  }

  private void drawPressAddButtonMessage(final ConfigurationType configurationType) {
    JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    panel.setBorder(new EmptyBorder(30, 0, 0, 0));
    panel.add(new JLabel("Press the"));

    JLabel addIcon = new JLabel(IconUtil.getAddIcon());
    addIcon.setBorder(new EmptyBorder(0, 0, 0, 5));
    panel.add(addIcon);

    final String configurationTypeDescription = configurationType != null
                                                ? configurationType.getConfigurationTypeDescription()
                                                : ExecutionBundle.message("run.configuration.default.type.description");
    panel.add(new JLabel(ExecutionBundle.message("empty.run.configuration.panel.text.label3", configurationTypeDescription)));
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(panel, true);

    myRightPanel.removeAll();
    myRightPanel.add(scrollPane, BorderLayout.CENTER);
    if (configurationType == null) {
      JPanel settingsPanel = new JPanel(new GridBagLayout());
      GridBag grid = new GridBag().setDefaultAnchor(GridBagConstraints.NORTHWEST);

      for (Pair<UnnamedConfigurable, JComponent> each : myAdditionalSettings) {
        settingsPanel.add(each.second, grid.nextLine().next());
      }
      settingsPanel.add(createSettingsPanel(), grid.nextLine().next());

      JPanel wrapper = new JPanel(new BorderLayout());
      wrapper.add(settingsPanel, BorderLayout.WEST);
      wrapper.add(Box.createGlue(), BorderLayout.CENTER);

      myRightPanel.add(wrapper, BorderLayout.SOUTH);
    }
    myRightPanel.revalidate();
    myRightPanel.repaint();
  }

  private JPanel createLeftPanel() {
    initTree();
    MyRemoveAction removeAction = new MyRemoveAction();
    MyMoveAction moveUpAction = new MyMoveAction(ExecutionBundle.message("move.up.action.name"), null, IconUtil.getMoveUpIcon(), -1);
    MyMoveAction moveDownAction = new MyMoveAction(ExecutionBundle.message("move.down.action.name"), null, IconUtil.getMoveDownIcon(), 1);
    myToolbarDecorator = ToolbarDecorator.createDecorator(myTree).setAsUsualTopToolbar()
      .setAddAction(new MyToolbarAddAction()).setAddActionName(ExecutionBundle.message("add.new.run.configuration.acrtion.name"))
      .setRemoveAction(removeAction).setRemoveActionUpdater(removeAction)
      .setRemoveActionName(ExecutionBundle.message("remove.run.configuration.action.name"))
      .setMoveUpAction(moveUpAction).setMoveUpActionName(ExecutionBundle.message("move.up.action.name")).setMoveUpActionUpdater(moveUpAction)
      .setMoveDownAction(moveDownAction).setMoveDownActionName(ExecutionBundle.message("move.down.action.name")).setMoveDownActionUpdater(moveDownAction)
      .addExtraAction(AnActionButton.fromAction(new MyCopyAction()))
      .addExtraAction(AnActionButton.fromAction(new MySaveAction()))
      .addExtraAction(AnActionButton.fromAction(new MyEditDefaultsAction()))
      .addExtraAction(AnActionButton.fromAction(new MyCreateFolderAction()))
      .setButtonComparator(ExecutionBundle.message("add.new.run.configuration.acrtion.name"),
                           ExecutionBundle.message("remove.run.configuration.action.name"),
                           ExecutionBundle.message("copy.configuration.action.name"),
                           ExecutionBundle.message("action.name.save.configuration"),
                           ExecutionBundle.message("run.configuration.edit.default.configuration.settings.text"),
                           ExecutionBundle.message("move.up.action.name"),
                           ExecutionBundle.message("move.down.action.name"),
                           ExecutionBundle.message("run.configuration.create.folder.text")
      ).setForcedDnD();
    return myToolbarDecorator.createPanel();
  }

  private JPanel createSettingsPanel() {
    JPanel bottomPanel = new JPanel(new GridBagLayout());
    GridBag g = new GridBag();

    bottomPanel.add(myConfirmation, g.nextLine().coverLine());
    bottomPanel.add(new JLabel("Temporary configurations limit:"), g.nextLine().next());
    bottomPanel.add(myRecentsLimit, g.next().anchor(GridBagConstraints.WEST));

    myRecentsLimit.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        setModified(true);
      }
    });
    myConfirmation.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        setModified(true);
      }
    });
    return bottomPanel;
  }

  @Nullable
  private ConfigurationType getSelectedConfigurationType() {
    final TreePath selectionPath = myTree.getSelectionPath();
    if (selectionPath == null) return null;
    final DefaultMutableTreeNode configurationTypeNode = getSelectedConfigurationTypeNode();
    return configurationTypeNode != null ? (ConfigurationType)configurationTypeNode.getUserObject() : null;
  }

  public JComponent createComponent() {
    for (RunConfigurationsSettings each : Extensions.getExtensions(RunConfigurationsSettings.EXTENSION_POINT)) {
      UnnamedConfigurable configurable = each.createConfigurable();
      myAdditionalSettings.add(Pair.create(configurable, configurable.createComponent()));
    }

    myWholePanel = new JPanel(new BorderLayout());
    mySplitter.setFirstComponent(createLeftPanel());
    mySplitter.setSecondComponent(myRightPanel);
    myWholePanel.add(mySplitter, BorderLayout.CENTER);

    updateDialog();

    Dimension d = myWholePanel.getPreferredSize();
    d.width = Math.max(d.width, 800);
    d.height = Math.max(d.height, 600);
    myWholePanel.setPreferredSize(d);

    mySplitter.setProportion(myProperties.getFloat(DIVIDER_PROPORTION, 0.3f));

    return myWholePanel;
  }

  public void reset() {
    final RunManagerEx manager = getRunManager();
    final RunManagerConfig config = manager.getConfig();
    myRecentsLimit.setText(Integer.toString(config.getRecentsLimit()));
    myConfirmation.setSelected(config.isRestartRequiresConfirmation());

    for (Pair<UnnamedConfigurable, JComponent> each : myAdditionalSettings) {
      each.first.reset();
    }

    setModified(false);
  }

  public Configurable getSelectedConfigurable() {
    return mySelectedConfigurable;
  }

  public void apply() throws ConfigurationException {
    updateActiveConfigurationFromSelected();

    final RunManagerImpl manager = getRunManager();
    final ConfigurationType[] types = manager.getConfigurationFactories();
    List<ConfigurationType> configurationTypes = new ArrayList<ConfigurationType>();
    for (int i = 0; i < myRoot.getChildCount(); i++) {
      final DefaultMutableTreeNode node = (DefaultMutableTreeNode)myRoot.getChildAt(i);
      if (node.getUserObject() instanceof ConfigurationType) {
        configurationTypes.add((ConfigurationType)node.getUserObject());
      }
    }
    for (ConfigurationType type : types) {
      if (!configurationTypes.contains(type))
        configurationTypes.add(type);
    }

    for (ConfigurationType configurationType : configurationTypes) {
      applyByType(configurationType);
    }

    try {
      int i = Math.max(1, Integer.parseInt(myRecentsLimit.getText()));
      int oldLimit = manager.getConfig().getRecentsLimit();
      if (oldLimit != i) {
        manager.getConfig().setRecentsLimit(i);
        manager.checkRecentsLimit();
      }
    }
    catch (NumberFormatException e) {
      // ignore
    }
    manager.getConfig().setRestartRequiresConfirmation(myConfirmation.isSelected());

    for (Configurable configurable : myStoredComponents.values()) {
      if (configurable.isModified()){
        configurable.apply();
      }
    }

    for (Pair<UnnamedConfigurable, JComponent> each : myAdditionalSettings) {
      each.first.apply();
    }

    manager.saveOrder();
    setModified(false);
    myTree.repaint();
  }

  protected void updateActiveConfigurationFromSelected() {
    if (mySelectedConfigurable != null && mySelectedConfigurable instanceof SingleConfigurationConfigurable) {
      RunnerAndConfigurationSettings settings =
        (RunnerAndConfigurationSettings)((SingleConfigurationConfigurable)mySelectedConfigurable).getSettings();

      getRunManager().setSelectedConfiguration(settings);
    }
  }

  private void applyByType(ConfigurationType type) throws ConfigurationException {
    RunnerAndConfigurationSettings selectedSettings = getSelectedSettings();
    int indexToMove = -1;

    DefaultMutableTreeNode typeNode = getConfigurationTypeNode(type);
    final RunManagerImpl manager = getRunManager();
    final ArrayList<RunConfigurationBean> stableConfigurations = new ArrayList<RunConfigurationBean>();
    if (typeNode != null) {
      final Set<String> names = new HashSet<String>();
      List<DefaultMutableTreeNode> configurationNodes = new ArrayList<DefaultMutableTreeNode>();
      getConfigurationNodes(typeNode, configurationNodes);
      for (DefaultMutableTreeNode node : configurationNodes) {
        final Object userObject = node.getUserObject();
        RunConfigurationBean configurationBean = null;
        RunnerAndConfigurationSettings settings = null;
        if (userObject instanceof SingleConfigurationConfigurable) {
          final SingleConfigurationConfigurable configurable = (SingleConfigurationConfigurable)userObject;
           settings = (RunnerAndConfigurationSettings)configurable.getSettings();
          if (manager.isTemporary(settings)) {
            applyConfiguration(typeNode, configurable);
          }
          configurationBean = new RunConfigurationBean(configurable);
        }
        else if (userObject instanceof RunnerAndConfigurationSettingsImpl) {
          settings = (RunnerAndConfigurationSettings)userObject;
          configurationBean = new RunConfigurationBean(settings,
                                                       manager.isConfigurationShared(settings),
                                                       manager.getBeforeRunTasks(settings.getConfiguration()));

        }
        if (configurationBean != null) {
          final SingleConfigurationConfigurable configurable = configurationBean.getConfigurable();
          final String nameText = configurable != null ? configurable.getNameText() : configurationBean.getSettings().getName();
          if (!names.add(nameText)) {
            TreeUtil.selectNode(myTree, node);
            throw new ConfigurationException(type.getDisplayName() + " with name \'" + nameText + "\' already exists");
          }
          stableConfigurations.add(configurationBean);
          if (settings == selectedSettings) {
            indexToMove = stableConfigurations.size()-1;
          }
        }
      }
    }
    // try to apply all
    for (RunConfigurationBean bean : stableConfigurations) {
      final SingleConfigurationConfigurable configurable = bean.getConfigurable();
      if (configurable != null) {
        applyConfiguration(typeNode, configurable);
      }
    }

    // if apply succeeded, update the list of configurations in RunManager
    Set<RunnerAndConfigurationSettings> toDeleteSettings = new THashSet<RunnerAndConfigurationSettings>();
    for (RunConfiguration each : manager.getConfigurations(type)) {
      ContainerUtil.addIfNotNull(toDeleteSettings, manager.getSettings(each));
    }

    //Just saved as 'stable' configuration shouldn't stay between temporary ones (here we order model to save)
    int shift = 0;
    if (selectedSettings != null && selectedSettings.getType() == type) {
      shift = adjustOrder();
    }
    if (shift != 0 && indexToMove != -1) {
      stableConfigurations.add(indexToMove-shift, stableConfigurations.remove(indexToMove));
    }
    for (RunConfigurationBean each : stableConfigurations) {
      toDeleteSettings.remove(each.getSettings());
      manager.addConfiguration(each.getSettings(), each.isShared(), each.getStepsBeforeLaunch(), false);
    }

    for (RunnerAndConfigurationSettings each : toDeleteSettings) {
      manager.removeConfiguration(each);
    }
  }

  private static void getConfigurationNodes(DefaultMutableTreeNode parentNode, List<DefaultMutableTreeNode> nodes) {
    for (int i = 0; i < parentNode.getChildCount(); i++) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode)parentNode.getChildAt(i);
      NodeKind kind = getKind(child);
      if (kind.isConfiguration()) {
        nodes.add(child);
      }
      else if (kind == FOLDER) {
        getConfigurationNodes(child, nodes);
      }
    }
  }

  @Nullable
  private DefaultMutableTreeNode getConfigurationTypeNode(final ConfigurationType type) {
    for (int i = 0; i < myRoot.getChildCount(); i++) {
      final DefaultMutableTreeNode node = (DefaultMutableTreeNode)myRoot.getChildAt(i);
      if (node.getUserObject() == type) return node;
    }
    return null;
  }

  private void applyConfiguration(DefaultMutableTreeNode typeNode, SingleConfigurationConfigurable<?> configurable) throws ConfigurationException {
    try {
      if (configurable != null) {
        configurable.apply();
        RunManagerImpl.getInstanceImpl(myProject).fireRunConfigurationChanged(configurable.getSettings());
      }
    }
    catch (ConfigurationException e) {
      for (int i = 0; i < typeNode.getChildCount(); i++) {
        final DefaultMutableTreeNode node = (DefaultMutableTreeNode)typeNode.getChildAt(i);
        if (Comparing.equal(configurable, node.getUserObject())) {
          TreeUtil.selectNode(myTree, node);
          break;
        }
      }
      throw e;
    }
  }

  public boolean isModified() {
    if (super.isModified()) return true;
    final RunManagerImpl runManager = getRunManager();
    final RunConfiguration[] allConfigurations = runManager.getAllConfigurations();
    final Set<RunConfiguration> currentConfigurations = new HashSet<RunConfiguration>();
    for (int i = 0; i < myRoot.getChildCount(); i++) {
      DefaultMutableTreeNode typeNode = (DefaultMutableTreeNode)myRoot.getChildAt(i);
      final Object object = typeNode.getUserObject();
      if (object instanceof ConfigurationType) {
        final RunnerAndConfigurationSettings[] configurationSettings =
          runManager.getConfigurationSettings((ConfigurationType)object);
        if (configurationSettings.length != typeNode.getChildCount()) return true;
        for (int j = 0; j < typeNode.getChildCount(); j++) {
          final Object userObject = ((DefaultMutableTreeNode)typeNode.getChildAt(j)).getUserObject();
          if (userObject instanceof SingleConfigurationConfigurable) {
            SingleConfigurationConfigurable configurable = (SingleConfigurationConfigurable)userObject;
            if (!Comparing.strEqual(configurationSettings[j].getConfiguration().getName(), configurable.getConfiguration().getName())) {
              return true;
            }
            if (configurable.isModified()) return true;
            currentConfigurations.add(configurable.getConfiguration());
          }
          else if (userObject instanceof RunnerAndConfigurationSettingsImpl) {
            currentConfigurations.add(((RunnerAndConfigurationSettings)userObject).getConfiguration());
          }
        }
      }
    }
    for (RunConfiguration configuration : allConfigurations) {
      if (!currentConfigurations.contains(configuration)) return true;
    }

    for (Configurable configurable : myStoredComponents.values()) {
      if (configurable.isModified()) return true;
    }

    for (Pair<UnnamedConfigurable, JComponent> each : myAdditionalSettings) {
      if (each.first.isModified()) return true;
    }

    return false;
  }

  public void disposeUIResources() {
    isDisposed = true;
    for (Configurable configurable : myStoredComponents.values()) {
      configurable.disposeUIResources();
    }
    myStoredComponents.clear();

    for (Pair<UnnamedConfigurable, JComponent> each : myAdditionalSettings) {
      each.first.disposeUIResources();
    }

    TreeUtil.traverseDepth(myRoot, new TreeUtil.Traverse() {
      public boolean accept(Object node) {
        if (node instanceof DefaultMutableTreeNode) {
          final DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)node;
          final Object userObject = treeNode.getUserObject();
          if (userObject instanceof SingleConfigurationConfigurable) {
            ((SingleConfigurationConfigurable)userObject).disposeUIResources();
          }
        }
        return true;
      }
    });
    myRightPanel.removeAll();
    myProperties.setFloat(DIVIDER_PROPORTION, mySplitter.getProportion());
    mySplitter.dispose();
  }

  private void updateDialog() {
    final Executor executor = myRunDialog != null ? myRunDialog.getExecutor() : null;
    if (executor == null) return;
    final StringBuilder buffer = new StringBuilder();
    buffer.append(executor.getId());
    final SingleConfigurationConfigurable<RunConfiguration> configuration = getSelectedConfiguration();
    if (configuration != null) {
      buffer.append(" - ");
      buffer.append(configuration.getNameText());
    }
    myRunDialog.setOKActionEnabled(canRunConfiguration(configuration, executor));
    myRunDialog.setTitle(buffer.toString());
  }

  private void setupDialogBounds() {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        UIUtil.setupEnclosingDialogBounds(myWholePanel);
      }
    });
  }

  @Nullable
  private SingleConfigurationConfigurable<RunConfiguration> getSelectedConfiguration() {
    final TreePath selectionPath = myTree.getSelectionPath();
    if (selectionPath != null) {
      final DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
      final Object userObject = treeNode.getUserObject();
      if (userObject instanceof SingleConfigurationConfigurable) {
        return (SingleConfigurationConfigurable<RunConfiguration>)userObject;
      }
    }
    return null;
  }

  private static boolean canRunConfiguration(@Nullable SingleConfigurationConfigurable<RunConfiguration> configuration, final @NotNull Executor executor) {
    try {
      return configuration != null && RunManagerImpl.canRunConfiguration(configuration.getSnapshot(), executor);
    }
    catch (ConfigurationException e) {
      return false;
    }
  }

  private RunManagerImpl getRunManager() {
    return RunManagerImpl.getInstanceImpl(myProject);
  }

  public String getHelpTopic() {
    final ConfigurationType type = getSelectedConfigurationType();
    if (type != null) {
      return "reference.dialogs.rundebug." + type.getId();
    }
    return "reference.dialogs.rundebug";
  }

  private void clickDefaultButton() {
    if (myRunDialog != null) myRunDialog.clickDefaultButton();
  }

  @Nullable
  private DefaultMutableTreeNode getSelectedConfigurationTypeNode() {
    final DefaultMutableTreeNode node = (DefaultMutableTreeNode)myTree.getSelectionPath().getLastPathComponent();
    final Object userObject = node.getUserObject();
    if (userObject instanceof ConfigurationType) {
      return node;
    }
    else {
      final DefaultMutableTreeNode parent = (DefaultMutableTreeNode)node.getParent();
      if (parent != null && parent.getUserObject() instanceof ConfigurationType) {
        return parent;
      }
      return null;
    }
  }

  private static String createUniqueName(DefaultMutableTreeNode typeNode, @Nullable String baseName) {
    String str = (baseName == null) ? ExecutionBundle.message("run.configuration.unnamed.name.prefix") : baseName;
    final ArrayList<String> currentNames = new ArrayList<String>();
    for (int i = 0; i < typeNode.getChildCount(); i++) {
      final Object userObject = ((DefaultMutableTreeNode)typeNode.getChildAt(i)).getUserObject();
      if (userObject instanceof SingleConfigurationConfigurable) {
        currentNames.add(((SingleConfigurationConfigurable)userObject).getNameText());
      }
      else if (userObject instanceof RunnerAndConfigurationSettingsImpl) {
        currentNames.add(((RunnerAndConfigurationSettings)userObject).getName());
      }
    }
    if (!currentNames.contains(str)) return str;

    final Matcher matcher = Pattern.compile("(.*?)\\s*\\(\\d+\\)").matcher(str);
    final String originalName = (matcher.matches()) ? matcher.group(1) : str;
    int i = 1;
    while (true) {
      final String newName = String.format("%s (%d)", originalName, i);
      if (!currentNames.contains(newName)) return newName;
      i++;
    }
  }

  private SingleConfigurationConfigurable<RunConfiguration> createNewConfiguration(final RunnerAndConfigurationSettings settings, final DefaultMutableTreeNode node) {
    final SingleConfigurationConfigurable<RunConfiguration> configurationConfigurable =
        SingleConfigurationConfigurable.editSettings(settings, null);
    installUpdateListeners(configurationConfigurable);
    DefaultMutableTreeNode nodeToAdd = new DefaultMutableTreeNode(configurationConfigurable);
    myTreeModel.insertNodeInto(nodeToAdd, node, node.getChildCount());
    TreeUtil.selectNode(myTree, nodeToAdd);
    return configurationConfigurable;
  }

  private void createNewConfiguration(final ConfigurationFactory factory) {
    DefaultMutableTreeNode node = null;
    DefaultMutableTreeNode selectedNode = null;
    TreePath selectionPath = myTree.getSelectionPath();
    if (selectionPath != null) {
      selectedNode = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
      if (getKind(selectedNode) != FOLDER) {
        selectedNode = null;
      }
    }
    for (int i = 0; i < myRoot.getChildCount(); i++) {
      final DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)myRoot.getChildAt(i);
      if (treeNode.getUserObject() == factory.getType()) {
        node = treeNode;
        if (selectedNode != null && selectedNode.getParent() == node) {
          node = selectedNode;
        }
        break;
      }
    }
    if (node == null) {
      node = new DefaultMutableTreeNode(factory.getType());
      //final DefaultMutableTreeNode lastChild = (DefaultMutableTreeNode)myRoot.getLastChild();
      //if (lastChild.getUserObject() instanceof String) {
      //  int index = myRoot.getIndex(lastChild);
      //  myRoot.insert(node, Math.max(index - 1, 0));
      //} else {
        myRoot.add(node);
      //}

      sortTopLevelBranches();
      ((DefaultTreeModel)myTree.getModel()).reload();
    }
    final RunnerAndConfigurationSettings settings = getRunManager().createConfiguration(createUniqueName(node, null), factory);
    if (factory instanceof ConfigurationFactoryEx) {
      ((ConfigurationFactoryEx)factory).onNewConfigurationCreated(settings.getConfiguration());
    }
    createNewConfiguration(settings, node);
  }

  @Nullable
  private String editFolderName(final String initialName, final ConfigurationType type) {
    return Messages.showInputDialog(myProject, "Enter new folder name", "New Folder", null, initialName, new InputValidator() {
      RunManagerImpl runManager = getRunManager();
      @Override
      public boolean checkInput(String inputString) {
        return inputString!= null
               && !inputString.isEmpty()
               && (!runManager.getFolders(type).contains(inputString) || inputString.equals(initialName));
      }

      @Override
      public boolean canClose(String inputString) {
        return checkInput(inputString);
      }
    });
  }

  private class MyToolbarAddAction extends AnAction implements AnActionButtonRunnable {
    public MyToolbarAddAction() {
      super(ExecutionBundle.message("add.new.run.configuration.acrtion.name"),
            ExecutionBundle.message("add.new.run.configuration.acrtion.name"), ADD_ICON);
      registerCustomShortcutSet(CommonShortcuts.INSERT, myTree);
    }

    public void actionPerformed(AnActionEvent e) {
      showAddPopup();
    }

    @Override
    public void run(AnActionButton button) {
      showAddPopup();
    }

    private void showAddPopup() {
      final JBPopupFactory popupFactory = JBPopupFactory.getInstance();
      final ConfigurationType[] configurationTypes = getRunManager().getConfigurationFactories(false);
      Arrays.sort(configurationTypes, new Comparator<ConfigurationType>() {
        public int compare(final ConfigurationType type1, final ConfigurationType type2) {
          return type1.getDisplayName().compareTo(type2.getDisplayName());
        }
      });
      final ListPopup popup =
          popupFactory.createListPopup(new BaseListPopupStep<ConfigurationType>(
            ExecutionBundle.message("add.new.run.configuration.acrtion.name"), configurationTypes) {

            @NotNull
            public String getTextFor(final ConfigurationType type) {
              return type.getDisplayName();
            }

            @Override
            public boolean isSpeedSearchEnabled() {
              return true;
            }

            @Override
            public boolean canBeHidden(ConfigurationType value) {
              return true;
            }

            public Icon getIconFor(final ConfigurationType type) {
              return type.getIcon();
            }

            public PopupStep onChosen(final ConfigurationType type, final boolean finalChoice) {
              if (hasSubstep(type)) {
                return getSupStep(type);
              }
              final ConfigurationFactory[] factories = type.getConfigurationFactories();
              if (factories.length > 0) {
                createNewConfiguration(factories[0]);
              }
              return FINAL_CHOICE;
            }

            public int getDefaultOptionIndex() {
              final TreePath selectionPath = myTree.getSelectionPath();
              if (selectionPath != null) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
                final Object userObject = node.getUserObject();
                ConfigurationType type = null;
                if (userObject instanceof SingleConfigurationConfigurable) {
                  final SingleConfigurationConfigurable configurable = (SingleConfigurationConfigurable)userObject;
                  type = configurable.getConfiguration().getType();
                }
                else if (userObject instanceof ConfigurationType) {
                  type = (ConfigurationType)userObject;
                }
                return ArrayUtil.find(configurationTypes, type);
              }
              return super.getDefaultOptionIndex();
            }

            private ListPopupStep getSupStep(final ConfigurationType type) {
              final ConfigurationFactory[] factories = type.getConfigurationFactories();
              Arrays.sort(factories, new Comparator<ConfigurationFactory>() {
                public int compare(final ConfigurationFactory factory1, final ConfigurationFactory factory2) {
                  return factory1.getName().compareTo(factory2.getName());
                }
              });
              return new BaseListPopupStep<ConfigurationFactory>(
                ExecutionBundle.message("add.new.run.configuration.action.name", type.getDisplayName()), factories) {

                @NotNull
                public String getTextFor(final ConfigurationFactory value) {
                  return value.getName();
                }

                public Icon getIconFor(final ConfigurationFactory factory) {
                  return factory.getIcon();
                }

                public PopupStep onChosen(final ConfigurationFactory factory, final boolean finalChoice) {
                  createNewConfiguration(factory);
                  return FINAL_CHOICE;
                }

              };
            }

            public boolean hasSubstep(final ConfigurationType type) {
              return type.getConfigurationFactories().length > 1;
            }

          });
      //new TreeSpeedSearch(myTree);
      popup.showUnderneathOf(myToolbarDecorator.getActionsPanel());
    }
  }


  private class MyRemoveAction extends AnAction implements AnActionButtonRunnable, AnActionButtonUpdater{

    public MyRemoveAction() {
      super(ExecutionBundle.message("remove.run.configuration.action.name"),
            ExecutionBundle.message("remove.run.configuration.action.name"), REMOVE_ICON);
      registerCustomShortcutSet(CommonShortcuts.DELETE, myTree);
    }

    public void actionPerformed(AnActionEvent e) {
      doRemove();
    }

    @Override
    public void run(AnActionButton button) {
      doRemove();
    }

    private void doRemove() {
      TreePath[] selections = myTree.getSelectionPaths();
      myTree.clearSelection();

      int nodeIndexToSelect = -1;
      DefaultMutableTreeNode parentToSelect = null;

      Set<DefaultMutableTreeNode> changedParents = new HashSet<DefaultMutableTreeNode>();
      boolean wasRootChanged = false;

      for (TreePath each : selections) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)each.getLastPathComponent();
        DefaultMutableTreeNode parent = (DefaultMutableTreeNode)node.getParent();
        NodeKind kind = getKind(node);
        if (!kind.isConfiguration() && kind != FOLDER)
          continue;

        if (node.getUserObject() instanceof SingleConfigurationConfigurable) {
          ((SingleConfigurationConfigurable)node.getUserObject()).disposeUIResources();
        }

        nodeIndexToSelect = parent.getIndex(node);
        parentToSelect = parent;
        myTreeModel.removeNodeFromParent(node);
        changedParents.add(parent);

        if (kind == FOLDER) {
          ConfigurationType type = (ConfigurationType)parent.getUserObject();
          getRunManager().removeFolder(type, (String)node.getUserObject());
          List<DefaultMutableTreeNode> children = new ArrayList<DefaultMutableTreeNode>();
          for (int i = 0; i < node.getChildCount(); i++) {
            children.add(0, (DefaultMutableTreeNode)node.getChildAt(i));
          }
          int confIndex = 0;
          for (int i = 0; i < parent.getChildCount(); i++) {
            if (getKind((DefaultMutableTreeNode)parent.getChildAt(i)).isConfiguration()) {
              confIndex = i;
              break;
            }
          }
          for (DefaultMutableTreeNode child : children) {
            if (getKind(child) == CONFIGURATION)
              myTreeModel.insertNodeInto(child, parent, confIndex);
          }
          confIndex = parent.getChildCount();
          for (int i = 0; i < parent.getChildCount(); i++) {
            if (getKind((DefaultMutableTreeNode)parent.getChildAt(i)) == TEMPORARY_CONFIGURATION) {
              confIndex = i;
              break;
            }
          }
          for (DefaultMutableTreeNode child : children) {
            if (getKind(child) == TEMPORARY_CONFIGURATION)
              myTreeModel.insertNodeInto(child, parent, confIndex);
          }
        }

        if (parent.getChildCount() == 0 && parent.getUserObject() instanceof ConfigurationType) {
          changedParents.remove(parent);
          wasRootChanged = true;

          nodeIndexToSelect = myRoot.getIndex(parent);
          nodeIndexToSelect = Math.max(0, nodeIndexToSelect - 1);
          parentToSelect = myRoot;
          parent.removeFromParent();
        }
      }

      if (wasRootChanged) {
        ((DefaultTreeModel)myTree.getModel()).reload();
      } else {
        for (DefaultMutableTreeNode each : changedParents) {
          myTreeModel.reload(each);
          myTree.expandPath(new TreePath(each));
        }
      }

      mySelectedConfigurable = null;
      if (myRoot.getChildCount() == 0) {
        drawPressAddButtonMessage(null);
      }
      else {
        TreeNode nodeToSelect = nodeIndexToSelect < parentToSelect.getChildCount()
                                ? parentToSelect.getChildAt(nodeIndexToSelect)
                                : parentToSelect.getChildAt(nodeIndexToSelect - 1);
        TreeUtil.selectInTree((DefaultMutableTreeNode)nodeToSelect, true, myTree);
      }
    }


    public void update(AnActionEvent e) {
      boolean enabled = isEnabled(e);
      e.getPresentation().setEnabled(enabled);
    }

    public boolean isEnabled(AnActionEvent e) {
      boolean enabled = false;
      TreePath[] selections = myTree.getSelectionPaths();
      if (selections != null) {
        for (TreePath each : selections) {
          NodeKind kind = getKind((DefaultMutableTreeNode)each.getLastPathComponent());
          if (kind.isConfiguration() || kind == FOLDER) {
            enabled = true;
            break;
          }
        }
      }
      return enabled;
    }
  }

  private class MyCopyAction extends AnAction {
    public MyCopyAction() {
      super(ExecutionBundle.message("copy.configuration.action.name"),
            ExecutionBundle.message("copy.configuration.action.name"),
            PlatformIcons.COPY_ICON);

      final AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_DUPLICATE);
      registerCustomShortcutSet(action.getShortcutSet(), myTree);
    }


    public void actionPerformed(AnActionEvent e) {
      final SingleConfigurationConfigurable<RunConfiguration> configuration = getSelectedConfiguration();
      LOG.assertTrue(configuration != null);
      try {
        final DefaultMutableTreeNode typeNode = getSelectedConfigurationTypeNode();
        final RunnerAndConfigurationSettings settings = configuration.getSnapshot();
        final String copyName = createUniqueName(typeNode, configuration.getNameText());
        settings.setName(copyName);
        final ConfigurationFactory factory = settings.getFactory();
        if (factory instanceof ConfigurationFactoryEx) {
          ((ConfigurationFactoryEx)factory).onConfigurationCopied(settings.getConfiguration());
        }
        final SingleConfigurationConfigurable<RunConfiguration> configurable = createNewConfiguration(settings, typeNode);
        IdeFocusManager.getInstance(myProject).requestFocus(configurable.getNameTextField(), true);
        configurable.getNameTextField().setSelectionStart(0);
        configurable.getNameTextField().setSelectionEnd(copyName.length());
      }
      catch (ConfigurationException e1) {
        Messages.showErrorDialog(myToolbarDecorator.getActionsPanel(), e1.getMessage(), e1.getTitle());
      }
    }

    public void update(AnActionEvent e) {
      final SingleConfigurationConfigurable<RunConfiguration> configuration = getSelectedConfiguration();
      e.getPresentation().setEnabled(configuration != null && !(configuration.getConfiguration() instanceof UnknownRunConfiguration));
    }
  }

  private class MySaveAction extends AnAction {

    public MySaveAction() {
      super(ExecutionBundle.message("action.name.save.configuration"), null, AllIcons.Actions.Menu_saveall);
    }

    public void actionPerformed(final AnActionEvent e) {
      final SingleConfigurationConfigurable<RunConfiguration> configurationConfigurable = getSelectedConfiguration();
      LOG.assertTrue(configurationConfigurable != null);
      try {
        configurationConfigurable.apply();
      }
      catch (ConfigurationException e1) {
        //do nothing
      }
      final RunnerAndConfigurationSettings originalConfiguration = configurationConfigurable.getSettings();
      if (getRunManager().isTemporary(originalConfiguration)) {
        getRunManager().makeStable(originalConfiguration.getConfiguration());
        adjustOrder();
      }
      myTree.repaint();
    }

    public void update(final AnActionEvent e) {
      final SingleConfigurationConfigurable<RunConfiguration> configuration = getSelectedConfiguration();
      final Presentation presentation = e.getPresentation();
      final boolean enabled = configuration != null && getRunManager().isTemporary(configuration.getSettings());
      presentation.setEnabled(enabled);
      presentation.setVisible(enabled);
    }
  }

  /**
   * Just saved as 'stable' configuration shouldn't stay between temporary ones (here we order nodes in JTree only)
   * @return shift (positive) for move configuration "up" to other stable configurations. Zero means "there is nothing to change"
   */


  private int adjustOrder() {
    TreePath selectionPath = myTree.getSelectionPath();
    if (selectionPath == null)
      return 0;
    final DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
    RunnerAndConfigurationSettings selectedSettings = getSettings(treeNode);
    if (selectedSettings == null || selectedSettings.isTemporary())
      return 0;
    MutableTreeNode parent = (MutableTreeNode)treeNode.getParent();
    int initialPosition = parent.getIndex(treeNode);
    int position = initialPosition;
    DefaultMutableTreeNode node = treeNode.getPreviousSibling();
    while (node != null) {
      RunnerAndConfigurationSettings settings = getSettings(node);
      if (settings != null && settings.isTemporary()) {
        position--;
      } else {
        break;
      }
      node = node.getPreviousSibling();
    }
    for (int i = 0; i < initialPosition - position; i++) {
      TreeUtil.moveSelectedRow(myTree, -1);
    }
    return initialPosition - position;
  }

  private class MyMoveAction extends AnAction implements AnActionButtonRunnable, AnActionButtonUpdater {
    private final int myDirection;

    protected MyMoveAction(String text, String description, Icon icon, int direction) {
      super(text, description, icon);
      myDirection = direction;
    }

    public void actionPerformed(final AnActionEvent e) {
      doMove();
    }

    private void doMove() {
      TreeUtil.moveSelectedRow(myTree, myDirection);
    }

    @Override
    public void run(AnActionButton button) {
      doMove();
    }

    public void update(final AnActionEvent e) {
      e.getPresentation().setEnabled(isEnabled(e));
    }

    @Override
    public boolean isEnabled(AnActionEvent e) {
      final TreePath selectionPath = myTree.getSelectionPath();
      if (selectionPath != null) {
        final DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
        if (!(treeNode.getUserObject() instanceof ConfigurationType) && !(treeNode.getUserObject() instanceof String)) {
          RunnerAndConfigurationSettings selectedSettings = getSettings(treeNode);
          if (selectedSettings == null)
            return false;
          RunnerAndConfigurationSettings siblingSettings;
          if (myDirection < 0) {
            siblingSettings = getSettings(treeNode.getPreviousSibling());
          } else {
            siblingSettings = getSettings(treeNode.getNextSibling());
          }
            return siblingSettings != null && siblingSettings.isTemporary() == selectedSettings.isTemporary();
          }
        }
      return false;
    }
  }

  private class MyEditDefaultsAction extends AnAction {
    public MyEditDefaultsAction() {
      super(ExecutionBundle.message("run.configuration.edit.default.configuration.settings.text"),
            ExecutionBundle.message("run.configuration.edit.default.configuration.settings.description"), AllIcons.General.Settings);
    }

    public void actionPerformed(final AnActionEvent e) {
      TreeNode defaults = TreeUtil.findNodeWithObject(DEFAULTS, myTree.getModel(), myRoot);
      if (defaults != null) {
        final ConfigurationType configurationType = getSelectedConfigurationType();
        if (configurationType != null) {
          defaults = TreeUtil.findNodeWithObject(configurationType, myTree.getModel(), defaults);
        }
        final DefaultMutableTreeNode defaultsNode = (DefaultMutableTreeNode)defaults;
        final TreePath path = TreeUtil.getPath(myRoot, defaultsNode);
        myTree.expandPath(path);
        TreeUtil.selectInTree(defaultsNode, true, myTree);
        myTree.scrollPathToVisible(path);
      }
    }

    @Override
    public void update(AnActionEvent e) {
      boolean isEnabled = TreeUtil.findNodeWithObject(DEFAULTS, myTree.getModel(), myRoot) != null;
      TreePath path = myTree.getSelectionPath();
      if (path != null) {
        Object o = path.getLastPathComponent();
        if (o instanceof DefaultMutableTreeNode && ((DefaultMutableTreeNode)o).getUserObject().equals(DEFAULTS)) {
          isEnabled = false;
        }
        o = path.getParentPath().getLastPathComponent();
        if (o instanceof DefaultMutableTreeNode && ((DefaultMutableTreeNode)o).getUserObject().equals(DEFAULTS)) {
          isEnabled = false;
        }
      }
      e.getPresentation().setEnabled(isEnabled);
    }
  }

  private class MyCreateFolderAction extends AnAction {
    private MyCreateFolderAction() {
      super(ExecutionBundle.message("run.configuration.create.folder.text"),
            ExecutionBundle.message("run.configuration.create.folder.description"), AllIcons.Nodes.Folder);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final ConfigurationType type = getSelectedConfigurationType();
      final RunManagerImpl runManager = getRunManager();
      String folderName = editFolderName(null, type);
      if (folderName != null) {
        int index = runManager.getFolders(type).size();
        runManager.createFolder(type, folderName);
        DefaultMutableTreeNode node = getSelectedConfigurationTypeNode();
        DefaultMutableTreeNode folderNode = new DefaultMutableTreeNode(folderName);
        myTreeModel.insertNodeInto(folderNode, node, index);
        TreeUtil.selectNode(myTree, folderNode);
      }
    }

    @Override
    public void update(AnActionEvent e) {
      boolean isEnabled = false;
      TreePath path = myTree.getSelectionPath();
      if (path != null) {
        Object o = path.getLastPathComponent();
        if (o instanceof DefaultMutableTreeNode
            && ((DefaultMutableTreeNode)o).getUserObject() instanceof ConfigurationType
           && ((DefaultMutableTreeNode)o).getParent() == myRoot) {
          isEnabled = true;
        }
      }
      e.getPresentation().setEnabled(isEnabled);
      e.getPresentation().setVisible(isEnabled);
    }
  }
  @Nullable
  private RunnerAndConfigurationSettings getSelectedSettings() {
    TreePath selectionPath = myTree.getSelectionPath();
    if (selectionPath == null)
      return null;
    return getSettings((DefaultMutableTreeNode)selectionPath.getLastPathComponent());
  }

  @Nullable
  private static RunnerAndConfigurationSettings getSettings(DefaultMutableTreeNode treeNode) {
    if (treeNode == null)
      return null;
    RunnerAndConfigurationSettings settings = null;
    if (treeNode.getUserObject() instanceof SingleConfigurationConfigurable) {
      settings = (RunnerAndConfigurationSettings)((SingleConfigurationConfigurable)treeNode.getUserObject()).getSettings();
    }
    if (treeNode.getUserObject() instanceof RunnerAndConfigurationSettings) {
      settings = (RunnerAndConfigurationSettings)treeNode.getUserObject();
    }
    return settings;
  }

  private static class RunConfigurationBean {
    private final RunnerAndConfigurationSettings mySettings;
    private final boolean myShared;
    private final List<BeforeRunTask> myStepsBeforeLaunch;
    private final SingleConfigurationConfigurable myConfigurable;

    public RunConfigurationBean(final RunnerAndConfigurationSettings settings,
                                final boolean shared,
                                final List<BeforeRunTask> stepsBeforeLaunch) {
      mySettings = settings;
      myShared = shared;
      myStepsBeforeLaunch = Collections.unmodifiableList(stepsBeforeLaunch);
      myConfigurable = null;
    }

    public RunConfigurationBean(final SingleConfigurationConfigurable configurable) {
      myConfigurable = configurable;
      mySettings = (RunnerAndConfigurationSettings)myConfigurable.getSettings();
      final ConfigurationSettingsEditorWrapper editorWrapper = (ConfigurationSettingsEditorWrapper)myConfigurable.getEditor();
      myShared = configurable.isStoreProjectConfiguration();
      myStepsBeforeLaunch = editorWrapper.getStepsBeforeLaunch();
    }

    public RunnerAndConfigurationSettings getSettings() {
      return mySettings;
    }

    public boolean isShared() {
      return myShared;
    }

    public List<BeforeRunTask> getStepsBeforeLaunch() {
      return myStepsBeforeLaunch;
    }

    public SingleConfigurationConfigurable getConfigurable() {
      return myConfigurable;
    }

    @Override
    public String toString() {
      return String.valueOf(mySettings);
    }
  }

  public interface RunDialogBase {
    void setOKActionEnabled(boolean isEnabled);

    @Nullable
    Executor getExecutor();

    void setTitle(String title);

    void clickDefaultButton();
  }

  enum NodeKind {
    CONFIGURATION_TYPE, FOLDER, CONFIGURATION, TEMPORARY_CONFIGURATION, UNKNOWN;

    boolean supportsDnD() {
      return this == FOLDER || this == CONFIGURATION || this == TEMPORARY_CONFIGURATION;
    }

    boolean isConfiguration() {
      return this == CONFIGURATION | this == TEMPORARY_CONFIGURATION;
    }
  }

  @NotNull
  private static NodeKind getKind(@Nullable DefaultMutableTreeNode node) {
    if (node == null)
      return UNKNOWN;
    Object userObject = node.getUserObject();
    if (userObject instanceof SingleConfigurationConfigurable || userObject instanceof RunnerAndConfigurationSettings) {
      RunnerAndConfigurationSettings settings = getSettings(node);
      if (settings == null) {
        return UNKNOWN;
      }
      return settings.isTemporary() ? TEMPORARY_CONFIGURATION : CONFIGURATION;
    }
    if (userObject instanceof String) {
      return FOLDER;
    }
    if (userObject instanceof ConfigurationType) {
      return CONFIGURATION_TYPE;
    }
    return UNKNOWN;
  }

  private class MyTreeModel extends DefaultTreeModel implements EditableModel, RowsDnDSupport.RefinedDropSupport {
    private MyTreeModel(TreeNode root) {
      super(root);
    }

    @Override
    public void addRow() {
    }

    @Override
    public void removeRow(int index) {
    }

    @Override
    public void exchangeRows(int oldIndex, int newIndex) {
      //Do nothing, use drop() instead
    }

    @Override
    public boolean canExchangeRows(int oldIndex, int newIndex) {
      return false;//Legacy, use canDrop() instead
    }

    @Override
    public boolean canDrop(int oldIndex, int newIndex, @NotNull Position position) {
      DefaultMutableTreeNode oldNode = (DefaultMutableTreeNode)myTree.getPathForRow(oldIndex).getLastPathComponent();
      DefaultMutableTreeNode newNode = (DefaultMutableTreeNode)myTree.getPathForRow(newIndex).getLastPathComponent();
      DefaultMutableTreeNode oldParent = (DefaultMutableTreeNode)oldNode.getParent();
      DefaultMutableTreeNode newParent = (DefaultMutableTreeNode)newNode.getParent();
      NodeKind oldKind = getKind(oldNode);
      NodeKind newKind = getKind(newNode);
      ConfigurationType oldType = getType(oldNode);
      ConfigurationType newType = getType(newNode);
      if (oldParent == newParent) {
        if (oldNode.getPreviousSibling() == newNode && position == Position.BELOW) {
          return false;
        }
        if (oldNode.getNextSibling() == newNode && position == Position.ABOVE) {
          return false;
        }
      }
      if (oldType != newType || newParent == oldNode || oldParent == newNode)
        return false;
      if (oldKind == FOLDER && newKind != FOLDER) {
        return false;
      }
      if (!oldKind.supportsDnD() || !newKind.supportsDnD()) {
        return false;
      }
      if (oldKind == TEMPORARY_CONFIGURATION && newKind == CONFIGURATION && position == Position.ABOVE)
        return false;
      if (oldKind == CONFIGURATION && newKind == TEMPORARY_CONFIGURATION && position == Position.BELOW)
        return false;
      if (oldKind == CONFIGURATION && newKind == TEMPORARY_CONFIGURATION && position == Position.ABOVE && newNode.getPreviousSibling() != null && getKind(newNode.getPreviousSibling()) != CONFIGURATION)
        return false;
      if (oldKind == TEMPORARY_CONFIGURATION && newKind == CONFIGURATION && position == Position.BELOW && newNode.getNextSibling() != null && getKind(newNode.getNextSibling()) != TEMPORARY_CONFIGURATION)
        return false;
      if (oldParent == newNode.getParent()) { //Same parent
        if (oldKind.isConfiguration() && newKind.isConfiguration()) {
          return oldKind == newKind;//both are temporary or saved
        } else if (oldKind.isConfiguration() && newKind == FOLDER){
          RunnerAndConfigurationSettings oldSettings = getSettings(oldNode);
          return (oldSettings != null && ((DefaultMutableTreeNode)newNode.getParent()).getUserObject() == oldSettings.getType());
        } else if (oldKind == FOLDER) {
          return true;
        }
      }
      return true;
    }

    @Override
    public boolean isDropInto(JComponent component, int oldIndex, int newIndex) {
      DefaultMutableTreeNode oldNode = (DefaultMutableTreeNode)myTree.getPathForRow(oldIndex).getLastPathComponent();
      DefaultMutableTreeNode newNode = (DefaultMutableTreeNode)myTree.getPathForRow(newIndex).getLastPathComponent();
      return getKind(oldNode).isConfiguration() && getKind(newNode) == FOLDER;
    }

    @Override
    public void drop(int oldIndex, int newIndex, @NotNull Position position) {
      DefaultMutableTreeNode oldNode = (DefaultMutableTreeNode)myTree.getPathForRow(oldIndex).getLastPathComponent();
      DefaultMutableTreeNode newNode = (DefaultMutableTreeNode)myTree.getPathForRow(newIndex).getLastPathComponent();
      DefaultMutableTreeNode newParent = (DefaultMutableTreeNode)newNode.getParent();
      NodeKind oldKind = getKind(oldNode);
      NodeKind newKind = getKind(newNode);
      if (isDropInto(myTree, oldIndex, newIndex)) { //Drop in folder
        removeNodeFromParent(oldNode);
        int index = newNode.getChildCount();
        if (oldKind == CONFIGURATION) {
          for (int i = 0; i < newNode.getChildCount(); i++) {
            if (getKind((DefaultMutableTreeNode)newNode.getChildAt(i)) == TEMPORARY_CONFIGURATION) {
              index = i;
              break;
            }
          }
        }
        insertNodeInto(oldNode, newNode, index);
        myTree.expandPath(new TreePath(newNode.getPath()));
      }
      else {
        removeNodeFromParent(oldNode);
        int index = newParent.getIndex(newNode);
        if (position == Position.BELOW)
          index++;
        insertNodeInto(oldNode, newParent, index);
      }
      myTree.setSelectionPath(new TreePath(oldNode.getPath()));
    }

    @Override
    public void insertNodeInto(MutableTreeNode newChild, MutableTreeNode parent, int index) {
      super.insertNodeInto(newChild, parent, index);
      Object userObject = ((DefaultMutableTreeNode)newChild).getUserObject();
      if (!getKind((DefaultMutableTreeNode)newChild).isConfiguration()) {
        return;
      }
      String newFolderName = getKind((DefaultMutableTreeNode)parent) == FOLDER
                             ? (String)((DefaultMutableTreeNode)parent).getUserObject()
                             : null;
      if (userObject instanceof RunnerAndConfigurationSettings) {
        ((RunnerAndConfigurationSettings)userObject).setFolderName(newFolderName);
      }
      else if (userObject instanceof SingleConfigurationConfigurable) {
        ((SingleConfigurationConfigurable)userObject).setFolderName(newFolderName);
      }
    }

    @Nullable
    private RunnerAndConfigurationSettings getSettings(@NotNull DefaultMutableTreeNode treeNode) {
      Object userObject = treeNode.getUserObject();
      if (userObject instanceof SingleConfigurationConfigurable) {
        SingleConfigurationConfigurable configurable = (SingleConfigurationConfigurable)userObject;
        return (RunnerAndConfigurationSettings)configurable.getSettings();
      } else if (userObject instanceof RunnerAndConfigurationSettings) {
        return (RunnerAndConfigurationSettings)userObject;
      }
      return null;
    }

    @Nullable
    private ConfigurationType getType(DefaultMutableTreeNode treeNode) {
      Object userObject = treeNode.getUserObject();
      if (userObject instanceof SingleConfigurationConfigurable) {
        SingleConfigurationConfigurable configurable = (SingleConfigurationConfigurable)userObject;
        return configurable.getConfiguration().getType();
      } else if (userObject instanceof RunnerAndConfigurationSettings) {
        return ((RunnerAndConfigurationSettings)userObject).getType();
      } else if (userObject instanceof ConfigurationType) {
        return (ConfigurationType)userObject;
      }
      if (treeNode.getParent() instanceof DefaultMutableTreeNode) {
        return getType((DefaultMutableTreeNode)treeNode.getParent());
      }
      return null;
    }
  }
}
