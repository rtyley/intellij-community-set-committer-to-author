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

package org.jetbrains.plugins.groovy.formatter.processors;

import com.intellij.formatting.Indent;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.formatter.GroovyBlock;
import org.jetbrains.plugins.groovy.lang.editor.actions.GroovyEditorActionUtil;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrThrowsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.formatter.GrControlStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConditionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCommandArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocTag;

/**
 * @author ilyas
 */
public abstract class GroovyIndentProcessor implements GroovyElementTypes {
  public static final int GDOC_COMMENT_INDENT = 1;

  /**
   * Calculates indent, based on code style, between parent block and child node
   *
   * @param parent        parent block
   * @param child         child node
   * @param prevChildNode previous child node
   * @return indent
   */
  @NotNull
  public static Indent getChildIndent(@NotNull final GroovyBlock parent, @Nullable final ASTNode prevChildNode, @NotNull final ASTNode child) {
    ASTNode astNode = parent.getNode();
    final PsiElement psiParent = astNode.getPsi();

    // For Groovy file
    if (psiParent instanceof GroovyFileBase) {
      return Indent.getNoneIndent();
    }

    if (GroovyEditorActionUtil.GSTRING_TOKENS_INNER.contains(child.getElementType()) &&
        GroovyTokenTypes.mGSTRING_SINGLE_BEGIN != child.getElementType()) {
      return Indent.getAbsoluteNoneIndent();
    }

    if (psiParent instanceof GrListOrMap) {
      if (mLBRACK.equals(child.getElementType()) ||
          mRBRACK.equals(child.getElementType())) {
        return Indent.getNoneIndent();
      } else {
        return Indent.getContinuationWithoutFirstIndent();
      }
    }

    // For common code block
    if (BLOCK_SET.contains(astNode.getElementType()) &&
        !BLOCK_STATEMENT.equals(astNode.getElementType())) {
      return indentForBlock(psiParent, child);
    }

    if (CASE_SECTION.equals(astNode.getElementType())) {
      return indentForCaseSection(psiParent, child);
    }

    if (SWITCH_STATEMENT.equals(astNode.getElementType())) {
      return indentForSwitchStatement(psiParent, child);
    }

    // For labels
    if (child.getPsi() instanceof GrLabel) {
      return Indent.getLabelIndent();
    }

    // for control structures
    if (psiParent instanceof GrControlStatement) {
      return getControlIndent(psiParent, child);
    }

    if (psiParent instanceof GrExpression) {
      return getExpressionIndent(psiParent, child);
    }

    //For parameter lists
    if (psiParent instanceof GrParameterList ||
        psiParent instanceof GrExtendsClause ||
        psiParent instanceof GrThrowsClause) {
      if (parent.getIndent() != null) {
        return Indent.getContinuationWithoutFirstIndent();
      }
      return Indent.getNoneIndent();
    }

    // For arguments
    if (psiParent instanceof GrArgumentList ||
        psiParent instanceof GrCommandArgumentList) {
      if (child.getElementType() != GroovyTokenTypes.mLPAREN &&
          child.getElementType() != GroovyTokenTypes.mRPAREN) {
        return Indent.getContinuationIndent();
      }
    }

    if ((psiParent instanceof GrDocComment &&
        child.getElementType() != mGDOC_COMMENT_START) ||
        psiParent instanceof GrDocTag &&
            child.getElementType() != mGDOC_TAG_NAME) {
      return Indent.getSpaceIndent(GDOC_COMMENT_INDENT);
    }

    return Indent.getNoneIndent();
  }

  /**
   * Returns indent for simple expressions
   *
   * @param psiParent
   * @param child
   * @return
   */
  private static Indent getExpressionIndent(PsiElement psiParent, ASTNode child) {
    // Assignment expression
    if (psiParent instanceof GrAssignmentExpression &&
        child.getPsi().equals(((GrAssignmentExpression) psiParent).getRValue())) {
      return Indent.getNormalIndent();
    }
    // Conditional expression
    if (psiParent instanceof GrConditionalExpression &&
        (child.getPsi().equals(((GrConditionalExpression) psiParent).getThenBranch()) ||
            child.getPsi().equals(((GrConditionalExpression) psiParent).getElseBranch()))) {
      return Indent.getNormalIndent();
    }
    // Property selection

    return Indent.getNoneIndent();
  }

  private static Indent getControlIndent(PsiElement psiParent, ASTNode child) {
    if (psiParent instanceof GrIfStatement) {
      if ((child.getPsi().equals(((GrIfStatement) psiParent).getThenBranch()) ||
          child.getPsi().equals(((GrIfStatement) psiParent).getElseBranch())) &&
          !BLOCK_SET.contains(child.getElementType())) {
        return Indent.getNormalIndent();
      }
      if (child.getPsi().equals(((GrIfStatement) psiParent).getCondition())) {
        return Indent.getContinuationWithoutFirstIndent();
      }
    }
    if (psiParent instanceof GrWhileStatement) {
      if (child.getPsi().equals(((GrWhileStatement) psiParent).getBody()) &&
          !BLOCK_SET.contains(child.getElementType())) {
        return Indent.getNormalIndent();
      }
      if (child.getPsi().equals(((GrWhileStatement) psiParent).getCondition())) {
        return Indent.getContinuationWithoutFirstIndent();
      }
    }
    if (psiParent instanceof GrSynchronizedStatement) {
      if (child.getPsi().equals(((GrSynchronizedStatement) psiParent).getMonitor())) {
        return Indent.getContinuationWithoutFirstIndent();
      }
    }
    if (psiParent instanceof GrForStatement) {
      if (child.getPsi().equals(((GrForStatement) psiParent).getBody()) &&
          !BLOCK_SET.contains(child.getElementType())) {
        return Indent.getNormalIndent();
      }
      if (child.getPsi().equals(((GrForStatement) psiParent).getClause())) {
        return Indent.getContinuationWithoutFirstIndent();
      }
    }
    return Indent.getNoneIndent();
  }

  /**
   * Indent for common block
   *
   * @param psiBlock
   * @param child
   * @return
   */
  private static Indent indentForBlock(PsiElement psiBlock, ASTNode child) {
    // Common case
    if (mLCURLY.equals(child.getElementType()) ||
        mRCURLY.equals(child.getElementType())) {
      return Indent.getNoneIndent();
    }
    return Indent.getNormalIndent();


  }

  private static Indent indentForCaseSection(PsiElement psiParent, ASTNode child) {
    if (CASE_LABEL.equals(child.getElementType())) {
      return Indent.getNoneIndent();
    }
    return Indent.getNormalIndent();
  }

  private static Indent indentForSwitchStatement(PsiElement psiParent, ASTNode child) {
    if (CASE_SECTION.equals(child.getElementType())) {
      return Indent.getNormalIndent();
    }
    return Indent.getNoneIndent();
  }
}

