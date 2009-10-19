/*
 * User: anna
 * Date: 20-Aug-2008
 */
package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.memberPushDown.PushDownProcessor;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.classMembers.MemberInfo;

//push first method from class a.A to class b.B
public class PushDownImportsTest extends MultiFileTestCase {
  protected String getTestRoot() {
    return "/refactoring/pushDown/";
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  protected Sdk getTestProjectJdk() {
    return JavaSdkImpl.getMockJdk15("java 1.5");
  }

  private void doTest() throws Exception {
    doTest(false);
  }

  private void doTest(final boolean fail) throws Exception {
    try {
      doTest(new PerformAction() {
        public void performAction(final VirtualFile rootDir, final VirtualFile rootAfter) throws Exception {
          final PsiClass srcClass = myJavaFacade.findClass("a.A");
          assertTrue("Source class not found", srcClass != null);

          final PsiClass targetClass = myJavaFacade.findClass("b.B");
          assertTrue("Target class not found", targetClass != null);

          final PsiMethod[] methods = srcClass.getMethods();
          assertTrue("No methods found", methods.length > 0);
          final MemberInfo[] membersToMove = new MemberInfo[1];
          final MemberInfo memberInfo = new MemberInfo(methods[0]);
          memberInfo.setChecked(true);
          membersToMove[0] = memberInfo;

          new PushDownProcessor(getProject(), membersToMove, srcClass, new DocCommentPolicy(DocCommentPolicy.ASIS)).run();


          //LocalFileSystem.getInstance().refresh(false);
          //FileDocumentManager.getInstance().saveAllDocuments();
        }
      });
    }
    catch (RuntimeException e) {
      if (fail) {
        return;
      }
      else {
        throw e;
      }
    }
    if (fail) {
      fail("Conflict was not detected");
    }
  }


  public void testStaticImportsInsidePushedMethod() throws Exception {
    doTest();
  }

  public void testStaticImportOfPushedMethod() throws Exception {
    doTest();
  }
}