package com.intellij.ui.border;

import com.intellij.util.xmlb.annotations.Text;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;

/**
 * @author evgeny zakrevsky
 */
public class IdeaTitledBorder extends TitledBorder {

  // Space between the border and the component's edge
  private int EDGE_SPACING = 0;

  // Space between the border and text
  private int TEXT_SPACING = 2;

  // Horizontal inset of text that is left or right justified
  private int TEXT_INSET_H = 4;

  private static final int SEPARATOR_RIGHT_SPACING = 5;

  //private static final int TITLE_LEFT_SPACING = 5;
  //static private final int DARKNESS = 10;

  public IdeaTitledBorder(String title, Font font, Color borderColor, int indent, int borderWidth, Insets insets) {
    super(title + "  ");

    Insets insideInsets = new Insets(0, 0, 0, 0);
    Insets outsideInsets = new Insets(0, 0, 0, 0);

    //applying text inset
    insideInsets.left -= TEXT_INSET_H;
    //outsideInsets.left += TEXT_INSET_H;

    //applying indent
    TEXT_INSET_H -= indent;
    insideInsets.left += indent;


    //applying separator right spacing
    insideInsets.right -= SEPARATOR_RIGHT_SPACING;
    outsideInsets.right += SEPARATOR_RIGHT_SPACING;

    //applying insets
    insideInsets.top += insets.top / 2;
    TEXT_SPACING += insets.top / 2;
    outsideInsets.top += insets.top / 2;
    outsideInsets.left += insets.left;
    outsideInsets.bottom += insets.bottom;
    outsideInsets.right += insets.right;

    this.titleFont = font;
    this.titleJustification = LEADING;
    this.titlePosition = DEFAULT_POSITION;
    this.border = BorderFactory.createCompoundBorder(new EmptyBorder(outsideInsets),
                                                     BorderFactory.createCompoundBorder(
                                                       BorderFactory.createMatteBorder(borderWidth, 0, 0, 0, borderColor),
                                                       new EmptyBorder(insideInsets)));
  }

  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {

    g.setPaintMode();

    Point textLoc = new Point();
    Border border = getBorder();

    if (getTitle() == null || getTitle().isEmpty()) {
      if (border != null) {
        border.paintBorder(c, g, x, y, width, height);
      }
      return;
    }

    Rectangle grooveRect = new Rectangle(x + EDGE_SPACING, y + EDGE_SPACING,
                                         width - (EDGE_SPACING * 2),
                                         height - (EDGE_SPACING * 2));
    Font font = g.getFont();
    Color color = g.getColor();

    g.setFont(getFont(c));

    JComponent jc = (c instanceof JComponent) ? (JComponent)c : null;
    FontMetrics fm = SwingUtilities2.getFontMetrics(jc, g);
    int fontHeight = fm.getHeight();
    int descent = fm.getDescent();
    int ascent = fm.getAscent();
    int diff;
    int stringWidth = SwingUtilities2.stringWidth(jc, fm,
                                                  getTitle());
    Insets insets;

    if (border != null) {
      insets = border.getBorderInsets(c);
    }
    else {
      insets = new Insets(0, 0, 0, 0);
    }

    int titlePos = getTitlePosition();
    switch (titlePos) {
      case ABOVE_TOP:
        diff = ascent + descent + (Math.max(EDGE_SPACING,
                                            TEXT_SPACING * 2) - EDGE_SPACING);
        grooveRect.y += diff;
        grooveRect.height -= diff;
        textLoc.y = grooveRect.y - (descent + TEXT_SPACING);
        break;
      case TOP:
      case DEFAULT_POSITION:
        diff = Math.max(0, ((ascent / 2) + TEXT_SPACING) - EDGE_SPACING);
        grooveRect.y += diff;
        grooveRect.height -= diff;
        textLoc.y = (grooveRect.y - descent) +
                    (insets.top + ascent + descent) / 2;
        break;
      case BELOW_TOP:
        textLoc.y = grooveRect.y + insets.top + ascent + TEXT_SPACING;
        break;
      case ABOVE_BOTTOM:
        textLoc.y = (grooveRect.y + grooveRect.height) -
                    (insets.bottom + descent + TEXT_SPACING);
        break;
      case BOTTOM:
        grooveRect.height -= fontHeight / 2;
        textLoc.y = ((grooveRect.y + grooveRect.height) - descent) +
                    ((ascent + descent) - insets.bottom) / 2;
        break;
      case BELOW_BOTTOM:
        grooveRect.height -= fontHeight;
        textLoc.y = grooveRect.y + grooveRect.height + ascent +
                    TEXT_SPACING;
        break;
    }

    int justification = getTitleJustification();
    if (c.getComponentOrientation().isLeftToRight()) {
      if (justification == LEADING ||
          justification == DEFAULT_JUSTIFICATION) {
        justification = LEFT;
      }
      else if (justification == TRAILING) {
        justification = RIGHT;
      }
    }
    else {
      if (justification == LEADING ||
          justification == DEFAULT_JUSTIFICATION) {
        justification = RIGHT;
      }
      else if (justification == TRAILING) {
        justification = LEFT;
      }
    }

    switch (justification) {
      case LEFT:
        textLoc.x = grooveRect.x + TEXT_INSET_H + insets.left;
        break;
      case RIGHT:
        textLoc.x = (grooveRect.x + grooveRect.width) -
                    (stringWidth + TEXT_INSET_H + insets.right);
        break;
      case CENTER:
        textLoc.x = grooveRect.x +
                    ((grooveRect.width - stringWidth) / 2);
        break;
    }

    // If title is positioned in middle of border AND its fontsize
    // is greater than the border's thickness, we'll need to paint
    // the border in sections to leave space for the component's background
    // to show through the title.
    //
    if (border != null) {
      //if (((titlePos == TOP || titlePos == DEFAULT_POSITION) &&
      //     (grooveRect.y > textLoc.y - ascent)) ||
      //    (titlePos == BOTTOM &&
      //     (grooveRect.y + grooveRect.height < textLoc.y + descent))) {

      Rectangle clipRect = new Rectangle();

      // save original clip
      Rectangle saveClip = g.getClipBounds();

      // paint strip left of text
      clipRect.setBounds(saveClip);
      if (computeIntersection(clipRect, x, y, textLoc.x - 1 - x, height)) {
        g.setClip(clipRect);
        border.paintBorder(c, g, grooveRect.x, grooveRect.y,
                           grooveRect.width, grooveRect.height);
      }

      // paint strip right of text
      clipRect.setBounds(saveClip);
      if (computeIntersection(clipRect, textLoc.x + stringWidth + 1, y,
                              x + width - (textLoc.x + stringWidth + 1), height)) {
        g.setClip(clipRect);
        border.paintBorder(c, g, grooveRect.x, grooveRect.y,
                           grooveRect.width, grooveRect.height);
      }

      if (titlePos == TOP || titlePos == DEFAULT_POSITION) {
        // paint strip below text
        clipRect.setBounds(saveClip);
        if (computeIntersection(clipRect, textLoc.x - 1, textLoc.y + descent,
                                stringWidth + 2, y + height - textLoc.y - descent)) {
          g.setClip(clipRect);
          border.paintBorder(c, g, grooveRect.x, grooveRect.y,
                             grooveRect.width, grooveRect.height);
        }
      }
      else { // titlePos == BOTTOM
        // paint strip above text
        clipRect.setBounds(saveClip);
        if (computeIntersection(clipRect, textLoc.x - 1, y,
                                stringWidth + 2, textLoc.y - ascent - y)) {
          g.setClip(clipRect);
          border.paintBorder(c, g, grooveRect.x, grooveRect.y,
                             grooveRect.width, grooveRect.height);
        }
      }

      // restore clip
      g.setClip(saveClip);
      //}
      //else {
      //  border.paintBorder(c, g, grooveRect.x, grooveRect.y,
      //                     grooveRect.width, grooveRect.height);
      //}
    }

    //Color newBackground =
    //  new Color(c.getBackground().getRed() - DARKNESS, c.getBackground().getGreen() - DARKNESS, c.getBackground().getBlue() - DARKNESS);
    //g.setColor(newBackground);
    //g.fillRoundRect(grooveRect.x, grooveRect.y, grooveRect.width, grooveRect.height - 15, 5, 5);
    //g.setColor(color);
    //if (jc != null) {
    //  for (Component comp : jc.getComponents()) {
    //    coloringRecursive(comp, newBackground, c.getBackground());
    //  }
    //}


    g.setColor(getTitleColor());
    SwingUtilities2.drawString(jc, g, getTitle(), textLoc.x, textLoc.y);

    g.setFont(font);
    g.setColor(color);
  }

