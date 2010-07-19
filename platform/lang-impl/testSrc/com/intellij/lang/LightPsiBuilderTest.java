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
package com.intellij.lang;

import com.intellij.lang.impl.PsiBuilderImpl;
import com.intellij.lexer.LexerBase;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.junit.Test;

import static org.junit.Assert.assertEquals;


public class LightPsiBuilderTest {
  private static final IElementType ROOT = new IElementType("ROOT", Language.ANY);
  private static final IElementType LETTER = new IElementType("LETTER", Language.ANY);
  private static final IElementType DIGIT = new IElementType("DIGIT", Language.ANY);
  private static final IElementType OTHER = new IElementType("OTHER", Language.ANY);
  private static final IElementType COLLAPSED = new IElementType("COLLAPSED", Language.ANY);

  @Test
  public void testPlain() {
    doTest("a<<b",
           new Parser() {
             public void parse(PsiBuilder builder) {
               while (builder.getTokenType() != null) {
                 builder.advanceLexer();
               }
             }
           },
           "Element(ROOT)\n" +
           "  PsiElement(LETTER)('a')\n" +
           "  PsiElement(OTHER)('<')\n" +
           "  PsiElement(OTHER)('<')\n" +
           "  PsiElement(LETTER)('b')\n"
    );
  }

  @Test
  public void testCollapse() {
    doTest("a<<b",
           new Parser() {
             public void parse(PsiBuilder builder) {
               PsiBuilder.Marker inner = null;
               while (builder.getTokenType() != null) {
                 if (builder.getTokenType() == OTHER && inner == null) inner = builder.mark();
                 builder.advanceLexer();
                 if (builder.getTokenType() != OTHER && inner != null) { inner.collapse(COLLAPSED); inner = null; }
               }
             }
           },
           "Element(ROOT)\n" +
           "  PsiElement(LETTER)('a')\n" +
           "  PsiElement(COLLAPSED)('<<')\n" +
           "  PsiElement(LETTER)('b')\n"
    );
  }


  private interface Parser {
    void parse(PsiBuilder builder);
  }

  private static void doTest(final String text, final Parser parser, final String expected) {
    final PsiBuilder builder = new PsiBuilderImpl(new MyTestLexer(), TokenSet.EMPTY, TokenSet.EMPTY, text);
    final PsiBuilder.Marker rootMarker = builder.mark();
    parser.parse(builder);
    rootMarker.done(ROOT);
    final ASTNode root = builder.getTreeBuilt();
    assertEquals(expected, DebugUtil.nodeTreeToString(root, true));
  }

  private static class MyTestLexer extends LexerBase {
    private CharSequence myBuffer = "";
    private int myIndex = 0;
    private int myBufferEnd = 1;

    public void start(CharSequence buffer, int startOffset, int endOffset, int initialState) {
      myBuffer = buffer.subSequence(startOffset, endOffset);
      myIndex = 0;
      myBufferEnd = myBuffer.length();
    }

    public int getState() {
      return 0;
    }

    public IElementType getTokenType() {
      if (myIndex >= myBufferEnd) {
        return null;
      }
      else if (Character.isDigit(myBuffer.charAt(myIndex))) {
        return DIGIT;
      }
      else if (Character.isLetter(myBuffer.charAt(myIndex))) {
        return LETTER;
      }
      else {
        return OTHER;
      }
    }

    public int getTokenStart() {
      return myIndex;
    }

    public int getTokenEnd() {
      return myIndex + 1;
    }

    public void advance() {
      if (myIndex < myBufferEnd) myIndex++;
    }

    public CharSequence getBufferSequence() {
      return myBuffer;
    }

    public int getBufferEnd() {
      return myBufferEnd;
    }
  }
}
