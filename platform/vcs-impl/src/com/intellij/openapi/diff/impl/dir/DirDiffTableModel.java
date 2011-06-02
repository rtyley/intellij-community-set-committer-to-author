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
package com.intellij.openapi.diff.impl.dir;

import com.intellij.ide.diff.DiffElement;
import com.intellij.ide.diff.DirDiffModel;
import com.intellij.ide.diff.DirDiffSettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Konstantin Bulenkov
 */
public class DirDiffTableModel extends AbstractTableModel implements DirDiffModel, Disposable {
  public static final String COLUMN_NAME = "Name";
  public static final String COLUMN_SIZE = "Size";
  public static final String COLUMN_DATE = "Date";
  private final Project myProject;
  private final DirDiffSettings mySettings;
  private DiffElement mySrc;
  private DiffElement myTrg;
  private DTree myTree;
  private final List<DirDiffElement> myElements = new ArrayList<DirDiffElement>();
  private final AtomicBoolean myUpdating = new AtomicBoolean(false);
  private JBTable myTable;
  public String DECORATOR = "DIFF_TABLE_DECORATOR";
  public volatile AtomicReference<String> text = new AtomicReference<String>(prepareText(""));
  private Updater updater;
  private List<DirDiffModelListener> myListeners = new ArrayList<DirDiffModelListener>();

  public static final String EMPTY_STRING = "                                                  ";
  private DirDiffPanel myPanel;

  public DirDiffTableModel(Project project, DiffElement src, DiffElement trg, DirDiffSettings settings) {
    myProject = project;
    mySettings = settings;
    mySrc = src;
    Disposer.register(this, src);
    myTrg = trg;
    Disposer.register(this, trg);
  }

  public void stopUpdating() {
    if (myUpdating.get()) {
      myUpdating.set(false);
    }
  }

  public void applyRemove() {
    myUpdating.set(true);
    final Iterator<DirDiffElement> i = myElements.iterator();
    while(i.hasNext()) {
      final DType type = i.next().getType();
      switch (type) {
        case SOURCE:
          if (!mySettings.showNewOnSource) i.remove();
          break;
        case TARGET:
          if (!mySettings.showNewOnTarget) i.remove();
          break;
        case SEPARATOR:
          break;
        case CHANGED:
          if (!mySettings.showDifferent) i.remove();
          break;
        case EQUAL:
          if (!mySettings.showEqual) i.remove();
          break;
      }
    }

    boolean sep = true;
    for (int j = myElements.size() - 1; j >= 0; j--) {
      if (myElements.get(j).isSeparator()) {
        if (sep) {
          myElements.remove(j);
        } else {
          sep = true;
        }
      } else {
        sep = false;
      }
    }
    fireTableDataChanged();
    myUpdating.set(false);
    selectFirstRow();
    myPanel.focusTable();
    myPanel.update(true);
  }

  private void selectFirstRow() {
    if (myElements.size() > 0) {
      int row = myElements.get(0).isSeparator() ? 1 : 0;
      if (row < myTable.getRowCount()) {
        myTable.getSelectionModel().setSelectionInterval(row, row);
      }
    }
  }

  public void setPanel(DirDiffPanel panel) {
    myPanel = panel;
  }

  public void updateFromUI() {
    getSettings().setFilter(myPanel.getFilter());
  }

  private static String prepareText(String text) {
    final int LEN = EMPTY_STRING.length();
    String right;
    if (text == null) {
      right = EMPTY_STRING;
    } else if (text.length() == LEN) {
      right = text;
    } else if (text.length() < LEN) {
      right = text + EMPTY_STRING.substring(0, LEN - text.length());
    } else {
      right = "..." + text.substring(text.length() - LEN + 2);
    }
    return "Loading... " + right;
  }

  void fireUpdateStarted() {
    for (DirDiffModelListener listener : myListeners) {
      listener.updateStarted();
    }
  }

  void fireUpdateFinished() {
    for (DirDiffModelListener listener : myListeners) {
      listener.updateFinished();
    }
  }

