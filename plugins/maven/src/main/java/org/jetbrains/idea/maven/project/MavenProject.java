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
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.*;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.embedder.MavenConsole;
import org.jetbrains.idea.maven.embedder.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.importing.MavenExtraArtifactType;
import org.jetbrains.idea.maven.importing.MavenImporter;
import org.jetbrains.idea.maven.utils.*;

import java.io.*;
import java.util.*;

public class MavenProject {
  private final VirtualFile myFile;
  private volatile State myState = new State();

  public static MavenProject read(DataInputStream in) throws IOException {
    String path = in.readUTF();
    int length = in.readInt();
    byte[] bytes = new byte[length];
    in.readFully(bytes);

    // should read full byte content first!!!

    VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
    if (file == null) return null;

    ByteArrayInputStream bs = new ByteArrayInputStream(bytes);
    ObjectInputStream os = new ObjectInputStream(bs);
    try {
      try {
        MavenProject result = new MavenProject(file);
        result.myState = (State)os.readObject();
        return result;
      }
      catch (ClassNotFoundException e) {
        IOException ioException = new IOException();
        ioException.initCause(e);
        throw ioException;
      }
    }
    finally {
      os.close();
      bs.close();
    }
  }

  public void write(DataOutputStream out) throws IOException {
    out.writeUTF(getPath());

    ByteArrayOutputStream bs = new ByteArrayOutputStream();
    ObjectOutputStream os = new ObjectOutputStream(bs);
    try {
      os.writeObject(myState);

      byte[] bytes = bs.toByteArray();
      out.writeInt(bytes.length);
      out.write(bytes);
    }
    finally {
      os.close();
      bs.close();
    }
  }

  public MavenProject(VirtualFile file) {
    myFile = file;
  }

  private MavenProjectChanges set(MavenProjectReaderResult readerResult,
                                  boolean updateLastReadStamp,
                                  boolean resetArtifacts,
                                  boolean resetProfiles) {
    State newState = myState.clone();

    if (updateLastReadStamp) newState.myLastReadStamp++;

    org.apache.maven.project.MavenProject nativeMavenProject = readerResult.nativeMavenProject;

    newState.myReadingProblems = readerResult.readingProblems;
    newState.myLocalRepository = readerResult.localRepository;

    newState.myActivatedProfilesIds = readerResult.activatedProfiles;

    Model model = nativeMavenProject.getModel();

    newState.myMavenId = new MavenId(model.getGroupId(),
                                     model.getArtifactId(),
                                     model.getVersion());

    Parent parent = model.getParent();
    newState.myParentId = parent != null
                          ? new MavenId(parent.getGroupId(), parent.getArtifactId(), parent.getVersion())
                          : null;

    newState.myPackaging = model.getPackaging();
    newState.myName = model.getName();

    Build build = model.getBuild();

    newState.myFinalName = build.getFinalName();
    newState.myDefaultGoal = build.getDefaultGoal();

    newState.myBuildDirectory = build.getDirectory();
    newState.myOutputDirectory = build.getOutputDirectory();
    newState.myTestOutputDirectory = build.getTestOutputDirectory();

    doSetFolders(newState, readerResult);

    newState.myFilters = build.getFilters() == null ? Collections.EMPTY_LIST : build.getFilters();
    newState.myProperties = model.getProperties() != null ? model.getProperties() : new Properties();

    doSetResolvedAttributes(newState, readerResult, resetArtifacts);

    newState.myModulesPathsAndNames = collectModulePathsAndNames(model, getDirectory());
    Collection<String> newProfiles = collectProfilesIds(model.getProfiles());
    if (resetProfiles || newState.myProfilesIds == null) {
      newState.myProfilesIds = newProfiles;
    }
    else {
      Set<String> mergedProfiles = new THashSet<String>(newState.myProfilesIds);
      mergedProfiles.addAll(newProfiles);
      newState.myProfilesIds = new ArrayList<String>(mergedProfiles);
    }

    newState.myStrippedMavenModel = MavenUtil.cloneObject(model);
    MavenUtil.stripDown(newState.myStrippedMavenModel);

    return setState(newState);
  }

