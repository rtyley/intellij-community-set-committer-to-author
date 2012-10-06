/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.server;

import com.intellij.util.SystemProperties;
import gnu.trove.THashSet;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.manager.WagonManager;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.resolver.ResolutionListener;
import org.apache.maven.cli.MavenCli;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.model.Activation;
import org.apache.maven.model.Model;
import org.apache.maven.model.Profile;
import org.apache.maven.model.profile.DefaultProfileInjector;
import org.apache.maven.profiles.activation.*;
import org.apache.maven.project.*;
import org.apache.maven.project.interpolation.AbstractStringBasedModelInterpolator;
import org.apache.maven.project.interpolation.ModelInterpolationException;
import org.apache.maven.project.path.DefaultPathTranslator;
import org.apache.maven.project.path.PathTranslator;
import org.apache.maven.project.validation.ModelValidationResult;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.SettingsUtils;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuilder;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.building.SettingsBuildingRequest;
import org.apache.maven.shared.dependency.tree.DependencyNode;
import org.apache.maven.shared.dependency.tree.DependencyTreeResolutionListener;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.context.DefaultContext;
import org.codehaus.plexus.logging.BaseLoggerManager;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.InitializationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.*;
import org.jetbrains.idea.maven.server.embedder.*;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

public class Maven3ServerEmbedderImpl extends MavenRemoteObject implements MavenServerEmbedder {
  @NotNull private final DefaultPlexusContainer myContainer;
  @NotNull private final Settings myMavenSettings;

  private final ArtifactRepository myLocalRepository;
  private final Maven3ServerConsoleLogger myConsoleWrapper;

  public Maven3ServerEmbedderImpl(MavenServerSettings settings) throws RemoteException {
    File mavenHome = settings.getMavenHome();
    if (mavenHome != null) {
      System.setProperty("maven.home", mavenHome.getPath());
    }

    myConsoleWrapper = new Maven3ServerConsoleLogger();
    myConsoleWrapper.setThreshold(settings.getLoggingLevel());

    ClassWorld classWorld = new ClassWorld("plexus.core", Thread.currentThread().getContextClassLoader());
    MavenCli cli = new MavenCli(classWorld) {
      @Override
      protected void customizeContainer(PlexusContainer container) {
        ((DefaultPlexusContainer)container).setLoggerManager(new BaseLoggerManager() {
          @Override
          protected Logger createLogger(String s) {
            return myConsoleWrapper;
          }
        });
      }
    };

    Class cliRequestClass;
    try {
      cliRequestClass = MavenCli.class.getClassLoader().loadClass("org.apache.maven.cli.MavenCli$CliRequest");
    }
    catch (ClassNotFoundException e) {
      throw new RuntimeException("Class \"org.apache.maven.cli.MavenCli$CliRequest\" not found");
    }

    Object cliRequest;
    try {
      String[] commandLineOptions = new String[settings.getUserProperties().size()];
      int idx = 0;
      for (Map.Entry<Object, Object> each : settings.getUserProperties().entrySet()) {
        commandLineOptions[idx++] = "-D" + each.getKey() + "=" + each.getValue();
      }

      Constructor constructor = cliRequestClass.getDeclaredConstructor(String[].class, ClassWorld.class);
      constructor.setAccessible(true);
      cliRequest = constructor.newInstance(commandLineOptions, classWorld);

      for (String each : new String[]{"initialize", "cli", "properties", "container"}) {
        Method m = MavenCli.class.getDeclaredMethod(each, cliRequestClass);
        m.setAccessible(true);
        m.invoke(cli, cliRequest);
      }
    }
    catch (InstantiationException e) {
      throw new RuntimeException(e);
    }
    catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
    catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
    catch (InvocationTargetException e) {
      throw new RuntimeException(e);
    }

    // reset threshold
    myContainer = FieldAccessor.get(MavenCli.class, cli, "container");
    myContainer.getLoggerManager().setThreshold(settings.getLoggingLevel());

    myMavenSettings = buildSettings(FieldAccessor.<SettingsBuilder>get(MavenCli.class, cli, "settingsBuilder"),
                                    settings,
                                    FieldAccessor.<Properties>get(cliRequestClass, cliRequest, "systemProperties"),
                                    FieldAccessor.<Properties>get(cliRequestClass, cliRequest, "userProperties"));

    myLocalRepository = createLocalRepository(settings.getSnapshotUpdatePolicy());
  }

