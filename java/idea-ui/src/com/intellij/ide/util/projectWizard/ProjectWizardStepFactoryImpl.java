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
package com.intellij.ide.util.projectWizard;

import com.intellij.ide.util.frameworkSupport.FrameworkSupportUtil;
import com.intellij.ide.util.newProjectWizard.*;
import com.intellij.ide.util.newProjectWizard.ProjectNameStep;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory;
import com.intellij.openapi.util.Computable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Oct 6, 2004
 */
public class ProjectWizardStepFactoryImpl extends ProjectWizardStepFactory {

  public ModuleWizardStep createNameAndLocationStep(WizardContext wizardContext, JavaModuleBuilder builder, ModulesProvider modulesProvider, Icon icon, String helpId) {
    return new NameLocationStep(wizardContext, builder, modulesProvider, icon, helpId);
  }

  public ModuleWizardStep createNameAndLocationStep(final WizardContext wizardContext) {
    return new ProjectNameStep(wizardContext, null);
  }

  /**
   * @deprecated
   */
  public ModuleWizardStep createOutputPathPathsStep(ModuleWizardStep nameAndLocationStep, JavaModuleBuilder builder, Icon icon, String helpId) {
    return new OutputPathsStep((NameLocationStep)nameAndLocationStep, builder, icon, helpId);
  }

  public ModuleWizardStep createSourcePathsStep(ModuleWizardStep nameAndLocationStep, SourcePathsBuilder builder, Icon icon, String helpId) {
    return null;
  }

  public ModuleWizardStep createSourcePathsStep(final WizardContext context, final SourcePathsBuilder builder, final Icon icon, @NonNls final String helpId) {
    return new SourcePathsStep(builder, icon, helpId);
  }

  /**
   * @deprecated
   */
  public ModuleWizardStep createProjectJdkStep(WizardContext context,
                                               final JavaModuleBuilder builder,
                                               final Computable<Boolean> isVisible,
                                               final Icon icon,
                                               final String helpId) {
    return createProjectJdkStep(context, null, builder, isVisible, icon, helpId);
  }

  public ModuleWizardStep createProjectJdkStep(WizardContext context,
                                               SdkType type,
                                               final JavaModuleBuilder builder,
                                               final Computable<Boolean> isVisible,
                                               final Icon icon,
                                               @NonNls final String helpId) {
    return new ProjectJdkForModuleStep(context, type){
      public void updateDataModel() {
        super.updateDataModel();
        builder.setModuleJdk(getJdk());
      }

      public boolean isStepVisible() {
        return isVisible.compute().booleanValue();
      }

      public Icon getIcon() {
        return icon;
      }

      @Override
      public String getName() {
        return "Specify JDK";
      }

      public String getHelpId() {
        return helpId;
      }
    };
  }

  public ModuleWizardStep createProjectJdkStep(final WizardContext wizardContext) {
    ModuleWizardStep projectSdkStep = wizardContext.getProjectSdkStep();
    if (projectSdkStep instanceof ProjectJdkStep) {
      return projectSdkStep;
    }
    projectSdkStep = new ProjectJdkStep(wizardContext) {
      public boolean isStepVisible() {
        final Sdk newProjectJdk = AddModuleWizard.getNewProjectJdk(wizardContext);
        if (newProjectJdk == null) return true;
        final ProjectBuilder projectBuilder = wizardContext.getProjectBuilder();
        return projectBuilder != null && !projectBuilder.isSuitableSdk(newProjectJdk);
      }
    };
    wizardContext.setProjectSdkStep(projectSdkStep);
    return projectSdkStep;
  }

  @Nullable
  @Override
  public Sdk getNewProjectSdk(WizardContext wizardContext) {
    return AddModuleWizard.getNewProjectJdk(wizardContext);
  }

  @Override
  public ModuleWizardStep createSupportForFrameworksStep(WizardContext wizardContext, ModuleBuilder moduleBuilder) {
    return createSupportForFrameworksStep(wizardContext, moduleBuilder, ModulesProvider.EMPTY_MODULES_PROVIDER);
  }

  @Override
  public ModuleWizardStep createSupportForFrameworksStep(WizardContext context, ModuleBuilder builder, ModulesProvider modulesProvider) {
    if (!FrameworkSupportUtil.getProviders(builder).isEmpty()) {
      final LibrariesContainer container = LibrariesContainerFactory.createContainer(context, modulesProvider);
      return new SupportForFrameworksStep(builder, container);
    }
    return null;
  }
}
