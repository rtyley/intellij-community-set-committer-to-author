package org.jetbrains.plugins.gradle.sync;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.config.GradleTextAttributes;
import org.jetbrains.plugins.gradle.diff.*;
import org.jetbrains.plugins.gradle.model.GradleEntityOwner;
import org.jetbrains.plugins.gradle.model.gradle.GradleModule;
import org.jetbrains.plugins.gradle.model.id.*;
import org.jetbrains.plugins.gradle.model.intellij.ModuleAwareContentRoot;
import org.jetbrains.plugins.gradle.ui.GradleProjectStructureNodeComparator;
import org.jetbrains.plugins.gradle.ui.GradleProjectStructureNode;
import org.jetbrains.plugins.gradle.ui.GradleProjectStructureNodeDescriptor;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleProjectStructureContext;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import java.util.*;

/**
 * Model for the target project structure tree used by the gradle integration.
 * <p/>
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 1/30/12 4:20 PM
 */
public class GradleProjectStructureTreeModel extends DefaultTreeModel {

  /**
   * <pre>
   *     ...
   *      |_module     &lt;- module's name is a key
   *          |_...
   *          |_dependencies   &lt;- dependencies holder node is a value
   *                  |_dependency1
   *                  |_dependency2
   * </pre>
   */
  private final Map<String, GradleProjectStructureNode<GradleSyntheticId>> myModuleDependencies
    = new HashMap<String, GradleProjectStructureNode<GradleSyntheticId>>();
  private final Map<String, GradleProjectStructureNode<GradleModuleId>> myModules
    = new HashMap<String, GradleProjectStructureNode<GradleModuleId>>();

  private final TreeNode[]                myNodeHolder                = new TreeNode[1];
  private final int[]                     myIndexHolder               = new int[1];
  private final NodeListener              myNodeListener              = new NodeListener();
  private final ObsoleteChangesDispatcher myObsoleteChangesDispatcher = new ObsoleteChangesDispatcher();
  private final NewChangesDispatcher      myNewChangesDispatcher      = new NewChangesDispatcher();

  @NotNull private final Project                                   myProject;
  @NotNull private final PlatformFacade                            myPlatformFacade;
  @NotNull private final GradleProjectStructureHelper              myProjectStructureHelper;
  @NotNull private final Comparator<GradleProjectStructureNode<?>> myNodeComparator;

  public GradleProjectStructureTreeModel(@NotNull Project project, @NotNull GradleProjectStructureContext context) {
    super(null);
    myProject = project;
    myPlatformFacade = context.getPlatformFacade();
    myProjectStructureHelper = context.getProjectStructureHelper();
    myNodeComparator = new GradleProjectStructureNodeComparator(context);
    rebuild();
  }

  public void rebuild() {
    myModuleDependencies.clear();
    myModules.clear();

    GradleProjectId projectId = GradleEntityIdMapper.mapEntityToId(getProject());
    GradleProjectStructureNode<GradleProjectId> root = buildNode(projectId, getProject().getName());
    final Collection<Module> modules = myPlatformFacade.getModules(getProject());
    final List<GradleProjectStructureNode<?>> dependencies = new ArrayList<GradleProjectStructureNode<?>>();
    RootPolicy<Object> visitor = new RootPolicy<Object>() {
      @Override
      public Object visitModuleOrderEntry(ModuleOrderEntry moduleOrderEntry, Object value) {
        GradleModuleDependencyId id = GradleEntityIdMapper.mapEntityToId(moduleOrderEntry);
        dependencies.add(buildNode(id, moduleOrderEntry.getModuleName()));
        return value;
      }

      @Override
      public Object visitLibraryOrderEntry(LibraryOrderEntry libraryOrderEntry, Object value) {
        if (libraryOrderEntry.getLibraryName() == null) {
          return value;
        }
        GradleLibraryDependencyId id = GradleEntityIdMapper.mapEntityToId(libraryOrderEntry);
        dependencies.add(buildNode(id, id.getDependencyName()));
        return value;
      }
    };
    for (Module module : modules) {
      dependencies.clear();
      final GradleModuleId moduleId = GradleEntityIdMapper.mapEntityToId(module);
      final GradleProjectStructureNode<GradleModuleId> moduleNode = buildNode(moduleId, moduleId.getModuleName());
      myModules.put(module.getName(), moduleNode); // Assuming that module names are unique.
      root.add(moduleNode);
      
      // Content roots
      final Collection<ModuleAwareContentRoot> contentRoots = myPlatformFacade.getContentRoots(module);
      for (ContentEntry entry : contentRoots) {
        GradleContentRootId contentRootId = GradleEntityIdMapper.mapEntityToId(entry);
        moduleNode.add(buildContentRootNode(contentRootId, contentRoots.size() <= 1));
      }
      
      // Dependencies
      for (OrderEntry orderEntry : myPlatformFacade.getOrderEntries(module)) {
        orderEntry.accept(visitor, null);
      }
      if (dependencies.isEmpty()) {
        continue;
      }
      GradleProjectStructureNode<GradleSyntheticId> dependenciesNode = getDependenciesNode(moduleId);
      for (GradleProjectStructureNode<?> dependency : dependencies) {
        dependenciesNode.add(dependency);
      }
    }

    setRoot(root);
  }

