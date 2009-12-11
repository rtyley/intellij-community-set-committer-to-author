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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.source.parsing.xml.XmlBuilder;
import com.intellij.psi.impl.source.parsing.xml.XmlBuilderDriver;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.apache.maven.model.*;
import org.apache.maven.profiles.activation.*;
import org.apache.maven.project.InvalidProjectModelException;
import org.apache.maven.project.JBMavenProjectHelper;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.inheritance.DefaultModelInheritanceAssembler;
import org.apache.maven.project.injection.DefaultProfileInjector;
import org.apache.maven.project.validation.ModelValidationResult;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.context.DefaultContext;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.embedder.*;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.intellij.openapi.util.text.StringUtil.isEmptyOrSpaces;

public class MavenProjectReader {
  private static final String UNKNOWN = MavenId.UNKNOWN_VALUE;

  private static final String PROFILE_FROM_POM = "pom";
  private static final String PROFILE_FROM_PROFILES_XML = "profiles.xml";
  private static final String PROFILE_FROM_SETTINGS_XML = "settings.xml";

  private final Map<VirtualFile, RawModelReadResult> myRawModelsCache = new THashMap<VirtualFile, RawModelReadResult>();
  private Pair<List<Profile>, Collection<MavenProjectProblem>> mySettingsProfilesWithProblemsCache;

  public MavenProjectReaderResult readProject(MavenGeneralSettings generalSettings,
                                              VirtualFile file,
                                              Collection<String> explicitProfiles,
                                              MavenProjectReaderProjectLocator locator) {
    Pair<RawModelReadResult, Collection<Profile>> readResult =
      doReadProjectModel(generalSettings, file, explicitProfiles, new THashSet<VirtualFile>(), locator);

    File basedir = getBaseDir(file);
    Model model = expandProperties(readResult.first.model, basedir);
    alignModel(model, basedir);

    Collection<Profile> activeProfiles = readResult.second;

    MavenProject mavenProject = new MavenProject(model);
    mavenProject.setFile(new File(file.getPath()));
    mavenProject.setActiveProfiles(new ArrayList<Profile>(activeProfiles));
    JBMavenProjectHelper.setSourceRoots(mavenProject,
                                        Collections.singletonList(model.getBuild().getSourceDirectory()),
                                        Collections.singletonList(model.getBuild().getTestSourceDirectory()),
                                        Collections.singletonList(model.getBuild().getScriptSourceDirectory()));

    return new MavenProjectReaderResult(readResult.first.problems, Collections.EMPTY_SET,
                                        generalSettings.getEffectiveLocalRepository(), mavenProject);
  }

  private File getBaseDir(VirtualFile file) {
    return new File(file.getParent().getPath());
  }

  private Pair<RawModelReadResult, Collection<Profile>> doReadProjectModel(MavenGeneralSettings generalSettings,
                                                                           VirtualFile file,
                                                                           Collection<String> explicitProfiles,
                                                                           Set<VirtualFile> recursionGuard,
                                                                           MavenProjectReaderProjectLocator locator) {
    RawModelReadResult cachedModel = myRawModelsCache.get(file);
    if (cachedModel == null) {
      cachedModel = doReadProjectModel(file, false);
      myRawModelsCache.put(file, cachedModel);
    }

    // todo modifying cached model and problems here??????
    Model model = cachedModel.model;
    Collection<String> alwaysOnProfiles = cachedModel.alwaysOnProfiles;
    Collection<MavenProjectProblem> problems = cachedModel.problems;

    repairModelHeader(model);
    resolveInheritance(generalSettings, model, file, explicitProfiles, recursionGuard, locator, problems);
    addSettingsProfiles(generalSettings, model, alwaysOnProfiles, problems);

    Collection<Profile> activatedProfiles = applyProfiles(model, getBaseDir(file), explicitProfiles, alwaysOnProfiles);

    repairModelBody(model);

    return Pair.create(cachedModel, activatedProfiles);
  }

