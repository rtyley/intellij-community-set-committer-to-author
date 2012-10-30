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
package org.jetbrains.jps.maven.model.impl;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.*;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.maven.model.JpsMavenExtensionService;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.io.PrintWriter;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/21/12
 */
public class MavenResourcesTarget extends ModuleBasedTarget<MavenResourceRootDescriptor> {

  MavenResourcesTarget(final MavenResourcesTargetType type, @NotNull JpsModule module) {
    super(type, module);
  }

  @Override
  public String getId() {
    return myModule.getName();
  }

  @Override
  public Collection<BuildTarget<?>> computeDependencies(BuildTargetRegistry targetRegistry) {
    final JavaModuleBuildTargetType targetType = isTests() ? JavaModuleBuildTargetType.TEST : JavaModuleBuildTargetType.PRODUCTION;
    return Collections.<BuildTarget<?>>singletonList(new ModuleBuildTarget(myModule, targetType));
  }

  @NotNull
  @Override
  public List<MavenResourceRootDescriptor> computeRootDescriptors(JpsModel model, ModuleExcludeIndex index, IgnoredFileIndex ignoredFileIndex, BuildDataPaths dataPaths) {
    // todo: should we honor ignored and excluded roots here?
    final List<MavenResourceRootDescriptor> result = new ArrayList<MavenResourceRootDescriptor>();
    for (ResourceRootConfiguration resource : getRootConfigurations(dataPaths)) {
      result.add(new MavenResourceRootDescriptor(this, resource));
    }
    return result;
  }

  private Collection<ResourceRootConfiguration> getRootConfigurations(BuildDataPaths dataPaths) {
    return getRootConfigurations(getModuleResourcesConfiguration(dataPaths));
  }

  private Collection<ResourceRootConfiguration> getRootConfigurations(@Nullable MavenModuleResourceConfiguration moduleConfig) {
    if (moduleConfig != null) {
      return isTests() ? moduleConfig.testResources : moduleConfig.resources;
    }
    return Collections.emptyList();
  }

  public MavenModuleResourceConfiguration getModuleResourcesConfiguration(BuildDataPaths dataPaths) {
    final MavenProjectConfiguration projectConfig = JpsMavenExtensionService.getInstance().getMavenProjectConfiguration(dataPaths);
    return projectConfig.moduleConfigurations.get(myModule.getName());
  }

  public boolean isTests() {
    return ((MavenResourcesTargetType)getTargetType()).isTests();
  }

  @Nullable
  @Override
  public BuildRootDescriptor findRootDescriptor(String rootId, BuildRootIndex rootIndex) {
    for (BuildRootDescriptor descriptor : rootIndex.getTargetRoots(this, null)) {
      if (descriptor.getRootId().equals(rootId)) {
        return descriptor;
      }
    }
    return null;
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return getTargetType().getTypeId() + ":" + myModule.getName();
  }

  @NotNull
  @Override
  public Collection<File> getOutputRoots(CompileContext context) {
    final Set<File> result = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);
    final File moduleOutput = getModuleOutputDir();
    for (ResourceRootConfiguration resConfig : getRootConfigurations(context.getProjectDescriptor().dataManager.getDataPaths())) {
      final File output = getOutputDir(moduleOutput, resConfig);
      if (output != null) {
        result.add(output);
      }
    }
    return result;
  }

  @Nullable
  public File getModuleOutputDir() {
    return JpsJavaExtensionService.getInstance().getOutputDirectory(myModule, isTests());
  }

  @Nullable
  public static File getOutputDir(@Nullable File moduleOutput, ResourceRootConfiguration config) {
    if (moduleOutput == null) {
      return null;
    }
    String targetPath = config.targetPath;
    if (StringUtil.isEmptyOrSpaces(targetPath)) {
      return moduleOutput;
    }
    final File targetPathFile = new File(targetPath);
    return targetPathFile.isAbsolute()? targetPathFile : new File(moduleOutput, targetPath);
  }

  @Override
  public void writeConfiguration(PrintWriter out, BuildDataPaths dataPaths, BuildRootIndex buildRootIndex) {
    final MavenModuleResourceConfiguration configuration = getModuleResourcesConfiguration(dataPaths);
    if (configuration != null) {
      out.write(Integer.toHexString(configuration.computeConfigurationHash(isTests())));
    }
  }
}
