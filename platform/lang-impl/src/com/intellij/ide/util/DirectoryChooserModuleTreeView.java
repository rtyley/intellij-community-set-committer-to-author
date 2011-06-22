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

package com.intellij.ide.util;

import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.projectView.impl.ModuleGroupUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.util.*;

/**
 * @author dsl
 */
public class DirectoryChooserModuleTreeView implements DirectoryChooserView {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.DirectoryChooserModuleTreeView");

  private final Tree myTree;
  private final List<DirectoryChooser.ItemWrapper>  myItems = new ArrayList<DirectoryChooser.ItemWrapper>();
  private final Map<DirectoryChooser.ItemWrapper, DefaultMutableTreeNode> myItemNodes = new HashMap<DirectoryChooser.ItemWrapper, DefaultMutableTreeNode>();
  private final Map<Module, DefaultMutableTreeNode> myModuleNodes = new HashMap<Module, DefaultMutableTreeNode>();
  private final Map<ModuleGroup, DefaultMutableTreeNode> myModuleGroupNodes = new HashMap<ModuleGroup, DefaultMutableTreeNode>();
  private final DefaultMutableTreeNode myRootNode;
  private final ProjectFileIndex myFileIndex;
  private final Project myProject;

  public DirectoryChooserModuleTreeView(Project project) {
    myRootNode = new DefaultMutableTreeNode();
    myTree = new Tree(myRootNode);
    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    myFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    myProject = project;
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    myTree.setCellRenderer(new MyTreeCellRenderer());
    new TreeSpeedSearch(myTree, new Convertor<TreePath, String>() {
      public String convert(final TreePath o) {
        final Object userObject = ((DefaultMutableTreeNode)o.getLastPathComponent()).getUserObject();
        if (userObject instanceof Module) {
          return ((Module)userObject).getName();
        }
        else {
          if (userObject == null) return "";
          return userObject.toString();
        }
      }
    }, true);
  }

  public void clearItems() {
    myRootNode.removeAllChildren();
    myItems.clear();
    myItemNodes.clear();
    myModuleNodes.clear();
    myModuleGroupNodes.clear();
    myRootNode.removeAllChildren();
    ((DefaultTreeModel)myTree.getModel()).nodeStructureChanged(myRootNode);
  }

  public JComponent getComponent() {
    return myTree;
  }