  private RawModelReadResult doReadProjectModel(VirtualFile file, boolean headerOnly) {
    Model result = new Model();
    LinkedHashSet<MavenProjectProblem> problems = createProblemsList();
    Set<String> alwaysOnProfiles = new THashSet<String>();

    Element xmlProject = readXml(file, problems, MavenProjectProblem.ProblemType.SYNTAX).getChild("project");
    if (xmlProject == null) {
      return new RawModelReadResult(result, problems, alwaysOnProfiles);
    }

    result.setModelVersion(findChildValueByPath(xmlProject, "modelVersion"));
    result.setGroupId(findChildValueByPath(xmlProject, "groupId"));
    result.setArtifactId(findChildValueByPath(xmlProject, "artifactId"));
    result.setVersion(findChildValueByPath(xmlProject, "version"));

    if (headerOnly) return new RawModelReadResult(result, problems, alwaysOnProfiles);

    result.setPackaging(findChildValueByPath(xmlProject, "packaging"));
    result.setName(findChildValueByPath(xmlProject, "name"));

    if (hasChildByPath(xmlProject, "parent")) {
      Parent parent = new Parent();

      String groupId = findChildValueByPath(xmlProject, "parent.groupId");
      String artifactId = findChildValueByPath(xmlProject, "parent.artifactId");
      String version = findChildValueByPath(xmlProject, "parent.version");

      parent.setGroupId(groupId);
      parent.setArtifactId(artifactId);
      parent.setVersion(version);
      parent.setRelativePath(findChildValueByPath(xmlProject, "parent.relativePath"));

      result.setParent(parent);
    }
    result.setBuild(new Build());
    readModelAndBuild(result, result.getBuild(), xmlProject);

    result.setProfiles(collectProfiles(file, xmlProject, problems, alwaysOnProfiles));
    return new RawModelReadResult(result, problems, alwaysOnProfiles);
  }

  private void readModelAndBuild(ModelBase mavenModelBase, BuildBase mavenBuildBase, Element xmlModel) {
    mavenModelBase.setModules(findChildrenValuesByPath(xmlModel, "modules", "module"));
    collectProperties(findChildByPath(xmlModel, "properties"), mavenModelBase);

    Element xmlBuild = findChildByPath(xmlModel, "build");
    if (xmlBuild == null) return;

    mavenBuildBase.setFinalName(findChildValueByPath(xmlBuild, "finalName"));
    mavenBuildBase.setDefaultGoal(findChildValueByPath(xmlBuild, "defaultGoal"));
    mavenBuildBase.setDirectory(findChildValueByPath(xmlBuild, "directory"));
    mavenBuildBase.setResources(collectResources(findChildrenByPath(xmlBuild, "resources", "resource")));
    mavenBuildBase.setTestResources(collectResources(findChildrenByPath(xmlBuild, "testResources", "testResource")));

    if (mavenBuildBase instanceof Build) {
      Build mavenBuild = (Build)mavenBuildBase;

      mavenBuild.setSourceDirectory(findChildValueByPath(xmlBuild, "sourceDirectory"));
      mavenBuild.setTestSourceDirectory(findChildValueByPath(xmlBuild, "testSourceDirectory"));
      mavenBuild.setScriptSourceDirectory(findChildValueByPath(xmlBuild, "scriptSourceDirectory"));
      mavenBuild.setOutputDirectory(findChildValueByPath(xmlBuild, "outputDirectory"));
      mavenBuild.setTestOutputDirectory(findChildValueByPath(xmlBuild, "testOutputDirectory"));
    }
  }

  private List<Resource> collectResources(List<Element> xmlResources) {
    List<Resource> result = new ArrayList<Resource>();
    for (Element each : xmlResources) {
      Resource r = new Resource();
      r.setDirectory(findChildValueByPath(each, "directory"));
      r.setFiltering("true".equals(findChildValueByPath(each, "filtering")));
      r.setTargetPath(findChildValueByPath(each, "targetPath"));
      r.setIncludes(findChildrenValuesByPath(each, "includes", "include"));
      r.setExcludes(findChildrenValuesByPath(each, "excludes", "exclude"));
      result.add(r);
    }
    return result;
  }