  private MavenProjectChanges setState(State newState) {
    MavenProjectChanges changes = myState.getChanges(newState);
    myState = newState;
    return changes;
  }

  private static void doSetResolvedAttributes(State state, MavenProjectReaderResult readerResult, boolean reset) {
    org.apache.maven.project.MavenProject nativeMavenProject = readerResult.nativeMavenProject;
    Model model = nativeMavenProject.getModel();

    Set<MavenId> newUnresolvedArtifacts = new THashSet<MavenId>();
    LinkedHashSet<MavenRemoteRepository> newRepositories = new LinkedHashSet<MavenRemoteRepository>();
    LinkedHashSet<MavenArtifact> newDependencies = new LinkedHashSet<MavenArtifact>();
    LinkedHashSet<MavenPlugin> newPlugins = new LinkedHashSet<MavenPlugin>();
    LinkedHashSet<MavenArtifact> newExtensions = new LinkedHashSet<MavenArtifact>();

    if (!reset) {
      if (state.myUnresolvedArtifactIds != null) newUnresolvedArtifacts.addAll(state.myUnresolvedArtifactIds);
      if (state.myRemoteRepositories != null) newRepositories.addAll(state.myRemoteRepositories);
      if (state.myDependencies != null) newDependencies.addAll(state.myDependencies);
      if (state.myPlugins != null) newPlugins.addAll(state.myPlugins);
      if (state.myExtensions != null) newExtensions.addAll(state.myExtensions);
    }

    newUnresolvedArtifacts.addAll(readerResult.unresolvedArtifactIds);
    newRepositories.addAll(convertRepositories(model.getRepositories()));
    newDependencies.addAll(convertArtifacts(nativeMavenProject.getArtifacts(), state.myLocalRepository));
    newPlugins.addAll(collectPlugins(readerResult.settings, model));
    newExtensions.addAll(convertArtifacts(nativeMavenProject.getExtensionArtifacts(), state.myLocalRepository));

    state.myUnresolvedArtifactIds = newUnresolvedArtifacts;
    state.myRemoteRepositories = new ArrayList<MavenRemoteRepository>(newRepositories);
    state.myDependencies = new ArrayList<MavenArtifact>(newDependencies);
    state.myPlugins = new ArrayList<MavenPlugin>(newPlugins);
    state.myExtensions = new ArrayList<MavenArtifact>(newExtensions);
  }

  private MavenProjectChanges setFolders(MavenProjectReaderResult readerResult) {
    State newState = myState.clone();
    doSetFolders(newState, readerResult);
    return setState(newState);
  }

  private static void doSetFolders(State newState, MavenProjectReaderResult readerResult) {
    org.apache.maven.project.MavenProject nativeMavenProject = readerResult.nativeMavenProject;

    newState.mySources = new ArrayList<String>(nativeMavenProject.getCompileSourceRoots());
    newState.myTestSources = new ArrayList<String>(nativeMavenProject.getTestCompileSourceRoots());

    newState.myResources = convertResources(nativeMavenProject.getResources());
    newState.myTestResources = convertResources(nativeMavenProject.getTestResources());
  }

  private static List<MavenResource> convertResources(List<Resource> resources) {
    if (resources == null) return new ArrayList<MavenResource>();

    List<MavenResource> result = new ArrayList<MavenResource>(resources.size());
    for (Resource each : resources) {
      result.add(new MavenResource(each));
    }
    return result;
  }

  private static List<MavenRemoteRepository> convertRepositories(List<Repository> repositories) {
    if (repositories == null) return new ArrayList<MavenRemoteRepository>();

    List<MavenRemoteRepository> result = new ArrayList<MavenRemoteRepository>(repositories.size());
    for (Repository each : repositories) {
      result.add(new MavenRemoteRepository(each));
    }
    return result;
  }