  @NotNull
  private static String getContentRootNodeName(@NotNull GradleContentRootId id, boolean singleRoot) {
    final String name = GradleBundle.message("gradle.import.structure.tree.node.content.root");
    if (singleRoot) {
      return name;
    }
    final String path = id.getRootPath();
    final int i = path.lastIndexOf('/');
    if (i < 0) {
      return name;
    }
    return name + ":" + path.substring(i + 1);
  }
  
  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  private GradleProjectStructureNode<GradleContentRootId> buildContentRootNode(@NotNull GradleContentRootId id) {
    final boolean singleRoot;
    if (id.getOwner() == GradleEntityOwner.GRADLE) {
      final GradleModule module = myProjectStructureHelper.findGradleModule(id.getModuleName());
      singleRoot = module == null || module.getContentRoots().size() <= 1;
    }
    else {
      final Module module = myProjectStructureHelper.findIntellijModule(id.getModuleName());
      singleRoot = module == null || myPlatformFacade.getContentRoots(module).size() <= 1;
    }
    return buildContentRootNode(id, singleRoot);
  }
  
  @NotNull
  private GradleProjectStructureNode<GradleContentRootId> buildContentRootNode(@NotNull GradleContentRootId id, boolean singleRoot) {
    GradleProjectStructureNode<GradleContentRootId> result = buildNode(id, getContentRootNodeName(id, singleRoot));
    result.getDescriptor().setToolTip(id.getRootPath());
    return result;
  }

  @NotNull
  private <T extends GradleEntityId> GradleProjectStructureNode<T> buildNode(@NotNull T id, @NotNull String name) {
    final GradleProjectStructureNode<T> result = new GradleProjectStructureNode<T>(GradleUtil.buildDescriptor(id, name), myNodeComparator);
    result.addListener(myNodeListener);
    return result;
  }

  @NotNull
  private GradleProjectStructureNode<GradleSyntheticId> getDependenciesNode(@NotNull GradleModuleId id) {
    final GradleProjectStructureNode<GradleSyntheticId> cached = myModuleDependencies.get(id.getModuleName());
    if (cached != null) {
      return cached;
    }
    GradleProjectStructureNode<GradleModuleId> moduleNode = getModuleNode(id);
    GradleProjectStructureNode<GradleSyntheticId> result
      = new GradleProjectStructureNode<GradleSyntheticId>(GradleConstants.DEPENDENCIES_NODE_DESCRIPTOR, myNodeComparator);
    result.addListener(myNodeListener);
    moduleNode.add(result);
    myModuleDependencies.put(id.getModuleName(), result);
    
    return result;
  }
  
  @NotNull
  private GradleProjectStructureNode<GradleModuleId> getModuleNode(@NotNull GradleModuleId id) {
    GradleProjectStructureNode<GradleModuleId> moduleNode = myModules.get(id.getModuleName());
    if (moduleNode == null) {
      moduleNode = buildNode(id, id.getModuleName());
      myModules.put(id.getModuleName(), moduleNode);
      ((GradleProjectStructureNode<?>)root).add(moduleNode);
    }
    return moduleNode;
  }

  /**
   * Notifies current model that particular module roots change just has happened.
   * <p/>
   * The model is expected to update itself if necessary.
   */
  public void onModuleRootsChange() {
    for (GradleProjectStructureNode<GradleSyntheticId> node : myModuleDependencies.values()) {
      node.sortChildren();
    }
  }
  
