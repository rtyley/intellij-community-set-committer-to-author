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
package com.intellij.openapi.editor.impl.softwrap.mapping;

import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.ex.SoftWrapModelEx;
import com.intellij.openapi.editor.impl.AbstractEditorProcessingOnDocumentModificationTest;
import com.intellij.openapi.editor.impl.SoftWrapModelImpl;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntProcedure;

import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 09/16/2010
 */
public class SoftWrapApplianceOnDocumentModificationTest extends AbstractEditorProcessingOnDocumentModificationTest {

  @Override
  protected void tearDown() throws Exception {
    myEditor.getSettings().setUseSoftWraps(false);
    super.tearDown();
  }

  public void testSoftWrapAdditionOnTyping() throws Exception {
    String text =
      "this is a test string that is expected to end just before right margin<caret>";
    init(800, text);

    int offset = myEditor.getDocument().getTextLength() + 1;
    assertTrue(getSoftWrapModel().getRegisteredSoftWraps().isEmpty());
    type(" thisisalongtokenthatisnotexpectedtobebrokenintopartsduringsoftwrapping");
    assertNotNull(myEditor.getSoftWrapModel().getSoftWrap(offset));
  }

  public void testLongLineOfIdSymbolsIsNotSoftWrapped() throws Exception {
    String text =
      "abcdefghijklmnopqrstuvwxyz<caret>\n" +
      "123\n" +
      "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    init(100, text);
    assertTrue(getSoftWrapModel().getRegisteredSoftWraps().isEmpty());
    type('1');
    assertTrue(getSoftWrapModel().getRegisteredSoftWraps().isEmpty());

    int offset = myEditor.getDocument().getText().indexOf("\n");
    type(" test");
    assertEquals(1, getSoftWrapModel().getRegisteredSoftWraps().size());
    assertNotNull(getSoftWrapModel().getSoftWrap(offset));
  }

  public void testFoldRegionCollapsing() throws Exception {
    String text =
      "class Test {\n" +
      "    public void foo() {\n" +
      "        System.out.println(\"test\");\n" +
      "    }\n" +
      "}";

    init(300, text);
    final FoldingModel foldingModel = myEditor.getFoldingModel();
    assertEmpty(foldingModel.getAllFoldRegions());

    final int startOffset = text.indexOf('{');
    final int endOffset = text.indexOf('}') + 1;

    VisualPosition foldStartPosition = myEditor.offsetToVisualPosition(startOffset);

    addFoldRegion(startOffset, endOffset, "...");

    final FoldRegion foldRegion = getFoldRegion(startOffset);
    assertNotNull(foldRegion);
    assertTrue(foldRegion.isExpanded());
    toggleFoldRegionState(foldRegion, false);

    // Expecting that all offsets that belong to collapsed fold region point to the region's start.
    assertEquals(foldStartPosition, myEditor.offsetToVisualPosition(startOffset + 5));
  }

  public void testTypingEnterAtDocumentEnd() throws IOException {
    String text =
      "class Test {\n" +
      "    public void foo() {\n" +
      "        System.out.println(\"test\");\n" +
      "    }\n" +
      "}<caret>";

    init(300, text);
    type('\n');
    VisualPosition position = myEditor.getCaretModel().getVisualPosition();
    assertEquals(new VisualPosition(5, 0), position);
  }

  public void testDeleteDocumentTail() throws IOException {
    String text =
      "class Test {\n" +
      "    public void foo() {\n" +
      "        System.out.println(\"test\");\n" +
      "    }\n" +
      "}\n" +
      "abcde";

    init(300, text);
    int offset = text.indexOf("abcde");
    myEditor.getSelectionModel().setSelection(offset, text.length());
    delete();
    assertEquals(new VisualPosition(5, 0), myEditor.getCaretModel().getVisualPosition());
  }

  public void testTypingTabOnLastEmptyLine() throws IOException {
    String text =
      "class Test {\n" +
      "}\n" +
      "<caret>";

    init(300, text);
    type('\t');
    assertEquals(new VisualPosition(2, 4), myEditor.getCaretModel().getVisualPosition());
  }

  public void testTrailingFoldRegionRemoval() throws IOException {
    String text =
      "public class BrokenAlignment {\n" +
      "    @SuppressWarnings({ \"SomeInspectionIWantToIgnore\" })\n" +
      "    public void doSomething(int x, int y) {\n" +
      "    }\n" +
      "}";

    init(700, text);

    int startFoldOffset = text.indexOf('@');
    int endFoldOffset = text.indexOf(')');
    addCollapsedFoldRegion(startFoldOffset, endFoldOffset, "/SomeInspectionIWantToIgnore/");

    int endSelectionOffset = text.lastIndexOf("}\n") + 1;
    myEditor.getSelectionModel().setSelection(startFoldOffset, endSelectionOffset);

    delete();
    // Don't expect any exceptions here.
  }

  public void testTypeNewLastLineAndSymbolOnIt() throws IOException {
    // Inspired by IDEA-59439
    String text = 
      "This is a test document\n" +
      "line1\n" +
      "line2\n" +
      "line3\n" +
      "line4\n" +
      "line5\n" +
      "line6<caret>";
    
    init(700, text);
    type("\nq");
    assertEquals(new VisualPosition(7, 1), myEditor.offsetToVisualPosition(myEditor.getDocument().getTextLength()));
  }
  
