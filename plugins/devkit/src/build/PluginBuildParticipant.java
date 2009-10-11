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
package org.jetbrains.idea.devkit.build;

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.compiler.make.BuildConfiguration;
import com.intellij.openapi.compiler.make.BuildParticipantBase;
import com.intellij.openapi.compiler.make.BuildRecipe;
import com.intellij.openapi.deployment.DeploymentUtil;
import com.intellij.openapi.deployment.LibraryLink;
import com.intellij.openapi.deployment.ModuleLink;
import com.intellij.openapi.deployment.PackagingMethod;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.descriptors.ConfigFile;
import com.intellij.util.descriptors.CustomConfigFile;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.Dependency;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.module.PluginModuleType;
import org.jetbrains.idea.devkit.projectRoots.IdeaJdk;
import org.jetbrains.idea.devkit.util.DescriptorUtil;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * @author peter
*/
public class PluginBuildParticipant extends BuildParticipantBase {
  @NonNls private static final String CLASSES = "/classes";
  @NonNls private static final String LIB = "/lib/";
  @NonNls private static final String LIB_DIRECTORY = "lib";
  private final PluginBuildConfiguration myPluginBuildConfiguration;

  public PluginBuildParticipant(final Module module, final PluginBuildConfiguration pluginBuildConfiguration) {
    super(module);
    myPluginBuildConfiguration = pluginBuildConfiguration;
  }

  public BuildRecipe getBuildInstructions(final CompileContext context) {
    //todo[nik] cache?
    final BuildRecipe buildRecipe = DeploymentUtil.getInstance().createBuildRecipe();
    registerBuildInstructions(buildRecipe, context);
    return buildRecipe;
  }

