/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl;

import com.intellij.openapi.diff.impl.util.TextDiffType;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.markup.LineMarkerRenderer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * <p>Draws the diff change highlighters on the editor's gutter.</p>
 * <p>Has ability to draw applied changes (used in the merge tool).</p>
 */
public class DiffLineMarkerRenderer implements LineMarkerRenderer {
  private final TextDiffType myDiffType;
  private final boolean myEnsureAtLeastOneLineHigh;

  /**
   * @param diffType                 the type of the difference.
   * @param ensureAtLeastOneLineHigh if true, the height of the rectangle will be at least one line high,
   *                                 if 0 is passed as a height of the Rectangle to {@link #paint(Editor, Graphics, Rectangle)}.
   *                                 The height won't be modified if ensureAtLeastOneLineHigh is false, or if height is not 0.
   */
  public DiffLineMarkerRenderer(@NotNull TextDiffType diffType, boolean ensureAtLeastOneLineHigh) {
    myDiffType = diffType;
    myEnsureAtLeastOneLineHigh = ensureAtLeastOneLineHigh;
  }

  @Override
  public void paint(Editor editor, Graphics g, Rectangle range) {
    Color color = myDiffType.getPolygonColor(editor);
    if (color == null) {
      return;
    }

    EditorGutterComponentEx gutter = ((EditorEx)editor).getGutterComponentEx();
    Graphics2D g2 = (Graphics2D)g;
    int x = 0;
    int y = range.y;
    int width = gutter.getWidth();
    int height = range.height;

    if (height == 0 && myEnsureAtLeastOneLineHigh) {
      height = editor.getLineHeight();
    }

    if (!myDiffType.isApplied()) {
      if (height > 2) {
        g.setColor(color);
        g.fillRect(x, y, width, height);
        UIUtil.drawFramingLines(g2, x, x + width, y - 1, y + height - 1, color.darker());
      }
      else {
        // insertion or deletion, when a range is null. matching the text highlighter which is a 2 pixel line
        DiffUtil.drawDoubleShadowedLine(g2, x, x + width, y - 1, color);
      }
    }
    else {
      DiffUtil.drawBoldDottedFramingLines(g2, x, x + width, y - 1, y + height - 1, color);
    }
  }
}
