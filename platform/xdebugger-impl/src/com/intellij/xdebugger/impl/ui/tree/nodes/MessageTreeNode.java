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
package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class MessageTreeNode extends XDebuggerTreeNode {
  private boolean myEllipsis;

  private MessageTreeNode(XDebuggerTree tree, final XDebuggerTreeNode parent, final String message, final SimpleTextAttributes attributes,
                          @Nullable Icon icon) {
    this(tree, parent, message, attributes, icon, false);
  }

  private MessageTreeNode(XDebuggerTree tree, final XDebuggerTreeNode parent, final String message, final SimpleTextAttributes attributes, @Nullable Icon icon,
                          final boolean ellipsis) {
    super(tree, parent, true);
    myEllipsis = ellipsis;
    setIcon(icon);
    myText.append(message, attributes);
  }

  protected MessageTreeNode(XDebuggerTree tree, XDebuggerTreeNode parent, boolean leaf) {
    super(tree, parent, leaf);
    myEllipsis = false;
  }

  private MessageTreeNode(XDebuggerTree tree, XDebuggerTreeNode parent, String infoMessage, String errorMessage) {
    super(tree, parent, true);
    myEllipsis = false;
  }

  protected List<? extends TreeNode> getChildren() {
    return Collections.emptyList();
  }

  public boolean isEllipsis() {
    return myEllipsis;
  }

  public List<? extends XDebuggerTreeNode> getLoadedChildren() {
    return null;
  }

  @Override
  public void clearChildren() {
  }

  public static MessageTreeNode createEllipsisNode(XDebuggerTree tree, XDebuggerTreeNode parent, final int remaining) {
    String message = remaining == -1 ? "..." : XDebuggerBundle.message("node.text.ellipsis.0.more.nodes.double.click.to.show", remaining);
    return new MessageTreeNode(tree, parent, message, SimpleTextAttributes.REGULAR_ATTRIBUTES, null, true);
  }

  public static MessageTreeNode createMessageNode(XDebuggerTree tree, XDebuggerTreeNode parent, String message, @Nullable Icon icon) {
    return new MessageTreeNode(tree, parent, message, SimpleTextAttributes.REGULAR_ATTRIBUTES, icon);
  }

  public static MessageTreeNode createLoadingMessage(XDebuggerTree tree, final XDebuggerTreeNode parent) {
    return new MessageTreeNode(tree, parent, XDebuggerUIConstants.COLLECTING_DATA_MESSAGE, XDebuggerUIConstants.COLLECTING_DATA_HIGHLIGHT_ATTRIBUTES, null);
  }

  public static MessageTreeNode createEvaluatingMessage(XDebuggerTree tree, final XDebuggerTreeNode parent, final String message) {
    return new MessageTreeNode(tree, parent, message, XDebuggerUIConstants.EVALUATING_EXPRESSION_HIGHLIGHT_ATTRIBUTES, null);
  }

  public static MessageTreeNode createEvaluatingMessage(XDebuggerTree tree, final XDebuggerTreeNode parent) {
    return new MessageTreeNode(tree, parent, XDebuggerUIConstants.EVALUATING_EXPRESSION_MESSAGE, XDebuggerUIConstants.EVALUATING_EXPRESSION_HIGHLIGHT_ATTRIBUTES, null);
  }

  public static MessageTreeNode createErrorMessage(XDebuggerTree tree, final XDebuggerTreeNode parent, @NotNull String errorMessage) {
    return new MessageTreeNode(tree, parent, errorMessage, XDebuggerUIConstants.ERROR_MESSAGE_ATTRIBUTES, XDebuggerUIConstants.ERROR_MESSAGE_ICON);
  }

  public static MessageTreeNode createInfoMessage(XDebuggerTree tree, final XDebuggerTreeNode parent, @NotNull String message) {
    return new MessageTreeNode(tree, parent, message, SimpleTextAttributes.REGULAR_ATTRIBUTES, XDebuggerUIConstants.INFORMATION_MESSAGE_ICON);
  }
}
