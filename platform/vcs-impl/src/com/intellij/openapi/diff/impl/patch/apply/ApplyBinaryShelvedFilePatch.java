/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.patch.apply;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;

public class ApplyBinaryShelvedFilePatch extends ApplyFilePatchBase<ShelveChangesManager.ShelvedBinaryFilePatch> {
  public ApplyBinaryShelvedFilePatch(ShelveChangesManager.ShelvedBinaryFilePatch patch) {
    super(patch);
  }

  // todo check the case!!!
  // todo check the case!!!
  // todo check the case!!!
  // todo check the case!!!
  // todo check the case!!!
  // todo check the case!!!
  @Override
  protected Result applyChange(Project project, VirtualFile fileToPatch, FilePath pathBeforeRename, Getter<CharSequence> baseContents) throws IOException {
    return null;
  }

  @Override
  protected void applyCreate(VirtualFile newFile) throws IOException {
  }
}
