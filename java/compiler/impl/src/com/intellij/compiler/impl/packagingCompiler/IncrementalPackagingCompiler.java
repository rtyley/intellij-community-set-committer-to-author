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

package com.intellij.compiler.impl.packagingCompiler;

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.compiler.make.BuildConfiguration;
import com.intellij.openapi.compiler.make.BuildParticipant;
import com.intellij.openapi.compiler.make.BuildParticipantProvider;
import com.intellij.openapi.deployment.DeploymentUtilImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author nik
 */
public class IncrementalPackagingCompiler extends PackagingCompilerBase {
  public static final Key<Set<BuildParticipant>> AFFECTED_PARTICIPANTS_KEY = Key.create("AFFECTED_PARTICIPANTS");
  private static final Key<List<String>> FILES_TO_DELETE_KEY = Key.create("files_to_delete");
  private static final Key<OldProcessingItemsBuilderContext> BUILDER_CONTEXT_KEY = Key.create("processing_items_builder");
  @NonNls private static final String INCREMENTAL_PACKAGING_CACHE_ID = "incremental_packaging";

  public IncrementalPackagingCompiler() {
    super(FILES_TO_DELETE_KEY, BUILDER_CONTEXT_KEY);
  }

  @Override
  protected PackagingProcessingItem[] collectItems(OldProcessingItemsBuilderContext builderContext, final Project project) {
    Module[] allModules = ModuleManager.getInstance(project).getSortedModules();
    final BuildParticipantProvider[] providers = DeploymentUtilImpl.getBuildParticipantProviders();
    for (BuildParticipantProvider provider : providers) {
      addItemsForProvider(provider, allModules, builderContext);
    }
    return builderContext.getProcessingItems();
  }

  private static void addItemsForProvider(final BuildParticipantProvider provider,
                                                                       final Module[] modulesToCompile,
                                                                       OldProcessingItemsBuilderContext builderContext) {
    for (Module module : modulesToCompile) {
      final Collection<? extends BuildParticipant> participants = provider.getParticipants(module);
      for (BuildParticipant participant : participants) {
        addItemsForParticipant(participant, builderContext);
      }
    }
  }

  private static void addItemsForParticipant(final BuildParticipant participant, OldProcessingItemsBuilderContext builderContext) {
    participant.buildStarted(builderContext.getCompileContext());
    new ProcessingItemsBuilder(participant, builderContext).build();
  }


  @NotNull
  public String getDescription() {
    return CompilerBundle.message("incremental.packaging.compiler.description");
  }

  protected String getOutputCacheId() {
    return INCREMENTAL_PACKAGING_CACHE_ID;
  }

  protected void onBuildFinished(OldProcessingItemsBuilderContext builderContext, JarsBuilder builder, final Project project) throws Exception {
    final Set<BuildParticipant> affectedParticipants = getAffectedParticipants(builderContext.getCompileContext());

    for (ExplodedDestinationInfo destination : builder.getJarsDestinations()) {
      affectedParticipants.add(builderContext.getDestinationOwner(destination));
    }

    CompileContext context = builderContext.getCompileContext();
    for (BuildParticipant participant : affectedParticipants) {
      BuildConfiguration buildConfiguration = participant.getBuildConfiguration();
      if (participant.willBuildExploded()) {
        participant.afterExplodedCreated(new File(FileUtil.toSystemDependentName(DeploymentUtilImpl.getOrCreateExplodedDir(participant))),
                                         context);
      }
      String jarPath = buildConfiguration.getJarPath();
      if (buildConfiguration.isJarEnabled() && jarPath != null) {
        participant.afterJarCreated(new File(FileUtil.toSystemDependentName(jarPath)), context);
      }
      participant.buildFinished(context);
    }
  }

  public static Set<BuildParticipant> getAffectedParticipants(CompileContext context) {
    return context.getUserData(AFFECTED_PARTICIPANTS_KEY);
  }

  @Override
  protected void beforeBuildStarted(OldProcessingItemsBuilderContext context) {
    context.getCompileContext().putUserData(AFFECTED_PARTICIPANTS_KEY, new HashSet<BuildParticipant>());
  }

  protected OldProcessingItemsBuilderContext createContext(CompileContext context) {
    return new OldProcessingItemsBuilderContext(context);
  }

  @Override
  protected void onFileCopied(OldProcessingItemsBuilderContext builderContext, ExplodedDestinationInfo explodedDestination) {
    getAffectedParticipants(builderContext.getCompileContext()).add(builderContext.getDestinationOwner(explodedDestination));
  }

  protected boolean doNotStartBuild(CompileContext context) {
    Module[] affectedModules = context.getCompileScope().getAffectedModules();
    return affectedModules.length == 0;
  }
}
