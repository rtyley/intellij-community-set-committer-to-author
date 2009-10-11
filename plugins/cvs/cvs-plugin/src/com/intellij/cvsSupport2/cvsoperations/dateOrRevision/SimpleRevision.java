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
package com.intellij.cvsSupport2.cvsoperations.dateOrRevision;

import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.history.CvsRevisionNumber;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.command.Command;


/**
 * author: lesya
 */
public class SimpleRevision implements RevisionOrDate {
  private final String myRevision;

  public static SimpleRevision createForTheSameVersionOf(@NotNull VirtualFile file) {
    Entry entry = CvsEntriesManager.getInstance().getEntryFor(file.getParent(), file.getName());
    return new SimpleRevision(entry.getRevision());

  }

  public SimpleRevision(String revision) {
    myRevision = prepareRevision(revision);
  }

  private String prepareRevision(String revision) {
    if (revision == null) {
      return null;
    }
    else if (StringUtil.startsWithChar(revision, '-')) {
      return revision.substring(1);
    }
    else {
      return revision;
    }
  }

  public String getRevision() {
    return myRevision;
  }

  public void setForCommand(Command command) {
    new CommandWrapper(command).setUpdateByRevisionOrDate(myRevision, null);
  }

  public CvsRevisionNumber getCvsRevisionNumber() {
    if (myRevision == null) return null;
    try {
      return new CvsRevisionNumber(myRevision);
    }
    catch (NumberFormatException ex) {
      return null;
    }
  }

}
