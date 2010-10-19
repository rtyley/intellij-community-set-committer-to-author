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
package org.jetbrains.android.compiler.tools;

import com.android.sdklib.IAndroidTarget;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.CommandLineBuilder;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.util.containers.HashMap;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class AndroidDx {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.compiler.tools.AndroidDx");

  @NonNls private static final String DEX_MAIN = "com.android.dx.command.dexer.Main";

  @SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
  public Map<CompilerMessageCategory, List<String>> execute(@NotNull Module module,
                                                            @NotNull IAndroidTarget target,
                                                            @NotNull String outputDir,
                                                            @NotNull String... compileTargets) {
    String outFile = outputDir + File.separatorChar + "classes.dex";

    final Map<CompilerMessageCategory, List<String>> messages = new HashMap<CompilerMessageCategory, List<String>>(2);
    messages.put(CompilerMessageCategory.ERROR, new ArrayList<String>());
    messages.put(CompilerMessageCategory.INFORMATION, new ArrayList<String>());
    messages.put(CompilerMessageCategory.WARNING, new ArrayList<String>());

    String dxJarPath = target.getPath(IAndroidTarget.DX_JAR);
    File dxJar = new File(dxJarPath);
    if (!dxJar.isFile()) {
      messages.get(CompilerMessageCategory.ERROR).add(AndroidBundle.message("android.file.not.exist.error", dxJarPath));
      return messages;
    }

    JavaParameters parameters = new JavaParameters();
    Sdk sdk = ModuleRootManager.getInstance(module).getSdk();

    // dex runs after simple java compilation, so JDK must be specified
    assert sdk != null;

    parameters.setJdk(sdk);
    parameters.setMainClass(DEX_MAIN);
    ParametersList params = parameters.getProgramParametersList();
    //params.add("--verbose");
    params.add("--output=" + outFile);
    params.addAll(compileTargets);
    parameters.getVMParametersList().add("-Xmx1024M");
    parameters.getClassPath().add(dxJar);
    Process process = null;
    try {
      process = CommandLineBuilder.createFromJavaParameters(parameters, true).createProcess();
    }
    catch (ExecutionException e) {
      messages.get(CompilerMessageCategory.ERROR).add("ExecutionException: " + e.getMessage());
      LOG.info(e);
    }

    final OSProcessHandler handler = new OSProcessHandler(process, "");
    handler.addProcessListener(new ProcessAdapter() {
      @Override
      public void onTextAvailable(ProcessEvent event, Key outputType) {
        String[] msgs = event.getText().split("\\n");
        for (String msg : msgs) {
          msg = msg.trim();
          if (outputType == ProcessOutputTypes.STDERR) {
            if (msg.toLowerCase().startsWith("warning")) {
              messages.get(CompilerMessageCategory.WARNING).add(msg);
            }
            else {
              messages.get(CompilerMessageCategory.ERROR).add(msg);
            }
          }
          else if (outputType == ProcessOutputTypes.STDOUT) {
            if (!msg.toLowerCase().startsWith("processing")) {
              messages.get(CompilerMessageCategory.INFORMATION).add(msg);
            }
          }
          LOG.info(msg);
        }
      }
    });

    handler.startNotify();
    handler.waitFor();

    return messages;
  }
}
