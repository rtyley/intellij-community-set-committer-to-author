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
package org.jetbrains.jps.incremental.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.asm4.ClassReader;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.builders.java.dependencyView.Callbacks;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.javac.OutputFileConsumer;
import org.jetbrains.jps.javac.OutputFileObject;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;

/**
* @author Eugene Zhuravlev
*         Date: 2/16/12
*/
class OutputFilesSink implements OutputFileConsumer {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.java.OutputFilesSink");

  private final CompileContext myContext;
  private final ModuleLevelBuilder.OutputConsumer myOutputConsumer;
  private final Callbacks.Backend myMappingsCallback;
  private final String myChunkName;
  private final Set<File> mySuccessfullyCompiled = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);

  public OutputFilesSink(CompileContext context,
                         ModuleLevelBuilder.OutputConsumer outputConsumer,
                         Callbacks.Backend callback,
                         String chunkName) {
    myContext = context;
    myOutputConsumer = outputConsumer;
    myMappingsCallback = callback;
    myChunkName = "[" +chunkName + "]";
  }

  public void save(final @NotNull OutputFileObject fileObject) {
    final BinaryContent content = fileObject.getContent();
    final File srcFile = fileObject.getSourceFile();
    boolean isTemp = false;
    final JavaFileObject.Kind outKind = fileObject.getKind();

    if (srcFile != null && content != null) {
      final String sourcePath = FileUtil.toSystemIndependentName(srcFile.getPath());
      final JavaSourceRootDescriptor rootDescriptor = myContext.getProjectDescriptor().getBuildRootIndex().findJavaRootDescriptor(myContext, srcFile);
      if (rootDescriptor != null) {
        isTemp = rootDescriptor.isTemp;
        if (!isTemp) {
          // first, handle [src->output] mapping and register paths for files_generated event
          try {
            if (outKind == JavaFileObject.Kind.CLASS) {
              myOutputConsumer.registerCompiledClass(rootDescriptor.target, new CompiledClass(fileObject.getFile(), srcFile, fileObject.getClassName(), content)); // todo: avoid array copying?
            }
            else {
              myOutputConsumer.registerOutputFile(rootDescriptor.target, fileObject.getFile(), Collections.<String>singleton(sourcePath));
            }
          }
          catch (IOException e) {
            myContext.processMessage(new CompilerMessage(JavaBuilder.BUILDER_NAME, e));
          }
        }
      }

      if (!isTemp && outKind == JavaFileObject.Kind.CLASS && !Utils.errorsDetected(myContext)) {
        // register in mappings any non-temp class file
        final ClassReader reader = new ClassReader(content.getBuffer(), content.getOffset(), content.getLength());
        myMappingsCallback.associate(FileUtil.toSystemIndependentName(fileObject.getFile().getPath()), sourcePath, reader);
      }
    }

    if (!isTemp && outKind != JavaFileObject.Kind.CLASS && outKind != JavaFileObject.Kind.SOURCE) {
      try {
        // this should be a generated resource
        FSOperations.markDirty(myContext, fileObject.getFile());
      }
      catch (IOException e) {
        LOG.info(e);
      }
    }

    try {
      writeToDisk(fileObject, isTemp);
    }
    catch (IOException e) {
      myContext.processMessage(new CompilerMessage(JavaBuilder.BUILDER_NAME, BuildMessage.Kind.ERROR, e.getMessage()));
    }
  }

  public Set<File> getSuccessfullyCompiled() {
    return Collections.unmodifiableSet(mySuccessfullyCompiled);
  }

  private void writeToDisk(@NotNull OutputFileObject fileObject, boolean isTemp) throws IOException {
    myContext.processMessage(new ProgressMessage("Writing classes... " + myChunkName));

    final File file = fileObject.getFile();
    final BinaryContent content = fileObject.getContent();
    if (content == null) {
      throw new IOException("Missing content for file " + file);
    }

    content.saveToFile(file);

    final File source = fileObject.getSourceFile();
    if (!isTemp && source != null) {
      mySuccessfullyCompiled.add(source);
      //final String className = fileObject.getClassName();
      //if (className != null) {
      //  myContext.processMessage(new ProgressMessage("Compiled " + className));
      //}
    }
  }

  public void markError(@NotNull final File sourceFile) {
    mySuccessfullyCompiled.remove(sourceFile);
  }
  public void markError(@NotNull final Set<File> problematic) {
    mySuccessfullyCompiled.removeAll(problematic);
  }
}
