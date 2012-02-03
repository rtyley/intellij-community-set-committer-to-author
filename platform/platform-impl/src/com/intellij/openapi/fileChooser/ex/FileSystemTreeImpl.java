/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.fileChooser.ex;

import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileElement;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.fileChooser.impl.FileComparator;
import com.intellij.openapi.fileChooser.impl.FileTreeBuilder;
import com.intellij.openapi.fileChooser.impl.FileTreeStructure;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.UIBundle;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.containers.ConvertingIterator;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.event.*;
import java.io.IOException;
import java.util.*;

public class FileSystemTreeImpl implements FileSystemTree {
  private final Tree myTree;
  private final FileTreeStructure myTreeStructure;
  private final AbstractTreeBuilder myTreeBuilder;
  private final Project myProject;
  private final ArrayList<Runnable> myOkActions = new ArrayList<Runnable>(2);
  private final FileChooserDescriptor myDescriptor;

  private final List<Listener> myListeners = new ArrayList<Listener>();
  private final MyExpansionListener myExpansionListener = new MyExpansionListener();

  private Map<VirtualFile, VirtualFile> myEverExpanded = new WeakHashMap<VirtualFile, VirtualFile>();

  public FileSystemTreeImpl(@Nullable final Project project, final FileChooserDescriptor descriptor) {
    this(project, descriptor, new Tree(), null, null, null);
    myTree.setRootVisible(descriptor.isTreeRootVisible());
    myTree.setShowsRootHandles(true);
  }

