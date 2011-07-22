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

import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * Utility wrapper around JColorChooser. Helps to avoid memory leak through JColorChooser.ColorChooserDialog.cancelButton.
 *
 * @author max
 */
public class ColorChooser {
  private ColorChooser() {}

  public static Color chooseColor(Component parent, String caption, @Nullable Color preselectedColor) {
    return ColorPicker.showDialog(parent, caption, preselectedColor, false);
  }

  public static Color chooseColor(Component parent, String caption, @Nullable Color preselectedColor, boolean enableOpacity) {
    return ColorPicker.showDialog(parent, caption, preselectedColor, enableOpacity);
  }

}
