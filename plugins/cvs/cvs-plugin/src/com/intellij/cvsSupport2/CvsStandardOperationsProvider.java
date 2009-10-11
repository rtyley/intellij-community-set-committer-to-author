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
package com.intellij.cvsSupport2;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.cvsoperations.common.RepositoryModificationOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsContent.GetFileContentOperation;
import com.intellij.cvsSupport2.errorHandling.CannotFindCvsRootException;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import org.netbeans.lib.cvsclient.command.KeywordSubstitution;

import java.io.File;
import java.util.Map;

public class CvsStandardOperationsProvider {

  private RepositoryModificationOperation myCurrentTransaction;
  private final Project myProject;

  public CvsStandardOperationsProvider(Project project) {
    myProject = project;
  }

  public void checkinFile(String path, Object parameters, Map userData) throws VcsException {
    getCurrentTransaction().commitFile(path);
  }

  public void addFile(String folderPath, String name, Object parameters, Map userData) throws VcsException {
    KeywordSubstitution substitution = null;
    if (parameters instanceof KeywordSubstitution) {
      substitution = (KeywordSubstitution)parameters;
    }
    getCurrentTransaction().addFile(folderPath, name, substitution);
  }

  public void removeFile(String path, Object parameters, Map userData) throws VcsException {
    getCurrentTransaction().removeFile(path);
  }

  public void addDirectory(String parentPath, String name, Object parameters, Map userData) throws VcsException {
    addFile(parentPath, name, parameters, userData);
  }

  public void removeDirectory(String path, Object parameters, Map userData) throws VcsException {
  }

  private RepositoryModificationOperation getCurrentTransaction() {
    if (myCurrentTransaction == null) createTransaction();
    return myCurrentTransaction;
  }

  public void createTransaction() {
    myCurrentTransaction =
    RepositoryModificationOperation.createGlobalTransactionOperation(null, CvsConfiguration.getInstance(myProject));
  }

  public int getFilesToProcessCount() {
    return getCurrentTransaction().getFilesToProcessCount();
  }

  public void commit(Object parameters) throws VcsException {
    getCurrentTransaction().setMessage(parameters);
    CvsVcs2.executeOperation(CvsBundle.message("operation.name.commit.changes"), getCurrentTransaction(), myProject);
    myCurrentTransaction = null;
  }

  public void rollback() {
    myCurrentTransaction = null;
  }

  public byte[] getFileContent(String path) throws VcsException {
    try {
      GetFileContentOperation command = GetFileContentOperation.createForFile(CvsVfsUtil.findFileByIoFile(new File(path)));
      CvsVcs2.executeOperation(CvsBundle.message("operation.name.get.file.content"), command, myProject);
      return command.getFileBytes();
    }
    catch (CannotFindCvsRootException cannotFindCvsRootException) {
      throw new VcsException(cannotFindCvsRootException);
    }
  }
}