  private static Settings buildSettings(SettingsBuilder builder,
                                        MavenServerSettings settings,
                                        Properties systemProperties,
                                        Properties userProperties)
    throws RemoteException {
    SettingsBuildingRequest settingsRequest = new DefaultSettingsBuildingRequest();
    settingsRequest.setGlobalSettingsFile(settings.getGlobalSettingsFile());
    settingsRequest.setUserSettingsFile(settings.getUserSettingsFile());
    settingsRequest.setSystemProperties(systemProperties);
    settingsRequest.setUserProperties(userProperties);

    Settings result = new Settings();
    try {
      result = builder.build(settingsRequest).getEffectiveSettings();
    }
    catch (SettingsBuildingException e) {
      Maven3ServerGlobals.getLogger().info(e);
    }

    if (settings.getLocalRepository() != null) {
      result.setLocalRepository(settings.getLocalRepository().getPath());
    }

    if (result.getLocalRepository() == null) {
      result.setLocalRepository(new File(SystemProperties.getUserHome(), ".m2/repository").getPath());
    }

    return result;
  }

  @SuppressWarnings({"unchecked"})
  public <T> T getComponent(Class<T> clazz, String roleHint) {
      try {
          return (T) myContainer.lookup(clazz.getName(), roleHint);
      } catch (ComponentLookupException e) {
          throw new RuntimeException(e);
      }
  }

  @SuppressWarnings({"unchecked"})
  public <T> T getComponent(Class<T> clazz) {
      try {
          return (T) myContainer.lookup(clazz.getName());
      } catch (ComponentLookupException e) {
          throw new RuntimeException(e);
      }
  }

  private ArtifactRepository createLocalRepository(MavenServerSettings.UpdatePolicy snapshotUpdatePolicy) {
    ArtifactRepositoryLayout layout = getComponent(ArtifactRepositoryLayout.class, "default");
    ArtifactRepositoryFactory factory = getComponent(ArtifactRepositoryFactory.class);

    String url = myMavenSettings.getLocalRepository();
    if (!url.startsWith("file:")) url = "file://" + url;

    ArtifactRepository localRepository = factory.createArtifactRepository("local", url, layout, null, null);

    boolean snapshotPolicySet = myMavenSettings.isOffline();
    if (!snapshotPolicySet && snapshotUpdatePolicy == MavenServerSettings.UpdatePolicy.ALWAYS_UPDATE) {
      factory.setGlobalUpdatePolicy(ArtifactRepositoryPolicy.UPDATE_POLICY_ALWAYS);
    }
    factory.setGlobalChecksumPolicy(ArtifactRepositoryPolicy.CHECKSUM_POLICY_WARN);

    return localRepository;
  }

  @Override
  public void customize(@Nullable MavenWorkspaceMap workspaceMap,
                        boolean failOnUnresolvedDependency,
                        @NotNull MavenServerConsole console,
                        @NotNull MavenServerProgressIndicator indicator) throws RemoteException {
  }

  @NotNull
  @Override
  public MavenServerExecutionResult resolveProject(@NotNull File file, @NotNull Collection<String> activeProfiles)
    throws RemoteException, MavenServerProcessCanceledException {
    DependencyTreeResolutionListener listener = new DependencyTreeResolutionListener(myConsoleWrapper);

    MavenExecutionResult result = doResolveProject(file,
                                                   new ArrayList<String>(activeProfiles),
                                                   Arrays.<ResolutionListener>asList(listener));
    return createExecutionResult(file, result, listener.getRootNode());
  }

  private static void setProfilesFromSettings(MavenExecutionRequest request, Settings settings) {
    List<org.apache.maven.settings.Profile> settingsProfiles = settings.getProfiles();

    request.setActiveProfiles(settings.getActiveProfiles());

    if (settingsProfiles != null) {
      for (org.apache.maven.settings.Profile rawProfile : settingsProfiles) {
        Profile profile = SettingsUtils.convertFromSettingsProfile(rawProfile);
        request.addProfile(profile);
      }
    }
  }

