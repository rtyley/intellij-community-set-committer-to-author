/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.ui.docking.DockContainer;
import com.intellij.ui.docking.DockContainerFactory;
import com.intellij.ui.docking.DockableContent;
import com.intellij.ui.tabs.JBTabs;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.impl.JBTabsImpl;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.CopyOnWriteArraySet;

class DockableEditorTabbedContainer implements DockContainerFactory, DockContainer {

  private EditorsSplitters mySplitters;
  private Project myProject;

  private CopyOnWriteArraySet<Listener> myListeners = new CopyOnWriteArraySet<Listener>();

  private JBTabs myCurrentOver;
  private Image myCurrentOverImg;
  private TabInfo myCurrentOverInfo;

  private boolean myDisposeWhenEmpty;

  DockableEditorTabbedContainer(Project project) {
    this(project, null, true);
  }

  DockableEditorTabbedContainer(Project project, EditorsSplitters splitters, boolean disposeWhenEmpty) {
    myProject = project;
    mySplitters = splitters;
    myDisposeWhenEmpty = disposeWhenEmpty;
  }

  @Override
  public DockContainer createContainer() {
    if (mySplitters == null) {
      mySplitters = new EditorsSplitters((FileEditorManagerImpl)FileEditorManager.getInstance(myProject), false) {
        @Override
        protected void afterFileClosed(VirtualFile file) {
          fireContentClosed(file);
        }

        @Override
        protected void afterFileOpen(VirtualFile file) {
          fireContentOpen(file);
        }
      };
      mySplitters.createCurrentWindow();
    }
    return this;
  }

  private void fireContentClosed(VirtualFile file) {
    for (Listener each : myListeners) {
      each.contentRemoved(file);
    }
  }

  private void fireContentOpen(VirtualFile file) {
    for (Listener each : myListeners) {
      each.contentAdded(file);
    }
  }

  @Override
  public RelativeRectangle getAcceptArea() {
    return new RelativeRectangle(mySplitters);
  }

  @Override
  public boolean canAccept(DockableContent content, RelativePoint point) {
    if (content instanceof EditorTabbedContainer.MyDragOutDelegate.DockableEditor) {
      EditorTabbedContainer.MyDragOutDelegate.DockableEditor editor = (EditorTabbedContainer.MyDragOutDelegate.DockableEditor)content;

      JBTabs targetTabs = mySplitters.getTabsAt(point);
      if (targetTabs != null) {
        EditorWindow targetWindow = EditorWindow.DATA_KEY.getData(targetTabs.getDataProvider());
        if (targetWindow != editor.getEditorWindow()) return true;
      }
    }

    return false;
  }

  @Override
  public void add(DockableContent content, RelativePoint dropTarget) {
    EditorWindow window = null;
    if (myCurrentOver != null) {
      window = EditorWindow.DATA_KEY.getData(myCurrentOver.getDataProvider());
    }

    VirtualFile file = ((EditorTabbedContainer.MyDragOutDelegate.DockableEditor)content).getFile();


    if (window == null) {
      window = mySplitters.getOrCreateCurrentWindow(file);
    }


    if (myCurrentOver != null) {
      int index = ((JBTabsImpl)myCurrentOver).getDropInfoIndex();
      file.putUserData(EditorWindow.INITIAL_INDEX_KEY, index);
    }

    ((FileEditorManagerImpl)FileEditorManagerEx.getInstanceEx(myProject)).openFileImpl2(window, file, true);
  }

  @Override
  public Image startDropOver(DockableContent content, RelativePoint point) {
    return null;
  }

  @Override
  public Image processDropOver(DockableContent content, RelativePoint point) {
    JBTabs current = mySplitters.getTabsAt(point);
    if (myCurrentOver != null && myCurrentOver != current) {
      resetDropOver(content);
    }

    if (myCurrentOver == null && current != null) {
      myCurrentOver = current;
      Presentation presentation = content.getPresentation();
      myCurrentOverInfo = new TabInfo(new JLabel("")).setText(presentation.getText()).setIcon(presentation.getIcon());
      myCurrentOverImg = myCurrentOver.startDropOver(myCurrentOverInfo, point);
    }

    if (myCurrentOver != null) {
      myCurrentOver.processDropOver(myCurrentOverInfo, point);
    }

    return myCurrentOverImg;
  }

  @Override
  public void resetDropOver(DockableContent content) {
    if (myCurrentOver != null) {
      myCurrentOver.resetDropOver(myCurrentOverInfo);
      myCurrentOver = null;
      myCurrentOverInfo = null;
      myCurrentOverImg = null;
    }
  }

  @Override
  public JComponent getComponent() {
    return mySplitters;
  }

  public EditorsSplitters getSplitters() {
    return mySplitters;
  }

  public void close(VirtualFile file) {
    mySplitters.getCurrentWindow().closeFile(file);
  }

  @Override
  public void closeAll() {
    VirtualFile[] files = mySplitters.getOpenFiles();
    for (VirtualFile each : files) {
      close(each);
    }
  }

  @Override
  public void addListener(final Listener listener, Disposable parent) {
    myListeners.add(listener);
    Disposer.register(parent, new Disposable() {
      @Override
      public void dispose() {
        myListeners.remove(listener);
      }
    });
  }

  @Override
  public boolean isEmpty() {
    return mySplitters.isEmptyVisible();
  }

  @Override
  public void dispose() {
  }

  @Override
  public boolean isDisposeWhenEmpty() {
    return myDisposeWhenEmpty;
  }
}
