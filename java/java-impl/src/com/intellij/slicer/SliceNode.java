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
package com.intellij.slicer;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.ui.DuplicateNodeRenderer;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author cdr
 */
public class SliceNode extends AbstractTreeNode<SliceUsage> implements DuplicateNodeRenderer.DuplicatableNode<SliceNode>, MyColoredTreeCellRenderer {
  protected List<AbstractTreeNode> myCachedChildren;
  private boolean initialized;
  private SliceNode duplicate;
  protected final DuplicateMap targetEqualUsages;
  private final SliceTreeBuilder myTreeBuilder;
  private final Collection<PsiElement> leafExpressions = new THashSet<PsiElement>(SliceLeafAnalyzer.LEAF_ELEMENT_EQUALITY);
  protected boolean changed;
  private int index; // my index in parent's mycachedchildren

  protected SliceNode(@NotNull Project project, SliceUsage sliceUsage, @NotNull DuplicateMap targetEqualUsages,
                      @NotNull Collection<PsiElement> leafExpressions) {
    this(project, sliceUsage, targetEqualUsages, null, leafExpressions);
  }

  protected SliceNode(@NotNull Project project, SliceUsage sliceUsage, @NotNull DuplicateMap targetEqualUsages,
                      SliceTreeBuilder treeBuilder, @NotNull Collection<PsiElement> leafExpressions) {
    super(project, sliceUsage);
    this.targetEqualUsages = targetEqualUsages;
    myTreeBuilder = treeBuilder;
    this.leafExpressions.addAll(leafExpressions);
  }

  protected SliceTreeBuilder getTreeBuilder() {
    return myTreeBuilder;
  }

  SliceNode copy(Collection<PsiElement> withLeaves) {
    SliceUsage newUsage = getValue().copy();
    SliceNode newNode = new SliceNode(getProject(), newUsage, targetEqualUsages, getTreeBuilder(), withLeaves);
    newNode.initialized = initialized;
    newNode.duplicate = duplicate;
    return newNode;
  }

  @NotNull
  public Collection<? extends AbstractTreeNode> getChildren() {
    ProgressIndicator current = ProgressManager.getInstance().getProgressIndicator();
    ProgressIndicator indicator = current == null ? new ProgressIndicatorBase() : current;
    if (current == null) {
      indicator.start();
    }
    final Collection[] nodes = new Collection[1];
    ((ProgressManagerImpl)ProgressManager.getInstance()).executeProcessUnderProgress(new Runnable(){
      public void run() {
        nodes[0] = getChildrenUnderProgress(ProgressManager.getInstance().getProgressIndicator());
      }
    }, indicator);
    if (current == null) {
      indicator.stop();
    }
    return nodes[0];
  }

  SliceNode getNext() {
    AbstractTreeNode parent = getParent();
    if (parent instanceof SliceNode) {
      Object[] children = getTreeBuilder().getTreeStructure().getChildElements(parent);
      return index == children.length - 1 ? null : (SliceNode)children[index + 1];
    }
    return null;
  }

  SliceNode getPrev() {
    AbstractTreeNode parent = getParent();
    if (parent instanceof SliceNode) {
      Object[] children = getTreeBuilder().getTreeStructure().getChildElements(parent);
      return index == 0 ? null : (SliceNode)children[index - 1];
    }
    return null;
  }

  protected List<? extends AbstractTreeNode> getChildrenUnderProgress(ProgressIndicator progress) {
    if (isUpToDate()) return myCachedChildren == null ? Collections.<AbstractTreeNode>emptyList() : myCachedChildren;
    final List<AbstractTreeNode> children = new ArrayList<AbstractTreeNode>();
    final SliceManager manager = SliceManager.getInstance(getProject());
    manager.runInterruptibly(new Runnable() {
      public void run() {
        Processor<SliceUsage> processor = new Processor<SliceUsage>() {
          public boolean process(SliceUsage sliceUsage) {
            manager.checkCanceled();
            SliceNode node = new SliceNode(myProject, sliceUsage, targetEqualUsages, getTreeBuilder(), getLeafExpressions());
            synchronized (children) {
              node.index = children.size();
              children.add(node);
            }
            return true;
          }
        };

        getValue().processChildren(processor, getTreeBuilder().dataFlowToThis);
      }
    }, new Runnable(){
      public void run() {
        changed = true;
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            if (getTreeBuilder().isDisposed()) return;
            DefaultMutableTreeNode node = getTreeBuilder().getNodeForElement(getValue());
            //myTreeBuilder.getUi().queueBackgroundUpdate(node, (NodeDescriptor)node.getUserObject(), new TreeUpdatePass(node));
            if (node == null) node = getTreeBuilder().getRootNode();
            getTreeBuilder().addSubtreeToUpdate(node);
          }
        });
      }
    }, progress);

    synchronized (children) {
      myCachedChildren = children;
    }
    return children;
  }

  private boolean isUpToDate() {
    if (myCachedChildren != null || !isValid() || getTreeBuilder().splitByLeafExpressions) {
      return true;
    }
    return false;
  }

  @NotNull
  @Override
  protected PresentationData createPresentation() {
    return new PresentationData(){
      @Override
      public Object[] getEqualityObjects() {
        return ArrayUtil.append(super.getEqualityObjects(), changed);
      }
    };
  }

  protected void update(PresentationData presentation) {
    if (!initialized) {
      duplicate = targetEqualUsages.putNodeCheckDupe(this);
      initialized = true;
    }
    if (presentation != null) {
      presentation.setChanged(presentation.isChanged() || changed);
      changed = false;
      if (duplicate != null) {
        presentation.setTooltip("Duplicate node");
      }
    }
  }

  public SliceNode getDuplicate() {
    return duplicate;
  }

  public void navigate(boolean requestFocus) {
    SliceUsage sliceUsage = getValue();
    sliceUsage.navigate(requestFocus);
  }

  public boolean canNavigate() {
    return getValue().canNavigate();
  }

  public boolean canNavigateToSource() {
    return getValue().canNavigateToSource();
  }

  public boolean isValid() {
    return getValue().isValid();
  }

  public boolean expandOnDoubleClick() {
    return false;
  }

  public void addLeafExpressions(@NotNull Collection<PsiElement> leafExpressions) {
    this.leafExpressions.addAll(leafExpressions);
  }

  @NotNull
  public Collection<PsiElement> getLeafExpressions() {
    return leafExpressions;
  }

  public void customizeCellRenderer(SliceUsageCellRenderer renderer, JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    renderer.setIcon(getPresentation().getIcon(expanded));
    if (isValid()) {
      SliceUsage sliceUsage = getValue();
      renderer.customizeCellRendererFor(sliceUsage);
      renderer.setToolTipText(sliceUsage.getPresentation().getTooltipText());
    }
    else {
      renderer.append(UsageViewBundle.message("node.invalid") + " ", SliceUsageCellRenderer.ourInvalidAttributes);
    }
  }

  public void setChanged() {
    //storedModificationCount = -1;
    //for (SliceNode cachedChild : myCachedChildren) {
    //  cachedChild.clearCaches();
    //}
    //myCachedChildren = null;
    //initialized = false;
    changed = true;
  }

  @Override
  public String toString() {
    return getValue()==null?"<null>":getValue().toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    SliceNode sliceNode = (SliceNode)o;

    return getValue().equals(sliceNode.getValue());
  }

  @Override
  public int hashCode() {
    return getValue().hashCode();
  }
}