  /**
   * Asks current model to update its state in accordance with the given changes.
   * 
   * @param changes  collections that contains all changes between the current gradle and intellij project structures
   */
  public void processCurrentChanges(@NotNull Collection<GradleProjectStructureChange> changes) {
    for (GradleProjectStructureChange change : changes) {
      change.invite(myNewChangesDispatcher);
    }
  }

  /**
   * Asks current model to process given changes assuming that they are obsolete.
   * <p/>
   * Example:
   * <pre>
   * <ol>
   *   <li>There is a particular intellij-local library (change from the gradle project structure);</li>
   *   <li>Corresponding node is shown at the current UI;</li>
   *   <li>The library is removed, i.e. corresponding change has become obsolete;</li>
   *   <li>This method is notified within the obsolete change and is expected to remove the corresponding node;</li>
   * </ol>
   * </pre>
   */
  public void processObsoleteChanges(Collection<GradleProjectStructureChange> changes) {
    for (GradleProjectStructureChange change : changes) {
      change.invite(myObsoleteChangesDispatcher);
    }
  }

  private void processNewProjectRenameChange(@NotNull GradleProjectRenameChange change) {
    // TODO den implement
  }

  private void processNewLanguageLevelChange(@NotNull GradleLanguageLevelChange change) {
    // TODO den implement
  }

  private void processNewMismatchedLibraryPathChange(@NotNull GradleMismatchedLibraryPathChange change) {
    for (GradleProjectStructureNode<GradleSyntheticId> holder : myModuleDependencies.values()) {
      for (GradleProjectStructureNode<GradleLibraryDependencyId> dependencyNode : holder.getChildren(GradleLibraryDependencyId.class)) {
        final GradleLibraryDependencyId id = dependencyNode.getDescriptor().getElement();
        if (change.getLibraryName().equals(id.getDependencyName())) {
          dependencyNode.addConflictChange(change);
          break;
        }
      }
    }
  }

  private void processNewLibraryDependencyPresenceChange(@NotNull GradleLibraryDependencyPresenceChange change) {
    processNewDependencyPresenceChange(change);
  }

  private void processNewModuleDependencyPresenceChange(@NotNull GradleModuleDependencyPresenceChange change) {
    processNewDependencyPresenceChange(change);
  }
  
  private <I extends GradleAbstractDependencyId> void processNewDependencyPresenceChange(@NotNull GradleEntityPresenceChange<I> change) {
    I id = change.getGradleEntity();
    TextAttributesKey attributes = GradleTextAttributes.GRADLE_LOCAL_CHANGE;
    if (id == null) {
      id = change.getIntellijEntity();
      attributes = GradleTextAttributes.INTELLIJ_LOCAL_CHANGE;
    }
    assert id != null;
    final GradleProjectStructureNode<GradleSyntheticId> dependenciesNode = getDependenciesNode(id.getOwnerModuleId());
    for (GradleProjectStructureNode<? extends GradleAbstractDependencyId> node : dependenciesNode.getChildren(id.getClass())) {
      if (id.equals(node.getDescriptor().getElement())) {
        node.setAttributes(attributes);
        return;
      }
    }
    GradleProjectStructureNode<I> newNode = buildNode(id, id.getDependencyName());
    dependenciesNode.add(newNode);
    newNode.setAttributes(attributes);
  }

  private void processNewModulePresenceChange(@NotNull GradleModulePresenceChange change) {
    final GradleModuleId id;
    final TextAttributesKey key;
    if (change.getGradleEntity() == null) {
      id = change.getIntellijEntity();
      key = GradleTextAttributes.INTELLIJ_LOCAL_CHANGE;
    }
    else {
      id = change.getGradleEntity();
      key = GradleTextAttributes.GRADLE_LOCAL_CHANGE;
    }
    assert id != null;
    final GradleProjectStructureNode<GradleModuleId> moduleNode = getModuleNode(id);
    moduleNode.setAttributes(key);
  }

  private void processNewContentRootPresenceChange(@NotNull GradleContentRootPresenceChange change) {
    GradleContentRootId id = change.getGradleEntity();
    TextAttributesKey key = GradleTextAttributes.GRADLE_LOCAL_CHANGE;
    if (id == null) {
      id = change.getIntellijEntity();
      key = GradleTextAttributes.INTELLIJ_LOCAL_CHANGE;
    }
    assert id != null;
    final GradleProjectStructureNode<GradleModuleId> moduleNode = getModuleNode(id.getModuleId());
    for (GradleProjectStructureNode<GradleContentRootId> contentRoot : moduleNode.getChildren(GradleContentRootId.class)) {
      if (id.equals(contentRoot.getDescriptor().getElement())) {
        contentRoot.setAttributes(key);
        return;
      }
    }
    GradleProjectStructureNode<GradleContentRootId> contentRootNode = buildContentRootNode(id);
    moduleNode.add(contentRootNode);
    contentRootNode.setAttributes(key);
  }
  
