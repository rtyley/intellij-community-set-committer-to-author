/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.notification;

import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.impl.ConsoleViewUtil;
import com.intellij.execution.impl.EditorCopyAction;
import com.intellij.execution.impl.EditorHyperlinkSupport;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.notification.impl.NotificationsManagerImpl;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.EditorPopupHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.text.DateFormat;

/**
 * @author peter
 */
class EventLogConsole {
  private final Editor myLogEditor;
  private final EditorHyperlinkSupport myHyperlinkSupport;
  private final LogModel myProjectModel;

  EventLogConsole(@NotNull Project project, LogModel model) {
    myProjectModel = model;
    myLogEditor = ConsoleViewUtil.setupConsoleEditor(project, false, false);

    ((EditorMarkupModel) myLogEditor.getMarkupModel()).setErrorStripeVisible(true);


    myLogEditor.addEditorMouseListener(new EditorPopupHandler() {
      public void invokePopup(final EditorMouseEvent event) {
        final ActionManager actionManager = ActionManager.getInstance();
        final ActionPopupMenu menu = actionManager.createActionPopupMenu(ActionPlaces.UNKNOWN, createPopupActions(actionManager));
        final MouseEvent mouseEvent = event.getMouseEvent();
        menu.getComponent().show(mouseEvent.getComponent(), mouseEvent.getX(), mouseEvent.getY());
      }
    });
    myHyperlinkSupport = new EditorHyperlinkSupport(myLogEditor, project);
  }

  void releaseEditor() {
    EditorFactory.getInstance().releaseEditor(myLogEditor);
  }

  private DefaultActionGroup createPopupActions(ActionManager actionManager) {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new DumbAwareAction("Clear All") {
      @Override
      public void update(AnActionEvent e) {
        final boolean enabled = e.getData(PlatformDataKeys.EDITOR) != null;
        e.getPresentation().setEnabled(enabled);
        e.getPresentation().setVisible(enabled);
      }

      public void actionPerformed(final AnActionEvent e) {
        final Editor editor = e.getData(PlatformDataKeys.EDITOR);
        if (editor != null) {
          editor.getDocument().deleteString(0, editor.getDocument().getTextLength());
        }
        for (Notification notification : myProjectModel.getNotifications()) {
          myProjectModel.removeNotification(notification);
        }
        myProjectModel.setStatusMessage(null);
      }
    });
    group.add(new EditorCopyAction());
    group.addSeparator();
    group.add(actionManager.getAction(IdeActions.ACTION_COMPARE_CLIPBOARD_WITH_SELECTION));
    return group;
  }

  void doPrintNotification(final Notification notification) {
    if (myLogEditor.isDisposed()) {
      return;
    }

    Document document = myLogEditor.getDocument();
    boolean scroll = document.getTextLength() == myLogEditor.getCaretModel().getOffset();

    append(document, DateFormat.getTimeInstance(DateFormat.MEDIUM).format(notification.getCreationTime()) + ". ");

    EventLog.LogEntry pair = EventLog.formatForLog(notification);

    final NotificationType type = notification.getType();
    TextAttributesKey key = type == NotificationType.ERROR
                                         ? ConsoleViewContentType.LOG_ERROR_OUTPUT_KEY
                                         : type == NotificationType.INFORMATION
                                           ? ConsoleViewContentType.NORMAL_OUTPUT_KEY
                                           : ConsoleViewContentType.LOG_WARNING_OUTPUT_KEY;

    int msgStart = document.getTextLength();
    String message = pair.message;
    append(document, message);

    TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(key);
    int layer = HighlighterLayer.CARET_ROW + 1;
    myLogEditor.getMarkupModel().addRangeHighlighter(msgStart, document.getTextLength(), layer, attributes, HighlighterTargetArea.EXACT_RANGE);

    for (Pair<TextRange, HyperlinkInfo> link : pair.links) {
      myHyperlinkSupport.addHyperlink(link.first.getStartOffset() + msgStart, link.first.getEndOffset() + msgStart, null, link.second);
    }

    append(document, "\n");

    if (scroll) {
      myLogEditor.getCaretModel().moveToOffset(document.getTextLength());
      myLogEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    }

    if (notification.isImportant()) {
      highlightNotification(notification, pair.status, document.getLineCount() - 2);
    }
  }

  private void highlightNotification(final Notification notification,
                                     String message, final int line) {
    TextAttributes attr = new TextAttributes(null, null, null, null, Font.BOLD);
    final RangeHighlighter lineHighlighter = myLogEditor.getMarkupModel().addLineHighlighter(line, HighlighterLayer.CARET_ROW + 1, attr);
    Color color = notification.getType() == NotificationType.ERROR
                  ? Color.red
                  : notification.getType() == NotificationType.WARNING ? Color.yellow : Color.green;
    lineHighlighter.setErrorStripeMarkColor(color);
    lineHighlighter.setErrorStripeTooltip(message);

    myProjectModel.removeHandlers.put(notification, new Runnable() {
      @Override
      public void run() {
        myLogEditor.getMarkupModel().removeHighlighter(lineHighlighter);
      }
    });
  }

  public Editor getConsoleEditor() {
    return myLogEditor;
  }

  @Nullable
  public RelativePoint getHyperlinkLocation(HyperlinkInfo info) {
    Project project = myLogEditor.getProject();
    RangeHighlighter range = myHyperlinkSupport.findHyperlinkRange(info);
    Window window = NotificationsManagerImpl.findWindowForBalloon(project);
    if (range != null && window != null) {
      Point point = this.myLogEditor.visualPositionToXY(this.myLogEditor.offsetToVisualPosition(range.getStartOffset()));
      return new RelativePoint(window, SwingUtilities.convertPoint(this.myLogEditor.getContentComponent(), point, window));
    }
    return null;
  }

  private static void append(Document document, String s) {
    document.insertString(document.getTextLength(), s);
  }

}