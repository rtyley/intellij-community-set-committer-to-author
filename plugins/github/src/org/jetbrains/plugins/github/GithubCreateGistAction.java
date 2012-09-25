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
package org.jetbrains.plugins.github;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.ide.BrowserUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.tasks.github.GithubApiUtil;
import git4idea.GitVcs;
import git4idea.Notificator;
import icons.GithubIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.ui.GitHubCreateGistDialog;
import org.jetbrains.plugins.github.ui.GithubLoginDialog;

import javax.swing.event.HyperlinkEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author oleg
 * @date 9/27/11
 */
public class GithubCreateGistAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(GithubCreateGistAction.class);
  private static final String FAILED_TO_CREATE_GIST = "Can't create Gist";

  protected GithubCreateGistAction() {
    super("Create Gist...", "Create github gist", GithubIcons.Github_icon);
  }

  @Override
  public void update(final AnActionEvent e) {
    final long startTime = System.nanoTime();
    try {
      final Project project = e.getData(PlatformDataKeys.PROJECT);
      if (project == null || project.isDefault()) {
        e.getPresentation().setVisible(false);
        e.getPresentation().setEnabled(false);
        return;
      }
      final Editor editor = e.getData(PlatformDataKeys.EDITOR);
      final VirtualFile file = e.getData(PlatformDataKeys.VIRTUAL_FILE);
      final VirtualFile[] files = e.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY);

      if (editor == null && file == null && files == null) {
        e.getPresentation().setVisible(false);
        e.getPresentation().setEnabled(false);
        return;
      }

      e.getPresentation().setVisible(true);
      e.getPresentation().setEnabled(true);
    }
    finally {
      if (LOG.isDebugEnabled()) {
        LOG.debug("GithubCreateGistAction#update finished in: " + (System.nanoTime() - startTime) / 10e6 + "ms");
      }
    }
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null || project.isDefault()) {
      return;
    }

    final Editor editor = e.getData(PlatformDataKeys.EDITOR);
    final VirtualFile file = e.getData(PlatformDataKeys.VIRTUAL_FILE);
    final VirtualFile[] files = e.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY);
    if (editor == null && file == null && files == null) {
      return;
    }

    final boolean useGitHubAccount;
    if (!GithubUtil.checkCredentials(project)) {
      final GithubLoginDialog dialog = new GithubLoginDialog(project);
      dialog.show();
      useGitHubAccount = GithubUtil.checkCredentials(project);
    } else {
      useGitHubAccount = true;
    }

    // Ask for description and other params
    final GitHubCreateGistDialog dialog = new GitHubCreateGistDialog(project, useGitHubAccount);
    dialog.show();
    if (!dialog.isOK()){
      return;
    }
    final GithubSettings settings = GithubSettings.getInstance();
    final String password = settings.getPassword();
    final Ref<String> url = new Ref<String>();
    final String description = dialog.getDescription();
    final boolean isPrivate = dialog.isPrivate();
    final boolean anonymous = dialog.isAnonymous();
    final boolean openInBrowser = dialog.isOpenInBrowser();
    
    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      @Override
      public void run() {
        List<NamedContent> contents = collectContents(project, editor, file, files);
        if (contents == null) {
          return;
        }
        String gistUrl = createGist(project, settings.getLogin(), password, anonymous, contents, isPrivate, description);
        url.set(gistUrl);
      }
    }, "Communicating With GitHub", false, project);

    if (url.isNull()){
      return;
    }
    if (openInBrowser) {
      BrowserUtil.launchBrowser(url.get());
    } else {
      Notificator.getInstance(project).notify(GitVcs.IMPORTANT_ERROR_NOTIFICATION, "Gist Created Successfully",
                                              "Your gist url: <a href='open'>" + url.get() + "</a>", NotificationType.INFORMATION,
                                              new NotificationListener() {
                                                @Override
                                                public void hyperlinkUpdate(@NotNull Notification notification,
                                                                            @NotNull HyperlinkEvent event) {
                                                  if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                                                    BrowserUtil.launchBrowser(url.get());
                                                  }
                                                }
                                              });
    }
  }

  @Nullable
  private static List<NamedContent> collectContents(@NotNull Project project, @Nullable Editor editor,
                                                    @Nullable VirtualFile file, @Nullable VirtualFile[] files) {
    if (editor != null) {
      NamedContent content = getContentFromEditor(editor, file, project);
      return content == null ? null : Collections.singletonList(content);
    }
    if (files != null) {
      List<NamedContent> contents = new ArrayList<NamedContent>();
      for (VirtualFile vf : files) {
        NamedContent content = getContentFromFile(vf, project);
        if (content == null) {
          return null;
        }
        contents.add(content);
      }
      return contents;
    }

    if (file == null) {
      LOG.error("File, files and editor can't be null all at once!");
      return null;
    }

    NamedContent content = getContentFromFile(file, project);
    return content == null ? null : Collections.singletonList(content);
  }

  @Nullable
  private static String createGist(@NotNull Project project, @Nullable String login, @Nullable String password, boolean anonymous,
                                   @NotNull List<NamedContent> contents, boolean isPrivate, @NotNull String description) {
    if (anonymous) {
      login = null;
      password = null;
    }
    String requestBody = prepareJsonRequest(description, isPrivate, contents);
    try {
      JsonElement jsonElement = GithubApiUtil.postRequest("https://api.github.com", login, password, "/gists", requestBody);
      if (jsonElement == null) {
        LOG.info("Null JSON response returned by GitHub");
        showError(project, "Failed to create gist", "Empty JSON response returned by GitHub", null, null);
        return null;
      }
      if (!jsonElement.isJsonObject()) {
        LOG.error(String.format("Unexpected JSON result format: %s", jsonElement));
        return null;
      }
      JsonElement htmlUrl = jsonElement.getAsJsonObject().get("html_url");
      if (htmlUrl == null) {
        LOG.info("Invalid JSON response: " + jsonElement);
        showError(project, "Invalid GitHub response", "No html_url property", jsonElement.toString(), null);
        return null;
      }
      return htmlUrl.getAsString();
    }
    catch (IOException e) {
      LOG.info("Exception when creating a Gist", e);
      showError(project, "Failed to create gist", "", null, e);
      return null;
    }
  }

  @Nullable
  private static String readFile(@NotNull final VirtualFile file) {
    return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Nullable
      @Override
      public String compute() {
        try {
          return new String(file.contentsToByteArray(), file.getCharset());
        }
        catch (IOException e) {
          LOG.info("Couldn't read contents of the file " + file, e);
          return null;
        }
      }
    });
  }

  private static void showError(@NotNull Project project, @NotNull String title, @NotNull String content,
                                @Nullable String details, @Nullable Exception e) {
    Notificator.getInstance(project).notifyError(title, content);
    LOG.info("Couldn't parse response as json data: \n" + content + "\n" + details, e);
  }

  private static String prepareJsonRequest(@NotNull String description, boolean isPrivate, @NotNull List<NamedContent> contents) {
    JsonObject json = new JsonObject();
    json.addProperty("description", description);
    json.addProperty("public", Boolean.toString(!isPrivate));

    JsonObject files = new JsonObject();
    for (NamedContent content : contents) {
      JsonObject file = new JsonObject();
      file.addProperty("content", content.getText());
      files.add(content.getName(), file);
    }

    json.add("files", files);
    return json.toString();
  }

  @Nullable
  private static NamedContent getContentFromFile(@NotNull VirtualFile file, @NotNull Project project) {
    String content = readFile(file);
    if (content == null) {
      showError(project, FAILED_TO_CREATE_GIST, "Couldn't read the contents of the file " + file, null, null);
      LOG.info("Couldn't read the contents of the file " + file);
      return null;
    }
    return new NamedContent(file.getName(), content);
  }

  @Nullable
  private static NamedContent getContentFromEditor(@NotNull Editor editor, @Nullable VirtualFile selectedFile, @NotNull Project project) {
    String text = editor.getSelectionModel().getSelectedText();
    if (text == null) {
      text = editor.getDocument().getText();
    }

    if (StringUtil.isEmpty(text)) {
      showError(project, FAILED_TO_CREATE_GIST, "No text was selected to Gist", null, null);
      return null;
    }

    String name;
    if (selectedFile == null) {
      name = "";
    }
    else {
      name = selectedFile.getName();
    }
    return new NamedContent(name, text);
  }

  private static class NamedContent {
    @NotNull private final String myName;
    @NotNull private final String myText;

    private NamedContent(@NotNull String name, @NotNull String text) {
      myName = name;
      myText = text;
    }

    @NotNull
    public String getName() {
      return myName;
    }

    @NotNull
    public String getText() {
      return myText;
    }
  }

}
