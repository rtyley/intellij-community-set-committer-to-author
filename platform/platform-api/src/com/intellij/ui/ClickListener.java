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

/*
 * @author max
 */
package com.intellij.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public abstract class ClickListener {

  private static final int EPS = 4;
  private static final long TIME_EPS = 500; // TODO: read system mouse sensitivity settings?

  public abstract void onClick(MouseEvent event, int clickCount);

  public void installOn(final JComponent c) {
    MouseAdapter adapter = new MouseAdapter() {
      private Point clickPoint;
      private long lastTimeClicked = -1;
      private int clickCount = 0;

      @Override
      public void mousePressed(MouseEvent e) {
        if (Math.abs(lastTimeClicked - e.getWhen()) > TIME_EPS) {
          clickCount = 0;
        }
        clickCount++;
        lastTimeClicked = e.getWhen();

        if (!e.isPopupTrigger()) {
          clickPoint = e.getPoint();
        }
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        Point releasedAt = e.getPoint();
        Point clickedAt = clickPoint;
        clickPoint = null;

        if (clickedAt == null) return;
        if (e.isPopupTrigger()) return;
        if (releasedAt.x < 0 || releasedAt.y < 0 || releasedAt.x >= c.getWidth() || releasedAt.y >= c.getWidth()) return;

        if (Math.abs(clickedAt.x - releasedAt.x) < EPS && Math.abs(clickedAt.y - releasedAt.y) < EPS) {
          onClick(e, clickCount);
        }
      }
    };

    c.addMouseListener(adapter);
    c.addMouseMotionListener(adapter);
  }
}