  private static List<MavenArtifact> convertArtifacts(Collection<Artifact> artifacts, File localRepository) {
    if (artifacts == null) return new ArrayList<MavenArtifact>();

    List<MavenArtifact> result = new ArrayList<MavenArtifact>(artifacts.size());
    for (Artifact each : artifacts) {
      result.add(new MavenArtifact(each, localRepository));
    }
    return result;
  }

  private static List<MavenPlugin> collectPlugins(MavenGeneralSettings settings, Model mavenModel) {
    List<MavenPlugin> result = new ArrayList<MavenPlugin>();
    Set<String> pluginKeys = new THashSet<String>();
    Build build = mavenModel.getBuild();
    doCollectPlugins(settings, build, false, result, pluginKeys);
    if (build != null) doCollectPlugins(settings, build.getPluginManagement(), true, result, pluginKeys);
    return result;
  }

  private static void doCollectPlugins(MavenGeneralSettings settings,
                                       PluginContainer container,
                                       boolean management,
                                       List<MavenPlugin> result,
                                       Set<String> pluginKeys) {
    if (container == null) return;

    List<Plugin> plugins = container.getPlugins();
    if (plugins == null) return;

    for (Plugin each : plugins) {
      String key = each.getGroupId() + ":" + each.getArtifactId();
      if (management && (!isDefaultPlugin(settings, each) || pluginKeys.contains(key))) continue;
      result.add(new MavenPlugin(each, management));
      pluginKeys.add(key);
    }
  }

  private static boolean isDefaultPlugin(MavenGeneralSettings settings, Plugin plugin) {
    return settings.isDefaultPlugin(plugin.getGroupId(), plugin.getArtifactId());
  }

  private static Map<String, String> collectModulePathsAndNames(Model mavenModel, String baseDir) {
    String basePath = baseDir + "/";
    Map<String, String> result = new LinkedHashMap<String, String>();
    for (Map.Entry<String, String> each : collectModulesRelativePathsAndNames(mavenModel).entrySet()) {
      result.put(new Path(basePath + each.getKey()).getPath(), each.getValue());
    }
    return result;
  }

  private static Map<String, String> collectModulesRelativePathsAndNames(Model mavenModel) {
    LinkedHashMap<String, String> result = new LinkedHashMap<String, String>();
    addModulesToList(mavenModel.getModules(), result);
    return result;
  }

  private static void addModulesToList(List moduleNames, LinkedHashMap<String, String> result) {
    for (String name : (List<String>)moduleNames) {
      if (name.trim().length() == 0) continue;

      String originalName = name;
      // module name can be relative and contain either / of \\ separators

      name = FileUtil.toSystemIndependentName(name);
      if (!name.endsWith("/")) name += "/";
      name += MavenConstants.POM_XML;

      result.put(name, originalName);
    }
  }

  private static Collection<String> collectProfilesIds(Collection<Profile> profiles) {
    if (profiles == null) return Collections.emptyList();

    Set<String> result = new THashSet<String>(profiles.size());
    for (Profile each : profiles) {
      String id = each.getId();
      if (id != null) result.add(id);
    }
    return result;
  }

  public long getLastReadStamp() {
    return myState.myLastReadStamp;
  }

  public VirtualFile getFile() {
    return myFile;
  }

  @NotNull
  public String getPath() {
    return myFile.getPath();
  }

  public String getDirectory() {
    return myFile.getParent().getPath();
  }

  public VirtualFile getDirectoryFile() {
    return myFile.getParent();
  }

  public VirtualFile getProfilesXmlFile() {
    return MavenUtil.findProfilesXmlFile(myFile);
  }

  public File getProfilesXmlIoFile() {
    return MavenUtil.getProfilesXmlIoFile(myFile);
  }

  public boolean hasReadingProblems() {
    return !myState.myReadingProblems.isEmpty();
  }

