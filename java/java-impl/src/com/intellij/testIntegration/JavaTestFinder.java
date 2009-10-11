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
package com.intellij.testIntegration;

import com.intellij.codeInsight.TestUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.regex.Pattern;

public class JavaTestFinder implements TestFinder {
  public PsiClass findSourceElement(PsiElement element) {
    return TestIntegrationUtils.findOuterClass(element);
  }

  @NotNull
  public Collection<PsiElement> findClassesForTest(PsiElement element) {
    PsiClass klass = findSourceElement(element);
    if (klass == null) return Collections.emptySet();

    GlobalSearchScope scope;
    Module module = getModule(element);
    if (module != null) {
      scope = GlobalSearchScope.moduleWithDependenciesScope(module);
    }
    else {
      scope = GlobalSearchScope.projectScope(element.getProject());
    }

    PsiShortNamesCache cache = JavaPsiFacade.getInstance(element.getProject()).getShortNamesCache();

    List<Pair<PsiClass, Integer>> classesWithWeights = new ArrayList<Pair<PsiClass, Integer>>();
    for (Pair<String, Integer> eachNameWithWeight : collectPossibleClassNamesWithWeights(klass.getName())) {
      for (PsiClass eachClass : cache.getClassesByName(eachNameWithWeight.first, scope)) {
        if (isTestSubjectClass(eachClass)) {
          classesWithWeights.add(new Pair<PsiClass, Integer>(eachClass, eachNameWithWeight.second));
        }
      }
    }

    Collections.sort(classesWithWeights, new Comparator<Pair<PsiClass, Integer>>() {
      public int compare(Pair<PsiClass, Integer> o1, Pair<PsiClass, Integer> o2) {
        int result = o2.second.compareTo(o1.second);
        if (result == 0) {
          result = o1.first.getName().compareTo(o2.first.getName());
        }
        return result;
      }
    });

    List<PsiElement> result = new ArrayList<PsiElement>();
    for (Pair<PsiClass, Integer> each : classesWithWeights) {
      result.add(each.first);
    }

    return result;
  }

  private boolean isTestSubjectClass(PsiClass klass) {
    if (klass.isEnum()
        || klass.isInterface()
        || klass.isAnnotationType()
        || TestUtil.isTestClass(klass)) {
      return false;
    }
    return true;
  }

  private List<Pair<String, Integer>> collectPossibleClassNamesWithWeights(String testName) {
    String[] words = NameUtil.splitNameIntoWords(testName);
    List<Pair<String, Integer>> result = new ArrayList<Pair<String, Integer>>();

    for (int from = 0; from < words.length; from++) {
      for (int to = from; to < words.length; to++) {
        result.add(new Pair<String, Integer>(StringUtil.join(words, from, to + 1, ""),
                                             words.length - from + to));
      }
    }

    return result;
  }

  @NotNull
  public Collection<PsiElement> findTestsForClass(PsiElement element) {
    PsiClass klass = findSourceElement(element);
    if (klass == null) return Collections.emptySet();

    GlobalSearchScope scope;
    Module module = getModule(element);
    if (module != null) {
      scope = GlobalSearchScope.moduleWithDependentsScope(module);
    }
    else {
      scope = GlobalSearchScope.projectScope(element.getProject());
    }

    PsiShortNamesCache cache = JavaPsiFacade.getInstance(element.getProject()).getShortNamesCache();

    String klassName = klass.getName();
    Pattern pattern = Pattern.compile(".*" + klassName + ".*");

    List<Pair<PsiClass, Integer>> classesWithProximities = new ArrayList<Pair<PsiClass, Integer>>();

    HashSet<String> names = new HashSet<String>();
    cache.getAllClassNames(names);
    for (String eachName : names) {
      if (pattern.matcher(eachName).matches()) {
        for (PsiClass eachClass : cache.getClassesByName(eachName, scope)) {
          if (TestUtil.isTestClass(eachClass)) {
            classesWithProximities.add(
                new Pair<PsiClass, Integer>(eachClass, calcTestNameProximity(klassName, eachName)));
          }
        }
      }
    }

    Collections.sort(classesWithProximities, new Comparator<Pair<PsiClass, Integer>>() {
      public int compare(Pair<PsiClass, Integer> o1, Pair<PsiClass, Integer> o2) {
        int result = o1.second.compareTo(o2.second);
        if (result == 0) {
          result = o1.first.getName().compareTo(o2.first.getName());
        }
        return result;
      }
    });

    List<PsiElement> result = new ArrayList<PsiElement>();
    for (Pair<PsiClass, Integer> each : classesWithProximities) {
      result.add(each.first);
    }

    return result;
  }

  private Integer calcTestNameProximity(String klassName, String testName) {
    int posProximity = testName.indexOf(klassName);
    int sizeProximity = testName.length() - klassName.length();

    return posProximity + sizeProximity;
  }

  private static Module getModule(PsiElement element) {
    ProjectFileIndex index = ProjectRootManager.getInstance(element.getProject()).getFileIndex();
    return index.getModuleForFile(element.getContainingFile().getVirtualFile());
  }

  public boolean isTest(PsiElement element) {
    return TestIntegrationUtils.isTest(element);
  }
}
