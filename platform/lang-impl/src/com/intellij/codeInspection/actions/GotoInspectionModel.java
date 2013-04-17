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
package com.intellij.codeInspection.actions;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.ex.ScopeToolState;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.gotoByName.SimpleChooseByNameModel;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.util.ArrayUtil;

import javax.swing.*;
import java.util.*;

/**
 * @author Konstantin Bulenkov
 */
public class GotoInspectionModel extends SimpleChooseByNameModel {
  private final Map<String, InspectionProfileEntry> myToolNames = new HashMap<String, InspectionProfileEntry>();
  private final Map<String, Set<InspectionProfileEntry>> myGroupNames = new HashMap<String, Set<InspectionProfileEntry>>();
  private final Map<String, InspectionProfileEntry> myToolShortNames = new HashMap<String, InspectionProfileEntry>();
  private String[] myNames;
  private final ListCellRenderer myListCellRenderer = new InspectionListCellRenderer();


  public GotoInspectionModel(Project project) {
    super(project, IdeBundle.message("prompt.goto.inspection.enter.name"), "goto.inspection.help.id");
    final InspectionProfileImpl rootProfile = (InspectionProfileImpl)InspectionProfileManager.getInstance().getRootProfile();
    for (ScopeToolState state : rootProfile.getAllTools()) {
      final InspectionProfileEntry tool = state.getTool();
      InspectionProfileEntry workingTool = tool;
      if (tool instanceof LocalInspectionToolWrapper) {
        workingTool = LocalInspectionToolWrapper.findTool2RunInBatch(project, null, tool.getShortName());
        if (workingTool == null) {
          continue;
        }
      }
      myToolNames.put(tool.getDisplayName(), workingTool);
      final String groupName = tool.getGroupDisplayName();
      Set<InspectionProfileEntry> toolsInGroup = myGroupNames.get(groupName);
      if (toolsInGroup == null) {
        toolsInGroup = new HashSet<InspectionProfileEntry>();
        myGroupNames.put(groupName, toolsInGroup);
      }
      toolsInGroup.add(workingTool);
      myToolShortNames.put(tool.getShortName(), workingTool);
    }

    final Set<String> nameIds = new HashSet<String>();
    nameIds.addAll(myToolNames.keySet());
    nameIds.addAll(myGroupNames.keySet());
    myNames = ArrayUtil.toStringArray(nameIds);
  }

  @Override
  public ListCellRenderer getListCellRenderer() {
    return myListCellRenderer;
  }

  @Override
  public String[] getNames() {
    return myNames;
  }

  @Override
  public Object[] getElementsByName(final String id, final String pattern) {
    final Set<InspectionProfileEntry> result = new HashSet<InspectionProfileEntry>();
    InspectionProfileEntry e = myToolNames.get(id);
    if (e != null) {
      result.add(e);
    }
    e = myToolShortNames.get(id);
    if (e != null) {
      result.add(e);
    }
    final Set<InspectionProfileEntry> entries = myGroupNames.get(id);
    if (entries != null) {
      result.addAll(entries);
    }
    return result.toArray(new InspectionProfileEntry[result.size()]);
  }

  @Override
  public String getElementName(final Object element) {
    if (element instanceof InspectionProfileEntry) {
      final InspectionProfileEntry entry = (InspectionProfileEntry)element;
      return entry.getDisplayName() + " " + entry.getGroupDisplayName();
    }
    return null;
  }
}
