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

package com.intellij.facet.impl.ui.libraries;

import com.intellij.facet.Facet;
import com.intellij.facet.ui.FacetConfigurationQuickFix;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.facet.ui.ValidationResult;
import com.intellij.facet.ui.libraries.*;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.Result;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

/**
 * @author nik
 */
public class FacetLibrariesValidatorImpl extends FacetLibrariesValidator {
  private final LibrariesValidatorContext myContext;
  private final FacetValidatorsManager myValidatorsManager;
  private RequiredLibrariesInfo myRequiredLibraries;
  private FacetLibrariesValidatorDescription myDescription;
  private final List<Library> myAddedLibraries = new ArrayList<Library>();

  public FacetLibrariesValidatorImpl(LibraryInfo[] requiredLibraries, FacetLibrariesValidatorDescription description,
                                     final LibrariesValidatorContext context, FacetValidatorsManager validatorsManager) {
    myContext = context;
    myValidatorsManager = validatorsManager;
    myRequiredLibraries = new RequiredLibrariesInfo(requiredLibraries);
    myDescription = description;
  }

  public void setRequiredLibraries(final LibraryInfo[] requiredLibraries) {
    myRequiredLibraries = new RequiredLibrariesInfo(requiredLibraries);
    onChange();
  }

  public boolean isLibrariesAdded() {
    return false;
  }

  public void setDescription(@NotNull final FacetLibrariesValidatorDescription description) {
    myDescription = description;
    onChange();
  }

  public ValidationResult check() {
    if (myRequiredLibraries == null) {
      return ValidationResult.OK;
    }

    ModuleRootModel rootModel = myContext.getRootModel();
    List<VirtualFile> roots = collectRoots(rootModel);
    RequiredLibrariesInfo.RequiredClassesNotFoundInfo info = myRequiredLibraries.checkLibraries(roots.toArray(new VirtualFile[roots.size()]));
    if (info == null) {
      return ValidationResult.OK;
    }

    String missingJars = IdeBundle.message("label.missed.libraries.prefix") + " " + info.getMissingJarsText();
    final String text = IdeBundle.message("label.missed.libraries.text", missingJars, info.getClassNames()[0]);
    LibraryInfo[] missingLibraries = info.getLibraryInfos();
    VirtualFile baseDir = myContext.getModule().getProject().getBaseDir();
    final String baseDirPath = baseDir != null ? baseDir.getPath() : "";
    LibraryCompositionSettings libraryCompositionSettings = new LibraryCompositionSettings(missingLibraries, 
                                                                                           myDescription.getDefaultLibraryName(), baseDirPath,
                                                                                           myDescription.getDefaultLibraryName(), null);
    return new ValidationResult(text, new LibrariesQuickFix(libraryCompositionSettings));
  }

  private void onChange() {
    if (myValidatorsManager != null) {
      myValidatorsManager.validate();
    }
  }

  public void onFacetInitialized(Facet facet) {
    for (Library addedLibrary : myAddedLibraries) {
      myDescription.onLibraryAdded(facet, addedLibrary);
    }
  }

  private List<VirtualFile> collectRoots(final @Nullable ModuleRootModel rootModel) {
    ArrayList<VirtualFile> roots = new ArrayList<VirtualFile>();
    if (rootModel != null) {
      RootPolicy<List<VirtualFile>> policy = new CollectingLibrariesPolicy();
      rootModel.processOrder(policy, roots);
    }
    return roots;
  }

  private class CollectingLibrariesPolicy extends RootPolicy<List<VirtualFile>> {
    private final Set<Module> myProcessedModules = new HashSet<Module>();

    public List<VirtualFile> visitLibraryOrderEntry(final LibraryOrderEntry libraryOrderEntry, final List<VirtualFile> value) {
      Library library = libraryOrderEntry.getLibrary();
      if (library != null) {
        value.addAll(Arrays.asList(myContext.getLibrariesContainer().getLibraryFiles(library, OrderRootType.CLASSES)));
      }
      return value;
    }

    public List<VirtualFile> visitModuleOrderEntry(final ModuleOrderEntry moduleOrderEntry, final List<VirtualFile> value) {
      Module module = moduleOrderEntry.getModule();
      if (module != null && myProcessedModules.add(module)) {
        ModuleRootModel dependency = myContext.getModulesProvider().getRootModel(module);
        if (dependency != null) {
          return dependency.processOrder(this, value);
        }
      }
      return value;
    }

  }

  private class LibrariesQuickFix extends FacetConfigurationQuickFix {
    private final LibraryCompositionSettings myLibrarySettings;

    public LibrariesQuickFix(final LibraryCompositionSettings libraryCompositionSettings) {
      super(IdeBundle.message("missing.libraries.fix.button"));
      myLibrarySettings = libraryCompositionSettings;
    }

    public void run(final JComponent place) {
      LibraryDownloadingMirrorsMap mirrorsMap = new LibraryDownloadingMirrorsMap();
      for (LibraryInfo libraryInfo : myLibrarySettings.getLibraryInfos()) {
        LibraryDownloadInfo downloadingInfo = libraryInfo.getDownloadingInfo();
        if (downloadingInfo != null) {
          RemoteRepositoryInfo repositoryInfo = downloadingInfo.getRemoteRepository();
          if (repositoryInfo != null) {
            mirrorsMap.registerRepository(repositoryInfo);
          }
        }
      }
      LibraryCompositionOptionsPanel panel = new LibraryCompositionOptionsPanel(myContext.getLibrariesContainer(), myLibrarySettings, mirrorsMap);
      LibraryCompositionDialog dialog = new LibraryCompositionDialog(place, panel, mirrorsMap);
      dialog.show();
      onChange();
    }
  }

  private class LibraryCompositionDialog extends DialogWrapper {
    private final LibraryCompositionOptionsPanel myPanel;
    private final LibraryDownloadingMirrorsMap myMirrorsMap;

    private LibraryCompositionDialog(final JComponent parent, final LibraryCompositionOptionsPanel panel,
                                     final LibraryDownloadingMirrorsMap mirrorsMap) {
      super(parent, true);
      setTitle(IdeBundle.message("specify.libraries.dialog.title"));
      myPanel = panel;
      myMirrorsMap = mirrorsMap;
      init();
    }

    protected JComponent createCenterPanel() {
      return myPanel.getMainPanel();
    }

    protected void doOKAction() {
      myPanel.apply();
      final LibraryCompositionSettings settings = myPanel.getLibraryCompositionSettings();
      final LibrariesContainer librariesContainer = myContext.getLibrariesContainer();
      if (settings.downloadFiles(myMirrorsMap, librariesContainer, myPanel.getMainPanel())) {
        ModifiableRootModel rootModel = myContext.getModifiableRootModel();
        if (rootModel == null) {
          final ModifiableRootModel model = ModuleRootManager.getInstance(myContext.getModule()).getModifiableModel();
          new WriteAction() {
            protected void run(final Result result) {
              settings.addLibraries(model, myAddedLibraries, librariesContainer);
              model.commit();
            }
          }.execute();
        }
        else {
          settings.addLibraries(rootModel, myAddedLibraries, librariesContainer);
        }
        super.doOKAction();
      }
    }
  }
}