  public Collection<String> getActiveProfilesIds() {
    return myState.myActivatedProfilesIds;
  }

  public String getName() {
    return myState.myName;
  }

  public String getDisplayName() {
    State state = myState;
    if (StringUtil.isEmptyOrSpaces(state.myName)) return state.myMavenId.getArtifactId();
    return state.myName;
  }

  public Model getMavenModel() {
    return myState.myStrippedMavenModel;
  }

  public MavenId getMavenId() {
    return myState.myMavenId;
  }

  public MavenId getParentId() {
    return myState.myParentId;
  }

  public String getPackaging() {
    return myState.myPackaging;
  }

  public String getFinalName() {
    return myState.myFinalName;
  }

  public String getDefaultGoal() {
    return myState.myDefaultGoal;
  }

  public String getBuildDirectory() {
    return myState.myBuildDirectory;
  }

  public String getGeneratedSourcesDirectory() {
    return getBuildDirectory() + "/generated-sources";
  }

  public String getOutputDirectory() {
    return myState.myOutputDirectory;
  }

  public String getTestOutputDirectory() {
    return myState.myTestOutputDirectory;
  }

  public List<String> getSources() {
    return myState.mySources;
  }

  public List<String> getTestSources() {
    return myState.myTestSources;
  }

  public List<MavenResource> getResources() {
    return myState.myResources;
  }

  public List<MavenResource> getTestResources() {
    return myState.myTestResources;
  }

  public List<String> getFilters() {
    return myState.myFilters;
  }

  public MavenProjectChanges read(MavenGeneralSettings generalSettings,
                                  Collection<String> profiles,
                                  MavenProjectReader reader,
                                  MavenProjectReaderProjectLocator locator) {
    return set(reader.readProject(generalSettings, myFile, profiles, locator), true, false, true);
  }

  public Pair<MavenProjectChanges, org.apache.maven.project.MavenProject> resolve(MavenGeneralSettings generalSettings,
                                                                                  MavenEmbedderWrapper embedder,
                                                                                  MavenProjectReader reader,
                                                                                  MavenProjectReaderProjectLocator locator)
    throws MavenProcessCanceledException {
    MavenProjectReaderResult result = reader.resolveProject(generalSettings,
                                                            embedder,
                                                            getFile(),
                                                            getActiveProfilesIds(),
                                                            locator);
    MavenProjectChanges changes = set(result, false, result.readingProblems.isEmpty(), false);

    for (MavenImporter eachImporter : getSuitableImporters()) {
      eachImporter.resolve(this, result.nativeMavenProject, embedder);
    }
    return Pair.create(changes, result.nativeMavenProject);
  }

  public Pair<Boolean, MavenProjectChanges> resolveFolders(MavenEmbedderWrapper embedder,
                                                           MavenGeneralSettings generalSettings,
                                                           MavenImportingSettings importingSettings,
                                                           MavenProjectReader reader,
                                                           MavenConsole console) throws MavenProcessCanceledException {
    MavenProjectReaderResult result = reader.generateSources(embedder,
                                                             generalSettings,
                                                             importingSettings,
                                                             getFile(),
                                                             getActiveProfilesIds(),
                                                             console);
    if (result == null || !result.readingProblems.isEmpty()) return Pair.create(false, MavenProjectChanges.NONE);
    MavenProjectChanges changes = setFolders(result);
    return Pair.create(true, changes);
  }

  public boolean isAggregator() {
    return "pom".equals(getPackaging()) || !getModulePaths().isEmpty();
  }

  public List<MavenProjectProblem> getProblems() {
    State state = myState;
    if (state.myProblemsCache == null) {
      synchronized (state) {
        if (state.myProblemsCache == null) {
          state.myProblemsCache = collectProblems(myFile, state);
        }
      }
    }
    return state.myProblemsCache;
  }

