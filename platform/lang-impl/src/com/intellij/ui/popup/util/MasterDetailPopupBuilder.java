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
package com.intellij.ui.popup.util;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.speedSearch.FilteringListModel;
import com.intellij.util.Function;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class MasterDetailPopupBuilder implements MasterController {

  private static final Color BORDER_COLOR = Gray._135;
  private Project myProject;
  private ActionGroup myActions;
  private Delegate myDelegate;
  private boolean myCloseOnEnter;

  private DetailView myDetailView;

  private JLabel myPathLabel;

  private JBPopup myPopup;
  private JComponent myChooserComponent;
  private ActionToolbar myActionToolbar;
  private boolean myAddDetailViewToEast = true;
  private Dimension myMinSize;
  private boolean myCancelOnWindowDeactivation = true;
  private Runnable myDoneRunnable;
  private boolean myCancelOnClickOutside;

  private final DetailController myDetailController = new DetailController(this);


  public String getDimensionServiceKey() {
    return myDimensionServiceKey;
  }

  public void setDimensionServiceKey(String dimensionServiceKey) {
    myDimensionServiceKey = dimensionServiceKey;
  }

  private String myDimensionServiceKey;


  public MasterDetailPopupBuilder setDetailView(DetailView detailView) {
    myDetailView = detailView;
    myDetailController.setDetailView(myDetailView);
    return this;
  }

  public ActionToolbar getActionToolbar() {
    return myActionToolbar;
  }

  public MasterDetailPopupBuilder(Project project) {
    myProject = project;
  }

  public JBPopup createMasterDetailPopup() {

    setupRenderer();

    myPathLabel = new JLabel(" ");
    myPathLabel.setHorizontalAlignment(SwingConstants.RIGHT);

    final Font font = myPathLabel.getFont();
    myPathLabel.setFont(font.deriveFont((float)10));

    if (myDetailView == null) {
      myDetailView = new DetailViewImpl(myProject);
    }

    JPanel footerPanel = new JPanel(new BorderLayout()) {
      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.setColor(BORDER_COLOR);
        g.drawLine(0, 0, getWidth(), 0);
      }
    };


    Runnable runnable = new Runnable() {
      public void run() {
        IdeFocusManager.getInstance(myProject).doWhenFocusSettlesDown(new Runnable() {
          public void run() {
            Object[] values = getSelectedItems();
            if (values.length == 1) {
              myDelegate.itemChosen((ItemWrapper)values[0], myProject, myPopup, false);
            }
            else {
              for (Object value : values) {
                if (value instanceof ItemWrapper) {
                  myDelegate.itemChosen((ItemWrapper)value, myProject, myPopup, false);
                }
              }
            }
          }
        });
      }
    };

    footerPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
    footerPanel.add(myPathLabel);

    JComponent toolBar = null;
    if (myActions != null) {
      myActionToolbar = ActionManager.getInstance().createActionToolbar("", myActions, true);
      myActionToolbar.setReservePlaceAutoPopupIcon(false);
      myActionToolbar.setMinimumButtonSize(new Dimension(20, 20));
      toolBar = myActionToolbar.getComponent();
      toolBar.setOpaque(false);
    }


    final PopupChooserBuilder builder = createInnerBuilder().
      setMovable(true).
      setResizable(true).
      setAutoselectOnMouseMove(false).
      setSettingButton(toolBar).
      setSouthComponent(footerPanel).
      setCancelOnWindowDeactivation(myCancelOnWindowDeactivation).
      setCancelOnClickOutside(myCancelOnClickOutside);


    if (myAddDetailViewToEast) {
      builder.
        setEastComponent((JComponent)myDetailView);
    }

    if (myDoneRunnable != null) {

      ActionListener actionListener = new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent event) {
          myDoneRunnable.run();
        }
      };
      //native button is pretty enough
      if (SystemInfo.isMacOSLion || SystemInfo.isMacOSMountainLion) {
        final JButton done = new JButton("Done");
        done.setMnemonic('o');
        done.addActionListener(actionListener);

        builder.setCommandButton(new ActiveComponent() {
          @Override
          public void setActive(boolean active) {
            //To change body of implemented methods use File | Settings | File Templates.
          }

          @Override
          public JComponent getComponent() {
            return done;
          }
        });
      }
      else {
        builder.setCommandButton(new InplaceButton("Close", AllIcons.Actions.CloseNew, actionListener));
      }
    }

    String title = myDelegate.getTitle();
    if (title != null) {
      builder.setTitle(title);
    }


    builder.
      setItemChoosenCallback(runnable).
      setCloseOnEnter(myCloseOnEnter).
      setMayBeParent(true).
      setDimensionServiceKey(myDimensionServiceKey).
      setFilteringEnabled(new Function<Object, String>() {
        public String fun(Object o) {
          return ((ItemWrapper)o).speedSearchText();
        }
      });

    if (myMinSize != null) {
      builder.setMinSize(myMinSize);
    }

    myPopup = builder.createPopup();
    builder.getScrollPane().setBorder(IdeBorderFactory.createBorder(SideBorder.RIGHT));
    myPopup.addListener(new JBPopupListener() {
      @Override
      public void beforeShown(LightweightWindowEvent event) {
        //To change body of implemented methods use File | Settings | File Templates.
      }

      @Override
      public void onClosed(LightweightWindowEvent event) {
        myDetailView.clearEditor();
      }
    });

    if (myDoneRunnable != null) {
      new AnAction("Done") {
        @Override
        public void actionPerformed(AnActionEvent e) {
          myDoneRunnable.run();
        }
      }.registerCustomShortcutSet(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK, myPopup.getContent());
    }

    return myPopup;
  }

  private void setupRenderer() {
    if (myChooserComponent instanceof JList) {
      final JList list = (JList)myChooserComponent;
      list.setCellRenderer(new ListItemRenderer(myDelegate, myProject));
    }
  }

  private PopupChooserBuilder createInnerBuilder() {
    if (myChooserComponent instanceof JList) {
      return new PopupChooserBuilder((JList)myChooserComponent);
    }
    else if (myChooserComponent instanceof JTree) {
      return new PopupChooserBuilder((JTree)myChooserComponent);
    }
    return null;
  }

  @Override
  public ItemWrapper[] getSelectedItems() {
    Object[] values = new Object[0];
    if (myChooserComponent instanceof JList) {
      values = ((JList)myChooserComponent).getSelectedValues();
    }
    else if (myChooserComponent instanceof JTree) {
      values = myDelegate.getSelectedItemsInTree();
    }
    ItemWrapper[] items = new ItemWrapper[values.length];
    for (int i = 0; i < values.length; i++) {
      items[i] = (ItemWrapper)values[i];
    }
    return items;
  }

  public void setAddDetailViewToEast(boolean addDetailViewToEast) {
    myAddDetailViewToEast = addDetailViewToEast;
  }

  public MasterDetailPopupBuilder setMinSize(Dimension minSize) {
    myMinSize = minSize;
    return this;
  }

  public MasterDetailPopupBuilder setCancelOnWindowDeactivation(boolean cancelOnWindowDeactivation) {
    myCancelOnWindowDeactivation = cancelOnWindowDeactivation;
    return this;
  }

  public void setDoneRunnable(Runnable doneRunnable) {
    myDoneRunnable = doneRunnable;
  }

  public void setCancelOnClickOutside(boolean cancelOnClickOutside) {
    myCancelOnClickOutside = cancelOnClickOutside;
  }

  @Override
  public JLabel getPathLabel() {
    return myPathLabel;
  }

  public static boolean allowedToRemoveItems(Object[] values) {
    for (Object value : values) {
      ItemWrapper item = (ItemWrapper)value;
      if (!item.allowedToRemove()) {
        return false;
      }
    }
    return values.length > 0;
  }

  public void removeSelectedItems(Project project) {
    if (myChooserComponent instanceof JList) {
      final JList list = (JList)myChooserComponent;
      int index = list.getSelectedIndex();
      if (index == -1 || index >= list.getModel().getSize()) {
        return;
      }
      Object[] values = list.getSelectedValues();
      for (Object value : values) {
        ItemWrapper item = (ItemWrapper)value;

        DefaultListModel model = list.getModel() instanceof DefaultListModel
                                 ? (DefaultListModel)list.getModel()
                                 : (DefaultListModel)((FilteringListModel)list.getModel()).getOriginalModel();
        if (item.allowedToRemove()) {
          model.removeElement(item);

          if (model.getSize() > 0) {
            if (model.getSize() == index) {
              list.setSelectedIndex(model.getSize() - 1);
            }
            else if (model.getSize() > index) {
              list.setSelectedIndex(index);
            }
          }
          else {
            list.clearSelection();
          }
          item.removed(project);
        }
      }
    }
    else {
      final Object[] items = getSelectedItems();
      for (Object item : items) {
        ((ItemWrapper)item).removed(project);
      }
    }
  }

  public MasterDetailPopupBuilder setActionsGroup(@Nullable ActionGroup actions) {
    myActions = actions;
    return this;
  }

  public MasterDetailPopupBuilder setTree(final JTree tree) {
    setChooser(tree);
    myDetailController.setTree(tree);
    return this;
  }

  public MasterDetailPopupBuilder setList(final JBList list) {
    setChooser(list);
    myDetailController.setList(list);
    return this;
  }

  private void setChooser(JComponent list) {
    myChooserComponent = list;
    list.addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_DELETE) {
          removeSelectedItems(myProject);
        }
        else if (e.getModifiersEx() == 0) {
          myDelegate.handleMnemonic(e, myProject, myPopup);
        }
      }
    });
    new AnAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        ItemWrapper[] items = getSelectedItems();
        if (items.length > 0) {
          myDelegate.itemChosen(items[0], myProject, myPopup, true);
        }
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)), list);
  }

  public MasterDetailPopupBuilder setDelegate(Delegate delegate) {
    myDelegate = delegate;
    return this;
  }

  public MasterDetailPopupBuilder setCloseOnEnter(boolean closeOnEnter) {
    myCloseOnEnter = closeOnEnter;
    return this;
  }

  public interface Delegate {
    @Nullable
    String getTitle();

    void handleMnemonic(KeyEvent e, Project project, JBPopup popup);

    @Nullable
    JComponent createAccessoryView(Project project);

    Object[] getSelectedItemsInTree();

    void itemChosen(ItemWrapper item, Project project, JBPopup popup, boolean withEnterOrDoubleClick);
  }

  public static class ListItemRenderer extends JPanel implements ListCellRenderer {
    private final Project myProject;
    private final ColoredListCellRenderer myRenderer;
    private Delegate myDelegate;

    private ListItemRenderer(Delegate delegate, Project project) {
      super(new BorderLayout());
      myProject = project;
      setBackground(UIUtil.getListBackground());
      this.myDelegate = delegate;
      final JComponent accessory = myDelegate.createAccessoryView(project);

      if (accessory != null) {
        add(accessory, BorderLayout.WEST);
      }

      myRenderer = new ItemWrapperListRenderer(myProject, accessory);
      add(myRenderer, BorderLayout.CENTER);
    }

    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      if (value instanceof SplitterItem) {
        String label = ((SplitterItem)value).getText();
        final TitledSeparator separator = new TitledSeparator(label);
        separator.setBackground(UIUtil.getListBackground());
        separator.setForeground(UIUtil.getListForeground());
        return separator;
      }
      myRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      myRenderer.revalidate();

      return this;
    }
  }
}
