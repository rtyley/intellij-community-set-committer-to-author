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

package com.intellij.ide.util;

import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

/**
 * @author yole
 */
public class PlatformModuleRendererFactory extends ModuleRendererFactory {
  public DefaultListCellRenderer getModuleRenderer() {
    return new DefaultListCellRenderer() {
      public Component getListCellRendererComponent(final JList list,
                                                    final Object value,
                                                    final int index,
                                                    final boolean isSelected,
                                                    final boolean cellHasFocus) {
        final Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        setText("");
        setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 2));
        setHorizontalTextPosition(SwingConstants.LEFT);
        setBackground(isSelected ? UIUtil.getListSelectionBackground() : UIUtil.getListBackground());
        setForeground(isSelected ? UIUtil.getListSelectionForeground() : UIUtil.getInactiveTextColor());
        return component;
      }
    };
  }
}
