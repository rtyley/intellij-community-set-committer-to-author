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

package com.intellij.application.options.colors;

import java.awt.*;

public interface PreviewPanel {
  void blinkSelectedHighlightType(Object selected);

  void disposeUIResources();

  class Empty implements PreviewPanel{
    public Component getPanel() {
      return null;
    }

    public void updateView() {
    }

    public void addListener(final ColorAndFontSettingsListener listener) {

    }

    public void blinkSelectedHighlightType(final Object selected) {

    }

    public void disposeUIResources() {

    }
  }

  Component getPanel();

  void updateView();

  void addListener(ColorAndFontSettingsListener listener);
}
