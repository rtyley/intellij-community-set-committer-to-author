/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight;

import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:johnnyclark@gmail.com">Johnny Clark</a>
 *         Creation Date: 8/3/12
 */
@State(
  name = "ConditionCheckManager",
  storages = {
    @Storage(id = "dir", file = StoragePathMacros.PROJECT_CONFIG_DIR + "/checker.xml", scheme = StorageScheme.DIRECTORY_BASED),
    @Storage(file = StoragePathMacros.PROJECT_FILE)
  }
)
public class ConditionCheckManager implements PersistentStateComponent<ConditionCheckManager.State> {
  @SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"}) private State state;
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.ConditionCheckManager");

  private List<ConditionChecker> myIsNullCheckMethods = new ArrayList<ConditionChecker>();
  private List<ConditionChecker> myIsNotNullCheckMethods = new ArrayList<ConditionChecker>();

  private List<ConditionChecker> myAssertIsNullMethods = new ArrayList<ConditionChecker>();
  private List<ConditionChecker> myAssertIsNotNullMethods = new ArrayList<ConditionChecker>();

  private List<ConditionChecker> myAssertTrueMethods = new ArrayList<ConditionChecker>();
  private List<ConditionChecker> myAssertFalseMethods = new ArrayList<ConditionChecker>();

  public static ConditionCheckManager getInstance(Project project) {
    return ServiceManager.getService(project, ConditionCheckManager.class);
  }

  public void setIsNullCheckMethods(List<ConditionChecker> methodConditionChecks) {
    myIsNullCheckMethods.clear();
    myIsNullCheckMethods.addAll(methodConditionChecks);
  }

  public void setIsNotNullCheckMethods(List<ConditionChecker> methodConditionChecks) {
    myIsNotNullCheckMethods.clear();
    myIsNotNullCheckMethods.addAll(methodConditionChecks);
  }

  public void setAssertIsNullMethods(List<ConditionChecker> methodConditionChecks) {
    myAssertIsNullMethods.clear();
    myAssertIsNullMethods.addAll(methodConditionChecks);
  }

  public void setAssertIsNotNullMethods(List<ConditionChecker> methodConditionChecks) {
    myAssertIsNotNullMethods.clear();
    myAssertIsNotNullMethods.addAll(methodConditionChecks);
  }

  public void setAssertTrueMethods(List<ConditionChecker> psiMethodWrappers) {
    myAssertTrueMethods.clear();
    myAssertTrueMethods.addAll(psiMethodWrappers);
  }

  public void setAssertFalseMethods(List<ConditionChecker> psiMethodWrappers) {
    myAssertFalseMethods.clear();
    myAssertFalseMethods.addAll(psiMethodWrappers);
  }

  public List<ConditionChecker> getIsNullCheckMethods() {
    return myIsNullCheckMethods;
  }

  public List<ConditionChecker> getIsNotNullCheckMethods() {
    return myIsNotNullCheckMethods;
  }

  public List<ConditionChecker> getAssertIsNullMethods() {
    return myAssertIsNullMethods;
  }

  public List<ConditionChecker> getAssertIsNotNullMethods() {
    return myAssertIsNotNullMethods;
  }

  public List<ConditionChecker> getAssertFalseMethods() {
    return myAssertFalseMethods;
  }

  public List<ConditionChecker> getAssertTrueMethods() {
    return myAssertTrueMethods;
  }

  public static class State {
    public List<String> myIsNullCheckMethods = new ArrayList<String>();
    public List<String> myIsNotNullCheckMethods = new ArrayList<String>();
    public List<String> myAssertIsNullMethods = new ArrayList<String>();
    public List<String> myAssertIsNotNullMethods = new ArrayList<String>();
    public List<String> myAssertTrueMethods = new ArrayList<String>();
    public List<String> myAssertFalseMethods = new ArrayList<String>();
  }

  @Override
  public State getState() {
    State state = new State();

    loadMethodChecksToState(state.myIsNullCheckMethods, myIsNullCheckMethods);
    loadMethodChecksToState(state.myIsNotNullCheckMethods, myIsNotNullCheckMethods);
    loadMethodChecksToState(state.myAssertIsNullMethods, myAssertIsNullMethods);
    loadMethodChecksToState(state.myAssertIsNotNullMethods, myAssertIsNotNullMethods);
    loadMethodChecksToState(state.myAssertTrueMethods, myAssertTrueMethods);
    loadMethodChecksToState(state.myAssertFalseMethods, myAssertFalseMethods);

    return state;
  }

  private static void loadMethodChecksToState(List<String> listToLoadTo, List<ConditionChecker> listToLoadFrom) {
    for (ConditionChecker checker : listToLoadFrom) {
      listToLoadTo.add(checker.toString());
    }
  }

