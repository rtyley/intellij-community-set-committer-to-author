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
package com.intellij.openapi.vcs.history;

import com.intellij.util.ArrayUtil;

import java.util.Date;

public interface VcsFileRevision extends VcsFileContent {
  VcsFileRevision NULL = new VcsFileRevision() {
    public VcsRevisionNumber getRevisionNumber() {
      return VcsRevisionNumber.NULL;
    }

    public Date getRevisionDate() {
      return new Date();
    }

    public String getAuthor() {
      return "";
    }

    public String getCommitMessage() {
      return "";
    }

    public String getBranchName() {
      return null;
    }

    public void loadContent(){
    }

    public byte[] getContent(){
      return ArrayUtil.EMPTY_BYTE_ARRAY;
    }

    public int compareTo(VcsFileRevision vcsFileRevision) {
      return 0;
    }
  };

  VcsRevisionNumber getRevisionNumber();
  String getBranchName();
  Date getRevisionDate();
  String getAuthor();
  String getCommitMessage();

}
