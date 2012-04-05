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
package com.intellij.ide.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.util.ExecUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.AppUIUtil;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.script.ScriptException;
import javax.swing.*;
import java.io.File;
import java.io.IOException;

import static com.intellij.util.containers.CollectionFactory.hashMap;
import static java.util.Arrays.asList;

public class CreateDesktopEntryAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.actions.CreateDesktopEntryAction");

  private static final int MIN_ICON_SIZE = 32;

  public static boolean isAvailable() {
    return SystemInfo.hasXdgOpen();
  }

  @Override
  public void update(final AnActionEvent event) {
    final boolean enabled = isAvailable();
    final Presentation presentation = event.getPresentation();
    presentation.setEnabled(enabled);
    presentation.setVisible(enabled);
  }

  @Override
  public void actionPerformed(final AnActionEvent event) {
    if (!isAvailable()) return;

    final Project project = event.getProject();
    final CreateDesktopEntryDialog dialog = new CreateDesktopEntryDialog(project);
    dialog.show();
    if (!dialog.isOK()) {
      return;
    }

    final boolean globalEntry = dialog.myGlobalEntryCheckBox.isSelected();
    ProgressManager.getInstance().run(new Task.Backgroundable(project, event.getPresentation().getText()) {
      @Override
      public void run(@NotNull final ProgressIndicator indicator) {
        createDesktopEntry(getProject(), indicator, globalEntry);
      }
    });
  }

  public static void createDesktopEntry(@Nullable final Project project,
                                        @NotNull final ProgressIndicator indicator,
                                        final boolean globalEntry) {
    if (!isAvailable()) return;
    final double step = (1.0 - indicator.getFraction()) / 3;

    try {
      indicator.setText(ApplicationBundle.message("desktop.entry.checking"));
      check();
      indicator.setFraction(indicator.getFraction() + step);

      indicator.setText(ApplicationBundle.message("desktop.entry.preparing"));
      final File entry = prepare();
      indicator.setFraction(indicator.getFraction() + step);

      indicator.setText(ApplicationBundle.message("desktop.entry.installing"));
      install(entry, globalEntry);
      indicator.setFraction(indicator.getFraction() + step);

      final String message = ApplicationBundle.message("desktop.entry.success",
                                                       ApplicationNamesInfo.getInstance().getProductName());
      Notifications.Bus.notify(
        new Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID, "Desktop entry created", message, NotificationType.INFORMATION)
      );
    }
    catch (Exception e) {
      final String message = e.getMessage();
      if (!StringUtil.isEmptyOrSpaces(message)) {
        LOG.warn(e);
        Notifications.Bus.notify(
          new Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID, "Failed to create desktop entry", message, NotificationType.ERROR),
          project
        );
      }
      else {
        LOG.error(e);
      }
    }
  }

  private static void check() throws ExecutionException, InterruptedException {
    final int result = ExecUtil.execAndGetResult("which", "xdg-desktop-menu");
    if (result != 0) throw new RuntimeException(ApplicationBundle.message("desktop.entry.xdg.missing"));
  }

  private static File prepare() throws IOException {
    final String homePath = PathManager.getHomePath();
    assert homePath != null && new File(homePath).isDirectory() : "Invalid home path: '" + homePath + "'";
    final String binPath = homePath + "/bin";
    assert new File(binPath).isDirectory() : "Invalid bin/ path: '" + binPath + "'";

    String name = ApplicationNamesInfo.getInstance().getFullProductName();
    if (PlatformUtils.isCommunity()) name += " Community Edition";

    final String iconPath = findIcon(binPath);
    if (iconPath == null) {
      throw new RuntimeException(ApplicationBundle.message("desktop.entry.icon.missing", binPath));
    }

    final String execPath = findScript(binPath);
    if (execPath == null) {
      throw new RuntimeException(ApplicationBundle.message("desktop.entry.script.missing", binPath));
    }

    final String wmClass = AppUIUtil.getFrameClass();

    final String content = ExecUtil.loadTemplate(CreateDesktopEntryAction.class.getClassLoader(), "entry.desktop",
                                                 hashMap(asList("$NAME$", "$SCRIPT$", "$ICON$", "$WM_CLASS$"),
                                                         asList(name, execPath, iconPath, wmClass)));

    final String entryName = wmClass + ".desktop";
    final File entryFile = new File(FileUtil.getTempDirectory(), entryName);
    FileUtil.writeToFile(entryFile, content);
    return entryFile;
  }

  @Nullable
  private static String findIcon(final String iconsPath) {
    final File iconsDir = new File(iconsPath);

    // 1. look for .svg icon
    for (String child : iconsDir.list()) {
      if (child.endsWith(".svg")) {
        return iconsPath + '/' + child;
      }
    }

    // 2. look for .png icon of max size
    int max = 0;
    String iconPath = null;
    for (String child : iconsDir.list()) {
      if (!child.endsWith(".png")) continue;
      final String path = iconsPath + '/' + child;
      final Icon icon = new ImageIcon(path);
      final int size = icon.getIconHeight();
      if (size >= MIN_ICON_SIZE && size > max && size == icon.getIconWidth()) {
        max = size;
        iconPath = path;
      }
    }

    return iconPath;
  }

  @Nullable
  private static String findScript(final String binPath) {
    final String productName = ApplicationNamesInfo.getInstance().getProductName();

    String execPath = binPath + '/' + productName + ".sh";
    if (new File(execPath).canExecute()) return execPath;

    execPath = binPath + '/' + productName.toLowerCase() + ".sh";
    if (new File(execPath).canExecute()) return execPath;

    return null;
  }

  private static void install(final File entryFile, final boolean globalEntry) throws IOException, ExecutionException, InterruptedException, ScriptException {
    try {
      final int result;
      if (globalEntry) {
        final String source = "#!/bin/sh\n" +
                               "xdg-desktop-menu install --mode system \"" + entryFile.getAbsolutePath() + "\"";
        final File script = ExecUtil.createTempExecutableScript("sudo", ".sh", source);
        result = ExecUtil.sudoAndGetResult(script.getAbsolutePath(), ApplicationBundle.message("desktop.entry.sudo.prompt"));
      }
      else {
        result = ExecUtil.execAndGetResult("xdg-desktop-menu", "install", "--mode", "user", entryFile.getAbsolutePath());
      }
      if (result != 0) throw new RuntimeException("'" + entryFile.getAbsolutePath() + "' : " + result);
    }
    finally {
      if (!entryFile.delete()) LOG.error("Failed to delete temp file '" + entryFile + "'");
    }
  }

  public static class CreateDesktopEntryDialog extends DialogWrapper {
    private JPanel myContentPane;
    private JLabel myLabel;
    private JCheckBox myGlobalEntryCheckBox;

    public CreateDesktopEntryDialog(final Project project) {
      super(project);
      init();
      setTitle("Create Desktop Entry");
      myLabel.setText(myLabel.getText().replace("$APP_NAME$", ApplicationNamesInfo.getInstance().getProductName()));
    }

    @Override
    protected JComponent createCenterPanel() {
      return myContentPane;
    }
  }
}
