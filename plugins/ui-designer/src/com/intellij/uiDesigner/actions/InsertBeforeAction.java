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

package com.intellij.uiDesigner.actions;

import com.intellij.uiDesigner.CaptionSelection;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.radComponents.RadContainer;

/**
 * @author yole
*/
public final class InsertBeforeAction extends RowColumnAction {
  public InsertBeforeAction() {
    super(UIDesignerBundle.message("action.insert.column.before.this"), "/com/intellij/uiDesigner/icons/insertColumnLeft.png",
          UIDesignerBundle.message("action.insert.row.before.this"), "/com/intellij/uiDesigner/icons/insertRowAbove.png");
  }

  protected void actionPerformed(CaptionSelection selection) {
    RadContainer container = selection.getContainer();
    container.getGridLayoutManager().insertGridCells(container, selection.getFocusedIndex(), selection.isRow(), true, false);
  }
}
