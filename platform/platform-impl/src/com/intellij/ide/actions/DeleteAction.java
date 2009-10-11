
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
package com.intellij.ide.actions;

import com.intellij.ide.DeleteProvider;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.event.KeyEvent;

public class DeleteAction extends AnAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.actions.DeleteAction");

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    DeleteProvider provider = getDeleteProvider(dataContext);
    if (provider == null) return;
    try {
      provider.deleteElement(dataContext);
    }
    catch (Throwable t) {
      if (t instanceof StackOverflowError){
        t.printStackTrace();
      }
      LOG.error(t);
    }
  }

  @Nullable
  protected DeleteProvider getDeleteProvider(DataContext dataContext) {
    return PlatformDataKeys.DELETE_ELEMENT_PROVIDER.getData(dataContext);
  }

  public void update(AnActionEvent event){
    String place = event.getPlace();
    Presentation presentation = event.getPresentation();
    if (ActionPlaces.PROJECT_VIEW_POPUP.equals(place) || ActionPlaces.COMMANDER_POPUP.equals(place))
      presentation.setText(IdeBundle.message("action.delete.ellipsis"));
    else
      presentation.setText(IdeBundle.message("action.delete"));
    DataContext dataContext = event.getDataContext();
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    DeleteProvider provider = getDeleteProvider(dataContext);
    if (event.getInputEvent() instanceof KeyEvent) {
      Object component = PlatformDataKeys.CONTEXT_COMPONENT.getData(dataContext);
      if (component instanceof JTextComponent) provider = null; // Do not override text deletion
    }
    final boolean canDelete = provider != null && provider.canDeleteElement(dataContext);
    if (ActionPlaces.isPopupPlace(event.getPlace())) {
      presentation.setVisible(canDelete);
    }
    else {
      presentation.setEnabled(canDelete);
    }
  }

  public DeleteAction(String text, String description, Icon icon) {
    super(text, description, icon);
  }

  public DeleteAction() {
  }
}
