/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.LicensingFacade;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Properties;

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings("SSBasedInspection")
public class AboutDialog extends JDialog {

  public AboutDialog(Window owner) {
    super(owner);
    init(owner);
  }


  private void init(Window window) {
    ApplicationInfoEx appInfo = (ApplicationInfoEx)ApplicationInfo.getInstance();
    JPanel mainPanel = new JPanel(new BorderLayout());
    final JComponent closeListenerOwner;
    Icon image = IconLoader.getIcon(appInfo.getAboutLogoUrl());
    final InfoSurface infoSurface;
    if (appInfo.showLicenseeInfo()) {
      infoSurface = new InfoSurface(image);
      infoSurface.setPreferredSize(new Dimension(image.getIconWidth(), image.getIconHeight()));
      mainPanel.add(infoSurface, BorderLayout.NORTH);

      closeListenerOwner = infoSurface;
    }
    else {
      infoSurface = null;
      mainPanel.add(new JLabel(image), BorderLayout.NORTH);
      closeListenerOwner = mainPanel;
    }
    setUndecorated(true);
    setContentPane(mainPanel);
    final Ref<Long> showTime = Ref.create(System.currentTimeMillis());
    addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.VK_ESCAPE && e.getModifiers() == 0) {
          dispose();
        }
        else if (infoSurface != null) {
          if (code == KeyEvent.VK_CONTROL || code == KeyEvent.VK_META) {
            showTime.set(System.currentTimeMillis());
            e.consume();
          }
          else if ((code == KeyEvent.VK_C && (e.isControlDown() || e.isMetaDown()))
                   || (!SystemInfo.isMac && code == KeyEvent.VK_INSERT && e.isControlDown())) {
            copyInfoToClipboard(infoSurface.getText());
            showTime.set(System.currentTimeMillis());
            e.consume();
          }
        }
      }
    });

    //final long delta = Patches.APPLE_BUG_ID_3716865 ? 100 : 0;
    final long delta = 500; //reproducible on Windows too

    addWindowFocusListener(new WindowFocusListener() {
      public void windowGainedFocus(WindowEvent e) {
      }

      public void windowLostFocus(WindowEvent e) {
        long eventTime = System.currentTimeMillis();
        if (eventTime - showTime.get() > delta && e.getOppositeWindow() != e.getWindow()) {
          dispose();
        }
        else {
          IdeFocusManager.getGlobalInstance().requestFocus(AboutDialog.this, true);
        }
      }
    });

    closeListenerOwner.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (!e.isConsumed()) {
          dispose();
          e.consume();
        }
      }
    });

    pack();

    setLocationRelativeTo(window);
  }

  private static void copyInfoToClipboard(String text) {
    try {
      CopyPasteManager.getInstance().setContents(new StringSelection(text));
    }
    catch (Exception ignore) {
    }
  }


  private static class InfoSurface extends JPanel {
    final Color col;
    final Color linkCol;
    private final Icon myImage;
    private Font myFont;
    private Font myBoldFont;
    private final List<AboutBoxLine> myLines = new ArrayList<AboutBoxLine>();
    private int linkX;
    private int linkY;
    private int linkWidth;
    private boolean inLink = false;
    private StringBuilder myInfo = new StringBuilder();

    public InfoSurface(Icon image) {
      myImage = image;

      setOpaque(false);
      col = Color.white;
      final ApplicationInfoEx info = ApplicationInfoEx.getInstanceEx();
      linkCol = info.getLogoTextColor();
      setBackground(col);
      ApplicationInfoEx ideInfo = (ApplicationInfoEx)ApplicationInfo.getInstance();
      Calendar cal = ideInfo.getBuildDate();
      myLines.add(new AboutBoxLine(ideInfo.getFullApplicationName(), true, false));
      appendLast();
      myLines.add(new AboutBoxLine(IdeBundle.message("aboutbox.build.number", ideInfo.getBuild().asString())));
      appendLast();
      String buildDate = "";
      if (ideInfo.getBuild().isSnapshot()) {
        buildDate = new SimpleDateFormat("HH:mm, ").format(cal.getTime());
      }
      buildDate += DateFormatUtil.formatAboutDialogDate(cal.getTime());
      myLines.add(new AboutBoxLine(IdeBundle.message("aboutbox.build.date", buildDate)));
      appendLast();
      myLines.add(new AboutBoxLine(""));

      LicensingFacade provider = LicensingFacade.getInstance();
      if (provider != null) {
        myLines.add(new AboutBoxLine(provider.getLicensedToMessage(), true, false));
        for (String message : provider.getLicenseRestrictionsMessages()) {
          myLines.add(new AboutBoxLine(message));
        }
      }
      myLines.add(new AboutBoxLine(""));

      final Properties properties = System.getProperties();
      myLines.add(new AboutBoxLine(IdeBundle.message("aboutbox.jdk", properties.getProperty("java.version", "unknown")), true, false));
      appendLast();
      myLines.add(new AboutBoxLine(IdeBundle.message("aboutbox.vm", properties.getProperty("java.vm.name", "unknown"))));
      appendLast();
      myLines.add(new AboutBoxLine(IdeBundle.message("aboutbox.vendor", properties.getProperty("java.vendor", "unknown"))));
      appendLast();

      myLines.add(new AboutBoxLine(""));
      myLines.add(new AboutBoxLine(info.getCompanyName(), true, false));
      myLines.add(new AboutBoxLine(info.getCompanyURL(), true, true));
      addMouseListener(new MouseAdapter() {
        public void mousePressed(MouseEvent event) {
          if (inLink) {
            event.consume();
            BrowserUtil.launchBrowser(info.getCompanyURL());
          }
        }
      });
      addMouseMotionListener(new MouseMotionAdapter() {
        public void mouseMoved(MouseEvent event) {
          if (
            event.getPoint().x > linkX && event.getPoint().y >= linkY &&
            event.getPoint().x < linkX + linkWidth && event.getPoint().y < linkY + 10
            ) {
            if (!inLink) {
              setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
              inLink = true;
            }
          }
          else {
            if (inLink) {
              setCursor(Cursor.getDefaultCursor());
              inLink = false;
            }
          }
        }
      });
    }

    private void appendLast() {
      myInfo.append(myLines.get(myLines.size() - 1).getText()).append("\n");
    }

    @Override
    protected void paintChildren(Graphics g) {
      super.paintChildren(g);
      Graphics2D g2 = (Graphics2D)g;
      UIUtil.applyRenderingHints(g);

      Font labelFont = UIUtil.getLabelFont();
      for (int labelSize = 10; labelSize != 6; labelSize -= 1) {
        g2.setPaint(col);
        myImage.paintIcon(this, g2, 0, 0);

        g2.setColor(col);
        TextRenderer renderer = new TextRenderer(0, 145, 398, 120, g2);
        g2.setComposite(AlphaComposite.Src);
        myFont = labelFont.deriveFont(Font.PLAIN, labelSize);
        myBoldFont = labelFont.deriveFont(Font.BOLD, labelSize + 1);
        try {
          renderer.render(75, 0, myLines);
          break;
        }
        catch (TextRenderer.OverflowException ignore) {
        }
      }
    }

    public String getText() {
      return myInfo.toString();
    }

    public class TextRenderer {
      private final int xBase;
      private final int yBase;
      private final int w;
      private final int h;
      private final Graphics2D g2;

      private int x = 0;
      private int y = 0;
      private FontMetrics fontmetrics;
      private int fontAscent;
      private int fontHeight;
      private Font font;

      public class OverflowException extends Exception {
      }

      public TextRenderer(final int xBase, final int yBase, final int w, final int h, final Graphics2D g2) {
        this.xBase = xBase;
        this.yBase = yBase;
        this.w = w;
        this.h = h;
        this.g2 = g2;

        if (SystemInfo.isWindows) {
          g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        }
      }

      public void render(int indentX, int indentY, List<AboutBoxLine> lines) throws OverflowException {
        x = indentX;
        y = indentY;
        g2.setColor(Color.black);
        for (int i = 0; i < lines.size(); i++) {
          AboutBoxLine line = lines.get(i);
          final String s = line.getText();
          setFont(line.isBold() ? myBoldFont : myFont);
          if (line.isLink()) {
            g2.setColor(linkCol);
            linkX = x;
            linkY = yBase + y - fontAscent;
            FontMetrics metrics = g2.getFontMetrics(font);
            linkWidth = metrics.stringWidth(s);
          }
          renderString(s, indentX);
          if (i == lines.size() - 2) {
            x += 50;
          }
          else if (i < lines.size() - 1) {
            lineFeed(indentX, s);
          }
        }
      }

      private void renderString(final String s, final int indentX) throws OverflowException {
        final List<String> words = StringUtil.split(s, " ");
        for (String word : words) {
          int wordWidth = fontmetrics.stringWidth(word);
          if (x + wordWidth >= w) {
            lineFeed(indentX, word);
          }
          else {
            char c = ' ';
            final int cW = fontmetrics.charWidth(c);
            if (x + cW < w) {
              g2.drawChars(new char[]{c}, 0, 1, xBase + x, yBase + y);
              x += cW;
            }
          }
          renderWord(word, indentX);
        }
      }

      private void renderWord(final String s, final int indentX) throws OverflowException {
        for (int j = 0; j != s.length(); ++j) {
          final char c = s.charAt(j);
          final int cW = fontmetrics.charWidth(c);
          if (x + cW >= w) {
            lineFeed(indentX, s);
          }
          g2.drawChars(new char[]{c}, 0, 1, xBase + x, yBase + y);
          x += cW;
        }
      }

      private void lineFeed(int indent, final String s) throws OverflowException {
        x = indent;
        if (s.length() == 0) {
          y += fontHeight / 3;
        }
        else {
          y += fontHeight;
        }
        if (y >= h) {
          throw new OverflowException();
        }
      }

      private void setFont(Font font) {
        this.font = font;
        fontmetrics = g2.getFontMetrics(font);
        g2.setFont(font);
        fontAscent = fontmetrics.getAscent();
        fontHeight = fontmetrics.getHeight();
      }
    }
  }

  private static class AboutBoxLine {
    private final String myText;
    private final boolean myBold;
    private final boolean myLink;

    public AboutBoxLine(final String text, final boolean bold, final boolean link) {
      myLink = link;
      myText = text;
      myBold = bold;
    }

    public AboutBoxLine(final String text) {
      myText = text;
      myBold = false;
      myLink = false;
    }


    public String getText() {
      return myText;
    }

    public boolean isBold() {
      return myBold;
    }

    public boolean isLink() {
      return myLink;
    }
  }
}