  public void testTrailingSoftWrapOffsetShiftOnTyping() throws IOException {
    // The main idea is to type on a logical line before soft wrap in order to ensure that its offset is correctly shifted back.
    String text = 
      "line1<caret>\n" +
      "second line that is long enough to be soft wrapped";
    init(100, text);

    TIntHashSet offsetsBefore = collectSoftWrapStartOffsets(1);
    assertTrue(!offsetsBefore.isEmpty());
    
    type('2');
    final TIntHashSet offsetsAfter = collectSoftWrapStartOffsets(1);
    assertSame(offsetsBefore.size(), offsetsAfter.size());
    offsetsBefore.forEach(new TIntProcedure() {
      @Override
      public boolean execute(int value) {
        assertTrue(offsetsAfter.contains(value + 1));
        return true;
      }
    });
  }
  
  public void testSoftWrapAwareMappingAfterLeadingFoldRegionCollapsing() throws IOException {
    String text =
      "line to fold 1\n" +
      "line to fold 2\n" +
      "line to fold 3\n" +
      "ordinary line 1\n" +
      "ordinary line 2\n" +
      "ordinary line 3\n" +
      "ordinary line 4\n" +
      "line that is long enough to be soft wrapped\n" +
      "ordinary line 5\n" +
      "ordinary line 6\n" +
      "ordinary line 7\n" +
      "ordinary line 8\n";
    
    init(200, text);
    LogicalPosition position = myEditor.visualToLogicalPosition(new VisualPosition(8, 0));
    assertSame(7, position.line); // Position from soft-wrapped part of the line
    
    addCollapsedFoldRegion(0, text.indexOf("ordinary line 1") - 1, "...");
    assertSame(7, myEditor.visualToLogicalPosition(new VisualPosition(6, 0)).line); // Check that soft wraps cache is correctly updated
  }
  
  public void testCaretPositionOnFoldRegionExpand() throws IOException {
    // We had a problem that caret preserved its visual position instead of offset. This test checks that.
    
    String text = 
      "/**\n" +
      " * This is a test comment\n" +
      " */\n" +
      "public class Test {\n" +
      "}";
    init(500, text);

    addCollapsedFoldRegion(0, text.indexOf("public") - 1, "/**...*/");
    
    int offset = text.indexOf("class");
    CaretModel caretModel = myEditor.getCaretModel();
    caretModel.moveToOffset(offset);
    assertEquals(offset, caretModel.getOffset());
    assertEquals(1, caretModel.getVisualPosition().line);
    
    toggleFoldRegionState(getFoldRegion(0), true);
    assertEquals(3, caretModel.getVisualPosition().line);
    assertEquals(offset, caretModel.getOffset());
  }
  
  public void testBackspaceAtTheEndOfSoftWrappedLine() throws IOException {
    // There was a problem that removing text from the last document line that was soft-wrapped removed soft wraps as well.
    String text = 
      "This a long string that is expected to be wrapped in more than one visual line<caret>";
    init(150, text);

    List<? extends SoftWrap> softWrapsBeforeModification = new ArrayList<SoftWrap>(getSoftWrapModel().getRegisteredSoftWraps());
    assertTrue(softWrapsBeforeModification.size() > 0);
    
    backspace();
    assertEquals(softWrapsBeforeModification, getSoftWrapModel().getRegisteredSoftWraps());
  }
  
  public void testRemoveOfAllSymbolsFromLastLine() throws IOException {
    // There was a problem that removing all text from the last document line corrupted soft wraps cache.
    String text =
      "Line1\n" +
      "Long line2 that is expected to be soft-wrapped<caret>";
    init(150, text);

    List<? extends SoftWrap> softWrapsBeforeModification = new ArrayList<SoftWrap>(getSoftWrapModel().getRegisteredSoftWraps());
    assertTrue(softWrapsBeforeModification.size() > 0);

    int offset = myEditor.getCaretModel().getOffset();
    VisualPosition positionBeforeModification = myEditor.offsetToVisualPosition(offset);
    type("\n123");
    myEditor.getSelectionModel().setSelection(offset + 1, myEditor.getDocument().getTextLength());
    delete();
    assertEquals(softWrapsBeforeModification, getSoftWrapModel().getRegisteredSoftWraps());
    assertEquals(positionBeforeModification, myEditor.offsetToVisualPosition(offset));
  }
  
  private static TIntHashSet collectSoftWrapStartOffsets(int documentLine) {
    TIntHashSet result = new TIntHashSet();
    for (SoftWrap softWrap : myEditor.getSoftWrapModel().getSoftWrapsForLine(documentLine)) {
      result.add(softWrap.getStart());
    }
    return result;
  }
  
  private void init(final int visibleWidth, String fileText) throws IOException {
    init(fileText);
    myEditor.getSettings().setUseSoftWraps(true);
    SoftWrapModelImpl model = (SoftWrapModelImpl)myEditor.getSoftWrapModel();
    model.refreshSettings();

    SoftWrapApplianceManager applianceManager = model.getApplianceManager();
    applianceManager.setWidthProvider(new SoftWrapApplianceManager.VisibleAreaWidthProvider() {
      @Override
      public int getVisibleAreaWidth() {
        return visibleWidth;
      }
    });
    applianceManager.registerSoftWrapIfNecessary(new Rectangle(visibleWidth, visibleWidth * 2), 0);
  }

  private static SoftWrapModelEx getSoftWrapModel() {
    return (SoftWrapModelEx)myEditor.getSoftWrapModel();
  }
}
