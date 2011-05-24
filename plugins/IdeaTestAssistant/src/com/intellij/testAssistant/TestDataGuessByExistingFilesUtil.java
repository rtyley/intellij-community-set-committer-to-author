package com.intellij.testAssistant;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.ide.util.gotoByName.GotoFileModel;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testIntegration.TestFramework;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * There is a possible case that particular test class is not properly configured with test annotations but uses test data files.
 * This class contains utility methods for guessing test data files location and name patterns from existing one.
 * 
 * @author Denis Zhdanov
 * @since 5/24/11 2:28 PM
 */
public class TestDataGuessByExistingFilesUtil {

  private TestDataGuessByExistingFilesUtil() {
  }

  /**
   * Tries to guess what test data files match to the given method if it's test method and there are existing test data
   * files for the target test class.
   * 
   * @param method      test method candidate
   * @return            collection of paths to the test data files for the given test if it's possible to guess them;
   *                    <code>null</code> otherwise
   */
  @Nullable
  static List<String> collectTestDataByExistingFiles(@NotNull PsiMethod method) {
    if (getTestName(method) == null) {
      return null;
    }
    PsiFile psiFile = getParent(method, PsiFile.class);
    if (psiFile == null) {
      return null;
    }
    return collectTestDataByExistingFiles(psiFile, getTestName(method.getName()));
  }
  
  @Nullable
  private static <T extends PsiElement> T getParent(@NotNull PsiElement element, Class<T> clazz) {
    for (PsiElement e = element; e != null; e = e.getParent()) {
      if (clazz.isAssignableFrom(e.getClass())) {
        return clazz.cast(e);
      }
    }
    return null;
  }
  
  @Nullable
  static List<String> collectTestDataByExistingFiles(@NotNull PsiFile psiFile, @NotNull String testName) {
    GotoFileModel model = new GotoFileModel(psiFile.getProject());
    TestDataDescriptor descriptor = buildDescriptorFromExistingTestData(psiFile, model);
    if (descriptor == null || !descriptor.isComplete()) {
      return null;
    }

    return descriptor.generate(testName);
  }
  
  @Nullable
  private static String getTestName(@NotNull PsiMethod method) {
    final PsiClass psiClass = getParent(method, PsiClass.class);
    if (psiClass == null) {
      return null;
    }

    TestFramework[] frameworks = Extensions.getExtensions(TestFramework.EXTENSION_NAME);
    TestFramework framework = null;
    for (TestFramework each : frameworks) {
      if (each.isTestClass(psiClass)) {
        framework = each;
        break;
      }
    }

    if (framework == null || isUtilityMethod(method, psiClass, framework)) {
      return null;
    }
    
    return getTestName(method.getName());
  }

  private static boolean isUtilityMethod(@NotNull PsiMethod method, @NotNull PsiClass psiClass, @NotNull TestFramework framework) {
    if (method == framework.findSetUpMethod(psiClass) || method == framework.findTearDownMethod(psiClass)) {
      return true;
    }
    
    // JUnit3
    if (framework.getClass().getName().contains("JUnit3")) {
      return !method.getName().startsWith("test");
    }
    
    // JUnit4
    else if (framework.getClass().getName().contains("JUnit4")) {
      return !AnnotationUtil.isAnnotated(method, "org.junit.Test", false);
    }
    return false;
  }
  
  @NotNull
  public static String getTestName(@NotNull String methodName) {
    return methodName.startsWith("test") ? methodName.substring("test".length()) : methodName;
  }

  @Nullable
  private static TestDataDescriptor buildDescriptorFromExistingTestData(@NotNull PsiFile file, @NotNull GotoFileModel gotoModel) {
    final PsiClass psiClass = PsiTreeUtil.getChildOfType(file, PsiClass.class);
    if (psiClass == null) {
      return null;
    }

    TestFramework[] frameworks = Extensions.getExtensions(TestFramework.EXTENSION_NAME);
    TestFramework framework = null;
    for (TestFramework each : frameworks) {
      if (each.isTestClass(psiClass)) {
        framework = each;
        break;
      }
    }
    if (framework == null) {
      return null;
    }

    final PsiElement setUpMethod = framework.findSetUpMethod(psiClass);
    final PsiElement tearDownMethod = framework.findTearDownMethod(psiClass);
    TestDataDescriptor descriptor = new TestDataDescriptor();
    for (PsiMethod method : psiClass.getMethods()) {
      final String name = getTestName(method.getName());
      if (method == setUpMethod || method == tearDownMethod || name.equals(psiClass.getName())) {
        continue;
      }
      final Collection<VirtualFile> matchedFiles = getMatchedFiles(gotoModel, name);
      if (!descriptor.isComplete()) {
        descriptor.populate(name, matchedFiles);
      }
      if (descriptor.isComplete()) {
        return descriptor;
      }
    }
    return null;
  }