  @Override
  public void loadState(State state) {
    this.state = state;
    loadMethods(myIsNullCheckMethods, state.myIsNullCheckMethods, ConditionChecker.Type.IS_NULL_METHOD);
    loadMethods(myIsNotNullCheckMethods, state.myIsNotNullCheckMethods, ConditionChecker.Type.IS_NOT_NULL_METHOD);
    loadMethods(myAssertIsNullMethods, state.myAssertIsNullMethods, ConditionChecker.Type.ASSERT_IS_NULL_METHOD);
    loadMethods(myAssertIsNotNullMethods, state.myAssertIsNotNullMethods, ConditionChecker.Type.ASSERT_IS_NOT_NULL_METHOD);
    loadMethods(myAssertTrueMethods, state.myAssertTrueMethods, ConditionChecker.Type.ASSERT_TRUE_METHOD);
    loadMethods(myAssertFalseMethods, state.myAssertFalseMethods, ConditionChecker.Type.ASSERT_FALSE_METHOD);
  }

  public void loadMethods(List<ConditionChecker> listToLoadTo, List<String> listToLoadFrom, ConditionChecker.Type type){
    listToLoadTo.clear();
    for (String setting : listToLoadFrom) {
      try {
        listToLoadTo.add(new ConditionChecker.FromConfigBuilder(setting, type).build());
      } catch (Exception e) {
        LOG.error("Problem occurred while attempting to load Condition Check from configuration file. " + e.getMessage());
      }
    }
  }

  public static boolean isMethod(@NotNull PsiMethod psiMethod, List<ConditionChecker> checkers) {
    for (ConditionChecker checker : checkers) {
      if (checker.matchesPsiMethod(psiMethod)) {
        return true;
      }
    }
    return false;
  }

  public static boolean isCheck(PsiMethod psiMethod) {
    ConditionCheckManager manager = getInstance(psiMethod.getProject());
    return isMethod(psiMethod, manager.getIsNullCheckMethods()) ||
           isMethod(psiMethod, manager.getIsNotNullCheckMethods()) ||
           isMethod(psiMethod, manager.getAssertIsNullMethods()) ||
           isMethod(psiMethod, manager.getAssertIsNotNullMethods()) ||
           isAssertTrueCheckMethod(psiMethod) ||
           isAssertFalseCheckMethod(psiMethod);
  }

  public static boolean isNullCheckMethod(PsiMethod psiMethod) {
    return methodMatches(psiMethod, getInstance(psiMethod.getProject()).getIsNullCheckMethods());
  }

  public static boolean isNotNullCheckMethod(PsiMethod psiMethod) {
    return methodMatches(psiMethod, getInstance(psiMethod.getProject()).getIsNotNullCheckMethods());
  }

  public static boolean isAssertIsNullCheckMethod(PsiMethod psiMethod) {
    return methodMatches(psiMethod, getInstance(psiMethod.getProject()).getAssertIsNullMethods());
  }

  public static boolean isAssertIsNotNullCheckMethod(PsiMethod psiMethod) {
    return methodMatches(psiMethod, getInstance(psiMethod.getProject()).getAssertIsNotNullMethods());
  }

  public static boolean isAssertTrueCheckMethod(PsiMethod psiMethod) {
    return methodMatches(psiMethod, getInstance(psiMethod.getProject()).getAssertTrueMethods());
  }

  public static boolean isAssertFalseCheckMethod(PsiMethod psiMethod) {
    return methodMatches(psiMethod, getInstance(psiMethod.getProject()).getAssertFalseMethods());
  }

  public static boolean isNullCheckMethod(PsiMethod psiMethod, int paramIndex) {
    return methodMatches(psiMethod, paramIndex, getInstance(psiMethod.getProject()).getIsNullCheckMethods());
  }

  public static boolean isNotNullCheckMethod(PsiMethod psiMethod, int paramIndex) {
    return methodMatches(psiMethod, paramIndex, getInstance(psiMethod.getProject()).getIsNotNullCheckMethods());
  }

  public static boolean isAssertIsNullCheckMethod(PsiMethod psiMethod, int paramIndex) {
    return methodMatches(psiMethod, paramIndex, getInstance(psiMethod.getProject()).getAssertIsNullMethods());
  }

  public static boolean isAssertIsNotNullCheckMethod(PsiMethod psiMethod, int paramIndex) {
    return methodMatches(psiMethod, paramIndex, getInstance(psiMethod.getProject()).getAssertIsNotNullMethods());
  }

  public static boolean isAssertTrueCheckMethod(PsiMethod psiMethod, int paramIndex) {
    return methodMatches(psiMethod, paramIndex, getInstance(psiMethod.getProject()).getAssertTrueMethods());
  }

  public static boolean isAssertFalseCheckMethod(PsiMethod psiMethod, int paramIndex) {
    return methodMatches(psiMethod, paramIndex, getInstance(psiMethod.getProject()).getAssertFalseMethods());
  }

  public static boolean methodMatches(PsiMethod psiMethod, List<ConditionChecker> checkers) {
    for (ConditionChecker checker : checkers) {
      if (checker.matchesPsiMethod(psiMethod))
        return true;
    }
    return false;
  }

  public static boolean methodMatches(PsiMethod psiMethod, int paramIndex, List<ConditionChecker> checkers) {
    for (ConditionChecker checker : checkers) {
      if (checker.matchesPsiMethod(psiMethod, paramIndex))
        return true;
    }
    return false;
  }
}
