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
package com.intellij.ide.hierarchy.method;

import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.ide.IdeBundle;

public final class ImplementMethodAction extends OverrideImplementMethodAction {
  protected final void update(final Presentation presentation, final int toImplement, final int toOverride) {
    if (toImplement > 0) {
      presentation.setEnabled(true);
      presentation.setVisible(true);
      presentation.setText(toImplement == 1 ? IdeBundle.message("action.implement.method") : IdeBundle.message("action.implement.methods"));
    }
    else {
      presentation.setEnabled(false);
      presentation.setVisible(false);
    }
  }

}