  public FileSystemTreeImpl(@Nullable final Project project,
                            final FileChooserDescriptor descriptor,
                            final Tree tree,
                            @Nullable TreeCellRenderer renderer,
                            @Nullable final Runnable onInitialized,
                            @Nullable final Convertor<TreePath, String> speedSearchConverter) {
    myProject = project;
    myTreeStructure = new FileTreeStructure(project, descriptor);
    myDescriptor = descriptor;
    myTree = tree;
    final DefaultTreeModel treeModel = new DefaultTreeModel(new DefaultMutableTreeNode());
    myTree.setModel(treeModel);

    myTree.addTreeExpansionListener(myExpansionListener);

    myTreeBuilder = createTreeBuilder(myTree, treeModel, myTreeStructure, FileComparator.getInstance(), descriptor, new Runnable() {
      public void run() {
        myTree.expandPath(new TreePath(treeModel.getRoot()));
        if (onInitialized != null) {
          onInitialized.run();
        }
      }
    });

    Disposer.register(myTreeBuilder, new Disposable() {
      public void dispose() {
        myTree.removeTreeExpansionListener(myExpansionListener);
      }
    });

    if (project != null) {
      Disposer.register(project, myTreeBuilder);
    }

    myTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(final TreeSelectionEvent e) {
        processSelectionChange();
      }
    });

    if (speedSearchConverter != null) {
      new TreeSpeedSearch(myTree, speedSearchConverter);
    } else {
      new TreeSpeedSearch(myTree);
    }
    myTree.setLineStyleAngled();
    TreeUtil.installActions(myTree);

    myTree.getSelectionModel().setSelectionMode(
        myTreeStructure.getChooserDescriptor().getChooseMultiple() ?
        TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION :
        TreeSelectionModel.SINGLE_TREE_SELECTION
    );
    registerTreeActions();

    if (renderer == null) {
      renderer = new NodeRenderer() {
        public void customizeCellRenderer(JTree tree,
                                          Object value,
                                          boolean selected,
                                          boolean expanded,
                                          boolean leaf,
                                          int row,
                                          boolean hasFocus) {
          super.customizeCellRenderer(tree, value, selected, expanded, leaf, row, hasFocus);
          final Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
          if (userObject instanceof FileNodeDescriptor) {
            String comment = ((FileNodeDescriptor)userObject).getComment();
            if (comment != null) {
              append(comment, SimpleTextAttributes.REGULAR_ATTRIBUTES);
            }
          }
        }
      };
    }
    myTree.setCellRenderer(renderer);

  }

  protected AbstractTreeBuilder createTreeBuilder(final JTree tree, DefaultTreeModel treeModel, final AbstractTreeStructure treeStructure,
                                                  final Comparator<NodeDescriptor> comparator, FileChooserDescriptor descriptor,
                                                  @Nullable final Runnable onInitialized) {
    return new FileTreeBuilder(tree, treeModel, treeStructure, comparator, descriptor, onInitialized);
  }

  private void registerTreeActions() {
    myTree.registerKeyboardAction(
        new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            performEnterAction(true);
          }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_FOCUSED
    );
    myTree.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) performEnterAction(false);
      }
    });
  }

  private void performEnterAction(boolean toggleNodeState) {
    TreePath path = myTree.getSelectionPath();
    if (path != null) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      if (node != null && node.isLeaf()) {
        fireOkAction();
      }
      else if (toggleNodeState) {
        if (myTree.isExpanded(path)) {
          myTree.collapsePath(path);
        }
        else {
          myTree.expandPath(path);
        }
      }
    }
  }

  public void addOkAction(Runnable action) { myOkActions.add(action); }

  private void fireOkAction() {
    for (Runnable action : myOkActions) {
      action.run();
    }
  }

  public void registerMouseListener(final ActionGroup group) {
    PopupHandler.installUnknownPopupHandler(myTree, group, ActionManager.getInstance());
  }

  public boolean areHiddensShown() {
    return myTreeStructure.areHiddensShown();
  }

  public void showHiddens(boolean showHidden) {
    myTreeStructure.showHiddens(showHidden);
    updateTree();
  }

  public void updateTree() {
    myTreeBuilder.queueUpdate();
  }

  public void dispose() {
    if (myTreeBuilder != null) {
      Disposer.dispose(myTreeBuilder);
    }

    myEverExpanded.clear();
  }

  public AbstractTreeBuilder getTreeBuilder() {
    return myTreeBuilder;
  }

  public void select(VirtualFile file, @Nullable final Runnable onDone) {
    select(new VirtualFile[]{file}, onDone);
  }

  public void select(VirtualFile[] file, @Nullable final Runnable onDone) {
    Object[] elements = new Object[file.length];
    for (int i = 0; i < file.length; i++) {
      VirtualFile eachFile = file[i];
      elements[i] = getFileElementFor(eachFile);
    }

    myTreeBuilder.select(elements, onDone);
  }

  public void expand(final VirtualFile file, @Nullable final Runnable onDone) {
    myTreeBuilder.expand(getFileElementFor(file), onDone);
  }

  @Nullable
  private static FileElement getFileElementFor(VirtualFile file) {
    VirtualFile selectFile;

    if ((file.getFileSystem() instanceof JarFileSystem) && file.getParent() == null) {
      selectFile = JarFileSystem.getInstance().getVirtualFileForJar(file);
      if (selectFile == null) {
        return null;
      }
    }
    else {
      selectFile = file;
    }

    return new FileElement(selectFile, selectFile.getName());
  }

  public Exception createNewFolder(final VirtualFile parentDirectory, final String newFolderName) {
    final Exception[] failReason = new Exception[] { null };
    CommandProcessor.getInstance().executeCommand(
        myProject, new Runnable() {
          public void run() {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              public void run() {
                try {
                  VirtualFile parent = parentDirectory;
                  for (String name : StringUtil.tokenize(newFolderName, "\\/")) {
                    VirtualFile folder = parent.createChildDirectory(this, name);
                    updateTree();
                    select(folder, null);
                    parent = folder;
                  }
                }
                catch (IOException e) {
                  failReason[0] = e;
                }
              }
            });
          }
        },
        UIBundle.message("file.chooser.create.new.folder.command.name"),
        null
    );
    return failReason[0];
  }

  public Exception createNewFile(final VirtualFile parentDirectory, final String newFileName, final FileType fileType, final String initialContent) {
    final Exception[] failReason = new Exception[] { null };
    CommandProcessor.getInstance().executeCommand(
        myProject, new Runnable() {
          public void run() {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              public void run() {
                try {
                  final String newFileNameWithExtension = newFileName.endsWith('.'+fileType.getDefaultExtension())? newFileName : newFileName+'.'+fileType.getDefaultExtension();
                  final VirtualFile file = parentDirectory.createChildData(this, newFileNameWithExtension);
                  VfsUtil.saveText(file, initialContent != null ? initialContent : "");
                  updateTree();
                  select(file, null);
                }
                catch (IOException e) {
                  failReason[0] = e;
                }
              }
            });
          }
        },
        UIBundle.message("file.chooser.create.new.file.command.name"),
        null
    );
    return failReason[0];
  }

  public JTree getTree() { return myTree; }

  @Nullable
  public VirtualFile getSelectedFile() {
    final TreePath path = myTree.getSelectionPath();
    if (path == null) return null;
    DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
    if (!(node.getUserObject() instanceof FileNodeDescriptor)) return null;
    FileNodeDescriptor descriptor = (FileNodeDescriptor)node.getUserObject();
    return descriptor.getElement().getFile();
  }

  public VirtualFile getNewFileParent() {
    if (getSelectedFile() != null) return getSelectedFile();

    final List<VirtualFile> roots = myDescriptor.getRoots();
    return roots.size() > 0 ? roots.get(0) : null;
  }

  public VirtualFile[] getSelectedFiles() {
    return collectSelectedFiles(new ConvertingIterator.IdConvertor<VirtualFile>());
  }

  public VirtualFile[] getChosenFiles() {
    return collectSelectedFiles(new Convertor<VirtualFile, VirtualFile>() {
      @Nullable
      public VirtualFile convert(VirtualFile file) {
        if (file == null || !file.isValid()) return null;
        return myTreeStructure.getChooserDescriptor().getFileToSelect(file);
      }
    });
  }

  private VirtualFile[] collectSelectedFiles(Convertor<VirtualFile, VirtualFile> fileConverter) {
    TreePath[] paths = myTree.getSelectionPaths();
    if (paths == null) return VirtualFile.EMPTY_ARRAY;
    ArrayList<VirtualFile> files = new ArrayList<VirtualFile>(paths.length);

    for (TreePath path : paths) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      if (!(node.getUserObject() instanceof FileNodeDescriptor)) return VirtualFile.EMPTY_ARRAY;
      FileNodeDescriptor descriptor = (FileNodeDescriptor)node.getUserObject();
      VirtualFile file = fileConverter.convert(descriptor.getElement().getFile());
      if (file != null && file.isValid()) files.add(file);
    }
    return VfsUtil.toVirtualFileArray(files);
  }

  public boolean selectionExists() {
    TreePath[] selectedPaths = myTree.getSelectionPaths();
    return selectedPaths != null && selectedPaths.length != 0;
  }

  public boolean isUnderRoots(VirtualFile file) {
    final List<VirtualFile> roots = myDescriptor.getRoots();
    if (roots.size() == 0) {
      return true;
    }
    for (VirtualFile root : roots) {
      if (root == null) continue;
      if (VfsUtilCore.isAncestor(root, file, false)) {
        return true;
      }
    }
    return false;
  }

  public void addListener(final Listener listener, final Disposable parent) {
    myListeners.add(listener);
    Disposer.register(parent, new Disposable() {
      public void dispose() {
        myListeners.remove(listener);
      }
    });
  }

  private void fireSelection(List<VirtualFile> selection) {
    for (Listener each : myListeners) {
      each.selectionChanged(selection);
    }
  }

  private void processSelectionChange() {
    if (myListeners.size() == 0) return;
    List<VirtualFile> selection = new ArrayList<VirtualFile>();

    final TreePath[] paths = myTree.getSelectionPaths();
    if (paths != null) {
      for (TreePath each : paths) {
        final Object last = each.getLastPathComponent();
        if (last instanceof DefaultMutableTreeNode) {
          final Object object = ((DefaultMutableTreeNode)last).getUserObject();
          if (object instanceof FileNodeDescriptor) {
            final FileElement element = ((FileNodeDescriptor)object).getElement();
            final VirtualFile file = element.getFile();
            if (file != null) {
              selection.add(file);
            }
          }
        }
      }
    }

    fireSelection(selection);
  }

  private class MyExpansionListener implements TreeExpansionListener {
    public void treeExpanded(final TreeExpansionEvent event) {
      if (myTreeBuilder == null || !myTreeBuilder.isNodeBeingBuilt(event.getPath())) return;

      TreePath path = event.getPath();
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      if (node.getUserObject() instanceof FileNodeDescriptor) {
        FileNodeDescriptor nodeDescriptor = (FileNodeDescriptor)node.getUserObject();
        final FileElement fileDescriptor = nodeDescriptor.getElement();
        final VirtualFile virtualFile = fileDescriptor.getFile();
        if (virtualFile != null) {
          if (!myEverExpanded.containsKey(virtualFile)) {
            if (virtualFile instanceof NewVirtualFile) {
              ((NewVirtualFile)virtualFile).markDirty();
            }
            myEverExpanded.put(virtualFile, virtualFile);
          }


          boolean async = myTreeBuilder.getTreeStructure().isToBuildChildrenInBackground(virtualFile);
          if (virtualFile instanceof NewVirtualFile) {
            RefreshQueue.getInstance().refresh(async, false, null, ModalityState.stateForComponent(myTree), virtualFile);
          }
          else {
            virtualFile.refresh(async, false);
          }
        }
      }
    }

    public void treeCollapsed(TreeExpansionEvent event) {
    }
  }
}