  private void processObsoleteProjectRenameChange(@NotNull GradleProjectRenameChange change) {
    // TODO den implement
  }
  
  private void processObsoleteLanguageLevelChange(@NotNull GradleLanguageLevelChange change) {
    // TODO den implement
  }
  
  private void processObsoleteMismatchedLibraryPathChange(@NotNull GradleMismatchedLibraryPathChange change) {
    for (GradleProjectStructureNode<GradleSyntheticId> holder : myModuleDependencies.values()) {
      for (GradleProjectStructureNode<GradleLibraryDependencyId> node : holder.getChildren(GradleLibraryDependencyId.class)) {
        final GradleLibraryDependencyId id = node.getDescriptor().getElement();
        if (id.getDependencyName().equals(change.getLibraryName())) {
          node.removeConflictChange(change);
          break;
        }
      }
    }
  }

  private void processObsoleteLibraryDependencyPresenceChange(@NotNull GradleLibraryDependencyPresenceChange change) {
    // We need to remove the corresponding node then.
    GradleLibraryDependencyId id = change.getGradleEntity();
    boolean removeNode;
    if (id == null) {
      id = change.getIntellijEntity();
      assert id != null;
      removeNode = !myProjectStructureHelper.isIntellijLibraryDependencyExist(id);
    }
    else {
      removeNode = !myProjectStructureHelper.isGradleLibraryDependencyExist(id);
    }
    processObsoleteDependencyPresenceChange(id, removeNode);
  }

  private void processObsoleteModuleDependencyPresenceChange(@NotNull GradleModuleDependencyPresenceChange change) {
    GradleModuleDependencyId id = change.getGradleEntity();
    boolean removeNode;
    if (id == null) {
      id = change.getIntellijEntity();
      assert id != null;
      removeNode = !myProjectStructureHelper.isIntellijModuleDependencyExist(id);
    }
    else {
      removeNode = !myProjectStructureHelper.isGradleModuleDependencyExist(id);
    }
    processObsoleteDependencyPresenceChange(id, removeNode);
  }

  private void processObsoleteDependencyPresenceChange(@NotNull GradleAbstractDependencyId id, boolean removeNode) {
    final GradleProjectStructureNode<GradleSyntheticId> holder = myModuleDependencies.get(id.getOwnerModuleName());
    if (holder == null) {
      return;
    }

    // There are two possible cases why 'local library dependency' change is obsolete:
    //   1. Corresponding dependency has been added at the counterparty;
    //   2. The 'local dependency' has been removed;
    // We should distinguish between those situations because we need to mark the node as 'synced' at one case and
    // completely removed at another one.

    for (GradleProjectStructureNode<? extends GradleAbstractDependencyId> node : holder.getChildren(id.getClass())) {
      GradleProjectStructureNodeDescriptor<? extends GradleAbstractDependencyId> descriptor = node.getDescriptor();
      if (!id.equals(descriptor.getElement())) {
        continue;
      }
      if (removeNode) {
        holder.remove(node);
      }
      else {
        descriptor.setAttributes(GradleTextAttributes.NO_CHANGE);
        holder.correctChildPositionIfNecessary(node);
      }
      return;
    }
  }

  private void processObsoleteModulePresenceChange(@NotNull GradleModulePresenceChange change) {
    GradleModuleId id = change.getGradleEntity();
    boolean removeNode;
    if (id == null) {
      id = change.getIntellijEntity();
      assert id != null;
      removeNode = myProjectStructureHelper.findIntellijModule(id.getModuleName()) == null;
    }
    else {
      removeNode = myProjectStructureHelper.findGradleModule(id.getModuleName()) == null;
    }
    

    // There are two possible cases why 'module presence' change is obsolete:
    //   1. Corresponding module has been added at the counterparty;
    //   2. The 'local module' has been removed;
    // We should distinguish between those situations because we need to mark the node as 'synced' at one case and
    // completely removed at another one.
    
    final GradleProjectStructureNode<GradleModuleId> moduleNode = myModules.get(id.getModuleName());
    if (moduleNode == null) {
      return;
    }
    if (removeNode) {
      moduleNode.removeFromParent();
    }
    else {
      moduleNode.setAttributes(GradleTextAttributes.NO_CHANGE);
    }
  }

