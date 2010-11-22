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

import com.intellij.facet.FacetType;
import com.intellij.facet.FacetTypeRegistry;
import com.intellij.facet.autodetecting.FacetDetector;
import com.intellij.facet.autodetecting.UnderlyingFacetSelector;
import com.intellij.facet.impl.autodetecting.FacetDetectorForWizardRegistry;
import com.intellij.facet.impl.autodetecting.FacetDetectorRegistryEx;
import com.intellij.facet.impl.autodetecting.FileContentPattern;
import com.intellij.facet.impl.autodetecting.facetsTree.DetectedFacetsTreeComponent;
import com.intellij.facet.impl.ui.FacetDetectionProcessor;
import com.intellij.ide.util.newProjectWizard.ProjectFromSourcesBuilder;
import com.intellij.ide.util.projectWizard.AbstractStepWithProgress;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.ui.ScrollPaneFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author nik
 */
public abstract class FacetDetectionStep extends AbstractStepWithProgress<Map<ModuleDescriptor, Map<File, List<FacetDetectionProcessor.DetectedInWizardFacetInfo>>>>
  implements ProjectFromSourcesBuilder.ProjectConfigurationUpdater {
  private final Icon myIcon;
  private final ModuleType myModuleType;
  private List<File> myLastRoots = null;
  private final DetectedFacetsTreeComponent myDetectedFacetsComponent;
  private JPanel myMainPanel;
  private JPanel myFacetsTreePanel;
  private JLabel myFacetsDetectedLabel;

  public FacetDetectionStep(final Icon icon, ModuleType moduleType) {
    super(ProjectBundle.message("message.text.stop.searching.for.facets", ApplicationNamesInfo.getInstance().getProductName()));
    myIcon = icon;
    myModuleType = moduleType;
    myDetectedFacetsComponent = new DetectedFacetsTreeComponent();
  }

  public void updateDataModel() {
  }

  protected boolean shouldRunProgress() {
    return myLastRoots == null || !Comparing.haveEqualElements(myLastRoots, getRoots());
  }

  protected String getProgressText() {
    return ProjectBundle.message("progress.text.searching.facets");
  }

  protected JComponent createResultsPanel() {
    JPanel mainPanel = myDetectedFacetsComponent.getMainPanel();
    myFacetsTreePanel.add(ScrollPaneFactory.createScrollPane(mainPanel), BorderLayout.CENTER);
    return myMainPanel;
  }

  protected Map<ModuleDescriptor, Map<File, List<FacetDetectionProcessor.DetectedInWizardFacetInfo>>> calculate() {
    myLastRoots = getRoots();

    ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();

    Map<ModuleDescriptor, Map<File, List<FacetDetectionProcessor.DetectedInWizardFacetInfo>>> result = new HashMap<ModuleDescriptor, Map<File, List<FacetDetectionProcessor.DetectedInWizardFacetInfo>>>();
    for (ModuleDescriptor moduleDescriptor : getModuleDescriptors()) {

      Map<File, List<FacetDetectionProcessor.DetectedInWizardFacetInfo>> root2Facets = new HashMap<File, List<FacetDetectionProcessor.DetectedInWizardFacetInfo>>();
      for (File root : moduleDescriptor.getContentRoots()) {
        FacetDetectionProcessor processor = new FacetDetectionProcessor(progressIndicator, myModuleType);
        processor.process(root);
        List<FacetDetectionProcessor.DetectedInWizardFacetInfo> facets = processor.getDetectedFacetsInfos();
        if (!facets.isEmpty()) {
          root2Facets.put(root, facets);
        }
      }

      if (!root2Facets.isEmpty()) {
        result.put(moduleDescriptor, root2Facets);
      }
    }

    return result;
  }

  protected abstract List<ModuleDescriptor> getModuleDescriptors();

  private List<File> getRoots() {
    List<File> roots = new ArrayList<File>();
    for (ModuleDescriptor moduleDescriptor : getModuleDescriptors()) {
      roots.addAll(moduleDescriptor.getContentRoots());
    }
    return roots;
  }

  protected void onFinished(final Map<ModuleDescriptor, Map<File, List<FacetDetectionProcessor.DetectedInWizardFacetInfo>>> result, final boolean canceled) {
    myDetectedFacetsComponent.clear();
    for (ModuleDescriptor moduleDescriptor : result.keySet()) {
      myDetectedFacetsComponent.addFacets(moduleDescriptor, result.get(moduleDescriptor));
    }
    myDetectedFacetsComponent.createTree();
    if (result.isEmpty()) {
      myFacetsDetectedLabel.setText(ProjectBundle.message("label.text.no.facets.detected"));
    }
    else {
      myFacetsDetectedLabel.setText(ProjectBundle.message("label.text.the.following.facets.are.detected"));
    }
  }

  public Icon getIcon() {
    return myIcon;
  }

  public static boolean isEnabled(@NotNull ModuleType moduleType) {
    for (FacetType<?,?> facetType : FacetTypeRegistry.getInstance().getFacetTypes()) {
      if (facetType.isSuitableModuleType(moduleType)) {
        final Ref<Boolean> hasDetector = Ref.create(false);
        //noinspection unchecked
        facetType.registerDetectors(new FacetDetectorRegistryEx(new FacetDetectorForWizardRegistry() {

          public void register(FileType fileType,
                               @NotNull FileContentPattern fileContentPattern,
                               FacetDetector facetDetector,
                               UnderlyingFacetSelector underlyingFacetSelector) {
            hasDetector.set(true);
          }
        }, null));
        if (hasDetector.get()) {
          return true;
        } 
      }
    }
    return false;
  }

  @NonNls
  public String getHelpId() {
    return "reference.dialogs.new.project.fromCode.facets";
  }

  public void updateModule(final ModuleDescriptor descriptor, final Module module, final ModifiableRootModel rootModel) {
    myDetectedFacetsComponent.createFacets(descriptor, module, rootModel);
  }
}