  @NotNull
  public MavenExecutionResult doResolveProject(@NotNull final File file,
                                               @NotNull final List<String> activeProfiles,
                                               List<ResolutionListener> listeners) {
    MavenExecutionRequest request = createRequest(file, activeProfiles, Collections.<String>emptyList(), Collections.<String>emptyList());

    setProfilesFromSettings(request, myMavenSettings);

    ProjectBuildingRequest config = request.getProjectBuildingRequest();

    List<Exception> exceptions = new ArrayList<Exception>();

    try {
      // copied from DefaultMavenProjectBuilder.buildWithDependencies
      ProjectBuilder builder = getComponent(ProjectBuilder.class);
      ProjectBuildingResult buildingResult = builder.build(new File(file.getPath()), config);
      //builder.calculateConcreteState(project, config, false);

      MavenProject project = buildingResult.getProject();

      // copied from DefaultLifecycleExecutor.execute
      //findExtensions(project);
      // end copied from DefaultLifecycleExecutor.execute

      //Artifact projectArtifact = project.getArtifact();
      //Map managedVersions = project.getManagedVersionMap();
      //ArtifactMetadataSource metadataSource = getComponent(ArtifactMetadataSource.class);
      project.setDependencyArtifacts(project.createArtifacts(getComponent(ArtifactFactory.class), null, null));
      //
      ArtifactResolver resolver = getComponent(ArtifactResolver.class);

      ArtifactResolutionRequest resolutionRequest = new ArtifactResolutionRequest();
      resolutionRequest.setArtifactDependencies(project.getDependencyArtifacts());
      resolutionRequest.setArtifact(project.getArtifact());
      resolutionRequest.setManagedVersionMap(project.getManagedVersionMap());
      resolutionRequest.setLocalRepository(myLocalRepository);
      resolutionRequest.setRemoteRepositories(project.getRemoteArtifactRepositories());
      resolutionRequest.setListeners(listeners);

      resolutionRequest.setResolveRoot(false);
      resolutionRequest.setResolveTransitively(true);

      ArtifactResolutionResult result = resolver.resolve(resolutionRequest);

      project.setArtifacts(result.getArtifacts());
      // end copied from DefaultMavenProjectBuilder.buildWithDependencies

      return new MavenExecutionResult(project, exceptions);
    }
    catch (Exception e) {
      return handleException(e);
    }


  }

  private MavenExecutionRequest createRequest(File file, List<String> activeProfiles, List<String> inactiveProfiles, List<String> goals) {
    //Properties executionProperties = myMavenSettings.getProperties();
    //if (executionProperties == null) {
    //  executionProperties = new Properties();
    //}

    MavenExecutionRequest result = new DefaultMavenExecutionRequest();
    result.setLocalRepository(myLocalRepository);
    result.setGoals(goals);
    result.setBaseDirectory(file.getParentFile());


    result.setPom(file);

      return result;
  }

  private static MavenExecutionResult handleException(Throwable e) {
      if (e instanceof RuntimeException) throw (RuntimeException) e;
      if (e instanceof Error) throw (Error) e;

      return new MavenExecutionResult(null, Collections.singletonList((Exception) e));
  }

  @NotNull
  public File getLocalRepositoryFile() {
      return new File(myLocalRepository.getBasedir());
  }

  @NotNull
  private MavenServerExecutionResult createExecutionResult(File file, MavenExecutionResult result, DependencyNode rootNode)
    throws RemoteException {
    Collection<MavenProjectProblem> problems = MavenProjectProblem.createProblemsList();
    THashSet<MavenId> unresolvedArtifacts = new THashSet<MavenId>();

    validate(file, result.getExceptions(), problems, unresolvedArtifacts);

    MavenProject mavenProject = result.getMavenProject();
    if (mavenProject == null) return new MavenServerExecutionResult(null, problems, unresolvedArtifacts);

    MavenModel model = MavenModelConverter.convertModel(mavenProject.getModel(),
                                                         mavenProject.getCompileSourceRoots(),
                                                         mavenProject.getTestCompileSourceRoots(),
                                                         mavenProject.getArtifacts(),
                                                         (rootNode == null ? Collections.emptyList() : rootNode.getChildren()),
                                                         mavenProject.getExtensionArtifacts(),
                                                         getLocalRepositoryFile());

    RemoteNativeMavenProjectHolder holder = new RemoteNativeMavenProjectHolder(mavenProject);
    try {
      UnicastRemoteObject.exportObject(holder, 0);
    }
    catch (RemoteException e) {
      throw new RuntimeException(e);
    }

    Collection<String> activatedProfiles = collectActivatedProfiles(mavenProject);

    MavenServerExecutionResult.ProjectData data = new MavenServerExecutionResult.ProjectData(
      model, MavenModelConverter.convertToMap(mavenProject.getModel()), holder, activatedProfiles);
    return new MavenServerExecutionResult(data, problems, unresolvedArtifacts);
  }