  private List<Profile> collectProfiles(VirtualFile projectFile,
                                        Element xmlProject,
                                        Collection<MavenProjectProblem> problems,
                                        Collection<String> alwaysOnProfiles) {
    List<Profile> result = new ArrayList<Profile>();
    collectProfiles(findChildrenByPath(xmlProject, "profiles", "profile"), result, PROFILE_FROM_POM);

    VirtualFile profilesFile = MavenUtil.findProfilesXmlFile(projectFile);
    if (profilesFile != null) {
      collectProfilesFromSettingsXmlOrProfilesXml(profilesFile,
                                                  "profilesXml",
                                                  false,
                                                  PROFILE_FROM_PROFILES_XML,
                                                  result,
                                                  alwaysOnProfiles,
                                                  problems);
    }

    return result;
  }

  private void addSettingsProfiles(MavenGeneralSettings generalSettings,
                                   Model model,
                                   Collection<String> alwaysOnProfiles,
                                   Collection<MavenProjectProblem> problems) {
    if (mySettingsProfilesWithProblemsCache == null) {

      List<Profile> settingsProfiles = new ArrayList<Profile>();
      Collection<MavenProjectProblem> settingsProblems = createProblemsList();

      for (VirtualFile each : generalSettings.getEffectiveSettingsFiles()) {
        collectProfilesFromSettingsXmlOrProfilesXml(each,
                                                    "settings",
                                                    true,
                                                    PROFILE_FROM_SETTINGS_XML,
                                                    settingsProfiles,
                                                    alwaysOnProfiles,
                                                    settingsProblems);
      }
      mySettingsProfilesWithProblemsCache = Pair.create(settingsProfiles, settingsProblems);
    }

    List<Profile> modelProfiles = model.getProfiles();
    for (Profile each : mySettingsProfilesWithProblemsCache.first) {
      addProfileIfDoesNotExist(each, modelProfiles);
    }
    problems.addAll(mySettingsProfilesWithProblemsCache.second);
  }

  private void collectProfilesFromSettingsXmlOrProfilesXml(VirtualFile profilesFile,
                                                           String rootElementName,
                                                           boolean isStrictRoot,
                                                           String profilesSource,
                                                           List<Profile> result,
                                                           Collection<String> alwaysOnProfiles,
                                                           Collection<MavenProjectProblem> problems) {
    Element fileElement = readXml(profilesFile, problems, MavenProjectProblem.ProblemType.SETTINGS_OR_PROFILES);

    Element rootElement = findChildByPath(fileElement, rootElementName);
    if (rootElement == null && !isStrictRoot) rootElement = fileElement;

    List<Element> xmlProfiles = findChildrenByPath(rootElement, "profiles", "profile");
    collectProfiles(xmlProfiles, result, profilesSource);

    List<Element> activeProfiles = findChildrenByPath(rootElement, "activeProfiles", "activeProfile");
    for (Element each : activeProfiles) {
      alwaysOnProfiles.add(each.getText());
    }
  }