  private void processObsoleteContentRootPresenceChange(@NotNull GradleContentRootPresenceChange change) {
    GradleContentRootId id = change.getGradleEntity();
    final boolean removeNode;
    if (id == null) {
      id = change.getIntellijEntity();
      assert id != null;
      removeNode = myProjectStructureHelper.findIntellijContentRoot(id) == null;
    }
    else {
      removeNode = myProjectStructureHelper.findGradleContentRoot(id) == null;
    }
    final GradleProjectStructureNode<GradleModuleId> moduleNode = myModules.get(id.getModuleName());
    if (moduleNode == null) {
      return;
    }
    for (GradleProjectStructureNode<GradleContentRootId> contentRootNode : moduleNode.getChildren(GradleContentRootId.class)) {
      if (!id.equals(contentRootNode.getDescriptor().getElement())) {
        continue;
      }
      if (removeNode) {
        contentRootNode.removeFromParent();
      }
      else {
        contentRootNode.setAttributes(GradleTextAttributes.NO_CHANGE);
      }
      return;
    }
  }
  
  private class NodeListener implements GradleProjectStructureNode.Listener {
    
    @Override
    public void onNodeAdded(@NotNull GradleProjectStructureNode<?> node, int index) {
      myIndexHolder[0] = index;
      nodesWereInserted(node.getParent(), myIndexHolder);
    }

    @Override
    public void onNodeRemoved(@NotNull GradleProjectStructureNode<?> parent,
                              @NotNull GradleProjectStructureNode<?> removedChild,
                              int removedChildIndex)
    {
      myIndexHolder[0] = removedChildIndex;
      myNodeHolder[0] = removedChild;
      nodesWereRemoved(parent, myIndexHolder, myNodeHolder); 
    }

    @Override
    public void onNodeChanged(@NotNull GradleProjectStructureNode<?> node) {
      nodeChanged(node);
    }

    @Override
    public void onNodeChildrenChanged(@NotNull GradleProjectStructureNode<?> parent, int[] childIndices) {
      nodesChanged(parent, childIndices);
    }
  }
  
  private class NewChangesDispatcher implements GradleProjectStructureChangeVisitor {
    @Override public void visit(@NotNull GradleProjectRenameChange change) { processNewProjectRenameChange(change); }
    @Override public void visit(@NotNull GradleLanguageLevelChange change) { processNewLanguageLevelChange(change); }
    @Override public void visit(@NotNull GradleModulePresenceChange change) { processNewModulePresenceChange(change); }
    @Override public void visit(@NotNull GradleContentRootPresenceChange change) { processNewContentRootPresenceChange(change); }

      @Override public void visit(@NotNull GradleLibraryDependencyPresenceChange change) { processNewLibraryDependencyPresenceChange(change); }
    @Override public void visit(@NotNull GradleModuleDependencyPresenceChange change) { processNewModuleDependencyPresenceChange(change); }
    @Override public void visit(@NotNull GradleMismatchedLibraryPathChange change) { processNewMismatchedLibraryPathChange(change); }
  }
  
  private class ObsoleteChangesDispatcher implements GradleProjectStructureChangeVisitor {
    @Override public void visit(@NotNull GradleProjectRenameChange change) { processObsoleteProjectRenameChange(change); }
    @Override public void visit(@NotNull GradleLanguageLevelChange change) { processObsoleteLanguageLevelChange(change); }
    @Override public void visit(@NotNull GradleModulePresenceChange change) { processObsoleteModulePresenceChange(change); }
    @Override public void visit(@NotNull GradleContentRootPresenceChange change) { processObsoleteContentRootPresenceChange(change); }
    @Override public void visit(@NotNull GradleLibraryDependencyPresenceChange change) {
      processObsoleteLibraryDependencyPresenceChange(change); 
    }
    @Override public void visit(@NotNull GradleModuleDependencyPresenceChange change) {
      processObsoleteModuleDependencyPresenceChange(change); 
    }
    @Override public void visit(@NotNull GradleMismatchedLibraryPathChange change) { processObsoleteMismatchedLibraryPathChange(change); }
  }
}
