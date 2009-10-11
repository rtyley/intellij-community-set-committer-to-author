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
package com.intellij.openapi.wm.impl;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.DataManager;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ex.LayoutFocusTraversalPolicyExt;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.BalloonLayout;
import com.intellij.ui.FocusTrackback;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */

// Made non-final for Fabrique
public class IdeFrameImpl extends JFrame implements IdeFrame, DataProvider {
  private String myTitle;

  private String myFileTitle;
  private File myCurrentFile;

  private Project myProject;
  private final LayoutFocusTraversalPolicyExt myLayoutFocusTraversalPolicy;

  private IdeRootPane myRootPane;
  private BalloonLayout myBalloonLayout;

  public IdeFrameImpl(ApplicationInfoEx applicationInfoEx, ActionManager actionManager, UISettings uiSettings, DataManager dataManager,
                      KeymapManager keymapManager, final Application application, final String[] commandLineArgs) {
    super(applicationInfoEx.getFullApplicationName());
    myRootPane = new IdeRootPane(actionManager, uiSettings, dataManager, keymapManager, application, commandLineArgs);
    setRootPane(myRootPane);

    AppUIUtil.updateFrameIcon(this);
    final Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
    setBounds(10, 10, screenSize.width - 20, screenSize.height - 40);

    myLayoutFocusTraversalPolicy = new LayoutFocusTraversalPolicyExt();
    setFocusTraversalPolicy(myLayoutFocusTraversalPolicy);

    setupCloseAction();
    new MnemonicHelper().register(this);

    myBalloonLayout = new BalloonLayout(myRootPane.getLayeredPane(), new Insets(8, 8, 8, 8));
  }

  /**
   * !!!!! CAUTION !!!!!
   * !!!!! CAUTION !!!!!
   * !!!!! CAUTION !!!!!
   *
   * THIS IS AN "ABSOLUTELY-GURU METHOD".
   * NOBODY SHOULD ADD OTHER USAGES OF IT :)
   * ONLY ANTON AND VOVA ARE PERMITTED TO USE THIS METHOD!!!
   *
   * !!!!! CAUTION !!!!!
   * !!!!! CAUTION !!!!!
   * !!!!! CAUTION !!!!!
   */
  public final void setDefaultFocusableComponent(final JComponent component) {
    myLayoutFocusTraversalPolicy.setOverridenDefaultComponent(component);
  }

  /**
   * This is overriden to get rid of strange Alloy LaF customization of frames. For unknown reason it sets the maxBounds rectangle
   * and it does it plain wrong. Setting bounds to <code>null</code> means default value should be taken from the underlying OS.
   */
  public synchronized void setMaximizedBounds(Rectangle bounds) {
    super.setMaximizedBounds(null);
  }

  private void setupCloseAction() {
    setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    addWindowListener(
      new WindowAdapter() {
        public void windowClosing(final WindowEvent e) {
          final Application app = ApplicationManager.getApplication();
          app.invokeLater(new DumbAwareRunnable() {
            public void run() {
              if (app.isDisposed()) {
                ApplicationManagerEx.getApplicationEx().exit();
                return;
              }
              
              final Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
              if (openProjects.length > 1) {
                if (myProject != null && myProject.isOpen()) {
                  ProjectUtil.closeProject(myProject);
                }
                app.getMessageBus().syncPublisher(AppLifecycleListener.TOPIC).projectFrameClosed();
              }
              else {
                ApplicationManagerEx.getApplicationEx().exit();
              }
            }
          }, ModalityState.NON_MODAL);
        }
      }
    );
  }

  public StatusBarEx getStatusBar() {
    return ((IdeRootPane)getRootPane()).getStatusBar();
  }

  public void updateToolbar() {
    ((IdeRootPane)getRootPane()).updateToolbar();
  }

  public void updateMenuBar(){
    ((IdeRootPane)getRootPane()).updateMainMenuActions();
  }

  public void setTitle(final String title) {
    myTitle = title;
    updateTitle();
  }

  private void setFrameTitle(final String text) {
    super.setTitle(text);
  }

  public void setFileTitle(final String fileTitle) {
    setFileTitle(fileTitle, null);
  }

  public void setFileTitle(@Nullable final String fileTitle, @Nullable File file) {
    myFileTitle = fileTitle;
    myCurrentFile = file;
    updateTitle();
  }

  private void updateTitle() {
    final StringBuilder sb = new StringBuilder();
    if (myTitle != null && myTitle.length() > 0) {
      sb.append(myTitle);
      sb.append(" - ");
    }
    if (myFileTitle != null && myFileTitle.length() > 0) {
      sb.append(myFileTitle);
      sb.append(" - ");
    }

    getRootPane().putClientProperty("Window.documentFile", myCurrentFile);

    sb.append(((ApplicationInfoEx)ApplicationInfo.getInstance()).getFullApplicationName());
    setFrameTitle(sb.toString());
  }

  public Object getData(final String dataId) {
    if (DataConstants.PROJECT.equals(dataId)) {
      if (myProject != null) {
        return myProject.isInitialized() ? myProject : null;
      }
    }
    return null;
  }

  public void setProject(final Project project) {
    getStatusBar().cleanupCustomComponents();
    myProject = project;
    if (project != null) {
      if (myRootPane != null) {
        myRootPane.installNorthComponents(project);
      }
    }
    else {
      if (myRootPane != null) { //already disposed
        myRootPane.deinstallNorthComponents();
      }
    }

    if (project == null) {
      FocusTrackback.release(this);
    }
  }

  public Project getProject() {
    return myProject;
  }

  public void dispose() {
    if (myRootPane != null) {
      myRootPane = null;
    }
    super.dispose();
  }

  @Override
  public void paint(Graphics g) {
    UIUtil.applyRenderingHints(g);
    super.paint(g);
  }

  public Rectangle suggestChildFrameBounds() {
//todo [kirillk] a dummy implementation
    final Rectangle b = getBounds();
    b.x += 100;
    b.width -= 200;
    b.y += 100;
    b.height -= 200;
    return b;
  }

  @Nullable
  public static Component findNearestModalComponent(@NotNull Component c) {
    Component eachParent = c;
    while (eachParent != null) {
      if (eachParent instanceof IdeFrameImpl) return eachParent;
      if (eachParent instanceof JDialog) {
        if (((JDialog)eachParent).isModal()) return eachParent;
      }
      eachParent = eachParent.getParent();
    }

    return null;
  }

  public final BalloonLayout getBalloonLayout() {
    return myBalloonLayout;
  }
}