  void addModelListener(DirDiffModelListener listener) {
    myListeners.add(listener);
  }

  public void reloadModel() {
    myUpdating.set(true);
    final JBLoadingPanel loadingPanel = getLoadingPanel();
    loadingPanel.startLoading();
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        try {
          updater = new Updater(loadingPanel, 100);
          updater.start();
          myTree = new DTree(null, "", true);
          scan(mySrc, myTree, true);
          scan(myTrg, myTree, false);

          myTree.setSource(mySrc);
          myTree.setTarget(myTrg);
          myTree.update(mySettings);
          applySettings();
        }
        catch (Exception e) {//
        }
      }
    });
  }

  private JBLoadingPanel getLoadingPanel() {
    return (JBLoadingPanel)myTable.getClientProperty(DECORATOR);
  }

  public void applySettings() {
    if (! myUpdating.get()) myUpdating.set(true);
    final JBLoadingPanel loadingPanel = getLoadingPanel();
    if (!loadingPanel.isLoading()) {
      loadingPanel.startLoading();
      if (updater == null) {
        updater = new Updater(loadingPanel, 100);
        updater.start();
      }
    }
    final Application app = ApplicationManager.getApplication();
    app.executeOnPooledThread(new Runnable() {
      public void run() {
        myTree.updateVisibility(mySettings);
        final ArrayList<DirDiffElement> elements = new ArrayList<DirDiffElement>();
        fillElements(myTree, elements);
        final Runnable uiThread = new Runnable() {
          public void run() {
            clear();
            myElements.addAll(elements);
            myUpdating.set(false);
            fireTableDataChanged();
            selectFirstRow();
            DirDiffTableModel.this.text.set("");
            if (loadingPanel.isLoading()) {
              loadingPanel.stopLoading();
            }
            myPanel.update(true);
          }
        };
        if (myProject.isDefault()) {
          SwingUtilities.invokeLater(uiThread);
        } else {
          app.invokeLater(uiThread);
        }
      }
    });
  }

  private void fillElements(DTree tree, List<DirDiffElement> elements) {
    if (!myUpdating.get()) return;
    boolean separatorAdded = tree.getParent() == null;
    text.set(prepareText(tree.getPath()));
    for (DTree child : tree.getChildren()) {
      if (!myUpdating.get()) return;
      if (!child.isContainer()) {
        if (child.isVisible()) {
          if (!separatorAdded) {
            elements.add(DirDiffElement.createDirElement(tree.getSource(), tree.getTarget(), tree.getPath()));
            separatorAdded = true;
          }
          switch (child.getType()) {
            case SOURCE:
              elements.add(DirDiffElement.createSourceOnly(child.getSource()));
              break;
            case TARGET:
              elements.add(DirDiffElement.createTargetOnly(child.getTarget()));
              break;
            case CHANGED:
              elements.add(DirDiffElement.createChange(child.getSource(), child.getTarget()));
              break;
            case EQUAL:
              elements.add(DirDiffElement.createEqual(child.getSource(), child.getTarget()));
              break;
            case ERROR:
              elements.add(DirDiffElement.createError(child.getSource(), child.getTarget()));
          }
        }
      } else {
        fillElements(child, elements);
      }
    }
  }

  public void clear() {
    if (!myElements.isEmpty()) {
      final int size = myElements.size();
      myElements.clear();
      fireTableRowsDeleted(0, size - 1);
    }
  }

  private void scan(DiffElement element, DTree root, boolean source) {
    if (!myUpdating.get()) return;
    if (element.isContainer()) {
      try {
        text.set(prepareText(element.getPath()));
        for (DiffElement child : element.getChildren()) {
          if (!myUpdating.get()) return;
          scan(child, root.addChild(child, source), source);
        }
      }
      catch (IOException e) {//
      }
    }
  }

  public String getTitle() {
    return "Diff between " + mySrc.getPresentablePath() + " and " + myTrg.getPresentablePath();
  }

  @Nullable
  public DirDiffElement getElementAt(int index) {
    return 0 <= index && index < myElements.size() ? myElements.get(index) : null;
  }

  public DiffElement getSourceDir() {
    return mySrc;
  }

  public DiffElement getTargetDir() {
    return myTrg;
  }

  public void setSourceDir(DiffElement src) {
    mySrc = src;
  }

  public void setTargetDir(DiffElement trg) {
    myTrg = trg;
  }

  @Override
  public int getRowCount() {
    return myElements.size();
  }

  @Override
  public int getColumnCount() {
    int count = 3;
    if (mySettings.showDate) count += 2;
    if (mySettings.showSize) count += 2;
    return count;
  }

  public JBTable getTable() {
    return myTable;
  }

  public void setTable(JBTable table) {
    myTable = table;
  }

  @Nullable
  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    final DirDiffElement element = myElements.get(rowIndex);
    if (element.isSeparator()) {
      return columnIndex == 0 ? element.getName() : null;
    }

    final String name = getColumnName(columnIndex);
    boolean isSrc = columnIndex < getColumnCount() / 2;
    if (name.equals(COLUMN_NAME)) {
      return isSrc ? element.getSourceName() : element.getTargetName();
    } else if (name.equals(COLUMN_SIZE)) {
      return isSrc ? element.getSourceSize() : element.getTargetSize();
    } else  if (name.equals(COLUMN_DATE)) {
      return isSrc ? element.getSourceModificationDate() : element.getTargetModificationDate();
    }
    return "";
  }

  @Override
  public String getColumnName(int column) {
    final int count = (getColumnCount() - 1) / 2;
    if (column == count) return "*";
    if (column > count) {
      column = getColumnCount() - 1 - column;
    }
    switch (column) {
      case 0: return COLUMN_NAME;
      case 1: return mySettings.showSize ? COLUMN_SIZE : COLUMN_DATE;
      case 2: return COLUMN_DATE;
    }
    return "";
  }

  public Project getProject() {
    return myProject;
  }

  public boolean isShowEqual() {
    return mySettings.showEqual;
  }

  public void setShowEqual(boolean show) {
    mySettings.showEqual = show;
  }

  public boolean isShowDifferent() {
    return mySettings.showDifferent;
  }

  public void setShowDifferent(boolean show) {
    mySettings.showDifferent = show;
  }

  public boolean isShowNewOnSource() {
    return mySettings.showNewOnSource;
  }

  public void setShowNewOnSource(boolean show) {
    mySettings.showNewOnSource = show;
  }

  public boolean isShowNewOnTarget() {
    return mySettings.showNewOnTarget;
  }

  public void setShowNewOnTarget(boolean show) {
    mySettings.showNewOnTarget = show;
  }

  public boolean isUpdating() {
    return myUpdating.get();
  }

  public DirDiffSettings.CompareMode getCompareMode() {
    return mySettings.compareMode;
  }

  public void setCompareMode(DirDiffSettings.CompareMode mode) {
    mySettings.compareMode = mode;
  }

  @Override
  public void dispose() {
    myListeners.clear();
  }

  public DirDiffSettings getSettings() {
    return mySettings;
  }

  class Updater extends Thread {
    private final JBLoadingPanel myLoadingPanel;
    private final int mySleep;

    Updater(JBLoadingPanel loadingPanel, int sleep) {
      super("Loading Updater");
      myLoadingPanel = loadingPanel;
      mySleep = sleep;
    }

    @Override
    public void run() {
      if (myLoadingPanel.isLoading()) {
        try {
          Thread.sleep(mySleep);
        }
        catch (InterruptedException e) {//
        }
        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            final String s = text.get();
            if (s != null && myLoadingPanel.isLoading()) {
              myLoadingPanel.setLoadingText(s);
            }
          }
        });
        updater = new Updater(myLoadingPanel, mySleep);
        updater.start();
      } else {
        updater = null;
        myPanel.focusTable();
      }
    }
  }
}