  private static Collection<String> collectActivatedProfiles(MavenProject mavenProject) {
    // for some reason project's active profiles do not contain parent's profiles - only local and settings'.
    // parent's profiles do not contain settings' profiles.

    List<Profile> profiles = new ArrayList<Profile>();
    while (mavenProject != null) {
      profiles.addAll(mavenProject.getActiveProfiles());
      mavenProject = mavenProject.getParent();
    }
    return collectProfilesIds(profiles);
  }


  private void validate(File file,
                        Collection<Exception> exceptions,
                        Collection<MavenProjectProblem> problems,
                        Collection<MavenId> unresolvedArtifacts) throws RemoteException {
    for (Exception each : exceptions) {
      Maven3ServerGlobals.getLogger().info(each);

      if (each instanceof InvalidProjectModelException) {
        ModelValidationResult modelValidationResult = ((InvalidProjectModelException)each).getValidationResult();
        if (modelValidationResult != null) {
          for (Object eachValidationProblem : modelValidationResult.getMessages()) {
            problems.add(MavenProjectProblem.createStructureProblem(file.getPath(), (String)eachValidationProblem));
          }
        }
        else {
          problems.add(MavenProjectProblem.createStructureProblem(file.getPath(), each.getCause().getMessage()));
        }
      }
      else if (each instanceof ProjectBuildingException) {
        String causeMessage = each.getCause() != null ? each.getCause().getMessage() : each.getMessage();
        problems.add(MavenProjectProblem.createStructureProblem(file.getPath(), causeMessage));
      }
      else {
        problems.add(MavenProjectProblem.createStructureProblem(file.getPath(), each.getMessage()));
      }
    }
    unresolvedArtifacts.addAll(retrieveUnresolvedArtifactIds());
  }

  private Set<MavenId> retrieveUnresolvedArtifactIds() {
    Set<MavenId> result = new THashSet<MavenId>();
    ((CustomMaven3WagonManager)getComponent(WagonManager.class)).getUnresolvedCollector().retrieveUnresolvedIds(result);
    ((CustomMaven3ArtifactResolver)getComponent(ArtifactResolver.class)).getUnresolvedCollector().retrieveUnresolvedIds(result);
    return result;
  }

