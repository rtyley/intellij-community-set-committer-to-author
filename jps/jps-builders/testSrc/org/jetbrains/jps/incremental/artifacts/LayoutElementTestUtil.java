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
package org.jetbrains.jps.incremental.artifacts;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.artifacts.*;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.Collections;

/**
 * @author nik
 */
public class LayoutElementTestUtil {
  public static LayoutElementCreator root() {
    return new LayoutElementCreator(new RootElement(Collections.<LayoutElement>emptyList()), null);
  }

  public static LayoutElementCreator archive(String name) {
    return new LayoutElementCreator(new ArchiveElement(name, Collections.<LayoutElement>emptyList()), null);
  }

  public static void addArtifactToLayout(Artifact main, Artifact included) {
    final ArtifactLayoutElement element = new ArtifactLayoutElement();
    element.setArtifactName(included.getName());
    ((CompositeLayoutElement)main.getRootElement()).getChildren().add(element);
  }

  public static class LayoutElementCreator {
    private CompositeLayoutElement myElement;
    private LayoutElementCreator myParent;
    
    public LayoutElementCreator(CompositeLayoutElement element, LayoutElementCreator parent) {
      myElement = element;
      myParent = parent;
    }

    public LayoutElementCreator dir(String name) {
      DirectoryElement dir = new DirectoryElement(name, Collections.<LayoutElement>emptyList());
      myElement.getChildren().add(dir);
      return new LayoutElementCreator(dir, this);
    }

    public LayoutElementCreator archive(String name) {
      ArchiveElement archive = new ArchiveElement(name, Collections.<LayoutElement>emptyList());
      myElement.getChildren().add(archive);
      return new LayoutElementCreator(archive, this);
    }

    public LayoutElementCreator fileCopy(String filePath) {
      return fileCopy(filePath, null);
    }

    public LayoutElementCreator fileCopy(String filePath, @Nullable String outputFileName) {
      return element(new FileCopyElement(filePath, outputFileName));
    }

    public LayoutElementCreator dirCopy(String dirPath) {
      return element(new DirectoryCopyElement(dirPath));
    }

    public LayoutElementCreator module(JpsModule module) {
      final ModuleOutputElement element = new ModuleOutputElement();
      element.setModuleName(module.getName());
      return element(element);
    }

    public LayoutElementCreator element(LayoutElement element) {
      myElement.getChildren().add(element);
      return this;
    }

    public LayoutElementCreator lib(JpsLibrary library) {
      final LibraryFilesElement element = new LibraryFilesElement();
      element.setLibraryName(library.getName());
      element.setLibraryLevel(LibraryFilesElement.PROJECT_LEVEL);
      return element(element);
    }

    public LayoutElementCreator extractedDir(String jarPath, String pathInJar) {
      ExtractedDirectoryElement dir = new ExtractedDirectoryElement();
      dir.setJarPath(jarPath);
      dir.setPathInJar(pathInJar);
      return element(dir);
    }

    public LayoutElementCreator artifact(Artifact included) {
      final ArtifactLayoutElement element = new ArtifactLayoutElement();
      element.setArtifactName(included.getName());
      return element(element);
    }

    public LayoutElementCreator end() {
      return myParent;
    }

    public CompositeLayoutElement buildElement() {
      if (myParent != null) {
        return myParent.buildElement();
      }
      return myElement;
    }
  }
}
