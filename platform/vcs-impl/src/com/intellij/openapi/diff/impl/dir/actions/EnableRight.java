/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.dir.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diff.impl.dir.DirDiffTableModel;

/**
 * @author Konstantin Bulenkov
 */
public class EnableRight extends DirDiffAction {
  protected EnableRight(DirDiffTableModel model) {
    super(model, "Show new files on right side", MOVE_LEFT);
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    return getModel().isShowNewOnTarget();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    getModel().setShowNewOnTarget(state);
  }
}
