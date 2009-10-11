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
package com.intellij.cvsSupport2.ui.experts;

import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.fileChooser.FileSystemTreeFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import java.awt.*;
import java.io.File;

/**
 * @author lesya
 */
public abstract class SelectLocationStep extends WizardStep {
  protected final FileSystemTree myFileSystemTree;
  private ActionToolbar myFileSystemToolBar;
  private VirtualFile mySelectedFile;

  private final TreeSelectionListener myTreeSelectionListener = new TreeSelectionListener() {
        public void valueChanged(TreeSelectionEvent e) {
          getWizard().updateStep();
        }
      };

  public SelectLocationStep(String description, CvsWizard wizard, @Nullable final Project project) {
    super(description, wizard);
    final FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false) {
      public boolean isFileVisible(final VirtualFile file, final boolean showHiddenFiles) {
        if (!super.isFileVisible(file, showHiddenFiles)) return false;
        if (!showHiddenFiles && project != null && (! project.isDefault()) && ProjectRootManager.getInstance(project).getFileIndex().isIgnored(file)) {
          return false;
        }
        return true;
      }
    };
    myFileSystemTree = FileSystemTreeFactory.SERVICE.getInstance().createFileSystemTree(project, descriptor);
    myFileSystemTree.updateTree();

    JTree tree = myFileSystemTree.getTree();
    tree.addSelectionPath(tree.getPathForRow(0));
  }

  protected void init() {

    final DefaultActionGroup fileSystemActionGroup = createFileSystemActionGroup();
    myFileSystemToolBar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN,
                                                                          fileSystemActionGroup, true);

    myFileSystemTree.getTree().getSelectionModel().addTreeSelectionListener(myTreeSelectionListener);

    myFileSystemTree.getTree().setCellRenderer(new NodeRenderer());
     myFileSystemTree.getTree().addMouseListener(new PopupHandler() {
      public void invokePopup(Component comp, int x, int y) {
        ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.UPDATE_POPUP,
                                                                                      fileSystemActionGroup);
        popupMenu.getComponent().show(comp, x, y);
      }
    });

    super.init();
  }

  protected JComponent createComponent() {
    JPanel result = new MyPanel();
    result.add(myFileSystemToolBar.getComponent(), BorderLayout.NORTH);
    result.add(ScrollPaneFactory.createScrollPane(myFileSystemTree.getTree()), BorderLayout.CENTER);
    return result;
  }

  protected void dispose() {
    mySelectedFile = myFileSystemTree.getSelectedFile();   // remember the file - it will be requested after dispose
    myFileSystemTree.getTree().getSelectionModel().removeTreeSelectionListener(myTreeSelectionListener);
    Disposer.dispose(myFileSystemTree);
  }

  public boolean nextIsEnabled() {
    return myFileSystemTree.getSelectedFile() != null;
  }

  public boolean setActive() {
    return true;
  }

  private DefaultActionGroup createFileSystemActionGroup() {
    DefaultActionGroup group = FileSystemTreeFactory.SERVICE.getInstance().createDefaultFileSystemActions(myFileSystemTree);
    
    AnAction[] actions = getActions();

    if (actions.length > 0) group.addSeparator();

    for (AnAction action : actions) {
      group.add(action);
    }

    return group;
  }

  protected AnAction[] getActions(){
    return AnAction.EMPTY_ARRAY;
  }

  public File getSelectedFile() {
    if (mySelectedFile != null) {
      return CvsVfsUtil.getFileFor(mySelectedFile);
    }
    return CvsVfsUtil.getFileFor(myFileSystemTree.getSelectedFile());
  }

  public Component getPreferredFocusedComponent() {
    return myFileSystemTree.getTree();
  }

  private class MyPanel extends JPanel implements TypeSafeDataProvider {
    private MyPanel() {
      super(new BorderLayout());
    }

    public void calcData(final DataKey key, final DataSink sink) {
      if (key == PlatformDataKeys.VIRTUAL_FILE_ARRAY) {
        sink.put(PlatformDataKeys.VIRTUAL_FILE_ARRAY, myFileSystemTree.getSelectedFiles());
      }
      else if (key == FileSystemTree.DATA_KEY) {
        sink.put(FileSystemTree.DATA_KEY, myFileSystemTree);
      }
    }
  }
}
