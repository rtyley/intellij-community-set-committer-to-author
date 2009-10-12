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
package com.intellij.openapi.vcs.checkout;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsBundle;
import org.apache.oro.io.GlobFilenameFilter;

import java.io.File;
import java.io.FilenameFilter;

/**
 * @author yole
 */
public class ProjectCheckoutListener implements CheckoutListener {
  public boolean processCheckedOutDirectory(Project project, File directory) {
    File[] files = directory.listFiles((FilenameFilter) new GlobFilenameFilter("*" + ProjectFileType.DOT_DEFAULT_EXTENSION));
    if (files != null && files.length > 0) {
      int rc = Messages.showYesNoDialog(project, VcsBundle.message("checkout.open.project.prompt", files[0].getAbsolutePath()),
                                        VcsBundle.message("checkout.title"), Messages.getQuestionIcon());
      if (rc == 0) {
        ProjectUtil.openProject(files [0].getAbsolutePath(), project, false);
      }
      return true;
    }
    return false;
  }
}