  private static List<MavenProjectProblem> collectProblems(VirtualFile file, State state) {
    List<MavenProjectProblem> result = new ArrayList<MavenProjectProblem>();

    validateParent(file, state, result);
    result.addAll(state.myReadingProblems);

    for (Map.Entry<String, String> each : state.myModulesPathsAndNames.entrySet()) {
      if (LocalFileSystem.getInstance().findFileByPath(each.getKey()) == null) {
        result.add(createDependencyProblem(file, ProjectBundle.message("maven.project.problem.moduleNotFound", each.getValue())));
      }
    }

    validateDependencies(file, state, result);
    validateExtensions(file, state, result);
    validatePlugins(file, state, result);

    return result;
  }

  private static void validateParent(VirtualFile file, State state, List<MavenProjectProblem> result) {
    if (!isParentResolved(state)) {
      result.add(createDependencyProblem(file, ProjectBundle.message("maven.project.problem.parentNotFound", state.myParentId)));
    }
  }

  private static void validateDependencies(VirtualFile file, State state, List<MavenProjectProblem> result) {
    for (MavenArtifact each : getUnresolvedDependencies(state)) {
      result.add(createDependencyProblem(file, ProjectBundle.message("maven.project.problem.unresolvedDependency",
                                                                     each.getDisplayStringWithType())));
    }
  }

  private static void validateExtensions(VirtualFile file, State state, List<MavenProjectProblem> result) {
    for (MavenArtifact each : getUnresolvedExtensions(state)) {
      result.add(createDependencyProblem(file, ProjectBundle.message("maven.project.problem.unresolvedExtension",
                                                                     each.getDisplayStringSimple())));
    }
  }

  private static void validatePlugins(VirtualFile file, State state, List<MavenProjectProblem> result) {
    for (MavenPlugin each : getUnresolvedPlugins(state)) {
      result.add(createDependencyProblem(file, ProjectBundle.message("maven.project.problem.unresolvedPlugin", each)));
    }
  }

  private static MavenProjectProblem createDependencyProblem(VirtualFile file, String description) {
    return new MavenProjectProblem(file, description, MavenProjectProblem.ProblemType.DEPENDENCY);
  }

  private static boolean isParentResolved(State state) {
    return !state.myUnresolvedArtifactIds.contains(state.myParentId);
  }

  private static List<MavenArtifact> getUnresolvedDependencies(State state) {
    if (state.myUnresolvedDependenciesCache == null) {
      synchronized (state) {
        if (state.myUnresolvedDependenciesCache == null) {
          List<MavenArtifact> result = new ArrayList<MavenArtifact>();
          for (MavenArtifact each : state.myDependencies) {
            if (!each.isResolved()) result.add(each);
          }
          state.myUnresolvedDependenciesCache = result;
        }
      }
    }
    return state.myUnresolvedDependenciesCache;
  }

  private static List<MavenArtifact> getUnresolvedExtensions(State state) {
    if (state.myUnresolvedExtensionsCache == null) {
      synchronized (state) {
        if (state.myUnresolvedExtensionsCache == null) {
          List<MavenArtifact> result = new ArrayList<MavenArtifact>();
          for (MavenArtifact each : state.myExtensions) {
            // Collect only extensions that were attempted to be resolved.
            // It is because embedder does not even try to resolve extensions that
            // are not necessary.
            if (state.myUnresolvedArtifactIds.contains(each.getMavenId())
                && !pomFileExists(state.myLocalRepository, each)) {
              result.add(each);
            }
          }
          state.myUnresolvedExtensionsCache = result;
        }
      }
    }
    return state.myUnresolvedExtensionsCache;
  }

  private static boolean pomFileExists(File localRepository, MavenArtifact artifact) {
    return MavenArtifactUtil.hasArtifactFile(localRepository, artifact.getMavenId(), "pom");
  }

