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

import com.intellij.ide.IdeBundle;
import com.intellij.ide.structureView.StructureView;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewWrapper;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
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

/**
 * @author Eugene Belyaev
 */
public class StructureViewWrapperImpl implements StructureViewWrapper, Disposable {
  private final Project myProject;
  private FileEditor myFileEditor;

  private StructureView myStructureView;

  private final JPanel myPanel;

  private final Alarm myAlarm;

  // -------------------------------------------------------------------------
  // Constructor
  // -------------------------------------------------------------------------

  public StructureViewWrapperImpl(Project project) {
    myProject = project;
    myPanel = new JPanel(new BorderLayout());
    myPanel.setBackground(UIUtil.getTreeTextBackground());

    myAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);

    getComponent().addHierarchyListener(new HierarchyListener() {
      public void hierarchyChanged(HierarchyEvent e) {
        if ((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0) {
          rebuild();
        }
      }
    });

    FileEditorManagerListener editorManagerListener = new FileEditorManagerAdapter() {
      private FileEditorManagerEvent myLastEvent;

      public void selectionChanged(final FileEditorManagerEvent event) {
        myLastEvent = event;
        myAlarm.cancelAllRequests();
        myAlarm.addRequest(new Runnable() {
          public void run() {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              public void run() {
                if (myLastEvent == null) {
                  return;
                }
                try {
                  if (myProject.isDisposed()) {
                    return; // project may have been closed
                  }
                  PsiDocumentManager.getInstance(myProject).commitAllDocuments();
                  setFileEditor(myLastEvent.getNewEditor());
                }
                finally {
                  myLastEvent = null;
                }
              }
            }, ModalityState.NON_MODAL);
          }
        }, 400);
      }
    };
    FileEditorManager.getInstance(project).addFileEditorManagerListener(editorManagerListener,this);
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
      if (!Comparing.equal(myStructureView.getFileEditor(), fileEditor)){
        setFileEditor(fileEditor);
        rebuild();
      }
      return myStructureView.navigateToSelectedElement(requestFocus);
    } else {
      return false;
    }
  }

  // -------------------------------------------------------------------------
  // Helper methods
  // -------------------------------------------------------------------------


  public void setFileEditor(FileEditor fileEditor) {
    final boolean fileChanged = myFileEditor != null ? !myFileEditor.equals(fileEditor) : fileEditor != null;
    if (fileChanged) {
      myFileEditor = fileEditor;      
    }
    if (fileChanged ||
        isStructureViewShowing() && myPanel.getComponentCount() == 0 && myFileEditor != null) {
      rebuild();
    }
  }

  public void rebuild() {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    boolean hadFocus = myStructureView != null && IJSwingUtilities.hasFocus2(myStructureView.getComponent());
    if (myStructureView != null) {
      myStructureView.storeState();
      Disposer.dispose(myStructureView);
      myStructureView = null;
    }
    myPanel.removeAll();

    if (!isStructureViewShowing()) {
      return;
    }

    if (myFileEditor!=null && myFileEditor.isValid()) {
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
    if (myStructureView == null) {
      myPanel.add(new JLabel(IdeBundle.message("message.nothing.to.show.in.structure.view"), SwingConstants.CENTER), BorderLayout.CENTER);
    }

    myPanel.validate();
    myPanel.repaint();
  }


  protected boolean isStructureViewShowing() {
    ToolWindowManager windowManager = ToolWindowManager.getInstance(myProject);
    ToolWindow toolWindow=windowManager.getToolWindow(ToolWindowId.STRUCTURE_VIEW);
    // it means that window is registered
    return toolWindow != null && toolWindow.isVisible();
  }
}