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
package com.intellij.xdebugger.impl.ui.tree.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.WatchMessageNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreePath;

/**
 * @author nik
 */
public abstract class XDebuggerTreeActionBase extends AnAction {
  @Override
  public void actionPerformed(final AnActionEvent e) {
    XDebuggerTreeNode node = getSelectionPathLastNode(e.getDataContext());
    if (node instanceof XValueNodeImpl) {
      XValueNodeImpl valueNode = (XValueNodeImpl)node;
      String nodeName = valueNode.getName();
      if (nodeName != null) {
        perform(valueNode, nodeName, e);
      }
    }
    else if (node instanceof WatchMessageNode) {
      perform((WatchMessageNode)node, e);
    }
  }

  protected void perform(WatchMessageNode node, AnActionEvent e) {
  }

  protected abstract void perform(final XValueNodeImpl node, @NotNull String nodeName, final AnActionEvent e);

  @Override
  public void update(final AnActionEvent e) {
    XDebuggerTreeNode node = getSelectionPathLastNode(e.getDataContext());
    if (node instanceof XValueNodeImpl) {
      e.getPresentation().setEnabled(isEnabled((XValueNodeImpl)node));
    }
    else {
      e.getPresentation().setEnabled(node instanceof WatchMessageNode && isEnabled((WatchMessageNode)node));
    }
  }

  protected boolean isEnabled(final XValueNodeImpl node) {
    return node.getName() != null;
  }

  protected boolean isEnabled(WatchMessageNode node) {
    return false;
  }

  @Nullable
  private static XDebuggerTreeNode getSelectionPathLastNode(DataContext dataContext) {
    XDebuggerTree tree = XDebuggerTree.getTree(dataContext);
    if (tree == null) return null;

    TreePath path = tree.getSelectionPath();
    if (path == null) return null;

    Object node = path.getLastPathComponent();
    return node instanceof XDebuggerTreeNode ? (XDebuggerTreeNode)node : null;
  }

  @Nullable
  public static XValueNodeImpl getSelectedNode(final DataContext dataContext) {
    XDebuggerTreeNode node = getSelectionPathLastNode(dataContext);
    return node instanceof XValueNodeImpl ? (XValueNodeImpl)node : null;
  }

  @Nullable
  public static XValue getSelectedValue(@NotNull DataContext dataContext) {
    XValueNodeImpl node = getSelectedNode(dataContext);
    return node != null ? node.getValueContainer() : null;
  }
}
