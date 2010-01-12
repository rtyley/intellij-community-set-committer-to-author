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

package com.intellij.moduleDependencies;

import com.intellij.CommonBundle;
import com.intellij.ProjectTopics;
import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.cyclicDependencies.CyclicGraphUtil;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.TreeExpander;
import com.intellij.ide.actions.ContextHelpAction;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.*;
import com.intellij.ui.content.Content;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.containers.HashMap;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.Graph;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * User: anna
 * Date: Feb 10, 2005
 */
public class ModulesDependenciesPanel extends JPanel implements ModuleRootListener, Disposable {
  @NonNls private static final String DIRECTION = "FORWARD_ANALIZER";
  private Content myContent;
  private final Project myProject;
  private Tree myLeftTree;
  private DefaultTreeModel myLeftTreeModel;

  private final Tree myRightTree;
  private final DefaultTreeModel myRightTreeModel;

  private Graph<Module> myModulesGraph;
  private final Module[] myModules;

  private final Splitter mySplitter;
  @NonNls private static final String ourHelpID = "module.dependencies.tool.window";

  public ModulesDependenciesPanel(final Project project, final Module[] modules) {
    super(new BorderLayout());
    myProject = project;
    myModules = modules;

    //noinspection HardCodedStringLiteral
    myRightTreeModel = new DefaultTreeModel(new DefaultMutableTreeNode("Root"));
    myRightTree = new Tree(myRightTreeModel);
    initTree(myRightTree, true);

    initLeftTree();

    mySplitter = new Splitter();
    mySplitter.setFirstComponent(new MyTreePanel(myLeftTree, myProject));
    mySplitter.setSecondComponent(new MyTreePanel(myRightTree, myProject));

    setSplitterProportion();
    add(mySplitter, BorderLayout.CENTER);
    add(createNorthPanel(), BorderLayout.NORTH);

    project.getMessageBus().connect(this).subscribe(ProjectTopics.PROJECT_ROOTS, this);
  }

  private void setSplitterProportion() {
    if (mySplitter == null){
      return;
    }
    myModulesGraph = buildGraph();
    DFSTBuilder<Module> builder = new DFSTBuilder<Module>(myModulesGraph);
    builder.buildDFST();
    if (builder.isAcyclic()){
      mySplitter.setProportion(1.f);
    } else {
      mySplitter.setProportion(0.5f);
    }
  }

  public void dispose() {
  }

  public ModulesDependenciesPanel(final Project project) {
    this(project, ModuleManager.getInstance(project).getModules());
  }

