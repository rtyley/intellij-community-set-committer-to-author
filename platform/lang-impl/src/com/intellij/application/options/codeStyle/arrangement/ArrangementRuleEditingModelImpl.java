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
package com.intellij.application.options.codeStyle.arrangement;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.codeStyle.arrangement.ArrangementUtil;
import com.intellij.psi.codeStyle.arrangement.model.*;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementSettingsGrouper;
import com.intellij.util.containers.hash.HashSet;
import gnu.trove.TIntIntHashMap;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultTreeModel;
import java.util.Set;

/**
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/15/12 2:40 PM
 */
public class ArrangementRuleEditingModelImpl implements ArrangementRuleEditingModel {

  private static final TIntIntHashMap EMPTY_CHANGES = new TIntIntHashMap();

  @NotNull private static final MyConditionsBuilder CONDITIONS_BUILDER = new MyConditionsBuilder();

  @NotNull private final Set<Listener> myListeners  = new HashSet<Listener>();
  @NotNull private final Set<Object>   myConditions = new HashSet<Object>();

  @NotNull private final DefaultTreeModel           myTreeModel;
  @NotNull private final ArrangementSettingsGrouper myGrouper;
  private final          boolean                    myRootVisible;

  @NotNull private ArrangementTreeNode       myTopMost;
  @NotNull private ArrangementTreeNode       myBottomMost;
  @NotNull private ArrangementMatchCondition myMatchCondition;
  private          int                       myRow;

  /**
   * Creates new <code>ArrangementRuleEditingModelImpl</code> object.
   *
   * @param model        tree model which holds target ui nodes. Basically, we need to perform ui nodes modification via it in order
   *                     to generate corresponding events automatically
   * @param node         backing settings node
   * @param topMost      there is a possible case that a single settings node is shown in more than one visual line
   *                     ({@link HierarchicalArrangementSettingsNode}). This argument is the top-most UI node used for the
   *                     settings node representation
   * @param bottomMost   bottom-most UI node used for the given settings node representation 
   * @param grouper      strategy that encapsulates information on how settings node should be displayed
   * @param row          row number for which current model is registered at the given model mappings
   * @param rootVisible  determines if the root should be count during rows calculations
   */
  public ArrangementRuleEditingModelImpl(@NotNull DefaultTreeModel model,
                                         @NotNull ArrangementMatchCondition node,
                                         @NotNull ArrangementTreeNode topMost,
                                         @NotNull ArrangementTreeNode bottomMost,
                                         @NotNull ArrangementSettingsGrouper grouper,
                                         int row,
                                         boolean rootVisible)
  {
    myTreeModel = model;
    myMatchCondition = node;
    myTopMost = topMost;
    myBottomMost = bottomMost;
    myGrouper = grouper;
    myRow = row;
    myRootVisible = rootVisible;
    refreshConditions();
  }

  private void refreshConditions() {
    myConditions.clear();
    CONDITIONS_BUILDER.conditions = myConditions;
    try {
      myMatchCondition.invite(CONDITIONS_BUILDER);
    }
    finally {
      CONDITIONS_BUILDER.conditions = null;
    }
  }

  @NotNull
  @Override
  public ArrangementMatchCondition getMatchCondition() {
    return myMatchCondition;
  }

  public int getRow() {
    return myRow;
  }

  @NotNull
  public ArrangementTreeNode getTopMost() {
    return myTopMost;
  }

  @NotNull
  public ArrangementTreeNode getBottomMost() {
    return myBottomMost;
  }

  @Override
  public boolean hasCondition(@NotNull Object key) {
    return myConditions.contains(key);
  }

  /**
   * There is a possible case that tree nodes referenced by the current model become out of date due to a tree modification.
   * <p/>
   * This method asks the model to refresh its tree nodes if necessary.
   */
  public void refreshTreeNodes() {
    for (ArrangementTreeNode node = myBottomMost; node != null; node = node.getParent()) {
      if (node == myTopMost) {
        // No refresh is necessary.
        return;
      }
      ArrangementMatchCondition matchCondition = myTopMost.getBackingSetting();
      if (matchCondition != null && matchCondition.equals(node.getBackingSetting())) {
        myTopMost = node;
        return;
      }
    }
    assert false;
  }
  
