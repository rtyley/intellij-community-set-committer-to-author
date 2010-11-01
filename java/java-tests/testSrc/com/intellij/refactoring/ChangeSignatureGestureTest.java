/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actions.EditorActionUtil;
import com.intellij.psi.PsiManager;
import com.intellij.refactoring.changeSignature.ChangeSignatureDetectorAction;
import com.intellij.refactoring.changeSignature.ChangeSignatureGestureDetector;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import junit.framework.Assert;

import java.util.List;

/**
 * User: anna
 * Date: Sep 9, 2010
 */
public class ChangeSignatureGestureTest extends LightCodeInsightFixtureTestCase {

  private void doTest(final Runnable run, boolean shouldShow) {
    myFixture.configureByFile("/refactoring/changeSignatureGesture/" + getTestName(false) + ".java");
    final ChangeSignatureGestureDetector detector = ChangeSignatureGestureDetector.getInstance(getProject());
    final Document document = myFixture.getEditor().getDocument();
    try {
      PsiManager.getInstance(getProject()).addPsiTreeChangeListener(detector);
      detector.addDocListener(document);
      new WriteCommandAction.Simple(getProject()) {
        @Override
        protected void run() throws Throwable {
          run.run();
        }
      }.execute().throwException();


      myFixture.doHighlighting();
      final String hint = ChangeSignatureDetectorAction.CHANGE_SIGNATURE;
      if (shouldShow) {
        final IntentionAction intention = myFixture.findSingleIntention(hint);
        myFixture.launchAction(intention);
        myFixture.checkResultByFile("/refactoring/changeSignatureGesture/" + getTestName(false) + "_after.java");
      } else {
        final List<IntentionAction> intentionActions = myFixture.filterAvailableIntentions(hint);
        Assert.assertEquals(true, intentionActions.isEmpty());
      }
    }
    finally {
      detector.removeDocListener(document);
      PsiManager.getInstance(getProject()).removePsiTreeChangeListener(detector);
    }
  }

  public void testSimple() {
    doTypingTest("param");
  }

  public void testNewParam() {
    doTypingTest(", int param");
  }

  public void testNewParamInSuper() {
    doTypingTest(", int param");
  }

  public void testNewParamInSuperUsed() {
    doTypingTest(", int param");
  }

  private void doTypingTest(final String param) {
    doTest(new Runnable() {
      @Override
      public void run() {
        myFixture.type(param);
      }
    }, true);
  }

  public void testReturnValue() {
    doTypingNoBorderTest("void");
  }

  public void testModifier() {
    doTypingNoBorderTest("private");
  }

  public void testAddParameterFinal() {
    doTypingTest("final int param");
  }

  private void doTypingNoBorderTest(final String param) {
    doTest(new Runnable() {
      @Override
      public void run() {
        myFixture.type(param);
      }
    }, false);
  }

  public void testDeleteParamInSuperUsed() {
    doDeleteTest();
  }

  private void doDeleteTest() {
    doTest(new Runnable() {
      @Override
      public void run() {
        final Editor editor = myFixture.getEditor();
        final Document document = editor.getDocument();
        final int selectionStart = editor.getSelectionModel().getSelectionStart();
        final int selectionEnd = editor.getSelectionModel().getSelectionEnd();
        CommandProcessor.getInstance().setCurrentCommandGroupId(EditorActionUtil.DELETE_COMMAND_GROUP);
        document.deleteString(selectionStart, selectionEnd);
        editor.getCaretModel().moveToOffset(selectionStart);
      }
    }, true);
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath();
  }
}
