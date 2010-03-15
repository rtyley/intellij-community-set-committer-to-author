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

import com.intellij.openapi.diff.impl.patch.ApplyPatchException;
import com.intellij.openapi.diff.impl.patch.ApplyPatchStatus;
import com.intellij.openapi.diff.impl.patch.PatchHunk;
import com.intellij.openapi.diff.impl.patch.TextFilePatch;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ApplyTextFilePatch extends ApplyFilePatchBase<TextFilePatch> {
  public ApplyTextFilePatch(final TextFilePatch patch) {
    super(patch);
  }

  @Nullable
  protected ApplyPatchStatus applyChange(final VirtualFile fileToPatch) throws IOException, ApplyPatchException {
    byte[] fileContents = fileToPatch.contentsToByteArray();
    CharSequence text = LoadTextUtil.getTextByBinaryPresentation(fileContents, fileToPatch);
    StringBuilder newText = new StringBuilder();
    ApplyPatchStatus status = applyModifications(text, newText);
    if (status != ApplyPatchStatus.ALREADY_APPLIED) {
      final Document document = FileDocumentManager.getInstance().getDocument(fileToPatch);
      if (document == null) {
        throw new ApplyPatchException("Failed to set contents for updated file " + fileToPatch.getPath());
      }
      document.setText(newText.toString());
      FileDocumentManager.getInstance().saveDocument(document);
    }
    return status;
  }

  // todo taken to another place also, cheeeeeck
  @Nullable
  public ApplyPatchStatus applyModifications(final CharSequence text, final StringBuilder newText) throws ApplyPatchException {
    final List<PatchHunk> hunks = myPatch.getHunks();
    if (hunks.size() == 0) {
      return ApplyPatchStatus.SUCCESS;
    }
    List<String> lines = new ArrayList<String>();
    Collections.addAll(lines, LineTokenizer.tokenize(text, false));
    ApplyPatchStatus result = null;
    for(PatchHunk hunk: hunks) {
      result = ApplyPatchStatus.and(result, new ApplyPatchHunk(hunk).apply(lines));
    }
    for(int i=0; i<lines.size(); i++) {
      newText.append(lines.get(i));
      if (i < lines.size()-1 || !hunks.get(hunks.size()-1).isNoNewLineAtEnd()) {
        newText.append("\n");
      }
    }
    return result;
  }

  protected void applyCreate(final VirtualFile newFile) throws IOException, ApplyPatchException {
    final Document document = FileDocumentManager.getInstance().getDocument(newFile);
    if (document == null) {
      throw new ApplyPatchException("Failed to set contents for new file " + newFile.getPath());
    }
    document.setText(myPatch.getNewFileText());
    FileDocumentManager.getInstance().saveDocument(document);
  }
}
