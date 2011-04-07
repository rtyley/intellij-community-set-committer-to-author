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

import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.diff.impl.dir.DirDiffIcons;
import com.intellij.openapi.diff.impl.dir.DirDiffTableModel;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public abstract class DirDiffAction extends ToggleAction implements DirDiffIcons {
  private final DirDiffTableModel myModel;

  protected DirDiffAction(DirDiffTableModel model, String name, Icon icon) {
    super(name, name, icon);
    myModel = model;
  }

  public DirDiffTableModel getModel() {
    return myModel;
  }
}
