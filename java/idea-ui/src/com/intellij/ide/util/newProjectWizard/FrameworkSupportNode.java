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
package com.intellij.ide.util.newProjectWizard;

import com.intellij.ide.util.frameworkSupport.FrameworkSupportConfigurable;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportProvider;
import com.intellij.ide.util.newProjectWizard.impl.FrameworkSupportModelBase;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.GuiUtils;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
* @author nik
*/
public class FrameworkSupportNode extends CheckedTreeNode {
  private final FrameworkSupportProvider myProvider;
  private final FrameworkSupportNode myParentNode;
  private final FrameworkSupportConfigurable myConfigurable;
  private final List<FrameworkSupportNode> myChildren = new ArrayList<FrameworkSupportNode>();

  public FrameworkSupportNode(final FrameworkSupportProvider provider, final FrameworkSupportNode parentNode, final FrameworkSupportModelBase model,
                              Disposable parentDisposable) {
    super(provider);
    setChecked(false);
    myProvider = provider;
    myParentNode = parentNode;
    model.registerComponent(provider, this);
    myConfigurable = provider.createConfigurable(model);
    Disposer.register(parentDisposable, myConfigurable);
    if (parentNode != null) {
      parentNode.add(this);
      parentNode.myChildren.add(this);
    }

    setConfigurableComponentEnabled(false);
  }

  public List<FrameworkSupportNode> getChildren() {
    return myChildren;
  }

  public void setConfigurableComponentEnabled(final boolean enable) {
    JComponent component = getConfigurable().getComponent();
    if (component != null) {
      UIUtil.setEnabled(component, enable, true);
    }
  }

  public FrameworkSupportProvider getProvider() {
    return myProvider;
  }

  public FrameworkSupportNode getParentNode() {
    return myParentNode;
  }

  public FrameworkSupportConfigurable getConfigurable() {
    return myConfigurable;
  }

  public static void sortByTitle(List<FrameworkSupportNode> nodes) {
    if (nodes.isEmpty()) return;

    Collections.sort(nodes, new Comparator<FrameworkSupportNode>() {
      public int compare(final FrameworkSupportNode o1, final FrameworkSupportNode o2) {
        return getTitleWithoutMnemonic(o1.getProvider()).compareTo(getTitleWithoutMnemonic(o2.getProvider()));
      }
    });
    for (FrameworkSupportNode node : nodes) {
      node.sortChildren();
    }
  }

  public String getTitle() {
    return getTitleWithoutMnemonic(myProvider);
  }

  private static String getTitleWithoutMnemonic(final FrameworkSupportProvider provider) {
    return GuiUtils.getTextWithoutMnemonicEscaping(provider.getTitle());
  }

  private void sortChildren() {
    sortByTitle(myChildren);
  }
}
