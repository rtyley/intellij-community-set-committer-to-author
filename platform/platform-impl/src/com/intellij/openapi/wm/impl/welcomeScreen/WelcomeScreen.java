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
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.DataManager;
import com.intellij.ide.plugins.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.PluginsFacade;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.LabeledIcon;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.UIBundle;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.PixelGrabber;
import java.util.*;
import java.util.List;

import static java.awt.GridBagConstraints.*;

/**
 * @author pti
 */
public class WelcomeScreen {
  private static final Insets ACTION_GROUP_CAPTION_INSETS = new Insets(20, 30, 5, 0);
  private static final Insets PLUGINS_CAPTION_INSETS = new Insets(20, 25, 0, 0);
  private static final Insets ACTION_ICON_INSETS = new Insets(5, 20, 15, 0);
  private static final Insets ACTION_NAME_INSETS = new Insets(15, 5, 0, 0);
  private static final Insets ACTION_DESCRIPTION_INSETS = new Insets(7, 5, 0, 30);
  private static final Insets NO_INSETS = new Insets(0, 0, 0, 0);

  private static final int MAIN_GROUP = 0;
  private static final int PLUGIN_DSC_MAX_WIDTH = 260;
  private static final int PLUGIN_DSC_MAX_ROWS = 2;
  private static final int PLUGIN_NAME_MAX_WIDTH = 180;
  private static final int PLUGIN_NAME_MAX_ROWS = 2;
  private static final int MAX_TOOLTIP_WIDTH = 400;
  private static final int ACTION_BUTTON_PADDING = 5;

  private static final Dimension ACTION_BUTTON_SIZE = new Dimension(66, 66);
  private static final Dimension PLUGIN_LOGO_SIZE = new Dimension(16, 16);
  private static final Icon DEFAULT_ICON = IconLoader.getIcon("/general/configurableDefault.png");

  @NonNls private static final String CAPTION_FONT_NAME = "Tahoma";
  private static final Font TEXT_FONT = new Font(CAPTION_FONT_NAME, Font.PLAIN, 11);
  private static final Font LINK_FONT = new Font(CAPTION_FONT_NAME, Font.BOLD, 12);
  private static final Font GROUP_CAPTION_FONT = new Font(CAPTION_FONT_NAME, Font.BOLD, 18);

  private static final Color WELCOME_PANEL_BACKGROUND = Color.WHITE;
  private static final Color MAIN_PANEL_BACKGROUND = WELCOME_PANEL_BACKGROUND;
  private static final Color PLUGINS_PANEL_BACKGROUND = new Color(248, 248, 248);
  private static final Color PLUGINS_PANEL_BORDER = new Color(234, 234, 234);
  private static final Color CAPTION_COLOR = new Color(47, 67, 96);
  private static final Color DISABLED_CAPTION_COLOR = UIUtil.getInactiveTextColor();
  private static final Color ACTION_BUTTON_COLOR = WELCOME_PANEL_BACKGROUND;
  private static final Color BUTTON_POPPED_COLOR = new Color(241, 241, 241);
  private static final Color BUTTON_PUSHED_COLOR = new Color(228, 228, 228);

  @NonNls private static final String HTML_PREFIX = "<html>";
  @NonNls private static final String HTML_SUFFIX = "</html>";
  @NonNls private static final String ___HTML_SUFFIX = "...</html>";
  @NonNls private static final String ESC_NEW_LINE = "\\n";

  private final JPanel myWelcomePanel;
  private final JPanel myMainPanel;
  private final JPanel myPluginsPanel;

  private Icon myCaptionImage;
  private Icon myDeveloperSlogan;
  private Color myCaptionBackground = new Color(23, 52, 150);

  private MyActionButton myPressedButton = null;
  private int mySelectedRow = -1;
  private int mySelectedColumn = -1;
  private int mySelectedGroup = -1;
  private int myPluginsIdx = -1;

  public static JPanel createWelcomePanel() {
    return new WelcomeScreen().myWelcomePanel;
  }

