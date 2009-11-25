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
package com.intellij.javadoc;

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.RegexpFilter;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.ex.PathUtilEx;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.HashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 24, 2004
 */
public class JavadocConfiguration implements ModuleRunProfile, JDOMExternalizable{
  public String OUTPUT_DIRECTORY;
  public String OPTION_SCOPE = PsiKeyword.PROTECTED;
  public boolean OPTION_HIERARCHY = true;
  public boolean OPTION_NAVIGATOR = true;
  public boolean OPTION_INDEX = true;
  public boolean OPTION_SEPARATE_INDEX = true;
  public boolean OPTION_DOCUMENT_TAG_USE = false;
  public boolean OPTION_DOCUMENT_TAG_AUTHOR = false;
  public boolean OPTION_DOCUMENT_TAG_VERSION = false;
  public boolean OPTION_DOCUMENT_TAG_DEPRECATED = true;
  public boolean OPTION_DEPRECATED_LIST = true;
  public String OTHER_OPTIONS = "";
  public String HEAP_SIZE;
  public String LOCALE;
  public boolean OPEN_IN_BROWSER = true;

  private final Project myProject;
  private GenerationOptions myGenerationOptions;

  public static final class GenerationOptions {
    public final String myPackageFQName;
    public final PsiDirectory myDirectory;
    public final boolean isGenerationForPackage;
    public final boolean isGenerationWithSubpackages;

    public GenerationOptions(String packageFQName, PsiDirectory directory, boolean generationForPackage, boolean generationWithSubpackages) {
      myPackageFQName = packageFQName;
      myDirectory = directory;
      isGenerationForPackage = generationForPackage;
      isGenerationWithSubpackages = generationWithSubpackages;
    }
  }

  public void setGenerationOptions(GenerationOptions generationOptions) {
    myGenerationOptions = generationOptions;
  }

  public JavadocConfiguration(Project project) {
    myProject = project;
  }

  public RunProfileState getState(@NotNull final Executor executor, @NotNull final ExecutionEnvironment env) throws ExecutionException {
    return new MyJavaCommandLineState(myProject, myGenerationOptions);
  }

  public String getName() {
    return JavadocBundle.message("javadoc.settings.title");
  }

  public void checkConfiguration() throws RuntimeConfigurationException {
    if (myGenerationOptions == null) {
      throw new RuntimeConfigurationError(JavadocBundle.message("javadoc.settings.not.specified"));
    }
  }

  public JavadocConfigurable createConfigurable() {
    return new JavadocConfigurable(this);
  }

  public Icon getIcon() {
    return null;
  }

