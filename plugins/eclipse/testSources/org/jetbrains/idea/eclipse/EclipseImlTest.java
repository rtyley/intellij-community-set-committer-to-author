/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/*
 * User: anna
 * Date: 28-Nov-2008
 */
package org.jetbrains.idea.eclipse;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.impl.RootModelImpl;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.IdeaTestCase;
import junit.framework.Assert;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.idea.eclipse.conversion.EclipseClasspathReader;
import org.jetbrains.idea.eclipse.importWizard.EclipseProjectFinder;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;

public class EclipseImlTest extends IdeaTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final File testRoot = new File(PluginPathManager.getPluginHomePath("eclipse") + "/testData/iml");
    assertTrue(testRoot.getAbsolutePath(), testRoot.isDirectory());

    final File currentTestRoot = new File(testRoot, getTestName(true));
    assertTrue(currentTestRoot.getAbsolutePath(), currentTestRoot.isDirectory());

    FileUtil.copyDir(currentTestRoot, new File(getProject().getBaseDir().getPath()));


  }

  private void doTest() throws Exception {
    doTest("/test");
  }

  private void doTest(final String relativePath) throws Exception {
    final String path = getProject().getBaseDir().getPath() + relativePath;

    final File classpathFile = new File(path, EclipseXml.DOT_CLASSPATH_EXT);
    String fileText = new String(FileUtil.loadFileText(classpathFile)).replaceAll("\\$ROOT\\$", getProject().getBaseDir().getPath());
    if (!SystemInfo.isWindows) {
      fileText = fileText.replaceAll(EclipseXml.FILE_PROTOCOL + "/", EclipseXml.FILE_PROTOCOL);
    }
    final Element classpathElement = JDOMUtil.loadDocument(fileText).getRootElement();
    final Module module = ApplicationManager.getApplication().runWriteAction(new Computable<Module>() {
      public Module compute() {
        return ModuleManager.getInstance(getProject())
          .newModule(new File(path) + File.separator + EclipseProjectFinder
            .findProjectName(path) + IdeaXml.IML_EXT, StdModuleTypes.JAVA);
      }
    });
    final ModifiableRootModel rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
    final EclipseClasspathReader classpathReader = new EclipseClasspathReader(path, getProject(), null);
    classpathReader.init(rootModel);
    classpathReader
      .readClasspath(rootModel, new ArrayList<String>(), new ArrayList<String>(), new HashSet<String>(), new HashSet<String>(), null, classpathElement);
    rootModel.commit();
    final RootModelImpl model = (RootModelImpl)ModuleRootManager.getInstance(module).getModifiableModel();
    final Element actualImlElement = new Element("root");
    model.writeExternal(actualImlElement);
    model.dispose();

    PathMacroManager.getInstance(module).collapsePaths(actualImlElement);
    PathMacroManager.getInstance(getProject()).collapsePaths(actualImlElement);


    final Element expectedIml = JDOMUtil.loadDocument(new File(getProject().getBaseDir().getPath() + "/expected", "expected.iml")).getRootElement();
    Assert.assertTrue(new String(JDOMUtil.printDocument(new Document(actualImlElement), "\n")),
                      JDOMUtil.areElementsEqual(expectedIml, actualImlElement));
  }


  public void testWorkspaceOnly() throws Exception {
    doTest();
  }

  public void testSrcBinJREProject() throws Exception {
    doTest();
  }


  public void testEmptySrc() throws Exception {
    doTest();
  }

  public void testEmpty() throws Exception {
    doTest();
  }

  public void testAllProps() throws Exception {
    doTest("/eclipse-ws-3.4.1-a/all-props");
  }

  public void testRoot() throws Exception {
    doTest();
  }

}
