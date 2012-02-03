package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.Module;
import org.jetbrains.jps.PathUtil;
import org.jetbrains.jps.Project;

import java.io.File;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 1/11/12
 */
public class ModuleRootsIndex {
  private final Map<File, RootDescriptor> myRootToModuleMap = new HashMap<File, RootDescriptor>();
  private final Map<Module, List<RootDescriptor>> myModuleToRootsMap = new HashMap<Module, List<RootDescriptor>>();
  private final int myTotalModuleCount;
  private final Set<File> myExcludedRoots = new HashSet<File>();

  public ModuleRootsIndex(Project project) {
    final Collection<Module> allModules = project.getModules().values();
    myTotalModuleCount = allModules.size();
    for (Module module : allModules) {
      List<RootDescriptor> moduleRoots = myModuleToRootsMap.get(module);
      if (moduleRoots == null) {
        moduleRoots = new ArrayList<RootDescriptor>();
        myModuleToRootsMap.put(module, moduleRoots);
      }
      for (String r : module.getSourceRoots()) {
        final File root = new File(FileUtil.toCanonicalPath(r));
        final RootDescriptor descriptor = new RootDescriptor(module, root, false);
        myRootToModuleMap.put(root, descriptor);
        moduleRoots.add(descriptor);
      }
      for (String r : module.getTestRoots()) {
        final File root = new File(FileUtil.toCanonicalPath(r));
        final RootDescriptor descriptor = new RootDescriptor(module, root, true);
        myRootToModuleMap.put(root, descriptor);
        moduleRoots.add(descriptor);
      }
      for (String r : module.getExcludes()) {
        final File root = new File(FileUtil.toCanonicalPath(r));
        myExcludedRoots.add(root);
      }
    }
  }

  public int getTotalModuleCount() {
    return myTotalModuleCount;
  }

  @NotNull
  public List<RootDescriptor> getModuleRoots(Module module) {
    final List<RootDescriptor> descriptors = myModuleToRootsMap.get(module);
    return descriptors != null? Collections.unmodifiableList(descriptors) : Collections.<RootDescriptor>emptyList();
  }

  @Nullable
  public RootDescriptor getRootDescriptor(File root) {
    return myRootToModuleMap.get(root);
  }

  @Nullable
  public RootDescriptor getModuleAndRoot(File file) {
    File current = file;
    while (current != null) {
      final RootDescriptor descriptor = getRootDescriptor(current);
      if (descriptor != null) {
        return descriptor;
      }
      current = FileUtil.getParentFile(current);
    }
    return null;
  }

  public boolean isExcluded(File file) {
    return PathUtil.isUnder(myExcludedRoots, file);
  }
}
