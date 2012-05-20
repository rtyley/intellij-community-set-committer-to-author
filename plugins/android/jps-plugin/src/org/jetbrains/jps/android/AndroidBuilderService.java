package org.jetbrains.jps.android;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.BuilderService;
import org.jetbrains.jps.incremental.ModuleLevelBuilder;
import org.jetbrains.jps.incremental.ProjectLevelBuilder;

import java.util.Arrays;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidBuilderService extends BuilderService {
  @NotNull
  @Override
  public List<? extends ModuleLevelBuilder> createModuleLevelBuilders() {
    return Arrays.asList(new AndroidSourceGeneratingBuilder(),
                         new AndroidLibraryPackagingBuilder());
  }

  @NotNull
  @Override
  public List<? extends ProjectLevelBuilder> createProjectLevelBuilders() {
    return Arrays.asList(new AndroidDexBuilder(),
                         new AndroidPackagingBuilder());
  }
}
