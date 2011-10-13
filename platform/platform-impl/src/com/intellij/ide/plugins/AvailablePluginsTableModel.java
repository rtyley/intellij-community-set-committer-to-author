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

/*
 * Created by IntelliJ IDEA.
 * User: Anna.Kozlova
 * Date: 19-Aug-2006
 * Time: 14:54:29
 */
package com.intellij.ide.plugins;

import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.ColumnInfo;

import javax.swing.*;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Dec 26, 2003
 * Time: 3:51:58 PM
 * To change this template use Options | File Templates.
 */
public class AvailablePluginsTableModel extends PluginTableModel {
  private final Map<PluginId, String> myUpdateVersions = new HashMap<PluginId, String>();

  public static final String ALL = "All";
  private String myCategory = ALL;
  private LinkedHashSet<String> myAvailableCategories = new LinkedHashSet<String>();

  protected static final String DOWNLOADS = "Downloads";
  protected static final String RELEASE_DATE = "Updated";
  public static final String [] SORT_MODES = new String[] {NAME, DOWNLOADS, RELEASE_DATE};

  public AvailablePluginsTableModel() {
    super.columns = new ColumnInfo[] {new AvailablePluginColumnInfo(mySortMode)};

    setSortKey(new RowSorter.SortKey(getNameColumn(), SortOrder.ASCENDING));
    view = new ArrayList<IdeaPluginDescriptor>();
  }

  public void addData(List<IdeaPluginDescriptor> list) {
    view.clear();
    myAvailableCategories.clear();
    //  For each downloadable plugin we need to know whether its counterpart
    //  is already installed, and if yes compare the difference in versions:
    //  availability of newer versions will be indicated separately.
    for (IdeaPluginDescriptor descr : list) {
      updateStatus(descr);
      view.add(descr);
      myAvailableCategories.add(descr.getCategory());
      myUpdateVersions.put(descr.getPluginId(), descr.getVersion());
    }

    fireTableDataChanged();
  }

  @Override
  public void setSortMode(String sortMode) {
    ((AvailablePluginColumnInfo)columns[getNameColumn()]).setSortMode(sortMode);
    super.setSortMode(sortMode);
  }

  public String getCategory() {
    return myCategory;
  }

  public void setCategory(String category, String filter) {
    myCategory = category;
    filter(filter);
  }

  @Override
  public boolean isPluginDescriptorAccepted(IdeaPluginDescriptor descriptor) {
    if (myCategory == ALL) return true;
    final String category = descriptor.getCategory();
    return category == null || category.equals(myCategory);
  }

  public LinkedHashSet<String> getAvailableCategories() {
    return myAvailableCategories;
  }

  private static void updateStatus(final IdeaPluginDescriptor descr) {
    if (descr instanceof PluginNode) {
      final PluginNode node = (PluginNode)descr;
      IdeaPluginDescriptor existing = PluginManager.getPlugin(descr.getPluginId());
      if (existing != null) {
        node.setStatus(PluginNode.STATUS_INSTALLED);
      }
    }
  }

  public void modifyData(List<IdeaPluginDescriptor> list) {
    //  For each downloadable plugin we need to know whether its counterpart
    //  is already installed, and if yes compare the difference in versions:
    //  availability of newer versions will be indicated separately.
    for (IdeaPluginDescriptor descr : list) {
      updateStatus(descr);
      PluginId descrId = descr.getPluginId();
      if (myUpdateVersions.containsKey(descrId)) {
        String currVersion = myUpdateVersions.get(descrId);
        int state = StringUtil.compareVersionNumbers(descr.getVersion(), currVersion);
        if (state > 0) {
          for (int i = 0; i < view.size(); i++) {
            IdeaPluginDescriptor obsolete = view.get(i);
            if (obsolete.getPluginId() == descrId) view.remove(obsolete);
          }
          view.add(descr);
        }
      }
      else {
        view.add(descr);
        myUpdateVersions.put(descr.getPluginId(), descr.getVersion());
      }
    }

    fireTableDataChanged();
  }

  @Override
  public void filter(final List<IdeaPluginDescriptor> filtered) {
    view.clear();
    for (IdeaPluginDescriptor descriptor : filtered) {
      view.add(descriptor);
    }
    super.filter(filtered);
  }

  public int getNameColumn() {
    return 0;
  }

}