  private JComponent createNorthPanel(){
    DefaultActionGroup group = new DefaultActionGroup();

    group.add(new AnAction(CommonBundle.message("action.close"), AnalysisScopeBundle.message("action.close.modules.dependencies.description"), IconLoader.getIcon("/actions/cancel.png")){
      public void actionPerformed(AnActionEvent e) {
        DependenciesAnalyzeManager.getInstance(myProject).closeContent(myContent);
      }
    });

    appendDependenciesAction(group);

    group.add(new ToggleAction(AnalysisScopeBundle.message("action.module.dependencies.direction"), "", isForwardDirection() ? IconLoader.getIcon("/actions/sortAsc.png") : IconLoader.getIcon("/actions/sortDesc.png")){
      public boolean isSelected(AnActionEvent e) {
        return isForwardDirection();
      }

      public void setSelected(AnActionEvent e, boolean state) {
        PropertiesComponent.getInstance(myProject).setValue(DIRECTION, String.valueOf(state));
        initLeftTreeModel();
      }

      public void update(final AnActionEvent e) {
        e.getPresentation().setIcon(isForwardDirection() ? IconLoader.getIcon("/actions/sortAsc.png") : IconLoader.getIcon("/actions/sortDesc.png"));
      }
    });

    group.add(new ContextHelpAction(ourHelpID));

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);
    return toolbar.getComponent();
  }

  private boolean isForwardDirection() {
    final String value = PropertiesComponent.getInstance(myProject).getValue(DIRECTION);
    return value == null || Boolean.parseBoolean(value);
  }

  private static void appendDependenciesAction(final DefaultActionGroup group) {
    final AnAction analyzeDepsAction = ActionManager.getInstance().getAction(IdeActions.ACTION_ANALYZE_DEPENDENCIES);
    group.add(new AnAction(analyzeDepsAction.getTemplatePresentation().getText(),
                           analyzeDepsAction.getTemplatePresentation().getDescription(),
                           IconLoader.getIcon("/general/toolWindowInspection.png")){

      public void actionPerformed(AnActionEvent e) {
        analyzeDepsAction.actionPerformed(e);
      }


      public void update(AnActionEvent e) {
        analyzeDepsAction.update(e);
      }
    });
  }

  private void buildRightTree(Module module){
    final DefaultMutableTreeNode root = (DefaultMutableTreeNode)myRightTreeModel.getRoot();
    root.removeAllChildren();
    final Set<List<Module>> cycles = CyclicGraphUtil.getNodeCycles(myModulesGraph, module);
    int index = 1;
    for (List<Module> modules : cycles) {
      final DefaultMutableTreeNode cycle = new DefaultMutableTreeNode(
        AnalysisScopeBundle.message("module.dependencies.cycle.node.text", Integer.toString(index++).toUpperCase()));
      root.add(cycle);
      cycle.add(new DefaultMutableTreeNode(new MyUserObject(false, module)));
      for (Module moduleInCycle : modules) {
        cycle.add(new DefaultMutableTreeNode(new MyUserObject(false, moduleInCycle)));
      }
    }
    ((DefaultTreeModel)myRightTree.getModel()).reload();
    TreeUtil.expandAll(myRightTree);
  }

  private void initLeftTreeModel(){
    final DefaultMutableTreeNode root = (DefaultMutableTreeNode)myLeftTreeModel.getRoot();
    root.removeAllChildren();
    myModulesGraph = buildGraph();
    setSplitterProportion();
    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
        final Map<Module, Boolean> inCycle = new HashMap<Module, Boolean>();
        for (Module module : myModules) {
          if (progressIndicator != null) {
            if (progressIndicator.isCanceled()) return;
            progressIndicator.setText(AnalysisScopeBundle.message("update.module.tree.progress.text", module.getName()));
          }
          if (!module.isDisposed()) {
            Boolean isInCycle = inCycle.get(module);
            if (isInCycle == null) {
              isInCycle = !CyclicGraphUtil.getNodeCycles(myModulesGraph, module).isEmpty();
              inCycle.put(module, isInCycle);
            }
            final DefaultMutableTreeNode moduleNode = new DefaultMutableTreeNode(new MyUserObject(isInCycle.booleanValue(), module));
            root.add(moduleNode);
            final Iterator<Module> out = myModulesGraph.getOut(module);
            while (out.hasNext()) {
              moduleNode.add(new DefaultMutableTreeNode(new MyUserObject(false, out.next())));
            }
          }
        }
      }
    }, AnalysisScopeBundle.message("update.module.tree.progress.title"), true, myProject);
    sortSubTree(root);
    myLeftTreeModel.reload();
  }

  private static void sortSubTree(final DefaultMutableTreeNode root) {
    TreeUtil.sort(root, new Comparator() {
      public int compare(final Object o1, final Object o2) {
        DefaultMutableTreeNode node1 = (DefaultMutableTreeNode)o1;
        DefaultMutableTreeNode node2 = (DefaultMutableTreeNode)o2;
        if (!(node1.getUserObject() instanceof MyUserObject)){
          return 1;
        }
        else if (!(node2.getUserObject() instanceof MyUserObject)){
          return -1;
        }
        return (node1.getUserObject().toString().compareTo(node2.getUserObject().toString()));
      }
    });
  }

  private void selectCycleUpward(final DefaultMutableTreeNode selection){
    ArrayList<DefaultMutableTreeNode> selectionNodes = new ArrayList<DefaultMutableTreeNode>();
    selectionNodes.add(selection);
    DefaultMutableTreeNode current = (DefaultMutableTreeNode)selection.getParent();
    boolean flag = false;
    while (current != null && current.getUserObject() != null){
      if (current.getUserObject().equals(selection.getUserObject())){
        flag = true;
        selectionNodes.add(current);
        break;
      }
      selectionNodes.add(current);
      current = (DefaultMutableTreeNode)current.getParent();
    }
    if (flag){
      for (DefaultMutableTreeNode node : selectionNodes) {
        ((MyUserObject)node.getUserObject()).setInCycle(true);
      }
    }
    if (current != null) current = (DefaultMutableTreeNode)current.getParent();
    while (current != null) {
      final Object userObject = current.getUserObject();
      if (userObject instanceof MyUserObject) {
        ((MyUserObject)userObject).setInCycle(false);
      }
      current = (DefaultMutableTreeNode)current.getParent();
    }
    myLeftTree.repaint();
  }

  private void initLeftTree(){
    //noinspection HardCodedStringLiteral
    final DefaultMutableTreeNode root = new DefaultMutableTreeNode("Root");
    myLeftTreeModel = new DefaultTreeModel(root);
    initLeftTreeModel();
    myLeftTree = new Tree(myLeftTreeModel);
    initTree(myLeftTree, false);

    myLeftTree.addTreeExpansionListener(new TreeExpansionListener() {
      public void treeCollapsed(TreeExpansionEvent event) {
      }

      public void treeExpanded(TreeExpansionEvent event) {
        final DefaultMutableTreeNode expandedNode = (DefaultMutableTreeNode)event.getPath().getLastPathComponent();
        for(int i = 0; i < expandedNode.getChildCount(); i++){
          DefaultMutableTreeNode child = (DefaultMutableTreeNode)expandedNode.getChildAt(i);
          if (child.getChildCount() == 0){
            Module module = ((MyUserObject)child.getUserObject()).getModule();
            final Iterator<Module> out = myModulesGraph.getOut(module);
            while (out.hasNext()) {
              final Module nextModule = out.next();
              child.add(new DefaultMutableTreeNode(new MyUserObject(false, nextModule)));
            }
            sortSubTree(child);
          }
        }
      }
    });

    myLeftTree.addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        final TreePath selectionPath = myLeftTree.getSelectionPath();
        if (selectionPath != null) {
          final DefaultMutableTreeNode selection = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
          if (selection != null){
            TreeUtil.traverseDepth(selection, new TreeUtil.Traverse() {
              public boolean accept(Object node) {
                DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)node;
                if (treeNode.getUserObject() instanceof MyUserObject){
                  ((MyUserObject)treeNode.getUserObject()).setInCycle(false);
                }
                return true;
              }
            });
            selectCycleUpward(selection);
            buildRightTree(((MyUserObject)selection.getUserObject()).getModule());
          }
        }
      }
    });
    TreeUtil.selectFirstNode(myLeftTree);
  }

  private static ActionGroup createTreePopupActions(final boolean isRightTree, final Tree tree) {
    DefaultActionGroup group = new DefaultActionGroup();
    final TreeExpander treeExpander = new TreeExpander() {
      public void expandAll() {
        TreeUtil.expandAll(tree);
      }

      public boolean canExpand() {
        return isRightTree;
      }

      public void collapseAll() {
        TreeUtil.collapseAll(tree, 3);
      }

      public boolean canCollapse() {
        return true;
      }
    };

    final CommonActionsManager actionManager = CommonActionsManager.getInstance();
    if (isRightTree){
      group.add(actionManager.createExpandAllAction(treeExpander, tree));
    }
    group.add(actionManager.createCollapseAllAction(treeExpander, tree));
    group.add(ActionManager.getInstance().getAction(IdeActions.MODULE_SETTINGS));
    appendDependenciesAction(group);
    return group;
  }

  private static void initTree(Tree tree, boolean isRightTree) {
    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    tree.setCellRenderer(new MyTreeCellRenderer());
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);
    UIUtil.setLineStyleAngled(tree);

    TreeToolTipHandler.install(tree);
    TreeUtil.installActions(tree);
    new TreeSpeedSearch(tree);
    PopupHandler.installUnknownPopupHandler(tree, createTreePopupActions(isRightTree, tree), ActionManager.getInstance());
  }


  private Graph<Module> buildGraph() {
    final Graph<Module> graph = ModuleManager.getInstance(myProject).moduleGraph();
    if (isForwardDirection()) {
      return graph;
    }
    else {
      return new Graph<Module>() {
        public Collection<Module> getNodes() {
          return graph.getNodes();
        }

        public Iterator<Module> getIn(final Module n) {
          return graph.getOut(n);
        }

        public Iterator<Module> getOut(final Module n) {
          return graph.getIn(n);
        }

      };
    }
  }

  public void setContent(final Content content) {
    myContent = content;
  }

  public void beforeRootsChange(ModuleRootEvent event) {
  }

  public void rootsChanged(ModuleRootEvent event) {
    initLeftTreeModel();
    TreeUtil.selectFirstNode(myLeftTree);
  }

  private static class MyUserObject{
    private boolean myInCycle;
    private final Module myModule;

    public MyUserObject(final boolean inCycle, final Module module) {
      myInCycle = inCycle;
      myModule = module;
    }

    public boolean isInCycle() {
      return myInCycle;
    }

    public void setInCycle(final boolean inCycle) {
      myInCycle = inCycle;
    }

    public Module getModule() {
      return myModule;
    }

    public boolean equals(Object object) {
      return object instanceof MyUserObject && myModule.equals(((MyUserObject)object).getModule());      
    }

    public int hashCode() {
      return myModule.hashCode();
    }

    public String toString() {
      return myModule.getName();
    }
  }

  private static class MyTreePanel extends JPanel implements DataProvider{
    private final Tree myTree;
    private final Project myProject;
    public MyTreePanel(final Tree tree, Project project) {
      super(new BorderLayout());
      myTree = tree;
      myProject = project;
      add(ScrollPaneFactory.createScrollPane(myTree), BorderLayout.CENTER);
    }

    public Object getData(String dataId) {
      if (PlatformDataKeys.PROJECT.is(dataId)){
        return myProject;
      }
      if (LangDataKeys.MODULE_CONTEXT.is(dataId)){
        final TreePath selectionPath = myTree.getLeadSelectionPath();
        if (selectionPath != null && selectionPath.getLastPathComponent() instanceof DefaultMutableTreeNode){
          DefaultMutableTreeNode node = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
          if (node.getUserObject() instanceof MyUserObject){
            return ((MyUserObject)node.getUserObject()).getModule();
          }
        }
      }
      if (PlatformDataKeys.HELP_ID.is(dataId)) {
        return ourHelpID;
      }
      return null;
    }
  }
   private static class MyTreeCellRenderer extends ColoredTreeCellRenderer {
    public void customizeCellRenderer(
    JTree tree,
    Object value,
    boolean selected,
    boolean expanded,
    boolean leaf,
    int row,
    boolean hasFocus
  ){
      final Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
      if (!(userObject instanceof MyUserObject)){
        if (userObject != null){
          append(userObject.toString(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        }
        return;
      }
      MyUserObject node = (MyUserObject)userObject;
      Module module = node.getModule();
      setIcon(module.getModuleType().getNodeIcon(expanded));
      if (node.isInCycle()){
        append(module.getName(), SimpleTextAttributes.ERROR_ATTRIBUTES);
      } else {
        append(module.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
    }
  }
}
