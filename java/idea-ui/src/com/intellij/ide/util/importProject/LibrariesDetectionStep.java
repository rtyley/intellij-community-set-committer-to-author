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

import com.intellij.ide.util.projectWizard.AbstractStepWithProgress;
import com.intellij.ide.util.projectWizard.importSources.DetectedProjectRoot;
import com.intellij.ide.util.projectWizard.importSources.JavaModuleSourceRoot;
import com.intellij.ide.util.projectWizard.importSources.impl.JavaProjectStructureDetector;
import com.intellij.ide.util.projectWizard.importSources.ProjectFromSourcesBuilder;
import com.intellij.openapi.fileTypes.FileTypeManager;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.io.File;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 6, 2004
 */
public class LibrariesDetectionStep extends AbstractStepWithProgress<List<LibraryDescriptor>> {
  private final JavaProjectStructureDetector myDetector;
  private final ProjectFromSourcesBuilder myBuilder;
  private final ProjectDescriptor myProjectDescriptor;
  private final ModuleInsight myInsight;
  private final Icon myIcon;
  private final String myHelpId;
  private LibrariesLayoutPanel myLibrariesPanel;

  public LibrariesDetectionStep(JavaProjectStructureDetector detector,
                                ProjectFromSourcesBuilder builder,
                                ProjectDescriptor projectDescriptor, final ModuleInsight insight,
                                Icon icon,
                                @NonNls String helpId) {
    super("Stop library analysis?");
    myDetector = detector;
    myBuilder = builder;
    myProjectDescriptor = projectDescriptor;
    myInsight = insight;
    myIcon = icon;
    myHelpId = helpId;
  }

  public void updateDataModel() {
    myProjectDescriptor.setLibraries(myLibrariesPanel.getChosenEntries());
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
    int hash = myBuilder.getBaseProjectPath().hashCode();
    for (DetectedProjectRoot root : myBuilder.getProjectRoots(myDetector)) {
      hash = 31 * hash + root.getDirectory().hashCode();
    }
    return hash;
  }

  protected List<LibraryDescriptor> calculate() {
    final List<JavaModuleSourceRoot> sourceRoots = new ArrayList<JavaModuleSourceRoot>();
    for (DetectedProjectRoot root : myBuilder.getProjectRoots(myDetector)) {
      if (root instanceof JavaModuleSourceRoot) {
        sourceRoots.add((JavaModuleSourceRoot)root);
      }
    }

    final HashSet<String> ignored = new HashSet<String>();
    final StringTokenizer tokenizer = new StringTokenizer(FileTypeManager.getInstance().getIgnoredFilesList(), ";", false);
    while (tokenizer.hasMoreTokens()) {
      ignored.add(tokenizer.nextToken());
    }
    
    myInsight.setRoots(Collections.singletonList(new File(myBuilder.getBaseProjectPath())), sourceRoots, ignored);
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