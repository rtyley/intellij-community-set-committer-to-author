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
package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TitlePanel;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This dialog allows selecting source paths inside selected source archives or directories.
 *
 * @author max
 * @author Constantine.Plotnikov
 */
public class DetectedSourceRootsDialog extends DialogWrapper {
  /**
   * A tree with paths.  The tree relies on the CheckboxTree for selection and unselection policy.
   */
  private CheckboxTree myTree;
  /**
   * Root node for the tree. The tree is three-level:
   * <ul>
   * <li>The root is a fake node that just holds child nodes.</li>
   * <li>The second level is archives or directories selected on the previous selection step.</li>
   * <li>The third level are paths with java sources inside pervious selection.</li>
   * </ul>
   */
  private CheckedTreeNode myRootNode;
  private JScrollPane myPane;

  public DetectedSourceRootsDialog(Component component, List<SuggestedChildRootInfo> suggestedRoots) {
    super(component, true);
    init(suggestedRoots);
  }

  public DetectedSourceRootsDialog(Project project, List<SuggestedChildRootInfo> suggestedRoots) {
    super(project, true);
    init(suggestedRoots);
  }

  private void init(List<SuggestedChildRootInfo> suggestedRoots) {
    myRootNode = createTree(suggestedRoots);
    myTree = createCheckboxTree();
    myPane = ScrollPaneFactory.createScrollPane(myTree);
    setTitle("Detected Source Roots");
    init();
  }

  private CheckboxTree createCheckboxTree() {
    CheckboxTree tree = new CheckboxTree(new CheckboxTree.CheckboxTreeCellRenderer(true) {
      public void customizeRenderer(JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {
        if (!(value instanceof CheckedTreeNode)) return;
        CheckedTreeNode node = (CheckedTreeNode)value;
        final Object userObject = node.getUserObject();
        VirtualFile file;
        if (userObject instanceof SuggestedChildRootInfo) {
          file = ((SuggestedChildRootInfo)userObject).getSuggestedRoot();
        }
        else if (userObject instanceof VirtualFile) {
          file = (VirtualFile)userObject;
        }
        else {
          return;
        }
        String text;
        SimpleTextAttributes attributes;
        Icon icon;
        boolean isValid = true;
        if (leaf) {
          VirtualFile ancestor = (VirtualFile)((CheckedTreeNode)node.getParent()).getUserObject();
          if (ancestor != null) {
            text = VfsUtil.getRelativePath(file, ancestor, File.separatorChar);
          }
          else {
            text = file.getPresentableUrl();
          }
          if (text == null) {
            isValid = false;
            text = file.getPresentableUrl();
          }
          attributes = SimpleTextAttributes.REGULAR_ATTRIBUTES;
          icon = PlatformIcons.DIRECTORY_CLOSED_ICON;
        }
        else {
          text = file.getPresentableUrl();
          if (text == null) {
            isValid = false;
          }
          attributes = SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
          icon = expanded ? PlatformIcons.DIRECTORY_OPEN_ICON : PlatformIcons.DIRECTORY_CLOSED_ICON;
        }
        final ColoredTreeCellRenderer textRenderer = getTextRenderer();
        textRenderer.setIcon(icon);
        if (!isValid) {
          textRenderer.append("[INVALID] ", SimpleTextAttributes.ERROR_ATTRIBUTES);
        }
        if (text != null) {
          textRenderer.append(text, attributes);
        }
      }
    }, myRootNode);
    tree.setRootVisible(false);
    TreeUtil.expandAll(tree);
    return tree;
  }

  private static CheckedTreeNode createTree(List<SuggestedChildRootInfo> suggestedRoots) {
    CheckedTreeNode root = new CheckedTreeNode(null);
    Map<VirtualFile, CheckedTreeNode> rootCandidateNodes = new HashMap<VirtualFile, CheckedTreeNode>();
    for (SuggestedChildRootInfo rootInfo : suggestedRoots) {
      final VirtualFile rootCandidate = rootInfo.getRootCandidate();
      CheckedTreeNode parent = rootCandidateNodes.get(rootCandidate);
      if (parent == null) {
        parent = new CheckedTreeNode(rootCandidate);
        rootCandidateNodes.put(rootCandidate, parent);
        root.add(parent);
      }
      parent.add(new CheckedTreeNode(rootInfo));
    }
    return root;
  }

  @Override
  protected JComponent createTitlePane() {
    return new TitlePanel("Choose Source Roots", "<html><body>IntelliJ IDEA just scanned files and detected following source root(s).<br>" +
                                                 "Select items in the tree below or press Cancel to cancel operation.</body></html>");
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myPane;
  }

  public SuggestedChildRootInfo[] getChosenRoots() {
    return myTree.getCheckedNodes(SuggestedChildRootInfo.class, null);
  }

  @NonNls
  @Override
  protected String getDimensionServiceKey() {
    return "DetectedSourceRootsDialog";
  }
}
