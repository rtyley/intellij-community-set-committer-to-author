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
import com.intellij.facet.ui.libraries.FacetLibrariesValidator;
import com.intellij.facet.ui.libraries.FacetLibrariesValidatorDescription;
import com.intellij.facet.ui.libraries.LibraryInfo;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

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
    RequiredLibrariesInfo.RequiredClassesNotFoundInfo info = myRequiredLibraries.checkLibraries(VfsUtil.toVirtualFileArray(roots), false);
    if (info == null) {
      return ValidationResult.OK;
    }

    String missingJars = IdeBundle.message("label.missed.libraries.prefix") + " " + info.getMissingJarsText();
    LibraryInfo[] missingLibraries = info.getLibraryInfos();
    VirtualFile baseDir = myContext.getModule().getProject().getBaseDir();
    final String baseDirPath = baseDir != null ? baseDir.getPath() : "";
    return new ValidationResult(missingJars, new LibrariesQuickFix(missingLibraries, myDescription.getDefaultLibraryName(), baseDirPath));
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
    final ArrayList<VirtualFile> roots = new ArrayList<VirtualFile>();
    if (rootModel != null) {
      rootModel.orderEntries().using(myContext.getModulesProvider()).recursively().librariesOnly().forEachLibrary(new Processor<Library>() {
        @Override
        public boolean process(Library library) {
          ContainerUtil.addAll(roots, myContext.getLibrariesContainer().getLibraryFiles(library, OrderRootType.CLASSES));
          return true;
        }
      });
    }
    return roots;
  }

  private class LibrariesQuickFix extends FacetConfigurationQuickFix {
    private LibraryInfo[] myMissingLibraries;
    private String myDefaultLibraryName;
    private String myBaseDirPath;

    public LibrariesQuickFix(LibraryInfo[] missingLibraries, String defaultLibraryName, String baseDirPath) {
      super(IdeBundle.message("missing.libraries.fix.button"));
      myMissingLibraries = missingLibraries;
      myDefaultLibraryName = defaultLibraryName;
      myBaseDirPath = baseDirPath;
    }

    public void run(final JComponent place) {
      final LibraryCompositionSettings settings = new LibraryCompositionSettings(myMissingLibraries, myDefaultLibraryName, myBaseDirPath);
      LibraryOptionsPanel panel = new LibraryOptionsPanel(settings, myContext.getLibrariesContainer(), false);
      LibraryCompositionDialog dialog = new LibraryCompositionDialog(place, panel);
      dialog.show();
      Disposer.dispose(settings);
      onChange();
    }
  }

  private class LibraryCompositionDialog extends DialogWrapper {
    private final LibraryOptionsPanel myPanel;

    private LibraryCompositionDialog(final JComponent parent, final LibraryOptionsPanel panel) {
      super(parent, true);
      setTitle(IdeBundle.message("specify.libraries.dialog.title"));
      myPanel = panel;
      init();
    }

    protected JComponent createCenterPanel() {
      return myPanel.getMainPanel();
    }

    protected void doOKAction() {
      myPanel.apply();
      final LibraryCompositionSettings settings = myPanel.getSettings();
      final LibrariesContainer librariesContainer = myContext.getLibrariesContainer();
      if (settings.downloadFiles(myPanel.getMainPanel(), false)) {
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
