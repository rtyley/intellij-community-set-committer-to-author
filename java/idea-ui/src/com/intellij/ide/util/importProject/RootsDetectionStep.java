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
package com.intellij.ide.util.importProject;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.newProjectWizard.DetectedProjectRoot;
import com.intellij.ide.util.newProjectWizard.ProjectFromSourcesBuilder;
import com.intellij.ide.util.newProjectWizard.ProjectStructureDetector;
import com.intellij.ide.util.newProjectWizard.StepSequence;
import com.intellij.ide.util.projectWizard.AbstractStepWithProgress;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.ui.ex.MultiLineLabel;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * @author nik
 */
public class RootsDetectionStep extends AbstractStepWithProgress<List<DetectedRootData>> {
  private static final String ROOTS_FOUND_CARD = "roots_found";
  private static final String ROOTS_NOT_FOUND_CARD = "roots_not_found";
  private final ProjectFromSourcesBuilder myBuilder;
  private final StepSequence mySequence;
  private final Icon myIcon;
  private final String myHelpId;
  private DetectedRootsChooser myDetectedRootsChooser;
  private String myCurrentContentEntryPath = null;
  private JPanel myResultPanel;

  public RootsDetectionStep(ProjectFromSourcesBuilder builder, StepSequence sequence, Icon icon, @NonNls String helpId) {
    super(IdeBundle.message("prompt.stop.searching.for.sources", ApplicationNamesInfo.getInstance().getProductName()));
    myBuilder = builder;
    mySequence = sequence;
    myIcon = icon;
    myHelpId = helpId;
  }

