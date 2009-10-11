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
package com.intellij.cvsSupport2.history;

import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.history.VcsFileContent;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.CvsBundle;

public abstract class CvsFileContent implements VcsFileContent{
  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.history.CvsFileContent");
  protected final ComparableVcsRevisionOnOperation myComparableCvsRevisionOnOperation;

  protected CvsFileContent(final ComparableVcsRevisionOnOperation comparableCvsRevisionOnOperation) {
    myComparableCvsRevisionOnOperation = comparableCvsRevisionOnOperation;
  }

  public boolean isDeleted() {
    return myComparableCvsRevisionOnOperation.isDeleted();
  }

  public boolean isLoaded() {
    return myComparableCvsRevisionOnOperation.isLoaded();
  }

  public byte[] getContent() {
    LOG.assertTrue(isLoaded());
    return myComparableCvsRevisionOnOperation.getContent();
  }

  public abstract VcsRevisionNumber getRevisionNumber();

  public void loadContent() throws VcsException {
    myComparableCvsRevisionOnOperation.loadContent();
    if (!isLoaded()) {
      throw new VcsException(CvsBundle.message("exception.text.cannot.load.revision", getRevisionNumber()));
    }
    if (fileNotFound()) {
      throw new VcsException(CvsBundle.message("exception.text.cannot.find.revision", getRevisionNumber()));
    }

    if (isDeleted()) {
      throw new VcsException(CvsBundle.message("message.text.revision.was.deleted.from.repository", getRevisionNumber()));
    }
  }

  public boolean fileNotFound() {
    return myComparableCvsRevisionOnOperation.fileNotFound();
  }
}