  private static List<MavenPlugin> getUnresolvedPlugins(State state) {
    if (state.myUnresolvedPluginsCache == null) {
      synchronized (state) {
        if (state.myUnresolvedPluginsCache == null) {
          List<MavenPlugin> result = new ArrayList<MavenPlugin>();
          for (MavenPlugin each : getDeclaredPlugins(state)) {
            if (!MavenArtifactUtil.hasArtifactFile(state.myLocalRepository, each.getMavenId())) {
              result.add(each);
            }
          }
          state.myUnresolvedPluginsCache = result;
        }
      }
    }
    return state.myUnresolvedPluginsCache;
  }

  public List<VirtualFile> getExistingModuleFiles() {
    LocalFileSystem fs = LocalFileSystem.getInstance();

    List<VirtualFile> result = new ArrayList<VirtualFile>();
    Set<String> pathsInStack = getModulePaths();
    for (String each : pathsInStack) {
      VirtualFile f = fs.findFileByPath(each);
      if (f != null) result.add(f);
    }
    return result;
  }

  public Set<String> getModulePaths() {
    return getModulesPathsAndNames().keySet();
  }

  public Map<String, String> getModulesPathsAndNames() {
    return myState.myModulesPathsAndNames;
  }

  public Collection<String> getProfilesIds() {
    return myState.myProfilesIds;
  }

  public List<MavenArtifact> getDependencies() {
    return myState.myDependencies;
  }

  public List<MavenArtifactNode> getDependenciesNodes() {
    return buildDependenciesNodes(myState.myDependencies);
  }

  private static List<MavenArtifactNode> buildDependenciesNodes(List<MavenArtifact> artifacts) {
    List<MavenArtifactNode> result = new ArrayList<MavenArtifactNode>();
    for (MavenArtifact each : artifacts) {
      List<MavenArtifactNode> currentScope = result;
      for (String eachKey : each.getTrail()) {
        MavenArtifactNode node = findNodeFor(eachKey, currentScope, true);
        if (node == null) {
          node = findNodeFor(eachKey, result, false);
          if (node == null) {
            MavenArtifact artifact = findArtifactFor(eachKey, artifacts);
            if (artifact == null) break;
            node = new MavenArtifactNode(artifact, new ArrayList<MavenArtifactNode>());
          }
          currentScope.add(node);
        }
        currentScope = node.getDependencies();
      }
    }
    return result;
  }

  private static MavenArtifactNode findNodeFor(String artifactKey, Collection<MavenArtifactNode> nodes, boolean strict) {
    for (MavenArtifactNode each : nodes) {
      if (each.getArtifact().getDisplayStringWithTypeAndClassifier().equals(artifactKey)) {
        return each;
      }
    }
    if (strict) return null;

    for (MavenArtifactNode each : nodes) {
      MavenArtifactNode result = findNodeFor(artifactKey, each.getDependencies(), strict);
      if (result != null) return result;
    }
    return null;
  }

  private static MavenArtifact findArtifactFor(String artifactKey, Collection<MavenArtifact> artifacts) {
    for (MavenArtifact each : artifacts) {
      if (each.getDisplayStringWithTypeAndClassifier().equals(artifactKey)) {
        return each;
      }
    }
    return null;
  }

  public boolean isSupportedDependency(MavenArtifact artifact) {
    String t = artifact.getType();
    if (MavenConstants.TYPE_JAR.equalsIgnoreCase(t)
        || MavenConstants.TYPE_TEST_JAR.equalsIgnoreCase(t)
        || "ejb".equalsIgnoreCase(t)
        || "ejb-client".equalsIgnoreCase(t)) {
      return true;
    }

    for (MavenImporter each : getSuitableImporters()) {
      if (each.isSupportedDependency(artifact)) return true;
    }
    return false;
  }

  public void addDependency(MavenArtifact dependency) {
    addDependency(dependency, false);
  }

  public void addDependency(MavenArtifact dependency, boolean toBegin) {
    State state = myState;
    List<MavenArtifact> dependenciesCopy = new ArrayList<MavenArtifact>(state.myDependencies.size() + 1);

    if (toBegin) {
      dependenciesCopy.add(dependency);
      dependenciesCopy.addAll(state.myDependencies);
    }
    else {
      dependenciesCopy.addAll(state.myDependencies);
      dependenciesCopy.add(dependency);
    }

    state.myDependencies = dependenciesCopy;
  }

