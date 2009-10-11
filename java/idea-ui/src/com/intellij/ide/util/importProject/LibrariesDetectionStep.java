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
package com.intellij.ide.util.importProject;

import com.intellij.ide.util.newProjectWizard.ProjectFromSourcesBuilder;
import com.intellij.ide.util.projectWizard.AbstractStepWithProgress;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.io.File;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 6, 2004
 */
public class LibrariesDetectionStep extends AbstractStepWithProgress<List<LibraryDescriptor>> {
  private final ProjectFromSourcesBuilder myBuilder;
  private final ModuleInsight myInsight;
  private final Icon myIcon;
  private final String myHelpId;
  private LibrariesLayoutPanel myLibrariesPanel;

  public LibrariesDetectionStep(ProjectFromSourcesBuilder builder, final ModuleInsight insight, Icon icon, @NonNls String helpId) {
    super("Stop library analysis?");
    myBuilder = builder;
    myInsight = insight;
    myIcon = icon;
    myHelpId = helpId;
  }

  public void updateDataModel() {
    myBuilder.setLibraries(myLibrariesPanel.getChosenEntries());
  }

  protected JComponent createResultsPanel() {
    myLibrariesPanel = new LibrariesLayoutPanel(myInsight);
    return myLibrariesPanel;
  }

  protected String getProgressText() {
    return "Searching for libraries. Please wait.";
  }

  int myPreviousStateHashCode = -1;
  protected boolean shouldRunProgress() {
    final int currentHash = calcStateHashCode();
    try {
      return currentHash != myPreviousStateHashCode;
    }
    finally {
      myPreviousStateHashCode = currentHash;
    }
  }

  private int calcStateHashCode() {
    int hash = myBuilder.getContentEntryPath().hashCode();
    for (Pair<String, String> pair : myBuilder.getSourcePaths()) {
      hash = 31 * hash + pair.getFirst().hashCode();
      hash = 31 * hash + pair.getSecond().hashCode();
    }
    return hash;
  }

  protected List<LibraryDescriptor> calculate() {
    // build sources array
    final List<Pair<String,String>> sourcePaths = myBuilder.getSourcePaths();
    final List<Pair<File,String>> _sourcePaths = new ArrayList<Pair<File, String>>();
    for (Pair<String, String> path : sourcePaths) {
      _sourcePaths.add(new Pair<File, String>(new File(path.first), path.second != null? path.second : ""));
    }
    // build ignored names set
    final HashSet<String> ignored = new HashSet<String>();
    final StringTokenizer tokenizer = new StringTokenizer(FileTypeManager.getInstance().getIgnoredFilesList(), ";", false);
    while (tokenizer.hasMoreTokens()) {
      ignored.add(tokenizer.nextToken());
    }
    
    myInsight.setRoots(Collections.singletonList(new File(myBuilder.getContentEntryPath())), _sourcePaths, ignored);
    myInsight.scanLibraries();
    
    return myInsight.getSuggestedLibraries();
  }

  protected void onFinished(List<LibraryDescriptor> libraries, final boolean canceled) {
    myLibrariesPanel.rebuild();
  }
  
  public Icon getIcon() {
    return myIcon;
  }

  public String getHelpId() {
    return myHelpId;
  }
  
}