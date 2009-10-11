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

import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ide.CopyPasteManager;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;

/**
 * @author nik
 */
public class XCopyValueAction extends XDebuggerTreeActionBase {
  protected void perform(final XValueNodeImpl node, @NotNull final String nodeName, final AnActionEvent e) {
    //todo[nik] is it correct? Perhaps we should use evaluator.evaluateMessage here
    String value = node.getValue();
    CopyPasteManager.getInstance().setContents(new StringSelection(value));
  }

  protected boolean isEnabled(final XValueNodeImpl node) {
    return super.isEnabled(node) && node.getValue() != null;
  }
}
