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

package org.jetbrains.android;

import com.android.sdklib.SdkConstants;
import org.jetbrains.android.sdk.AndroidSdkTestProfile;
import org.jetbrains.android.sdk.Android15TestProfile;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.refactoring.rename.RenameProcessor;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Aug 9, 2009
 * Time: 8:50:20 PM
 * To change this template use File | Settings | File Templates.
 */
public class AndroidRenameTest extends AndroidTestCase {
  private static final String BASE_PATH = "/rename/";
  private static final String R_JAVA_PATH = "gen/p1/p2/R.java";

  public AndroidRenameTest() {
    super(false);
  }

  @Override
  public AndroidSdkTestProfile getTestProfile() {
    return new Android15TestProfile();
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    AndroidRenameResourceProcessor.ASK = false;
  }

  public void testXmlReferenceToFileResource() throws Throwable {
    createManifest();
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout/layout1.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.copyFileToProject(BASE_PATH + "pic.png", "res/drawable/pic.png");
    myFixture.copyFileToProject(BASE_PATH + "styles.xml", "res/values/styles.xml");
    myFixture.copyFileToProject(BASE_PATH + "R1.java", R_JAVA_PATH);
    renameElementWithTextOccurences("pic1.png");
    myFixture.checkResultByFile(BASE_PATH + "layout_file_after.xml");
    myFixture.checkResultByFile(R_JAVA_PATH, BASE_PATH + "R_file_after.java", true);
    myFixture.checkResultByFile("res/values/styles.xml", BASE_PATH + "styles_after.xml", true);
    assertNotNull(myFixture.findFileInTempDir("res/drawable/pic1.png"));
  }

  public void testXmlReferenceToFileResource1() throws Throwable {
    createManifest();
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout/layout1.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.copyFileToProject(BASE_PATH + "pic.png", "res/drawable/pic.9.png");
    myFixture.copyFileToProject(BASE_PATH + "styles.xml", "res/values/styles.xml");
    myFixture.copyFileToProject(BASE_PATH + "R1.java", R_JAVA_PATH);
    renameElementWithTextOccurences("pic1.9.png");
    myFixture.checkResultByFile(BASE_PATH + "layout_file_after.xml");
    myFixture.checkResultByFile(R_JAVA_PATH, BASE_PATH + "R_file_after.java", true);
    myFixture.checkResultByFile("res/values/styles.xml", BASE_PATH + "styles_after.xml", true);
    assertNotNull(myFixture.findFileInTempDir("res/drawable/pic1.9.png"));
  }

  private void renameElementWithTextOccurences(final String newName) throws Throwable {
    new WriteCommandAction.Simple(myFixture.getProject()) {
      @Override
      protected void run() throws Throwable {
        Editor editor = myFixture.getEditor();
        PsiFile file = myFixture.getFile();
        Editor completionEditor = InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(editor, file);
        PsiElement element = TargetElementUtilBase.findTargetElement(completionEditor, TargetElementUtilBase.REFERENCED_ELEMENT_ACCEPTED |
                                                                                       TargetElementUtilBase.ELEMENT_NAME_ACCEPTED);
        assert element != null;
        final PsiElement substitution = RenamePsiElementProcessor.forElement(element).substituteElementToRename(element, editor);
        new RenameProcessor(myFixture.getProject(), substitution, newName, false, true).run();
      }
    }.execute().throwException();
  }

  public void testXmlReferenceToValueResource() throws Throwable {
    createManifest();
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "layout2.xml", "res/layout/layout2.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "R2.java", R_JAVA_PATH);
    myFixture.renameElementAtCaret("str1");
    myFixture.checkResultByFile(BASE_PATH + "layout_value_after.xml");
    myFixture.checkResultByFile(R_JAVA_PATH, BASE_PATH + "R_value_after.java", true);
    myFixture.checkResultByFile("res/values/strings.xml", BASE_PATH + "strings_after.xml", true);
  }

