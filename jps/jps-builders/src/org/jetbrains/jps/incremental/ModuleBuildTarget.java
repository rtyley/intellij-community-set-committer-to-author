package org.jetbrains.jps.incremental;

import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.util.JpsPathUtil;
import org.jetbrains.jps.builders.BuildRootIndex;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaDependenciesEnumerator;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsTypedModuleSourceRoot;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class ModuleBuildTarget extends BuildTarget<JavaSourceRootDescriptor> {
  private final JpsModule myModule;
  private final String myModuleName;
  private final JavaModuleBuildTargetType myTargetType;

  public ModuleBuildTarget(@NotNull JpsModule module, JavaModuleBuildTargetType targetType) {
    super(targetType);
    myTargetType = targetType;
    myModuleName = module.getName();
    myModule = module;
  }

  @NotNull
  public JpsModule getModule() {
    return myModule;
  }

  public String getModuleName() {
    return myModuleName;
  }

  public boolean isTests() {
    return myTargetType.isTests();
  }

  @Override
  public String getId() {
    return myModuleName;
  }

  @Override
  public Collection<BuildTarget<?>> computeDependencies() {
    JpsJavaDependenciesEnumerator enumerator = JpsJavaExtensionService.dependencies(myModule).compileOnly();
    if (!isTests()) {
      enumerator.productionOnly();
    }
    final List<BuildTarget<?>> dependencies = new ArrayList<BuildTarget<?>>();
    enumerator.processModules(new Consumer<JpsModule>() {
      @Override
      public void consume(JpsModule module) {
        dependencies.add(new ModuleBuildTarget(module, myTargetType));
      }
    });
    if (isTests()) {
      dependencies.add(new ModuleBuildTarget(myModule, JavaModuleBuildTargetType.PRODUCTION));
    }
    return dependencies;
  }

  @NotNull
  @Override
  public List<JavaSourceRootDescriptor> computeRootDescriptors(JpsModel model, ModuleExcludeIndex index, IgnoredFileIndex ignoredFileIndex) {
    List<JavaSourceRootDescriptor> roots = new ArrayList<JavaSourceRootDescriptor>();
    JavaSourceRootType type = isTests() ? JavaSourceRootType.TEST_SOURCE : JavaSourceRootType.SOURCE;
    for (JpsTypedModuleSourceRoot<JpsSimpleElement<JavaSourceRootProperties>> sourceRoot : myModule.getSourceRoots(type)) {
      final File root = JpsPathUtil.urlToFile(sourceRoot.getUrl());
      roots.add(new JavaSourceRootDescriptor(root, this, false, false, sourceRoot.getProperties().getData().getPackagePrefix()));
    }
    return roots;
  }

  @Override
  public JavaSourceRootDescriptor findRootDescriptor(String rootId, BuildRootIndex rootIndex) {
    List<JavaSourceRootDescriptor> descriptors = rootIndex.getRootDescriptors(new File(rootId), Collections.<JavaModuleBuildTargetType>singletonList(myTargetType), null);
    return ContainerUtil.getFirstItem(descriptors);
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return "Module '" + myModuleName + "' " + (myTargetType.isTests() ? "tests" : "production");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || !(o instanceof ModuleBuildTarget)) {
      return false;
    }

    ModuleBuildTarget target = (ModuleBuildTarget)o;
    return myTargetType == target.myTargetType && myModuleName.equals(target.myModuleName);
  }

  @Override
  public int hashCode() {
    return 31 * myModuleName.hashCode() + myTargetType.hashCode();
  }
}
