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
package com.intellij.openapi.diff.impl;

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.diff.impl.util.LabeledEditor;
import com.intellij.openapi.diff.impl.util.SyncScrollSupport;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.ui.ScrollUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

public class DiffSideView {
  private final JComponent MOCK_COMPONENT = new JPanel();
  {
    MOCK_COMPONENT.setFocusable(true);
  }

  private static final DiffHighlighterFactory DUMMY_HIGHLIGHTER_FACTORY = new DiffHighlighterFactoryImpl(null, null, null);
  private final LabeledEditor myPanel;

  private final DiffSidesContainer myContainer;
  private final CurrentLineMarker myLineMarker = new CurrentLineMarker();

  private DiffHighlighterFactory myHighlighterFactory = DUMMY_HIGHLIGHTER_FACTORY;
  private EditorSource myEditorSource = EditorSource.NULL;
  private boolean myIsMaster = false;
  private JComponent myTitle = new JLabel();

  public DiffSideView(DiffSidesContainer container, @Nullable Border editorBorder) {
    myContainer = container;
    myPanel = new LabeledEditor(editorBorder);
    insertComponent(MOCK_COMPONENT);
  }

  public JComponent getComponent() {
    return myPanel;
  }

  public void setEditorSource(final Project project, final EditorSource source) {
    MyState state = new MyState();
    myEditorSource = source;
    myLineMarker.attach(myEditorSource);
    Editor editor = myEditorSource.getEditor();
    final FileEditor fileEditor = myEditorSource.getFileEditor();
    if (editor == null) {
      insertComponent(fileEditor == null ? MOCK_COMPONENT : fileEditor.getComponent());
      DataManager.registerDataProvider(myPanel, new DataProvider() {
        @Override
        public Object getData(@NonNls String dataId) {
          if (PlatformDataKeys.PROJECT.is(dataId)) {return project;}
          if (PlatformDataKeys.FILE_EDITOR.is(dataId)) {return fileEditor;}
          return null;
        }
      });
      if (fileEditor != null) {
        ScrollUtil.scrollVertically(fileEditor.getComponent(), 0);
        ScrollUtil.scrollHorizontally(fileEditor.getComponent(), 0);

      }
    } else {
      DataManager.removeDataProvider(myPanel);
      editor.getScrollingModel().scrollHorizontally(0);
      insertComponent(editor.getComponent());
      applyHighlighter();
      MyEditorFocusListener.install(this);

      state.restore();
    }
  }

  private void insertComponent(JComponent component) {
    myPanel.setComponent(component, myTitle);
  }

  public void setHighlighterFactory(DiffHighlighterFactory highlighterFactory) {
    myHighlighterFactory = highlighterFactory;
    applyHighlighter();
  }

  private void applyHighlighter() {
    EditorEx editor = myEditorSource.getEditor();
    if (editor == null) return;
    EditorHighlighter highlighter = myHighlighterFactory.createHighlighter();
    if (highlighter != null) editor.setHighlighter(highlighter);
    editor.getColorsScheme().setColor(EditorColors.CARET_ROW_COLOR, null);
  }

  public void setTitle(@NotNull JComponent title) {
    myTitle = title;
    myPanel.updateTitle(myTitle);
  }

  public void beSlave() {
    myIsMaster = false;
    myLineMarker.hide();
  }

  @Nullable
  public OpenFileDescriptor getCurrentOpenFileDescriptor() {
    final EditorEx editor = myEditorSource.getEditor();
    final DiffContent content = myEditorSource.getContent();
    if (content == null || editor == null) {
      return null;
    }
    return content.getOpenFileDescriptor(editor.getCaretModel().getOffset());
  }

  private static class MyEditorFocusListener extends FocusAdapter {
    private final DiffSideView mySideView;

    private MyEditorFocusListener(DiffSideView sideView) {
      mySideView = sideView;
    }

    public void focusGained(FocusEvent e) {
      mySideView.becomeMaster();
    }

    public static MyEditorFocusListener install(DiffSideView sideView) {
      final MyEditorFocusListener listener = new MyEditorFocusListener(sideView);
      final JComponent focusableComponent = sideView.getFocusableComponent();
      focusableComponent.addFocusListener(listener);
      sideView.myEditorSource.addDisposable(new Disposable() {
        public void dispose() {
          focusableComponent.removeFocusListener(listener);
        }
      });
      return listener;
    }
  }

  public JComponent getFocusableComponent() {
    Editor editor = getEditor();
    return editor != null ? editor.getContentComponent() : MOCK_COMPONENT;
  }

  public void becomeMaster() {
    if (myIsMaster) return;
    myIsMaster = true;
    myContainer.setCurrentSide(this);
    beMaster();
  }

  private void beMaster() {
    myLineMarker.set();
  }

  public void scrollToFirstDiff(int logicalLine) {
    Editor editor = myEditorSource.getEditor();
    SyncScrollSupport.scrollEditor(editor, logicalLine);
  }

  @Nullable
  public Editor getEditor() {
    return myEditorSource.getEditor();
  }

  @Nullable
  public FragmentSide getSide() {
    return myEditorSource.getSide();
  }

  private class MyState {
    private final boolean isFocused;
    public MyState() {
      isFocused = IJSwingUtilities.hasFocus(getFocusableComponent());
    }

    public void restore() {
      if (isFocused) getFocusableComponent().requestFocus();
      if (myIsMaster) beMaster();
    }
  }
}
