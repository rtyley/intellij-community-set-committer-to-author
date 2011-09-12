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
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Jun 7, 2002
 * Time: 8:30:35 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij;

import com.intellij.idea.Bombed;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestRunnerUtil;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.io.*;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

@SuppressWarnings({"HardCodedStringLiteral"})
public class TestCaseLoader {

  /** Holds name of JVM property that is assumed to define target test group name. */
  private static final String TARGET_TEST_GROUP = "idea.test.group";

  /** Holds name of JVM property that is assumed to define filtering rules for test classes. */
  private static final String TARGET_TEST_PATTERNS = "idea.test.patterns";

  /** Holds name of JVM property that is assumed to determine if only 'fast' tests should be executed. */
  private static final String FAST_TESTS_ONLY_FLAG = "idea.fast.only";

  private final List<Class> myClassList = new ArrayList<Class>();
  private Class myFirstTestClass;
  private Class myLastTestClass;
  private final TestClassesFilter myTestClassesFilter;
  private final String myTestGroupName;
  private final Set<String> blockedTests = new HashSet<String>();
  private final String[] slowTestNames;

  public TestCaseLoader(String classFilterName) {
    InputStream excludedStream = getClass().getClassLoader().getResourceAsStream(classFilterName);
    String preconfiguredGroup = System.getProperty(TARGET_TEST_GROUP);
    if (preconfiguredGroup == null || "".equals(preconfiguredGroup.trim())) {
      myTestGroupName = "";
    } else {
      myTestGroupName = preconfiguredGroup.trim();
    }
    if (excludedStream != null) {
      try {
        myTestClassesFilter = GroupBasedTestClassFilter.createOn(new InputStreamReader(excludedStream), myTestGroupName);
      }
      finally {
        try {
          excludedStream.close();
        }
        catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
    else {
      String patterns = System.getProperty(TARGET_TEST_PATTERNS);
      if (patterns != null) {
        myTestClassesFilter = new PatternListTestClassFilter(StringUtil.split(patterns, ";"));
      }
      else {
        myTestClassesFilter = TestClassesFilter.ALL_CLASSES;
      }
    }

    String[] names;
    try {
      InputStream stream = getClass().getClassLoader().getResourceAsStream("tests/slowTests.txt");
      names = FileUtil.loadTextAndClose(new InputStreamReader(stream)).split("\\s");
    }
    catch (Exception e) {
      // no luck
      names = new String[0];
    }
    slowTestNames = names;
    if (Comparing.equal(System.getProperty(FAST_TESTS_ONLY_FLAG), "true")) {
      blockedTests.addAll(Arrays.asList(slowTestNames));
    }
    else {
      checkClassesExist();
    }
    System.out.println("Using test group: [" + myTestGroupName +"]");
  }

  void checkClassesExist() {
    String s = "";
    for (String slowTestName : slowTestNames) {
      if (slowTestName.trim().length() == 0) continue;
      if (blockedTests.contains(slowTestName)) continue;
      try {
        Class.forName(slowTestName);
      }
      catch (ClassNotFoundException e) {
        s += "\n" + slowTestName;
      }
    }
    if (s.length() != 0) {
      throw new RuntimeException("Tests in slowTests.txt which cannot be instantiated: "+s);
    }
  }

  /*
   * Adds <code>testCaseClass</code> to the list of classdes
   * if the class is a test case we wish to load. Calls
   * <code>shouldLoadTestCase ()</code> to determine that.
   */
  void addClassIfTestCase(final Class testCaseClass) {
    if (shouldAddTestCase(testCaseClass, true) && testCaseClass != myFirstTestClass && testCaseClass != myLastTestClass) {
      myClassList.add(testCaseClass);
    }
  }

  void addFirstTest(Class aClass) {
    assert myFirstTestClass == null : "already added: "+aClass;
    assert shouldAddTestCase(aClass, false) : "not a test: "+aClass;
    myFirstTestClass = aClass;
  }

  void addLastTest(Class aClass) {
    assert myLastTestClass == null : "already added: "+aClass;
    assert shouldAddTestCase(aClass, false) : "not a test: "+aClass;
    myLastTestClass = aClass;
  }

  /**
   * Determine if we should load this test case.
   */
  private boolean shouldAddTestCase(final Class testCaseClass, boolean testForExcluded) {
    if ((testCaseClass.getModifiers() & Modifier.ABSTRACT) != 0) return false;
    if (testForExcluded && shouldExcludeTestClass(testCaseClass)) return false;

    if (TestCase.class.isAssignableFrom(testCaseClass) || TestSuite.class.isAssignableFrom(testCaseClass)) {
      return true;
    }
    try {
      final Method suiteMethod = testCaseClass.getMethod("suite");
      if (Test.class.isAssignableFrom(suiteMethod.getReturnType()) && (suiteMethod.getModifiers() & Modifier.STATIC) != 0) {
        //System.out.println("testCaseClass = " + testCaseClass);
        return true;
      }
    } catch (NoSuchMethodException e) { }

    return TestRunnerUtil.isJUnit4TestClass(testCaseClass);
  }

  /*
   * Determine if we should exclude this test case.
   */
  private boolean shouldExcludeTestClass(Class testCaseClass) {
    return !myTestClassesFilter.matches(testCaseClass.getName()) || isBombed(testCaseClass)
              || blockedTests.contains(testCaseClass.getName());
  }

  public static boolean isBombed(final Method method) {
    final Bombed bombedAnnotation = method.getAnnotation(Bombed.class);
    if (bombedAnnotation == null) return false;
    if (PlatformTestUtil.isRotten(bombedAnnotation)) {
      String message = "Disarm the stale bomb for '" + method + "' in class '" + method.getDeclaringClass() + "'";
      System.err.println(message);
      //Assert.fail(message);
    }
    return !PlatformTestUtil.bombExplodes(bombedAnnotation);
  }

  public static boolean isBombed(final Class<?> testCaseClass) {
    final Bombed bombedAnnotation = testCaseClass.getAnnotation(Bombed.class);
    if (bombedAnnotation == null) return false;
    if (PlatformTestUtil.isRotten(bombedAnnotation)) {
      String message = "Disarm the stale bomb for '" + testCaseClass + "'";
      System.err.println(message);
     // Assert.fail(message);
    }
    return !PlatformTestUtil.bombExplodes(bombedAnnotation);
  }

  public void loadTestCases(final Collection<String> classNamesIterator) {
    for (String className : classNamesIterator) {
      try {
        Class candidateClass = Class.forName(className);
        addClassIfTestCase(candidateClass);
      }
      catch (UnsatisfiedLinkError e) {
        //ignore
      }
      catch (ClassNotFoundException e) {
        e.printStackTrace();
        System.err.println("Cannot load class: " + className + " " + e.getMessage());
      }
      catch (NoClassDefFoundError e) {
        e.printStackTrace();
        System.err.println("Cannot load class that " + className + " is dependant on");
      }
      catch (ExceptionInInitializerError e) {
        e.printStackTrace();
        e.getException().printStackTrace();
        System.err.println("Cannot load class: " + className + " " + e.getException().getMessage());
      }
    }
  }
  
  private static final List<String> ourRanklist = getTeamCityRankList();
  private static List<String> getTeamCityRankList() {
    final String filepath = System.getProperty("teamcity.tests.recentlyFailedTests.file", null);
    if (filepath == null) {
      return Collections.emptyList();
    }

    List<String> result = new ArrayList<String>();
    try {
      BufferedReader reader = new BufferedReader(new FileReader(filepath));
      do {
        final String classname = reader.readLine();
        if (classname == null) break;
        result.add(classname);
      }
      while (true);
      return result;
    }
    catch (IOException e) {
      return Collections.emptyList();
    }
  }

  private int getRank(Class aClass) {
    final String name = aClass.getName();
    if (aClass == myFirstTestClass) return -1;
    if (aClass == myLastTestClass) return myClassList.size() + ourRanklist.size();
    int i = ourRanklist.indexOf(name);
    if (i != -1) {
      return i;
    }
    return ourRanklist.size();
  }

  public List<Class> getClasses() {
    List<Class> result = new ArrayList<Class>(myClassList.size());
    if (myFirstTestClass != null) {
      result.add(myFirstTestClass);
    }
    result.addAll(myClassList);
    if (myLastTestClass != null) {
      result.add(myLastTestClass);
    }

    if (!ourRanklist.isEmpty()) {
      Collections.sort(result, new Comparator<Class>() {
        @Override
        public int compare(final Class o1, final Class o2) {
          return getRank(o1) - getRank(o2);
        }
      });
    }

    return result;
  }

  public void clearClasses() {
    myClassList.clear();
  }
}
