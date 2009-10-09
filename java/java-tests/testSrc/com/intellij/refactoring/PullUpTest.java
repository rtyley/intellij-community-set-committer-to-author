/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.listeners.JavaRefactoringListenerManager;
import com.intellij.refactoring.listeners.MoveMemberListener;
import com.intellij.refactoring.memberPullUp.PullUpHelper;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.testFramework.LightCodeInsightTestCase;

/**
 * @author ven
 */
public class PullUpTest extends LightCodeInsightTestCase {
  private static final String BASE_PATH = "/refactoring/pullUp/";


  public void testQualifiedThis() throws Exception {
    doTest(new Pair<String, Class<? extends PsiMember>> ("Inner", PsiClass.class));
  }

  public void testQualifiedSuper() throws Exception {
    doTest(new Pair<String, Class<? extends PsiMember>> ("Inner", PsiClass.class));
  }

  public void testQualifiedReference() throws Exception {     // IDEADEV-25008
    doTest(new Pair<String, Class<? extends PsiMember>> ("x", PsiField.class),
           new Pair<String, Class<? extends PsiMember>> ("getX", PsiMethod.class),
           new Pair<String, Class<? extends PsiMember>> ("setX", PsiMethod.class));

  }

  public void testTryCatchFieldInitializer() throws Exception {
    doTest(new Pair<String, Class<? extends PsiMember>>("field", PsiField.class));
  }

  public void testIfFieldInitializationWithNonMovedField() throws Exception {
    doTest(new Pair<String, Class<? extends PsiMember>>("f", PsiField.class));
  }

  public void testIfFieldMovedInitialization() throws Exception {
    doTest(new Pair<String, Class<? extends PsiMember>>("f", PsiField.class));
  }

  public void testMultipleConstructorsFieldInitialization() throws Exception {
    doTest(new Pair<String, Class<? extends PsiMember>>("f", PsiField.class));
  }

  public void testMultipleConstructorsFieldInitializationNoGood() throws Exception {
    doTest(new Pair<String, Class<? extends PsiMember>>("f", PsiField.class));
  }


  public void testRemoveOverride() throws Exception {
    doTest(new Pair<String, Class<? extends PsiMember>> ("get", PsiMethod.class));
  }

  private void doTest(Pair<String, Class<? extends PsiMember>>... membersToFind) throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    PsiElement elementAt = getFile().findElementAt(getEditor().getCaretModel().getOffset());
    final PsiClass sourceClass = PsiTreeUtil.getParentOfType(elementAt, PsiClass.class);
    assertNotNull(sourceClass);

    PsiClass targetClass = sourceClass.getSuperClass();
    if (!targetClass.isWritable()) {
      final PsiClass[] interfaces = sourceClass.getInterfaces();
      assertTrue(interfaces.length == 1);
      assertTrue(interfaces[0].isWritable());
      targetClass = interfaces[0];
    }
    MemberInfo[] infos = findMembers(sourceClass, membersToFind);

    final int[] countMoved = new int[] {0};
    final MoveMemberListener listener = new MoveMemberListener() {
      public void memberMoved(PsiClass aClass, PsiMember member) {
        assertEquals(sourceClass, aClass);
        countMoved[0]++;
      }
    };
    JavaRefactoringListenerManager.getInstance(getProject()).addMoveMembersListener(listener);
    final PullUpHelper helper = new PullUpHelper(sourceClass, targetClass, infos, new DocCommentPolicy(DocCommentPolicy.ASIS));
    helper.moveMembersToBase();
    helper.moveFieldInitializations();
    JavaRefactoringListenerManager.getInstance(getProject()).removeMoveMembersListener(listener);
    assertEquals(countMoved[0], membersToFind.length);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public static MemberInfo[] findMembers(final PsiClass sourceClass, final Pair<String, Class<? extends PsiMember>>... membersToFind) {
    MemberInfo[] infos = new MemberInfo[membersToFind.length];
    for (int i = 0; i < membersToFind.length; i++) {
      final Class<? extends PsiMember> clazz = membersToFind[i].getSecond();
      final String name = membersToFind[i].getFirst();
      PsiMember member = null;
      if (PsiClass.class.isAssignableFrom(clazz)) {
        member = sourceClass.findInnerClassByName(name, false);
      } else if (PsiMethod.class.isAssignableFrom(clazz)) {
        final PsiMethod[] methods = sourceClass.findMethodsByName(name, false);
        assertEquals(1, methods.length);
        member = methods[0];
      } else if (PsiField.class.isAssignableFrom(clazz)) {
        member = sourceClass.findFieldByName(name, false);
      }

      assertNotNull(member);
      infos[i] = new MemberInfo(member);
    }
    return infos;
  }

  protected Sdk getProjectJDK() {
    return JavaSdkImpl.getMockJdk15("50");
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }
}