  public void onSelectionChange(final Runnable runnable) {
    myTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        runnable.run();
      }
    });
  }

  public DirectoryChooser.ItemWrapper getItemByIndex(int i) {
    return myItems.get(i);
  }

  public void clearSelection() {
    myTree.clearSelection();
  }

  public void selectItemByIndex(int selectionIndex) {
    if (selectionIndex < 0) {
      myTree.clearSelection();
    } else {
      final DirectoryChooser.ItemWrapper itemWrapper = myItems.get(selectionIndex);
      final DefaultMutableTreeNode node = myItemNodes.get(itemWrapper);
      final TreePath treePath = expandNode(node);
      myTree.setSelectionPath(treePath);
      myTree.scrollPathToVisible(treePath);
    }
  }

  private TreePath expandNode(final DefaultMutableTreeNode node) {
    final TreeNode[] path = node.getPath();
    final TreePath treePath = new TreePath(path);
    TreePath expandPath = treePath;
    if (myTree.getModel().isLeaf(expandPath.getLastPathComponent())) {
      expandPath = expandPath.getParentPath();
    }
    myTree.expandPath(expandPath);
    return treePath;
  }

  public void addItem(DirectoryChooser.ItemWrapper itemWrapper) {
    myItems.add(itemWrapper);
    final PsiDirectory directory = itemWrapper.getDirectory();
    final Module module = myFileIndex.getModuleForFile(directory.getVirtualFile());
    DefaultMutableTreeNode node = myModuleNodes.get(module);
    if (node == null) {
      node = new DefaultMutableTreeNode(module, true);
      final String[] groupPath = module != null ? ModuleManager.getInstance(myProject).getModuleGroupPath(module) : null;
      if (groupPath == null || groupPath.length == 0){
        insertNode(node, myRootNode);
      } else {
        final DefaultMutableTreeNode parentNode = ModuleGroupUtil.buildModuleGroupPath(new ModuleGroup(groupPath),
                                                                                       myRootNode,
                                                                                       myModuleGroupNodes,
                                                                                       new Consumer<ModuleGroupUtil.ParentChildRelation<DefaultMutableTreeNode>>() {
                                                                                         public void consume(final ModuleGroupUtil.ParentChildRelation<DefaultMutableTreeNode> parentChildRelation) {
                                                                                           insertNode(parentChildRelation.getChild(), parentChildRelation.getParent());
                                                                                         }
                                                                                       },
                                                                                       new Function<ModuleGroup, DefaultMutableTreeNode>() {
                                                                                         public DefaultMutableTreeNode fun(final ModuleGroup moduleGroup) {
                                                                                           return new DefaultMutableTreeNode(moduleGroup, true);
                                                                                         }
                                                                                       });
        insertNode(node, parentNode);
      }
      myModuleNodes.put(module, node);
    }
    final DefaultMutableTreeNode itemNode = new DefaultMutableTreeNode(itemWrapper, false);
    myItemNodes.put(itemWrapper, itemNode);
    insertNode(itemNode, node);
    ((DefaultTreeModel)myTree.getModel()).nodeStructureChanged(node);
  }

  private void insertNode(final DefaultMutableTreeNode nodeToInsert, DefaultMutableTreeNode rootNode) {
    final Enumeration enumeration = rootNode.children();
    ArrayList children = Collections.list(enumeration);
    final int index = Collections.binarySearch(children, nodeToInsert, new Comparator<DefaultMutableTreeNode>() {
      public int compare(DefaultMutableTreeNode node1, DefaultMutableTreeNode node2) {
        final Object o1 = node1.getUserObject();
        final Object o2 = node2.getUserObject();
        if (o1 instanceof Module && o2 instanceof Module) {
          return ((Module)o1).getName().compareToIgnoreCase(((Module)o2).getName());
        }
        if (o1 instanceof ModuleGroup && o2 instanceof ModuleGroup){
          return o1.toString().compareToIgnoreCase(o2.toString());
        }
        if (o1 instanceof ModuleGroup) return -1;
        if (o1 instanceof DirectoryChooser.ItemWrapper && o2 instanceof DirectoryChooser.ItemWrapper) {
          final VirtualFile virtualFile1 = ((DirectoryChooser.ItemWrapper)o1).getDirectory().getVirtualFile();
          final VirtualFile virtualFile2 = ((DirectoryChooser.ItemWrapper)o2).getDirectory().getVirtualFile();
          return Comparing.compare(virtualFile1.getPath(), virtualFile2.getPath());
        }
        return 1;
      }
    });
    final int insertionPoint = -(index+1);
    if (insertionPoint < 0 || insertionPoint > rootNode.getChildCount()) {
      LOG.error("insertionPoint = " + insertionPoint + "; children=" + children + "; node=" + nodeToInsert);
      return;
    }
    rootNode.insert(nodeToInsert, insertionPoint);
    ((DefaultTreeModel)myTree.getModel()).nodeStructureChanged(rootNode);
  }

  public void listFilled() {
    if (myModuleNodes.size() == 1) {
      final Iterator<DefaultMutableTreeNode> iterator = myItemNodes.values().iterator();
      if (iterator.hasNext()){
        final DefaultMutableTreeNode node = iterator.next();
        expandNode(node);
      }
    }
  }

  public int getItemsSize() {
    return myItems.size();
  }

  @Nullable
  public DirectoryChooser.ItemWrapper getSelectedItem() {
    final TreePath selectionPath = myTree.getSelectionPath();
    if (selectionPath == null) return null;
    final DefaultMutableTreeNode node = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
    return node.getUserObject() instanceof DirectoryChooser.ItemWrapper ? (DirectoryChooser.ItemWrapper)node.getUserObject() : null;
  }


  private class MyTreeCellRenderer extends ColoredTreeCellRenderer {
    public void customizeCellRenderer(JTree tree, Object nodeValue, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      final Object value = ((DefaultMutableTreeNode)nodeValue).getUserObject();
      if (value instanceof DirectoryChooser.ItemWrapper) {
        DirectoryChooser.ItemWrapper wrapper = (DirectoryChooser.ItemWrapper)value;
        DirectoryChooser.PathFragment[] fragments = wrapper.getFragments();
        for (DirectoryChooser.PathFragment fragment : fragments) {
          append(fragment.getText(),
                 fragment.isCommon() ? SimpleTextAttributes.REGULAR_ATTRIBUTES : SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        }
        setIcon(wrapper.getIcon(myFileIndex));
      }
      else if (value instanceof Module) {
        final Module module = (Module)value;
        append(module.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        int flags1 = expanded ? Iconable.ICON_FLAG_OPEN : Iconable.ICON_FLAG_CLOSED;
        setIcon(module.getModuleType().getNodeIcon((flags1 & Iconable.ICON_FLAG_OPEN) != 0));
      } else if (value instanceof ModuleGroup) {
        append(value.toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        setIcon(expanded ? PlatformIcons.OPENED_MODULE_GROUP_ICON : PlatformIcons.CLOSED_MODULE_GROUP_ICON);
      }
    }
  }
}