  private void collectProfiles(List<Element> xmlProfiles, List<Profile> result, String source) {
    for (Element each : xmlProfiles) {
      String id = findChildValueByPath(each, "id");
      if (isEmptyOrSpaces(id)) continue;

      Profile profile = new Profile();
      profile.setId(id);
      profile.setSource(source);
      if (!addProfileIfDoesNotExist(profile, result)) continue;

      Element xmlActivation = findChildByPath(each, "activation");
      if (xmlActivation != null) {
        Activation activation = new Activation();
        activation.setActiveByDefault("true".equals(findChildValueByPath(xmlActivation, "activeByDefault")));

        Element xmlOS = findChildByPath(xmlActivation, "os");
        if (xmlOS != null) {
          ActivationOS activationOS = new ActivationOS();
          activationOS.setName(findChildValueByPath(xmlOS, "name"));
          activationOS.setFamily(findChildValueByPath(xmlOS, "family"));
          activationOS.setArch(findChildValueByPath(xmlOS, "arch"));
          activationOS.setVersion(findChildValueByPath(xmlOS, "version"));
          activation.setOs(activationOS);
        }

        activation.setJdk(findChildValueByPath(xmlActivation, "jdk"));

        Element xmlProperty = findChildByPath(xmlActivation, "property");
        if (xmlProperty != null) {
          ActivationProperty activationProperty = new ActivationProperty();
          activationProperty.setName(findChildValueByPath(xmlProperty, "name"));
          activationProperty.setValue(findChildValueByPath(xmlProperty, "value"));
          activation.setProperty(activationProperty);
        }

        Element xmlFile = findChildByPath(xmlActivation, "file");
        if (xmlFile != null) {
          ActivationFile activationFile = new ActivationFile();
          activationFile.setExists(findChildValueByPath(xmlFile, "exists"));
          activationFile.setMissing(findChildValueByPath(xmlFile, "missing"));
          activation.setFile(activationFile);
        }

        profile.setActivation(activation);
      }

      profile.setBuild(new BuildBase());
      readModelAndBuild(profile, profile.getBuild(), each);
    }
  }

  private boolean addProfileIfDoesNotExist(Profile profile, List<Profile> result) {
    for (Profile each : result) {
      if (Comparing.equal(each.getId(), profile.getId())) return false;
    }
    result.add(profile);
    return true;
  }

  private void collectProperties(Element xmlProperties, ModelBase mavenModelBase) {
    if (xmlProperties == null) return;

    Properties props = mavenModelBase.getProperties();

    for (Element each : (Iterable<? extends Element>)xmlProperties.getChildren()) {
      String name = each.getName();
      String value = each.getText();
      if (!props.containsKey(name) && !StringUtil.isEmptyOrSpaces(value)) {
        props.setProperty(name, value);
      }
    }
  }

  private List<Profile> applyProfiles(Model model, File basedir, Collection<String> explicitProfiles, Collection<String> alwaysOnProfiles) {
    List<Profile> activated = new ArrayList<Profile>();
    List<Profile> activeByDefault = new ArrayList<Profile>();

    List<Profile> rawProfiles = model.getProfiles();
    List<Profile> expandedProfiles = null;

    for (int i = 0; i < rawProfiles.size(); i++) {
      Profile eachRawProfile = rawProfiles.get(i);

      if (explicitProfiles.contains(eachRawProfile.getId())
        || alwaysOnProfiles.contains(eachRawProfile.getId())) {
        activated.add(eachRawProfile);
        continue;
      }

      Activation activation = eachRawProfile.getActivation();
      if (activation == null) continue;

      if (activation.isActiveByDefault()) {
        activeByDefault.add(eachRawProfile);
      }

      // expand only if necessary
      if (expandedProfiles == null) expandedProfiles = expandProperties(model, basedir).getProfiles();
      Profile eachExpandedProfile = expandedProfiles.get(i);

      for (ProfileActivator eachActivator : getProfileActivators()) {
        try {
          if (eachActivator.canDetermineActivation(eachExpandedProfile) && eachActivator.isActive(eachExpandedProfile)) {
            activated.add(eachRawProfile);
            break;
          }
        }
        catch (ProfileActivationException e) {
          MavenLog.LOG.warn(e);
        }
      }
    }

    List<Profile> activatedProfiles = activated.isEmpty() ? activeByDefault : activated;

    for (Profile each : activatedProfiles) {
      new DefaultProfileInjector().inject(each, model);
    }

    return activatedProfiles;
  }

  private ProfileActivator[] getProfileActivators() {
    SystemPropertyProfileActivator sysPropertyActivator = new SystemPropertyProfileActivator();
    DefaultContext context = new DefaultContext();
    context.put("SystemProperties", MavenEmbedderFactory.collectSystemProperties());
    try {
      sysPropertyActivator.contextualize(context);
    }
    catch (ContextException e) {
      MavenLog.LOG.error(e);
      return new ProfileActivator[0];
    }

    return new ProfileActivator[]{new FileProfileActivator(), sysPropertyActivator, new JdkPrefixProfileActivator(),
      new OperatingSystemProfileActivator()};
  }

