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
package com.intellij.openapi.vcs.vfs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * author: lesya
 */
public class VcsVirtualFile extends AbstractVcsVirtualFile {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.vfs.VcsVirtualFile");

  private byte[] myContent;
  private final VcsFileRevision myFileRevision;
  private boolean myContentLoadFailed = false;

  public VcsVirtualFile(String path,
                        VcsFileRevision revision, VirtualFileSystem fileSystem) {
    super(path, fileSystem);
    myFileRevision = revision;
  }

  public VcsVirtualFile(String path,
                        byte[] content,
                        String revision, VirtualFileSystem fileSystem) {
    this(path, null, fileSystem);
    myContent = content;
    setRevision(revision);
  }

  @NotNull
  public byte[] contentsToByteArray() throws IOException {
    if (myContentLoadFailed || myProcessingBeforeContentsChange) {
      return ArrayUtil.EMPTY_BYTE_ARRAY;
    }
    if (myContent == null) {
      loadContent();
    }
    return myContent;
  }

  private void loadContent() throws IOException {
    if (myContent != null) return;
    
    final VcsFileSystem vcsFileSystem = ((VcsFileSystem)getFileSystem());

    try {
      myFileRevision.loadContent();
      fireBeforeContentsChange();

      myModificationStamp++;
      setRevision(myFileRevision.getRevisionNumber().asString());
      myContent = myFileRevision.getContent();
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          vcsFileSystem.fireContentsChanged(this, VcsVirtualFile.this, 0);
        }
      });

    }
    catch (VcsException e) {
      myContentLoadFailed = true;
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          vcsFileSystem.fireBeforeFileDeletion(this, VcsVirtualFile.this);
        }
      });
      myContent = ArrayUtil.EMPTY_BYTE_ARRAY;
      setRevision("0");

      Messages.showMessageDialog(
        VcsBundle.message("message.text.could.not.load.virtual.file.content", getPresentableUrl(), e.getLocalizedMessage()),
                                 VcsBundle.message("message.title.could.not.load.content"),
                                 Messages.getInformationIcon());

      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          vcsFileSystem.fireFileDeleted(this, VcsVirtualFile.this, getName(), getParent());
        }
      });

    }
    catch (ProcessCanceledException ex) {
      myContent = null;
    }

  }


  public boolean isDirectory() {
    return false;
  }

  public String getRevision() {
    if (myRevision == null) {
      try {
        loadContent();
      }
      catch (IOException e) {
        LOG.info(e);
      }
    }
    return myRevision;
  }
}
