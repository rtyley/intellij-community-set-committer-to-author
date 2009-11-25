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

package com.intellij.ide.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.structureView.StructureView;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewWrapper;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.Alarm;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.util.Arrays;

/**
 * @author Eugene Belyaev
 */
public class StructureViewWrapperImpl implements StructureViewWrapper, Disposable {
  private final Project myProject;

  private FileEditor myFileEditor;
  private Module myModule;

  private StructureView myStructureView;
  private ModuleStructureComponent myModuleStructureComponent;

  private final JPanel myPanel;
  private final Alarm myUpdateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

  // -------------------------------------------------------------------------
  // Constructor
  // -------------------------------------------------------------------------

  public StructureViewWrapperImpl(Project project) {
    myProject = project;
    myPanel = new JPanel(new BorderLayout());
    myPanel.setBackground(UIUtil.getTreeTextBackground());

    ActionManager.getInstance().addTimerListener(500, new TimerListener() {
      public ModalityState getModalityState() {
        return ModalityState.stateForComponent(myPanel);
      }

      public void run() {
        checkUpdate();
      }
    });

    getComponent().addHierarchyListener(new HierarchyListener() {
      public void hierarchyChanged(HierarchyEvent e) {
        if ((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0) {
          scheduleRebuild();
        }
      }
    });
  }

  private void checkUpdate() {
    if (myProject.isDisposed()) return;

    Window mywindow = SwingUtilities.windowForComponent(myPanel);
    if (mywindow != null && !mywindow.isActive()) return;

    final KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
    final Window focusWindow = focusManager.getFocusedWindow();

    if (focusWindow == mywindow) {
      final Component owner = focusManager.getFocusOwner();
      if (owner == null || SwingUtilities.isDescendingFrom(owner, myPanel)) return;

      final DataContext dataContext = DataManager.getInstance().getDataContext(owner);
      final FileEditor fileEditor = PlatformDataKeys.FILE_EDITOR.getData(dataContext);
      if (fileEditor != null) {
        if (Arrays.asList(FileEditorManager.getInstance(myProject).getSelectedEditors()).contains(fileEditor)) {
          setFileEditor(fileEditor);
          return;
        }
      }

      final VirtualFile[] files = PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
      if (files != null && files.length == 1) {
        FileEditorProviderManager editorProviderManager = FileEditorProviderManager.getInstance();
        final FileEditorProvider[] providers = editorProviderManager.getProviders(myProject, files[0]);
        for (FileEditorProvider provider : providers) {
          FileEditor tempFileEditor = provider.createEditor(myProject, files[0]);
          setFileEditor(tempFileEditor);
          return;
        }
      }

      setModule(LangDataKeys.MODULE_CONTEXT.getData(dataContext));
    }
  }


  // -------------------------------------------------------------------------
  // StructureView interface implementation
  // -------------------------------------------------------------------------

  public JComponent getComponent() {
    return myPanel;
  }

  public void dispose() {
    myFileEditor = null;
    rebuild();
  }

  public boolean selectCurrentElement(FileEditor fileEditor, boolean requestFocus) {
    if (myStructureView != null) {
      if (!Comparing.equal(myStructureView.getFileEditor(), fileEditor)) {
        setFileEditor(fileEditor);
        rebuild();
      }
      return myStructureView.navigateToSelectedElement(requestFocus);
    }
    else {
      return false;
    }
  }

  private void setModule(Module module) {
    if (module != myModule) {
      myFileEditor = null;
      myModule = module;
      scheduleRebuild();
    }
    else if (module == null) {
      setFileEditor(null);
    }
  }

  public void setFileEditor(FileEditor fileEditor) {
    if (myModule != null) {
      myModule = null;
      scheduleRebuild();
    }
    else {
      if (!Comparing.equal(myFileEditor, fileEditor)) {
        myFileEditor = fileEditor;
        scheduleRebuild();
        return;
      }

      if (isStructureViewShowing() && myPanel.getComponentCount() == 0 && myFileEditor != null) {
        scheduleRebuild();
      }
    }
  }

  private void scheduleRebuild() {
    myUpdateAlarm.cancelAllRequests();
    myUpdateAlarm.addRequest(new Runnable() {
      public void run() {
        rebuild();
      }
    }, 300, ModalityState.stateForComponent(myPanel));
  }

  public void rebuild() {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    boolean hadFocus = myStructureView != null && IJSwingUtilities.hasFocus2(myStructureView.getComponent()) ||
                       myModuleStructureComponent != null && IJSwingUtilities.hasFocus2(myModuleStructureComponent);

    if (myStructureView != null) {
      myStructureView.storeState();
      Disposer.dispose(myStructureView);
      myStructureView = null;
    }

    if (myModuleStructureComponent != null) {
      Disposer.dispose(myModuleStructureComponent);
      myModuleStructureComponent = null;
    }

    myPanel.removeAll();

    if (!isStructureViewShowing()) {
      return;
    }

    if (myModule != null) {
      myModuleStructureComponent = new ModuleStructureComponent(myModule);
      myPanel.add(myModuleStructureComponent, BorderLayout.CENTER);
      if (hadFocus) {
        JComponent focusedComponent = IdeFocusTraversalPolicy.getPreferredFocusedComponent(myModuleStructureComponent);
        if (focusedComponent != null) {
          focusedComponent.requestFocus();
        }
      }
    }
    else if (myFileEditor != null && myFileEditor.isValid()) {
      final StructureViewBuilder structureViewBuilder = myFileEditor.getStructureViewBuilder();
      if (structureViewBuilder != null) {
        myStructureView = structureViewBuilder.createStructureView(myFileEditor, myProject);
        myPanel.add(myStructureView.getComponent(), BorderLayout.CENTER);
        if (hadFocus) {
          JComponent focusedComponent = IdeFocusTraversalPolicy.getPreferredFocusedComponent(myStructureView.getComponent());
          if (focusedComponent != null) {
            focusedComponent.requestFocus();
          }
        }
        myStructureView.restoreState();
        myStructureView.centerSelectedRow();
      }
    }

    if (myModuleStructureComponent == null && myStructureView == null) {
      myPanel.add(new JLabel(IdeBundle.message("message.nothing.to.show.in.structure.view"), SwingConstants.CENTER), BorderLayout.CENTER);
    }

    myPanel.validate();
    myPanel.repaint();
  }


  protected boolean isStructureViewShowing() {
    ToolWindowManager windowManager = ToolWindowManager.getInstance(myProject);
    ToolWindow toolWindow = windowManager.getToolWindow(ToolWindowId.STRUCTURE_VIEW);
    // it means that window is registered
    return toolWindow != null && toolWindow.isVisible();
  }
}
