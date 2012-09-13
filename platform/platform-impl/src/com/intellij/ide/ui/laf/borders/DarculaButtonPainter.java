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
package com.intellij.ide.ui.laf.borders;

import com.intellij.ide.ui.laf.DarculaUIUtil;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.ui.Gray;
import com.intellij.util.ui.JBInsets;

import javax.swing.border.Border;
import javax.swing.plaf.UIResource;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaButtonPainter implements Border, UIResource {
  private static final JBInsets myInsets = new JBInsets(6, 12, 6, 12);
  private static final int myOffset = 4;

  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    final Graphics2D g2d = (Graphics2D)g;
    final int yOff = getBorderInsets(c).height() / 4;
    int offset = getOffset();
    if (c.hasFocus()) {
      DarculaUIUtil.paintFocusRing(g2d, offset, yOff, width - 2 * offset, height - 2 * yOff);
    } else {
      final GraphicsConfig config = new GraphicsConfig(g);
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_DEFAULT);
      g2d.setPaint(new GradientPaint(width / 2, y + yOff + 1, Gray._120.withAlpha(90), width / 2, height - 2*yOff, Gray._105.withAlpha(90)));
      g.drawRoundRect(x + offset + 1, y + yOff + 1, width - 2 * offset, height - 2*yOff, 5, 5);

      ((Graphics2D)g).setPaint(Gray._40.withAlpha(180));
      g.drawRoundRect(x + offset, y + yOff, width - 2 * offset, height - 2*yOff, 5, 5);

      config.restore();
    }
  }

  @Override
  public JBInsets getBorderInsets(Component c) {
    return myInsets;
  }

  protected int getOffset() {
    return myOffset;
  }

  @Override
  public boolean isBorderOpaque() {
    return false;
  }
}
