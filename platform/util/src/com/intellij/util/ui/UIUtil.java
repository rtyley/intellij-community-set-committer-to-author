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
package com.intellij.util.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.SideBorder;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ReflectionUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import javax.swing.plaf.ComboBoxUI;
import javax.swing.plaf.ProgressBarUI;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.plaf.basic.ComboPopup;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.JTextComponent;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Pattern;

/**
 * @author max
 */
public class UIUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.ui.UIUtil");
  @NonNls public static final String HTML_MIME = "text/html";
  public static final char MNEMONIC = 0x1B;
  @NonNls public static final String JSLIDER_ISFILLED = "JSlider.isFilled";
  @NonNls public static final String ARIAL_FONT_NAME = "Arial";
  @NonNls public static final String TABLE_FOCUS_CELL_BACKGROUND_PROPERTY = "Table.focusCellBackground";

  private static final Color UNFOCUSED_SELECTION_COLOR = new Color(212, 212, 212);

  private static final Color ACTIVE_HEADER_COLOR = new Color(160, 186, 213);
  private static final Color INACTIVE_HEADER_COLOR = new Color(128, 128, 128);

  private static final Color BORDER_COLOR = new Color(170, 170, 170);

  public static final Pattern CLOSE_TAG_PATTERN = Pattern.compile("<\\s*([^<>/ ]+)([^<>]*)/\\s*>", Pattern.CASE_INSENSITIVE);

  @NonNls public static final String FOCUS_PROXY_KEY = "isFocusProxy";

  public static Key<Integer> KEEP_BORDER_SIDES = Key.create("keepBorderSides");

  // accessed only from EDT
  private static final HashMap<Color, BufferedImage> ourAppleDotSamples = new HashMap<Color, BufferedImage>();

  @NonNls public static final String CENTER_TOOLTIP_DEFAULT = "ToCenterTooltip";
  @NonNls public static final String CENTER_TOOLTIP_STRICT = "ToCenterTooltip.default";

  private static final String ROOT_PANE = "JRootPane.future";

  private UIUtil() {
  }

  public static void drawLinePickedOut(Graphics graphics, int x, int y, int x1, int y1) {
    if (x == x1) {
      int minY = Math.min(y, y1);
      int maxY = Math.max(y,  y1);
      graphics.drawLine(x,  minY+1, x1, maxY-1);
    } else if (y == y1) {
      int minX = Math.min(x, x1);
      int maxX = Math.max(x, x1);
      graphics.drawLine(minX+1, y,  maxX-1, y1);
    } else {
      drawLine(graphics, x, y, x1, y1);
    }
  }

  public static boolean isReallyTypedEvent(KeyEvent e) {
    char c = e.getKeyChar();
    if (!(c >= 0x20 && c != 0x7F)) return false;

    int modifiers = e.getModifiers();
    if (SystemInfo.isMac) {
      return !e.isMetaDown() && !e.isControlDown();
    }

    return (modifiers & ActionEvent.ALT_MASK) == (modifiers & ActionEvent.CTRL_MASK);
  }

  public static int getStringY(@NotNull final String string, @NotNull final Rectangle bounds, @NotNull final Graphics2D g) {
    final int centerY = bounds.height / 2;
    final FontMetrics fm = g.getFontMetrics();
    final Font font = g.getFont();
    final FontRenderContext frc = g.getFontRenderContext();
    final Rectangle stringBounds = font.getStringBounds(string, frc).getBounds();

    return (int)(centerY - stringBounds.height / 2.0 - stringBounds.y);
  }

  public static void setEnabled(Component component, boolean enabled, boolean recursively) {
    component.setEnabled(enabled);
    if (component instanceof JLabel) {
      Color color = enabled ? getLabelForeground() : UIManager.getColor("Label.disabledForeground");
      if (color != null) {
        component.setForeground(color);
      }
    }
    if (recursively && enabled == component.isEnabled()) {
      if (component instanceof Container) {
        final Container container = (Container)component;
        final int subComponentCount = container.getComponentCount();
        for (int i = 0; i < subComponentCount; i++) {
          setEnabled(container.getComponent(i), enabled, recursively);
        }

      }
    }
  }

  public static void drawLine(Graphics g, int x1, int y1, int x2, int y2) {
    g.drawLine(x1, y1, x2, y2);
  }

  public static String[] splitText(String text, FontMetrics fontMetrics, int widthLimit, char separator) {
    ArrayList<String> lines = new ArrayList<String>();
    String currentLine = "";
    StringBuffer currentAtom = new StringBuffer();

    for (int i = 0; i < text.length(); i++) {
      char ch = text.charAt(i);
      currentAtom.append(ch);

      if (ch == separator) {
        currentLine += currentAtom.toString();
        currentAtom.setLength(0);
      }

      String s = currentLine + currentAtom.toString();
      int width = fontMetrics.stringWidth(s);

      if (width >= widthLimit - fontMetrics.charWidth('w')) {
        if (currentLine.length() > 0) {
          lines.add(currentLine);
          currentLine = "";
        }
        else {
          lines.add(currentAtom.toString());
          currentAtom.setLength(0);
        }
      }
    }

    String s = currentLine + currentAtom.toString();
    if (s.length() > 0) {
      lines.add(s);
    }

    return ArrayUtil.toStringArray(lines);
  }

  public static void setActionNameAndMnemonic(String text, Action action) {
    int mnemoPos = text.indexOf('&');
    if (mnemoPos >= 0 && mnemoPos < text.length() - 2) {
      String mnemoChar = text.substring(mnemoPos + 1, mnemoPos + 2).trim();
      if (mnemoChar.length() == 1) {
        action.putValue(Action.MNEMONIC_KEY, Integer.valueOf((int)mnemoChar.charAt(0)));
      }
    }

    text = text.replaceAll("&", "");
    action.putValue(Action.NAME, text);
  }

  public static Font getLabelFont() {
    return UIManager.getFont("Label.font");
  }

  public static Color getLabelBackground() {
    return UIManager.getColor("Label.background");
  }

  public static Color getLabelForeground() {
    return UIManager.getColor("Label.foreground");
  }

  public static Icon getOptionPanelWarningIcon() {
    return UIManager.getIcon("OptionPane.warningIcon");
  }

  /**
   * @deprecated use com.intellij.util.ui.UIUtil#getQuestionIcon()
   */
  public static Icon getOptionPanelQuestionIcon() {
    return getQuestionIcon();
  }

  @NotNull
  public static String removeMnemonic(@NotNull String s) {
    if (s.indexOf('&') != -1) {
      s = StringUtil.replace(s, "&", "");
    }
    if (s.indexOf(MNEMONIC) != -1) {
      s = StringUtil.replace(s, String.valueOf(MNEMONIC), "");
    }
    return s;
  }

  public static int getDisplayMnemonicIndex(@NotNull String s) {
    int idx = s.indexOf('&');
    if (idx >= 0) return idx;

    return s.indexOf(MNEMONIC);
  }

  public static String replaceMnemonicAmpersand(final String value) {
    if (value.indexOf('&') >= 0) {
      boolean useMacMnemonic = value.contains("&&");
      StringBuilder realValue = new StringBuilder();
      int i = 0;
      while (i < value.length()) {
        char c = value.charAt(i);
        if (c == '\\') {
          if (i < value.length() - 1 && value.charAt(i + 1) == '&') {
            realValue.append('&');
            i++;
          }
          else {
            realValue.append(c);
          }
        }
        else if (c == '&') {
          if (i < value.length() - 1 && value.charAt(i + 1) == '&') {
            if (SystemInfo.isMac) {
              realValue.append(MNEMONIC);
            }
            i++;
          }
          else {
            if (!SystemInfo.isMac || !useMacMnemonic) {
              realValue.append(MNEMONIC);
            }
          }
        }
        else {
          realValue.append(c);
        }
        i++;
      }

      return realValue.toString();
    }
    return value;
  }

  public static Color getTableHeaderBackground() {
    return UIManager.getColor("TableHeader.background");
  }

  public static Color getTreeTextForeground() {
    return UIManager.getColor("Tree.textForeground");
  }

  public static Color getTreeSelectionBackground() {
    return UIManager.getColor("Tree.selectionBackground");
  }

  public static Color getTreeTextBackground() {
    return UIManager.getColor("Tree.textBackground");
  }

  public static Color getListSelectionForeground() {
    final Color color = UIManager.getColor("List.selectionForeground");
    if (color == null) {
      return UIManager.getColor("List[Selected].textForeground");  // Nimbus
    }
    return color;
  }

  public static Color getFieldForegroundColor() {
    return UIManager.getColor("field.foreground");
  }

  public static Color getTableSelectionBackground() {
    return UIManager.getColor("Table.selectionBackground");
  }

  public static Color getActiveTextColor() {
    return UIManager.getColor("textActiveText");
  }

  public static Color getInactiveTextColor() {
    return UIManager.getColor("textInactiveText");
  }

  /**
   * @deprecated use com.intellij.util.ui.UIUtil#getTextFieldBackground()
   */
  public static Color getActiveTextFieldBackgroundColor() {
    return getTextFieldBackground();
  }

  public static Color getInactiveTextFieldBackgroundColor() {
    return UIManager.getColor("TextField.inactiveBackground");
  }

  public static Font getTreeFont() {
    return UIManager.getFont("Tree.font");
  }

  public static Font getListFont() {
    return UIManager.getFont("List.font");
  }

  public static Color getTreeSelectionForeground() {
    return UIManager.getColor("Tree.selectionForeground");
  }

  /**
   * @deprecated use com.intellij.util.ui.UIUtil#getInactiveTextColor()
   */
  public static Color getTextInactiveTextColor() {
    return getInactiveTextColor();
  }

  public static void installPopupMenuColorAndFonts(final JComponent contentPane) {
    LookAndFeel.installColorsAndFont(contentPane, "PopupMenu.background", "PopupMenu.foreground", "PopupMenu.font");
  }

  public static void installPopupMenuBorder(final JComponent contentPane) {
    LookAndFeel.installBorder(contentPane, "PopupMenu.border");
  }

  public static boolean isMotifLookAndFeel() {
    return "Motif".equals(UIManager.getLookAndFeel().getID());
  }

  public static Color getTreeSelectionBorderColor() {
    return UIManager.getColor("Tree.selectionBorderColor");
  }

  public static int getTreeRightChildIndent() {
    return UIManager.getInt("Tree.rightChildIndent");
  }

  public static int getTreeLeftChildIndent() {
    return UIManager.getInt("Tree.leftChildIndent");
  }

  public static Color getToolTipBackground() {
    return UIManager.getColor("ToolTip.background");
  }

  public static Color getToolTipForeground() {
    return UIManager.getColor("ToolTip.foreground");
  }

  public static Color getComboBoxDisabledForeground() {
    return UIManager.getColor("ComboBox.disabledForeground");
  }

  public static Color getComboBoxDisabledBackground() {
    return UIManager.getColor("ComboBox.disabledBackground");
  }

  public static Color getButtonSelectColor() {
    return UIManager.getColor("Button.select");
  }

  public static Integer getPropertyMaxGutterIconWidth(final String propertyPrefix) {
    return (Integer)UIManager.get(propertyPrefix + ".maxGutterIconWidth");
  }

  public static Color getMenuItemDisabledForeground() {
    return UIManager.getColor("MenuItem.disabledForeground");
  }

  public static Object getMenuItemDisabledForegroundObject() {
    return UIManager.get("MenuItem.disabledForeground");
  }

  public static Object getTabbedPanePaintContentBorder(final JComponent c) {
    return c.getClientProperty("TabbedPane.paintContentBorder");
  }

  public static boolean isMenuCrossMenuMnemonics() {
    return UIManager.getBoolean("Menu.crossMenuMnemonic");
  }

  public static Color getTableBackground() {
    return UIManager.getColor("Table.background");
  }

  public static Color getTableSelectionForeground() {
    return UIManager.getColor("Table.selectionForeground");
  }

  public static Color getTableForeground() {
    return UIManager.getColor("Table.foreground");
  }

  public static Color getTableGridColor() {
    return UIManager.getColor("Table.gridColor");
  }

  public static Color getListBackground() {
    // Fixes most of the GTK+ L&F glitches
    return isUnderGTKLookAndFeel() ? getTreeTextBackground() : UIManager.getColor("List.background");
  }

  public static Color getListBackground(boolean isSelected) {
    return isSelected ? getListSelectionBackground() : getListBackground();
  }

  public static Color getListForeground() {
    return UIManager.getColor("List.foreground");
  }

  public static Color getListForeground(boolean isSelected) {
    return isSelected ? getListSelectionForeground() : getListForeground();
  }

  public static Color getPanelBackground() {
    return UIManager.getColor("Panel.background");
  }

  public static Color getTreeForeground() {
    return UIManager.getColor("Tree.foreground");
  }

  public static Color getTableFocusCellBackground() {
    return UIManager.getColor(TABLE_FOCUS_CELL_BACKGROUND_PROPERTY);
  }

  public static Color getListSelectionBackground() {
    final Color color = UIManager.getColor("List.selectionBackground");
    if (color == null) {
      return UIManager.getColor("List[Selected].textBackground");  // Nimbus
    }
    return color;
  }

  public static Color getListUnfocusedSelectionBackground() {
    return UNFOCUSED_SELECTION_COLOR;
  }

  public static Color getTextFieldForeground() {
    return UIManager.getColor("TextField.foreground");
  }

  public static Color getTextFieldBackground() {
    return isUnderGTKLookAndFeel() ? UIManager.getColor("EditorPane.background") : UIManager.getColor("TextField.background");
  }

  public static Font getButtonFont() {
    return UIManager.getFont("Button.font");
  }

  public static Font getToolTipFont() {
    return UIManager.getFont("ToolTip.font");
  }

  public static Color getTabbedPaneBackground() {
    return UIManager.getColor("TabbedPane.background");
  }

  public static void setSliderIsFilled(final JSlider slider, final boolean value) {
    slider.putClientProperty("JSlider.isFilled", Boolean.valueOf(value));
  }

  public static Color getLabelTextForeground() {
    return UIManager.getColor("Label.textForeground");
  }

  public static Color getControlColor() {
    return UIManager.getColor("control");
  }

  public static Font getOptionPaneMessageFont() {
    return UIManager.getFont("OptionPane.messageFont");
  }

  public static Color getSeparatorShadow() {
    return UIManager.getColor("Separator.shadow");
  }

  public static Font getMenuFont() {
    return UIManager.getFont("Menu.font");
  }

  public static Color getSeparatorHighlight() {
    return UIManager.getColor("Separator.highlight");
  }

  public static Border getTableFocusCellHighlightBorder() {
    return UIManager.getBorder("Table.focusCellHighlightBorder");
  }

  public static void setLineStyleAngled(final ClientPropertyHolder component) {
    component.putClientProperty("JTree.lineStyle", "Angled");
  }

  public static void setLineStyleAngled(final JTree component) {
    component.putClientProperty("JTree.lineStyle", "Angled");
  }

  public static Color getTableFocusCellForeground() {
  return UIManager.getColor("Table.focusCellForeground");
  }

  /**
   * @deprecated  use com.intellij.util.ui.UIUtil#getPanelBackground() instead
   */
  public static Color getPanelBackgound() {
    return getPanelBackground();
  }

  public static Border getTextFieldBorder() {
    return UIManager.getBorder("TextField.border");
  }

  public static Border getButtonBorder() {
    return UIManager.getBorder("Button.border");
  }

  public static Icon getErrorIcon() {
    return UIManager.getIcon("OptionPane.errorIcon");
  }

  public static Icon getInformationIcon() {
    return UIManager.getIcon("OptionPane.informationIcon");
  }

  public static Icon getQuestionIcon() {
    return UIManager.getIcon("OptionPane.questionIcon");
  }

  public static Icon getWarningIcon() {
    return UIManager.getIcon("OptionPane.warningIcon");
  }

  public static Icon getBalloonInformationIcon() {
    return IconLoader.getIcon("/general/balloonInformation.png");
  }

  public static Icon getBalloonWarningIcon() {
    return IconLoader.getIcon("/general/balloonWarning.png");
  }

  public static Icon getBalloonErrorIcon() {
    return IconLoader.getIcon("/general/balloonError.png");
  }

  public static Icon getRadioButtonIcon() {
    return UIManager.getIcon("RadioButton.icon");
  }

  public static Icon getTreeCollapsedIcon() {
    return UIManager.getIcon("Tree.collapsedIcon");
  }

  public static Icon getTreeExpandedIcon() {
    return UIManager.getIcon("Tree.expandedIcon");
  }

  public static Border getTableHeaderCellBorder() {
    return UIManager.getBorder("TableHeader.cellBorder");
  }

  public static Color getWindowColor() {
    return UIManager.getColor("window");
  }

  public static Color getTextAreaForeground() {
    return UIManager.getColor("TextArea.foreground");
  }

  public static Color getOptionPaneBackground() {
    return UIManager.getColor("OptionPane.background");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static boolean isUnderQuaquaLookAndFeel() {
    return UIManager.getLookAndFeel().getName().contains("Quaqua");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static boolean isUnderAlloyLookAndFeel() {
    return UIManager.getLookAndFeel().getName().contains("Alloy");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static boolean isUnderNimbusLookAndFeel() {
    return UIManager.getLookAndFeel().getName().contains("Nimbus");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static boolean isUnderAquaLookAndFeel() {
    return UIManager.getLookAndFeel().getName().contains("Mac OS X");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static boolean isUnderGTKLookAndFeel() {
    return UIManager.getLookAndFeel().getName().contains("GTK");
  }

  public static boolean isFullRowSelectionLAF() {
    return isUnderNimbusLookAndFeel() || isUnderQuaquaLookAndFeel();
  }

  public static boolean isUnderNativeMacLookAndFeel() {
    return isUnderAquaLookAndFeel() || isUnderQuaquaLookAndFeel();
  }

  public static int getListCellHPadding() {
    return isUnderNativeMacLookAndFeel() ? 7 : 2;
  }

  public static int getListCellVPadding() {
    return 1;
  }

  public static Insets getListCellPadding() {
    return new Insets(getListCellVPadding(), getListCellHPadding(), getListCellVPadding(), getListCellHPadding());
  }

  public static Insets getListViewportPadding() {
    return isUnderNativeMacLookAndFeel() ? new Insets(1, 0, 1, 0) : new Insets(5, 5, 5, 5);
  }

  public static boolean isToUseDottedCellBorder() {
    return !isUnderNativeMacLookAndFeel();
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static void removeQuaquaVisualMarginsIn(Component component) {
    if (component instanceof JComponent) {
      final JComponent jComponent = (JComponent)component;
      final Component[] children = jComponent.getComponents();
      for (Component child : children) {
        removeQuaquaVisualMarginsIn(child);
      }

      jComponent.putClientProperty("Quaqua.Component.visualMargin", new Insets(0, 0, 0, 0));
    }
  }

  public static boolean isControlKeyDown(MouseEvent mouseEvent) {
    return SystemInfo.isMac ? mouseEvent.isMetaDown() : mouseEvent.isControlDown();
  }

  public static String[] getValidFontNames(final boolean familyName) {
    Set<String> result = new TreeSet<String>();
    Font[] fonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();
    for (Font font : fonts) {
      // Adds fonts that can display symbols at [A, Z] + [a, z] + [0, 9]
      try {
        if (font.canDisplay('a') && font.canDisplay('z') && font.canDisplay('A') && font.canDisplay('Z') && font.canDisplay('0') &&
            font.canDisplay('1')) {
          result.add(familyName ? font.getFamily() : font.getName());
        }
      }
      catch (Exception e) {
        // JRE has problems working with the font. Just skip.
      }
    }
    return ArrayUtil.toStringArray(result);
  }

  public static String[] getStandardFontSizes() {
    return new String[]{"8", "9", "10", "11", "12", "14", "16", "18", "20", "22", "24", "26", "28", "36", "48", "72"};
  }

  public static void setupEnclosingDialogBounds(final JComponent component) {
    component.revalidate();
    component.repaint();
    final Window window = SwingUtilities.windowForComponent(component);
    if (window != null &&
        (window.getSize().height < window.getMinimumSize().height || window.getSize().width < window.getMinimumSize().width)) {
      window.pack();
    }
  }

  public static String displayPropertiesToCSS(Font font, Color fg) {
    @NonNls StringBuilder rule = new StringBuilder("body {");
    if (font != null) {
      rule.append(" font-family: ");
      rule.append(font.getFamily());
      rule.append(" ; ");
      rule.append(" font-size: ");
      rule.append(font.getSize());
      rule.append("pt ;");
      if (font.isBold()) {
        rule.append(" font-weight: 700 ; ");
      }
      if (font.isItalic()) {
        rule.append(" font-style: italic ; ");
      }
    }
    if (fg != null) {
      rule.append(" color: #");
      if (fg.getRed() < 16) {
        rule.append('0');
      }
      rule.append(Integer.toHexString(fg.getRed()));
      if (fg.getGreen() < 16) {
        rule.append('0');
      }
      rule.append(Integer.toHexString(fg.getGreen()));
      if (fg.getBlue() < 16) {
        rule.append('0');
      }
      rule.append(Integer.toHexString(fg.getBlue()));
      rule.append(" ; ");
    }
    rule.append(" }");
    return rule.toString();
  }

  /**
   * @param g
   * @param x  top left X coordinate.
   * @param y  top left Y coordinate.
   * @param x1 right bottom X coordinate.
   * @param y1 right bottom Y coordinate.
   */
  public static void drawDottedRectangle(Graphics g, int x, int y, int x1, int y1) {
    int i1;
    for (i1 = x; i1 <= x1; i1 += 2) {
      drawLine(g, i1, y, i1, y);
    }

    for (i1 = i1 != x1 + 1 ? y + 2 : y + 1; i1 <= y1; i1 += 2) {
      drawLine(g, x1, i1, x1, i1);
    }

    for (i1 = i1 != y1 + 1 ? x1 - 2 : x1 - 1; i1 >= x; i1 -= 2) {
      drawLine(g, i1, y1, i1, y1);
    }

    for (i1 = i1 != x - 1 ? y1 - 2 : y1 - 1; i1 >= y; i1 -= 2) {
      drawLine(g, x, i1, x, i1);
    }
  }

  /**
   * Should be invoked only in EDT.
   *
   * @param g       Graphics surface
   * @param startX  Line start X coordinate
   * @param endX    Line end X coordinate
   * @param lineY   Line Y coordinate
   * @param bgColor Background color (optional)
   * @param fgColor Foreground color (optional)
   * @param opaque  If opaque the image will be dr
   */
  public static void drawBoldDottedLine(final Graphics2D g,
                                        final int startX,
                                        final int endX,
                                        final int lineY,
                                        final Color bgColor,
                                        final Color fgColor,
                                        final boolean opaque) {
    if (SystemInfo.isMac || SystemInfo.isLinux) {
      drawAppleDottedLine(g, startX, endX, lineY, bgColor, fgColor, opaque);
    }
    else {
      drawBoringDottedLine(g, startX, endX, lineY, bgColor, fgColor, opaque);
    }
  }

  public static void drawSearchMatch(final Graphics2D g,
                                        final int startX,
                                        final int endX,
                                        final int height) {
    Color c1 = new Color(255, 234, 162);
    Color c2 = new Color(255, 208, 66);
    drawSearchMatch(g, startX, endX, height, c1, c2);
  }

  public static void drawSearchMatch(Graphics2D g, int startX, int endX, int height, Color c1, Color c2) {
    final boolean drawRound = endX - startX > 4;

    final Composite oldComposite = g.getComposite();
    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
    g.setPaint(new GradientPaint(startX, 2, c1, startX, height - 5, c2));
    g.fillRect(startX, 3, endX - startX, height - 5);

    if (drawRound) {
      g.drawLine(startX - 1, 4, startX - 1, height - 4);
      g.drawLine(endX, 4, endX, height - 4);

      g.setColor(new Color(100, 100, 100, 50));
      g.drawLine(startX - 1, 4, startX - 1, height - 4);
      g.drawLine(endX, 4, endX, height - 4);

      g.drawLine(startX, 3, endX - 1, 3);
      g.drawLine(startX, height - 3, endX - 1, height - 3);
    }

    g.setComposite(oldComposite);
  }

  public static void drawRectPickedOut(Graphics2D g, int x, int y, int w, int h) {
    g.drawLine(x+1, y, x+w-1, y);
    g.drawLine(x+w, y+1, x+w, y+h-1);
    g.drawLine(x+w-1, y+h, x+1, y+h);
    g.drawLine(x, y+1, x, y+h-1);
  }

  private static void drawBoringDottedLine(final Graphics2D g,
                                           final int startX,
                                           final int endX,
                                           final int lineY,
                                           final Color bgColor,
                                           final Color fgColor,
                                           final boolean opaque) {
    final Color oldColor = g.getColor();

    // Fill 2 lines with background color
    if (opaque && bgColor != null) {
      g.setColor(bgColor);

      drawLine(g, startX, lineY, endX, lineY);
      drawLine(g, startX, lineY + 1, endX, lineY + 1);
    }

    // Draw dotted line:
    //
    // CCC CCC CCC ...
    // CCC CCC CCC ...
    //
    // (where "C" - colored pixel, " " - white pixel)

    final int step = 4;
    final int startPosCorrection = startX % step < 3 ? 0 : 1;

    g.setColor(fgColor != null ? fgColor : oldColor);
    // Now draw bold line segments
    for (int dotXi = (startX / step + startPosCorrection) * step; dotXi < endX; dotXi += step) {
      g.drawLine(dotXi, lineY, dotXi + 1, lineY);
      g.drawLine(dotXi, lineY + 1, dotXi + 1, lineY + 1);
    }

    // restore color
    g.setColor(oldColor);
  }

  private static void drawAppleDottedLine(final Graphics2D g,
                                          final int startX,
                                          final int endX,
                                          final int lineY,
                                          final Color bgColor,
                                          final Color fgColor,
                                          final boolean opaque) {
    final Color oldColor = g.getColor();

    // Fill 3 lines with background color
    if (opaque && bgColor != null) {
      g.setColor(bgColor);

      drawLine(g, startX, lineY, endX, lineY);
      drawLine(g, startX, lineY + 1, endX, lineY + 1);
      drawLine(g, startX, lineY + 2, endX, lineY + 2);
    }

    // Draw apple like dotted line:
    //
    // CCC CCC CCC ...
    // CCC CCC CCC ...
    // CCC CCC CCC ...
    //
    // (where "C" - colored pixel, " " - white pixel)

    final int step = 4;
    final int startPosCorrection = startX % step < 3 ? 0 : 1;

    // Optimization - lets draw dotted line using dot sample image.

    // draw one dot by pixel:

    // save old settings
    final Composite oldComposite = g.getComposite();
    // draw image "over" on top of background
    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER));

    // sample
    final BufferedImage image = getAppleDotStamp(fgColor, oldColor);

    // Now copy our dot several times
    final int dotX0 = (startX / step + startPosCorrection) * step;
    for (int dotXi = dotX0; dotXi < endX; dotXi += step) {
      g.drawImage(image, dotXi, lineY, null);
    }

    //restore previous settings
    g.setComposite(oldComposite);
  }

  private static BufferedImage getAppleDotStamp(final Color fgColor,
                                                final Color oldColor) {
    final Color color = fgColor != null ? fgColor : oldColor;

    // let's avoid of generating tons of GC and store samples for different colors
    BufferedImage sample = ourAppleDotSamples.get(color);
    if (sample == null) {
      sample = createAppleDotStamp(color);
      ourAppleDotSamples.put(color, sample);
    }
    return sample;
  }

  private static BufferedImage createAppleDotStamp(final Color color) {
    final BufferedImage image = new BufferedImage(3, 3, BufferedImage.TYPE_INT_ARGB);
    final Graphics2D g = image.createGraphics();

    g.setColor(color);

    // Each dot:
    // | 20%  | 50%  | 20% |
    // | 80%  | 80%  | 80% |
    // | 50%  | 100% | 50% |

    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC, .2f));
    g.drawLine(0, 0, 0, 0);
    g.drawLine(2, 0, 2, 0);

    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC, 0.7f));
    g.drawLine(0, 1, 2, 1);

    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC, 1.0f));
    g.drawLine(1, 2, 1, 2);

    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC, .5f));
    g.drawLine(1, 0, 1, 0);
    g.drawLine(0, 2, 0, 2);
    g.drawLine(2, 2, 2, 2);

    // dispose graphics
    g.dispose();

    return image;
  }

  public static void applyRenderingHints(final Graphics g) {
    Toolkit tk = Toolkit.getDefaultToolkit();
    //noinspection HardCodedStringLiteral
    Map map = (Map)tk.getDesktopProperty("awt.font.desktophints");
    if (map != null) {
      ((Graphics2D)g).addRenderingHints(map);
    }
  }

  @TestOnly
  public static void dispatchAllInvocationEvents() {
    assert SwingUtilities.isEventDispatchThread() : Thread.currentThread();
    final EventQueue eventQueue = Toolkit.getDefaultToolkit().getSystemEventQueue();
    while (true) {
      AWTEvent event = eventQueue.peekEvent();
      if (event == null) break;
      try {
        AWTEvent event1 = eventQueue.getNextEvent();
        if (event1 instanceof InvocationEvent) {
          ((InvocationEvent)event1).dispatch();
        }
      }
      catch (Exception e) {
        LOG.error(e); //?
      }
    }
  }

  @TestOnly
  public static void pump() {
    assert !SwingUtilities.isEventDispatchThread();
    final BlockingQueue<Object> queue = new LinkedBlockingQueue<Object>();
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        queue.offer(queue);
      }
    });
    try {
      queue.take();
    }
    catch (InterruptedException e) {
      LOG.error(e);
    }
  }

  public static void addAwtListener(final AWTEventListener listener, long mask, Disposable parent) {
    Toolkit.getDefaultToolkit().addAWTEventListener(listener, mask);
    Disposer.register(parent, new Disposable() {
      public void dispose() {
        Toolkit.getDefaultToolkit().removeAWTEventListener(listener);
      }
    });
  }

  public static void drawVDottedLine(Graphics2D g, int lineX, int startY, int endY, @Nullable final Color bgColor, final Color fgColor) {
    if (bgColor != null) {
      g.setColor(bgColor);
      drawLine(g, lineX, startY, lineX, endY);
    }

    g.setColor(fgColor);
    for (int i = (startY / 2) * 2; i < endY; i += 2) {
      g.drawRect(lineX, i, 0, 0);
    }
  }

  public static void drawHDottedLine(Graphics2D g, int startX, int endX, int lineY, @Nullable final Color bgColor, final Color fgColor) {
    if (bgColor != null) {
      g.setColor(bgColor);
      drawLine(g, startX, lineY, endX, lineY);
    }

    g.setColor(fgColor);

    for (int i = (startX / 2) * 2; i < endX; i += 2) {
      g.drawRect(i, lineY, 0, 0);
    }
  }

  public static void drawDottedLine(Graphics2D g, int x1, int y1, int x2, int y2, @Nullable final Color bgColor, final Color fgColor) {
    if (x1 == x2) {
      drawVDottedLine(g, x1, y1, y2, bgColor, fgColor);
    }
    else if (y1 == y2) {
      drawHDottedLine(g, x1, x2, y1, bgColor, fgColor);
    }
    else {
      throw new IllegalArgumentException("Only vertical or horizontal lines are supported");
    }
  }

  public static boolean isFocusAncestor(@NotNull final JComponent component) {
    final Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    if (owner == null) return false;
    if (owner == component) return true;
    return SwingUtilities.isDescendingFrom(owner, component);
  }


  public static boolean isCloseClick(MouseEvent e) {
    return isCloseClick(e, MouseEvent.MOUSE_PRESSED);
  }

  public static boolean isCloseClick(MouseEvent e, int effectiveType) {
    if (e.isPopupTrigger() || e.getID() != effectiveType) return false;
    return e.getButton() == MouseEvent.BUTTON2 || e.getButton() == MouseEvent.BUTTON1 && e.isShiftDown();
  }

  public static boolean isActionClick(MouseEvent e) {
    return isActionClick(e, MouseEvent.MOUSE_PRESSED);
  }

  public static boolean isActionClick(MouseEvent e, int effectiveType) {
    return isActionClick(e, effectiveType, false);
  }

  public static boolean isActionClick(MouseEvent e, int effectiveType, boolean allowShift) {
    if ((!allowShift && isCloseClick(e)) || e.isPopupTrigger() || e.getID() != effectiveType) return false;
    return e.getButton() == MouseEvent.BUTTON1;
  }

  @NotNull
  public static Color getBgFillColor(@NotNull JComponent c) {
    final Component parent = findNearestOpaque(c);
    return parent == null ? c.getBackground() : parent.getBackground();
  }

  @Nullable
  public static Component findNearestOpaque(JComponent c) {
    Component eachParent = c;
    while (eachParent != null) {
      if (eachParent.isOpaque()) return eachParent;
      eachParent = eachParent.getParent();
    }

    return eachParent;
  }

  @NonNls
  public static String getCssFontDeclaration(final Font font) {
    return getCssFontDeclaration(font, null, null, null);
  }

  @Language("HTML")
  @NonNls
  public static String getCssFontDeclaration(final Font font, @Nullable Color fgColor, @Nullable Color linkColor, @Nullable String liImg) {
    URL resource = liImg != null ? SystemInfo.class.getResource(liImg) : null;

    String fontFamilyAndSize = "font-family:" + font.getFamily() + "; font-size:" + font.getSize() + ";";
    //@Language("CSS")
    String body = "body, div, td {" + fontFamilyAndSize + " " + (fgColor != null ? "color:" + ColorUtil.toHex(fgColor) : "") + "}";
    if (resource != null) {
      body +=  "ul {list-style-image: " + resource.toExternalForm() +"}";
    }
    String link = linkColor != null ? "a {" + fontFamilyAndSize + " color:" + ColorUtil.toHex(linkColor) + "}" : "";
    return "<style> " + body + " " + link + "</style>";
  }

  public static boolean isWinLafOnVista() {
    return (SystemInfo.isWindowsVista || SystemInfo.isWindows7) && "Windows".equals(UIManager.getLookAndFeel().getName());
  }

  public static boolean isStandardMenuLAF() {
    return isWinLafOnVista() ||
           isUnderNimbusLookAndFeel() ||
           isUnderGTKLookAndFeel();
  }

  public static Color getFocusedFillColor() {
    return toAlpha(getListSelectionBackground(), 100);
  }

  public static Color getFocusedBoundsColor() {
    return getBoundsColor();
  }

  public static Color getBoundsColor() {
    return getBorderColor();
  }

  public static Color getBoundsColor(boolean focused) {
    return focused ? getFocusedBoundsColor() : getBoundsColor();
  }

  public static Color toAlpha(final Color color, final int alpha) {
    Color actual = color != null ? color : Color.black;
    return new Color(actual.getRed(), actual.getGreen(), actual.getBlue(), alpha);
  }

  public static void requestFocus(@NotNull final JComponent c) {
    if (c.isShowing()) {
      c.requestFocus();
    }
    else {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          c.requestFocus();
        }
      });
    }
  }

  //todo maybe should do for all kind of listeners via the AWTEventMulticaster class

  public static void dispose(final Component c) {
    if (c == null) return;

    final MouseListener[] mouseListeners = c.getMouseListeners();
    for (MouseListener each : mouseListeners) {
      c.removeMouseListener(each);
    }

    final MouseMotionListener[] motionListeners = c.getMouseMotionListeners();
    for (MouseMotionListener each : motionListeners) {
      c.removeMouseMotionListener(each);
    }

    final MouseWheelListener[] mouseWheelListeners = c.getMouseWheelListeners();
    for (MouseWheelListener each : mouseWheelListeners) {
      c.removeMouseWheelListener(each);
    }
  }

  public static void disposeProgress(final JProgressBar progress) {
    if (!isUnderNativeMacLookAndFeel()) return;

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        if (isToDispose(progress)) {
          progress.getUI().uninstallUI(progress);
          progress.putClientProperty("isDisposed", Boolean.TRUE);
        }
      }
    });
  }

  private static boolean isToDispose(final JProgressBar progress) {
    final ProgressBarUI ui = progress.getUI();

    if (ui == null) return false;
    if (Boolean.TYPE.equals(progress.getClientProperty("isDisposed"))) return false;

    try {
      final Field progressBarField = ReflectionUtil.findField(ui.getClass(), JProgressBar.class, "progressBar");
      progressBarField.setAccessible(true);
      return progressBarField.get(ui) != null;
    }
    catch (NoSuchFieldException e) {
      return true;
    }
    catch (IllegalAccessException e) {
      return true;
    }
  }

  @Nullable
  public static Component findUltimateParent(Component c) {
    if (c == null) return null;

    Component eachParent = c;
    while (true) {
      if (eachParent.getParent() == null) return eachParent;
      eachParent = eachParent.getParent();
    }
  }

  public static void setToolkitModal(final JDialog dialog) {
    try {
      final Class<?> modalityType = dialog.getClass().getClassLoader().loadClass("java.awt.Dialog$ModalityType");
      final Field field = modalityType.getField("TOOLKIT_MODAL");
      final Object value = field.get(null);

      final Method method = dialog.getClass().getMethod("setModalityType", modalityType);
      method.invoke(dialog, value);
    }
    catch (Exception e) {
      // ignore - no JDK 6
    }
  }

  public static void updateDialogIcon(final JDialog dialog, final List<Image> images) {
    try {
      final Method method = dialog.getClass().getMethod("setIconImages", List.class);
      method.invoke(dialog, images);
    }
    catch (Exception e) {
      // ignore - no JDK 6
    }
  }

  public static boolean hasJdk6Dialogs() {
    try {
      UIUtil.class.getClassLoader().loadClass("java.awt.Dialog$ModalityType");
    }
    catch (Throwable e) {
      return false;
    }
    return true;
  }

  public static Color getHeaderActiveColor() {
    return ACTIVE_HEADER_COLOR;
  }

  public static Color getHeaderInactiveColor() {
    return INACTIVE_HEADER_COLOR;
  }

  public static Color getBorderColor() {
    return BORDER_COLOR;
  }

  /**
   * @deprecated use getBorderColor instead
   */
  public static Color getBorderInactiveColor() {
    return getBorderColor();
  }

  /**
   * @deprecated use getBorderColor instead
   */
  public static Color getBorderActiveColor() {
    return getBorderColor();
  }

  /**
   * @deprecated use getBorderColor instead
   */
  public static Color getBorderSeparatorColor() {
    return getBorderColor();
  }

  public static HTMLEditorKit getHTMLEditorKit() {
    final HTMLEditorKit kit = new HTMLEditorKit();

    Font font = getLabelFont();
    @NonNls String family = font != null ? font.getFamily() : "Tahoma";
    int size = font != null ? font.getSize() : 11;

    final StyleSheet styleSheet = kit.getStyleSheet();
    styleSheet.addRule(String.format("body, div, p { font-family: %s; font-size: %s; } p { margin-top: 0; }", family, size));
    kit.setStyleSheet(styleSheet);

    return kit;
  }

  public static void removeScrollBorder(final Component c) {
    new AwtVisitor(c) {
      public boolean visit(final Component component) {
        if (component instanceof JScrollPane) {
          if (!hasNonPrimitiveParents(c, component)) {
            final JScrollPane scrollPane = (JScrollPane)component;
            Integer keepBorderSides = (Integer)scrollPane.getClientProperty(KEEP_BORDER_SIDES);
            if (keepBorderSides != null) {
              if (scrollPane.getBorder() instanceof LineBorder) {
                Color color = ((LineBorder)scrollPane.getBorder()).getLineColor();
                scrollPane.setBorder(new SideBorder(color, keepBorderSides.intValue()));
              }
              else {
                scrollPane.setBorder(new SideBorder(getBoundsColor(), keepBorderSides.intValue()));
              }
            }
            else {
              scrollPane.setBorder(new SideBorder(getBoundsColor(), SideBorder.NONE));
            }
          }
        }
        return false;
      }
    };
  }

  public static boolean hasNonPrimitiveParents(Component stopParent, Component c) {
    Component eachParent = c.getParent();
    while (true) {
      if (eachParent == null || eachParent == stopParent) return false;
      if (!isPrimitive(eachParent)) return true;
      eachParent = eachParent.getParent();
    }
  }

  public static boolean isPrimitive(Component c) {
    return c instanceof JPanel || c instanceof JLayeredPane;
  }

  public static Point getCenterPoint(Dimension container, Dimension child) {
    return getCenterPoint(new Rectangle(new Point(), container), child);
  }

  public static Point getCenterPoint(Rectangle container, Dimension child) {
    Point result = new Point();

    Point containerLocation = container.getLocation();
    Dimension containerSize = container.getSize();

    result.x = containerLocation.x + (containerSize.width / 2 - child.width / 2);
    result.y = containerLocation.y + (containerSize.height / 2 - child.height / 2);

    return result;
  }

  public static String toHtml(String html) {
    return toHtml(html, 0);
  }

  @NonNls
  public static String toHtml(String html, final int hPadding) {
    html = CLOSE_TAG_PATTERN.matcher(html).replaceAll("<$1$2></$1>");
    Font font = getLabelFont();
    @NonNls String family = font != null ? font.getFamily() : "Tahoma";
    int size = font != null ? font.getSize() : 11;
    return "<html><style>body { font-family: "
           + family + "; font-size: "
           + size + ";} ul li {list-style-type:circle;}</style>"
           + addPadding(html, hPadding) + "</html>";
  }

  public static String addPadding(final String html, int hPadding) {
    return String.format("<p style=\"margin: 0 %dpx 0 %dpx;\">%s</p>", hPadding, hPadding, html);
  }

  public static String convertSpace2Nbsp(String html) {
    StringBuffer result = new StringBuffer();
    int currentPos = 0;
    int braces = 0;
    while (currentPos < html.length()) {
      String each = html.substring(currentPos, currentPos + 1);
      if ("<".equals(each)) {
        braces++;
      } else if (">".equals(each)) {
        braces--;
      }

      if (" ".equals(each) && braces == 0) {
        result.append("&nbsp;");
      } else {
        result.append(each);
      }
      currentPos++;
    }

    String text = result.toString();
    int htmlTag = text.toLowerCase().lastIndexOf("</html>");

    if (htmlTag >= 0) {
      text = text.substring(0, htmlTag) + "<br><br>" + text.substring(htmlTag);
    }

    return result.toString();
  }

  public static void invokeLaterIfNeeded(@NotNull Runnable runnable) {
    if (SwingUtilities.isEventDispatchThread()) {
      runnable.run();
    }
    else {
      SwingUtilities.invokeLater(runnable);
    }
  }

  /**
   * Invoke and wait in the event dispatch thread
   * or in the current thread if the current thread
   * is event queue thread.
   *
   * @param runnable a runnable to invoke
   */
  public static void invokeAndWaitIfNeeded(@NotNull Runnable runnable) {
    if (SwingUtilities.isEventDispatchThread()) {
      runnable.run();
    }
    else {
      try {
        SwingUtilities.invokeAndWait(runnable);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
  }

  public static boolean isFocusProxy(@Nullable Component c) {
    return c instanceof JComponent && Boolean.TRUE.equals(((JComponent)c).getClientProperty(FOCUS_PROXY_KEY));
  }

  public static void setFocusProxy(JComponent c, boolean isProxy) {
    c.putClientProperty(FOCUS_PROXY_KEY, isProxy ? Boolean.TRUE : null);
  }

  public static void maybeInstall(InputMap map, String action, KeyStroke stroke) {
    if (map.get(stroke) == null) {
      map.put(stroke, action);
    }
  }

  /**
   * Avoid blinking while changing background
   */
  public static void changeBackGround(final Component component, final Color background) {
    final Color oldBackGround = component.getBackground();
    if (background == null || !background.equals(oldBackGround)){
      component.setBackground(background);
    }
  }

  public static void initDefaultLAF(String productName) {
    try {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    }
    catch (Exception ignored) {
    }
  }

  public static void addKeyboardShortcut(final JComponent target,
                                         final AbstractButton button, final KeyStroke keyStroke) {
    target.registerKeyboardAction(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          if (button.isEnabled()) {
            button.doClick();
          }
        }
      },
      keyStroke,
      JComponent.WHEN_FOCUSED
    );
  }

  public static void installComboBoxCopyAction(JComboBox comboBox) {
    final Component editorComponent = comboBox.getEditor().getEditorComponent();
    if (!(editorComponent instanceof JTextComponent)) return;
    final InputMap inputMap = ((JTextComponent)editorComponent).getInputMap();
    for (KeyStroke keyStroke : inputMap.allKeys()) {
      if (DefaultEditorKit.copyAction.equals(inputMap.get(keyStroke))) {
        comboBox.getInputMap().put(keyStroke, DefaultEditorKit.copyAction);
      }
    }
    comboBox.getActionMap().put(DefaultEditorKit.copyAction, new AbstractAction() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        if (!(e.getSource() instanceof JComboBox)) return;
        final JComboBox comboBox = (JComboBox)e.getSource();
        final String text;
        final Object selectedItem = comboBox.getSelectedItem();
        if (selectedItem instanceof String) {
          text = (String)selectedItem;
        }
        else {
          final Component component =
            comboBox.getRenderer().getListCellRendererComponent(new JList(), selectedItem, 0, false, false);
          if (component instanceof JLabel) {
            text = ((JLabel)component).getText();
          }
          else if (component != null) {
            final String str = component.toString();
            // skip default Component.toString and handle SimpleColoredComponent case
            text = str == null || str.startsWith(component.getClass().getName()+"[")? null : str;
          }
          else {
            text = null;
          }
        }
        if (text != null) {
          final JTextField textField = new JTextField(text);
          textField.selectAll();
          textField.copy();
        }
      }
    });
  }

  @Nullable
  public static ComboPopup getComboBoxPopup(JComboBox comboBox) {
    final ComboBoxUI ui = comboBox.getUI();
    if (ui instanceof BasicComboBoxUI) {
      try {
        final Field popup = BasicComboBoxUI.class.getDeclaredField("popup");
        popup.setAccessible(true);
        return (ComboPopup) popup.get(ui);
      }
      catch (NoSuchFieldException e) {
        return null;
      }
      catch (IllegalAccessException e) {
        return null;
      }
    }

    return null;
  }

  public static class MacTreeUI extends BasicTreeUI {
    public static final String SOURCE_LIST_CLIENT_PROPERTY = "mac.ui.source.list";

    private static final Icon TREE_COLLAPSED_ICON = getTreeCollapsedIcon();
    private static final Icon TREE_EXPANDED_ICON = getTreeExpandedIcon();
    private static final Icon TREE_SELECTED_COLLAPSED_ICON = IconLoader.getIcon("/mac/tree_white_right_arrow.png");
    private static final Icon TREE_SELECTED_EXPANDED_ICON = IconLoader.getIcon("/mac/tree_white_down_arrow.png");

    private static final Border LIST_BACKGROUND_PAINTER = (Border)UIManager.get("List.sourceListBackgroundPainter");
    private static final Border LIST_SELECTION_BACKGROUND_PAINTER = (Border)UIManager.get("List.sourceListSelectionBackgroundPainter");
    private static final Border LIST_FOCUSED_SELECTION_BACKGROUND_PAINTER =
      (Border)UIManager.get("List.sourceListFocusedSelectionBackgroundPainter");

    private boolean myWideSelection;
    private boolean myOldRepaintAllRowValue;

    public MacTreeUI() {
      this(true);
    }


    public MacTreeUI(final boolean wideSelection) {
      myWideSelection = wideSelection;
    }

    private final MouseListener mySelectionListener = new MouseAdapter() {
      @Override
      public void mousePressed(@NotNull final MouseEvent e) {
        final JTree tree = (JTree)e.getSource();
        if (SwingUtilities.isLeftMouseButton(e) && !e.isPopupTrigger()) {
          // if we can't stop any ongoing editing, do nothing
          if (isEditing(tree) && tree.getInvokesStopCellEditing()
              && !stopEditing(tree)) {
            return;
          }

          final TreePath pressedPath = getClosestPathForLocation(tree, e.getX(), e.getY());
          if (pressedPath != null) {
            Rectangle bounds = getPathBounds(tree, pressedPath);

            if (e.getY() >= (bounds.y + bounds.height)) {
              return;
            }

            if (bounds.contains(e.getPoint()) || isLocationInExpandControl(pressedPath, e.getX(), e.getY())) return;
            if (tree.getDragEnabled() || !startEditing(pressedPath, e)) {
              selectPathForEvent(pressedPath, e);
            }
          }

        }
      }
    };

    @Override
    protected void completeUIInstall() {
      super.completeUIInstall();

      myOldRepaintAllRowValue = UIManager.getBoolean("Tree.repaintWholeRow");
      UIManager.put("Tree.repaintWholeRow", true);

      tree.setShowsRootHandles(true);
      tree.addMouseListener(mySelectionListener);

      //final InputMap inputMap = tree.getInputMap(JComponent.WHEN_FOCUSED);
      //inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clearSelection");
    }

    @Override
    public void uninstallUI(JComponent c) {
      super.uninstallUI(c);

      UIManager.put("Tree.repaintWholeRow", myOldRepaintAllRowValue);
      c.removeMouseListener(mySelectionListener);
    }

    @Override
    protected void installKeyboardActions() {
      super.installKeyboardActions();

      final InputMap inputMap = tree.getInputMap(JComponent.WHEN_FOCUSED);
      inputMap.put(KeyStroke.getKeyStroke("pressed LEFT"), "collapse_or_move_up");
      inputMap.put(KeyStroke.getKeyStroke("pressed RIGHT"), "expand");

      final ActionMap actionMap = tree.getActionMap();

      final Action expandAction = actionMap.get("expand");
      if (expandAction != null) {
        actionMap.put("expand", new TreeUIAction() {
          @Override
          public void actionPerformed(ActionEvent e) {
            expandAction.actionPerformed(e);
          }
        });
      }

      actionMap.put("collapse_or_move_up", new TreeUIAction() {
        public void actionPerformed(final ActionEvent e) {
          final Object source = e.getSource();
          if (source instanceof JTree) {
            JTree tree = (JTree)source;
            int selectionRow = tree.getLeadSelectionRow();
            if (selectionRow == -1) return;

            if (isLeaf(selectionRow) || tree.isCollapsed(selectionRow)) {
              final TreePath parentPath = tree.getPathForRow(selectionRow).getParentPath();
              if (parentPath != null) {
                if (parentPath.getParentPath() != null || tree.isRootVisible()) {
                  final int parentRow = tree.getRowForPath(parentPath);
                  tree.scrollRowToVisible(parentRow);
                  tree.setSelectionInterval(parentRow, parentRow);
                }
              }
            }
            else {
              tree.collapseRow(selectionRow);
            }
          }
        }
      });
    }

    private abstract static class TreeUIAction extends AbstractAction implements UIResource {
    }

    @Override
    protected void paintHorizontalPartOfLeg(final Graphics g,
                                            final Rectangle clipBounds,
                                            final Insets insets,
                                            final Rectangle bounds,
                                            final TreePath path,
                                            final int row,
                                            final boolean isExpanded,
                                            final boolean hasBeenExpanded,
                                            final boolean isLeaf) {

    }

    @Override
    protected boolean isToggleSelectionEvent(MouseEvent e) {
      return SwingUtilities.isLeftMouseButton(e) && (SystemInfo.isMac ? e.isMetaDown() : e.isControlDown()) && !e.isPopupTrigger();
    }

    @Override
    protected void paintVerticalPartOfLeg(final Graphics g, final Rectangle clipBounds, final Insets insets, final TreePath path) {
    }

    @Override
    protected void paintHorizontalLine(Graphics g, JComponent c, int y, int left, int right) {
    }

    public boolean isWideSelection() {
      return myWideSelection;
    }

    @Override
    protected void paintRow(final Graphics g,
                            final Rectangle clipBounds,
                            final Insets insets,
                            final Rectangle bounds,
                            final TreePath path,
                            final int row,
                            final boolean isExpanded,
                            final boolean hasBeenExpanded,
                            final boolean isLeaf) {
      final int containerWidth = tree.getParent() instanceof JViewport ? tree.getParent().getWidth() : tree.getWidth();
      final int xOffset = tree.getParent() instanceof JViewport ? ((JViewport)tree.getParent()).getViewPosition().x : 0;

      if (path != null && myWideSelection) {
        boolean selected = tree.isPathSelected(path);
        Graphics2D rowGraphics = (Graphics2D)g.create();
        rowGraphics.setClip(clipBounds);

        final Object sourceList = tree.getClientProperty(SOURCE_LIST_CLIENT_PROPERTY);
        if (sourceList != null && ((Boolean)sourceList)) {
          if (selected) {
            if (tree.hasFocus()) {
              LIST_FOCUSED_SELECTION_BACKGROUND_PAINTER.paintBorder(tree, rowGraphics, xOffset, bounds.y, containerWidth, bounds.height);
            }
            else {
              LIST_SELECTION_BACKGROUND_PAINTER.paintBorder(tree, rowGraphics, xOffset, bounds.y, containerWidth, bounds.height);
            }
          }
          else {
            rowGraphics.setColor(tree.getBackground());
            rowGraphics.fillRect(xOffset, bounds.y, containerWidth, bounds.height);
          }
        }
        else {
          Color bg = tree.hasFocus() ? getTreeSelectionBackground() : getListUnfocusedSelectionBackground();
          if (!selected) {
            bg = tree.getBackground();
          }

          rowGraphics.setColor(bg);
          rowGraphics.fillRect(xOffset, bounds.y, containerWidth, bounds.height - 1);
        }

        if (shouldPaintExpandControl(path, row, isExpanded, hasBeenExpanded, isLeaf)) {
          paintExpandControl(rowGraphics, bounds, insets, bounds, path, row, isExpanded, hasBeenExpanded, isLeaf);
        }

        super.paintRow(rowGraphics, clipBounds, insets, bounds, path, row, isExpanded, hasBeenExpanded, isLeaf);
        rowGraphics.dispose();
      }
      else {
        super.paintRow(g, clipBounds, insets, bounds, path, row, isExpanded, hasBeenExpanded, isLeaf);
      }
    }

    @Override
    public void paint(Graphics g, JComponent c) {
      if (myWideSelection) {
        final int containerWidth = tree.getParent() instanceof JViewport ? tree.getParent().getWidth() : tree.getWidth();
        final int xOffset = tree.getParent() instanceof JViewport ? ((JViewport)tree.getParent()).getViewPosition().x : 0;
        final Rectangle bounds = g.getClipBounds();

        // draw background for the given clip bounds
        final Object sourceList = tree.getClientProperty(SOURCE_LIST_CLIENT_PROPERTY);
        if (sourceList != null && ((Boolean)sourceList)) {
          Graphics2D backgroundGraphics = (Graphics2D)g.create();
          backgroundGraphics.setClip(xOffset, bounds.y, containerWidth, bounds.height);
          LIST_BACKGROUND_PAINTER.paintBorder(tree, backgroundGraphics, xOffset, bounds.y, containerWidth, bounds.height);
          backgroundGraphics.dispose();
        }
      }

      super.paint(g, c);
    }

    @Override
    protected CellRendererPane createCellRendererPane() {
      return new CellRendererPane() {
        @Override
        public void paintComponent(Graphics g, Component c, Container p, int x, int y, int w, int h, boolean shouldValidate) {
          if (c instanceof JComponent && myWideSelection) {
            ((JComponent)c).setOpaque(false);
          }

          super.paintComponent(g, c, p, x, y, w, h, shouldValidate);
        }
      };
    }

    @Override
    protected void paintExpandControl(Graphics g,
                                      Rectangle clipBounds,
                                      Insets insets,
                                      Rectangle bounds,
                                      TreePath path,
                                      int row,
                                      boolean isExpanded,
                                      boolean hasBeenExpanded,
                                      boolean isLeaf) {
      boolean isPathSelected = tree.getSelectionModel().isPathSelected(path);

      Icon expandIcon = isPathSelected && tree.hasFocus() ? TREE_SELECTED_EXPANDED_ICON
                                                          : TREE_EXPANDED_ICON;
      Icon collapseIcon = isPathSelected && tree.hasFocus() ? TREE_SELECTED_COLLAPSED_ICON
                                                            : TREE_COLLAPSED_ICON;


      if (!isLeaf(row)) {
        setExpandedIcon(expandIcon);
        setCollapsedIcon(collapseIcon);
      }

      super.paintExpandControl(g, clipBounds, insets, bounds, path, row, isExpanded, hasBeenExpanded, isLeaf);
    }
  }

  public static boolean isPrinting(Graphics g) {
    return g instanceof PrintGraphics;
  }

  public static int getSelectedButton(ButtonGroup group) {
    Enumeration<AbstractButton> enumeration = group.getElements();
    int i = 0;
    while (enumeration.hasMoreElements()) {
      AbstractButton button = enumeration.nextElement();
      if (group.isSelected(button.getModel())) {
        return i;
      }
      i++;
    }
    return -1;
  }

  public static void setSelectedButton(ButtonGroup group, int index) {
    Enumeration<AbstractButton> enumeration = group.getElements();
    int i = 0;
    while (enumeration.hasMoreElements()) {
      AbstractButton button = enumeration.nextElement();
      group.setSelected(button.getModel(), index == i);
      i++;
    }
  }

  public static boolean isSelectionButtonDown(MouseEvent e) {
    return e.isShiftDown() || e.isControlDown() || e.isMetaDown();
  }

  public static void setComboBoxEditorBounds(int x, int y, int width, int height, JComponent editor) {
    if(SystemInfo.isMac && isUnderAquaLookAndFeel()) {
      // fix for too wide combobox editor, see AquaComboBoxUI.layoutContainer:
      // it adds +4 pixels to editor width. WTF?!
      editor.reshape(x, y, width - 4, height - 1);
    } else {
      editor.reshape(x, y, width, height);
    }
  }

  public static int fixComboBoxHeight(final int height) {
    return SystemInfo.isMac && isUnderAquaLookAndFeel() ? 28 : height;
  }

  @Nullable
  public static <T> T getParentOfType(Class<? extends T> cls, Component c) {
    Component eachParent = c;
    while (eachParent != null) {
      if (cls.isAssignableFrom(eachParent.getClass())) {
        return (T)eachParent;
      }

      eachParent = eachParent.getParent();
    }

    return null;
  }

  public static void scrollListToVisibleIfNeeded(@NotNull final JList list) {
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        final int selectedIndex = list.getSelectedIndex();
        if (selectedIndex >= 0) {
          final Rectangle visibleRect = list.getVisibleRect();
          final Rectangle cellBounds = list.getCellBounds(selectedIndex, selectedIndex);
          if (!visibleRect.contains(cellBounds)) {
            list.scrollRectToVisible(cellBounds);
          }
        }
      }
    });
  }

  @Nullable
  public static <T extends JComponent> T findComponentOfType(JComponent parent, Class<T> cls) {
    if (parent == null || cls.isAssignableFrom(parent.getClass())) return (T)parent;
    for (Component component : parent.getComponents()) {
      if (component instanceof JComponent) {
        T comp = findComponentOfType((JComponent)component, cls);
        if (comp != null) return comp;
      }
    }
    return null;
  }

  public static JRootPane getRootPane(Component c) {
    JRootPane root = getParentOfType(JRootPane.class, c);
    if (root != null) return root;
    Component eachParent = c;
    while (eachParent != null) {
      if (eachParent instanceof JComponent) {
        WeakReference<JRootPane> pane = (WeakReference<JRootPane>)((JComponent)eachParent).getClientProperty(ROOT_PANE);
        if (pane != null) return pane.get();
      }
      eachParent = eachParent.getParent();
    }

    return null;
  }

  public static void setFutureRootPane(JComponent c, JRootPane pane) {
    c.putClientProperty(ROOT_PANE, new WeakReference<JRootPane>(pane));
  }

}

