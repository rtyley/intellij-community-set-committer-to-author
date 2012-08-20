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
package com.intellij.uiDesigner.core;

import junit.framework.TestCase;

import javax.swing.*;
import java.awt.*;

public final class SpansTest extends TestCase{
  
  /**
   * button(can grow) | text field (want grow)
   *   text field (want grow, span 2) 
   */ 
  public void test1() {
    final GridLayoutManager layout = new GridLayoutManager(2,2, new Insets(0,0,0,0), 0, 0);
    final JPanel panel = new JPanel(layout);

    final JButton button = new JButton();
    button.setPreferredSize(new Dimension(50, 10));
    
    final JTextField field1 = new JTextField();
    field1.setPreferredSize(new Dimension(50, 10));

    final JTextField field2 = new JTextField();

    panel.add(button, new GridConstraints(0,0,1,1,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_HORIZONTAL,
      GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0));

    panel.add(field1, new GridConstraints(0,1,1,1,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_HORIZONTAL,
      GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0));

    panel.add(field2, new GridConstraints(1,0,1,2,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_HORIZONTAL,
      GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0));

    final Dimension preferredSize = panel.getPreferredSize();
    if (SystemInfo.isMac) {
      assertEquals(125, preferredSize.width);
    } else {
      assertEquals(100, preferredSize.width);
    }

    panel.setSize(new Dimension(500, panel.getHeight()));
    panel.doLayout();

    assertEquals(500, field2.getWidth());
    if (SystemInfo.isMac) {
      assertEquals(75, button.getWidth());
      assertEquals(425, field1.getWidth());
    } else {
      assertEquals(50, button.getWidth());
      assertEquals(450, field1.getWidth());
    }
  }


  /**
   * button(can grow) | text field (can grow)
   *   text field (want grow, span 2)
   */
  public void test2() {
    final JPanel panel = new JPanel(new GridLayoutManager(2,2, new Insets(0,0,0,0), 0, 0));

    final JButton button = new JButton();
    button.setPreferredSize(new Dimension(50, 10));

    final JTextField field1 = new JTextField();
    field1.setPreferredSize(new Dimension(50, 10));

    final JTextField field2 = new JTextField();

    panel.add(button, new GridConstraints(0,0,1,1,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_HORIZONTAL,
      GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0));

    panel.add(field1, new GridConstraints(0,1,1,1,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_HORIZONTAL,
      GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0));

    panel.add(field2, new GridConstraints(1,0,1,2,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_HORIZONTAL,
      GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0));

    final Dimension preferredSize = panel.getPreferredSize();
    if (SystemInfo.isMac) {
      assertEquals(125, preferredSize.width);
    } else {
      assertEquals(100, preferredSize.width);
    }

    panel.setSize(new Dimension(500, panel.getHeight()));
    panel.doLayout();

    assertEquals(500, field2.getWidth());
    if (SystemInfo.isMac) {
      assertEquals(263, button.getWidth());
      assertEquals(237, field1.getWidth());
    } else {
      assertEquals(250, button.getWidth());
      assertEquals(250, field1.getWidth());
    }
  }

  /**
   * button(can grow) | text field (want grow, span 2)
   */
  public void test3() {
    final JPanel panel = new JPanel(new GridLayoutManager(1,3, new Insets(0,0,0,0), 0, 0));

    final JButton button = new JButton();
    button.setPreferredSize(new Dimension(50, 10));

    final JTextField field1 = new JTextField();
    field1.setPreferredSize(new Dimension(110, 10));

    panel.add(button, new GridConstraints(0,0,1,1,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_HORIZONTAL,
      GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0));

    panel.add(field1, new GridConstraints(0,1,1,2,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_HORIZONTAL,
      GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0));

    final Dimension preferredSize = panel.getPreferredSize();
    if (SystemInfo.isMac) {
      assertEquals(185, preferredSize.width);
    } else {
      assertEquals(160, preferredSize.width);
    }

    panel.setSize(new Dimension(500, panel.getHeight()));
    panel.doLayout();

    if (SystemInfo.isMac) {
      assertEquals(75, button.getWidth());
      assertEquals(425, field1.getWidth());
    } else {
      assertEquals(50, button.getWidth());
      assertEquals(450, field1.getWidth());
    }
  }

  /**
   * button (can grow, span 2 )       | text field 1 (span 1)
   * text field 2 (want grow, span 2) | empty
   */
  public void test4() {
    final GridLayoutManager layoutManager = new GridLayoutManager(2,3, new Insets(0,0,0,0), 0, 0);
    final JPanel panel = new JPanel(layoutManager);

    final JButton button = new JButton();
    button.setPreferredSize(new Dimension(50, 10));

    final JTextField field1 = new JTextField();
    field1.setPreferredSize(new Dimension(110, 10));

    final JTextField field2 = new JTextField();
    field2.setPreferredSize(new Dimension(110, 10));

    panel.add(button, new GridConstraints(0,0,1,2,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_HORIZONTAL,
      GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0));

    panel.add(field1, new GridConstraints(0,2,1,1,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_HORIZONTAL,
      GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0));

    panel.add(field2, new GridConstraints(1,0,1,2,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_HORIZONTAL,
      GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0));

    final Dimension preferredSize = panel.getPreferredSize();

    // field will be not null after getPreferredSize()
    final DimensionInfo horizontalInfo = layoutManager.myHorizontalInfo;
    assertEquals(GridConstraints.SIZEPOLICY_WANT_GROW | GridConstraints.SIZEPOLICY_CAN_GROW, horizontalInfo.getCellSizePolicy(0));
    assertEquals(GridConstraints.SIZEPOLICY_CAN_SHRINK, horizontalInfo.getCellSizePolicy(1));
    assertEquals(GridConstraints.SIZEPOLICY_WANT_GROW, horizontalInfo.getCellSizePolicy(2));

    assertEquals(220, preferredSize.width);
    
    panel.setSize(new Dimension(500, panel.getHeight()));
    panel.doLayout();

    assertEquals(250, button.getWidth());
    assertEquals(250, field1.getWidth());
    assertEquals(250, field2.getWidth());
  }
  
}
