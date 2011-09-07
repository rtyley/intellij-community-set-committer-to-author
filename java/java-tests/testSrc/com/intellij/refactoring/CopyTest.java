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
package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.search.ProjectScope;
import com.intellij.refactoring.copy.CopyHandler;
import com.intellij.refactoring.inline.InlineToAnonymousClassHandler;
import com.intellij.refactoring.inline.InlineToAnonymousClassProcessor;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.MultiMap;

import java.io.File;

public class CopyTest extends CodeInsightTestCase {
  
  private String getRoot() {
    return JavaTestUtil.getJavaTestDataPath() + "/refactoring/copy/multifile/" + getTestName(true);
  }

  public void testCopyAvailable() throws Exception {
    doTest();
  }

  public void testJavaAndTxt() throws Exception {
    doTest();
  }

  private void doTest() throws Exception {
    String rootBefore = getRoot();
    PsiTestUtil.removeAllRoots(myModule, JavaSdkImpl.getMockJdk17());
    PsiTestUtil.createTestProjectStructure(myProject, myModule, rootBefore, myFilesToDelete);
    PsiPackage pack1 = myJavaFacade.findPackage("pack1");
    PsiPackage pack2 = myJavaFacade.findPackage("pack2");
    assertTrue(CopyHandler.canCopy(new PsiElement[]{pack1.getDirectories()[0], pack2.getDirectories()[0]}));
  }
}
