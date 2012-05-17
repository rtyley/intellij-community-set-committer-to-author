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
package com.intellij.core;

import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.ModuleManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * @author yole
 */
public class CoreProjectLoader {
  public static void loadProject(Project project, @NotNull VirtualFile virtualFile) throws IOException, JDOMException {
    if (virtualFile.isDirectory() && virtualFile.findChild(".idea") != null) {
      loadDirectoryProject(project, virtualFile);
    }
    else {
      // TODO load .ipr
      throw new UnsupportedOperationException();
    }
  }

  private static void loadDirectoryProject(Project project, VirtualFile projectDir) throws IOException, JDOMException {
    VirtualFile dotIdea = projectDir.findChild(".idea");
    VirtualFile modulesXml = dotIdea.findChild("modules.xml");
    final Document document = JDOMUtil.loadDocument(new ByteArrayInputStream(modulesXml.contentsToByteArray()));
    final Element moduleManagerState = getComponentState(document, "ProjectModuleManager");
    if (moduleManagerState == null) {
      throw new JDOMException("cannot find ProjectModuleManager state in modules.xml");
    }
    final CoreModuleManager moduleManager = (CoreModuleManager)ModuleManager.getInstance(project);
    moduleManager.loadState(moduleManagerState);
    moduleManager.loadModules();
  }

  @Nullable
  private static Element getComponentState(Document document, String componentName) {
    final Element[] elements = JDOMUtil.getElements(document.getRootElement());
    for (Element element : elements) {
      if (element.getName().equals("component") && componentName.equals(element.getAttributeValue("name"))) {
        return element;
      }
    }
    return null;
  }
}