  public List<MavenArtifact> findDependencies(MavenProject depProject) {
    return findDependencies(depProject.getMavenId());
  }

  public List<MavenArtifact> findDependencies(MavenId id) {
    List<MavenArtifact> result = new SmartList<MavenArtifact>();
    for (MavenArtifact each : getDependencies()) {
      if (each.getMavenId().equals(id)) result.add(each);
    }
    return result;
  }

  public List<MavenArtifact> findDependencies(String groupId, String artifactId) {
    List<MavenArtifact> result = new SmartList<MavenArtifact>();
    for (MavenArtifact each : getDependencies()) {
      if (each.getMavenId().equals(groupId, artifactId)) result.add(each);
    }
    return result;
  }

  public boolean hasUnresolvedArtifacts() {
    State state = myState;
    return !isParentResolved(state)
           || !getUnresolvedDependencies(state).isEmpty()
           || !getUnresolvedExtensions(state).isEmpty();
  }

  public boolean hasUnresolvedPlugins() {
    return !getUnresolvedPlugins(myState).isEmpty();
  }

  public List<MavenPlugin> getPlugins() {
    return myState.myPlugins;
  }

  public List<MavenPlugin> getDeclaredPlugins() {
    return getDeclaredPlugins(myState);
  }

  private static List<MavenPlugin> getDeclaredPlugins(State state) {
    return ContainerUtil.findAll(state.myPlugins, new Condition<MavenPlugin>() {
      public boolean value(MavenPlugin mavenPlugin) {
        return !mavenPlugin.isDefault();
      }
    });
  }

  @Nullable
  public Element getPluginConfiguration(String groupId, String artifactId) {
    return doGetPluginOrGoalConfiguration(groupId, artifactId, null);
  }

  @Nullable
  public Element getPluginGoalConfiguration(String groupId, String artifactId, String goal) {
    return doGetPluginOrGoalConfiguration(groupId, artifactId, goal);
  }

  @Nullable
  private Element doGetPluginOrGoalConfiguration(String groupId, String artifactId, @Nullable String goalOrNull) {
    MavenPlugin plugin = findPlugin(groupId, artifactId);
    if (plugin == null) return null;

    Element configElement = null;
    if (goalOrNull == null) {
      configElement = plugin.getConfigurationElement();
    }
    else {
      for (MavenPlugin.Execution each : plugin.getExecutions()) {
        if (each.getGoals().contains(goalOrNull)) {
          configElement = each.getConfigurationElement();
        }
      }
    }
    return configElement;
  }

  @Nullable
  public MavenPlugin findPlugin(String groupId, String artifactId) {
    for (MavenPlugin each : getPlugins()) {
      if (each.getMavenId().equals(groupId, artifactId)) return each;
    }
    return null;
  }

  @Nullable
  public String getSourceLevel() {
    return getCompilerLevel("source");
  }

  @Nullable
  public String getTargetLevel() {
    return getCompilerLevel("target");
  }

  private String getCompilerLevel(String level) {
    String result = MavenJDOMUtil.findChildValueByPath(getPluginConfiguration("org.apache.maven.plugins", "maven-compiler-plugin"), level);
    return normalizeCompilerLevel(result);
  }

  private static class CompilerLevelTable {
    public static Map<String, String> table = new THashMap<String, String>();

    static {
      table.put("1.1", "1.1");
      table.put("1.2", "1.2");
      table.put("1.3", "1.3");
      table.put("1.4", "1.4");
      table.put("1.5", "1.5");
      table.put("5", "1.5");
      table.put("1.6", "1.6");
      table.put("1.7", "1.7");
      table.put("7", "1.7");
    }
  }

