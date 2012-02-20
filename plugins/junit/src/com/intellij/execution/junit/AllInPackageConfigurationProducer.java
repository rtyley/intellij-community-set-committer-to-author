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

package com.intellij.execution.junit;

import com.intellij.execution.JavaRunConfigurationExtensionManager;
import com.intellij.execution.Location;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;


public class AllInPackageConfigurationProducer extends JUnitConfigurationProducer {
  private PsiPackage myPackage = null;

  protected RunnerAndConfigurationSettings createConfigurationByElement(final Location location, final ConfigurationContext context) {
    final Project project = location.getProject();
    final PsiElement element = location.getPsiElement();
    myPackage = checkPackage(element);
    if (myPackage == null) return null;
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    boolean junitJarFound = false;
    final Module locationModule = location.getModule();
    if (locationModule != null) {
      junitJarFound = facade.findClass(JUnitUtil.TESTCASE_CLASS, 
                                       GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(locationModule, true)) != null;
    } else {
      for (PsiDirectory directory : myPackage.getDirectories()) {
        final Module module = ModuleUtil.findModuleForFile(directory.getVirtualFile(), project);
        if (module != null) {
          if (facade.findClass(JUnitUtil.TESTCASE_CLASS, GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, true)) != null) {
            junitJarFound = true;
            break;
          }
        }
      }
    }
    if (!junitJarFound) return null;
    RunnerAndConfigurationSettings settings = cloneTemplateConfiguration(project, context);
    final JUnitConfiguration configuration = (JUnitConfiguration)settings.getConfiguration();
    final JUnitConfiguration.Data data = configuration.getPersistentData();
    data.PACKAGE_NAME = myPackage.getQualifiedName();
    data.TEST_OBJECT = JUnitConfiguration.TEST_PACKAGE;
    data.setScope(setupPackageConfiguration(context, project, configuration, data.getScope()));
    configuration.setGeneratedName();
    JavaRunConfigurationExtensionManager.getInstance().extendCreatedConfiguration(configuration, location);
    return settings;
  }

  public PsiElement getSourceElement() {
    return myPackage;
  }
}
