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
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.vcs.ChangeListColumn;
import com.intellij.openapi.vcs.CommittedChangesProvider;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author yole
 */
public class ColumnFilteringStrategy extends JPanel implements ChangeListFilteringStrategy {
  private final JList myValueList;
  private final CopyOnWriteArrayList<ChangeListener> myListeners = ContainerUtil.createEmptyCOWList();
  private final ChangeListColumn myColumn;
  private final Class<? extends CommittedChangesProvider> myProviderClass;

  public ColumnFilteringStrategy(final ChangeListColumn column,
                                 final Class<? extends CommittedChangesProvider> providerClass) {
    setLayout(new BorderLayout());
    myValueList = new JList();
    add(new JScrollPane(myValueList));
    myValueList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(final ListSelectionEvent e) {
        for(ChangeListener listener: myListeners) {
          listener.stateChanged(new ChangeEvent(this));
        }
      }
    });
    myValueList.setCellRenderer(new ColoredListCellRenderer() {
      protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (index == 0) {
          append(value.toString(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        }
        else if (value.toString().length() == 0) {
          append(VcsBundle.message("committed.changes.filter.none"), SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
        else {
          append(value.toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
      }
    });
    myColumn = column;
    myProviderClass = providerClass;
  }

  public String toString() {
    return myColumn.getTitle();
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(100, 100);
  }

  @Nullable
  public JComponent getFilterUI() {
    return this;
  }

  public void setFilterBase(List<CommittedChangeList> changeLists) {
    final Object oldSelection = myValueList.getSelectedValue();
    final Collection<String> values = new TreeSet<String>();
    for(CommittedChangeList changeList: changeLists) {
      if (myProviderClass == null || myProviderClass.isInstance(changeList.getVcs().getCommittedChangesProvider())) {
        //noinspection unchecked
        values.add(myColumn.getValue(ReceivedChangeList.unwrap(changeList)).toString());
      }
    }
    final String[] valueArray = values.toArray(new String[values.size()]);
    myValueList.setModel(new AbstractListModel() {
      public int getSize() {
        return valueArray.length+1;
      }

      public Object getElementAt(final int index) {
        if (index == 0) {
          return VcsBundle.message("committed.changes.filter.all");
        }
        return valueArray [index-1];
      }
    });
    if (oldSelection != null) {
      myValueList.setSelectedValue(oldSelection, false);
    }
  }

  public void addChangeListener(final ChangeListener listener) {
    myListeners.add(listener);
  }

  public void removeChangeListener(final ChangeListener listener) {
    myListeners.remove(listener);
  }

  @NotNull
  public List<CommittedChangeList> filterChangeLists(List<CommittedChangeList> changeLists) {
    final Object[] selection = myValueList.getSelectedValues();
    if (myValueList.getSelectedIndex() == 0 || selection.length == 0) {
      return changeLists;
    }
    List<CommittedChangeList> result = new ArrayList<CommittedChangeList>();
    for(CommittedChangeList changeList: changeLists) {
      if (myProviderClass == null || myProviderClass.isInstance(changeList.getVcs().getCommittedChangesProvider())) {
        for(Object value: selection) {
          //noinspection unchecked
          if (value.toString().equals(myColumn.getValue(ReceivedChangeList.unwrap(changeList)).toString())) {
            result.add(changeList);
            break;
          }
        }
      }
    }
    return result;
  }
}
