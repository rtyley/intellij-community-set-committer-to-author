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
package org.jetbrains.plugins.groovy.refactoring.convertToJava;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiMethod;
import com.intellij.util.containers.hash.HashMap;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;

import java.util.*;

import static org.jetbrains.plugins.groovy.refactoring.convertToJava.GenerationUtil.suggestMethodName;

class ExpressionContext implements Cloneable {
  List<String> myStatements = new ArrayList<String>();
  Set<String> myUsedVarNames;
  LocalVarAnalyzer.Result analyzedVars = LocalVarAnalyzer.initialResult();
  TypeProvider typeProvider;

  Project project;
  private Map<String, Boolean> myProps = new HashMap<String, Boolean>();
  private static final String myShouldInsertCurlyBrackets = "shouldInsertCurly";
  private static final String myInAnonymousContext = "inAnonymousContext";
  private Ref<String> myRefSetterName = new Ref<String>(null);

  private Map<PsiMethod, String> setters;

  private ExpressionContext(Project project, Set<String> usedVarNames, Map<PsiMethod, String> setters) {
    this.project = project;
    myUsedVarNames = usedVarNames;
    this.setters = setters;
  }

  ExpressionContext(Project project) {
    this(project, new HashSet<String>(), new HashMap<PsiMethod, String>());
    typeProvider = new TypeProvider();
  }

  @Override
  public Object clone() {
    return copy();
  }

  ExpressionContext copy() {
    final ExpressionContext expressionContext = new ExpressionContext(project, myUsedVarNames, setters);
    expressionContext.myProps.putAll(myProps);
    expressionContext.analyzedVars = analyzedVars;
    expressionContext.typeProvider = typeProvider;
    expressionContext.myRefSetterName = myRefSetterName;
    return expressionContext;
  }

  ExpressionContext extend() {
    final HashSet<String> usedVarNames = new HashSet<String>();
    usedVarNames.addAll(myUsedVarNames);
    final ExpressionContext expressionContext = new ExpressionContext(project, usedVarNames, setters);
    expressionContext.myProps.putAll(myProps);
    expressionContext.analyzedVars = analyzedVars;
    expressionContext.typeProvider = typeProvider;
    expressionContext.myRefSetterName = myRefSetterName;
    return expressionContext;
  }

  public void setInsertCurlyBrackets() {
    myProps.put(myShouldInsertCurlyBrackets, true);
  }

  private boolean getProp(String name) {
    final Boolean aBoolean = myProps.get(name);
    return aBoolean != null && aBoolean.booleanValue();
  }

  public boolean shouldInsertCurlyBrackets() {
    return getProp(myShouldInsertCurlyBrackets);
  }

  public boolean isInAnonymousContext() {
    return getProp(myInAnonymousContext);
  }

  public void setInAnonymousContext(boolean inAnonymousContext) {
    myProps.put(myInAnonymousContext, inAnonymousContext);
  }

  public void searchForLocalVarsToWrap(GroovyPsiElement root) {
    analyzedVars = LocalVarAnalyzer.searchForVarsToWrap(root, analyzedVars, this);
  }

  public String getRefSetterName() {
    return myRefSetterName.get();
  }

  public String getRefSetterName(GroovyPsiElement context) {
    if (myRefSetterName.isNull()) {
      myRefSetterName.set(suggestMethodName(context, "setGroovyRef", this));
    }
    return myRefSetterName.get();
  }

  public String getSetterName(PsiMethod setter, GroovyPsiElement place) {
    String name = setters.get(setter);
    if (name != null) return name;

    name = GenerationUtil.suggestMethodName(place, setter.getName(), this);
    setters.put(setter, name);
    return name;
  }

  public Map<PsiMethod, String> getSetters() {
    return Collections.unmodifiableMap(setters);
  }
}