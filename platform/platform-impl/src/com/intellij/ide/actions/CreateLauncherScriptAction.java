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
package com.intellij.ide.actions;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.system.ExecUtil;
import com.intellij.openapi.util.text.StringUtil;

import javax.swing.*;
import java.io.File;
import java.io.IOException;

import static com.intellij.util.containers.CollectionFactory.hashMap;
import static java.util.Arrays.asList;

/**
 * @author yole
 */
public class CreateLauncherScriptAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.actions.CreateLauncherScriptAction");

  public static boolean isAvailable() {
    return SystemInfo.isUnix;
  }

  @Override
  public void update(AnActionEvent e) {
    final boolean canCreateScript = isAvailable();
    final Presentation presentation = e.getPresentation();
    presentation.setVisible(canCreateScript);
    presentation.setEnabled(canCreateScript);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    if (!isAvailable()) return;

    Project project = e.getProject();
    CreateLauncherScriptDialog dialog = new CreateLauncherScriptDialog(project);
    dialog.show();
    if (!dialog.isOK()) {
      return;
    }

    final File target = new File(dialog.myPathField.getText(), dialog.myNameField.getText());
    if (target.exists()) {
      int rc = Messages.showOkCancelDialog(project, ApplicationBundle.message("launcher.script.overwrite", target),
                                           "Create Launcher Script", Messages.getQuestionIcon());
      if (rc != 0) {
        return;
      }
    }

    createLauncherScript(project, target.getAbsolutePath());
  }

  public static void createLauncherScript(Project project, String pathName) {
    if (!isAvailable()) return;

    try {
      final File scriptFile = createLauncherScriptFile();
      final File scriptTarget = new File(pathName);

      final File launcherScriptContainingDir = scriptTarget.getParentFile();
      if (!(launcherScriptContainingDir.exists() || launcherScriptContainingDir.mkdirs()) ||
          !scriptFile.renameTo(scriptTarget)) {
        final String launcherScriptContainingDirPath = launcherScriptContainingDir.getCanonicalPath();
        final String installationScriptSrc =
          "#!/bin/sh\n" +
          // create all intermediate folders
          "mkdir -p \"" + launcherScriptContainingDirPath + "\"\n" +
          // copy file and change ownership to root (UID 0 = root, GID 0 = root (wheel on Macs))
          "install -g 0 -o 0 \"" + scriptFile.getCanonicalPath() + "\" \"" + pathName + "\"";
        final File installationScript = ExecUtil.createTempExecutableScript("launcher_installer", ".sh", installationScriptSrc);
        ExecUtil.sudoAndGetResult(installationScript.getAbsolutePath(),
                                  ApplicationBundle.message("launcher.script.sudo.prompt", launcherScriptContainingDirPath));
      }
    }
    catch (Exception e) {
      final String message = e.getMessage();
      if (!StringUtil.isEmptyOrSpaces(message)) {
        LOG.warn(e);
        Notifications.Bus.notify(
          new Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID, "Failed to create launcher script", message, NotificationType.ERROR),
          project
        );
      }
      else {
        LOG.error(e);
      }
    }
  }

  private static File createLauncherScriptFile() throws IOException {
    String runPath = PathManager.getHomePath();
    if (!SystemInfo.isMac) {
      // for Macs just use "*.app"
      final String productName = ApplicationNamesInfo.getInstance().getProductName().toLowerCase();
      runPath += "/bin/" + productName + ".sh";
    }
    final String launcherContents = ExecUtil.loadTemplate(CreateLauncherScriptAction.class.getClassLoader(), "launcher.py",
                                                          hashMap(asList("$CONFIG_PATH$", "$RUN_PATH$"),
                                                                  asList(PathManager.getConfigPath(), runPath)));
    return ExecUtil.createTempExecutableScript("launcher", "", launcherContents);
  }

  public static String defaultScriptName() {
    final String scriptName = ApplicationNamesInfo.getInstance().getScriptName();
    return StringUtil.isEmptyOrSpaces(scriptName) ? "idea" : scriptName;
  }

  public static class CreateLauncherScriptDialog extends DialogWrapper {
    private JPanel myMainPanel;
    private JTextField myNameField;
    private JTextField myPathField;
    private JLabel myTitle;

    protected CreateLauncherScriptDialog(Project project) {
      super(project);
      init();
      setTitle("Create Launcher Script");
      final String productName = ApplicationNamesInfo.getInstance().getProductName();
      myTitle.setText(myTitle.getText().replace("$APP_NAME$", productName));
      myNameField.setText(defaultScriptName());
    }

    @Override
    protected JComponent createCenterPanel() {
      return myMainPanel;
    }
  }
}
