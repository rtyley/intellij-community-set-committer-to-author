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
package com.intellij.xdebugger.impl.actions.handlers;

import org.jetbrains.annotations.NotNull;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.stepping.XSmartStepIntoHandler;
import com.intellij.xdebugger.stepping.XSmartStepIntoVariant;
import com.intellij.xdebugger.impl.actions.XDebuggerSuspendedActionHandler;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.ui.awt.RelativePoint;

import javax.swing.*;
import java.util.List;

/**
 * @author nik
 */
public class XDebuggerSmartStepIntoHandler extends XDebuggerSuspendedActionHandler {

  protected boolean isEnabled(@NotNull XDebugSession session, DataContext dataContext) {
    return super.isEnabled(session, dataContext) && session.getDebugProcess().getSmartStepIntoHandler() != null;
  }

  protected void perform(@NotNull XDebugSession session, DataContext dataContext) {
    final XSmartStepIntoHandler<?> handler = session.getDebugProcess().getSmartStepIntoHandler();
    final XSourcePosition position = session.getCurrentPosition();
    if (position == null || handler == null) return;

    final FileEditor editor = FileEditorManager.getInstance(session.getProject()).getSelectedEditor(position.getFile());
    if (!(editor instanceof TextEditor)) return;

    final RelativePoint relativePoint = DebuggerUIUtil.calcPopupLocation(((TextEditor)editor).getEditor(), position.getLine());
    doSmartStepInto(handler, position, session, relativePoint);
  }

  private static <V extends XSmartStepIntoVariant> void doSmartStepInto(final XSmartStepIntoHandler<V> handler,
                                                             XSourcePosition position, final XDebugSession session, RelativePoint relativePoint) {
    final List<V> variants = handler.computeSmartStepVariants(position);
    if (variants.isEmpty()) {
      session.stepInto();
      return;
    }
    else if (variants.size() == 1) {
      session.smartStepInto(handler, variants.get(0));
      return;
    }

    JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<V>(handler.getPopupTitle(position), variants) {
      @Override
      public Icon getIconFor(V aValue) {
        return aValue.getIcon();
      }

      @NotNull
      @Override
      public String getTextFor(V value) {
        return value.getText();
      }

      @Override
      public PopupStep onChosen(V selectedValue, boolean finalChoice) {
        session.smartStepInto(handler, selectedValue);
        return FINAL_CHOICE;
      }
    }).show(relativePoint);
  }
}
