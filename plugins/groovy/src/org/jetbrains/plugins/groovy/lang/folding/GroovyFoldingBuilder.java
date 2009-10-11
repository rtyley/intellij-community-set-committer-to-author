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

package org.jetbrains.plugins.groovy.lang.folding;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiComment;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.codeInsight.folding.JavaCodeFoldingSettings;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ilyas
 */
public class GroovyFoldingBuilder implements FoldingBuilder, GroovyElementTypes, DumbAware {

  @NotNull
  public FoldingDescriptor[] buildFoldRegions(@NotNull ASTNode node, @NotNull Document document) {
    List<FoldingDescriptor> descriptors = new ArrayList<FoldingDescriptor>();
    appendDescriptors(node.getPsi(), document, descriptors);
    return descriptors.toArray(new FoldingDescriptor[descriptors.size()]);
  }

  private void appendDescriptors(PsiElement element, Document document, List<FoldingDescriptor> descriptors) {
    ASTNode node = element.getNode();
    if (node == null) return;
    IElementType type = node.getElementType();

    if (BLOCK_SET.contains(type) || type == CLOSABLE_BLOCK) {
      if (isMultiline(element)) {
        descriptors.add(new FoldingDescriptor(node, node.getTextRange()));
      }
    }
    // comments
    if ((type.equals(mML_COMMENT) || type.equals(GROOVY_DOC_COMMENT)) &&
        isMultiline(element) &&
        isWellEndedComment(element)) {
      descriptors.add(new FoldingDescriptor(node, node.getTextRange()));
    }

    PsiElement child = element.getFirstChild();
    while (child != null) {
      appendDescriptors(child, document, descriptors);
      child = child.getNextSibling();
    }

    if (element instanceof GroovyFile) {
      GroovyFile file = (GroovyFile)element;
      addFoldingsForImports(descriptors, file);
    }

  }

  private static void addFoldingsForImports(final List<FoldingDescriptor> descriptors, final GroovyFile file) {
    final GrImportStatement[] statements = file.getImportStatements();
    if (statements.length > 1) {
      PsiElement first = statements[0];
      while (first != null) {
        PsiElement marker = first;
        PsiElement next = first.getNextSibling();
        while (next instanceof GrImportStatement || next instanceof LeafPsiElement) {
          if (next instanceof GrImportStatement) marker = next;
          next = next.getNextSibling();
        }
        if (marker != first) {
          int start = first.getTextRange().getStartOffset();
          int end = marker.getTextRange().getEndOffset();
          int tail = "import ".length();
          if (start + tail < end) {
            descriptors.add(new FoldingDescriptor(first.getNode(), new TextRange(start + tail, end)));
          }
        }
        while (!(next instanceof GrImportStatement) && next !=null) next = next.getNextSibling();
        first = next;
      }
    }
  }

  private boolean isWellEndedComment(PsiElement element) {
    if (element instanceof PsiComment) {
      PsiComment comment = (PsiComment) element;
      ASTNode node = comment.getNode();
      if (node != null &&
          node.getElementType() == GroovyTokenTypes.mML_COMMENT &&
          node.getText().endsWith("*/")) return true;
    }
    return true;
  }

  private static boolean isMultiline(PsiElement element) {
    String text = element.getText();
    return text.contains("\n") || text.contains("\r") || text.contains("\r\n");
  }

  public String getPlaceholderText(@NotNull ASTNode node) {
    final IElementType elemType = node.getElementType();
    if (BLOCK_SET.contains(elemType) || elemType == CLOSABLE_BLOCK) {
      return "{...}";
    }
    if (elemType.equals(mML_COMMENT)) {
      return "/*...*/";
    }
    if (elemType.equals(GROOVY_DOC_COMMENT)) {
      return "/**...*/";
    }
    if (IMPORT_STATEMENT.equals(elemType)) {
      return "...";
    }
    return null;
  }

  public boolean isCollapsedByDefault(@NotNull ASTNode node) {
    return node.getElementType() == IMPORT_STATEMENT && JavaCodeFoldingSettings.getInstance().isCollapseImports();
  }
}
