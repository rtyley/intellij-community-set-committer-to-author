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

package org.jetbrains.plugins.groovy.compiler.generator;

import com.intellij.compiler.impl.TranslatingCompilerFilesMonitor;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerPaths;
import com.intellij.openapi.compiler.ex.CompileContextEx;
import com.intellij.openapi.compiler.options.ExcludedEntriesConfiguration;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Chunk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.compiler.GroovyCompilerBase;
import org.jetbrains.plugins.groovy.compiler.GroovyCompilerConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
 */
public class GroovycStubGenerator extends GroovyCompilerBase {

  public GroovycStubGenerator(Project project) {
    super(project);
  }

  @Override
  public void compile(CompileContext compileContext, Chunk<Module> moduleChunk, VirtualFile[] virtualFiles, OutputSink sink) {
    final CompileScope scope = compileContext.getCompileScope();
    final VirtualFile[] javaFiles = scope.getFiles(StdFileTypes.JAVA, true);
    if (javaFiles.length == 0) {
      return;
    }

    boolean hasJava = false;
    for (VirtualFile javaFile : javaFiles) {
      final Module module = ModuleUtil.findModuleForFile(javaFile, myProject);
      if (module != null && moduleChunk.containsNode(module)) {
        hasJava = true;
        break;
      }
    }

    if (!hasJava) {
      return;
    }

    final ExcludedEntriesConfiguration excluded = GroovyCompilerConfiguration.getExcludeConfiguration(myProject);

    List<VirtualFile> total = new ArrayList<VirtualFile>();
    for (final VirtualFile virtualFile : virtualFiles) {
      if (!excluded.isExcluded(virtualFile)) {
        if (virtualFile.getFileType() == GroovyFileType.GROOVY_FILE_TYPE) {
          total.add(virtualFile);
        }
      }
    }

    if (total.isEmpty()) {
      return;
    }

    super.compile(compileContext, moduleChunk, VfsUtil.toVirtualFileArray(total), sink);
  }

  @Override
  protected void compileFiles(CompileContext compileContext, Module module,
                              final List<VirtualFile> toCompile, OutputSink sink, boolean tests) {
    final String rootPath = CompilerPaths.getGeneratedDataDirectory(myProject) + "/groovyStubs/";
    final File outDir = new File(rootPath + myProject.getLocationHash() + "/" + module.getName() + "/" + (tests ? "tests" : "production") + "/");
    outDir.mkdirs();

    final VirtualFile tempOutput = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(outDir);
    assert tempOutput != null;
    cleanDirectory(tempOutput);

    ((CompileContextEx)compileContext).assignModule(tempOutput, module, tests);

    if (GroovyCompilerConfiguration.getInstance(myProject).isUseGroovycStubs()) {
      runGroovycCompiler(compileContext, module, toCompile, true, tempOutput, sink, tests);
    } else {
      final GroovyToJavaGenerator generator = new GroovyToJavaGenerator(myProject, compileContext);
      for (VirtualFile file : toCompile) {
        final String outPath = tempOutput.getPath();
        final List<String> relPaths = generator.generateItems(file, tempOutput);
        final List<String> fullPaths = new ArrayList<String>();
        for (String relPath : relPaths) {
          fullPaths.add(outPath + "/" + relPath);
        }
        addStubsToCompileScope(fullPaths, compileContext, module);
      }
    }
  }

  private void cleanDirectory(final VirtualFile dir) {
    new WriteCommandAction(myProject) {
      protected void run(Result result) throws Throwable {
        deleteChildrenRecursively(dir);
      }

      private void deleteChildrenRecursively(final VirtualFile dir) throws IOException {
        for (final VirtualFile child : dir.getChildren()) {
          if (child.isDirectory()) {
            deleteChildrenRecursively(child);
          }
          TranslatingCompilerFilesMonitor.removeSourceInfo(child);
          try {
            child.delete(this);
          }
          catch (IOException ignored) {
            //may be a leaked handle from some non-completely terminated compiler process, or compiler caches, or something else
            //not a big deal, we'll delete it next time
          }
        }
      }
    }.execute();
  }

  @NotNull
  public String getDescription() {
    return "Groovy to java source code generator";
  }

  public boolean validateConfiguration(CompileScope scope) {
    return true;
  }

}
