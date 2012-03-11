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

import com.intellij.formatting.Spacing;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.formatter.GroovyBlock;
import org.jetbrains.plugins.groovy.formatter.MethodCallWithoutQualifierBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConditionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;

import static org.jetbrains.plugins.groovy.formatter.models.spacing.SpacingTokens.*;
import static org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocTokenTypes.mGDOC_ASTERISKS;
import static org.jetbrains.plugins.groovy.lang.groovydoc.parser.GroovyDocElementTypes.GDOC_INLINED_TAG;
import static org.jetbrains.plugins.groovy.lang.groovydoc.parser.GroovyDocElementTypes.GROOVY_DOC_COMMENT;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mTRIPLE_DOT;
import static org.jetbrains.plugins.groovy.lang.lexer.TokenSets.DOTS;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.METHOD_DEFS;

/**
 * @author ilyas
 */
public abstract class GroovySpacingProcessorBasic {

  private static final Spacing NO_SPACING_WITH_NEWLINE = Spacing.createSpacing(0, 0, 0, true, 1);
  private static final Spacing NO_SPACING = Spacing.createSpacing(0, 0, 0, false, 0);
  private static final Spacing COMMON_SPACING = Spacing.createSpacing(1, 1, 0, true, 100);
  private static final Spacing COMMON_SPACING_WITH_NL = Spacing.createSpacing(1, 1, 1, true, 100);
  private static final Spacing IMPORT_BETWEEN_SPACING = Spacing.createSpacing(0, 0, 1, true, 100);
  private static final Spacing IMPORT_OTHER_SPACING = Spacing.createSpacing(0, 0, 2, true, 100);
  private static final Spacing LAZY_SPACING = Spacing.createSpacing(0, 239, 0, true, 100);

