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
package com.intellij.openapi.deployment;

import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.PathUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LibraryLinkImpl extends LibraryLink {
  private static final Map<PackagingMethod, String> methodToDescriptionForDirs = new HashMap<PackagingMethod, String>();
  private static final Map<PackagingMethod, String> methodToDescriptionForFiles = new HashMap<PackagingMethod, String>();
  @NonNls public static final String LEVEL_ATTRIBUTE_NAME = "level";
  @NonNls public static final String URL_ELEMENT_NAME = "url";
  @NonNls private static final String TEMP_ELEMENT_NAME = "temp";
  @NonNls public static final String NAME_ATTRIBUTE_NAME = "name";

  @NonNls private static final String JAR_SUFFIX = ".jar";

  static {
    methodToDescriptionForDirs.put(PackagingMethod.DO_NOT_PACKAGE, CompilerBundle.message("packaging.method.description.do.not.package"));
    methodToDescriptionForDirs.put(PackagingMethod.COPY_FILES, CompilerBundle.message("packaging.method.description.copy.directories"));
    methodToDescriptionForDirs.put(PackagingMethod.JAR_AND_COPY_FILE, CompilerBundle.message("packaging.method.description.jar.and.copy.file"));
    methodToDescriptionForDirs.put(PackagingMethod.JAR_AND_COPY_FILE_AND_LINK_VIA_MANIFEST, CompilerBundle.message("packaging.method.description.jar.and.copy.file.and.link.via.manifest"));

    methodToDescriptionForFiles.put(PackagingMethod.DO_NOT_PACKAGE, CompilerBundle.message("packaging.method.description.do.not.package"));
    methodToDescriptionForFiles.put(PackagingMethod.COPY_FILES, CompilerBundle.message("packaging.method.description.copy.files"));
    methodToDescriptionForFiles.put(PackagingMethod.COPY_FILES_AND_LINK_VIA_MANIFEST, CompilerBundle.message("packaging.method.description.copy.files.and.link.via.manifest"));
  }

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.deployment.LibraryLink");
  private LibraryInfo myLibraryInfo;
  private final Project myProject;

  public LibraryLinkImpl(@Nullable Library library, @NotNull Module parentModule) {
    super(parentModule);
    myProject = parentModule.getProject();
    if (library == null) {
      myLibraryInfo = new LibraryInfoImpl();
    }
    else {
      myLibraryInfo = new LibraryInfoBasedOnLibrary(library);
    }

  }

  public List<String> getClassesRootUrls() {
    List<String> urls = getUrls();
    Library library = getLibrary();
    if (library == null) {
      return urls;
    }
    return getLibraryClassRoots(library);
  }

  public static List<String> getLibraryClassRoots(final Library library) {
    VirtualFile[] files = library.getFiles(OrderRootType.CLASSES);
    ArrayList<String> classRoots = new ArrayList<String>(files.length);
    for (VirtualFile file : files) {
      classRoots.add(file.getUrl());
    }
    return classRoots;
  }

  @Nullable
  public Library getLibrary() {
    return getLibrary(null);
  }

  @Nullable
  public Library getLibrary(@Nullable ModulesProvider provider) {
    fixLibraryInfo();
    final LibraryInfo libraryInfo = myLibraryInfo;
    if (libraryInfo instanceof LibraryInfoBasedOnLibrary) {
      return ((LibraryInfoBasedOnLibrary)libraryInfo).getLibrary();
    }

    LOG.assertTrue(libraryInfo instanceof LibraryInfoImpl);
    final LibraryInfoImpl info = (LibraryInfoImpl)libraryInfo;
    final Library library = info.findLibrary(myProject, getParentModule(), provider);
    if (library != null) {
      myLibraryInfo = new LibraryInfoBasedOnLibrary(library);
    }

    return library;
  }

  public String toString() {
    return CompilerBundle.message("library.link.string.presentation.presentablename.to.uri", getPresentableName(), getURI());
  }

  public String getPresentableName() {
    final String name = getName();
    if (name != null) return name;
    List<String> urls = getUrls();
    if (urls.isEmpty()) return CompilerBundle.message("linrary.link.empty.library.presentable.name");
    final String url = urls.get(0);
    final String path = PathUtil.toPresentableUrl(url);

    return FileUtil.toSystemDependentName(path);
  }

  public String getDescription() {
    String levelName = myLibraryInfo.getLevel();
    if (levelName.equals(MODULE_LEVEL)) {
      return CompilerBundle.message("library.link.description.module.library");
    }
    final LibraryTable table = findTable(levelName, myProject);
    return table == null ? "???" : table.getPresentation().getDisplayName(false);
  }

  public String getDescriptionForPackagingMethod(PackagingMethod method) {
    if (hasDirectoriesOnly()) {
      final String text = methodToDescriptionForDirs.get(method);
      return text != null ? text : methodToDescriptionForFiles.get(method);
    }
    else {
      final String text = methodToDescriptionForFiles.get(method);
      return text != null ? text : methodToDescriptionForDirs.get(method);
    }
  }

  public List<String> getUrls() {
    fixLibraryInfo();
    return myLibraryInfo.getUrls();
  }

  public boolean equalsIgnoreAttributes(ContainerElement otherElement) {
    if (!(otherElement instanceof LibraryLink)) return false;
    final LibraryLink otherLibraryLink = (LibraryLink)otherElement;
    if (!Comparing.strEqual(getName(), otherLibraryLink.getName())) return false;
    if (!Comparing.strEqual(getLevel(), otherLibraryLink.getLevel())) return false;
    if (getName() != null) return true;
    return getUrls().equals(otherLibraryLink.getUrls());
  }

  public boolean hasDirectoriesOnly() {
    boolean hasDirsOnly = true;
    final Library library = getLibrary();
    if (library != null) {
      final VirtualFile[] files = library.getFiles(OrderRootType.CLASSES);
      for (VirtualFile file : files) {
        if (file != null && !VfsUtil.virtualToIoFile(file).isDirectory()) {
          hasDirsOnly = false;
          break;
        }
      }
    } else {
      final List<String> urls = getClassesRootUrls();
      for (final String url : urls) {
        VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
        if (file != null && !VfsUtil.virtualToIoFile(file).isDirectory()) {
          hasDirsOnly = false;
          break;
        }
      }
    }
    return hasDirsOnly;
  }

  public String getName() {
    return myLibraryInfo.getName();
  }

  public String getLevel() {
    return myLibraryInfo.getLevel();
  }

  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    myLibraryInfo.readExternal(element);

    List<String> urls = getUrls();
    if (LibraryLink.MODULE_LEVEL.equals(getLevel()) && urls.size() == 1) {
      String jarName = getJarFileName(urls.get(0));
      if (jarName != null) {
        String outputPath = getURI();
        if (outputPath != null) {
          int nameIndex = outputPath.lastIndexOf('/');
          if (outputPath.substring(nameIndex + 1).equals(jarName)) {
            if (nameIndex <= 0) {
              setURI("/");
            }
            else {
              setURI(outputPath.substring(0, nameIndex));
            }
          }
        }
      }
    }
  }

  @Nullable
  private static String getJarFileName(final String url) {
    if (!url.endsWith(JarFileSystem.JAR_SEPARATOR)) return null;

    String path = url.substring(0, url.length() - JarFileSystem.JAR_SEPARATOR.length());
    String jarName = path.substring(path.lastIndexOf('/') + 1);
    return jarName.endsWith(JAR_SUFFIX) ? jarName : null;
  }

  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    String name = getName();
    if (name == null) {
      List<String> urls = getUrls();
      for (final String url : urls) {
        final Element urlElement = new Element(URL_ELEMENT_NAME);
        urlElement.setText(url);
        element.addContent(urlElement);
      }
    }
    else {
      element.setAttribute(NAME_ATTRIBUTE_NAME, name);
    }
    if (getLevel() != null) {
      element.setAttribute(LEVEL_ATTRIBUTE_NAME, getLevel());
    }
  }

  public boolean resolveElement(ModulesProvider provider, final FacetsProvider facetsProvider) {
    return getLibrary(provider) != null;

  }

  public LibraryLink clone() {
    LibraryLink libraryLink = DeploymentUtil.getInstance().createLibraryLink(getLibrary(), getParentModule());
    Element temp = new Element(TEMP_ELEMENT_NAME);
    try {
      writeExternal(temp);
      libraryLink.readExternal(temp);
    }
    catch (Exception e) {
      LOG.error(e);
    }
    return libraryLink;
  }

  public void fixLibraryInfo() {
    if (myLibraryInfo instanceof LibraryInfoBasedOnLibrary) {
      LibraryInfoBasedOnLibrary libraryInfo = (LibraryInfoBasedOnLibrary)myLibraryInfo;
      Library library = libraryInfo.getLibrary();
      if (((LibraryEx)library).isDisposed()) {
        LibraryInfoImpl info = libraryInfo.getInfoToRestore();
        Library newLibrary = info.findLibrary(myProject, getParentModule(), null);
        myLibraryInfo = newLibrary != null ? new LibraryInfoBasedOnLibrary(newLibrary) : info;
      }
    }
  }

}
