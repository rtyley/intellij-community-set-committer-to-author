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

package com.intellij.conversion.impl;

import com.intellij.conversion.*;
import com.intellij.openapi.components.StorageScheme;

import java.io.File;
import java.util.*;

/**
 * @author nik
 */
public class ConversionRunner {
  private final ConverterProvider myProvider;
  private final ConversionContextImpl myContext;
  private final ConversionProcessor<ModuleSettings> myModuleFileConverter;
  private final ConversionProcessor<ProjectSettings> myProjectFileConverter;
  private final ConversionProcessor<WorkspaceSettings> myWorkspaceConverter;
  private boolean myProcessProjectFile;
  private boolean myProcessWorkspaceFile;
  private boolean myProcessRunConfigurations;
  private List<File> myModulesFilesToProcess = new ArrayList<File>();
  private ProjectConverter myConverter;
  private ConversionProcessor<RunManagerSettings> myRunConfigurationsConverter;

  public ConversionRunner(ConverterProvider provider, ConversionContextImpl context) {
    myProvider = provider;
    myContext = context;
    myConverter = provider.createConverter(context);
    myModuleFileConverter = myConverter.createModuleFileConverter();
    myProjectFileConverter = myConverter.createProjectFileConverter();
    myWorkspaceConverter = myConverter.createWorkspaceFileConverter();
    myRunConfigurationsConverter = myConverter.createRunConfigurationsConverter();
  }

  public boolean isConversionNeeded() throws CannotConvertException {
    myProcessProjectFile = myContext.getStorageScheme() == StorageScheme.DEFAULT && myProjectFileConverter != null
                           && myProjectFileConverter.isConversionNeeded(myContext.getProjectSettings());

    myProcessWorkspaceFile = myWorkspaceConverter != null && myContext.getWorkspaceFile().exists()
                             && myWorkspaceConverter.isConversionNeeded(myContext.getWorkspaceSettings());

    if (myModuleFileConverter != null) {
      for (File moduleFile : myContext.getModuleFiles()) {
        if (moduleFile.exists() && myModuleFileConverter.isConversionNeeded(myContext.getModuleSettings(moduleFile))) {
          myModulesFilesToProcess.add(moduleFile);
        }
      }
    }

    myProcessRunConfigurations = myRunConfigurationsConverter != null
                                 && myRunConfigurationsConverter.isConversionNeeded(myContext.getRunManagerSettings());

    return myProcessProjectFile || myProcessWorkspaceFile || myProcessRunConfigurations || !myModulesFilesToProcess.isEmpty();
  }

  public boolean isModuleConversionNeeded(File moduleFile) throws CannotConvertException {
    return myModuleFileConverter != null && myModuleFileConverter.isConversionNeeded(myContext.getModuleSettings(moduleFile));
  }

  public Set<File> getAffectedFiles() {
    Set<File> affectedFiles = new HashSet<File>();
    if (myProcessProjectFile) {
      affectedFiles.add(myContext.getProjectFile());
    }
    if (myProcessWorkspaceFile) {
      affectedFiles.add(myContext.getWorkspaceFile());
    }
    affectedFiles.addAll(myModulesFilesToProcess);
    if (myProcessRunConfigurations) {
      try {
        affectedFiles.addAll(myContext.getRunManagerSettings().getAffectedFiles());
      }
      catch (CannotConvertException ignored) {
      }
    }
    affectedFiles.addAll(myConverter.getAdditionalAffectedFiles());
    return affectedFiles;
  }

  public void preProcess() throws CannotConvertException {
    if (myProcessProjectFile) {
      myProjectFileConverter.preProcess(myContext.getProjectSettings());
    }

    if (myProcessWorkspaceFile) {
      myWorkspaceConverter.preProcess(myContext.getWorkspaceSettings());
    }

    for (File moduleFile : myModulesFilesToProcess) {
      myModuleFileConverter.preProcess(myContext.getModuleSettings(moduleFile));
    }

    if (myProcessRunConfigurations) {
      myRunConfigurationsConverter.preProcess(myContext.getRunManagerSettings());
    }

    myConverter.preProcessingFinished();
  }

  public void process() throws CannotConvertException {
    if (myProcessProjectFile) {
      myProjectFileConverter.process(myContext.getProjectSettings());
    }

    if (myProcessWorkspaceFile) {
      myWorkspaceConverter.process(myContext.getWorkspaceSettings());
    }

    for (File moduleFile : myModulesFilesToProcess) {
      myModuleFileConverter.process(myContext.getModuleSettings(moduleFile));
    }

    if (myProcessRunConfigurations) {
      myRunConfigurationsConverter.process(myContext.getRunManagerSettings());
    }
    myConverter.processingFinished();
  }

  public void postProcess() throws CannotConvertException {
    if (myProcessProjectFile) {
      myProjectFileConverter.postProcess(myContext.getProjectSettings());
    }

    if (myProcessWorkspaceFile) {
      myWorkspaceConverter.postProcess(myContext.getWorkspaceSettings());
    }

    for (File moduleFile : myModulesFilesToProcess) {
      myModuleFileConverter.postProcess(myContext.getModuleSettings(moduleFile));
    }

    if (myProcessRunConfigurations) {
      myRunConfigurationsConverter.postProcess(myContext.getRunManagerSettings());
    }
    
    myConverter.postProcessingFinished();
  }

  public ConverterProvider getProvider() {
    return myProvider;
  }

  public static List<File> getReadOnlyFiles(final Collection<File> affectedFiles) {
    List<File> result = new ArrayList<File>();
    for (File file : affectedFiles) {
      if (!file.canWrite()) {
        result.add(file);
      }
    }
    return result;
  }

  public void convertModule(File moduleFile) throws CannotConvertException {
    final ModuleSettings settings = myContext.getModuleSettings(moduleFile);
    myModuleFileConverter.preProcess(settings);
    myModuleFileConverter.process(settings);
    myModuleFileConverter.postProcess(settings);
  }
}
