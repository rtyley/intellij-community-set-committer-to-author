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

package com.intellij.uiDesigner.binding;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.compiler.AsmCodeGenerator;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class GeneratedCodeFoldingPass extends TextEditorHighlightingPass {
  private final PsiFile myPsiFile;
  private final Editor myEditor;
  private final List<TextRange> myFoldingData = new ArrayList<TextRange>();
  
  protected GeneratedCodeFoldingPass(@NotNull PsiFile psiFile, @NotNull Editor editor) {
    super(psiFile.getProject(), editor.getDocument(), false);
    myPsiFile = psiFile;
    myEditor = editor;
  }

  public void doCollectInformation(final ProgressIndicator progress) {
    myPsiFile.accept(new MyFoldingVisitor(progress));
  }

  public void doApplyInformationToEditor() {
    myEditor.getFoldingModel().runBatchFoldingOperation(new Runnable() {
      public void run() {
        FoldingModel foldingModel = myEditor.getFoldingModel();
        synchronized (myFoldingData) {
          for(TextRange foldingData: myFoldingData) {
            final int startOffset = foldingData.getStartOffset();
            final int endOffset = foldingData.getEndOffset();

            boolean generatedCodeUnfolded = false;
            FoldRegion[] regions = foldingModel.getAllFoldRegions();
            for(FoldRegion region: regions) {
              if (region.getPlaceholderText().equals(UIDesignerBundle.message("uidesigner.generated.code.folding.placeholder.text")) &&
                region.isExpanded()) {
                generatedCodeUnfolded = true;
              }
              if (region.getStartOffset() >= startOffset && region.getEndOffset() <= endOffset) {
                foldingModel.removeFoldRegion(region);
              }
            }

            final FoldRegion region =
              foldingModel.addFoldRegion(startOffset, endOffset, UIDesignerBundle.message("uidesigner.generated.code.folding.placeholder.text"));
            if (region != null && !generatedCodeUnfolded) {
              region.setExpanded(false);
            }
          }
        }
      }
    });
  }

  private static boolean isGeneratedUIInitializer(PsiClassInitializer initializer) {
    PsiCodeBlock body = initializer.getBody();
    if (body.getStatements().length != 1) return false;
    PsiStatement statement = body.getStatements()[0];
    if (!(statement instanceof PsiExpressionStatement) ||
        !(((PsiExpressionStatement)statement).getExpression() instanceof PsiMethodCallExpression)) {
      return false;
    }

    PsiMethodCallExpression call = (PsiMethodCallExpression)((PsiExpressionStatement)statement).getExpression();
    return AsmCodeGenerator.SETUP_METHOD_NAME.equals(call.getMethodExpression().getReferenceName());
  }

  private class MyFoldingVisitor extends JavaRecursiveElementWalkingVisitor {
    private final ProgressIndicator myProgress;
    private PsiElement myLastElement;

    private MyFoldingVisitor(final ProgressIndicator progress) {
      myProgress = progress;
    }

    @Override
      public void visitMethod(PsiMethod method) {
      myProgress.checkCanceled();
      if (AsmCodeGenerator.SETUP_METHOD_NAME.equals(method.getName()) ||
          AsmCodeGenerator.GET_ROOT_COMPONENT_METHOD_NAME.equals(method.getName()) ||
          AsmCodeGenerator.LOAD_BUTTON_TEXT_METHOD.equals(method.getName()) ||
          AsmCodeGenerator.LOAD_LABEL_TEXT_METHOD.equals(method.getName())) {
        addFoldingData(method);
      }
    }

    @Override
      public void visitClassInitializer(PsiClassInitializer initializer) {
      myProgress.checkCanceled();
      if (isGeneratedUIInitializer(initializer)) {
        addFoldingData(initializer);
      }
    }

    private void addFoldingData(final PsiElement element) {
      PsiElement prevSibling = PsiTreeUtil.skipSiblingsBackward(element, PsiWhiteSpace.class);
      synchronized (myFoldingData) {
        if (myLastElement == null || prevSibling != myLastElement) {
          myFoldingData.add(element.getTextRange());
        }
        else {
          TextRange lastRange = myFoldingData.get(myFoldingData.size()-1);
          myFoldingData.set(myFoldingData.size()-1, new TextRange(lastRange.getStartOffset(), element.getTextRange().getEndOffset()));
        }
      }
      myLastElement =  element;
    }
  }
}
