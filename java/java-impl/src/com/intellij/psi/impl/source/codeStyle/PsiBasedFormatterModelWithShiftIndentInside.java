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

/*
 * @author max
 */
package com.intellij.psi.impl.source.codeStyle;

import com.intellij.formatting.Block;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.TokenType;
import com.intellij.psi.formatter.DocumentBasedFormattingModel;
import com.intellij.psi.formatter.FormatterUtil;
import com.intellij.psi.formatter.FormattingDocumentModelImpl;
import com.intellij.psi.formatter.PsiBasedFormattingModel;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class PsiBasedFormatterModelWithShiftIndentInside extends PsiBasedFormattingModel {
  private static final Logger LOG =
      Logger.getInstance("#com.intellij.psi.impl.source.codeStyle.PsiBasedFormatterModelWithShiftIndentInside");

  private final Project myProject;

  public PsiBasedFormatterModelWithShiftIndentInside(final PsiFile file,
                                                     @NotNull final Block rootBlock,
                                                     final FormattingDocumentModelImpl documentModel) {
    super(file, rootBlock, documentModel);
    myProject = file.getProject();
  }

  public TextRange shiftIndentInsideRange(TextRange textRange, int shift) {
    return shiftIndentInsideWithPsi(textRange, shift);
  }

  public void commitChanges() {
  }


  private TextRange shiftIndentInsideWithPsi(final TextRange textRange, final int shift) {
    final int offset = textRange.getStartOffset();

    ASTNode leafElement = findElementAt(offset);
    while (leafElement != null && !leafElement.getTextRange().equals(textRange)) {
      leafElement = leafElement.getTreeParent();
    }

    if (leafElement != null && leafElement.getTextRange().equals(textRange) && ShiftIndentInsideHelper.mayShiftIndentInside(leafElement)) {
      return new ShiftIndentInsideHelper(StdFileTypes.JAVA, myProject).shiftIndentInside(leafElement, shift).getTextRange();
    } else {
      return textRange;
    }

  }

  protected String replaceWithPsiInLeaf(final TextRange textRange, String whiteSpace, ASTNode leafElement) {
     if (!myCanModifyAllWhiteSpaces) {
       if (leafElement.getElementType() == TokenType.WHITE_SPACE) return null;
       ASTNode prevNode = TreeUtil.prevLeaf(leafElement);

       if (prevNode != null) {
         IElementType type = prevNode.getElementType();
         if(type == TokenType.WHITE_SPACE) {
           final String text = prevNode.getText();

           final @NonNls String cdataStartMarker = "<![CDATA[";
           final int cdataPos = text.indexOf(cdataStartMarker);
           if (cdataPos != -1 && whiteSpace.indexOf(cdataStartMarker) == -1) {
             whiteSpace = DocumentBasedFormattingModel.mergeWsWithCdataMarker(whiteSpace, text, cdataPos);
             if (whiteSpace == null) return null;
           }

           prevNode = TreeUtil.prevLeaf(prevNode);
           type = prevNode != null ? prevNode.getElementType():null;
         }

         final @NonNls String cdataEndMarker = "]]>";
         if(type == XmlElementType.XML_CDATA_END && whiteSpace.indexOf(cdataEndMarker) == -1) {
           final ASTNode at = findElementAt(prevNode.getStartOffset());

           if (at != null && at.getPsi() instanceof PsiWhiteSpace) {
             final String s = at.getText();
             final int cdataEndPos = s.indexOf(cdataEndMarker);
             whiteSpace = DocumentBasedFormattingModel.mergeWsWithCdataMarker(whiteSpace, s, cdataEndPos);
             leafElement = at;
           } else {
             whiteSpace = null;
           }
           if (whiteSpace == null) return null;
         }
       }
     }
     FormatterUtil.replaceWhiteSpace(whiteSpace, leafElement, TokenType.WHITE_SPACE, textRange);
     return whiteSpace;
   }
}
