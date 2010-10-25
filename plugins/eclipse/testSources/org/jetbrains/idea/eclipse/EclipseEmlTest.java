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

/*
 * User: anna
 * Date: 28-Nov-2008
 */
package org.jetbrains.idea.eclipse;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.IdeaTestCase;
import junit.framework.Assert;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.idea.eclipse.config.EclipseClasspathStorageProvider;
import org.jetbrains.idea.eclipse.conversion.IdeaSpecificSettings;
import org.jetbrains.idea.eclipse.importWizard.EclipseProjectFinder;

import java.io.File;
import java.io.IOException;

public class EclipseEmlTest extends IdeaTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final File testRoot = new File(PluginPathManager.getPluginHomePath("eclipse") + "/testData", "eml");
    assertTrue(testRoot.getAbsolutePath(), testRoot.isDirectory());

    final File currentTestRoot = new File(testRoot, getTestName(true));
    assertTrue(currentTestRoot.getAbsolutePath(), currentTestRoot.isDirectory());

    FileUtil.copyDir(currentTestRoot, new File(getProject().getBaseDir().getPath()));


  }



  protected static void doTest(String relativePath, final Project project) throws Exception {
    final String path = project.getBaseDir().getPath() + relativePath;
    final Module module = ApplicationManager.getApplication().runWriteAction(new Computable<Module>() {
      @Override
      public Module compute() {
        return ModuleManager.getInstance(project)
          .newModule(path + "/" + EclipseProjectFinder.findProjectName(path) + IdeaXml.IML_EXT, StdModuleTypes.JAVA);
      }
    });

    replaceRoot(path, EclipseXml.DOT_CLASSPATH_EXT, project);


    final EclipseClasspathStorageProvider.EclipseClasspathConverter converter =
      new EclipseClasspathStorageProvider.EclipseClasspathConverter(module);
    final ModifiableRootModel rootModel = ModuleRootManager.getInstance(module).getModifiableModel();

    final Element classpathElement = JDOMUtil.loadDocument(new String(FileUtil.loadFileText(new File(path, EclipseXml.DOT_CLASSPATH_EXT)))).getRootElement();
    converter.getClasspath(rootModel, classpathElement);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        rootModel.commit();
      }
    });


    checkModule(path, module);
  }

  protected static void checkModule(String path, Module module) throws WriteExternalException, IOException, JDOMException {
    ModifiableRootModel rootModel;
    rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
    final Element root = new Element("component");
    IdeaSpecificSettings.writeIDEASpecificClasspath(root, rootModel);
    rootModel.dispose();

    final String resulted = new String(JDOMUtil.printDocument(new Document(root), "\n"));

    final File emlFile = new File(path, module.getName() + EclipseXml.IDEA_SETTINGS_POSTFIX);
    Assert.assertTrue(resulted.replaceAll(StringUtil.escapeToRegexp(module.getProject().getBaseDir().getPath()), "\\$ROOT\\$"),
                      JDOMUtil.areElementsEqual(root, JDOMUtil.loadDocument(new String(FileUtil.loadFileText(emlFile))).getRootElement()));
  }

  private static void replaceRoot(String path, final String child, final Project project) throws IOException, JDOMException {
    final File emlFile = new File(path, child);
    String fileText = new String(FileUtil.loadFileText(emlFile)).replaceAll("\\$ROOT\\$", project.getBaseDir().getPath());
    if (!SystemInfo.isWindows) {
      fileText = fileText.replaceAll(EclipseXml.FILE_PROTOCOL + "/", EclipseXml.FILE_PROTOCOL);
    }
    JDOMUtil.writeDocument(JDOMUtil.loadDocument(fileText), emlFile, "\n");
  }

  public void testSrcInZip() throws Exception {
    doTest("/test", getProject());
  }

}