  @NotNull
  @Override
  public MavenArtifact resolve(@NotNull MavenArtifactInfo info, @NotNull List<MavenRemoteRepository> remoteRepositories)
    throws RemoteException, MavenServerProcessCanceledException {
    //DependencyTreeResolutionListener listener = new DependencyTreeResolutionListener(myConsoleWrapper);
    //
    //MavenExecutionResult result = myImpl.resolveProject(file,
    //                                                    new ArrayList<String>(activeProfiles),
    //                                                    Arrays.<ResolutionListener>asList(listener));
    //return createExecutionResult(file, result, listener.getRootNode());

    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public List<MavenArtifact> resolveTransitively(@NotNull List<MavenArtifactInfo> artifacts,
                                                 @NotNull List<MavenRemoteRepository> remoteRepositories)
    throws RemoteException, MavenServerProcessCanceledException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Collection<MavenArtifact> resolvePlugin(@NotNull MavenPlugin plugin,
                                                 @NotNull List<MavenRemoteRepository> repositories,
                                                 int nativeMavenProjectId,
                                                 boolean transitive) throws RemoteException, MavenServerProcessCanceledException {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public MavenServerExecutionResult execute(@NotNull File file, @NotNull Collection<String> activeProfiles, @NotNull List<String> goals)
    throws RemoteException, MavenServerProcessCanceledException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void reset() throws RemoteException {
  }

  @Override
  public void release() throws RemoteException {
    myContainer.dispose();
  }

  public void clearCaches() throws RemoteException {
    // do nothing
  }

  public void clearCachesFor(final MavenId projectId) throws RemoteException {
    // do nothing
  }

  public static MavenModel interpolateAndAlignModel(MavenModel model, File basedir) throws RemoteException {
    Model result = MavenModelConverter.toNativeModel(model);
    result = doInterpolate(result, basedir);

    PathTranslator pathTranslator = new DefaultPathTranslator();
    pathTranslator.alignToBaseDirectory(result, basedir);

    return MavenModelConverter.convertModel(result, null);
  }

  public static MavenModel assembleInheritance(MavenModel model, MavenModel parentModel) {
    throw new UnsupportedOperationException();
  }

  public static ProfileApplicationResult applyProfiles(MavenModel model,
                                                       File basedir,
                                                       Collection<String> explicitProfiles,
                                                       Collection<String> alwaysOnProfiles) throws RemoteException {
    Model nativeModel = MavenModelConverter.toNativeModel(model);

    List<Profile> activatedPom = new ArrayList<Profile>();
    List<Profile> activatedExternal = new ArrayList<Profile>();
    List<Profile> activeByDefault = new ArrayList<Profile>();

    List<Profile> rawProfiles = nativeModel.getProfiles();
    List<Profile> expandedProfilesCache = null;

    for (int i = 0; i < rawProfiles.size(); i++) {
      Profile eachRawProfile = rawProfiles.get(i);

      boolean shouldAdd = explicitProfiles.contains(eachRawProfile.getId()) || alwaysOnProfiles.contains(eachRawProfile.getId());

      Activation activation = eachRawProfile.getActivation();
      if (activation != null) {
        if (activation.isActiveByDefault()) {
          activeByDefault.add(eachRawProfile);
        }

        // expand only if necessary
        if (expandedProfilesCache == null) expandedProfilesCache = doInterpolate(nativeModel, basedir).getProfiles();
        Profile eachExpandedProfile = expandedProfilesCache.get(i);

        for (ProfileActivator eachActivator : getProfileActivators(basedir)) {
          try {
            if (eachActivator.canDetermineActivation(eachExpandedProfile) && eachActivator.isActive(eachExpandedProfile)) {
              shouldAdd = true;
              break;
            }
          }
          catch (ProfileActivationException e) {
            Maven3ServerGlobals.getLogger().warn(e);
          }
        }
      }

      if (shouldAdd) {
        if (MavenConstants.PROFILE_FROM_POM.equals(eachRawProfile.getSource())) {
          activatedPom.add(eachRawProfile);
        }
        else {
          activatedExternal.add(eachRawProfile);
        }
      }
    }

    List<Profile> activatedProfiles = new ArrayList<Profile>(activatedPom.isEmpty() ? activeByDefault : activatedPom);
    activatedProfiles.addAll(activatedExternal);

    for (Profile each : activatedProfiles) {
      new DefaultProfileInjector().injectProfile(nativeModel, each, null, null);
    }

    return new ProfileApplicationResult(MavenModelConverter.convertModel(nativeModel, null),
                                        collectProfilesIds(activatedProfiles));
  }

  private static Model doInterpolate(Model result, File basedir) throws RemoteException {
    try {
      AbstractStringBasedModelInterpolator interpolator = new CustomMaven3ModelInterpolator(new DefaultPathTranslator());
      interpolator.initialize();

      Properties props = MavenServerUtil.collectSystemProperties();
      ProjectBuilderConfiguration config = new DefaultProjectBuilderConfiguration().setExecutionProperties(props);
      result = interpolator.interpolate(result, basedir, config, false);
    }
    catch (ModelInterpolationException e) {
      Maven3ServerGlobals.getLogger().warn(e);
    }
    catch (InitializationException e) {
      Maven3ServerGlobals.getLogger().error(e);
    }
    return result;
  }

  private static Collection<String> collectProfilesIds(List<Profile> profiles) {
    Collection<String> result = new THashSet<String>();
    for (Profile each : profiles) {
      if (each.getId() != null) {
        result.add(each.getId());
      }
    }
    return result;
  }

  private static ProfileActivator[] getProfileActivators(File basedir) throws RemoteException {
    SystemPropertyProfileActivator sysPropertyActivator = new SystemPropertyProfileActivator();
    DefaultContext context = new DefaultContext();
    context.put("SystemProperties", MavenServerUtil.collectSystemProperties());
    try {
      sysPropertyActivator.contextualize(context);
    }
    catch (ContextException e) {
      Maven3ServerGlobals.getLogger().error(e);
      return new ProfileActivator[0];
    }

    return new ProfileActivator[]{new MyFileProfileActivator(basedir),
      sysPropertyActivator,
      new JdkPrefixProfileActivator(),
      new OperatingSystemProfileActivator()};
  }


}

