
/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

/**
 * created at Jan 3, 2002
 * @author Jeka
 */
package com.intellij.compiler;

import com.intellij.CommonBundle;
import com.intellij.ProjectTopics;
import com.intellij.compiler.impl.javaCompiler.BackendCompiler;
import com.intellij.compiler.impl.javaCompiler.api.CompilerAPICompiler;
import com.intellij.compiler.impl.javaCompiler.eclipse.EclipseCompiler;
import com.intellij.compiler.impl.javaCompiler.eclipse.EclipseEmbeddedCompiler;
import com.intellij.compiler.impl.javaCompiler.javac.JavacCompiler;
import com.intellij.compiler.impl.javaCompiler.jikes.JikesCompiler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.compiler.options.ExcludedEntriesConfiguration;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.ModuleAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import org.apache.oro.text.regex.*;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

@State(
  name = "CompilerConfiguration",
  storages = {
    @Storage( file = StoragePathMacros.PROJECT_FILE),
    @Storage( file = StoragePathMacros.PROJECT_CONFIG_DIR + "/compiler.xml", scheme = StorageScheme.DIRECTORY_BASED)
  }
)
public class CompilerConfigurationImpl extends CompilerConfiguration implements PersistentStateComponent<Element>, ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.CompilerConfiguration");
  @NonNls public static final String TESTS_EXTERNAL_COMPILER_HOME_PROPERTY_NAME = "tests.external.compiler.home";
  public static final int DEPENDENCY_FORMAT_VERSION = 55;

  @SuppressWarnings({"WeakerAccess"}) public String DEFAULT_COMPILER;
  @NotNull private BackendCompiler myDefaultJavaCompiler;

  // extensions of the files considered as resource files
  private final List<Pattern> myRegexpResourcePatterns = new ArrayList<Pattern>();
  // extensions of the files considered as resource files. If present, overrides patterns in old regexp format stored in myRegexpResourcePatterns
  private final List<String> myWildcardPatterns = new ArrayList<String>();
  private final List<CompiledPattern> myCompiledPatterns = new ArrayList<CompiledPattern>();
  private final List<CompiledPattern> myNegatedCompiledPatterns = new ArrayList<CompiledPattern>();
  private boolean myWildcardPatternsInitialized = false;
  private final Project myProject;
  private final ModuleManager myModuleManager;
  private final ExcludedEntriesConfiguration myExcludedEntriesConfiguration;

  private final Collection<BackendCompiler> myRegisteredCompilers = new ArrayList<BackendCompiler>();
  private JavacCompiler JAVAC_EXTERNAL_BACKEND;
  private final Perl5Matcher myPatternMatcher = new Perl5Matcher();

  {
    loadDefaultWildcardPatterns();
  }

  private boolean myEnableAnnotationProcessors = false;
  private final Map<String, String> myProcessorsMap = new HashMap<String, String>(); // map: AnnotationProcessorName -> options
  private boolean myObtainProcessorsFromClasspath = true;
  private String myProcessorPath = "";
  private final Map<Module, String> myProcessedModules = new HashMap<Module, String>();
  private final Map<String, String> myModuleNames = new HashMap<String, String>();
  private boolean myAddNotNullAssertions = true;

  @Nullable
  private String myBytecodeTargetLevel = null;  // null means compiler default
  private final Map<String, String> myModuleBytecodeTarget = new java.util.HashMap<String, String>();

  public CompilerConfigurationImpl(Project project, ModuleManager moduleManager) {
    myProject = project;
    myModuleManager = moduleManager;
    myExcludedEntriesConfiguration = new ExcludedEntriesConfiguration();
    Disposer.register(project, myExcludedEntriesConfiguration);
    project.getMessageBus().connect(project).subscribe(ProjectTopics.MODULES, new ModuleAdapter() {
      public void beforeModuleRemoved(Project project, Module module) {
        myProcessedModules.remove(module);
        myModuleNames.remove(module.getName());
      }

      public void moduleAdded(Project project, Module module) {
        final String moduleName = module.getName();
        if (myModuleNames.containsKey(moduleName)) {
          final String dirName = myModuleNames.remove(moduleName);
          myProcessedModules.put(module, dirName);
        }
      }
    });
  }

  public Element getState() {
    try {
      @NonNls final Element e = new Element("state");
      writeExternal(e);
      return e;
    }
    catch (WriteExternalException e1) {
      LOG.error(e1);
      return null;
    }
  }

  public void loadState(Element state) {
    try {
      readExternal(state);
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }

  public void setProjectBytecodeTarget(@Nullable String level) {
    myBytecodeTargetLevel = level;
  }

  @Override
  @Nullable
  public String getProjectBytecodeTarget() {
    return myBytecodeTargetLevel;
  }

  public void setModulesBytecodeTargetMap(@NotNull Map<String, String> mapping) {
    myModuleBytecodeTarget.clear();
    myModuleBytecodeTarget.putAll(mapping);
  }

  public Map<String, String> getModulesBytecodeTargetMap() {
    return myModuleBytecodeTarget;
  }

  @Override
  @Nullable
  public String getBytecodeTargetLevel(Module module) {
    final String level = myModuleBytecodeTarget.get(module.getName());
    if (level != null) {
      return "".equals(level)? null : level;
    }
    return myBytecodeTargetLevel;
  }

  private void loadDefaultWildcardPatterns() {
    if (!myWildcardPatterns.isEmpty()) {
      removeWildcardPatterns();
    }
    try {
      addWildcardResourcePattern("?*.properties");
      addWildcardResourcePattern("?*.xml");
      addWildcardResourcePattern("?*.gif");
      addWildcardResourcePattern("?*.png");
      addWildcardResourcePattern("?*.jpeg");
      addWildcardResourcePattern("?*.jpg");
      addWildcardResourcePattern("?*.html");
      addWildcardResourcePattern("?*.dtd");
      addWildcardResourcePattern("?*.tld");
      addWildcardResourcePattern("?*.ftl");
    }
    catch (MalformedPatternException e) {
      LOG.error(e);
    }
  }

  public static String getTestsExternalCompilerHome() {
    String compilerHome = System.getProperty(TESTS_EXTERNAL_COMPILER_HOME_PROPERTY_NAME, null);
    if (compilerHome == null) {
      if (SystemInfo.isMac) {
        compilerHome = new File(System.getProperty("java.home")).getAbsolutePath();
      }
      else {
        compilerHome = new File(System.getProperty("java.home")).getParentFile().getAbsolutePath();        
      }
    }
    return compilerHome;
  }

  private static Pattern compilePattern(@NonNls String s) throws MalformedPatternException {
    try {
      final PatternCompiler compiler = new Perl5Compiler();
      return SystemInfo.isFileSystemCaseSensitive? compiler.compile(s) : compiler.compile(s, Perl5Compiler.CASE_INSENSITIVE_MASK);
    }
    catch (org.apache.oro.text.regex.MalformedPatternException ex) {
      throw new MalformedPatternException(ex);
    }
  }

  public void disposeComponent() {
  }

  public void initComponent() { }

  public void projectClosed() {
  }

  public JavacCompiler getJavacCompiler() {
    createCompilers();
    return JAVAC_EXTERNAL_BACKEND;
  }

  public void projectOpened() {
    createCompilers();
  }

  private void createCompilers() {
    if (JAVAC_EXTERNAL_BACKEND != null) {
      return;
    }

    JAVAC_EXTERNAL_BACKEND = new JavacCompiler(myProject);
    myRegisteredCompilers.add(JAVAC_EXTERNAL_BACKEND);

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      final BackendCompiler JIKES_BACKEND = new JikesCompiler(myProject);
      myRegisteredCompilers.add(JIKES_BACKEND);

      if (EclipseCompiler.isInitialized()) {
        final EclipseCompiler eclipse = new EclipseCompiler(myProject);
        myRegisteredCompilers.add(eclipse);
      }

      if (ApplicationManagerEx.getApplicationEx().isInternal()) {
        try {
          final EclipseEmbeddedCompiler eclipseEmbedded = new EclipseEmbeddedCompiler(myProject);
          myRegisteredCompilers.add(eclipseEmbedded);
        }
        catch (NoClassDefFoundError e) {
          // eclipse jar must be not in the classpath
        }
      }

      try {
        CompilerAPICompiler inProcessJavaCompiler = new CompilerAPICompiler(myProject);
        myRegisteredCompilers.add(inProcessJavaCompiler);
      }
      catch (NoClassDefFoundError e) {
        // wrong JDK
      }
    }

    final BackendCompiler[] compilers = Extensions.getExtensions(BackendCompiler.EP_NAME, myProject);
    final Set<FileType> types = new HashSet<FileType>();
    for (BackendCompiler compiler : compilers) {
      myRegisteredCompilers.add(compiler);
      types.addAll(compiler.getCompilableFileTypes());
    }

    final CompilerManager compilerManager = CompilerManager.getInstance(myProject);
    for (FileType type : types) {
      compilerManager.addCompilableFileType(type);
    }

    myDefaultJavaCompiler = JAVAC_EXTERNAL_BACKEND;
    for (BackendCompiler compiler : myRegisteredCompilers) {
      if (compiler.getId().equals(DEFAULT_COMPILER)) {
        myDefaultJavaCompiler = compiler;
        break;
      }
    }
    DEFAULT_COMPILER = myDefaultJavaCompiler.getId();
  }

  public Collection<BackendCompiler> getRegisteredJavaCompilers() {
    createCompilers();
    return myRegisteredCompilers;
  }

  public String[] getResourceFilePatterns() {
    return getWildcardPatterns();
  }

  private String[] getRegexpPatterns() {
    String[] patterns = ArrayUtil.newStringArray(myRegexpResourcePatterns.size());
    int index = 0;
    for (final Pattern myRegexpResourcePattern : myRegexpResourcePatterns) {
      patterns[index++] = myRegexpResourcePattern.getPattern();
    }
    return patterns;
  }

  private String[] getWildcardPatterns() {
    return ArrayUtil.toStringArray(myWildcardPatterns);
  }

  public void addResourceFilePattern(String namePattern) throws MalformedPatternException {
    addWildcardResourcePattern(namePattern);
  }

  // need this method only for handling patterns in old regexp format
  private void addRegexpPattern(String namePattern) throws MalformedPatternException {
    Pattern pattern = compilePattern(namePattern);
    if (pattern != null) {
      myRegexpResourcePatterns.add(pattern);
    }
  }

  public ExcludedEntriesConfiguration getExcludedEntriesConfiguration() {
    return myExcludedEntriesConfiguration;
  }

  public boolean isExcludedFromCompilation(final VirtualFile virtualFile) {
    return myExcludedEntriesConfiguration.isExcluded(virtualFile);
  }

  @Override
  public boolean isResourceFile(VirtualFile virtualFile) {
    return isResourceFile(virtualFile.getName(), virtualFile.getParent());
  }

  @Override
  public boolean isAddNotNullAssertions() {
    return myAddNotNullAssertions;
  }

  @Override
  public void setAddNotNullAssertions(boolean enabled) {
    myAddNotNullAssertions = enabled;
  }

  public boolean isAnnotationProcessorsEnabled() {
    return myEnableAnnotationProcessors;
  }

  public void setAnnotationProcessorsEnabled(boolean enableAnnotationProcessors) {
    myEnableAnnotationProcessors = enableAnnotationProcessors;
  }

  public boolean isObtainProcessorsFromClasspath() {
    return myObtainProcessorsFromClasspath;
  }

  public void setObtainProcessorsFromClasspath(boolean obtainProcessorsFromClasspath) {
    myObtainProcessorsFromClasspath = obtainProcessorsFromClasspath;
  }

  public String getProcessorPath() {
    return myProcessorPath;
  }

  public void setProcessorsPath(String processorsPath) {
    myProcessorPath = processorsPath;
  }

  public Map<String, String> getAnnotationProcessorsMap() {
    return Collections.unmodifiableMap(myProcessorsMap);
  }

  public void setAnnotationProcessorsMap(Map<String, String> map) {
    myProcessorsMap.clear();
    myProcessorsMap.putAll(map);
  }

  public void setAnotationProcessedModules(Map<Module, String> modules) {
    myProcessedModules.clear();
    myModuleNames.clear();
    myProcessedModules.putAll(modules);
  }

  public Map<Module, String> getAnotationProcessedModules() {
    return Collections.unmodifiableMap(myProcessedModules);
  }

  public boolean isAnnotationProcessingEnabled(Module module) {
    return myProcessedModules.containsKey(module);
  }

  public String getGeneratedSourceDirName(Module module) {
    return myProcessedModules.get(module);
  }

  private void addWildcardResourcePattern(@NonNls final String wildcardPattern) throws MalformedPatternException {
    final CompiledPattern pattern = convertToRegexp(wildcardPattern);
    if (pattern != null) {
      myWildcardPatterns.add(wildcardPattern);
      if (isPatternNegated(wildcardPattern)) {
        myNegatedCompiledPatterns.add(pattern);
      }
      else {
        myCompiledPatterns.add(pattern);
      }
    }
  }

  public void removeResourceFilePatterns() {
    removeWildcardPatterns();
  }

  private void removeRegexpPatterns() {
    myRegexpResourcePatterns.clear();
  }

  private void removeWildcardPatterns() {
    myWildcardPatterns.clear();
    myCompiledPatterns.clear();
    myNegatedCompiledPatterns.clear();
  }

  private static CompiledPattern convertToRegexp(String wildcardPattern) {
    if (isPatternNegated(wildcardPattern)) {
      wildcardPattern = wildcardPattern.substring(1);
    }

    wildcardPattern = FileUtil.toSystemIndependentName(wildcardPattern);

    String srcRoot = null;
    int colon = wildcardPattern.indexOf(":");
    if (colon > 0) {
      srcRoot = wildcardPattern.substring(0, colon);
      wildcardPattern = wildcardPattern.substring(colon + 1);
    }

    String dirPattern = null;
    int slash = wildcardPattern.lastIndexOf('/');
    if (slash >= 0) {
      dirPattern = wildcardPattern.substring(0, slash + 1);
      wildcardPattern = wildcardPattern.substring(slash + 1);
      if (!dirPattern.startsWith("/")) {
        dirPattern = "/" + dirPattern;
      }
      //now dirPattern starts and ends with '/'

      dirPattern = normalizeWildcards(dirPattern);

      dirPattern = StringUtil.replace(dirPattern, "/.*.*/", "(/.*)?/");
      dirPattern = StringUtil.trimEnd(dirPattern, "/");

      dirPattern = optimize(dirPattern);
    }

    wildcardPattern = normalizeWildcards(wildcardPattern);
    wildcardPattern = optimize(wildcardPattern);

    final Pattern dirCompiled = dirPattern == null ? null : compilePattern(dirPattern);
    final Pattern srcCompiled = srcRoot == null ? null : compilePattern(optimize(normalizeWildcards(srcRoot)));
    return new CompiledPattern(compilePattern(wildcardPattern), dirCompiled, srcCompiled);
  }

  private static String optimize(String wildcardPattern) {
    return wildcardPattern.replaceAll("(?:\\.\\*)+", ".*");
  }

  private static String normalizeWildcards(String wildcardPattern) {
    wildcardPattern = StringUtil.replace(wildcardPattern, "\\!", "!");
    wildcardPattern = StringUtil.replace(wildcardPattern, ".", "\\.");
    wildcardPattern = StringUtil.replace(wildcardPattern, "*?", ".+");
    wildcardPattern = StringUtil.replace(wildcardPattern, "?*", ".+");
    wildcardPattern = StringUtil.replace(wildcardPattern, "*", ".*");
    wildcardPattern = StringUtil.replace(wildcardPattern, "?", ".");
    return wildcardPattern;
  }

  public static boolean isPatternNegated(String wildcardPattern) {
    return wildcardPattern.length() > 1 && wildcardPattern.charAt(0) == '!';
  }

  public boolean isResourceFile(String name) {
    return isResourceFile(name, null);
  }

  private boolean matches(String s, Pattern p) {
    synchronized (myPatternMatcher) {
      try {
        return myPatternMatcher.matches(s, p);
      }
      catch (Exception e) {
        LOG.error("Exception matching file name \"" + s + "\" against the pattern \"" + p + "\"", e);
        return false;
      }
    }
  }

  private boolean isResourceFile(String name, @Nullable VirtualFile parent) {
    final Ref<String> parentRef = Ref.create(null);
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < myCompiledPatterns.size(); i++) {
      if (matches(name, parent, parentRef, myCompiledPatterns.get(i))) {
        return true;
      }
    }

    if (myNegatedCompiledPatterns.isEmpty()) {
      return false;
    }

    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < myNegatedCompiledPatterns.size(); i++) {
      if (matches(name, parent, parentRef, myNegatedCompiledPatterns.get(i))) {
        return false;
      }
    }
    return true;
  }

  private boolean matches(String name, VirtualFile parent, Ref<String> parentRef, CompiledPattern pair) {
    if (!matches(name, pair.fileName)) {
      return false;
    }

    if (parent != null && (pair.dir != null || pair.srcRoot != null)) {
      VirtualFile srcRoot = ProjectRootManager.getInstance(myProject).getFileIndex().getSourceRootForFile(parent);
      if (pair.dir != null) {
        String parentPath = parentRef.get();
        if (parentPath == null) {
          parentRef.set(parentPath = srcRoot == null ? parent.getPath() : VfsUtilCore.getRelativePath(parent, srcRoot, '/'));
        }
        if (parentPath == null || !matches("/" + parentPath, pair.dir)) {
          return false;
        }
      }

      if (pair.srcRoot != null) {
        String srcRootName = srcRoot == null ? null : srcRoot.getName();
        if (srcRootName == null || !matches(srcRootName, pair.srcRoot)) {
          return false;
        }
      }
    }

    return true;
  }

  // property names
  @NonNls private static final String EXCLUDE_FROM_COMPILE = "excludeFromCompile";
  @NonNls private static final String RESOURCE_EXTENSIONS = "resourceExtensions";
  @NonNls private static final String ANNOTATION_PROCESSING = "annotationProcessing";
  @NonNls private static final String BYTECODE_TARGET_LEVEL = "bytecodeTargetLevel";
  @NonNls private static final String WILDCARD_RESOURCE_PATTERNS = "wildcardResourcePatterns";
  @NonNls private static final String ENTRY = "entry";
  @NonNls private static final String NAME = "name";
  @NonNls private static final String ADD_NOTNULL_ASSERTIONS = "addNotNullAssertions";

  public void readExternal(Element parentNode) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, parentNode);

    final Element notNullAssertions = parentNode.getChild(ADD_NOTNULL_ASSERTIONS);
    if (notNullAssertions != null) {
      myAddNotNullAssertions = Boolean.valueOf(notNullAssertions.getAttributeValue("enabled", "true"));
    }

    Element node = parentNode.getChild(EXCLUDE_FROM_COMPILE);
    if (node != null) {
      myExcludedEntriesConfiguration.readExternal(node);
    }

    try {
      removeRegexpPatterns();
      node = parentNode.getChild(RESOURCE_EXTENSIONS);
      if (node != null) {
        for (final Object o : node.getChildren(ENTRY)) {
          Element element = (Element)o;
          String pattern = element.getAttributeValue(NAME);
          if (pattern != null && !"".equals(pattern)) {
            addRegexpPattern(pattern);
          }
        }
      }

      removeWildcardPatterns();
      node = parentNode.getChild(WILDCARD_RESOURCE_PATTERNS);
      if (node != null) {
        myWildcardPatternsInitialized = true;
        for (final Object o : node.getChildren(ENTRY)) {
          final Element element = (Element)o;
          String pattern = element.getAttributeValue(NAME);
          if (pattern != null && !"".equals(pattern)) {
            addWildcardResourcePattern(pattern);
          }
        }
      }
    }
    catch (MalformedPatternException e) {
      throw new InvalidDataException(e);
    }

    final Element annotationProcessingSettings = parentNode.getChild(ANNOTATION_PROCESSING);
    if (annotationProcessingSettings != null) {
      myEnableAnnotationProcessors = Boolean.valueOf(annotationProcessingSettings.getAttributeValue("enabled", "false"));
      myObtainProcessorsFromClasspath = Boolean.valueOf(annotationProcessingSettings.getAttributeValue("useClasspath", "true"));

      final StringBuilder pathBuilder = new StringBuilder();
      for (Element pathElement : (Collection<Element>)annotationProcessingSettings.getChildren("processorPath")) {
        final String path = pathElement.getAttributeValue("value");
        if (path != null) {
          if (pathBuilder.length() > 0) {
            pathBuilder.append(File.pathSeparator);
          }
          pathBuilder.append(path);
        }
      }
      myProcessorPath = pathBuilder.toString();

      myProcessorsMap.clear();
      for (Element processorChild : (Collection<Element>)annotationProcessingSettings.getChildren("processor")) {
        final String name = processorChild.getAttributeValue("name");
        final String options = processorChild.getAttributeValue("options", "");
        myProcessorsMap.put(name, options);
      }
      myProcessedModules.clear();
      myModuleNames.clear();

      final Collection<Element> processed = (Collection<Element>)annotationProcessingSettings.getChildren("processModule");
      if (!processed.isEmpty()) {
        final Map<String, Module> moduleMap = new HashMap<String, Module>();
        for (Module module : myModuleManager.getModules()) {
          moduleMap.put(module.getName(), module);
        }
        for (Element moduleElement : processed) {
          final String name = moduleElement.getAttributeValue("name");
          final String dirname = moduleElement.getAttributeValue("generatedDirName");
          if (name != null) {
            final Module module = moduleMap.get(name);
            if (module != null) {
              myProcessedModules.put(module, dirname);
            }
            else {
              myModuleNames.put(name, dirname);
            }
          }
        }
      }

      myBytecodeTargetLevel = null;
      myModuleBytecodeTarget.clear();
      final Element bytecodeTargetElement = parentNode.getChild(BYTECODE_TARGET_LEVEL);
      if (bytecodeTargetElement != null) {
        myBytecodeTargetLevel = bytecodeTargetElement.getAttributeValue("target");
        for (Element elem : (Collection<Element>)bytecodeTargetElement.getChildren("module")) {
          final String name = elem.getAttributeValue("name");
          if (name == null) {
            continue;
          }
          final String target = elem.getAttributeValue("target");
          if (target == null) {
            continue;
          }
          myModuleBytecodeTarget.put(name, target);
        }
      }
    }
  }

  public void writeExternal(Element parentNode) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, parentNode);

    if (myAddNotNullAssertions != true) {
      final Element notNullAssertions = new Element(ADD_NOTNULL_ASSERTIONS);
      notNullAssertions.setAttribute("enabled", String.valueOf(myAddNotNullAssertions));
      parentNode.addContent(notNullAssertions);
    }

    if(myExcludedEntriesConfiguration.getExcludeEntryDescriptions().length > 0) {
      Element newChild = new Element(EXCLUDE_FROM_COMPILE);
      myExcludedEntriesConfiguration.writeExternal(newChild);
      parentNode.addContent(newChild);
    }

    final Element newChild = new Element(RESOURCE_EXTENSIONS);
    for (final String pattern : getRegexpPatterns()) {
      final Element entry = new Element(ENTRY);
      entry.setAttribute(NAME, pattern);
      newChild.addContent(entry);
    }
    parentNode.addContent(newChild);

    if (myWildcardPatternsInitialized || !myWildcardPatterns.isEmpty()) {
      final Element wildcardPatterns = new Element(WILDCARD_RESOURCE_PATTERNS);
      for (final String wildcardPattern : myWildcardPatterns) {
        final Element entry = new Element(ENTRY);
        entry.setAttribute(NAME, wildcardPattern);
        wildcardPatterns.addContent(entry);
      }
      parentNode.addContent(wildcardPatterns);
    }

    final Element annotationProcessingSettings = new Element(ANNOTATION_PROCESSING);
    parentNode.addContent(annotationProcessingSettings);
    annotationProcessingSettings.setAttribute("enabled", String.valueOf(myEnableAnnotationProcessors));
    annotationProcessingSettings.setAttribute("useClasspath", String.valueOf(myObtainProcessorsFromClasspath));
    if (myProcessorPath.length() > 0) {
      final StringTokenizer tokenizer = new StringTokenizer(myProcessorPath, File.pathSeparator, false);
      while (tokenizer.hasMoreTokens()) {
        final String path = tokenizer.nextToken();
        final Element pathElement = new Element("processorPath");
        annotationProcessingSettings.addContent(pathElement);
        pathElement.setAttribute("value", path);
      }
    }
    for (Map.Entry<String, String> entry : myProcessorsMap.entrySet()) {
      final Element processor = new Element("processor");
      annotationProcessingSettings.addContent(processor);
      processor.setAttribute("name", entry.getKey());
      processor.setAttribute("options", entry.getValue());
    }
    final List<Module> modules = new ArrayList<Module>(myProcessedModules.keySet());
    Collections.sort(modules, new Comparator<Module>() {
      public int compare(Module o1, Module o2) {
        return o1.getName().compareToIgnoreCase(o2.getName());
      }
    });
    for (Module module : modules) {
      final Element moduleElement = new Element("processModule");
      annotationProcessingSettings.addContent(moduleElement);
      moduleElement.setAttribute("name", module.getName());
      final String dirName = myProcessedModules.get(module);
      if (dirName != null && dirName.length() > 0) {
        moduleElement.setAttribute("generatedDirName", dirName);
      }
    }

    if (!StringUtil.isEmpty(myBytecodeTargetLevel) || !myModuleBytecodeTarget.isEmpty()) {
      final Element bytecodeTarget = new Element(BYTECODE_TARGET_LEVEL);
      parentNode.addContent(bytecodeTarget);
      if (!StringUtil.isEmpty(myBytecodeTargetLevel)) {
        bytecodeTarget.setAttribute("target", myBytecodeTargetLevel);
      }
      if (!myModuleBytecodeTarget.isEmpty()) {
        final List<String> moduleNames = new ArrayList<String>(myModuleBytecodeTarget.keySet());
        Collections.sort(moduleNames, new Comparator<String>() {
          @Override
          public int compare(String o1, String o2) {
            return o1.compareTo(o2);
          }
        });
        for (String name : moduleNames) {
          final Element moduleElement = new Element("module");
          bytecodeTarget.addContent(moduleElement);
          moduleElement.setAttribute("name", name);
          moduleElement.setAttribute("target", myModuleBytecodeTarget.get(name));
        }
      }
    }
  }

  @NotNull @NonNls
  public String getComponentName() {
    return "CompilerConfiguration";
  }

  public BackendCompiler getDefaultCompiler() {
    createCompilers();
    return myDefaultJavaCompiler;
  }

  /**
   * @param defaultCompiler The compiler that is passed as a parameter to setDefaultCompiler() 
   * must be one of the registered compilers in compiler configuration.
   * Otherwise because of lazy compiler initialization, the value of default compiler will point to some other compiler instance
   */
  public void setDefaultCompiler(BackendCompiler defaultCompiler) {
    myDefaultJavaCompiler = defaultCompiler;
    DEFAULT_COMPILER = defaultCompiler.getId();
  }

  public void convertPatterns() {
    if (!needPatternConversion()) {
      return;
    }
    try {
      boolean ok;
      try {
        ok = doConvertPatterns();
      }
      catch (MalformedPatternException e) {
        ok = false;
      }
      if (!ok) {
        final String initialPatternString = patternsToString(getRegexpPatterns());
        final String message = CompilerBundle.message(
          "message.resource.patterns.format.changed",
          ApplicationNamesInfo.getInstance().getProductName(),
          initialPatternString,
          CommonBundle.getOkButtonText(),
          CommonBundle.getCancelButtonText()
        );
        final String wildcardPatterns = Messages.showInputDialog(
          myProject, message, CompilerBundle.message("pattern.conversion.dialog.title"), Messages.getWarningIcon(), initialPatternString, new InputValidator() {
          public boolean checkInput(String inputString) {
            return true;
          }
          public boolean canClose(String inputString) {
            final StringTokenizer tokenizer = new StringTokenizer(inputString, ";", false);
            StringBuilder malformedPatterns = new StringBuilder();

            while (tokenizer.hasMoreTokens()) {
              String pattern = tokenizer.nextToken();
              try {
                addWildcardResourcePattern(pattern);
              }
              catch (MalformedPatternException e) {
                malformedPatterns.append("\n\n");
                malformedPatterns.append(pattern);
                malformedPatterns.append(": ");
                malformedPatterns.append(e.getMessage());
              }
            }

            if (malformedPatterns.length() > 0) {
              Messages.showErrorDialog(CompilerBundle.message("error.bad.resource.patterns", malformedPatterns.toString()),
                                       CompilerBundle.message("bad.resource.patterns.dialog.title"));
              removeWildcardPatterns();
              return false;
            }
            return true;
          }
        });
        if (wildcardPatterns == null) { // cancel pressed
          loadDefaultWildcardPatterns();
        }
      }
    }
    finally {
      myWildcardPatternsInitialized = true;
    }
  }

  private boolean needPatternConversion() {
    return !myWildcardPatternsInitialized && !myRegexpResourcePatterns.isEmpty();
  }

  private boolean doConvertPatterns() throws MalformedPatternException {
    final String[] regexpPatterns = getRegexpPatterns();
    final List<String> converted = new ArrayList<String>();
    final Pattern multipleExtensionsPatternPattern = compilePattern("\\.\\+\\\\\\.\\((\\w+(?:\\|\\w+)*)\\)");
    final Pattern singleExtensionPatternPattern = compilePattern("\\.\\+\\\\\\.(\\w+)");
    final Perl5Matcher matcher = new Perl5Matcher();
    for (final String regexpPattern : regexpPatterns) {
      //final Matcher multipleExtensionsMatcher = multipleExtensionsPatternPattern.matcher(regexpPattern);
      if (matcher.matches(regexpPattern, multipleExtensionsPatternPattern)) {
        final MatchResult match = matcher.getMatch();
        final StringTokenizer tokenizer = new StringTokenizer(match.group(1), "|", false);
        while (tokenizer.hasMoreTokens()) {
          converted.add("?*." + tokenizer.nextToken());
        }
      }
      else {
        //final Matcher singleExtensionMatcher = singleExtensionPatternPattern.matcher(regexpPattern);
        if (matcher.matches(regexpPattern, singleExtensionPatternPattern)) {
          final MatchResult match = matcher.getMatch();
          converted.add("?*." + match.group(1));
        }
        else {
          return false;
        }
      }
    }
    for (final String aConverted : converted) {
      addWildcardResourcePattern(aConverted);
    }
    return true;
  }

  private static String patternsToString(final String[] patterns) {
    final StringBuilder extensionsString = new StringBuilder();
    for (int idx = 0; idx < patterns.length; idx++) {
      if (idx > 0) {
        extensionsString.append(";");
      }
      extensionsString.append(patterns[idx]);
    }
    return extensionsString.toString();
  }
  
  private static class CompiledPattern {
    @NotNull final Pattern fileName;
    @Nullable final Pattern dir;
    @Nullable final Pattern srcRoot;

    private CompiledPattern(Pattern fileName, Pattern dir, Pattern srcRoot) {
      this.fileName = fileName;
      this.dir = dir;
      this.srcRoot = srcRoot;
    }
  }
  
}