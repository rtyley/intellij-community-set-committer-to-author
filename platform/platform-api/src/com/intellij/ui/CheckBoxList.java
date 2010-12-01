package com.intellij.ui;

import com.intellij.ui.components.JBList;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicRadioButtonUI;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author oleg
 */
public class CheckBoxList extends JBList {
  private static final int DEFAULT_CHECK_BOX_WIDTH = 20;
  private CheckBoxListListener checkBoxListListener;

  public CheckBoxList(final CheckBoxListListener checkBoxListListener) {
    this(new CheckBoxListModel(), checkBoxListListener);
  }
  public CheckBoxList(final CheckBoxListModel dataModel, final CheckBoxListListener checkBoxListListener) {
    this(dataModel);
    setCheckBoxListListener(checkBoxListListener);
  }

  public CheckBoxList() {
    this(new CheckBoxListModel());
  }

  public CheckBoxList(final CheckBoxListModel dataModel) {
    super();
    setModel(dataModel);
    setCellRenderer(new CellRenderer());
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    setBorder(BorderFactory.createEtchedBorder());
    addKeyListener(new KeyAdapter() {
      @Override
      public void keyTyped(KeyEvent e) {
        if (e.getKeyChar() == ' ') {
          int[] indices = CheckBoxList.this.getSelectedIndices();
          for (int index : indices) {
            if (index >= 0) {
              JCheckBox checkbox = (JCheckBox)getModel().getElementAt(index);
              setSelected(checkbox, index);
            }
          }
        }
      }
    });
    addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (isEnabled()) {
          int index = locationToIndex(e.getPoint());

          if (index != -1) {
            JCheckBox checkbox = (JCheckBox)getModel().getElementAt(index);
            int iconArea;
            try {
              iconArea = ((BasicRadioButtonUI)checkbox.getUI()).getDefaultIcon().getIconWidth();
            }
            catch (ClassCastException c) {
              iconArea = DEFAULT_CHECK_BOX_WIDTH;
            }
            if (e.getX() < iconArea) {
              setSelected(checkbox, index);
            }
          }
        }
      }
    });
  }

  public CheckBoxListModel getCBModel() {
    return (CheckBoxListModel)getModel();
  }

  public boolean isItemSelected(int index) {
    return ((JCheckBox)getModel().getElementAt(index)).isSelected();  
  }

  private void setSelected(JCheckBox checkbox, int index) {
    boolean value = !checkbox.isSelected();
    checkbox.setSelected(value);
    repaint();

    final CheckBoxListModel model = getCBModel();
    model.fireContentsChanged(model, index, index);

    if (checkBoxListListener != null) {
      checkBoxListListener.checkBoxSelectionChanged(index, value);
    }
  }

  public void setCheckBoxListListener(CheckBoxListListener checkBoxListListener) {
    this.checkBoxListListener = checkBoxListListener;
  }

  private class CellRenderer implements ListCellRenderer {
    private final Border mySelectedBorder;
    private final Border myBorder;

    private CellRenderer() {
      mySelectedBorder = UIManager.getBorder("List.focusCellHighlightBorder");
      final Insets borderInsets = mySelectedBorder.getBorderInsets(new JCheckBox());
      myBorder = new EmptyBorder(borderInsets);
    }

    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      JCheckBox checkbox = (JCheckBox)value;
      if (!UIUtil.isUnderNimbusLookAndFeel()) {
        checkbox.setBackground(getBackground(isSelected, checkbox));
        checkbox.setForeground(getForeground(isSelected, checkbox));
      }
      checkbox.setEnabled(isEnabled());
      checkbox.setFont(getFont(checkbox));
      checkbox.setFocusPainted(false);
      checkbox.setBorderPainted(true);

      checkbox.setBorder(isSelected ? mySelectedBorder : myBorder);
      return checkbox;
    }
  }

  protected Font getFont(final JCheckBox checkbox) {
    return getFont();
  }

  protected Color getBackground(final boolean isSelected, final JCheckBox checkbox) {
      return isSelected ? getSelectionBackground() : getBackground();
    }

  protected Color getForeground(final boolean isSelected, final JCheckBox checkbox) {
    return isSelected ? getSelectionForeground() : getForeground();
  }

  public static class CheckBoxListModel extends DefaultListModel {
    @Override
    protected void fireContentsChanged(Object source, int index0, int index1) {
      super.fireContentsChanged(source, index0, index1);
    }
  }
}
