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
package com.intellij.ide.navigationToolbar.ui;

import com.intellij.ide.navigationToolbar.NavBarItem;
import com.intellij.util.ui.UIUtil;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaNavBarUI extends CommonNavBarUI {
  @Override
  public Font getElementFont(NavBarItem navBarItem) {
    return UIUtil.getLabelFont().deriveFont(11.0f);
  }

  @Override
  public void doPaintNavBarPanel(Graphics2D g, Rectangle r, boolean mainToolbarVisible, boolean undocked) {
  }
}
