package org.jetbrains.jps.incremental;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.jps.idea.OwnServiceLoader;

import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/17/11
 */
public class BuilderRegistry {
  private static class Holder {
    static final BuilderRegistry ourInstance = new BuilderRegistry();
  }
  private final Map<BuilderCategory, List<ModuleLevelBuilder>> myModuleLevelBuilders = new HashMap<BuilderCategory, List<ModuleLevelBuilder>>();
  private final List<ProjectLevelBuilder> myProjectLevelBuilders = new ArrayList<ProjectLevelBuilder>();

  public static BuilderRegistry getInstance() {
    return Holder.ourInstance;
  }

  private BuilderRegistry() {
    for (BuilderCategory category : BuilderCategory.values()) {
      myModuleLevelBuilders.put(category, new ArrayList<ModuleLevelBuilder>());
    }

    final OwnServiceLoader<BuilderService> loader = OwnServiceLoader.load(BuilderService.class);

    for (BuilderService service : loader) {
      myProjectLevelBuilders.addAll(service.createProjectLevelBuilders());
      final List<? extends ModuleLevelBuilder> moduleLevelBuilders = service.createModuleLevelBuilders();
      for (ModuleLevelBuilder builder : moduleLevelBuilders) {
        myModuleLevelBuilders.get(builder.getCategory()).add(builder);
      }
    }
  }

  public int getModuleLevelBuilderCount() {
    int count = 0;
    for (BuilderCategory category : BuilderCategory.values()) {
      count += getBuilders(category).size();
    }
    return count;
  }

  public List<BuildTask> getBeforeTasks(){
    return Collections.emptyList(); // todo
  }

  public List<BuildTask> getAfterTasks(){
    return Collections.emptyList(); // todo
  }

  public List<ModuleLevelBuilder> getBuilders(BuilderCategory category){
    return Collections.unmodifiableList(myModuleLevelBuilders.get(category)); // todo
  }

  public List<ModuleLevelBuilder> getModuleLevelBuilders() {
    return ContainerUtil.concat(myModuleLevelBuilders.values());
  }

  public List<ProjectLevelBuilder> getProjectLevelBuilders() {
    return myProjectLevelBuilders;
  }
}
