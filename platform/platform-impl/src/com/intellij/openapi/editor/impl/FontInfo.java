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
package com.intellij.openapi.editor.impl;

import gnu.trove.TIntHashSet;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;

/**
 * @author max
 */
public class FontInfo {

  private final TIntHashSet mySymbolsToBreakDrawingIteration = new TIntHashSet();

  private final String myFamilyName;
  private final Font myFont;
  private final int mySize;
  @JdkConstants.FontStyle private final int myStyle;
  private final TIntHashSet mySafeCharacters = new TIntHashSet();
  private FontMetrics myFontMetrics = null;
  private final int[] charWidth = new int[128];
  private final boolean myHasGlyphsToBreakDrawingIteration;

  public FontInfo(final String familyName, final int size, @JdkConstants.FontStyle int style) {
    myFamilyName = familyName;
    mySize = size;
    myStyle = style;
    myFont = new Font(familyName, style, size);
    
    parseProblemGlyphs();
    myHasGlyphsToBreakDrawingIteration = !mySymbolsToBreakDrawingIteration.isEmpty();
  }
  
  private void parseProblemGlyphs() {
    BufferedImage buffer = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
    final Graphics graphics = buffer.getGraphics();
    if (!(graphics instanceof Graphics2D)) {
      return;
    }
    final FontRenderContext context = ((Graphics2D)graphics).getFontRenderContext();
    char[] charBuffer = new char[1];
    for (char c = 0; c < 128; c++) {
      if (!myFont.canDisplay(c)) {
        continue;
      }
      charBuffer[0] = c;
      final GlyphVector vector = myFont.createGlyphVector(context, charBuffer);
      final float y = vector.getGlyphMetrics(0).getAdvanceY();
      if (Math.round(y) != 0) {
        mySymbolsToBreakDrawingIteration.add(c);
      }
    }
  }

  /**
   * We've experienced a problem that particular symbols from particular font are represented really weird
   * by the IJ editor (IDEA-83645).
   * <p/>
   * Eventually it was found out that outline font glyphs can have a 'y advance', i.e. instruction on how the subsequent
   * glyphs location should be adjusted after painting the current glyph. In terms of java that means that such a problem
   * glyph should be the last symbol at the {@link Graphics#drawChars(char[], int, int, int, int) text drawing iteration}.
   * <p/>
   * Hopefully, such glyphs are exceptions from the normal processing, so, this method allows to answer whether a font
   * {@link #getFont() referenced} by the current object has such a glyph.
   * 
   * @return    true if the {@link #getFont() target font} has problem glyphs; <code>false</code> otherwise
   */
  public boolean hasGlyphsToBreakDrawingIteration() {
    return myHasGlyphsToBreakDrawingIteration;
  }

  /**
   * @return    unicode symbols which glyphs {@link #hasGlyphsToBreakDrawingIteration() have problems}
   * at the {@link #getFont() target font}.
   */
  @NotNull
  public TIntHashSet getSymbolsToBreakDrawingIteration() {
    return mySymbolsToBreakDrawingIteration;
  }

  public boolean canDisplay(char c) {
    try {
      if (c < 128) return true;
      if (mySafeCharacters.contains(c)) return true;
      if (myFont.canDisplay(c)) {
        mySafeCharacters.add(c);
        return true;
      }
      return false;
    }
    catch (Exception e) {
      // JRE has problems working with the font. Just skip.
      return false;
    }
  }

  public Font getFont() {
    return myFont;
  }

  public int charWidth(char c, JComponent anyComponent) {
    final FontMetrics metrics = fontMetrics(anyComponent);
    if (c < 128) return charWidth[c];
    return metrics.charWidth(c);
  }

  private FontMetrics fontMetrics(JComponent anyComponent) {
    if (myFontMetrics == null) {
      myFontMetrics = anyComponent.getFontMetrics(myFont);
      for (int i = 0; i < 128; i++) {
        charWidth[i] = myFontMetrics.charWidth(i);
      }
    }
    return myFontMetrics;
  }

  public int getSize() {
    return mySize;
  }

  @JdkConstants.FontStyle
  public int getStyle() {
    return myStyle;
  }
}