  public void acceptMinimumSize(Component c) {
    Dimension minimumSize = getMinimumSize(c);
    c.setMinimumSize(new Dimension(Math.max(minimumSize.width, c.getMinimumSize().width),
                                   Math.max(minimumSize.height, c.getMinimumSize().height)));
    c.setPreferredSize(new Dimension(Math.max(c.getPreferredSize().width, c.getMinimumSize().width),
                                     Math.max(c.getPreferredSize().height, c.getMinimumSize().height)));
  }

  private static boolean computeIntersection(Rectangle dest,
                                             int rx, int ry, int rw, int rh) {
    int x1 = Math.max(rx, dest.x);
    int x2 = Math.min(rx + rw, dest.x + dest.width);
    int y1 = Math.max(ry, dest.y);
    int y2 = Math.min(ry + rh, dest.y + dest.height);
    dest.x = x1;
    dest.y = y1;
    dest.width = x2 - x1;
    dest.height = y2 - y1;

    if (dest.width <= 0 || dest.height <= 0) {
      return false;
    }
    return true;
  }

  private static void coloringRecursive(Component c, Color newBackground, Color oldBackground) {
    if (c.getBackground().equals(oldBackground)) {
      c.setBackground(newBackground);
    }
    if (c instanceof JComponent) {
      for (Component comp : ((JComponent)c).getComponents()) {
        coloringRecursive(comp, newBackground, oldBackground);
      }
    }
  }
}
