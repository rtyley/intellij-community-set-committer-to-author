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
package org.jetbrains.idea.maven.execution;

import com.intellij.execution.CantRunException;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.MavenImportingTestCase;

import java.util.ArrayList;
import java.util.List;

public class JavaClasspathConfigurationTest extends MavenImportingTestCase {
  public void testConfiguringModuleDependencies() throws Exception {
    VirtualFile m1 = createModulePom("m1", "<groupId>test</groupId>" +
                                           "<artifactId>m1</artifactId>" +
                                           "<version>1</version>" +

                                           "<dependencies>" +
                                           "  <dependency>" +
                                           "    <groupId>test</groupId>" +
                                           "    <artifactId>m2</artifactId>" +
                                           "    <version>1</version>" +
                                           "  </dependency>" +
                                           "</dependencies>");

    VirtualFile m2 = createModulePom("m2", "<groupId>test</groupId>" +
                                           "<artifactId>m2</artifactId>" +
                                           "<version>1</version>" +

                                           "<dependencies>" +
                                           "  <dependency>" +
                                           "    <groupId>test</groupId>" +
                                           "    <artifactId>m3</artifactId>" +
                                           "    <version>1</version>" +
                                           "  </dependency>" +
                                           "  <dependency>" +
                                           "    <groupId>test</groupId>" +
                                           "    <artifactId>m4</artifactId>" +
                                           "    <version>1</version>" +
                                           "    <optional>true</optional>" +
                                           "  </dependency>" +
                                           "</dependencies>");

    VirtualFile m3 = createModulePom("m3", "<groupId>test</groupId>" +
                                           "<artifactId>m3</artifactId>" +
                                           "<version>1</version>");

    VirtualFile m4 = createModulePom("m4", "<groupId>test</groupId>" +
                                           "<artifactId>m4</artifactId>" +
                                           "<version>1</version>");

    importProjects(m1, m2, m3, m4);
    assertModules("m1", "m2", "m3", "m4");

    assertModuleModuleDeps("m1", "m2", "m3");
    assertModuleModuleDeps("m2", "m3", "m4");

    setupJdkForModule("m1");
    setupJdkForModule("m2");
    setupJdkForModule("m3");
    setupJdkForModule("m4");

    assertModuleClasspath("m1",
                         getProjectPath() + "/m1/target/classes",
                         getProjectPath() + "/m2/target/classes",
                         getProjectPath() + "/m3/target/classes");

    assertModuleClasspath("m2",
                         getProjectPath() + "/m2/target/classes",
                         getProjectPath() + "/m3/target/classes",
                         getProjectPath() + "/m4/target/classes");
  }