  @Override
  public void addAndCondition(@NotNull ArrangementAtomMatchCondition setting) {
    TIntIntHashMap rowChanges = doAddAndCondition(setting);
    refreshConditions();
    notifyListeners(rowChanges);
  }
  
  @NotNull
  private TIntIntHashMap doAddAndCondition(@NotNull ArrangementAtomMatchCondition setting) {
    ArrangementMatchCondition newNode = ArrangementUtil.and(myMatchCondition.clone(), setting);
    return applyNewSetting(newNode);
  }

  @Override
  public void removeAndCondition(@NotNull ArrangementMatchCondition node) {
    TIntIntHashMap rowChanges = doRemoveAndCondition(node);
    refreshConditions();
    notifyListeners(rowChanges);
  }
  
  @NotNull
  private TIntIntHashMap doRemoveAndCondition(@NotNull ArrangementMatchCondition node) {
    if (!(myMatchCondition instanceof ArrangementCompositeMatchCondition)) {
      return EMPTY_CHANGES;
    }

    ArrangementMatchCondition newNode = myMatchCondition.clone();
    ArrangementCompositeMatchCondition composite = (ArrangementCompositeMatchCondition)newNode;
    composite.getOperands().remove(node);
    if (composite.getOperands().size() == 1) {
      newNode = composite.getOperands().iterator().next();
    }

    return applyNewSetting(newNode);
  }

  @NotNull
  private TIntIntHashMap applyNewSetting(@NotNull ArrangementMatchCondition newNode) {
    myMatchCondition = newNode;
    HierarchicalArrangementSettingsNode grouped = myGrouper.group(newNode);
    int newDepth = ArrangementConfigUtil.getDepth(grouped);
    int oldDepth = ArrangementConfigUtil.distance(myTopMost, myBottomMost);
    if (oldDepth == newDepth) {
      myBottomMost.setSettings(ArrangementConfigUtil.getLast(grouped).getCurrent());
      return EMPTY_CHANGES;
    }

    Pair<ArrangementTreeNode, Integer> replacement = ArrangementConfigUtil.map(null, grouped, null);
    ArrangementTreeNode newBottom = replacement.first;
    ArrangementTreeNode newTop = ArrangementConfigUtil.getRoot(newBottom);
    final TIntIntHashMap rowChanges = ArrangementConfigUtil.replace(myTopMost, myBottomMost, newTop, myTreeModel, myRootVisible);
    myTopMost = newTop;
    myBottomMost = newBottom;
    rowChanges.remove(myRow);
    int newRow = ArrangementConfigUtil.getRow(myBottomMost, myRootVisible);
    rowChanges.put(myRow, newRow);
    myRow = newRow;
    return rowChanges;
  }

  public void addListener(@NotNull Listener listener) {
    myListeners.add(listener);
  }

  private void notifyListeners(@NotNull TIntIntHashMap rowChanges) {
    for (Listener listener : myListeners) {
      listener.onChanged(this, rowChanges);
    }
  }

  @Override
  public String toString() {
    return "model for " + myMatchCondition;
  }

  private static class MyConditionsBuilder implements ArrangementSettingsNodeVisitor {

    @NotNull Set<Object> conditions;

    @Override
    public void visit(@NotNull ArrangementAtomMatchCondition setting) {
      conditions.add(setting.getValue()); 
    }

    @Override
    public void visit(@NotNull ArrangementCompositeMatchCondition setting) {
      for (ArrangementMatchCondition operand : setting.getOperands()) {
        operand.invite(this);
      } 
    }
  }

  public interface Listener {
    void onChanged(@NotNull ArrangementRuleEditingModelImpl model, @NotNull TIntIntHashMap rowChanges);
  }
}
