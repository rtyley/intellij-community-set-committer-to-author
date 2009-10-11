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
package com.intellij.ui;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;

public final class TreeToolTipHandler extends AbstractToolTipHandler<Integer, JTree>{
  protected TreeToolTipHandler(JTree tree) {
    super(tree);
    tree.getSelectionModel().addTreeSelectionListener(
      new TreeSelectionListener() {
        public void valueChanged(TreeSelectionEvent e) {
          try {
            repaintHint();
          }
          catch (Exception e1) {
            // Workaround for some race conditions in Swing, see
            // http://www.intellij.net/tracker/idea/viewSCR?publicId=53961
          }
        }
      }
    );
  }

  public static void install(JTree tree) {
    new TreeToolTipHandler(tree);
  }

  protected Integer getCellKeyForPoint(Point point) {
    int rowIndex = myComponent.getRowForLocation(point.x, point.y);
    return rowIndex != -1 ? new Integer(rowIndex) : null;
  }

  protected Rectangle getCellBounds(Integer key, Component rendererComponent) {
    int rowIndex = key.intValue();
    TreePath path = myComponent.getPathForRow(rowIndex);
    Rectangle cellBounds = myComponent.getPathBounds(path);
    return cellBounds;
  }

  protected Component getRendererComponent(Integer key) {
    int rowIndex = key.intValue();
    Component rComponent;
    TreeCellRenderer renderer = myComponent.getCellRenderer();
    if (renderer == null) {
      rComponent = null;
    } else {
      TreePath path = myComponent.getPathForRow(rowIndex);
      if (path == null) {
        rComponent = null;
      } else {
      Object node = path.getLastPathComponent();
        rComponent = renderer.getTreeCellRendererComponent(
            myComponent,
            node,
            myComponent.isRowSelected(rowIndex),
            myComponent.isExpanded(rowIndex),
            myComponent.getModel().isLeaf(node),
            rowIndex,
            myComponent.hasFocus()
        );
      }
    }
    return rComponent;
  }
}