/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.service.task;

import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Denis Zhdanov
 * @since 5/12/13 10:18 PM
 */
public class ExternalSystemTasksPanel extends JPanel implements DataProvider {

  @NotNull ExternalSystemTasksTreeModel myAllTasksModel = new ExternalSystemTasksTreeModel();
  @NotNull Tree                         myAllTasksTree  = new Tree(myAllTasksModel);

  public ExternalSystemTasksPanel() {
    super(new GridBagLayout());

    add(new JBScrollPane(myAllTasksTree), ExternalSystemUiUtil.getFillLineConstraints(0));
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    if (ExternalSystemDataKeys.ALL_TASKS_MODEL.is(dataId)) {
      return myAllTasksModel;
    }
    return null;
  }
}
