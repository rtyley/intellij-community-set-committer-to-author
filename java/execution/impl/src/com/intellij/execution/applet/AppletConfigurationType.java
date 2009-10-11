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
package com.intellij.execution.applet;

import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiClassUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class AppletConfigurationType implements LocatableConfigurationType {
  private final ConfigurationFactory myFactory;
  private static final Icon ICON = IconLoader.getIcon("/runConfigurations/applet.png");

  /**reflection*/
  AppletConfigurationType() {
    myFactory = new ConfigurationFactory(this) {
      public RunConfiguration createTemplateConfiguration(Project project) {
        return new AppletConfiguration("", project, this);
      }
    };
  }

  public String getDisplayName() {
    return ExecutionBundle.message("applet.configuration.name");
  }

  public String getConfigurationTypeDescription() {
    return ExecutionBundle.message("applet.configuration.description");
  }

  public Icon getIcon() {
    return ICON;
  }

  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[]{myFactory};
  }

  public RunnerAndConfigurationSettings createConfigurationByLocation(Location location) {
    location = JavaExecutionUtil.stepIntoSingleClass(location);
    final Project project = location.getProject();
    final PsiElement element = location.getPsiElement();
    final PsiClass aClass = getAppletClass(element, PsiManager.getInstance(project));
    if (aClass == null) return null;
    RunnerAndConfigurationSettings settings = RunManagerEx.getInstanceEx(project).createConfiguration("", getConfigurationFactories()[0]);
    final AppletConfiguration configuration = (AppletConfiguration)settings.getConfiguration();
    configuration.MAIN_CLASS_NAME = JavaExecutionUtil.getRuntimeQualifiedName(aClass);
    configuration.setModule(new JUnitUtil.ModuleOfClass().convert(aClass));
    configuration.setName(configuration.getGeneratedName());
    return settings;
  }

  public boolean isConfigurationByLocation(final RunConfiguration configuration, Location location) {
    final PsiClass aClass = getAppletClass(location.getPsiElement(), PsiManager.getInstance(location.getProject()));
    return aClass != null &&
           Comparing.equal(JavaExecutionUtil.getRuntimeQualifiedName(aClass), ((AppletConfiguration)configuration).MAIN_CLASS_NAME);
  }

  private static PsiClass getAppletClass(PsiElement element, final PsiManager manager) {
    while (element != null) {
      if (element instanceof PsiClass) {
        final PsiClass aClass = (PsiClass)element;
        if (isAppletClass(aClass, manager)){
          return aClass;
        }
      }
      element = element.getParent();
    }
    return null;
  }

  private static boolean isAppletClass(final PsiClass aClass, final PsiManager manager) {
    if (!PsiClassUtil.isRunnableClass(aClass, true)) return false;

    final Module module = JavaExecutionUtil.findModule(aClass);
    final GlobalSearchScope scope = module != null
                              ? GlobalSearchScope.moduleWithLibrariesScope(module)
                              : GlobalSearchScope.projectScope(manager.getProject());
    PsiClass appletClass = JavaPsiFacade.getInstance(manager.getProject()).findClass("java.applet.Applet", scope);
    if (appletClass != null) {
      if (aClass.isInheritor(appletClass, true)) return true;
    }
    appletClass = JavaPsiFacade.getInstance(manager.getProject()).findClass("javax.swing.JApplet", scope);
    if (appletClass != null) {
      if (aClass.isInheritor(appletClass, true)) return true;
    }
    return false;
  }


  @NotNull
  public String getId() {
    return "Applet";
  }

  public static AppletConfigurationType getInstance() {
    return ContainerUtil.findInstance(Extensions.getExtensions(CONFIGURATION_TYPE_EP), AppletConfigurationType.class);
  }
}