package com.intellij.openapi.editor;

import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.testFramework.LightPlatformTestCase;
import org.jetbrains.annotations.NonNls;

/**
 * @author max
 */
public class FoldingTest extends LightPlatformTestCase {
  public void testStressFoldingFromZeroOffset() throws Exception {
    for (int len = 2; len < 25; len++) {
      stress(len);
    }
  }

  public void testStress8() throws Exception {
    DocumentImpl doc = new DocumentImpl("0123456789\n123456789\n23456789");
    Editor editor = EditorFactory.getInstance().createEditor(doc);
    try {
      final FoldingModel model = editor.getFoldingModel();
      model.runBatchFoldingOperation(new Runnable() {
        @Override
        public void run() {
          model.addFoldRegion(0, 8, "/*...*/").setExpanded(false);
          model.addFoldRegion(10, 12, "/*...*/").setExpanded(false);
        }
      });

      assertEquals(10, editor.logicalPositionToOffset(new LogicalPosition(0, 10)));

      for (int line = 0; line <= 3; line++) {
        for (int column = 0; column <= 100; column++) {
          LogicalPosition log = new LogicalPosition(line, column);
          editor.logicalToVisualPosition(log);
        }
      }
    }
    finally {
      EditorFactory.getInstance().releaseEditor(editor);
    }
  }

  private static void stress(final int len) {
    DocumentImpl doc = new DocumentImpl("0123456789\n123456789\n23456789");
    Editor editor = EditorFactory.getInstance().createEditor(doc);
    try {
      final FoldingModel model = editor.getFoldingModel();
      model.runBatchFoldingOperation(new Runnable() {
        @Override
        public void run() {
          model.addFoldRegion(0, len, "/*...*/").setExpanded(false);
          model.addFoldRegion(len + 2, len + 4, "/*...*/").setExpanded(false);
        }
      });

      for (int line = 0; line <= 3; line++) {
        for (int column = 0; column <= 100; column++) {
          LogicalPosition log = new LogicalPosition(line, column);
          editor.logicalToVisualPosition(log);
        }
      }
    }
    finally {
      EditorFactory.getInstance().releaseEditor(editor);
    }
  }

  public void testIntersects () throws Exception {
    @NonNls DocumentImpl doc = new DocumentImpl("I don't know what you mean by `glory,'\" Alice said" +
     "Humpty Dumpty smiled contemptuously. \"Of course you don't -- till I tell you. I meant `there's a nice knock-down argument for you!'" +
     "But glory doesn't mean `a nice knock-down argument,'\" Alice objected." +
     "When I use a word,\" Humpty Dumpty said, in a rather scornful tone, \"it means just what I choose it to mean -- neither more nor less." +
     "The question is,\" said Alice, \"whether you can make words mean so many different things." +
     "The question is,\" said Humpty Dumpty, \"which is to be master -- that's all." );
    Editor editor = EditorFactory.getInstance().createEditor(doc);
    try {
      final FoldingModel model = editor.getFoldingModel();

      model.runBatchFoldingOperation(new Runnable() {
        @Override
        public void run() {
          FoldRegion region = model.addFoldRegion(5, 10, ".");
          assertNotNull(region);
          region = model.addFoldRegion(7, 11, ".");
          assertNull(region);
          region = model.addFoldRegion(20, 30, ".");
          assertNotNull(region);
          region = model.addFoldRegion(9, 12, ".");
          assertNull(region);
          region = model.addFoldRegion(7, 10, ".");
          assertNotNull(region);
          region = model.addFoldRegion(7, 10, ".");
          assertNull(region);
          region = model.addFoldRegion(5, 30, ".");
          assertNotNull(region);
        }
      });
    }
    finally {
      EditorFactory.getInstance().releaseEditor(editor);
    }
  }
}