  private void repairModelHeader(Model model) {
    if (isEmptyOrSpaces(model.getModelVersion())) model.setModelVersion("4.0.0");

    Parent parent = model.getParent();
    if (parent != null) {
      if (isEmptyOrSpaces(parent.getGroupId())) parent.setGroupId(UNKNOWN);
      if (isEmptyOrSpaces(parent.getArtifactId())) parent.setArtifactId(UNKNOWN);
      if (isEmptyOrSpaces(parent.getVersion())) parent.setVersion(UNKNOWN);
      if (isEmptyOrSpaces(parent.getRelativePath())) parent.setRelativePath("../pom.xml");
    }

    if (isEmptyOrSpaces(model.getGroupId())) {
      if (parent != null) {
        model.setGroupId(parent.getGroupId());
      }
      else {
        model.setGroupId(UNKNOWN);
      }
    }
    if (isEmptyOrSpaces(model.getArtifactId())) model.setArtifactId(UNKNOWN);
    if (isEmptyOrSpaces(model.getVersion())) {
      if (parent != null) {
        model.setVersion(parent.getVersion());
      }
      else {
        model.setVersion(UNKNOWN);
      }
    }

    if (isEmptyOrSpaces(model.getPackaging())) model.setPackaging("jar");
  }

  private void repairModelBody(Model model) {
    if (model.getBuild() == null) {
      model.setBuild(new Build());
    }
    Build build = model.getBuild();

    if (isEmptyOrSpaces(build.getFinalName())) {
      build.setFinalName("${project.artifactId}-${project.version}");
    }

    build.setSourceDirectory(isEmptyOrSpaces(build.getSourceDirectory()) ? "src/main/java" : build.getSourceDirectory());
    build.setTestSourceDirectory(isEmptyOrSpaces(build.getTestSourceDirectory()) ? "src/test/java" : build.getTestSourceDirectory());
    build
      .setScriptSourceDirectory(isEmptyOrSpaces(build.getScriptSourceDirectory()) ? "src/main/scripts" : build.getScriptSourceDirectory());

    build.setResources(repairResources(build.getResources(), "src/main/resources"));
    build.setTestResources(repairResources(build.getTestResources(), "src/test/resources"));

    build.setDirectory(isEmptyOrSpaces(build.getDirectory()) ? "target" : build.getDirectory());
    build
      .setOutputDirectory(isEmptyOrSpaces(build.getOutputDirectory()) ? "${project.build.directory}/classes" : build.getOutputDirectory());
    build.setTestOutputDirectory(
      isEmptyOrSpaces(build.getTestOutputDirectory()) ? "${project.build.directory}/test-classes" : build.getTestOutputDirectory());
  }

  private List<Resource> repairResources(List<Resource> resources, String defaultDir) {
    List<Resource> result = new ArrayList<Resource>();
    if (resources.isEmpty()) {
      result.add(createResource(defaultDir));
      return result;
    }

    for (Resource each : resources) {
      if (isEmptyOrSpaces(each.getDirectory())) continue;
      each.setDirectory(each.getDirectory());
      result.add(each);
    }
    return result;
  }

  private Resource createResource(String directory) {
    Resource result = new Resource();
    result.setDirectory(directory);
    return result;
  }

