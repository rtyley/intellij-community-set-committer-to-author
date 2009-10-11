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
package com.intellij.ide.actions;

import com.intellij.ide.TreeExpander;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 19, 2004
 * Time: 7:38:56 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class TreeCollapseAllActionBase extends AnAction implements DumbAware {
  public void actionPerformed(AnActionEvent e) {
    TreeExpander expander = getExpander(e.getDataContext());
    if (expander == null) return;
    if (!expander.canCollapse()) return;
    expander.collapseAll();
  }

  protected abstract TreeExpander getExpander(DataContext dataContext);

  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    TreeExpander expander = getExpander(event.getDataContext());
    presentation.setEnabled(expander != null && expander.canCollapse());
  }
}