  public static String normalizeCompilerLevel(String level) {
    if (level == null) return null;
    return CompilerLevelTable.table.get(level);
  }

  public Properties getProperties() {
    return myState.myProperties;
  }

  public File getLocalRepository() {
    return myState.myLocalRepository;
  }

  public List<MavenRemoteRepository> getRemoteRepositories() {
    return myState.myRemoteRepositories;
  }

  public List<MavenImporter> getSuitableImporters() {
    return MavenImporter.getSuitableImporters(this);
  }

  public Pair<String, String> getClassifierAndExtension(MavenArtifact artifact, MavenExtraArtifactType type) {
    for (MavenImporter each : getSuitableImporters()) {
      Pair<String, String> result = each.getExtraArtifactClassifierAndExtension(artifact, type);
      if (result != null) return result;
    }
    return Pair.create(type.getDefaultClassifier(), type.getDefaultExtension());
  }

  @Override
  public String toString() {
    return getMavenId().toString();
  }

  private static class State implements Cloneable, Serializable {
    long myLastReadStamp = 0;

    MavenId myMavenId;
    MavenId myParentId;
    String myPackaging;
    String myName;

    String myFinalName;
    String myDefaultGoal;

    String myBuildDirectory;
    String myOutputDirectory;
    String myTestOutputDirectory;

    List<String> mySources;
    List<String> myTestSources;
    List<MavenResource> myResources;
    List<MavenResource> myTestResources;

    List<String> myFilters;
    Properties myProperties;
    List<MavenPlugin> myPlugins;
    List<MavenArtifact> myExtensions;

    List<MavenArtifact> myDependencies;

    Map<String, String> myModulesPathsAndNames;

    Collection<String> myProfilesIds;

    Model myStrippedMavenModel;
    List<MavenRemoteRepository> myRemoteRepositories;

    Collection<String> myActivatedProfilesIds;
    Collection<MavenProjectProblem> myReadingProblems;
    Set<MavenId> myUnresolvedArtifactIds;
    File myLocalRepository;

    volatile List<MavenProjectProblem> myProblemsCache;
    volatile List<MavenArtifact> myUnresolvedDependenciesCache;
    volatile List<MavenPlugin> myUnresolvedPluginsCache;
    volatile List<MavenArtifact> myUnresolvedExtensionsCache;

    @Override
    public State clone() {
      try {
        State result = (State)super.clone();
        result.myProblemsCache = null;
        result.myUnresolvedDependenciesCache = null;
        result.myUnresolvedPluginsCache = null;
        result.myUnresolvedExtensionsCache = null;
        return result;
      }
      catch (CloneNotSupportedException e) {
        throw new RuntimeException(e);
      }
    }

    public MavenProjectChanges getChanges(State other) {
      if (myLastReadStamp == 0) return MavenProjectChanges.ALL;

      MavenProjectChanges result = new MavenProjectChanges();

      result.packaging |= !Comparing.equal(myPackaging, other.myPackaging);

      result.output |= !Comparing.equal(myFinalName, other.myFinalName);
      result.output |= !Comparing.equal(myBuildDirectory, other.myBuildDirectory);
      result.output |= !Comparing.equal(myOutputDirectory, other.myOutputDirectory);
      result.output |= !Comparing.equal(myTestOutputDirectory, other.myTestOutputDirectory);

      result.sources |= !Comparing.equal(mySources, other.mySources);
      result.sources |= !Comparing.equal(myTestSources, other.myTestSources);
      result.sources |= !Comparing.equal(myResources, other.myResources);
      result.sources |= !Comparing.equal(myTestResources, other.myTestResources);

      boolean repositoryChanged = !Comparing.equal(myLocalRepository, other.myLocalRepository);

      result.dependencies |= repositoryChanged;
      result.dependencies |= !Comparing.equal(myDependencies, other.myDependencies);

      result.plugins |= repositoryChanged;
      result.plugins |= !Comparing.equal(myPlugins, other.myPlugins);

      return result;
    }
  }
}