  public static Spacing getSpacing(GroovyBlock child1, GroovyBlock child2, CommonCodeStyleSettings settings) {

    ASTNode leftNode = child1.getNode();
    ASTNode rightNode = child2.getNode();
    final PsiElement left = leftNode.getPsi();
    final PsiElement right = rightNode.getPsi();

    IElementType leftType = leftNode.getElementType();
    IElementType rightType = rightNode.getElementType();

    //Braces Placement
    // For multi-line strings
    if (!mirrorsAst(child1) || !mirrorsAst(child2)) {
      return NO_SPACING;
    }

    if (leftType == mGDOC_COMMENT_START && rightType == mGDOC_COMMENT_DATA
        || leftType == mGDOC_COMMENT_DATA && rightType == mGDOC_COMMENT_END) {
      return LAZY_SPACING;
    }

    //For type parameters
    if (mLT == leftType && right instanceof GrTypeParameter ||
        mGT == rightType && left instanceof GrTypeParameter ||
        mIDENT == leftType && right instanceof GrTypeParameterList) {
      return NO_SPACING;
    }

    if (ARGUMENTS.equals(rightType)) {
      return NO_SPACING;
    }
    // For left square bracket in array declarations and selections by index
    if ((mLBRACK.equals(rightType) &&
         rightNode.getTreeParent() != null &&
         INDEX_OR_ARRAY.contains(rightNode.getTreeParent().getElementType())) ||
        ARRAY_DECLARATOR.equals(rightType)) {
      return NO_SPACING;
    }

    if (METHOD_DEFS.contains(leftType)) {
      if (rightType == mSEMI) {
        return NO_SPACING;
      }
      return Spacing.createSpacing(0, 0, settings.BLANK_LINES_AROUND_METHOD + 1, settings.KEEP_LINE_BREAKS, 100);
    }

    if (METHOD_DEFS.contains(rightType)) {
      if (leftNode.getElementType() == GROOVY_DOC_COMMENT) {
        return Spacing.createSpacing(0, 0, settings.BLANK_LINES_AROUND_METHOD, settings.KEEP_LINE_BREAKS, 0);
      }
      return Spacing.createSpacing(0, 0, settings.BLANK_LINES_AROUND_METHOD + 1, settings.KEEP_LINE_BREAKS, 100);
    }

    if (leftType == mLCURLY && rightType == PARAMETERS_LIST) { //closure
      return LAZY_SPACING;
    }

    // For parentheses in arguments and typecasts
    if (LEFT_BRACES.contains(leftType) || RIGHT_BRACES.contains(rightType)) {
      return NO_SPACING_WITH_NEWLINE;
    }

    if (right != null && right instanceof GrTypeArgumentList) {
      return NO_SPACING_WITH_NEWLINE;
    }

/********** punctuation marks ************/
    // For dots, commas etc.
    if ((PUNCTUATION_SIGNS.contains(rightType)) ||
        (mCOLON.equals(rightType) && !(right.getParent() instanceof GrConditionalExpression))) {
      return NO_SPACING_WITH_NEWLINE;
    }

    if (DOTS.contains(leftType)) {
      return NO_SPACING_WITH_NEWLINE;
    }

/********** imports ************/
    if (IMPORT_STATEMENT.equals(leftType) && IMPORT_STATEMENT.equals(rightType)) {
      return IMPORT_BETWEEN_SPACING;
    }
    if ((IMPORT_STATEMENT.equals(leftType) &&
         (!IMPORT_STATEMENT.equals(rightType) && !mSEMI.equals(rightType))) ||
        ((!IMPORT_STATEMENT.equals(leftType) && !mSEMI.equals(leftType)) && IMPORT_STATEMENT.equals(rightType))) {
      return IMPORT_OTHER_SPACING;
    }

    //todo:check it for multiple assignments
    if ((VARIABLE_DEFINITION.equals(leftType) || VARIABLE_DEFINITION.equals(rightType)) &&
        !(leftNode.getTreeNext() instanceof PsiErrorElement)) {
      return Spacing.createSpacing(0, 0, 1, false, 100);
    }

/********** exclusions ************/
    // For << and >> ...
    if ((mLT.equals(leftType) && mLT.equals(rightType)) ||
        (mGT.equals(leftType) && mGT.equals(rightType))) {
      return NO_SPACING_WITH_NEWLINE;
    }

    // Unary and postfix expressions
    if (PREFIXES.contains(leftType) ||
        POSTFIXES.contains(rightType) ||
        (PREFIXES_OPTIONAL.contains(leftType) && left.getParent() instanceof GrUnaryExpression)) {
      return NO_SPACING_WITH_NEWLINE;
    }

    if (RANGES.contains(leftType) || RANGES.contains(rightType)) {
      return NO_SPACING_WITH_NEWLINE;
    }

    if (mGDOC_ASTERISKS == leftType && mGDOC_COMMENT_DATA == rightType) {
      String text = rightNode.getText();
      if (text.length() > 0 && !StringUtil.startsWithChar(text, ' ')) {
        return COMMON_SPACING;
      }
      return NO_SPACING;
    }

    if (leftType == mGDOC_TAG_VALUE_TOKEN && rightType == mGDOC_COMMENT_DATA) {
      return LAZY_SPACING;
    }

    if (left instanceof GrStatement &&
        right instanceof GrStatement &&
        left.getParent() instanceof GrStatementOwner &&
        right.getParent() instanceof GrStatementOwner) {
      return COMMON_SPACING_WITH_NL;
    }

    if (rightType == mGDOC_INLINE_TAG_END ||
        leftType == mGDOC_INLINE_TAG_START ||
        rightType == mGDOC_INLINE_TAG_START ||
        leftType == mGDOC_INLINE_TAG_END) {
      return NO_SPACING;
    }

    if ((leftType == GDOC_INLINED_TAG && rightType == mGDOC_COMMENT_DATA)
      || (leftType == mGDOC_COMMENT_DATA && rightType == GDOC_INLINED_TAG))
    {
      // Keep formatting between groovy doc text and groovy doc reference tag as is.
      return NO_SPACING;
    }

    if (leftType == CLASS_TYPE_ELEMENT && rightType == mTRIPLE_DOT) {
      return NO_SPACING;
    }

    // diamonds
    if (rightType == mLT || rightType == mGT) {
      if (right.getParent() instanceof GrCodeReferenceElement) {
        PsiElement p = right.getParent().getParent();
        if (p instanceof GrNewExpression || p instanceof GrAnonymousClassDefinition) {
          return NO_SPACING;
        }
      }
    }

    return COMMON_SPACING;
  }

  private static boolean mirrorsAst(GroovyBlock block) {
    return block.getNode().getTextRange().equals(block.getTextRange()) || block instanceof MethodCallWithoutQualifierBlock;
  }
}
