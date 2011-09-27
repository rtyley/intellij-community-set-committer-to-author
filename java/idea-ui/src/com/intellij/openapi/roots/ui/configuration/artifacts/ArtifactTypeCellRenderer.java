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
package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.ide.ui.ListCellRendererWrapper;
import com.intellij.packaging.artifacts.ArtifactType;

import javax.swing.*;

/**
 * @author nik
 */
public class ArtifactTypeCellRenderer extends ListCellRendererWrapper<ArtifactType> {
  public ArtifactTypeCellRenderer(final ListCellRenderer listCellRenderer) {
    super(listCellRenderer);
  }

  @Override
  public void customize(JList list, ArtifactType type, int index, boolean selected, boolean hasFocus) {
    setIcon(type.getIcon());
    setText(type.getPresentableName());
  }
}