  public void testJavaReferenceToFileResource() throws Throwable {
    createManifest();
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "R3.java", R_JAVA_PATH);
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.copyFileToProject(BASE_PATH + "layout3.xml", "res/layout/layout3.xml");
    myFixture.copyFileToProject(BASE_PATH + "pic.png", "res/drawable/pic.png");
    myFixture.renameElementAtCaret("pic1");
    myFixture.checkResultByFile(BASE_PATH + "R_file_after.java", true);
    myFixture.checkResultByFile("res/layout/layout3.xml", BASE_PATH + "layout_file_after.xml", true);
    assertNotNull(myFixture.findFileInTempDir("res/drawable/pic1.png"));
  }

  public void testJavaReferenceToValueResource() throws Throwable {
    createManifest();
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "R4.java", R_JAVA_PATH);
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.copyFileToProject(BASE_PATH + "layout4.xml", "res/layout/layout4.xml");
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.renameElementAtCaret("str1");
    myFixture.checkResultByFile(BASE_PATH + "R_value_after.java", true);
    myFixture.checkResultByFile("res/layout/layout4.xml", BASE_PATH + "layout_value_after.xml", true);
    myFixture.checkResultByFile("res/values/strings.xml", BASE_PATH + "strings_after.xml", true);
  }

  public void testXmlReferenceToId() throws Throwable {
    createManifest();
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "layout5.xml", "res/layout/layout5.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.copyFileToProject(BASE_PATH + "R5.java", R_JAVA_PATH);
    myFixture.renameElementAtCaret("@+id/anchor1");
    myFixture.checkResultByFile(BASE_PATH + "layout_id_after.xml");
    myFixture.checkResultByFile(R_JAVA_PATH, BASE_PATH + "R_id_after.java", true);
  }

  public void testIdDeclaration() throws Throwable {
    createManifest();
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "layout6.xml", "res/layout/layout6.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.copyFileToProject(BASE_PATH + "R6.java", R_JAVA_PATH);
    myFixture.renameElementAtCaret("@+id/anchor1");
    myFixture.checkResultByFile(BASE_PATH + "layout_id_after.xml");
    myFixture.checkResultByFile(R_JAVA_PATH, BASE_PATH + "R_id_after.java", true);
  }

  public void testJavaReferenceToId() throws Throwable {
    createManifest();
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "R7.java", R_JAVA_PATH);
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.copyFileToProject(BASE_PATH + "layout7.xml", "res/layout/layout7.xml");
    myFixture.renameElementAtCaret("anchor1");
    myFixture.checkResultByFile(BASE_PATH + "R_id_after.java", true);
    myFixture.checkResultByFile("res/layout/layout7.xml", BASE_PATH + "layout_id_after.xml", true);
  }

  public void testStyleable() throws Throwable {
    createManifest();
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "R8.java", R_JAVA_PATH);
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.copyFileToProject(BASE_PATH + "attrs8.xml", "res/values/attrs8.xml");
    myFixture.renameElementAtCaret("LabelView1");
    myFixture.checkResultByFile(BASE_PATH + "R8_after.java", true);
    myFixture.checkResultByFile("res/values/attrs8.xml", BASE_PATH + "attrs8_after.xml", true);
  }

  public void testAttr() throws Throwable {
    createManifest();
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "R9.java", R_JAVA_PATH);
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.copyFileToProject(BASE_PATH + "attrs9.xml", "res/values/attrs9.xml");
    myFixture.renameElementAtCaret("attr1");
    myFixture.checkResultByFile(BASE_PATH + "R9_after.java", true);
    myFixture.checkResultByFile("res/values/attrs9.xml", BASE_PATH + "attrs9_after.xml", true);
  }

  public void testRenameComponent() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "MyActivity.java", "src/p1/p2/MyActivity.java");
    VirtualFile manifestFile = myFixture.copyFileToProject(BASE_PATH + "ActivityManifest.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.configureFromExistingVirtualFile(manifestFile);
    myFixture.renameElementAtCaret("MyActivity1");
    myFixture.checkResultByFile(BASE_PATH + "ActivityManifest_after.xml");
  }

  public void testRenameWidget() throws Throwable {
    createManifest();
    myFixture.copyFileToProject(BASE_PATH + "MyWidget.java", "src/p1/p2/MyWidget.java");
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "layout_widget.xml", "res/layout/layout_widget.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.renameElementAtCaret("MyWidget1");
    myFixture.checkResultByFile(BASE_PATH + "layout_widget_after.xml");
  }
}
