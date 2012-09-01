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
package com.intellij.execution.console;

import com.intellij.openapi.components.*;
import com.intellij.util.containers.ContainerUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author peter
 */
@State(name="ConsoleFoldingSettings", storages=@Storage( file = StoragePathMacros.APP_CONFIG + "/consoleFolding.xml"))
public class ConsoleFoldingSettings implements PersistentStateComponent<ConsoleFoldingSettings.MyBean> {
  private final List<String> myPositivePatterns = new ArrayList<String>();
  private final List<String> myNegativePatterns = new ArrayList<String>();

  public ConsoleFoldingSettings() {
    for (CustomizableConsoleFoldingBean regexp : CustomizableConsoleFoldingBean.EP_NAME.getExtensions()) {
      patternList(regexp.negate).add(regexp.substring);
    }
  }

  public static ConsoleFoldingSettings getSettings() {
    return ServiceManager.getService(ConsoleFoldingSettings.class);
  }

  public boolean shouldFoldLine(String line) {
    return containsAny(line, myPositivePatterns) && !containsAny(line, myNegativePatterns);
  }

  private static boolean containsAny(String line, List<String> patterns) {
    for (String pattern : patterns) {
      if (line.contains(pattern)) {
        return true;
      }
    }
    return false;
  }

  public List<String> getPositivePatterns() {
    return myPositivePatterns;
  }

  public List<String> getNegativePatterns() {
    return myNegativePatterns;
  }

  public MyBean getState() {
    MyBean result = new MyBean();
    writeDiff(result.addedPositive, result.removedPositive, false);
    writeDiff(result.addedNegative, result.removedNegative, true);
    return result;
  }

  private void writeDiff(List<String> added, List<String> removed, boolean negated) {
    Set<String> baseline = ContainerUtil.newTroveSet();
    for (CustomizableConsoleFoldingBean regexp : CustomizableConsoleFoldingBean.EP_NAME.getExtensions()) {
      if (regexp.negate == negated) {
        baseline.add(regexp.substring);
      }
    }

    final List<String> current = patternList(negated);
    added.addAll(current);
    added.removeAll(baseline);

    baseline.removeAll(current);
    removed.addAll(baseline);
  }

  private List<String> patternList(boolean negated) {
    return negated ? myNegativePatterns : myPositivePatterns;
  }

  public void loadState(MyBean state) {
    myPositivePatterns.clear();
    myNegativePatterns.clear();

    myPositivePatterns.addAll(state.addedPositive);
    myNegativePatterns.addAll(state.addedNegative);

    Set<String> removedPositive = new HashSet<String>(state.removedPositive);
    Set<String> removedNegative = new HashSet<String>(state.removedNegative);

    for (CustomizableConsoleFoldingBean regexp : CustomizableConsoleFoldingBean.EP_NAME.getExtensions()) {
      if (!(regexp.negate ? removedNegative : removedPositive).contains(regexp.substring)) {
        patternList(regexp.negate).add(regexp.substring);
      }
    }
  }

  public static class MyBean {
    public List<String> addedPositive = new ArrayList<String>();
    public List<String> addedNegative = new ArrayList<String>();
    public List<String> removedPositive = new ArrayList<String>();
    public List<String> removedNegative = new ArrayList<String>();
  }

}
