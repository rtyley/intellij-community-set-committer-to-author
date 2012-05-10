/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.roots;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.IdeaTestCase;

import java.io.File;

/**
 * @author dsl
 */
public class ExportingModulesTest extends IdeaTestCase {
  public void test1() throws Exception {
    final String rootPath = PathManagerEx.getTestDataPath().replace(File.separatorChar, '/') + "/moduleRootManager/exportedModules/";
    final VirtualFile testRoot = LocalFileSystem.getInstance().refreshAndFindFileByPath(rootPath);
    assertNotNull(testRoot);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        final ModifiableModuleModel moduleModel = ModuleManager.getInstance(myProject).getModifiableModel();
        final Module moduleA = moduleModel.newModule("A.iml", StdModuleTypes.JAVA.getId());
        final Module moduleB = moduleModel.newModule("B.iml", StdModuleTypes.JAVA.getId());
        final Module moduleC = moduleModel.newModule("C.iml", StdModuleTypes.JAVA.getId());
        moduleModel.commit();

        configureModule(moduleA, testRoot, "A");
        configureModule(moduleB, testRoot, "B");
        configureModule(moduleC, testRoot, "C");

        final ModifiableRootModel rootModelB = ModuleRootManager.getInstance(moduleB).getModifiableModel();
        final ModuleOrderEntry moduleBAentry = rootModelB.addModuleOrderEntry(moduleA);
        moduleBAentry.setExported(true);
        rootModelB.commit();

        final ModifiableRootModel rootModelC = ModuleRootManager.getInstance(moduleC).getModifiableModel();
        rootModelC.addModuleOrderEntry(moduleB);
        rootModelC.commit();

        final PsiClass pCClass =
          JavaPsiFacade.getInstance(myProject).findClass("p.C", GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(moduleC));
        assertNotNull(pCClass);

        final PsiClass pAClass =
          JavaPsiFacade.getInstance(myProject).findClass("p.A", GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(moduleB));
        assertNotNull(pAClass);

        final PsiClass pAClass2 =
          JavaPsiFacade.getInstance(myProject).findClass("p.A", GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(moduleC));
        assertNotNull(pAClass2);
      }
    });
  }

  private void configureModule(final Module module, final VirtualFile testRoot, final String name) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        final ModifiableRootModel rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
        final VirtualFile contentRoot = testRoot.findChild(name);
        final ContentEntry contentEntry = rootModel.addContentEntry(contentRoot);
        contentEntry.addSourceFolder(contentRoot.findChild("src"), false);
        rootModel.commit();
      }
    });
  }
}