  private void resolveInheritance(final MavenGeneralSettings generalSettings,
                                  final Model model,
                                  final VirtualFile file,
                                  final Collection<String> explicitProfiles,
                                  final Set<VirtualFile> recursionGuard,
                                  final MavenProjectReaderProjectLocator locator,
                                  Collection<MavenProjectProblem> problems) {
    if (recursionGuard.contains(file)) {
      problems.add(createProblem(file, ProjectBundle.message("maven.project.problem.recursiveInheritance"),
                                 MavenProjectProblem.ProblemType.PARENT));
      return;
    }
    recursionGuard.add(file);

    try {
      Parent parent = model.getParent();
      final MavenParentDesc[] parentDesc = new MavenParentDesc[1];
      if (parent != null) {
        MavenId parentId = new MavenId(parent.getGroupId(), parent.getArtifactId(), parent.getVersion());
        if (parentId.equals(model.getGroupId(), model.getArtifactId(), model.getVersion())) {
          problems.add(createProblem(file, ProjectBundle.message("maven.project.problem.selfInheritance"),
                                     MavenProjectProblem.ProblemType.PARENT));
          return;
        }
        parentDesc[0] = new MavenParentDesc(parentId, parent.getRelativePath());
      }

      Pair<VirtualFile, RawModelReadResult> parentModelWithProblems =
        new MavenParentProjectFileProcessor<Pair<VirtualFile, RawModelReadResult>>() {
          @Nullable
          protected VirtualFile findManagedFile(@NotNull MavenId id) {
            return locator.findProjectFile(id);
          }

          @Override
          @Nullable
          protected Pair<VirtualFile, RawModelReadResult> processRelativeParent(VirtualFile parentFile) {
            Model parentModel = doReadProjectModel(parentFile, true).model;
            MavenId parentId = parentDesc[0].getParentId();
            if (!parentId.equals(new MavenId(parentModel))) return null;

            return super.processRelativeParent(parentFile);
          }

          @Override
          protected Pair<VirtualFile, RawModelReadResult> processSuperParent(VirtualFile parentFile) {
            return null; // do not process superPom
          }

          @Override
          protected Pair<VirtualFile, RawModelReadResult> doProcessParent(VirtualFile parentFile) {
            RawModelReadResult result = doReadProjectModel(generalSettings, parentFile, explicitProfiles, recursionGuard, locator).first;
            return Pair.create(parentFile, result);
          }
        }.process(generalSettings, file, parentDesc[0]);

      if (parentModelWithProblems == null) return; // no parent or parent not found;

      Model parentModel = parentModelWithProblems.second.model;
      if (!parentModelWithProblems.second.problems.isEmpty()) {
        problems.add(createProblem(parentModelWithProblems.first,
                                   ProjectBundle.message("maven.project.problem.parentHasProblems", new MavenId(parentModel)),
                                   MavenProjectProblem.ProblemType.PARENT));
      }

      new DefaultModelInheritanceAssembler().assembleModelInheritance(model, parentModel);

      List<Profile> profiles = model.getProfiles();
      for (Profile each : parentModel.getProfiles()) {
        addProfileIfDoesNotExist(each, profiles);
      }
    }
    finally {
      recursionGuard.remove(file);
    }
  }

  private Model expandProperties(Model model, File basedir) {
    return MavenEmbedderWrapper.interpolate(model, basedir);
  }

  private void alignModel(Model model, File basedir) {
    MavenEmbedderWrapper.alignModel(model, basedir);
  }

  private MavenProjectProblem createStructureProblem(VirtualFile file, String description) {
    return createProblem(file, description, MavenProjectProblem.ProblemType.STRUCTURE);
  }

  private MavenProjectProblem createSyntaxProblem(VirtualFile file, MavenProjectProblem.ProblemType type) {
    return createProblem(file, ProjectBundle.message("maven.project.problem.syntaxError", file.getName()), type);
  }

  private MavenProjectProblem createProblem(VirtualFile file, String description, MavenProjectProblem.ProblemType type) {
    return new MavenProjectProblem(file, description, type);
  }

  private LinkedHashSet<MavenProjectProblem> createProblemsList() {
    return createProblemsList(Collections.<MavenProjectProblem>emptySet());
  }

  private LinkedHashSet<MavenProjectProblem> createProblemsList(Collection<MavenProjectProblem> copyThis) {
    return new LinkedHashSet<MavenProjectProblem>(copyThis);
  }

