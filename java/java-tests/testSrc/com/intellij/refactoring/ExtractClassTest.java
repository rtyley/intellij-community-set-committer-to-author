/*
 * User: anna
 * Date: 20-Aug-2008
 */
package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.extractclass.ExtractClassProcessor;
import junit.framework.Assert;

import java.util.ArrayList;

public class ExtractClassTest extends MultiFileTestCase{
  protected String getTestRoot() {
    return "/refactoring/extractClass/";
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  private void doTestMethod() throws Exception {
    doTestMethod(null);
  }

  private void doTestMethod(String conflicts) throws Exception {
    doTestMethod("foo", conflicts);
  }

  private void doTestMethod(final String methodName, final String conflicts) throws Exception {
    doTest(new PerformAction() {
      public void performAction(final VirtualFile rootDir, final VirtualFile rootAfter) throws Exception {
        PsiClass aClass = myJavaFacade.findClass("Test", GlobalSearchScope.projectScope(myProject));

        assertNotNull("Class Test not found", aClass);

        final ArrayList<PsiMethod> methods = new ArrayList<PsiMethod>();
        methods.add(aClass.findMethodsByName(methodName, false)[0]);
        
        doTest(aClass, methods, new ArrayList<PsiField>(), conflicts, false);
      }
    });
  }

  public void testStatic() throws Exception {
    doTestMethod();
  }

  public void testFieldReference() throws Exception {
    doTestMethod("foo", "Field 'myField' needs getter");
  }

  public void testVarargs() throws Exception {
    doTestMethod();
  }

  public void testNoDelegation() throws Exception {
    doTestMethod();
  }

  public void testNoFieldDelegation() throws Exception {
    doTestFieldAndMethod();
  }

  public void testFieldInitializers() throws Exception {
    doTestField(null);
  }

  public void testDependantFieldInitializers() throws Exception {
    doTestField(null);
  }

  public void testDependantNonStaticFieldInitializers() throws Exception {
    doTestField(null, true);
  }

  public void testInheritanceDelegation() throws Exception {
    doTestMethod();
  }

  private void doTestFieldAndMethod() throws Exception {
    doTestFieldAndMethod("bar");
  }

  private void doTestFieldAndMethod(final String methodName) throws Exception {
    doTest(new PerformAction() {
      public void performAction(final VirtualFile rootDir, final VirtualFile rootAfter) throws Exception {
        PsiClass aClass = myJavaFacade.findClass("Test", GlobalSearchScope.projectScope(myProject));

        assertNotNull("Class Test not found", aClass);

        final ArrayList<PsiMethod> methods = new ArrayList<PsiMethod>();
        methods.add(aClass.findMethodsByName(methodName, false)[0]);

        final ArrayList<PsiField> fields = new ArrayList<PsiField>();
        fields.add(aClass.findFieldByName("myT", false));

        doTest(aClass, methods, fields, null, false);
      }
    });
  }

  private void doTestField(final String conflicts) throws Exception {
    doTestField(conflicts, false);
  }

  private void doTestField(final String conflicts, final boolean generateGettersSetters) throws Exception {
    doTest(new PerformAction() {
      public void performAction(final VirtualFile rootDir, final VirtualFile rootAfter) throws Exception {
        PsiClass aClass = myJavaFacade.findClass("Test", GlobalSearchScope.projectScope(myProject));

        assertNotNull("Class Test not found", aClass);

        final ArrayList<PsiMethod> methods = new ArrayList<PsiMethod>();

        final ArrayList<PsiField> fields = new ArrayList<PsiField>();
        fields.add(aClass.findFieldByName("myT", false));

        doTest(aClass, methods, fields, conflicts, generateGettersSetters);
      }
    });
  }

  private static void doTest(final PsiClass aClass, final ArrayList<PsiMethod> methods, final ArrayList<PsiField> fields, final String conflicts,
                             boolean generateGettersSetters) {
    try {
      ExtractClassProcessor processor = new ExtractClassProcessor(aClass, fields, methods, new ArrayList<PsiClass>(), "", "Extracted", null, generateGettersSetters);
      processor.run();
      LocalFileSystem.getInstance().refresh(false);
      FileDocumentManager.getInstance().saveAllDocuments();
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      if (conflicts != null) {
        Assert.assertEquals(e.getMessage(), conflicts);
        return;
      } else {
        fail(e.getMessage());
      }
    }
    if (conflicts != null) {
      fail("Conflicts were not detected: " + conflicts);
    }
  }

  public void testGenerateGetters() throws Exception {
    doTestField(null, true);
  }

  public void testIncrementDecrement() throws Exception {
    doTestField(null, true);
  }


  public void testGetters() throws Exception {
    doTestFieldAndMethod("getMyT");
  }

  public void testHierarchy() throws Exception {
    doTestFieldAndMethod();
  }

  public void testPublicFieldDelegation() throws Exception {
    doTest(new PerformAction() {
      public void performAction(final VirtualFile rootDir, final VirtualFile rootAfter) throws Exception {
        PsiClass aClass = myJavaFacade.findClass("Test", GlobalSearchScope.projectScope(myProject));

        assertNotNull("Class Test not found", aClass);

        final ArrayList<PsiField> fields = new ArrayList<PsiField>();
        fields.add(aClass.findFieldByName("myT", false));

        ExtractClassProcessor processor = new ExtractClassProcessor(aClass, fields, new ArrayList<PsiMethod>(), new ArrayList<PsiClass>(), "", "Extracted");
        processor.run();
        LocalFileSystem.getInstance().refresh(false);
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    });
  }

  private void doTestInnerClass() throws Exception {
    doTest(new PerformAction() {
      public void performAction(final VirtualFile rootDir, final VirtualFile rootAfter) throws Exception {
        PsiClass aClass = myJavaFacade.findClass("Test", GlobalSearchScope.projectScope(myProject));

        assertNotNull("Class Test not found", aClass);

        final ArrayList<PsiClass> classes = new ArrayList<PsiClass>();
        classes.add(aClass.findInnerClassByName("Inner", false));
        ExtractClassProcessor processor = new ExtractClassProcessor(aClass, new ArrayList<PsiField>(), new ArrayList<PsiMethod>(), classes, "", "Extracted");
        processor.run();
        LocalFileSystem.getInstance().refresh(false);
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    });
  }

  public void testInner() throws Exception {
    doTestInnerClass();
  }

  public void testMultipleGetters() throws Exception {
    doTestField(null);
  }

  public void testMultipleGetters1() throws Exception {
    doTestMethod("getMyT", "Field 'myT' needs getter");
  }

  public void testUsedInInitializer() throws Exception {
    doTestField("Class initializer requires moved members");
  }

  public void testUsedInConstructor() throws Exception {
    doTestField("Constructor requires moved members");
  }

  public void testRefInJavadoc() throws Exception {
    doTestField(null);
  }

  public void testMethodTypeParameters() throws Exception {
    doTestMethod();
  }

  public void testPublicVisibility() throws Exception {
    doTest(new PerformAction() {
      public void performAction(final VirtualFile rootDir, final VirtualFile rootAfter) throws Exception {
        PsiClass aClass = myJavaFacade.findClass("Test", GlobalSearchScope.projectScope(myProject));

        assertNotNull("Class Test not found", aClass);

        final ArrayList<PsiMethod> methods = new ArrayList<PsiMethod>();
        methods.add(aClass.findMethodsByName("foos", false)[0]);

        final ArrayList<PsiField> fields = new ArrayList<PsiField>();
        fields.add(aClass.findFieldByName("myT", false));

        final ExtractClassProcessor processor =
          new ExtractClassProcessor(aClass, fields, methods, new ArrayList<PsiClass>(), "", "Extracted", PsiModifier.PUBLIC, false);
        processor.run();
        LocalFileSystem.getInstance().refresh(false);
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    });
  }
}