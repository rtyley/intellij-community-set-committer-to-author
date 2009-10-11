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
package com.intellij.cvsSupport2.changeBrowser;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.netbeans.lib.cvsclient.command.log.LogInformation;
import org.netbeans.lib.cvsclient.command.log.Revision;
import org.netbeans.lib.cvsclient.command.log.SymbolicName;

import java.util.List;

public class LogInformationWrapper {
  private final String myFile;
  private final List<Revision> myRevisions;
  private final List<SymbolicName> mySymbolicNames;
  @NonNls private static final String CVS_REPOSITORY_FILE_POSTFIX = ",v";

  public LogInformationWrapper(final String file, final List<Revision> revisions, final List<SymbolicName> symbolicNames) {
    myFile = file;
    myRevisions = revisions;
    mySymbolicNames = symbolicNames;
  }

  public String getFile() {
    return myFile;
  }

  public List<Revision> getRevisions() {
    return myRevisions;
  }

  public List<SymbolicName> getSymbolicNames() {
    return mySymbolicNames;
  }

  @Nullable
  public static LogInformationWrapper wrap(final String repository, final LogInformation log) {
    LogInformationWrapper wrapper = null;
    if (!log.getRevisionList().isEmpty()) {
      final String rcsFileName = log.getRcsFileName();
      if (FileUtil.toSystemIndependentName(rcsFileName).startsWith(FileUtil.toSystemIndependentName(repository))) {
        String relativePath = rcsFileName.substring(repository.length());
        if (relativePath.startsWith("/")) {
          relativePath = relativePath.substring(1);
        }

        if (relativePath.endsWith(CVS_REPOSITORY_FILE_POSTFIX)) {
          relativePath = relativePath.substring(0, relativePath.length() - CVS_REPOSITORY_FILE_POSTFIX.length());
        }

        //noinspection unchecked
        wrapper = new LogInformationWrapper(relativePath, log.getRevisionList(), log.getAllSymbolicNames());
      }
    }
    return wrapper;
  }
}
