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
package org.jetbrains.idea.maven.embedder;

import org.codehaus.plexus.logging.Logger;

public class MavenExecutionOptions {
  public enum LoggingLevel {
    DEBUG("Debug", Logger.LEVEL_DEBUG),
    INFO("Info", Logger.LEVEL_INFO),
    WARN("Warn", Logger.LEVEL_WARN),
    ERROR("Error", Logger.LEVEL_ERROR),
    FATAL("Fatal", Logger.LEVEL_FATAL),
    DISABLED("Disabled", Logger.LEVEL_DISABLED);

    private final String myDisplayString;
    private final int myLevel;

    private LoggingLevel(String displayString, int level) {
      myDisplayString = displayString;
      myLevel = level;
    }

    public String getDisplayString() {
      return myDisplayString;
    }

    public int getLevel() {
      return myLevel;
    }
  }

  public enum FailureMode {
    FAST("Fail Fast", "--fail-fast"), AT_END("Fail At End", "--fail-at-end"), NEVER("Never Fail", "--fail-never");

    private final String myDisplayString;
    private final String myCommandLineOption;

    private FailureMode(String displayString, String commandLineOption) {
      myDisplayString = displayString;
      myCommandLineOption = commandLineOption;
    }

    public String getDisplayString() {
      return myDisplayString;
    }

    public String getCommandLineOption() {
      return myCommandLineOption;
    }
  }

  public enum ChecksumPolicy {
    NOT_SET("No Global Policy", ""),
    FAIL("Fail", "--strict-checksums"),
    WARN("Warn", "--lax-checksums");

    private final String myDisplayString;
    private final String myCommandLineOption;

    private ChecksumPolicy(String displayString, String commandLineOption) {
      myDisplayString = displayString;
      myCommandLineOption = commandLineOption;
    }

    public String getDisplayString() {
      return myDisplayString;
    }

    public String getCommandLineOption() {
      return myCommandLineOption;
    }
  }

  public enum SnapshotUpdatePolicy {
    ALWAYS_UPDATE("Always Update", "--update-snapshots"), DO_NOT_UPDATE("Do Not Update", "");

    private final String myDisplayString;
    private final String myCommandLineOption;

    private SnapshotUpdatePolicy(String displayString, String commandLineOption) {
      myDisplayString = displayString;
      myCommandLineOption = commandLineOption;
    }

    public String getDisplayString() {
      return myDisplayString;
    }

    public String getCommandLineOption() {
      return myCommandLineOption;
    }
  }

  public enum PluginUpdatePolicy {
    UPDATE("Check For Updates", "--check-plugin-updates"), DO_NOT_UPDATE("Do Not Update", "--no-plugin-updates");

    private final String myDisplayString;
    private final String myCommandLineOption;

    private PluginUpdatePolicy(String displayString, String commandLineOption) {
      myDisplayString = displayString;
      myCommandLineOption = commandLineOption;
    }

    public String getDisplayString() {
      return myDisplayString;
    }

    public String getCommandLineOption() {
      return myCommandLineOption;
    }
  }
}