  private WelcomeScreen() {
    initApplicationSpecificImages();

    // Create caption pane
    JPanel topPanel = createCaptionPane();

    // Create Main Panel for Quick Start and Documentation
    myMainPanel = new WelcomeScrollablePanel(new GridLayout(1, 2));
    myMainPanel.setBackground(MAIN_PANEL_BACKGROUND);
    setUpMainPanel();
    JScrollPane mainScrollPane = scrollPane(myMainPanel, null);

    // Create Plugins Panel
    myPluginsPanel = new WelcomeScrollablePanel(new GridBagLayout());
    myPluginsPanel.setBackground(PLUGINS_PANEL_BACKGROUND);
    setUpPluginsPanel();
    JScrollPane pluginsScrollPane = scrollPane(myPluginsPanel, PLUGINS_PANEL_BORDER);

    // Create Welcome panel
    GridBagConstraints gBC;
    myWelcomePanel = new JPanel(new GridBagLayout());
    myWelcomePanel.setBackground(WELCOME_PANEL_BACKGROUND);
    gBC = new GridBagConstraints(0, 0, 2, 1, 1, 0, NORTHWEST, HORIZONTAL, new Insets(7, 7, 7, 7), 0, 0);
    myWelcomePanel.add(topPanel, gBC);
    gBC = new GridBagConstraints(0, 1, 1, 1, 0.7, 1, NORTHWEST, BOTH, new Insets(0, 7, 7, 7), 0, 0);
    myWelcomePanel.add(mainScrollPane, gBC);
    gBC = new GridBagConstraints(1, 1, 1, 1, 0.3, 1, NORTHWEST, BOTH, new Insets(0, 0, 7, 7), 0, 0);
    myWelcomePanel.add(pluginsScrollPane, gBC);
  }

  private void initApplicationSpecificImages() {
    if (myCaptionImage == null) {
      ApplicationInfoEx applicationInfoEx = ApplicationInfoEx.getInstanceEx();
      myCaptionImage = IconLoader.getIcon(applicationInfoEx.getWelcomeScreenCaptionUrl());
      myDeveloperSlogan = IconLoader.getIcon(applicationInfoEx.getWelcomeScreenDeveloperSloganUrl());

      BufferedImage image = new BufferedImage(myCaptionImage.getIconWidth(), myCaptionImage.getIconHeight(), BufferedImage.TYPE_INT_RGB);
      myCaptionImage.paintIcon(null, image.getGraphics(), 0, 0);
      final int[] pixels = new int[1];
      final PixelGrabber pixelGrabber =
        new PixelGrabber(image, myCaptionImage.getIconWidth() - 1, myCaptionImage.getIconHeight() - 2, 1, 1, pixels, 0, 1);
      try {
        pixelGrabber.grabPixels();
        myCaptionBackground = new Color(pixels[0]);
      }
      catch (InterruptedException ignore) {
      }
    }
  }

