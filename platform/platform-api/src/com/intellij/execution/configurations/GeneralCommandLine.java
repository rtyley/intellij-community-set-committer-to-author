/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.execution.configurations;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessNotCreatedException;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * OS-independent way of executing external processes with complex parameters.
 *
 * Main idea of the class is to accept parameters "as-is", just as they should look to an external process, and quote/escape them
 * as required by the underlying platform.
 *
 * todo: check help
 */
public class GeneralCommandLine {
  private String myExePath = null;
  private File myWorkDirectory = null;
  private Map<String, String> myEnvParams = null;
  private boolean myPassParentEnvironment = true;
  private final ParametersList myProgramParams = new ParametersList();
  private Charset myCharset = CharsetToolkit.getDefaultSystemCharset();
  private boolean myRedirectErrorStream = false;

  public GeneralCommandLine() { }

  public GeneralCommandLine(final String... command) {
    this(Arrays.asList(command));
  }

  public GeneralCommandLine(final List<String> command) {
    final int size = command.size();
    if (size > 0) {
      setExePath(command.get(0));
      if (size > 1) {
        addParameters(command.subList(1, size));
      }
    }
  }

  public String getExePath() {
    return myExePath;
  }

  public void setExePath(@NotNull @NonNls final String exePath) {
    myExePath = exePath.trim();
  }

  public File getWorkDirectory() {
    return myWorkDirectory;
  }

  public void setWorkDirectory(@Nullable @NonNls final String path) {
    setWorkDirectory(path != null ? new File(path) : null);
  }

  public void setWorkDirectory(@Nullable final File workDirectory) {
    myWorkDirectory = workDirectory;
  }

  /**
   * @deprecated use {@link #setWorkDirectory(java.io.File)} (to remove in IDEA 12).
   */
  public void setWorkingDirectory(@Nullable final File workDirectory) {
    setWorkDirectory(workDirectory);
  }

  @Nullable
  public Map<String, String> getEnvParams() {
    return myEnvParams;
  }

  public void setEnvParams(@Nullable final Map<String, String> envParams) {
    myEnvParams = envParams;
  }

  public void setPassParentEnvs(final boolean passParentEnvironment) {
    myPassParentEnvironment = passParentEnvironment;
  }

  public void addParameters(final String... parameters) {
    for (String parameter : parameters) {
      addParameter(parameter);
    }
  }

  public void addParameters(@NotNull final List<String> parameters) {
    for (final String parameter : parameters) {
      addParameter(parameter);
    }
  }

  public void addParameter(@NotNull @NonNls final String parameter) {
    myProgramParams.add(parameter);
  }

  public ParametersList getParametersList() {
    return myProgramParams;
  }

  @NotNull
  public Charset getCharset() {
    return myCharset;
  }

  public void setCharset(@NotNull final Charset charset) {
    myCharset = charset;
  }

  public void setRedirectErrorStream(final boolean redirectErrorStream) {
    myRedirectErrorStream = redirectErrorStream;
  }

  /**
   * @deprecated please use {@link #getCommandLineString()} (to remove in IDEA 12).
   */
  @SuppressWarnings("UnusedDeclaration")
  public String getCommandLineParams() {
    return getCommandLineString();
  }

  /**
   * Returns string representation of this command line.<br/>
   * Warning: resulting string is not OS-dependent - <b>do not</b> use it for executing this command line.
   *
   * @return single-string representation of this command line.
   */
  public String getCommandLineString() {
    return getCommandLineString(null);
  }

  /**
   * Returns string representation of this command line.<br/>
   * Warning: resulting string is not OS-dependent - <b>do not</b> use it for executing this command line.
   *
   * @param exeName use this executable name instead of given by {@link #setExePath(String)}
   * @return single-string representation of this command line.
   */
  public String getCommandLineString(@Nullable final String exeName) {
    final List<String> commands = new ArrayList<String>();
    if (exeName != null) {
      commands.add(exeName);
    }
    else if (myExePath != null) {
      commands.add(FileUtil.toSystemDependentName(myExePath));
    }
    else {
      commands.add("<null>");
    }
    commands.addAll(myProgramParams.getList());
    return ParametersList.join(commands);
  }

  /**
   * Returns a list of command and its parameters prepared in OS-dependent way to be executed by e.g. {@link Runtime#exec(String[])}.
   *
   * @deprecated this method is not intended for internal use (to remove in IDEA 12).
   */
  public String[] getCommands() {
    return prepareCommands();
  }

  /**
   * @deprecated use {@link #addParameter(String)} and {@link #addParameters(String...)} methods for adding parameters -
   * any quoting needed will be done on {@link #createProcess()} (to remove in IDEA 12).
   */
  @SuppressWarnings("UnusedDeclaration")
  public static String quoteParameter(final String parameter) {
    return parameter;
  }

  /**
   * @deprecated use {@link #addParameter(String)} and {@link #addParameters(String...)} methods for adding parameters -
   * any quoting needed will be done on {@link #createProcess()} (to remove in IDEA 12).
   */
  @SuppressWarnings("UnusedDeclaration")
  public static String quote(final String parameter) {
    return parameter;
  }

  public Process createProcess() throws ExecutionException {
    checkWorkingDirectory();

    final String[] commands = prepareCommands();
    if (StringUtil.isEmptyOrSpaces(commands[0])) {
      throw new ExecutionException(IdeBundle.message("run.configuration.error.executable.not.specified"));
    }

    try {
      final ProcessBuilder builder = new ProcessBuilder(commands);
      final Map<String, String> environment = builder.environment();
      setupEnvironment(environment);
      builder.directory(myWorkDirectory);
      builder.redirectErrorStream(myRedirectErrorStream);
      return builder.start();
    }
    catch (IOException e) {
      throw new ProcessNotCreatedException(e.getMessage(), e, this);
    }
  }

  private void checkWorkingDirectory() throws ExecutionException {
    if (myWorkDirectory == null) {
      return;
    }
    if (!myWorkDirectory.exists()) {
      throw new ExecutionException(
        IdeBundle.message("run.configuration.error.working.directory.does.not.exist", myWorkDirectory.getAbsolutePath()));
    }
    if (!myWorkDirectory.isDirectory()) {
      throw new ExecutionException(IdeBundle.message("run.configuration.error.working.directory.not.directory"));
    }
  }

  private String[] prepareCommands() {
    final List<String> parameters = myProgramParams.getList();
    final String[] result = new String[parameters.size() + 1];
    result[0] = prepareCommand(myExePath != null ? FileUtil.toSystemDependentName(myExePath) : null);
    for (int i = 0; i < parameters.size(); i++) {
      result[i + 1] = prepareCommand(parameters.get(i));
    }
    return result;
  }

  private static String prepareCommand(String parameter) {
    // AFAIK, the only thing needed is escaping double quotes on Windows
    if (SystemInfo.isWindows) {
      if (parameter.contains("\"")) {
        parameter = StringUtil.replace(parameter, "\"", "\\\"");
      }
    }
    return parameter;
  }

  private void setupEnvironment(final Map<String, String> environment) {
    if (!myPassParentEnvironment) {
      environment.clear();
    }
    if (myEnvParams != null) {
      environment.putAll(myEnvParams);
    }
  }

  @Override
  public String toString() {
    return myExePath + " " + myProgramParams;
  }
}