  @NotNull
  private static Collection<VirtualFile> getMatchedFiles(@NotNull GotoFileModel gotoModel, @NotNull String testName) {
    String pattern = String.format("*%s*", testName);
    final NameUtil.Matcher matcher = NameUtil.buildMatcher(pattern, 0, true, true, pattern.toLowerCase().equals(pattern));
    List<VirtualFile> result = new ArrayList<VirtualFile>();
    for (String name : gotoModel.getNames(false)) {
      if (matcher.matches(name)) {
        final Object[] elements = gotoModel.getElementsByName(name, false, pattern);
        if (elements != null) {
          for (Object element : elements) {
            if (element instanceof PsiFile) {
              result.add(((PsiFile)element).getVirtualFile());
            }
          }
        }
      }
    }
    return result;
  }

  
  private static class TestLocationDescriptor {

    public String dir;
    public String filePrefix;
    public String fileSuffix;
    public String ext;
    public boolean startWithLowerCase;

    public boolean isComplete() {
      return dir != null && filePrefix != null && fileSuffix != null && ext != null;
    }

    public void populate(@NotNull String testName, @NotNull VirtualFile matched) {
      final String fileName = matched.getNameWithoutExtension();
      int i = fileName.indexOf(testName);
      final char firstChar = testName.charAt(0);
      boolean testNameStartsWithLowerCase = Character.isLowerCase(firstChar);
      if (i < 0) {
        i = fileName.indexOf(
          (testNameStartsWithLowerCase ? Character.toUpperCase(firstChar) : Character.toLowerCase(firstChar)) + testName.substring(1)
        );
        startWithLowerCase = !testNameStartsWithLowerCase;
      }
      else {
        startWithLowerCase = testNameStartsWithLowerCase;
      }

      // Skip files that doesn't contain target test name and files that contain digit after target test name fragment.
      // Example: there are tests with names 'testEnter()' and 'testEnter2()' and we don't want test data file 'testEnter2'
      // to be matched to the test 'testEnter()'.
      if (i < 0 || (i + testName.length() < fileName.length()) && Character.isDigit(fileName.charAt(i + testName.length()))) {
        return;
      }

      filePrefix = fileName.substring(0, i);
      fileSuffix = fileName.substring(i + testName.length());
      ext = matched.getExtension();
      dir = matched.getParent().getPath();
    }

    @Override
    public String toString() {
      return String.format("%s/%s[...]%s.%s", dir, filePrefix, fileSuffix, ext);
    }
  }

  private static class TestDataDescriptor {

    private final List<TestLocationDescriptor> myDescriptors = new ArrayList<TestLocationDescriptor>();

    public void populate(@NotNull String testName, @NotNull Collection<VirtualFile> matched) {
      for (VirtualFile file : matched) {
        TestLocationDescriptor descriptor;
        if (myDescriptors.isEmpty()) {
          myDescriptors.add(descriptor = new TestLocationDescriptor());
        }
        else {
          final TestLocationDescriptor last = myDescriptors.get(myDescriptors.size() - 1);
          if (last.isComplete()) {
            myDescriptors.add(descriptor = new TestLocationDescriptor());
          }
          else {
            descriptor = last;
          }
        }
        descriptor.populate(testName, file);
      }
    }

    public boolean isComplete() {
      if (myDescriptors.isEmpty()) {
        return false;
      }

      for (TestLocationDescriptor descriptor : myDescriptors) {
        if (!descriptor.isComplete()) {
          return false;
        }
      }
      return true;
    }

    @NotNull
    public List<String> generate(@NotNull final String testName) {
      List<String> result = new ArrayList<String>();
      for (TestLocationDescriptor descriptor : myDescriptors) {
        result.add(String.format(
          "%s/%s%c%s%s.%s",
          descriptor.dir, descriptor.filePrefix,
          descriptor.startWithLowerCase ? Character.toLowerCase(testName.charAt(0)) : Character.toUpperCase(testName.charAt(0)),
          testName.substring(1), descriptor.fileSuffix, descriptor.ext
        ));
      }
      return result;
    }

    @Override
    public String toString() {
      return myDescriptors.toString();
    }
  }
}