  private JPanel createCaptionPane() {
    JPanel topPanel = new JPanel(new GridBagLayout()) {
      public void paint(Graphics g) {
        Icon welcome = myCaptionImage;
        welcome.paintIcon(null, g, 0, 0);
        g.setColor(myCaptionBackground);
        g.fillRect(welcome.getIconWidth(), 0, getWidth() - welcome.getIconWidth(), welcome.getIconHeight());
        super.paint(g);
      }
    };
    topPanel.setOpaque(false);
    topPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, myCaptionBackground));

    JPanel transparentTopPanel = new JPanel();
    transparentTopPanel.setOpaque(false);

    topPanel.add(transparentTopPanel, new GridBagConstraints(0, 0, 1, 1, 1, 0, CENTER, HORIZONTAL, NO_INSETS, 0, 0));
    topPanel.add(new JLabel(myDeveloperSlogan), new GridBagConstraints(1, 0, 1, 1, 0, 0, SOUTHWEST, NONE, new Insets(0, 0, 0, 10), 0, 0));

    return topPanel;
  }

  private void setUpMainPanel() {
    final ActionManager actionManager = ActionManager.getInstance();

    // Create QuickStarts group of actions
    ActionGroupDescriptor quickStarts = new ActionGroupDescriptor(UIBundle.message("welcome.screen.quick.start.action.group.name"), 0);
    // Append plug-in actions to the end of the QuickStart list
    quickStarts.appendActionsFromGroup((DefaultActionGroup)actionManager.getAction(IdeActions.GROUP_WELCOME_SCREEN_QUICKSTART));
    final JPanel quickStartPanel = quickStarts.getPanel();
    // Add empty panel at the end of the QuickStarts panel
    JPanel emptyPanel_2 = new JPanel();
    emptyPanel_2.setBackground(MAIN_PANEL_BACKGROUND);
    quickStartPanel.add(emptyPanel_2, new GridBagConstraints(0, quickStarts.getIdx() + 2, 2, 1, 1, 1, NORTHWEST, BOTH, NO_INSETS, 0, 0));

    // Create Documentation group of actions
    ActionGroupDescriptor docsGroup = new ActionGroupDescriptor(UIBundle.message("welcome.screen.documentation.action.group.name"), 1);
    // Append plug-in actions to the end of the QuickStart list
    docsGroup.appendActionsFromGroup((DefaultActionGroup)actionManager.getAction(IdeActions.GROUP_WELCOME_SCREEN_DOC));
    final JPanel docsPanel = docsGroup.getPanel();
    // Add empty panel at the end of the Documentation list
    JPanel emptyPanel_3 = new JPanel();
    emptyPanel_3.setBackground(MAIN_PANEL_BACKGROUND);
    docsPanel.add(emptyPanel_3, new GridBagConstraints(0, docsGroup.getIdx() + 2, 2, 1, 1, 1, NORTHWEST, BOTH, NO_INSETS, 0, 0));

    // Add QuickStarts and Docs to main panel
    myMainPanel.add(quickStartPanel);
    myMainPanel.add(docsPanel);
  }

  private void setUpPluginsPanel() {
    GridBagConstraints gBC;

    JLabel pluginsCaption = new JLabel(UIBundle.message("welcome.screen.plugins.panel.plugins.label"));
    pluginsCaption.setFont(GROUP_CAPTION_FONT);
    pluginsCaption.setForeground(CAPTION_COLOR);

    JLabel openPluginManager = new JLabel(UIBundle.message("welcome.screen.plugins.panel.manager.link"));
    openPluginManager.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        final PluginManagerConfigurable configurable = new PluginManagerConfigurable(PluginManagerUISettings.getInstance());
        ShowSettingsUtil.getInstance().editConfigurable(myPluginsPanel, configurable);
      }
    });
    openPluginManager.setForeground(CAPTION_COLOR);
    openPluginManager.setFont(LINK_FONT);
    openPluginManager.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    JLabel installedPluginsCaption = new JLabel(UIBundle.message("welcome.screen.plugins.panel.my.plugins.label"));
    installedPluginsCaption.setFont(LINK_FONT);
    installedPluginsCaption.setForeground(CAPTION_COLOR);

    JPanel installedPluginsPanel = new JPanel(new GridBagLayout());
    installedPluginsPanel.setBackground(PLUGINS_PANEL_BACKGROUND);

    JLabel bundledPluginsCaption = new JLabel(UIBundle.message("welcome.screen.plugins.panel.bundled.plugins.label"));
    bundledPluginsCaption.setFont(LINK_FONT);
    bundledPluginsCaption.setForeground(CAPTION_COLOR);

    JPanel bundledPluginsPanel = new JPanel(new GridBagLayout());
    bundledPluginsPanel.setBackground(PLUGINS_PANEL_BACKGROUND);

    createListOfPlugins(installedPluginsPanel, bundledPluginsPanel);

    JPanel topPluginsPanel = new JPanel(new GridBagLayout());
    topPluginsPanel.setBackground(PLUGINS_PANEL_BACKGROUND);

    gBC = new GridBagConstraints(0, 0, 1, 1, 0, 0, NORTHWEST, NONE, PLUGINS_CAPTION_INSETS, 0, 0);
    topPluginsPanel.add(pluginsCaption, gBC);

    JLabel emptyLabel_1 = new JLabel();
    emptyLabel_1.setBackground(PLUGINS_PANEL_BACKGROUND);
    gBC = new GridBagConstraints(1, 0, 1, 1, 1, 0, NORTHWEST, NONE, NO_INSETS, 0, 0);
    topPluginsPanel.add(emptyLabel_1, gBC);

    gBC = new GridBagConstraints(2, 0, 1, 1, 0, 0, NORTHWEST, NONE, new Insets(22, 0, 0, 10), 0, 0);
    topPluginsPanel.add(openPluginManager, gBC);

    gBC = new GridBagConstraints(0, 0, 1, 1, 0, 0, NORTHWEST, HORIZONTAL, NO_INSETS, 0, 0);
    myPluginsPanel.add(topPluginsPanel, gBC);

    gBC = new GridBagConstraints(0, 1, 1, 1, 0, 0, NORTHWEST, NONE, new Insets(20, 25, 0, 0), 0, 0);
    myPluginsPanel.add(installedPluginsCaption, gBC);
    gBC = new GridBagConstraints(0, 2, 1, 1, 1, 0, NORTHWEST, NONE, new Insets(0, 5, 0, 0), 0, 0);
    myPluginsPanel.add(installedPluginsPanel, gBC);

    gBC = new GridBagConstraints(0, 3, 1, 1, 0, 0, NORTHWEST, NONE, new Insets(20, 25, 0, 0), 0, 0);
    myPluginsPanel.add(bundledPluginsCaption, gBC);
    gBC = new GridBagConstraints(0, 4, 1, 1, 1, 0, NORTHWEST, NONE, new Insets(0, 5, 0, 0), 0, 0);
    myPluginsPanel.add(bundledPluginsPanel, gBC);

    JPanel emptyPanel_1 = new JPanel();
    emptyPanel_1.setBackground(PLUGINS_PANEL_BACKGROUND);
    gBC = new GridBagConstraints(0, 5, 1, 1, 1, 1, NORTHWEST, BOTH, NO_INSETS, 0, 0);
    myPluginsPanel.add(emptyPanel_1, gBC);
  }

  private void createListOfPlugins(final JPanel installedPluginsPanel, final JPanel bundledPluginsPanel) {
    //Create the list of installed plugins
    List<IdeaPluginDescriptor> installedPlugins =
      new ArrayList<IdeaPluginDescriptor>(Arrays.asList(PluginsFacade.INSTANCE.getPlugins()));

    if (installedPlugins.size() == 0) {
      addListItemToPlugins(installedPluginsPanel,
                           italic(UIBundle.message("welcome.screen.plugins.panel.no.plugins.currently.installed.message.text")));
      addListItemToPlugins(bundledPluginsPanel,
                           italic(UIBundle.message("welcome.screen.plugins.panel.all.bundled.plugins.were.uninstalled.message.text")));
    }
    else {
      final Comparator<IdeaPluginDescriptor> pluginsComparator = new Comparator<IdeaPluginDescriptor>() {
        public int compare(final IdeaPluginDescriptor o1, final IdeaPluginDescriptor o2) {
          final boolean e1 = ((IdeaPluginDescriptorImpl)o1).isEnabled();
          final boolean e2 = ((IdeaPluginDescriptorImpl)o2).isEnabled();
          if (e1 && !e2) return -1;
          if (!e1 && e2) return 1;
          return o1.getName().toLowerCase().compareTo(o2.getName().toLowerCase());
        }
      };
      Collections.sort(installedPlugins, pluginsComparator);

      int embeddedPlugins = 0;
      int installedPluginsCount = 0;

      for (IdeaPluginDescriptor plugin : installedPlugins) {
        if (plugin.getName().equals("IDEA CORE") || ((IdeaPluginDescriptorImpl)plugin).isUseCoreClassLoader()) {
          // this is not really a plugin, so it shouldn't be displayed
          continue;
        }
        if (plugin.isBundled()) {
          embeddedPlugins++;
          addListItemToPlugins(bundledPluginsPanel, (IdeaPluginDescriptorImpl)plugin);
        }
        else {
          installedPluginsCount++;
          addListItemToPlugins(installedPluginsPanel, (IdeaPluginDescriptorImpl)plugin);
        }
      }
      if (embeddedPlugins == 0) {
        addListItemToPlugins(bundledPluginsPanel,
                             italic(UIBundle.message("welcome.screen.plugins.panel.all.bundled.plugins.were.uninstalled.message.text")));
      }
      if (installedPluginsCount == 0) {
        addListItemToPlugins(installedPluginsPanel,
                             italic(UIBundle.message("welcome.screen.plugins.panel.no.plugins.currently.installed.message.text")));
      }
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static String italic(final String message) {
    return "<i>" + message + "</i>";
  }


  private void addListItemToPlugins(final JPanel bundledPluginsPanel, final String title) {
    addListItemToPlugins(bundledPluginsPanel, title, null, null, null, null, true, false);
  }

  private void addListItemToPlugins(final JPanel bundledPluginsPanel, final IdeaPluginDescriptorImpl plugin) {
    addListItemToPlugins(bundledPluginsPanel, plugin.getName(), plugin.getDescription(), plugin.getVendorLogoPath(),
                         plugin.getPluginClassLoader(), plugin.getUrl(), plugin.isEnabled(), PluginManager.isIncompatible(plugin));
  }

  public void addListItemToPlugins(final JPanel panel,
                                   String name,
                                   String description,
                                   final String iconPath,
                                   final ClassLoader pluginClassLoader,
                                   final String url,
                                   final boolean enabled,
                                   final boolean incompatible) {
    if (StringUtil.isEmptyOrSpaces(name)) {
      return;
    }
    else {
      name = name.trim();
    }

    final int y = myPluginsIdx += 2;
    Icon logoImage;

    // Check the iconPath and insert empty icon in case of empty or invalid value
    if (StringUtil.isEmptyOrSpaces(iconPath)) {
      logoImage = new EmptyIcon(PLUGIN_LOGO_SIZE.width, PLUGIN_LOGO_SIZE.height);
    }
    else {
      logoImage = IconLoader.findIcon(iconPath, pluginClassLoader);
      if (logoImage == null) logoImage = new EmptyIcon(PLUGIN_LOGO_SIZE.width, PLUGIN_LOGO_SIZE.height);
    }
    JLabel imageLabel = new JLabel(logoImage);
    GridBagConstraints gBC = new GridBagConstraints(0, y, 1, 1, 0, 0, NORTHWEST, NONE, new Insets(15, 20, 0, 0), 0, 0);
    panel.add(imageLabel, gBC);

    name = name + " " + (incompatible ? UIBundle.message("welcome.screen.incompatible.plugins.description")
                                      : (enabled ? "" : UIBundle.message("welcome.screen.disabled.plugins.description")));
    String shortenedName = adjustStringBreaksByWidth(name, LINK_FONT, false, PLUGIN_NAME_MAX_WIDTH, PLUGIN_NAME_MAX_ROWS);
    JLabel logoName = new JLabel(shortenedName);
    logoName.setFont(LINK_FONT);
    logoName.setForeground(enabled ? CAPTION_COLOR : DISABLED_CAPTION_COLOR);
    if (shortenedName.endsWith(___HTML_SUFFIX)) {
      logoName.setToolTipText(adjustStringBreaksByWidth(name, UIUtil.getToolTipFont(), false, MAX_TOOLTIP_WIDTH, 0));
    }

    JPanel logoPanel = new JPanel(new BorderLayout());
    logoPanel.setBackground(PLUGINS_PANEL_BACKGROUND);
    logoPanel.add(logoName, BorderLayout.WEST);
    gBC = new GridBagConstraints(1, y, 1, 1, 0, 0, NORTHWEST, NONE, new Insets(15, 7, 0, 0), 0, 0);
    panel.add(logoPanel, gBC);

    if (!StringUtil.isEmptyOrSpaces(url)) {
      JLabel learnMore = new JLabel(UIBundle.message("welcome.screen.plugins.panel.learn.more.link"));
      learnMore.setFont(LINK_FONT);
      learnMore.setForeground(enabled ? CAPTION_COLOR : DISABLED_CAPTION_COLOR);
      learnMore.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      learnMore.setToolTipText(UIBundle.message("welcome.screen.plugins.panel.learn.more.tooltip.text"));
      learnMore.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          try {
            BrowserUtil.launchBrowser(url);
          }
          catch (IllegalThreadStateException ignore) {
          }
        }
      });

      logoPanel.add(new JLabel(" "), BorderLayout.CENTER);
      logoPanel.add(learnMore, BorderLayout.EAST);
    }

    if (!StringUtil.isEmpty(description)) {
      description = description.trim();
      if (description.startsWith(HTML_PREFIX)) {
        description = description.replaceAll(HTML_PREFIX, "");
        if (description.endsWith(HTML_SUFFIX)) {
          description = description.replaceAll(HTML_SUFFIX, "");
        }
      }
      description = description.replaceAll(ESC_NEW_LINE, "");
      String shortenedDcs = adjustStringBreaksByWidth(description, TEXT_FONT, false, PLUGIN_DSC_MAX_WIDTH, PLUGIN_DSC_MAX_ROWS);
      JLabel pluginDescription = new JLabel(shortenedDcs);
      pluginDescription.setFont(TEXT_FONT);
      if (shortenedDcs.endsWith(___HTML_SUFFIX)) {
        pluginDescription.setToolTipText(adjustStringBreaksByWidth(description, UIUtil.getToolTipFont(), false, MAX_TOOLTIP_WIDTH, 0));
      }

      gBC = new GridBagConstraints(1, y + 1, 1, 1, 0, 0, NORTHWEST, HORIZONTAL, new Insets(5, 7, 0, 0), 5, 0);
      panel.add(pluginDescription, gBC);
    }
  }

  /**
   * This method checks the width of the given string with given font applied, breaks the string into the specified number of lines if necessary,
   * and/or cuts it, so that the string does not exceed the given width (with ellipsis concatenated at the end if needed).<br>
   * It also removes all of the formatting HTML tags, except <b>&lt;br&gt;</b> and <b>&lt;li&gt;</b> (they are used for correct line breaks).
   * Returns the resulting or original string surrounded by <b>&lt;html&gt;</b> tags.
   *
   * @param string        not <code>null</code> {@link String String} value, otherwise the "Not specified." string is returned.
   * @param font          not <code>null</code> {@link Font Font} object.
   * @param isAntiAliased <code>boolean</code> value to denote whether the font is anti-aliased or not.
   * @param maxWidth      <code>int</code> value specifying maximum width of the resulting string in pixels.
   * @param maxRows       <code>int</code> value specifying the number of rows. If the value is positive, the string is modified to not exceed
   *                      the specified number, and method adds an ellipsis instead of the exceeding part. If the value is zero or negative,
   *                      the entire string is broken into lines until its end.
   * @return the resulting or original string ({@link String String}) surrounded by <b>&lt;html&gt;</b> tags.
   */
  @SuppressWarnings({"HardCodedStringLiteral"})
  private static String adjustStringBreaksByWidth(String string,
                                                  final Font font,
                                                  final boolean isAntiAliased,
                                                  final int maxWidth,
                                                  final int maxRows) {
    string = string.trim();
    if (StringUtil.isEmpty(string)) {
      return "<html>" + UIBundle.message("welcome.screen.text.not.specified.message") + "</html>";
    }

    string = string.replaceAll("<li>", " <>&gt; ");
    string = string.replaceAll("<br>", " <>");
    string = string.replaceAll("(<[^>]+?>)", " ");
    string = string.replaceAll("[\\s]{2,}", " ");
    Rectangle2D r = font.getStringBounds(string, new FontRenderContext(new AffineTransform(), isAntiAliased, false));

    if (r.getWidth() > maxWidth) {

      StringBuffer prefix = new StringBuffer();
      String suffix = string;
      int maxIdxPerLine = (int)(maxWidth / r.getWidth() * string.length());
      int lengthLeft = string.length();
      int rows = maxRows;
      if (rows <= 0) {
        rows = string.length() / maxIdxPerLine + 1;
      }

      while (lengthLeft > maxIdxPerLine && rows > 1) {
        int i;
        for (i = maxIdxPerLine; i > 0; i--) {
          if (suffix.charAt(i) == ' ') {
            prefix.append(suffix.substring(0, i)).append("<br>");
            suffix = suffix.substring(i + 1, suffix.length());
            lengthLeft = suffix.length();
            if (maxRows > 0) {
              rows--;
            }
            else {
              rows = lengthLeft / maxIdxPerLine + 1;
            }
            break;
          }
        }
        if (i == 0) {
          if (rows > 1 && maxRows <= 0) {
            prefix.append(suffix.substring(0, maxIdxPerLine)).append("<br>");
            suffix = suffix.substring(maxIdxPerLine, suffix.length());
            lengthLeft = suffix.length();
            rows--;
          }
          else {
            break;
          }
        }
      }
      if (suffix.length() > maxIdxPerLine) {
        suffix = suffix.substring(0, maxIdxPerLine - 3);
        for (int i = suffix.length() - 1; i > 0; i--) {
          if (suffix.charAt(i) == ' ') {
            if ("...".equals(suffix.substring(i - 3, i))) {
              suffix = suffix.substring(0, i - 1);
              break;
            }
            else if (suffix.charAt(i - 1) == '>') {
              //noinspection AssignmentToForLoopParameter
              i--;
            }
            else if (suffix.charAt(i - 1) == '.') {
              suffix = suffix.substring(0, i) + "..";
              break;
            }
            else {
              suffix = suffix.substring(0, i) + "...";
              break;
            }
          }
        }
      }
      string = prefix + suffix;
    }
    string = string.replaceAll(" <>", "<br>");
    return HTML_PREFIX + string + HTML_SUFFIX;
  }

  private class ActionGroupDescriptor {
    private int myIdx = -1;
    private int myCount = 0;
    private final JPanel myPanel;
    private final int myColumnIdx;

    public ActionGroupDescriptor(final String caption, final int columnIndex) {
      JPanel panel = new JPanel(new GridBagLayout()) {
        public Dimension getPreferredSize() {
          return getMinimumSize();
        }
      };
      panel.setBackground(MAIN_PANEL_BACKGROUND);

      JLabel actionGroupCaption = new JLabel(caption);
      actionGroupCaption.setFont(GROUP_CAPTION_FONT);
      actionGroupCaption.setForeground(CAPTION_COLOR);

      GridBagConstraints gBC = new GridBagConstraints(0, 0, 2, 1, 0, 0, NORTHWEST, NONE, ACTION_GROUP_CAPTION_INSETS, 0, 0);
      panel.add(actionGroupCaption, gBC);
      myPanel = panel;
      myColumnIdx = columnIndex;
    }

    public void addButton(final MyActionButton button, String commandLink, String description) {
      GridBagConstraints gBC;

      final int y = myIdx += 2;
      gBC = new GridBagConstraints(0, y, 1, 2, 0, 0, NORTHWEST, NONE, ACTION_ICON_INSETS, ACTION_BUTTON_PADDING, ACTION_BUTTON_PADDING);
      myPanel.add(button, gBC);
      button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      button.setupWithinPanel(myMainPanel, MAIN_GROUP, myCount, myColumnIdx);
      myCount++;

      JLabel name = new JLabel(underlineHtmlText(commandLink));
      name.addMouseListener(new MouseAdapter() {
        public void mouseClicked(MouseEvent e) {
          button.onPress(e);
        }
      });
      name.setForeground(CAPTION_COLOR);
      name.setFont(LINK_FONT);
      name.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      gBC = new GridBagConstraints(1, y, 1, 1, 0, 0, SOUTHWEST, NONE, ACTION_NAME_INSETS, 5, 0);
      myPanel.add(name, gBC);

      description = wrapWithHtml(description);
      JLabel shortDescription = new JLabel(description);
      shortDescription.setFont(TEXT_FONT);
      gBC = new GridBagConstraints(1, y + 1, 1, 1, 0, 0, NORTHWEST, HORIZONTAL, ACTION_DESCRIPTION_INSETS, 5, 0);
      myPanel.add(shortDescription, gBC);
    }

    private String wrapWithHtml(final String description) {
      return HTML_PREFIX + description + HTML_SUFFIX;
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    private String underlineHtmlText(final String commandLink) {
      return "<html><nobr><u>" + commandLink + "</u></nobr></html>";
    }

    private void appendActionsFromGroup(final ActionGroup group) {
      final AnAction[] actions = group.getChildren(null);
      PresentationFactory factory = new PresentationFactory();
      for (final AnAction action : actions) {
        if (action instanceof ActionGroup) {
          final ActionGroup childGroup = (ActionGroup)action;
          appendActionsFromGroup(childGroup);
        }
        else {
          Presentation presentation = factory.getPresentation(action);
          action.update(new AnActionEvent(null, DataManager.getInstance().getDataContext(myMainPanel),
                                          ActionPlaces.WELCOME_SCREEN, presentation, ActionManager.getInstance(), 0));
          if (presentation.isVisible()) {
            appendButtonForAction(action);
          }
        }
      }
    }

    public void appendButtonForAction(final AnAction action) {
      final Presentation presentation = action.getTemplatePresentation();
      final Icon icon = presentation.getIcon();
      final String text = presentation.getText();
      MyActionButton button = new ButtonWithExtension(icon, "") {
        protected void onPress(InputEvent e, MyActionButton button) {
          final ActionManager actionManager = ActionManager.getInstance();
          AnActionEvent evt = new AnActionEvent(
            null,
            DataManager.getInstance().getDataContext(e.getComponent()),
            ActionPlaces.WELCOME_SCREEN,
            action.getTemplatePresentation(),
            actionManager,
            0
          );
          action.beforeActionPerformedUpdate(evt);
          if (evt.getPresentation().isEnabled()) {
            action.actionPerformed(evt);
          }
        }
      };

      addButton(button, text, presentation.getDescription());
    }

    public JPanel getPanel() {
      return myPanel;
    }

    public int getIdx() {
      return myIdx;
    }
  }

  private abstract class MyActionButton extends JComponent implements ActionButtonComponent {
    private int myGroupIdx;
    private int myRowIdx;
    private int myColumnIdx;
    private final String myDisplayName;
    private final Icon myIcon;

    private MyActionButton(Icon icon, String displayName) {
      myDisplayName = displayName;
      myIcon = new LabeledIcon(icon != null ? icon : DEFAULT_ICON, getDisplayName(), null);
    }

    private void setupWithinPanel(JPanel panel, int groupIdx, int rowIdx, int columnIdx) {
      myGroupIdx = groupIdx;
      myRowIdx = rowIdx;
      myColumnIdx = columnIdx;
      setToolTipText(null);
      setupListeners(panel);
    }

    protected String getDisplayName() {
      return myDisplayName != null ? myDisplayName : "";
    }

    public Dimension getMaximumSize() {
      return ACTION_BUTTON_SIZE;
    }

    public Dimension getMinimumSize() {
      return ACTION_BUTTON_SIZE;
    }

    public Dimension getPreferredSize() {
      return ACTION_BUTTON_SIZE;
    }

    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      ActionButtonLook look = ActionButtonLook.IDEA_LOOK;
      paintBackground(g);
      look.paintIcon(g, this, myIcon);
      paintBorder(g);
    }

    protected Color getNormalButtonColor() {
      return ACTION_BUTTON_COLOR;
    }

    protected void paintBackground(Graphics g) {
      Dimension dimension = getSize();
      int state = getPopState();
      if (state != NORMAL) {
        if (state == POPPED) {
          g.setColor(BUTTON_POPPED_COLOR);
          g.fillRect(0, 0, dimension.width, dimension.height);
        }
        else {
          g.setColor(BUTTON_PUSHED_COLOR);
          g.fillRect(0, 0, dimension.width, dimension.height);
        }
      }
      else {
        g.setColor(getNormalButtonColor());
        g.fillRect(0, 0, dimension.width, dimension.height);
      }
      if (state == PUSHED) {
        g.setColor(BUTTON_PUSHED_COLOR);
        g.fillRect(0, 0, dimension.width, dimension.height);
      }
    }

    public int getPopState() {
      if (myPressedButton == this) return PUSHED;
      if (myPressedButton != null) return NORMAL;
      if (mySelectedColumn == myColumnIdx &&
          mySelectedRow == myRowIdx &&
          mySelectedGroup == myGroupIdx) {
        return POPPED;
      }
      return NORMAL;
    }

    private void setupListeners(final JPanel panel) {
      addMouseListener(new MouseAdapter() {
        public void mousePressed(MouseEvent e) {
          myPressedButton = MyActionButton.this;
          panel.repaint();
        }

        public void mouseReleased(MouseEvent e) {
          if (myPressedButton == MyActionButton.this) {
            myPressedButton = null;
            onPress(e);
          }
          else {
            myPressedButton = null;
          }

          panel.repaint();
        }

        public void mouseExited(MouseEvent e) {
          mySelectedColumn = -1;
          mySelectedRow = -1;
          mySelectedGroup = -1;
          panel.repaint();
        }
      });

      addMouseMotionListener(new MouseMotionListener() {
        public void mouseDragged(MouseEvent e) {
        }

        public void mouseMoved(MouseEvent e) {
          mySelectedColumn = myColumnIdx;
          mySelectedRow = myRowIdx;
          mySelectedGroup = myGroupIdx;
          panel.repaint();
        }
      });
    }

    protected abstract void onPress(InputEvent e);
  }

  private abstract class ButtonWithExtension extends MyActionButton {
    private ButtonWithExtension(Icon icon, String displayName) {
      super(icon, displayName);
    }

    protected void onPress(InputEvent e) {
      onPress(e, this);
    }

    protected abstract void onPress(InputEvent e, MyActionButton button);
  }

  private static JScrollPane scrollPane(final JPanel panel, final Color borderColor) {
    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(panel);
    scrollPane.setBorder(borderColor != null ?
                         BorderFactory.createLineBorder(borderColor, 1) : BorderFactory.createEmptyBorder());
    scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
    return scrollPane;
  }
}
