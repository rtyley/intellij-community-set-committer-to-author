/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.plugins.groovy.refactoring.move;

import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.move.moveClassesOrPackages.SingleSourceRootMoveDestination;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.io.File;

/**
 * @author Maxim.Medvedev
 */
public class GroovyMoveScriptTest extends JavaCodeInsightFixtureTestCase {
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "refactoring/move/moveScript/";
  }

  public void testMoveScriptBasic() throws Exception {
    doTest("moveScriptBasic", new String[]{"a/Script.groovy"}, "b");
  }

  public void testUpdateRefs() throws Exception {
    doTest("updateReferences", new String[]{"a/Script.groovy"}, "b");
  }

  public void testMultiMove() throws Exception {
    doTest("multiMove", new String[]{"a/Script.groovy", "a/Script2.groovy"}, "b");
  }

  private void performAction(String[] fileNames, String newDirName, String dir) throws Exception {
    final PsiFile[] files = new PsiFile[fileNames.length];
    final VirtualFileManager fileManager = VirtualFileManager.getInstance();
    for (int i = 0; i < files.length; i++) {
      String fileName = fileNames[i];
      final VirtualFile file = fileManager.findFileByUrl("file://" + dir + "/" + fileName);
      assertNotNull("File " + fileName + " not found", file);

      files[i] = PsiManager.getInstance(getProject()).findFile(file);
      assertNotNull("File " + fileName + " not found", files[i]);
    }
    final VirtualFile virDir = fileManager.findFileByUrl("file://" + dir + "/" + newDirName);
    assertNotNull("Directory " + newDirName + " not found", virDir);

    final PsiDirectory psiDirectory = PsiManager.getInstance(getProject()).findDirectory(virDir);
    assertNotNull("Directory " + newDirName + " not found", psiDirectory);

    final PsiPackage pkg = JavaDirectoryService.getInstance().getPackage(psiDirectory);
    final SingleSourceRootMoveDestination destination = new SingleSourceRootMoveDestination(PackageWrapper.create(pkg), psiDirectory);
    new WriteCommandAction(myFixture.getProject()) {
      @Override
      protected void run(Result result) throws Throwable {
        new MoveGroovyScriptProcessor(getProject(), files, destination, false, false, null).run();
      }
    }.execute();

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    FileDocumentManager.getInstance().saveAllDocuments();
  }

  private void doTest(String testName, String[] fileNames, String newDirName) throws Exception {
    final VirtualFile actualRoot = myFixture.copyDirectoryToProject(testName + "/before", "");

    performAction(fileNames, newDirName, actualRoot.getPath());

    File expectedRoot = new File(getTestDataPath() + testName + "/after");
    getProject().getComponent(PostprocessReformattingAspect.class).doPostponedFormatting();

    VirtualFileManager.getInstance().refresh(false);
    GroovyMoveClassTest.assertDirsEquals(expectedRoot, actualRoot);
  }


}
