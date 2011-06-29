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

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pass;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.refactoring.introduce.inplace.AbstractInplaceIntroducer;
import com.intellij.refactoring.introduceField.IntroduceFieldHandler;
import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 * Date: 3/16/11
 */
public class InplaceIntroduceFieldTest extends AbstractInplaceIntroduceTest {

  private static final String BASE_PATH = "/refactoring/inplaceIntroduceField/";

  public void testAnchor() throws Exception {

    doTest(new Pass<AbstractInplaceIntroducer>() {
      @Override
      public void pass(AbstractInplaceIntroducer inplaceIntroduceFieldPopup) {
      }
    });
  }

  public void testReplaceAll() throws Exception {

    doTest(new Pass<AbstractInplaceIntroducer>() {
      @Override
      public void pass(AbstractInplaceIntroducer inplaceIntroduceFieldPopup) {
        inplaceIntroduceFieldPopup.setReplaceAllOccurrences(true);
      }
    });
  }

  public void testEscapePosition() throws Exception {
    doTestEscape();
  }

  public void testEscapePositionOnLocal() throws Exception {
    doTestEscape();
  }

  @Override
  protected String getBasePath() {
    return BASE_PATH;
  }

  @Override
  protected MyIntroduceHandler createIntroduceHandler() {
    return new MyIntroduceFieldHandler();
  }

  public static class MyIntroduceFieldHandler extends IntroduceFieldHandler implements MyIntroduceHandler  {
    @Override
    public boolean invokeImpl(Project project, @NotNull PsiExpression selectedExpr, Editor editor) {
      return super.invokeImpl(project, selectedExpr, editor);
    }

    @Override
    public boolean invokeImpl(Project project, PsiLocalVariable localVariable, Editor editor) {
      return super.invokeImpl(project, localVariable, editor);
    }
  }
}