  public MavenProjectReaderResult resolveProject(MavenGeneralSettings generalSettings,
                                                 MavenEmbedderWrapper embedder,
                                                 VirtualFile file,
                                                 Collection<String> explicitProfiles,
                                                 MavenProjectReaderProjectLocator locator) throws MavenProcessCanceledException {
    MavenProject mavenProject = null;
    Collection<MavenProjectProblem> problems = createProblemsList();
    Set<MavenId> unresolvedArtifactsIds = new THashSet<MavenId>();

    try {
      Pair<MavenProject, Set<MavenId>> result = doResolveProject(embedder, file, explicitProfiles, problems);
      mavenProject = result.first;
      unresolvedArtifactsIds = result.second;
    }
    catch (MavenProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      String message = e.getMessage();

      if (e instanceof RuntimeException && e.getCause() != null) {
        message = e.getCause().getMessage();
      }

      if (message != null) {
        problems.add(createStructureProblem(file, message));
      }
      MavenLog.LOG.info(e);
      MavenLog.printInTests(e); // print exception since we need to know if something wrong with our logic
    }

    if (mavenProject == null) {
      if (problems.isEmpty()) {
        problems.add(createSyntaxProblem(file, MavenProjectProblem.ProblemType.SYNTAX));
      }
      mavenProject = readProject(generalSettings, file, explicitProfiles, locator).nativeMavenProject;
    }

    return new MavenProjectReaderResult(problems, unresolvedArtifactsIds, embedder.getLocalRepositoryFile(), mavenProject);
  }

  private Pair<MavenProject, Set<MavenId>> doResolveProject(MavenEmbedderWrapper embedder,
                                                            VirtualFile file,
                                                            Collection<String> profiles,
                                                            Collection<MavenProjectProblem> problems) throws MavenProcessCanceledException {
    MavenExecutionResult result = embedder.resolveProject(file, profiles);
    validate(file, result, problems);
    return Pair.create(result.getMavenProject(), result.getUnresolvedArtifactIds());
  }

  private boolean validate(VirtualFile file, MavenExecutionResult r, Collection<MavenProjectProblem> problems) {
    for (Exception each : r.getExceptions()) {
      MavenLog.LOG.info(each);

      if (each instanceof InvalidProjectModelException) {
        ModelValidationResult modelValidationResult = ((InvalidProjectModelException)each).getValidationResult();
        if (modelValidationResult != null) {
          for (Object eachValidationProblem : modelValidationResult.getMessages()) {
            problems.add(createStructureProblem(file, (String)eachValidationProblem));
          }
        }
        else {
          problems.add(createStructureProblem(file, each.getCause().getMessage()));
        }
      }
      else if (each instanceof ProjectBuildingException) {
        String causeMessage = each.getCause() != null ? each.getCause().getMessage() : each.getMessage();
        problems.add(createStructureProblem(file, causeMessage));
      }
      else {
        problems.add(createStructureProblem(file, each.getMessage()));
      }
    }

    return problems.isEmpty();
  }

  public MavenProjectReaderResult generateSources(MavenEmbedderWrapper embedder,
                                                  MavenImportingSettings importingSettings,
                                                  VirtualFile file,
                                                  Collection<String> profiles,
                                                  MavenConsole console) throws MavenProcessCanceledException {
    try {
      MavenExecutionResult result = embedder.execute(file, profiles, Arrays.asList(importingSettings.getUpdateFoldersOnImportPhase()));

      if (result.hasExceptions()) {
        MavenConsoleHelper.printExecutionExceptions(console, result);
      }

      Collection<MavenProjectProblem> problems = createProblemsList();
      if (!validate(file, result, problems)) return null;

      MavenProject project = result.getMavenProject();
      if (project == null) return null;

      return new MavenProjectReaderResult(problems, result.getUnresolvedArtifactIds(), embedder.getLocalRepositoryFile(), project);
    }
    catch (MavenProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      MavenConsoleHelper.printException(console, e);
      MavenLog.LOG.warn(e);
      return null;
    }
  }