  public void testOptionalLibraryDependencies() throws Exception {
    VirtualFile m1 = createModulePom("m1", "<groupId>test</groupId>" +
                                           "<artifactId>m1</artifactId>" +
                                           "<version>1</version>" +

                                           "<dependencies>" +
                                           "  <dependency>" +
                                           "    <groupId>test</groupId>" +
                                           "    <artifactId>m2</artifactId>" +
                                           "    <version>1</version>" +
                                           "  </dependency>" +
                                           "</dependencies>");

    VirtualFile m2 = createModulePom("m2", "<groupId>test</groupId>" +
                                           "<artifactId>m2</artifactId>" +
                                           "<version>1</version>" +

                                           "<dependencies>" +
                                           "  <dependency>" +
                                           "    <groupId>jmock</groupId>" +
                                           "    <artifactId>jmock</artifactId>" +
                                           "    <version>1.0</version>" +
                                           "  </dependency>" +
                                           "  <dependency>" +
                                           "    <groupId>junit</groupId>" +
                                           "    <artifactId>junit</artifactId>" +
                                           "    <version>4.0</version>" +
                                           "    <optional>true</optional>" +
                                           "  </dependency>" +
                                           "</dependencies>");

    importProjects(m1, m2);
    assertModules("m1", "m2");

    assertModuleModuleDeps("m1", "m2");
    assertModuleLibDeps("m1", "Maven: jmock:jmock:1.0");
    assertModuleLibDeps("m2", "Maven: jmock:jmock:1.0", "Maven: junit:junit:4.0");

    setupJdkForModule("m1");
    setupJdkForModule("m2");

    assertModuleClasspath("m1",
                         getProjectPath() + "/m1/target/classes",
                         getProjectPath() + "/m2/target/classes",
                         getRepositoryPath() + "/jmock/jmock/1.0/jmock-1.0.jar");

    assertModuleClasspath("m2",
                         getProjectPath() + "/m2/target/classes",
                         getRepositoryPath() + "/jmock/jmock/1.0/jmock-1.0.jar",
                         getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar");
  }

  public void testDoNotChangeClasspathForRegularModules() throws Exception {
    VirtualFile m1 = createModulePom("m1", "<groupId>test</groupId>" +
                                           "<artifactId>m1</artifactId>" +
                                           "<version>1</version>" +

                                           "<dependencies>" +
                                           "  <dependency>" +
                                           "    <groupId>test</groupId>" +
                                           "    <artifactId>m2</artifactId>" +
                                           "    <version>1</version>" +
                                           "    <optional>true</optional>" +
                                           "  </dependency>" +
                                           "  <dependency>" +
                                           "    <groupId>junit</groupId>" +
                                           "    <artifactId>junit</artifactId>" +
                                           "    <version>4.0</version>" +
                                           "    <optional>true</optional>" +
                                           "  </dependency>" +
                                           "</dependencies>");

    VirtualFile m2 = createModulePom("m2", "<groupId>test</groupId>" +
                                           "<artifactId>m2</artifactId>" +
                                           "<version>1</version>");

    importProjects(m1, m2);
    assertModules("m1", "m2");

    Module user = createModule("user");

    ModifiableRootModel model = ModuleRootManager.getInstance(user).getModifiableModel();
    model.addModuleOrderEntry(getModule("m1"));
    VirtualFile out = user.getModuleFile().getParent().createChildDirectory(this, "output");
    model.getModuleExtension(CompilerModuleExtension.class).setCompilerOutputPath(out);
    model.getModuleExtension(CompilerModuleExtension.class).inheritCompilerOutputPath(false);
    model.commit();

    assertModuleModuleDeps("m1", "m2");
    assertModuleLibDeps("m1", "Maven: junit:junit:4.0");

    assertModuleModuleDeps("user", "m1");
    assertModuleLibDeps("user");

    setupJdkForModule("user");
    setupJdkForModule("m1");
    setupJdkForModule("m2");

    assertModuleClasspath("user",
                         getProjectPath() + "/user/output",
                         getProjectPath() + "/m1/target/classes",
                         getProjectPath() + "/m2/target/classes",
                         getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar");

    assertModuleClasspath("m1",
                         getProjectPath() + "/m1/target/classes",
                         getProjectPath() + "/m2/target/classes",
                         getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar");
  }

  public void testDoNotIncludeProvidedAndTestTransitiveDependencies() throws Exception {
    VirtualFile m1 = createModulePom("m1", "<groupId>test</groupId>" +
                                           "<artifactId>m1</artifactId>" +
                                           "<version>1</version>" +

                                           "<dependencies>" +
                                           "  <dependency>" +
                                           "    <groupId>test</groupId>" +
                                           "    <artifactId>m2</artifactId>" +
                                           "    <version>1</version>" +
                                           "  </dependency>" +
                                           "</dependencies>");

    VirtualFile m2 = createModulePom("m2", "<groupId>test</groupId>" +
                                           "<artifactId>m2</artifactId>" +
                                           "<version>1</version>" +

                                           "<dependencies>" +
                                           "  <dependency>" +
                                           "    <groupId>jmock</groupId>" +
                                           "    <artifactId>jmock</artifactId>" +
                                           "    <version>1.0</version>" +
                                           "    <scope>provided</scope>" +
                                           "  </dependency>" +
                                           "  <dependency>" +
                                           "    <groupId>junit</groupId>" +
                                           "    <artifactId>junit</artifactId>" +
                                           "    <version>4.0</version>" +
                                           "    <scope>test</scope>" +
                                           "  </dependency>" +
                                           "</dependencies>");

    importProjects(m1, m2);
    assertModules("m1", "m2");

    assertModuleModuleDeps("m1", "m2");
    assertModuleLibDeps("m1");
    assertModuleLibDeps("m2", "Maven: jmock:jmock:1.0", "Maven: junit:junit:4.0");

    setupJdkForModule("m1");
    setupJdkForModule("m2");

    assertModuleClasspath("m1",
                         getProjectPath() + "/m1/target/classes",
                         getProjectPath() + "/m2/target/classes");

    assertModuleClasspath("m2",
                         getProjectPath() + "/m2/target/classes",
                         getRepositoryPath() + "/jmock/jmock/1.0/jmock-1.0.jar",
                         getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar");
  }

  private void assertModuleClasspath(String moduleName, String... paths) throws CantRunException {
    JavaParameters params = new JavaParameters();
    params.configureByModule(getModule(moduleName), JavaParameters.CLASSES_ONLY);
    List<String> systemPaths = new ArrayList<String>();
    for (String each : paths) {
      systemPaths.add(FileUtil.toSystemDependentName(each));
    }
    assertOrderedElementsAreEqual(params.getClassPath().getPathList(), systemPaths);
  }
}
