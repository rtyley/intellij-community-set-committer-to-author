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

/*
 * @author max
 */
package com.intellij.openapi.updateSettings.impl;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.util.BuildNumber;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class BuildInfo implements Comparable<BuildInfo> {
  private final BuildNumber myNumber;
  private final String myName;
  private final String myMessage;
  private final List<PatchInfo> myPatches;

  public BuildInfo(Element node) {
    myNumber = BuildNumber.fromString(node.getAttributeValue("number"));
    myName = node.getAttributeValue("name");

    myPatches = new ArrayList<PatchInfo>();
    for (Object patchNode : node.getChildren("patch")) {
      myPatches.add(new PatchInfo((Element)patchNode));
    }

    Element messageTag = node.getChild("message");
    myMessage = messageTag != null ? messageTag.getValue() : "";
  }

  public int compareTo(BuildInfo o) {
    return myNumber.compareTo(o.myNumber);
  }

  public BuildNumber getNumber() {
    return myNumber;
  }

  public String getName() {
    return myName != null ? myName : "";
  }

  public String getMessage() {
    return myMessage;
  }

  @Nullable
  public PatchInfo findPatchForCurrentBuild() {
    BuildNumber currentBuild = ApplicationInfo.getInstance().getBuild();
    for (PatchInfo each : myPatches) {
      if (each.getFromBuild().equals(currentBuild)) return each;
    }
    return null;
  }
}
