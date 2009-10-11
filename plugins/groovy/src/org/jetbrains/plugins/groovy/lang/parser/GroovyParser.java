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

package org.jetbrains.plugins.groovy.lang.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.Separators;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.*;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.blocks.OpenOrClosableBlock;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.declaration.Declaration;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.ConditionalExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.ExpressionStatement;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.StrictContextExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.imports.ImportStatement;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.TypeDefinition;
import org.jetbrains.plugins.groovy.lang.parser.parsing.toplevel.CompilationUnit;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * Parser for Groovy script files
 *
 * @author ilyas, Dmitry.Krasilschikov
 */
public class GroovyParser implements PsiParser {

  @NotNull
  public ASTNode parse(IElementType root, PsiBuilder builder) {
    //builder.setDebugMode(true);
    PsiBuilder.Marker rootMarker = builder.mark();
    CompilationUnit.parse(builder, this);
    rootMarker.done(root);
    return builder.getTreeBuilt();

  }

  public boolean parseForStatement(PsiBuilder builder) {
    PsiBuilder.Marker marker = builder.mark();

    ParserUtils.getToken(builder, GroovyTokenTypes.kFOR);
    if (!ParserUtils.getToken(builder, GroovyTokenTypes.mLPAREN, GroovyBundle.message("lparen.expected"))) {
      marker.done(GroovyElementTypes.FOR_STATEMENT);
      return true;
    }
    if (!ForStatement.forClauseParse(builder, this)) {
      builder.error(GroovyBundle.message("for.clause.expected"));
      marker.done(GroovyElementTypes.FOR_STATEMENT);
      return true;
    }

    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);

    if (!ParserUtils.getToken(builder, GroovyTokenTypes.mRPAREN, GroovyBundle.message("rparen.expected"))) {
      while (!builder.eof() && GroovyTokenTypes.mNLS.equals(builder.getTokenType())){
        builder.advanceLexer();
      }
      marker.done(GroovyElementTypes.FOR_STATEMENT);
      return true;
    }

    PsiBuilder.Marker warn = builder.mark();
    if (builder.getTokenType() == GroovyTokenTypes.mNLS) {
      ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
    }

    if (parseExtendedStatement(builder)) {
      warn.rollbackTo();
      marker.done(GroovyElementTypes.FOR_STATEMENT);
      return true;
    }

