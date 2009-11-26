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
import com.intellij.ide.projectView.impl.ProjectRootsUtil;
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
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.openapi.wm.impl.IdeRootPane;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.Alarm;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;

/**
 * @author Eugene Belyaev
 */
public class StructureViewWrapperImpl implements StructureViewWrapper, Disposable {
  private final Project myProject;

  private VirtualFile myFile;

  private StructureView myStructureView;
  private ModuleStructureComponent myModuleStructureComponent;

  private final JPanel myPanel;
  private final Alarm myUpdateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  private final String myKey = new String("DATA_SELECTOR");

  // -------------------------------------------------------------------------
  // Constructor
  // -------------------------------------------------------------------------

  public StructureViewWrapperImpl(Project project) {
    myProject = project;
    myPanel = new ContentPanel();
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
      if (owner instanceof IdeRootPane) return;

      final DataContext dataContext = DataManager.getInstance().getDataContext(owner);
      if (dataContext.getData(myKey) == this) return;

      final VirtualFile[] files = PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
      if (files != null && files.length == 1) {
        setFile(files[0]);
      }
      else {
        setFile(null);
      }
    }
  }

  private void setFile(VirtualFile file) {
    if (!Comparing.equal(file, myFile)) {
      myFile = file;
      scheduleRebuild();
    }
  }


  // -------------------------------------------------------------------------
  // StructureView interface implementation
  // -------------------------------------------------------------------------

  public JComponent getComponent() {
    return myPanel;
  }

  public void dispose() {
    rebuild();
  }

  public boolean selectCurrentElement(FileEditor fileEditor, VirtualFile file, boolean requestFocus) {
    if (myStructureView != null) {
      if (!Comparing.equal(myStructureView.getFileEditor(), fileEditor)) {
        myFile = file;
        rebuild();
      }
      return myStructureView.navigateToSelectedElement(requestFocus);
    }
    else {
      return false;
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

    VirtualFile file = myFile;
    if (file == null) {
      final VirtualFile[] selectedFiles = FileEditorManager.getInstance(myProject).getSelectedFiles();
      if (selectedFiles.length > 0) {
        file = selectedFiles[0];
      }
    }

    if (file != null) {
      if (file.isDirectory()) {
        if (ProjectRootsUtil.isModuleContentRoot(file, myProject)) {
          Module module = ModuleUtil.findModuleForFile(file, myProject);
          if (module != null) {
            myModuleStructureComponent = new ModuleStructureComponent(module);
            myPanel.add(myModuleStructureComponent, BorderLayout.CENTER);
            if (hadFocus) {
              JComponent focusedComponent = IdeFocusTraversalPolicy.getPreferredFocusedComponent(myModuleStructureComponent);
              if (focusedComponent != null) {
                focusedComponent.requestFocus();
              }
            }
          }
        }
      }
      else {
        FileEditor editor = FileEditorManager.getInstance(myProject).getSelectedEditor(file);
        if (editor == null) editor = crteateTempFileEditor(file);
        if (editor != null && editor.isValid()) {
          final StructureViewBuilder structureViewBuilder = editor.getStructureViewBuilder();
          if (structureViewBuilder != null) {
            myStructureView = structureViewBuilder.createStructureView(editor, myProject);
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
      }
    }

    if (myModuleStructureComponent == null && myStructureView == null) {
      myPanel.add(new JLabel(IdeBundle.message("message.nothing.to.show.in.structure.view"), SwingConstants.CENTER), BorderLayout.CENTER);
    }

    myPanel.validate();
    myPanel.repaint();
  }

  @Nullable
  private FileEditor crteateTempFileEditor(VirtualFile file) {
    FileEditorProviderManager editorProviderManager = FileEditorProviderManager.getInstance();
    final FileEditorProvider[] providers = editorProviderManager.getProviders(myProject, file);
    for (FileEditorProvider provider : providers) {
      return provider.createEditor(myProject, file);
    }
    return null;
  }


  protected boolean isStructureViewShowing() {
    ToolWindowManager windowManager = ToolWindowManager.getInstance(myProject);
    ToolWindow toolWindow = windowManager.getToolWindow(ToolWindowId.STRUCTURE_VIEW);
    // it means that window is registered
    return toolWindow != null && toolWindow.isVisible();
  }

  private class ContentPanel extends JPanel implements DataProvider {
    public ContentPanel() {
      super(new BorderLayout());
    }

    public Object getData(@NonNls String dataId) {
      if (dataId == myKey) return StructureViewWrapperImpl.this;
      return null;
    }
  }
}
