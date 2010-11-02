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
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.impl.content.GraphicsConfig;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.BaseButtonBehavior;
import com.intellij.util.ui.TimedDeadzone;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * User: spLeaner
 */
public class ConfigurationErrorsComponent extends JPanel implements Disposable, ListDataListener {
  private static final boolean ONE_LINE = true;
  private static final boolean MULTI_LINE = false;

  private static final Icon EXPAND = IconLoader.getIcon("/actions/expandall.png");
  private static final Icon COLLAPSE = IconLoader.getIcon("/actions/collapseall.png");
  private static final Icon FIX = IconLoader.findIcon("/actions/quickfixBulb.png");
  private static final Icon IGNORE = IconLoader.findIcon("/toolbar/unknown.png");
  private static final Icon NAVIGATE = IconLoader.findIcon("/general/autoscrollToSource.png");

  private ConfigurationErrorsListModel myConfigurationErrorsListModel;
  private ErrorView myCurrentView;

  private OneLineErrorComponent myOneLineErrorComponent;
  private MultiLineErrorComponent myMultiLineErrorComponent;

  public ConfigurationErrorsComponent(@NotNull final Project project) {
    setLayout(new BorderLayout());
    myConfigurationErrorsListModel = new ConfigurationErrorsListModel(project);
    myConfigurationErrorsListModel.addListDataListener(this);

    addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(final ComponentEvent e) {
        revalidate();
        repaint();
      }
    });

    ensureCurrentViewIs(ONE_LINE);
    Disposer.register(this, myConfigurationErrorsListModel);
  }

  @Override
  public void dispose() {
    if (myConfigurationErrorsListModel != null) {
      myConfigurationErrorsListModel.removeListDataListener(this);
      myConfigurationErrorsListModel = null;
    }
  }

  private void ensureCurrentViewIs(final boolean oneLine) {
    if (oneLine) {
      if (myCurrentView instanceof OneLineErrorComponent) return;
      if (myOneLineErrorComponent == null) {
        myOneLineErrorComponent = new OneLineErrorComponent(myConfigurationErrorsListModel) {
          @Override
          public void onViewChange() {
            ensureCurrentViewIs(MULTI_LINE);
          }
        };
      }

      if (myCurrentView != null) {
        remove(myCurrentView.self());
      }

      myCurrentView = myOneLineErrorComponent;
    } else {
      if (myCurrentView instanceof MultiLineErrorComponent) return;
      if (myMultiLineErrorComponent == null) {
        myMultiLineErrorComponent = new MultiLineErrorComponent(myConfigurationErrorsListModel) {
          @Override
          public void onViewChange() {
            ensureCurrentViewIs(ONE_LINE);
          }
        };
      }

      if (myCurrentView != null) {
        remove(myCurrentView.self());
      }

      myCurrentView = myMultiLineErrorComponent;
    }

    add(myCurrentView.self(), BorderLayout.CENTER);
    myCurrentView.updateView();
    revalidate();
    repaint();
  }

  @Override
  public void intervalAdded(final ListDataEvent e) {
    updateCurrentView();
  }

  @Override
  public void intervalRemoved(final ListDataEvent e) {
    updateCurrentView();
  }

  @Override
  public void contentsChanged(final ListDataEvent e) {
    updateCurrentView();
  }

  private void updateCurrentView() {
    if (myCurrentView instanceof MultiLineErrorComponent && myConfigurationErrorsListModel.getSize() == 0) {
      ensureCurrentViewIs(ONE_LINE);
    }

    myCurrentView.updateView();
  }

  private interface ErrorView {
    void updateView();
    void onViewChange();
    JComponent self();
  }

  private abstract static class MultiLineErrorComponent extends JPanel implements ErrorView {
    private ConfigurationErrorsListModel myModel;
    private JList myList = new JBList();

    protected MultiLineErrorComponent(@NotNull final ConfigurationErrorsListModel model) {
      setLayout(new BorderLayout());
      setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));

      myModel = model;
      myList.setModel(model);
      myList.setCellRenderer(new ErrorListRenderer(myList));
      myList.setBackground(UIUtil.getPanelBackground());

      myList.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(final MouseEvent e) {
          if (!e.isPopupTrigger()) {
            processListMouseEvent(e, true);
          }
        }
      });

      myList.addComponentListener(new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
          myList.setCellRenderer(new ErrorListRenderer(myList)); // request cell renderer size invalidation
          updatePreferredSize();
        }
      });

      add(new JBScrollPane(myList), BorderLayout.CENTER);
      add(buildToolbar(), BorderLayout.WEST);
    }

    private void processListMouseEvent(final MouseEvent e, final boolean click) {
      final int index = myList.locationToIndex(e.getPoint());
      if (index > -1) {
        final Object value = myList.getModel().getElementAt(index);
        if (value != null && value instanceof ConfigurationError) {
          final ConfigurationError error = (ConfigurationError)value;
          final Component renderer = myList.getCellRenderer().getListCellRendererComponent(myList, value, index, false, false);
          if (renderer instanceof ErrorListRenderer) {
            final Rectangle bounds = myList.getCellBounds(index, index);
            renderer.setBounds(bounds);
            renderer.doLayout();

            final Point point = e.getPoint();
            point.translate(-bounds.x, -bounds.y);

            final Component deepestComponentAt = SwingUtilities.getDeepestComponentAt(renderer, point.x, point.y);
            if (deepestComponentAt instanceof ToolbarAlikeButton) {
              final String name = ((ToolbarAlikeButton)deepestComponentAt).getButtonName();
              if ("FIX".equals(name)) {
                onClickFix(error);
                error.fix();
              } else {
                onClickIgnore(error);
              }
            }
          }
        }
      }
    }

    private void onClickIgnore(@NotNull final ConfigurationError error) {
      error.ignore(!error.isIgnored());
      final ListModel model = myList.getModel();
      if (model instanceof ConfigurationErrorsListModel) {
        ((ConfigurationErrorsListModel)model).update(error);
      }
    }

    private void onClickFix(@NotNull final ConfigurationError error) {
      error.fix();
    }

    @Override
    public void addNotify() {
      super.addNotify();
      updatePreferredSize();
    }

    private void updatePreferredSize() {
      final Window window = SwingUtilities.getWindowAncestor(this);
      if (window != null) {
        final Dimension d = window.getSize();
        final Dimension preferredSize = getPreferredSize();
        setPreferredSize(new Dimension(preferredSize.width, d.height / 4));
        setMinimumSize(new Dimension(preferredSize.width, 100));
      }
    }

    private JComponent buildToolbar() {
      final JPanel result = new JPanel();
      result.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
      result.setLayout(new BorderLayout());
      result.add(new ToolbarAlikeButton(COLLAPSE) {
        {
          setToolTipText("Collapse");
        }

        @Override
        public void onClick() {
          onViewChange();
        }
      }, BorderLayout.NORTH);

      return result;
    }

    @Override
    public void updateView() {
    }

    @Override
    public JComponent self() {
      return this;
    }

    public abstract void onViewChange();
  }

  private abstract static class ToolbarAlikeButton extends JComponent {
    private BaseButtonBehavior myBehavior;
    private Icon myIcon;
    private String myName;

    private ToolbarAlikeButton(@NotNull final Icon icon, @NotNull final String name) {
      this(icon);
      myName = name;
    }

    private ToolbarAlikeButton(@NotNull final Icon icon) {
      myIcon = icon;

      myBehavior = new BaseButtonBehavior(this, TimedDeadzone.NULL) {
        @Override
        protected void execute(MouseEvent e) {
          onClick();
        }
      };

      setOpaque(false);
    }

    public String getButtonName() {
      return myName;
    }

    public void onClick() {}

    @Override
    public Insets getInsets() {
      return new Insets(2, 2, 2, 2);
    }

    @Override
    public Dimension getPreferredSize() {
      return getMinimumSize();
    }

    @Override
    public Dimension getMinimumSize() {
      final Insets insets = getInsets();
      return new Dimension(myIcon.getIconWidth() + insets.left + insets.right, myIcon.getIconHeight() + insets.top + insets.bottom);
    }

    @Override
    public void paint(final Graphics g) {
      final Insets insets = getInsets();
      final Dimension d = getSize();

      int x = (d.width - myIcon.getIconWidth() - insets.left - insets.right) / 2;
      int y = (d.height - myIcon.getIconHeight() - insets.top - insets.bottom) / 2;

      if (myBehavior.isHovered()) {
        // todo
      }

      if (myBehavior.isPressedByMouse()) {
        x += 1;
        y += 1;
      }

      myIcon.paintIcon(this, g, x + insets.left, y + insets.top);
    }
  }

  private static class ErrorListRenderer extends JComponent implements ListCellRenderer {
    private boolean mySelected;
    private boolean myHasFocus;
    private JTextPane myText;
    private JTextPane myFakeTextPane;
    private JViewport myFakeViewport;
    private JList myList;
    private JPanel myButtonsPanel;
    private JPanel myFixGroup;

    private ErrorListRenderer(@NotNull final JList list) {
      setLayout(new BorderLayout());
      setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
      setOpaque(false);

      myList = list;

      myText = new JTextPane() {
        @Override
        public void setCursor(Cursor cursor) {
          super.setCursor(cursor);
          //onCursorChanged(cursor);
        }
      };

      myButtonsPanel = new JPanel(new BorderLayout());
      myButtonsPanel.setBorder(BorderFactory.createEmptyBorder(5, 3, 5, 3));
      myButtonsPanel.setOpaque(false);
      final JPanel buttons = new JPanel();
      buttons.setOpaque(false);
      buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
      myButtonsPanel.add(buttons, BorderLayout.NORTH);
      add(myButtonsPanel, BorderLayout.EAST);

      myFixGroup = new JPanel();
      myFixGroup.setOpaque(false);
      myFixGroup.setLayout(new BoxLayout(myFixGroup, BoxLayout.Y_AXIS));

      myFixGroup.add(new ToolbarAlikeButton(FIX, "FIX") {});
      myFixGroup.add(Box.createHorizontalStrut(3));
      buttons.add(myFixGroup);

      buttons.add(new ToolbarAlikeButton(NAVIGATE, "NAVIGATE") {});
      buttons.add(Box.createHorizontalStrut(3));

      buttons.add(new ToolbarAlikeButton(IGNORE, "IGNORE") {});

      myFakeTextPane = new JTextPane();
      myText.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
      myFakeTextPane.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
      myText.setOpaque(false);
      if (UIUtil.isUnderNimbusLookAndFeel()) {
        myText.setBackground(new Color(0, 0, 0, 0));
      }

      myText.setEditable(false);
      myFakeTextPane.setEditable(false);
      myText.setEditorKit(UIUtil.getHTMLEditorKit());
      myFakeTextPane.setEditorKit(UIUtil.getHTMLEditorKit());

      myFakeViewport = new JViewport();
      myFakeViewport.setView(myFakeTextPane);

      add(myText, BorderLayout.CENTER);
    }

    @Override
    public Dimension getPreferredSize() {
      final Container parent = myList.getParent();
      if (parent != null) {
        myFakeTextPane.setText(myText.getText());
        final Dimension size = parent.getSize();
        myFakeViewport.setSize(size);
        final Dimension preferredSize = myFakeTextPane.getPreferredSize();

        final Dimension buttonsPrefSize = myButtonsPanel.getPreferredSize();
        final int maxHeight = Math.max(buttonsPrefSize.height, preferredSize.height);

        final Insets insets = getInsets();
        return new Dimension(Math.min(size.width - 20, preferredSize.width), maxHeight + insets.top + insets.bottom);
      }

      return super.getPreferredSize();
    }

    @Override
    public Component getListCellRendererComponent(final JList list, final Object value, final int index, final boolean isSelected, final boolean cellHasFocus) {
      final ConfigurationError error = (ConfigurationError)value;

      myList = list;

      mySelected = isSelected;
      myHasFocus = cellHasFocus;

      myFixGroup.setVisible(error.canBeFixed());

      myText.setText(error.getDescription());

      setBackground(error.isIgnored() ? MessageType.WARNING.getPopupBackground() : MessageType.ERROR.getPopupBackground());
      return this;
    }

    @Override
    protected void paintComponent(Graphics g) {
      final Graphics2D g2d = (Graphics2D)g;

      final Rectangle bounds = getBounds();
      final Insets insets = getInsets();

      final GraphicsConfig cfg = new GraphicsConfig(g);
      cfg.setAntialiasing(true);

      final Shape shape = new RoundRectangle2D.Double(insets.left, insets.top, bounds.width - 1 - insets.left - insets.right,
                                                      bounds.height - 1 - insets.top - insets.bottom, 6, 6);

      if (mySelected) {
        g2d.setColor(UIUtil.getListSelectionBackground());
        g2d.fillRect(0, 0, bounds.width, bounds.height);
      }

      g2d.setColor(Color.WHITE);
      g2d.fill(shape);


      Color bgColor = getBackground();

      g2d.setColor(bgColor);
      g2d.fill(shape);

      g2d.setColor(myHasFocus || mySelected ? getBackground().darker().darker() : getBackground().darker());
      g2d.draw(shape);
      cfg.restore();

      super.paintComponent(g);
    }
  }

  private abstract static class OneLineErrorComponent extends JComponent implements ErrorView, LinkListener {
    private LinkLabel myErrorsLabel = new LinkLabel(null, null);
    private LinkLabel myIgnoredErrorsLabel = new LinkLabel(null, null);
    private JLabel mySingleErrorLabel = new JLabel();

    private ConfigurationErrorsListModel myModel;

    private OneLineErrorComponent(@NotNull final ConfigurationErrorsListModel model) {
      myModel = model;

      setLayout(new BorderLayout());
      setOpaque(true);

      updateLabel(myErrorsLabel, MessageType.ERROR.getPopupBackground(), this);
      updateLabel(mySingleErrorLabel, MessageType.ERROR.getPopupBackground(), null);
      updateLabel(myIgnoredErrorsLabel, MessageType.WARNING.getPopupBackground(), this);
    }

    private static void updateLabel(@NotNull final JLabel label, @NotNull final Color bgColor, @Nullable final LinkListener listener) {
      label.setBorder(BorderFactory.createEmptyBorder(3, 5, 3, 5));
      label.setOpaque(true);
      label.setBackground(bgColor);
      if (label instanceof LinkLabel) {
        ((LinkLabel)label).setListener(listener, null);
      }
    }

    @Override
    public void updateView() {
      if (myModel.getSize() == 0) {
        setBorder(null);
      } else {
        if (getBorder() == null) setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(5, 0, 5, 0, UIUtil.getPanelBackground()),
                                                   BorderFactory.createLineBorder(UIUtil.getPanelBackgound().darker())));
      }

      final List<ConfigurationError> errors = myModel.getErrors();
      if (errors.size() > 0) {
        if (errors.size() == 1) {
          mySingleErrorLabel.setText(myModel.getErrors().get(0).getPlainTextTitle());
        } else {
          myErrorsLabel.setText(String.format("%s errors found", errors.size()));
        }
      }

      final List<ConfigurationError> ignoredErrors = myModel.getIgnoredErrors();
      if (ignoredErrors.size() > 0) {
        myIgnoredErrorsLabel.setText(String.format("%s ignored error%s", ignoredErrors.size(), ignoredErrors.size() == 1 ? "" : "s"));
      }

      removeAll();
      if (errors.size() > 0) {
        if (errors.size() == 1) {
          add(wrapLabel(mySingleErrorLabel, errors.get(0)), BorderLayout.CENTER);
          mySingleErrorLabel.setToolTipText(errors.get(0).getDescription());
        } else {
          add(myErrorsLabel, BorderLayout.CENTER);
        }
      }

      if (ignoredErrors.size() > 0) {
        add(myIgnoredErrorsLabel, errors.size() > 0 ? BorderLayout.EAST : BorderLayout.CENTER);
      }

      revalidate();
      repaint();
    }

    private JComponent wrapLabel(@NotNull final JLabel label, @NotNull final ConfigurationError configurationError) {
      final JPanel result = new JPanel(new BorderLayout());
      result.setBackground(label.getBackground());
      result.add(label, BorderLayout.CENTER);

      final JPanel buttonsPanel = new JPanel();
      buttonsPanel.setOpaque(false);
      buttonsPanel.setLayout(new BoxLayout(buttonsPanel, BoxLayout.X_AXIS));

      if (configurationError.canBeFixed()) {
        buttonsPanel.add(new ToolbarAlikeButton(FIX) {
          {
            setToolTipText("Fix error");
          }

          @Override
          public void onClick() {
            final Object o = myModel.getElementAt(0);
            if (o instanceof ConfigurationError) {
              ((ConfigurationError)o).fix();
              updateView();
              final Container ancestor = SwingUtilities.getAncestorOfClass(ConfigurationErrorsComponent.class, this);
              if (ancestor != null && ancestor instanceof JComponent) {
                ((JComponent)ancestor).revalidate();
                ancestor.repaint();
              }
            }
          }
        });

        buttonsPanel.add(Box.createHorizontalStrut(3));
      }

      buttonsPanel.add(new ToolbarAlikeButton(NAVIGATE) {
        {
          setToolTipText("Navigate to error");
        }

        @Override
        public void onClick() {
          final Object o = myModel.getElementAt(0);
          if (o instanceof ConfigurationError) {
            ((ConfigurationError)o).navigate();
          }
        }
      });

      buttonsPanel.add(Box.createHorizontalStrut(3));

      buttonsPanel.add(new ToolbarAlikeButton(IGNORE) {
        {
          setToolTipText("Ignore error");
        }

        @Override
        public void onClick() {
          final Object o = myModel.getElementAt(0);
          if (o instanceof ConfigurationError) {
            ((ConfigurationError)o).ignore(!((ConfigurationError)o).isIgnored());
            updateView();
          }
        }
      });
      buttonsPanel.add(Box.createHorizontalStrut(5));

      result.add(buttonsPanel, BorderLayout.EAST);

      return result;
    }

    @Override
    public JComponent self() {
      return this;
    }

    public abstract void onViewChange();

    @Override
    public void linkSelected(LinkLabel aSource, Object aLinkData) {
      onViewChange();
    }
  }

  private static class ConfigurationErrorsListModel extends AbstractListModel implements ConfigurationErrors, Disposable {
    private MessageBusConnection myConnection;
    private List<ConfigurationError> myErrorsList = new ArrayList<ConfigurationError>();

    private ConfigurationErrorsListModel(@NotNull final Project project) {
      myConnection = project.getMessageBus().connect();
      myConnection.subscribe(TOPIC, this);
    }

    @Override
    public int getSize() {
      return myErrorsList.size();
    }

    @Override
    public Object getElementAt(int index) {
      return myErrorsList.get(index);
    }

    @Override
    public void addError(@NotNull ConfigurationError error) {
      if (!myErrorsList.contains(error)) {
        int ndx = 0;
        if (error.isIgnored()) {
          ndx = myErrorsList.size();
        }

        myErrorsList.add(ndx, error);
        fireIntervalAdded(this, ndx, ndx);
      }
    }

    @Override
    public void removeError(@NotNull ConfigurationError error) {
      if (myErrorsList.contains(error)) {
        final int ndx = myErrorsList.indexOf(error);
        myErrorsList.remove(ndx);
        fireIntervalRemoved(this, ndx, ndx);
      }
    }

    public List<ConfigurationError> getErrors() {
      return ContainerUtil.filter(myErrorsList, new Condition<ConfigurationError>() {
        @Override
        public boolean value(final ConfigurationError error) {
          return !error.isIgnored();
        }
      });
    }

    public List<ConfigurationError> getIgnoredErrors() {
      return ContainerUtil.filter(myErrorsList, new Condition<ConfigurationError>() {
        @Override
        public boolean value(final ConfigurationError error) {
          return error.isIgnored();
        }
      });
    }

    @Override
    public void dispose() {
      if (myConnection != null) {
        myConnection.disconnect();
        myConnection = null;
      }
    }

    public void update(final ConfigurationError error) {
      final int ndx = myErrorsList.indexOf(error);
      if (ndx >= 0) {
        fireContentsChanged(this, ndx, ndx);
      }
    }
  }
}