    if (!parseStatement(builder, true)) {
      warn.rollbackTo();
      builder.error(GroovyBundle.message("expression.expected"));
      marker.done(GroovyElementTypes.FOR_STATEMENT);
      return true;
    } else {
      warn.drop();
      marker.done(GroovyElementTypes.FOR_STATEMENT);
      return true;
    }
  }

  public boolean parseIfStatement(PsiBuilder builder) {
    //allow error messages
    PsiBuilder.Marker ifStmtMarker = builder.mark();

    if (!ParserUtils.getToken(builder, GroovyTokenTypes.kIF)) {
      ifStmtMarker.rollbackTo();
      builder.error(GroovyBundle.message("if.expected"));
      return false;
    }

    if (!ParserUtils.getToken(builder, GroovyTokenTypes.mLPAREN, GroovyBundle.message("lparen.expected"))) {
//      ifStmtMarker.done(IF_STATEMENT);
//      return IF_STATEMENT;
      ifStmtMarker.drop();
      return false;
    }

    if (!ConditionalExpression.parse(builder, this)) {
      builder.error(GroovyBundle.message("expression.expected"));
    }

    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);

    if (!ParserUtils.getToken(builder, GroovyTokenTypes.mRPAREN, GroovyBundle.message("rparen.expected"))) {
      final IElementType type = builder.getTokenType();
      while (!builder.eof()) {
        if (GroovyTokenTypes.mNLS.equals(type) || GroovyTokenTypes.mRPAREN.equals(type) ||
            GroovyTokenTypes.mLCURLY.equals(type) || GroovyTokenTypes.mRCURLY.equals(type)) {
          break;
        }

        builder.advanceLexer();
        builder.error(GroovyBundle.message("rparen.expected"));
      }
      if (!ParserUtils.getToken(builder, GroovyTokenTypes.mRPAREN)) {
        ifStmtMarker.done(GroovyElementTypes.IF_STATEMENT);
        return true;
      }
    }

    PsiBuilder.Marker warn = builder.mark();
    if (builder.getTokenType() == GroovyTokenTypes.mNLS) {
      ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
    }

    if (!parseStatement(builder, true) && !parseExtendedStatement(builder)) {
      warn.rollbackTo();
      builder.error(GroovyBundle.message("expression.expected"));
      ifStmtMarker.done(GroovyElementTypes.IF_STATEMENT);
      return true;
    } else {
      warn.drop();
    }

    PsiBuilder.Marker rb = builder.mark();
    if (GroovyTokenTypes.kELSE.equals(builder.getTokenType()) ||
        (Separators.parse(builder) &&
            builder.getTokenType() == GroovyTokenTypes.kELSE)) {
      rb.drop();
      ParserUtils.getToken(builder, GroovyTokenTypes.kELSE);

      warn = builder.mark();
      if (builder.getTokenType() == GroovyTokenTypes.mNLS) {
        ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
      }

      if (!parseStatement(builder, true) && !parseExtendedStatement(builder)) {
        warn.rollbackTo();
        builder.error(GroovyBundle.message("expression.expected"));
        ifStmtMarker.done(GroovyElementTypes.IF_STATEMENT);
        return true;
      } else {
        warn.drop();
      }

      ifStmtMarker.done(GroovyElementTypes.IF_STATEMENT);
      return true;

    } else {
      rb.rollbackTo();
      ifStmtMarker.done(GroovyElementTypes.IF_STATEMENT);
      return true;
    }
  }

  /**
   * Parses list of statements after case label(s)
   *
   * @param builder
   */
  public void parseSwitchCaseList(PsiBuilder builder) {

    if (GroovyTokenTypes.kCASE.equals(builder.getTokenType()) ||
        GroovyTokenTypes.kDEFAULT.equals(builder.getTokenType()) ||
        GroovyTokenTypes.mRCURLY.equals(builder.getTokenType())) {
      return;
    }

    if (!parseStatement(builder, false) && !parseExtendedStatement(builder)) {
      builder.error(GroovyBundle.message("wrong.statement"));
      return;
    }

    while (parseExtendedStatement(builder)) {
      if (GroovyTokenTypes.mSEMI.equals(builder.getTokenType()) || GroovyTokenTypes.mNLS.equals(builder.getTokenType())) {
        Separators.parse(builder);
      }
    }
    if (GroovyTokenTypes.mSEMI.equals(builder.getTokenType()) || GroovyTokenTypes.mNLS.equals(builder.getTokenType())) {
      Separators.parse(builder);
    }

    if (GroovyTokenTypes.kCASE.equals(builder.getTokenType()) ||
        GroovyTokenTypes.kDEFAULT.equals(builder.getTokenType()) ||
        GroovyTokenTypes.mRCURLY.equals(builder.getTokenType())) {
      return;
    }
    boolean result = parseStatement(builder, false);
    while (result && (GroovyTokenTypes.mSEMI.equals(builder.getTokenType()) || GroovyTokenTypes.mNLS.equals(builder.getTokenType())) ||
           parseExtendedStatement(builder)) {

      if (GroovyTokenTypes.mSEMI.equals(builder.getTokenType()) || GroovyTokenTypes.mNLS.equals(builder.getTokenType())) {
        Separators.parse(builder);
      }
      while (parseExtendedStatement(builder)) {
        if (GroovyTokenTypes.mSEMI.equals(builder.getTokenType()) || GroovyTokenTypes.mNLS.equals(builder.getTokenType())) {
          Separators.parse(builder);
        }
      }
      if (GroovyTokenTypes.mSEMI.equals(builder.getTokenType()) || GroovyTokenTypes.mNLS.equals(builder.getTokenType())) {
        Separators.parse(builder);
      }

      if (GroovyTokenTypes.kCASE.equals(builder.getTokenType()) ||
          GroovyTokenTypes.kDEFAULT.equals(builder.getTokenType()) ||
          GroovyTokenTypes.mRCURLY.equals(builder.getTokenType())) {
        break;
      }

      result = parseStatement(builder, false);
      if (!isExtendedSeparator(builder.getTokenType())) {
        cleanAfterError(builder);
      }
    }
    Separators.parse(builder);
  }

  //gsp directives, scriptlets and such
  protected boolean isExtendedSeparator(final IElementType tokenType) {
    return false;
  }

  //gsp template statement, for example
  protected boolean parseExtendedStatement(PsiBuilder builder) {
    return false;
  }

  public boolean parseWhileStatement(PsiBuilder builder) {

    PsiBuilder.Marker marker = builder.mark();

    ParserUtils.getToken(builder, GroovyTokenTypes.kWHILE);

    if (!ParserUtils.getToken(builder, GroovyTokenTypes.mLPAREN, GroovyBundle.message("lparen.expected"))) {
      marker.done(GroovyElementTypes.WHILE_STATEMENT);
      return true;
    }

    if (!StrictContextExpression.parse(builder, this)) {
      builder.error(GroovyBundle.message("expression.expected"));
    }

    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);

    if (!ParserUtils.getToken(builder, GroovyTokenTypes.mRPAREN, GroovyBundle.message("rparen.expected"))) {
      while (!builder.eof() && !GroovyTokenTypes.mNLS.equals(builder.getTokenType()) && !GroovyTokenTypes.mRPAREN.equals(builder.getTokenType())) {
        builder.advanceLexer();
        builder.error(GroovyBundle.message("rparen.expected"));
      }
      if (!ParserUtils.getToken(builder, GroovyTokenTypes.mRPAREN)) {
        marker.done(GroovyElementTypes.WHILE_STATEMENT);
        return true;
      }
    }

    PsiBuilder.Marker warn = builder.mark();
    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);

    if (!parseStatement(builder, true) && !parseExtendedStatement(builder)) {
      warn.rollbackTo();
      builder.error(GroovyBundle.message("expression.expected"));
      marker.done(GroovyElementTypes.WHILE_STATEMENT);
      return true;
    } else {
      warn.drop();
      marker.done(GroovyElementTypes.WHILE_STATEMENT);
      return true;
    }
  }

  /**
   * Rolls marker forward after possible errors
   *
   * @param builder
   */
  protected void cleanAfterError(PsiBuilder builder) {
    int i = 0;
    PsiBuilder.Marker em = builder.mark();
    while (!builder.eof() &&
        !(GroovyTokenTypes.mNLS.equals(builder.getTokenType()) ||
            GroovyTokenTypes.mRCURLY.equals(builder.getTokenType()) ||
            GroovyTokenTypes.mSEMI.equals(builder.getTokenType())) &&
        !isExtendedSeparator(builder.getTokenType())
        ) {
      builder.advanceLexer();
      i++;
    }
    if (i > 0) {
      em.error(GroovyBundle.message("separator.or.rcurly.expected"));
    } else {
      em.drop();
    }
  }

  public void parseBlockBody(PsiBuilder builder) {


    parseExtendedStatement(builder);
    if (GroovyTokenTypes.mSEMI.equals(builder.getTokenType()) || GroovyTokenTypes.mNLS.equals(builder.getTokenType())) {
      Separators.parse(builder);
    }
    while (parseExtendedStatement(builder)) {
      Separators.parse(builder);
    }

    boolean result = parseStatement(builder, false);

    while (result &&
        (GroovyTokenTypes.mSEMI.equals(builder.getTokenType()) ||
            GroovyTokenTypes.mNLS.equals(builder.getTokenType()) || parseExtendedStatement(builder))) {
      Separators.parse(builder);
      while (parseExtendedStatement(builder)) {
        Separators.parse(builder);
      }
      result = parseStatement(builder, false);
      if (!isExtendedSeparator(builder.getTokenType())) {
        cleanAfterError(builder);
      }
    }
    cleanAfterError(builder);
    Separators.parse(builder);
    while (parseExtendedStatement(builder)) {
      Separators.parse(builder);
    }

  }

  public boolean parseStatement(PsiBuilder builder, boolean isBlockStatementNeeded) {
    if (isBlockStatementNeeded && GroovyTokenTypes.mLCURLY.equals(builder.getTokenType())) {
      return OpenOrClosableBlock.parseBlockStatement(builder, this);
    }

     if (GroovyTokenTypes.kIMPORT.equals(builder.getTokenType())) {
      PsiBuilder.Marker marker = builder.mark();
      ImportStatement.parse(builder, this);
      marker.error(GroovyBundle.message("import.not.allowed"));
      return true;
    }

    if (GroovyTokenTypes.kIF.equals(builder.getTokenType())) {
      return parseIfStatement(builder);
    }
    if (GroovyTokenTypes.kSWITCH.equals(builder.getTokenType())) {
      return SwitchStatement.parse(builder, this);
    }
    if (GroovyTokenTypes.kTRY.equals(builder.getTokenType())) {
      return TryCatchStatement.parse(builder, this);
    }
    if (GroovyTokenTypes.kWHILE.equals(builder.getTokenType())) {
      return parseWhileStatement(builder);
    }
    if (GroovyTokenTypes.kFOR.equals(builder.getTokenType())) {
      return parseForStatement(builder);
    }
    if (ParserUtils.lookAhead(builder, GroovyTokenTypes.kSYNCHRONIZED, GroovyTokenTypes.mLPAREN)) {
      PsiBuilder.Marker synMarker = builder.mark();
      if (SynchronizedStatement.parse(builder, this)) {
        synMarker.drop();
        return true;
      } else {
        synMarker.rollbackTo();
      }
    }

    // Possible errors
    if (GroovyTokenTypes.kELSE.equals(builder.getTokenType())) {
      ParserUtils.wrapError(builder, GroovyBundle.message("else.without.if"));
      parseStatement(builder, true);
      return true;
    }
    if (GroovyTokenTypes.kCATCH.equals(builder.getTokenType())) {
      ParserUtils.wrapError(builder, GroovyBundle.message("catch.without.try"));
      parseStatement(builder, false);
      return true;
    }
    if (GroovyTokenTypes.kFINALLY.equals(builder.getTokenType())) {
      ParserUtils.wrapError(builder, GroovyBundle.message("finally.without.try"));
      parseStatement(builder, false);
      return true;
    }
    if (GroovyTokenTypes.kCASE.equals(builder.getTokenType())) {
      PsiBuilder.Marker marker = builder.mark();
      SwitchStatement.parseCaseLabel(builder, this);
      marker.error(GroovyBundle.message("case.without.switch"));
      parseStatement(builder, false);
      return true;
    }
    if (GroovyTokenTypes.kDEFAULT.equals(builder.getTokenType())) {
      PsiBuilder.Marker marker = builder.mark();
      SwitchStatement.parseCaseLabel(builder, this);
      marker.error(GroovyBundle.message("default.without.switch"));
      parseStatement(builder, false);
      return true;
    }

    if (BranchStatement.BRANCH_KEYWORDS.contains(builder.getTokenType())) {
      return BranchStatement.parse(builder, this);
    }
    if (ParserUtils.lookAhead(builder, GroovyTokenTypes.mIDENT, GroovyTokenTypes.mCOLON)) {
      return parseLabeledStatement(builder);
    }

    //declaration
    PsiBuilder.Marker declMarker = builder.mark();
    if (!Declaration.parse(builder, false, this)) {
      declMarker.rollbackTo();
    } else {
      declMarker.drop();
      return true;
    }

    PsiBuilder.Marker marker = builder.mark();
    if (ImportStatement.parse(builder, this)) {
      marker.error(GroovyBundle.message("import.not.allowed"));
    }
    else {
      marker.drop();
    }
    if (TypeDefinition.parse(builder, this)) return true;

    return ExpressionStatement.parse(builder, this);

  }

  public boolean parseStatementWithImports(PsiBuilder builder) {
    if (ImportStatement.parse(builder, this)) {
      return true;
    } else {
      return parseStatement(builder, false);
    }
  }

  private boolean parseLabeledStatement(PsiBuilder builder) {

    PsiBuilder.Marker marker = builder.mark();
    ParserUtils.eatElement(builder, GroovyElementTypes.LABEL);
    ParserUtils.getToken(builder, GroovyTokenTypes.mCOLON);

    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);

    parseStatement(builder, false);

    marker.done(GroovyElementTypes.LABELED_STATEMENT);
    return true;
  }
}