  protected void registerBuildInstructions(final BuildRecipe instructions, final CompileContext context) {
    Sdk jdk = IdeaJdk.findIdeaJdk(ModuleRootManager.getInstance(getModule()).getSdk());
    if (jdk != null && IdeaJdk.isFromIDEAProject(jdk.getHomePath())) {
      return;
    }

    registerDescriptorCopyingInstructions(instructions, context);

    if (jdk == null) {
      context.addMessage(CompilerMessageCategory.ERROR, DevKitBundle.message("jdk.type.incorrect", getModule().getName()), null, -1, -1);
      return;
    }

    final Module[] wrongSetDependencies = PluginBuildUtil.getWrongSetDependencies(getModule());
    if (wrongSetDependencies.length != 0) {
      boolean realProblems = false;
      final String pluginId = DescriptorUtil.getPluginId(getModule());

      for (Module dependency : wrongSetDependencies) {
        if (!PluginModuleType.isOfType(dependency)) {
          realProblems = true;
          context.addMessage(CompilerMessageCategory.ERROR,
                             DevKitBundle.message("incorrect.dependency.non-plugin-module", dependency.getName(), getModule().getName()), null,
                             -1, -1);
        }
        else {
          final XmlFile pluginXml = PluginModuleType.getPluginXml(dependency);
          boolean isDeclared = false;
          if (pluginXml != null) {
            final XmlTag rootTag = pluginXml.getDocument().getRootTag();
            final XmlTag[] dependencies = rootTag != null ? rootTag.findSubTags("depends") : XmlTag.EMPTY;
            for (XmlTag dep : dependencies) {
              if (dep.getValue().getTrimmedText().equals(pluginId)) {
                isDeclared = true;
                break;
              }
            }
          }
          if (!isDeclared) {
            // make this a warning instead?
            realProblems = true;
            context.addMessage(CompilerMessageCategory.ERROR,
                               DevKitBundle.message("incorrect.dependency.not-declared", dependency.getName(), getModule().getName()), null, -1,
                               -1);
          }
        }
      }
      if (realProblems) return;
    }

    final String explodedPath = myPluginBuildConfiguration.getExplodedPath();
    if (explodedPath == null) return; //where to put everything?
    HashSet<Module> modules = new HashSet<Module>();
    PluginBuildUtil.getDependencies(getModule(), modules);

    ModuleLink[] containingModules = new ModuleLink[modules.size()];
    int i = 0;
    final DeploymentUtil makeUtil = DeploymentUtil.getInstance();
    for (Module dep : modules) {
      ModuleLink link = makeUtil.createModuleLink(dep, getModule());
      containingModules[i++] = link;
      link.setPackagingMethod(PackagingMethod.COPY_FILES);
      link.setURI(CLASSES);
    }

    // output may be excluded, copy it nevertheless
    makeUtil.addModuleOutputContents(context, instructions, getModule(), getModule(), CLASSES, explodedPath, null);

    // child Java utility modules
    makeUtil.addJavaModuleOutputs(getModule(), containingModules, instructions, context, explodedPath, DevKitBundle.message("presentable.plugin.module.name",
                                                                                                                            ModuleUtil.getModuleNameInReadAction(getModule())));

    HashSet<Library> libs = new HashSet<Library>();
    PluginBuildUtil.getLibraries(getModule(), libs);
    for (Module dependentModule : modules) {
      PluginBuildUtil.getLibraries(dependentModule, libs);
    }

    final LibraryLink[] libraryLinks = new LibraryLink[libs.size()];
    i = 0;
    for (Library library : libs) {
      LibraryLink link = makeUtil.createLibraryLink(library, getModule());
      libraryLinks[i++] = link;
      link.setPackagingMethod(PackagingMethod.COPY_FILES);
      final boolean onlyDirs = link.hasDirectoriesOnly();
      if (onlyDirs) {//todo split one lib into 2 separate libs if there are jars and dirs
        link.setURI(CLASSES);
      }
      else {
        link.setURI(LIB);
      }
    }

    // libraries
    final VirtualFile libDir = jdk.getHomeDirectory().findFileByRelativePath(LIB_DIRECTORY);
    for (i = 0; i < libraryLinks.length; i++) {
      LibraryLink libraryLink = libraryLinks[i];
      final Library library = libraryLink.getLibrary();
      if (library != null) {
        VirtualFile[] files = library.getFiles(OrderRootType.CLASSES);
        for (VirtualFile file : files) {
          if (file.getFileSystem() instanceof JarFileSystem) {
            file = ((JarFileSystem)file.getFileSystem()).getVirtualFileForJar(file);
          }
          if (libDir != null && file != null && VfsUtil.isAncestor(libDir, file, false)) {
            context.addMessage(CompilerMessageCategory.ERROR, DevKitBundle.message("dont.add.idea.libs.to.classpath", file.getName()), null,
                               -1, -1);
          }
        }
        makeUtil.addLibraryLink(context, instructions, libraryLink, getModule(), explodedPath);
      }
    }
  }

  protected CustomConfigFile[] getCustomDescriptors() {
    final ConfigFile[] configFiles = getDeploymentDescriptors();
    if (configFiles.length == 1) {
      final ConfigFile configFile = configFiles[0];
      final XmlFile xmlFile = configFile.getXmlFile();
      if (xmlFile != null) {
        final XmlDocument document = xmlFile.getDocument();
        if (document != null) {
          final DomElement domElement = DomManager.getDomManager(xmlFile.getProject()).getDomElement(document.getRootTag());
          if (domElement instanceof IdeaPlugin) {
            final ArrayList<CustomConfigFile> list = new ArrayList<CustomConfigFile>();
            for(Dependency dependency: ((IdeaPlugin)domElement).getDependencies()) {
              final String file = dependency.getConfigFile().getValue();
              final VirtualFile virtualFile = configFile.getVirtualFile();
              assert virtualFile != null;
              final VirtualFile parent = virtualFile.getParent();
              assert parent != null;
              final String url = parent.getUrl();
              list.add(new CustomConfigFile(url + "/" + file, configFile.getMetaData().getDirectoryPath()));
            }
            return list.toArray(new CustomConfigFile[list.size()]);
          }
        }
      }
    }
    return super.getCustomDescriptors();
  }

  protected ConfigFile[] getDeploymentDescriptors() {
    ConfigFile configFile = myPluginBuildConfiguration.getPluginXML();
    if (configFile != null) {
      return new ConfigFile[]{configFile};
    }
    return ConfigFile.EMPTY_ARRAY;
  }

  public BuildConfiguration getBuildConfiguration() {
    return myPluginBuildConfiguration;
  }
}
