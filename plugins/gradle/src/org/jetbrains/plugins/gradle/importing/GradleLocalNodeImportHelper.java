package org.jetbrains.plugins.gradle.importing;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Ref;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.config.GradleTextAttributes;
import org.jetbrains.plugins.gradle.model.*;
import org.jetbrains.plugins.gradle.model.gradle.*;
import org.jetbrains.plugins.gradle.model.id.GradleEntityId;
import org.jetbrains.plugins.gradle.model.id.GradleEntityIdMapper;
import org.jetbrains.plugins.gradle.sync.GradleProjectStructureHelper;
import org.jetbrains.plugins.gradle.ui.GradleProjectStructureNode;
import org.jetbrains.plugins.gradle.ui.GradleProjectStructureNodeDescriptor;
import org.jetbrains.plugins.gradle.util.GradleLog;

import java.util.*;

/**
 * Gradle integration shows a project structure tree which contain nodes for gradle-local entities (modules, libraries etc).
 * End-user can select interested nodes and import them into the current intellij project.
 * <p/>
 * This class helps during that at the following ways:
 * <pre>
 * <ul>
 *   <li>filters out non gradle-local nodes;</li>
 *   <li>
 *     collects all nodes that should be imported. For example, an user can mark 'module' node to import. We need to import not
 *     only that module but its (transitive) dependencies as well. I.e. basically the algorithm looks like 'import all entities up
 *     to the path to root and all sub-entities';
 *   </li>
 *   <li>
 *     sorts entities to import in topological order. Example: let module<sub>1</sub> depend on module<sub>2</sub>. We need to
 *     import module<sub>2</sub> before module<sub>1</sub> then;
 *   </li>
 * </ul>
 * </pre>
 * 
 * @author Denis Zhdanov
 * @since 2/10/12 11:51 AM
 */
public class GradleLocalNodeImportHelper {

  @NotNull private final GradleProjectStructureHelper   myProjectStructureHelper;
  @NotNull private final GradleEntityIdMapper           myIdMapper;
  @NotNull private final GradleModuleImporter           myModuleImporter;
  @NotNull private final GradleLibraryImporter          myLibraryImporter;
  @NotNull private final GradleModuleDependencyImporter myModuleDependencyImporter;
  @NotNull private final GradleContentRootImporter      myContentRootImporter;

  public GradleLocalNodeImportHelper(@NotNull GradleProjectStructureHelper projectStructureHelper,
                                     @NotNull GradleEntityIdMapper idMapper,
                                     @NotNull GradleModuleImporter moduleImporter,
                                     @NotNull GradleLibraryImporter libraryImporter,
                                     @NotNull GradleModuleDependencyImporter moduleDependencyImporter,
                                     @NotNull GradleContentRootImporter contentRootImporter)
  {
    myProjectStructureHelper = projectStructureHelper;
    myIdMapper = idMapper;
    myModuleImporter = moduleImporter;
    myModuleDependencyImporter = moduleDependencyImporter;
    myLibraryImporter = libraryImporter;
    myContentRootImporter = contentRootImporter;
  }

  /**
   * {@link #deriveEntitiesToImport(Iterable) Derives} target entities from the given nodes
   * and {@link #importEntities(Collection) imports them}.
   * 
   * @param nodes  'anchor nodes' to import
   */
  public void importNodes(Iterable<GradleProjectStructureNode<?>> nodes) {
    final List<GradleEntity> entities = deriveEntitiesToImport(nodes);
    importEntities(entities);
  }
  
  /**
   * Collects all nodes that should be imported based on the given nodes and returns corresponding gradle entities
   * sorted in topological order.
   * 
   * @param nodes  'anchor nodes' to import
   * @return       collection of gradle entities that should be imported based on the given nodes
   */
  @NotNull
  public List<GradleEntity> deriveEntitiesToImport(@NotNull Iterable<GradleProjectStructureNode<?>> nodes) {
    EntitiesToImport toImport = new EntitiesToImport();
    for (GradleProjectStructureNode<?> node : nodes) {
      collectEntitiesToImport(node, toImport);
    }
    return toImport.getAll();
  }
  
