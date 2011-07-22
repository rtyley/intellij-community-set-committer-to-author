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

import com.intellij.ui.border.IdeaTitledBorder;
import com.intellij.util.ui.UIUtil;

import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;

public class IdeBorderFactory {
  public static final int BORDER_ROUNDNESS = 5;

  private IdeBorderFactory() {
  }

  public static Border createBorder() {
    return createBorder(SideBorder.ALL);
  }

  public static Border createBorder(int borders) {
    return new SideBorder(getBorderColor(), borders);
  }

  public static Border createRoundedBorder() {
    return new RoundedLineBorder(getBorderColor(), BORDER_ROUNDNESS);
  }

  public static Border createEmptyBorder(Insets insets) {
    return new EmptyBorder(insets);
  }

  public static Border createEmptyBorder(int thickness) {
    return new EmptyBorder(thickness, thickness, thickness, thickness);
  }

  public static Border createEmptyBorder(int top, int left, int bottom, int right) {
    return new EmptyBorder(top, left, bottom, right);
  }

  public static TitledBorder createTitledBorderBoldWithIndent(String title) {
    return new IdeaTitledBorder(title, UIUtil.getBorderFont().deriveFont(Font.BOLD), UIUtil.getBorderColor(), 12, 1, 15);
  }

  public static TitledBorder createTitledBorderBoldWithoutIndent(String title) {
    return new IdeaTitledBorder(title, UIUtil.getBorderFont().deriveFont(Font.BOLD), UIUtil.getBorderColor(), 0, 1, 15);
  }

  public static TitledBorder createTitledBorderSimpleWithIndent(String title) {
    return new IdeaTitledBorder(title, UIUtil.getBorderFont(), UIUtil.getBorderColor(), 10, 1, 7);
  }

  public static TitledBorder createTitledBorderSimpleWithoutIndent(String title) {
    return new IdeaTitledBorder(title, UIUtil.getBorderFont(), UIUtil.getBorderColor(), 0, 1, 7);
  }

  @Deprecated
  // Don't remove, used in TeamCity plugin.
  public static TitledBorder createTitledHeaderBorder(String title) {
    return new IdeaTitledBorder(title, UIUtil.getBorderFont().deriveFont(Font.BOLD), UIUtil.getBorderColor(), 10, 1, 15);
  }

  private static Color getBorderColor() {
    return UIUtil.getBorderColor();
  }
}