  private Element readXml(final VirtualFile file,
                          final Collection<MavenProjectProblem> problems,
                          final MavenProjectProblem.ProblemType type) {
    final LinkedList<Element> stack = new LinkedList<Element>();
    final Element root = new Element("root");

    String text = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      public String compute() {
        if (!file.isValid()) return null;
        try {
          return VfsUtil.loadText(file);
        }
        catch (IOException e) {
          MavenLog.LOG.warn("Cannot read the pom file: " + e);
          problems.add(createProblem(file, e.getMessage(), type));
        }
        return null;
      }
    });
    if (text == null) return root;

    XmlBuilderDriver driver = new XmlBuilderDriver(text);
    XmlBuilder builder = new XmlBuilder() {
      public void doctype(@Nullable CharSequence publicId, @Nullable CharSequence systemId, int startOffset, int endOffset) {
      }

      public ProcessingOrder startTag(CharSequence localName, String namespace, int startoffset, int endoffset, int headerEndOffset) {
        String name = localName.toString();
        if (StringUtil.isEmptyOrSpaces(name)) return ProcessingOrder.TAGS;

        Element newElement = new Element(name);

        Element parent = stack.isEmpty() ? root : stack.getLast();
        parent.addContent(newElement);
        stack.addLast(newElement);

        return ProcessingOrder.TAGS_AND_TEXTS;
      }

      public void endTag(CharSequence localName, String namespace, int startoffset, int endoffset) {
        String name = localName.toString();
        if (isEmptyOrSpaces(name)) return;

        int index = -1;
        for (int i = stack.size() - 1; i >= 0; i--) {
          if (stack.get(i).getName().equals(name)) {
            index = i;
            break;
          }
        }
        if (index == -1) return;
        while (stack.size() > index) {
          stack.removeLast();
        }
      }

      public void textElement(CharSequence text, CharSequence physical, int startoffset, int endoffset) {
        stack.getLast().addContent(JDOMUtil.legalizeText(text.toString()));
      }

      public void attribute(CharSequence name, CharSequence value, int startoffset, int endoffset) {
      }

      public void entityRef(CharSequence ref, int startOffset, int endOffset) {
      }

      public void error(String message, int startOffset, int endOffset) {
        problems.add(createSyntaxProblem(file, type));
      }
    };

    driver.build(builder);
    return root;
  }

  private Element findChildByPath(Element element, String path) {
    List<String> parts = StringUtil.split(path, ".");
    Element current = element;
    for (String each : parts) {
      current = current.getChild(each);
      if (current == null) break;
    }
    return current;
  }

  private boolean hasChildByPath(Element element, String path) {
    return findChildValueByPath(element, path) != null;
  }

  private String findChildValueByPath(Element element, String path) {
    Element child = findChildByPath(element, path);
    return child == null ? null : child.getText();
  }

  private List<Element> findChildrenByPath(Element element, String path, String childrenName) {
    return collectChildren(findChildByPath(element, path), childrenName);
  }

  private List<Element> collectChildren(Element container, String childrenName) {
    if (container == null) return Collections.emptyList();

    List<Element> result = new ArrayList<Element>();
    for (Element each : (Iterable<? extends Element>)container.getChildren(childrenName)) {
      result.add(each);
    }
    return result;
  }

  private List<String> findChildrenValuesByPath(Element element, String path, String childrenName) {
    List<String> result = new ArrayList<String>();
    for (Element each : findChildrenByPath(element, path, childrenName)) {
      String value = each.getValue();
      if (!StringUtil.isEmptyOrSpaces(value)) {
        result.add(value);
      }
    }
    return result;
  }

  private static class RawModelReadResult {
    public Model model;
    public Collection<MavenProjectProblem> problems;
    public Set<String> alwaysOnProfiles;

    private RawModelReadResult(Model model, Collection<MavenProjectProblem> problems, Set<String> alwaysOnProfiles) {
      this.model = model;
      this.problems = problems;
      this.alwaysOnProfiles = alwaysOnProfiles;
    }
  }
}
