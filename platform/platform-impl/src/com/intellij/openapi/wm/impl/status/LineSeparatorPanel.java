/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.wm.impl.status;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.impl.BulkVirtualFileListenerAdapter;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.ui.ClickListener;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.LineSeparator;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * @author Denis Zhdanov
 * @since 3/17/13 11:56 AM
 */
public class LineSeparatorPanel extends EditorBasedWidget implements StatusBarWidget.Multiframe, CustomStatusBarWidget {

  @NotNull private final TextPanel myComponent;

  private boolean myActionEnabled;

  public LineSeparatorPanel(@NotNull final Project project) {
    super(project);

    myComponent = new TextPanel(LineSeparator.CRLF.toString()) {
      @Override
      protected void paintComponent(@NotNull final Graphics g) {
        super.paintComponent(g);
        if (myActionEnabled && getText() != null) {
          final Rectangle r = getBounds();
          final Insets insets = getInsets();
          AllIcons.Ide.Statusbar_arrows.paintIcon(this, g, r.width - insets.right - AllIcons.Ide.Statusbar_arrows.getIconWidth() - 2,
                                                  r.height / 2 - AllIcons.Ide.Statusbar_arrows.getIconHeight() / 2);
        }
      }
    };

    new ClickListener() {
      @Override
      public boolean onClick(MouseEvent e, int clickCount) {
        update();
        showPopup(e);
        return true;
      }
    }.installOn(myComponent);
    myComponent.setBorder(WidgetBorder.INSTANCE);
  }

  private void update() {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        String lineSeparator = null;
        VirtualFile file = getSelectedFile();
        if (file == null || !file.isWritable()) {
          myActionEnabled = false;
        }
        else {
          lineSeparator = LoadTextUtil.detectLineSeparator(file, true);
        }

        myComponent.resetColor();
        if (lineSeparator == null) {
          myComponent.setText("");
        }
        else {
          myActionEnabled = true;
          myComponent.setToolTipText(String.format("Line separator: %s%nClick to change", StringUtil.escapeLineBreak(lineSeparator)));
          myComponent.setText(LineSeparator.fromString(lineSeparator).toString());
        }

        if (myActionEnabled) {
          myComponent.setForeground(UIUtil.getActiveTextColor());
        }
        else {
          myComponent.setForeground(UIUtil.getInactiveTextColor());
        }

        if (myStatusBar != null) {
          myStatusBar.updateWidget(ID());
        }
      }
    });
  }

  private void showPopup(MouseEvent e) {
    if (!myActionEnabled) {
      return;
    }
    DataContext dataContext = getContext();
    AnAction group = ActionManager.getInstance().getAction("ChangeLineSeparators");
    if (!(group instanceof ActionGroup)) {
      return;
    }
    
    ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(
      "Line separator",
      (ActionGroup)group,
      dataContext,
      JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
      false
    );
    Dimension dimension = popup.getContent().getPreferredSize();
    Point at = new Point(0, -dimension.height);
    popup.show(new RelativePoint(e.getComponent(), at));
    Disposer.register(this, popup); // destroy popup on unexpected project close
  }

  @Override
  public void install(@NotNull StatusBar statusBar) {
    super.install(statusBar);
    ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(
      VirtualFileManager.VFS_CHANGES, new BulkVirtualFileListenerAdapter(new VirtualFileAdapter() {
      @Override
      public void contentsChanged(VirtualFileEvent event) {
        update();
      }
    }));
  }

  @NotNull
  private DataContext getContext() {
    Editor editor = getEditor();
    DataContext parent = DataManager.getInstance().getDataContext((Component)myStatusBar);
    return SimpleDataContext.getSimpleContext(
      PlatformDataKeys.VIRTUAL_FILE_ARRAY.getName(),
      new VirtualFile[] {getSelectedFile()},
      SimpleDataContext.getSimpleContext(PlatformDataKeys.PROJECT.getName(),
                                         getProject(),
                                         SimpleDataContext.getSimpleContext(PlatformDataKeys.CONTEXT_COMPONENT.getName(),
                                                                            editor == null ? null : editor.getComponent(), parent)
      ));
  }
  
  @Override
  public JComponent getComponent() {
    return myComponent;
  }

  @Override
  public StatusBarWidget copy() {
    return new LineSeparatorPanel(getProject());
  }

  @NotNull
  @Override
  public String ID() {
    return "LineSeparator";
  }

  @Nullable
  @Override
  public WidgetPresentation getPresentation(@NotNull PlatformType type) {
    return null;
  }

  @Override
  public void selectionChanged(FileEditorManagerEvent event) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    update();
  }

  @Override
  public void fileOpened(FileEditorManager source, VirtualFile file) {
    update();
  }
}
