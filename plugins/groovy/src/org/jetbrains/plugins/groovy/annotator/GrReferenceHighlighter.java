/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.annotator;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class GrReferenceHighlighter extends TextEditorHighlightingPass {
  @NotNull private final GroovyFile myFile;
  @Nullable private List<HighlightInfo> myInfos = null;

  protected GrReferenceHighlighter(@Nullable Document document, @NotNull GroovyFile file) {
    super(file.getProject(), document);
    myFile = file;
  }

  @Override
  public void doCollectInformation(@NotNull ProgressIndicator progress) {
    myInfos = new ArrayList<HighlightInfo>();
    myFile.accept(new GroovyRecursiveElementVisitor() {
      @Override
      public void visitReferenceExpression(GrReferenceExpression referenceExpression) {
        super.visitReferenceExpression(referenceExpression);
        visit(referenceExpression);
      }

      private void visit(GrReferenceElement element) {
        final PsiElement resolved = element.resolve();
        final TextAttributesKey attribute = GrHighlightUtil.getDeclarationHighlightingAttribute(resolved);
        if (attribute != null) {
          final PsiElement refNameElement = GrHighlightUtil.getElementToHighlight(element);
          myInfos.add(HighlightInfo.createHighlightInfo(HighlightInfoType.INFORMATION, refNameElement, null, attribute));
        }
      }

      @Override
      public void visitCodeReferenceElement(GrCodeReferenceElement refElement) {
        super.visitCodeReferenceElement(refElement);
        visit(refElement);
      }

      @Override
      public void visitAnnotation(GrAnnotation annotation) {
        super.visitAnnotation(annotation);
        visit(annotation.getClassReference());
      }
    });
  }

  @Override
  public void doApplyInformationToEditor() {
    if (myInfos == null) return;
    UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, 0, myFile.getTextLength(), myInfos, getColorsScheme(), getId());
  }
}