  @NotNull
  public Module[] getModules() {
    return Module.EMPTY_ARRAY;
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  private class MyJavaCommandLineState extends CommandLineState {
    private final GenerationOptions myGenerationOptions;
    private final Project myProject;
    @NonNls private static final String INDEX_HTML = "index.html";

    public MyJavaCommandLineState(Project project, GenerationOptions generationOptions) {
      super(null);
      myGenerationOptions = generationOptions;
      myProject = project;
      TextConsoleBuilder builder = TextConsoleBuilderFactory.getInstance().createBuilder(project);
      builder.addFilter(new RegexpFilter(project, "$FILE_PATH$:$LINE$:[^\\^]+\\^"));
      builder.addFilter(new RegexpFilter(project, "$FILE_PATH$:$LINE$: warning - .+$"));
      setConsoleBuilder(builder);
    }

    protected GeneralCommandLine createCommandLine() throws ExecutionException {
      final GeneralCommandLine cmdLine = new GeneralCommandLine();
      final Sdk jdk = PathUtilEx.getAnyJdk(myProject);
      setupExeParams(jdk, cmdLine);
      setupProgramParameters(jdk, cmdLine);
      return cmdLine;
    }

    private void setupExeParams(final Sdk jdk, GeneralCommandLine cmdLine) throws ExecutionException {
      final String jdkPath = jdk != null && jdk.getSdkType() instanceof JavaSdkType ? ((JavaSdkType)jdk.getSdkType()).getBinPath(jdk) : null;
      if (jdkPath == null) {
        throw new CantRunException(JavadocBundle.message("javadoc.generate.no.jdk.path"));
      }
      String versionString = jdk.getVersionString();
      if (HEAP_SIZE != null && HEAP_SIZE.trim().length() != 0) {
        if (versionString.indexOf("1.1") > -1) {
          cmdLine.getParametersList().prepend("-J-mx" + HEAP_SIZE + "m");
        }
        else {
          cmdLine.getParametersList().prepend("-J-Xmx" + HEAP_SIZE + "m");
        }
      }
      cmdLine.setWorkingDirectory(null);
      @NonNls final String javadocExecutableName = File.separator + (SystemInfo.isWindows ? "javadoc.exe" : "javadoc");
      @NonNls String exePath = jdkPath.replace('/', File.separatorChar) + javadocExecutableName;
      if (new File(exePath).exists()) {
        cmdLine.setExePath(exePath);
      } else { //try to use wrapper jdk
        exePath = new File(jdkPath).getParent().replace('/', File.separatorChar) + javadocExecutableName;
        if (!new File(exePath).exists()){
          final File parent = new File(System.getProperty("java.home")).getParentFile(); //try system jre
          exePath = parent.getPath() + File.separator + "bin" + javadocExecutableName;
          if (!new File(exePath).exists()){
            throw new CantRunException(JavadocBundle.message("javadoc.generate.no.jdk.path"));
          }
        }
        cmdLine.setExePath(exePath);
      }
    }

    private void setupProgramParameters(final Sdk jdk, final GeneralCommandLine cmdLine) throws CantRunException {
      @NonNls final ParametersList parameters = cmdLine.getParametersList();

      if (LOCALE != null && LOCALE.length() > 0) {
        parameters.add("-locale");
        parameters.add(LOCALE);
      }

      if (OPTION_SCOPE != null) {
        parameters.add("-" + OPTION_SCOPE);
      }

      if (!OPTION_HIERARCHY) {
        parameters.add("-notree");
      }

      if (!OPTION_NAVIGATOR) {
        parameters.add("-nonavbar");
      }

      if (!OPTION_INDEX) {
        parameters.add("-noindex");
      }
      else if (OPTION_SEPARATE_INDEX) {
        parameters.add("-splitindex");
      }

      if (OPTION_DOCUMENT_TAG_USE) {
        parameters.add("-use");
      }

      if (OPTION_DOCUMENT_TAG_AUTHOR) {
        parameters.add("-author");
      }

      if (OPTION_DOCUMENT_TAG_VERSION) {
        parameters.add("-version");
      }

      if (!OPTION_DOCUMENT_TAG_DEPRECATED) {
        parameters.add("-nodeprecated");
      }
      else if (!OPTION_DEPRECATED_LIST) {
        parameters.add("-nodeprecatedlist");
      }

      parameters.addParametersString(OTHER_OPTIONS);

      final String classPath = jdk.getSdkType() instanceof JavaSdk
                               ? ProjectRootsTraversing.collectRoots(myProject, ProjectRootsTraversing.PROJECT_LIBRARIES).getPathsString()
                               : ProjectRootsTraversing.collectRoots(myProject, ProjectRootsTraversing.LIBRARIES_AND_JDK).getPathsString(); //libraries are included into jdk
      if (classPath.length() >0) {
        parameters.add("-classpath");
        parameters.add(classPath);
      }

      parameters.add("-sourcepath");
      parameters.add(ProjectRootsTraversing.collectRoots(myProject, ProjectRootsTraversing.PROJECT_SOURCES).getPathsString());

      if (OUTPUT_DIRECTORY != null) {
        parameters.add("-d");
        parameters.add(OUTPUT_DIRECTORY.replace('/', File.separatorChar));
      }

      final Collection<String> packages = new HashSet<String>();
      final Collection<String> sources = new HashSet<String>();
      ApplicationManager.getApplication().runReadAction(
        new Runnable(){
          public void run() {
            FileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
            VirtualFile startingFile = null;
            if (myGenerationOptions.isGenerationForPackage) {
              if (!myGenerationOptions.isGenerationWithSubpackages) {
                packages.add(myGenerationOptions.myPackageFQName);
                return;
              }
              startingFile = myGenerationOptions.myDirectory.getVirtualFile();
            }
            MyContentIterator contentIterator = new MyContentIterator(myProject, packages, sources);
            if (startingFile == null) {
              fileIndex.iterateContent(contentIterator);
            }
            else {
              fileIndex.iterateContentUnderDirectory(startingFile, contentIterator);
            }
          }
        }
      );
      if (packages.size() + sources.size() == 0) {
        throw new CantRunException(JavadocBundle.message("javadoc.generate.no.classes.in.selected.packages.error"));
      }
      parameters.addAll(new ArrayList<String>(packages));
      parameters.addAll(new ArrayList<String>(sources));
    }

    protected OSProcessHandler startProcess() throws ExecutionException {
      final OSProcessHandler handler = JavaCommandLineStateUtil.startProcess(createCommandLine());
      ProcessTerminatedListener.attach(handler, myProject, JavadocBundle.message("javadoc.generate.exited"));
      handler.addProcessListener(new ProcessAdapter() {
        public void processTerminated(ProcessEvent event) {
          if (OPEN_IN_BROWSER) {
            File url = new File(OUTPUT_DIRECTORY, INDEX_HTML);
            if (url.exists() && event.getExitCode() == 0) {
              BrowserUtil.launchBrowser(url.getPath());
            }
          }
        }
      });
      return handler;
    }
  }

  private static class MyContentIterator implements ContentIterator {
    private final PsiManager myPsiManager;
    private final Collection<String> myPackages;
    private final Collection<String> mySourceFiles;

    public MyContentIterator(Project project, Collection<String> packages, Collection<String> sources) {
      myPsiManager = PsiManager.getInstance(project);
      myPackages = packages;
      mySourceFiles = sources;
    }

    public boolean processFile(VirtualFile fileOrDir) {
      if (!fileOrDir.isInLocalFileSystem()) {
        return true;
      }
      final Module module = ModuleUtil.findModuleForFile(fileOrDir, myPsiManager.getProject());
      final PsiDirectory directory = getPsiDirectory(fileOrDir);
      if (directory == null) {
        if (!StdFileTypes.JAVA.equals(FileTypeManager.getInstance().getFileTypeByFile(fileOrDir))) {
          return true;
        }
        PsiPackage psiPackage = getPsiPackage(fileOrDir.getParent());
        if (psiPackage != null) {
          if (psiPackage.getQualifiedName().length() == 0  || containsPackagePrefix(module, psiPackage.getQualifiedName())) {
            mySourceFiles.add(PathUtil.getLocalPath(fileOrDir));
          }
        }
        return true;
      }
      else {
        processDirWithJavaFiles(directory, myPackages, mySourceFiles, module);
      }

      return true;
    }

    @Nullable
    private PsiDirectory getPsiDirectory(VirtualFile fileOrDir) {
      return fileOrDir != null ? myPsiManager.findDirectory(fileOrDir) : null;
    }

    @Nullable
    private PsiPackage getPsiPackage(VirtualFile dir) {
      PsiDirectory directory = getPsiDirectory(dir);
      return directory != null ? JavaDirectoryService.getInstance().getPackage(directory) : null;
    }
  }

  private static boolean processDirWithJavaFiles(PsiDirectory dir, Collection<String> packages, final Collection<String> sources, final Module module) {
    PsiFile[] files = dir.getFiles();
    for (PsiFile file : files) {
      if (file instanceof PsiJavaFile) {
        final PsiJavaFile javaFile = (PsiJavaFile)file;
        if (containsPackagePrefix(module, javaFile.getPackageName())) {
          sources.add(PathUtil.getLocalPath(javaFile.getVirtualFile()));
        } else {
          packages.add(javaFile.getPackageName());
        }
        return true;
      }
    }
    return false;
  }

  public static boolean containsPackagePrefix(Module module, String packageFQName) {
    if (module == null) return false;
    final ContentEntry[] contentEntries = ModuleRootManager.getInstance(module).getContentEntries();
    for (ContentEntry contentEntry : contentEntries) {
      final SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
      for (SourceFolder sourceFolder : sourceFolders) {
        final String packagePrefix = sourceFolder.getPackagePrefix();
        final int prefixLength = packagePrefix.length();
        if (prefixLength > 0 && packageFQName.startsWith(packagePrefix)) {
          return true;
        }
      }
    }
    return false;
  }
}
