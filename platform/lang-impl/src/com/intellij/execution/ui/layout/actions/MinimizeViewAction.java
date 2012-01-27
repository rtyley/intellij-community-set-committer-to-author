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

package com.intellij.execution.ui.layout.actions;

import com.intellij.execution.ui.layout.Tab;
import com.intellij.execution.ui.layout.ViewContext;
import com.intellij.execution.ui.actions.BaseViewAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.content.Content;

import javax.swing.*;

public class MinimizeViewAction extends BaseViewAction {
  private static final Icon ICON = IconLoader.getIcon("/actions/move-to-button.png");
  private static final Icon ICON_TOP = IconLoader.getIcon("/actions/move-to-button-top.png");

  protected void update(final AnActionEvent e, final ViewContext context, final Content[] content) {
    setEnabled(e, isEnabled(context, content, e.getPlace()));
    e.getPresentation().setIcon(ViewContext.TAB_TOOLBAR_PLACE.equals(e.getPlace()) ? ICON_TOP : ICON);
  }

  protected void actionPerformed(final AnActionEvent e, final ViewContext context, final Content[] content) {
    for (Content each : content) {
      context.findCellFor(each).minimize(each);
    }
  }

  public static boolean isEnabled(ViewContext context, Content[] content, String place) {
    if (!context.isMinimizeActionEnabled() || content.length == 0) {
      return false;
    }

    if (ViewContext.TAB_TOOLBAR_PLACE.equals(place) || ViewContext.TAB_POPUP_PLACE.equals(place)) {
      Tab tab = getTabFor(context, content);
      if (tab == null) {
        return false;
      }
      return !tab.isDefault();
    }
    else {
      return getTabFor(context, content) != null;
    }
  }
}