  protected JComponent createResultsPanel() {
    final JPanel panel = new JPanel(new GridBagLayout());
    myDetectedRootsChooser = new DetectedRootsChooser();
    myDetectedRootsChooser.addSelectionListener(new DetectedRootsChooser.RootSelectionListener() {
      @Override
      public void selectionChanged() {
        updateSelectedTypes();
        fireStateChanged();
      }
    });
    final String text = IdeBundle.message("label.project.roots.have.been.found");
    final JLabel label = new JLabel(text);
    label.setUI(new MultiLineLabelUI());
    panel.add(label, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST,
                                            GridBagConstraints.HORIZONTAL, new Insets(8, 10, 0, 10), 0, 0));
    panel.add(myDetectedRootsChooser.getComponent(),
              new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH,
                                     new Insets(8, 10, 8, 10), 0, 0));

    final JButton markAllButton = new JButton(IdeBundle.message("button.mark.all"));
    panel.add(markAllButton,
              new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                                     new Insets(0, 10, 8, 2), 0, 0));

    final JButton unmarkAllButton = new JButton(IdeBundle.message("button.unmark.all"));
    panel.add(unmarkAllButton,
              new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                                     new Insets(0, 0, 8, 10), 0, 0));

    markAllButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myDetectedRootsChooser.setAllElementsMarked(true);
      }
    });
    unmarkAllButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myDetectedRootsChooser.setAllElementsMarked(false);
      }
    });

    myResultPanel = new JPanel(new CardLayout());
    myResultPanel.add(ROOTS_FOUND_CARD, panel);
    JPanel notFoundPanel = new JPanel(new BorderLayout());
    notFoundPanel.setBorder(IdeBorderFactory.createEmptyBorder(5));
    notFoundPanel.add(BorderLayout.NORTH, new MultiLineLabel(IdeBundle.message("label.project.roots.not.found")));
    myResultPanel.add(ROOTS_NOT_FOUND_CARD, notFoundPanel);
    return myResultPanel;
  }

  public JComponent getPreferredFocusedComponent() {
    return myDetectedRootsChooser.getComponent();
  }

  public void updateDataModel() {
    MultiMap<ProjectStructureDetector, DetectedProjectRoot> roots = new MultiMap<ProjectStructureDetector, DetectedProjectRoot>();
    final List<DetectedRootData> selectedElements = myDetectedRootsChooser.getMarkedElements();
    if (selectedElements.size() > 0) {
      for (final DetectedRootData rootData : selectedElements) {
        for (ProjectStructureDetector detector : rootData.getSelectedDetectors()) {
          roots.putValue(detector, rootData.getSelectedRoot());
        }
      }
    }
    myBuilder.setProjectRoots(roots);
    updateSelectedTypes();
  }

  private void updateSelectedTypes() {
    final Set<String> selectedTypes = new HashSet<String>();
    for (DetectedRootData rootData : myDetectedRootsChooser.getMarkedElements()) {
      for (ProjectStructureDetector detector : rootData.getSelectedDetectors()) {
        selectedTypes.add(detector.getClass().getName());
      }
    }
    mySequence.setTypes(selectedTypes);
  }

  protected boolean shouldRunProgress() {
    return isContentEntryChanged();
  }

  protected void onFinished(final List<DetectedRootData> foundRoots, final boolean canceled) {
    final CardLayout layout = (CardLayout)myResultPanel.getLayout();
    if (foundRoots.size() > 0 && !canceled) {
      myCurrentContentEntryPath = getContentRootPath();
      myDetectedRootsChooser.setElements(foundRoots);
      updateSelectedTypes();
      fireStateChanged();
      layout.show(myResultPanel, ROOTS_FOUND_CARD);
    }
    else {
      myCurrentContentEntryPath = null;
      layout.show(myResultPanel, ROOTS_NOT_FOUND_CARD);
    }
    myResultPanel.revalidate();
  }

  protected boolean isContentEntryChanged() {
    final String contentEntryPath = getContentRootPath();
    return myCurrentContentEntryPath == null ? contentEntryPath != null : !myCurrentContentEntryPath.equals(contentEntryPath);
  }

  protected List<DetectedRootData> calculate() {
    final String contentRootPath = getContentRootPath();
    if (contentRootPath == null) {
      return Collections.emptyList();
    }
    final File entryFile = new File(contentRootPath);
    if (!entryFile.exists()) {
      return Collections.emptyList();
    }
    final File[] children = entryFile.listFiles();
    if (children == null || children.length == 0) {
      return Collections.emptyList();
    }

    Map<File, DetectedRootData> rootData = new LinkedHashMap<File, DetectedRootData>();
    for (ProjectStructureDetector detector : ProjectStructureDetector.EP_NAME.getExtensions()) {
      final List<DetectedProjectRoot> detectedRoots = detector.detectRoots(entryFile);
      for (DetectedProjectRoot detectedRoot : detectedRoots) {
        if (isUnderIncompatibleRoot(detectedRoot, rootData)) {
          continue;
        }

        final DetectedRootData data = rootData.get(detectedRoot.getDirectory());
        if (data == null) {
          rootData.put(detectedRoot.getDirectory(), new DetectedRootData(detector, detectedRoot));
        }
        else {
          detectedRoot = data.addRoot(detector, detectedRoot);
        }
        removeIncompatibleRoots(detectedRoot, rootData);
      }
    }
    return new ArrayList<DetectedRootData>(rootData.values());
  }

  private static void removeIncompatibleRoots(DetectedProjectRoot root, Map<File, DetectedRootData> rootData) {
    DetectedRootData[] allRoots = rootData.values().toArray(new DetectedRootData[rootData.values().size()]);
    for (DetectedRootData child : allRoots) {
      final File childDirectory = child.getDirectory();
      if (FileUtil.isAncestor(root.getDirectory(), childDirectory, true)) {
        for (DetectedProjectRoot projectRoot : child.getAllRoots()) {
          if (!root.canContainRoot(projectRoot)) {
            child.removeRoot(projectRoot);
          }
        }
        if (child.getAllRoots().length == 0) {
          rootData.remove(childDirectory);
        }
      }
    }
  }


  private static boolean isUnderIncompatibleRoot(DetectedProjectRoot root, Map<File, DetectedRootData> rootData) {
    File directory = root.getDirectory().getParentFile();
    while (directory != null) {
      final DetectedRootData data = rootData.get(directory);
      if (data != null) {
        for (DetectedProjectRoot parentRoot : data.getAllRoots()) {
          if (!parentRoot.canContainRoot(root)) {
            return true;
          }
        }
      }
      directory = directory.getParentFile();
    }
    return false;
  }

  @Nullable
  private String getContentRootPath() {
    return myBuilder.getContentEntryPath();
  }

  protected String getProgressText() {
    final String root = getContentRootPath();
    return IdeBundle.message("progress.searching.for.sources", root != null ? root.replace('/', File.separatorChar) : "");
  }

  public Icon getIcon() {
    return myIcon;
  }

  public String getHelpId() {
    return myHelpId;
  }
}
