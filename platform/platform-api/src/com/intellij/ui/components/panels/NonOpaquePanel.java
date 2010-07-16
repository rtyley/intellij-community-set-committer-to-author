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
package com.intellij.ui.components.panels;

import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

public class NonOpaquePanel extends Wrapper {

  private static final Color TRANSPARENT = new Color(0, 0, 0, 0);

  public NonOpaquePanel() {
    setOpaque(false);
  }

  public NonOpaquePanel(JComponent wrapped) {
    super(wrapped);
    setOpaque(false);
  }

  public NonOpaquePanel(LayoutManager layout, JComponent wrapped) {
    super(layout, wrapped);
    setOpaque(false);
  }

  public NonOpaquePanel(boolean isDoubleBuffered) {
    super(isDoubleBuffered);
    setOpaque(false);
  }

  public NonOpaquePanel(LayoutManager layout) {
    super(layout);
    setOpaque(false);
  }

  public NonOpaquePanel(LayoutManager layout, boolean isDoubleBuffered) {
    super(layout, isDoubleBuffered);
    setOpaque(false);
  }

  @Override
  public void setOpaque(boolean isOpaque) {
    super.setOpaque(isOpaque);

    if (!isOpaque && UIUtil.isUnderNimbusLookAndFeel()) {
      if (UIUtil.isUnderNimbusLookAndFeel()) {
        setBackground(TRANSPARENT);
      }
    }
  }

  public static void setTransparent(JComponent c) {
    c.setOpaque(false);
    if (UIUtil.isUnderNimbusLookAndFeel()) {
      c.setBackground(TRANSPARENT);
    }
  }
}
