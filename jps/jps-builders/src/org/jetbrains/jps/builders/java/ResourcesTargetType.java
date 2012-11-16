package org.jetbrains.jps.builders.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildTargetLoader;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.incremental.ResourcesTarget;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.*;

/**
 * @author nik
 */
public class ResourcesTargetType extends BuildTargetType<ResourcesTarget> {
  public static final ResourcesTargetType PRODUCTION = new ResourcesTargetType("resources-production", false);
  public static final ResourcesTargetType TEST = new ResourcesTargetType("resources-test", true);
  public static final List<ResourcesTargetType> ALL_TYPES = Arrays.asList(PRODUCTION, TEST);

  private boolean myTests;

  private ResourcesTargetType(String typeId, boolean tests) {
    super(typeId);
    myTests = tests;
  }

  @NotNull
  @Override
  public List<ResourcesTarget> computeAllTargets(@NotNull JpsModel model) {
    List<JpsModule> modules = model.getProject().getModules();
    List<ResourcesTarget> targets = new ArrayList<ResourcesTarget>(modules.size());
    for (JpsModule module : modules) {
      targets.add(new ResourcesTarget(module, this));
    }
    return targets;
  }

  @NotNull
  @Override
  public Loader createLoader(@NotNull JpsModel model) {
    return new Loader(model);
  }

  public boolean isTests() {
    return myTests;
  }

  public static ResourcesTargetType getInstance(boolean tests) {
    return tests ? TEST : PRODUCTION;
  }

  private class Loader extends BuildTargetLoader<ResourcesTarget> {
    private final Map<String, JpsModule> myModules;

    public Loader(JpsModel model) {
      myModules = new HashMap<String, JpsModule>();
      for (JpsModule module : model.getProject().getModules()) {
        myModules.put(module.getName(), module);
      }
    }

    @Nullable
    @Override
    public ResourcesTarget createTarget(@NotNull String targetId) {
      JpsModule module = myModules.get(targetId);
      return module != null ? new ResourcesTarget(module, ResourcesTargetType.this) : null;
    }
  }
}
