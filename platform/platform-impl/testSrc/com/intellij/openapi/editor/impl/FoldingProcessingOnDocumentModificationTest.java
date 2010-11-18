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
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.FoldRegion;

import java.io.IOException;

/**
 * @author Denis Zhdanov
 * @since 11/18/10 7:42 PM
 */
public class FoldingProcessingOnDocumentModificationTest extends AbstractEditorProcessingOnDocumentModificationTest {
  
  public void testUnexpectedClassLevelJavadocExpandingOnClassSignatureChange() throws IOException {
    // Inspired by IDEA-61275

    String text =
      "/**\n" +
      " * This is a test comment\n" +
      " */\n" +
      "public <caret>class Test {\n" +
      "}";
    init(text);

    CaretModel caretModel = myEditor.getCaretModel();
    int caretOffset = caretModel.getOffset();
    
    addCollapsedFoldRegion(0, text.indexOf("public") - 1, "/***/");
    assertEquals(caretOffset, caretModel.getOffset());
    
    type('a');

    assertEquals(caretOffset + 1, caretModel.getOffset());
    assertEquals(1, myEditor.getFoldingModel().getAllFoldRegions().length);
    FoldRegion foldRegion = getFoldRegion(0);
    assertFalse(foldRegion.isExpanded());
  }
}
