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

package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.codeInsight.folding.CodeFoldingManager;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.util.ArrayList;
import java.io.IOException;


public class CopyPasteFoldingProcessor implements CopyPastePostProcessor {
  public TextBlockTransferableData collectTransferableData(final PsiFile file, final Editor editor, final int[] startOffsets, final int[] endOffsets) {
    // might be slow
    //CodeFoldingManager.getInstance(file.getManager().getProject()).updateFoldRegions(editor);

    final ArrayList<FoldingTransferableData.FoldingData> list = new ArrayList<FoldingTransferableData.FoldingData>();
    final FoldRegion[] regions = editor.getFoldingModel().getAllFoldRegions();
    for (final FoldRegion region : regions) {
      if (!region.isValid()) continue;
      for (int j = 0; j < startOffsets.length; j++) {
        if (startOffsets[j] <= region.getStartOffset() && region.getEndOffset() <= endOffsets[j]) {
          list.add(
            new FoldingTransferableData.FoldingData(
              region.getStartOffset() - startOffsets[j],
              region.getEndOffset() - startOffsets[j],
              region.isExpanded()
            )
          );
        }
      }
    }

    return new FoldingTransferableData(list.toArray(new FoldingTransferableData.FoldingData[list.size()]));
  }

  @Nullable
  public TextBlockTransferableData extractTransferableData(final Transferable content) {
    FoldingTransferableData foldingData = null;
    try {
      foldingData =
      (FoldingTransferableData)content.getTransferData(FoldingTransferableData.FoldingData.FLAVOR);
    }
    catch (UnsupportedFlavorException e) {
    }
    catch (IOException e) {
    }

    if (foldingData != null) { // copy to prevent changing of original by convertLineSeparators
      return foldingData.clone();
    }
    return null;
  }

  public void processTransferableData(final Project project, final Editor editor, final RangeMarker bounds, final TextBlockTransferableData value) {
    final CodeFoldingManager foldingManager = CodeFoldingManager.getInstance(project);
    foldingManager.updateFoldRegions(editor);

    Runnable processor1 = new Runnable() {
      public void run() {
        for (FoldingTransferableData.FoldingData data : ((FoldingTransferableData) value).getData()) {
          FoldRegion region = foldingManager.findFoldRegion(editor, data.startOffset + bounds.getStartOffset(), data.endOffset + bounds.getStartOffset());
          if (region != null) {
            region.setExpanded(data.isExpanded);
          }
        }
      }
    };
    editor.getFoldingModel().runBatchFoldingOperation(processor1);
  }
}
