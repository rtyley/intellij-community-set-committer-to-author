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

import com.intellij.psi.codeStyle.arrangement.JavaRearranger;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryType;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementModifier;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingType;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingsAtomNode;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingsNode;
import com.intellij.ui.treeStructure.Tree;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.util.Arrays;

import static org.junit.Assert.assertTrue;

/**
 * @author Denis Zhdanov
 * @since 8/16/12 11:04 AM
 */
public abstract class AbstractArrangementRuleEditingModelTest {

  @NotNull protected ArrangementRuleEditingModelBuilder                 myBuilder;
  @NotNull protected JTree                                              myTree;
  @NotNull protected DefaultMutableTreeNode                             myRoot;
  @NotNull protected TIntObjectHashMap<ArrangementRuleEditingModelImpl> myRowMappings;
  @NotNull protected JavaRearranger                                     myGrouper;

  @Before
  public void setUp() {
    myBuilder = new ArrangementRuleEditingModelBuilder();
    myRoot = new DefaultMutableTreeNode();
    myTree = new Tree(myRoot);
    myTree.expandPath(new TreePath(myRoot));
    myRowMappings = new TIntObjectHashMap<ArrangementRuleEditingModelImpl>();
    myGrouper = new JavaRearranger();
  }

  protected void configure(@NotNull ArrangementSettingsNode settingsNode) {
    myBuilder.build(settingsNode, myTree, myRoot, myGrouper, myRowMappings);
  }

  protected static ArrangementSettingsAtomNode atom(@NotNull Object condition) {
    final ArrangementSettingType type;
    if (condition instanceof ArrangementEntryType) {
      type = ArrangementSettingType.TYPE;
    }
    else if (condition instanceof ArrangementModifier) {
      type = ArrangementSettingType.MODIFIER;
    }
    else {
      throw new IllegalArgumentException(String.format("Unexpected condition of class %s: %s", condition.getClass(), condition));
    }
    return new ArrangementSettingsAtomNode(type, condition);
  }

  protected void checkRows(int... rows) {
    for (int row : rows) {
      assertTrue(
        String.format("Expected to find mappings for rows %s. Actual: %s", Arrays.toString(rows), Arrays.toString(myRowMappings.keys())),
        myRowMappings.containsKey(row)
      );
    }
  }
}