  private void collectEntitiesToImport(@NotNull GradleProjectStructureNode<?> node, @NotNull final EntitiesToImport toImport) {
    final Ref<Boolean> recursive = new Ref<Boolean>(false);
    final GradleEntityVisitor visitor = new GradleEntityVisitorAdapter() {
      @Override
      public void visit(@NotNull GradleModule module) {
        final Module intellijModule = myProjectStructureHelper.findIntellijModule(module);
        if (intellijModule != null) {
          // Already imported
          return;
        }
        toImport.modules.add(module);
        if (!recursive.get()) {
          return;
        }
        for (GradleDependency dependency : module.getDependencies()) {
          dependency.invite(this);
        }
      }

      @Override
      public void visit(@NotNull GradleModuleDependency dependency) {
        final ModuleOrderEntry intellijModuleDependency = myProjectStructureHelper.findIntellijModuleDependency(dependency);
        if (intellijModuleDependency != null) {
          // Already imported.
          return;
        }
        toImport.dependencies.add(dependency);
        final GradleModule gradleModule = dependency.getTarget();
        final Module intellijModule = myProjectStructureHelper.findIntellijModule(gradleModule);
        if (intellijModule != null) {
          return;
        }
        boolean r = recursive.get();
        recursive.set(true);
        try {
          gradleModule.invite(this);
        }
        finally {
          recursive.set(r);
        }
      }

      @Override
      public void visit(@NotNull GradleLibraryDependency dependency) {
        final LibraryOrderEntry intellijDependency
          = myProjectStructureHelper.findIntellijLibraryDependency(dependency.getOwnerModule().getName(), dependency.getName());
        if (intellijDependency != null) {
          // Already imported.
          return;
        }
        toImport.dependencies.add(dependency);
        final GradleLibrary gradleLibrary = dependency.getTarget();
        final Library intellijLibrary = myProjectStructureHelper.findIntellijLibrary(gradleLibrary);
        if (intellijLibrary == null) {
          toImport.libraries.add(gradleLibrary);
        }
      }
    };
    
    // Collect up.
    for (GradleProjectStructureNode<?> n = node.getParent(); n != null; n = n.getParent()) {
      final GradleProjectStructureNodeDescriptor<?> descriptor = n.getDescriptor();
      if (n.getType() == GradleEntityType.SYNTHETIC) {
        continue;
      }
      if (descriptor.getAttributes() != GradleTextAttributes.GRADLE_LOCAL_CHANGE) {
        break;
      }
      Object id = descriptor.getElement();
      if (id instanceof GradleEntityId) {
        final Object entity = myIdMapper.mapIdToEntity((GradleEntityId)id);
        if (entity instanceof GradleEntity) {
          ((GradleEntity)entity).invite(visitor);
        }
      }
    }

    // Collect down.
    final Stack<GradleEntity> toProcess = new Stack<GradleEntity>();
    final Object id = node.getDescriptor().getElement();
    if (id instanceof GradleEntityId) {
      final Object entity = myIdMapper.mapIdToEntity((GradleEntityId)id);
      if (entity instanceof GradleEntity) {
        toProcess.push((GradleEntity)entity);
      }
    }
    
    recursive.set(true);
    while (!toProcess.isEmpty()) {
      final GradleEntity entity = toProcess.pop();
      entity.invite(visitor);
    }
  }

  /**
   * Imports given 'gradle-local' entities to the current intellij projcet.
   * 
   * @param entities  target 'gradle-local' entities to import
   */
  public void importEntities(@NotNull Collection<GradleEntity> entities) {
    for (GradleEntity entity : entities) {
      entity.invite(new GradleEntityVisitor() {
        @Override
        public void visit(@NotNull GradleProject project) {
        }

        @Override
        public void visit(@NotNull GradleModule module) {
          myModuleImporter.importModule(module, myProjectStructureHelper.getProject()); 
        }

        @Override
        public void visit(@NotNull GradleContentRoot contentRoot) {
          final Module intellijModule = myProjectStructureHelper.findIntellijModule(contentRoot.getOwnerModule());
          if (intellijModule == null) {
            GradleLog.LOG.warn(String.format(
              "Can't import gradle module content root. Reason: corresponding module is not registered at the current intellij project. "
              + "Content root: '%s'", contentRoot
            ));
            return;
          }
          myContentRootImporter.importContentRoots(contentRoot, intellijModule);
        }

        @Override
        public void visit(@NotNull GradleLibrary library) {
          myLibraryImporter.importLibrary(library, myProjectStructureHelper.getProject()); 
        }

        @Override
        public void visit(@NotNull GradleModuleDependency dependency) {
          final Module module = myProjectStructureHelper.findIntellijModule(dependency.getOwnerModule());
          assert module != null;
          myModuleDependencyImporter.importDependency(dependency, module); 
        }

        @Override
        public void visit(@NotNull GradleLibraryDependency dependency) {
          final Module module = myProjectStructureHelper.findIntellijModule(dependency.getOwnerModule());
          assert module != null;
          myModuleDependencyImporter.importDependency(dependency, module); 
        }
      });
    }
  }
  
  private static class EntitiesToImport {
    
    public final Set<GradleModule>     modules      = new HashSet<GradleModule>();
    public final Set<GradleLibrary>    libraries    = new HashSet<GradleLibrary>();
    public final Set<GradleDependency> dependencies = new HashSet<GradleDependency>();

    @NotNull
    public List<GradleEntity> getAll() {
      List<GradleEntity> result = new ArrayList<GradleEntity>();
      result.addAll(modules);
      result.addAll(libraries);
      result.addAll(dependencies);
      return result;
    }
  }
}
