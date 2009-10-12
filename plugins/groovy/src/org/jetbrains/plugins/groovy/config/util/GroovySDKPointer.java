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

package org.jetbrains.plugins.groovy.config.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyIcons;

import javax.swing.*;

/**
 * Pointer to added but not registered Groovy SDK
 *
 * @author ilyas
 */
public class GroovySDKPointer implements AbstractSDK {

  private final String myGroovyLibraryName;
  private final String myPathToLibrary;
  private final String myVersion;
  private final boolean myProjectLib;

  public GroovySDKPointer(@NotNull String name, @NotNull String path, String version, final boolean isProjectLib) {
    myGroovyLibraryName = name;
    myPathToLibrary = path;
    myVersion = version;
    myProjectLib = isProjectLib;
  }

  public String getLibraryName() {
    return myGroovyLibraryName;
  }

  public boolean isProjectLib() {
    return myProjectLib;
  }

  public String getPresentation() {
    return " (Groovy version \"" + getVersion() + "\")";
  }

  public String getPath() {
    return myPathToLibrary;
  }

  public Icon getIcon() {
    return GroovyIcons.GROOVY_SDK;
  }

  public String getVersion() {
    return myVersion;
  }
}
