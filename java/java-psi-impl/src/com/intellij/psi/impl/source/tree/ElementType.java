/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.tree;

import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.tree.TokenSet;

public interface ElementType extends JavaTokenType, JavaDocTokenType, JavaElementType, JavaDocElementType {
  TokenSet JAVA_WHITESPACE_BIT_SET = TokenSet.create(WHITE_SPACE);

  TokenSet JAVA_PLAIN_COMMENT_BIT_SET = TokenSet.create(END_OF_LINE_COMMENT, C_STYLE_COMMENT);
  TokenSet JAVA_COMMENT_BIT_SET = TokenSet.orSet(JAVA_PLAIN_COMMENT_BIT_SET, TokenSet.create(DOC_COMMENT));

  TokenSet JAVA_COMMENT_OR_WHITESPACE_BIT_SET = TokenSet.orSet(JAVA_WHITESPACE_BIT_SET, JAVA_COMMENT_BIT_SET);

  TokenSet KEYWORD_BIT_SET = TokenSet.create(
    ABSTRACT_KEYWORD, ASSERT_KEYWORD, BOOLEAN_KEYWORD, BREAK_KEYWORD, BYTE_KEYWORD, CASE_KEYWORD, CATCH_KEYWORD, CHAR_KEYWORD,
    CLASS_KEYWORD, CONST_KEYWORD, CONTINUE_KEYWORD, DEFAULT_KEYWORD, DO_KEYWORD, DOUBLE_KEYWORD, ELSE_KEYWORD, ENUM_KEYWORD,
    EXTENDS_KEYWORD, FINAL_KEYWORD, FINALLY_KEYWORD, FLOAT_KEYWORD, FOR_KEYWORD, GOTO_KEYWORD, IF_KEYWORD, IMPLEMENTS_KEYWORD,
    IMPORT_KEYWORD, INSTANCEOF_KEYWORD, INT_KEYWORD, INTERFACE_KEYWORD, LONG_KEYWORD, NATIVE_KEYWORD, NEW_KEYWORD, PACKAGE_KEYWORD,
    PRIVATE_KEYWORD, PROTECTED_KEYWORD, PUBLIC_KEYWORD, RETURN_KEYWORD, SHORT_KEYWORD, SUPER_KEYWORD, STATIC_KEYWORD, STRICTFP_KEYWORD,
    SWITCH_KEYWORD, SYNCHRONIZED_KEYWORD, THIS_KEYWORD, THROW_KEYWORD, THROWS_KEYWORD, TRANSIENT_KEYWORD, TRY_KEYWORD, VOID_KEYWORD,
    VOLATILE_KEYWORD, WHILE_KEYWORD);

  TokenSet LITERAL_BIT_SET = TokenSet.create(TRUE_KEYWORD, FALSE_KEYWORD, NULL_KEYWORD);

  TokenSet OPERATION_BIT_SET = TokenSet.create(
    EQ, GT, LT, EXCL, TILDE, QUEST, COLON, PLUS, MINUS, ASTERISK, DIV, AND, OR, XOR,
    PERC, EQEQ, LE, GE, NE, ANDAND, OROR, PLUSPLUS, MINUSMINUS, LTLT, GTGT, GTGTGT,
    PLUSEQ, MINUSEQ, ASTERISKEQ, DIVEQ, ANDEQ, OREQ, XOREQ, PERCEQ, LTLTEQ, GTGTEQ, GTGTGTEQ);

  TokenSet MODIFIER_BIT_SET = TokenSet.create(
    PUBLIC_KEYWORD, PROTECTED_KEYWORD, PRIVATE_KEYWORD, STATIC_KEYWORD, ABSTRACT_KEYWORD, FINAL_KEYWORD, NATIVE_KEYWORD,
    SYNCHRONIZED_KEYWORD, STRICTFP_KEYWORD, TRANSIENT_KEYWORD, VOLATILE_KEYWORD, DEFAULT_KEYWORD);

  TokenSet PRIMITIVE_TYPE_BIT_SET = TokenSet.create(
    BOOLEAN_KEYWORD, BYTE_KEYWORD, SHORT_KEYWORD, INT_KEYWORD, LONG_KEYWORD, CHAR_KEYWORD, FLOAT_KEYWORD, DOUBLE_KEYWORD, VOID_KEYWORD);

  TokenSet EXPRESSION_BIT_SET = TokenSet.create(
    REFERENCE_EXPRESSION, LITERAL_EXPRESSION, THIS_EXPRESSION, SUPER_EXPRESSION, PARENTH_EXPRESSION, METHOD_CALL_EXPRESSION,
    TYPE_CAST_EXPRESSION, PREFIX_EXPRESSION, POSTFIX_EXPRESSION, BINARY_EXPRESSION, POLYADIC_EXPRESSION, CONDITIONAL_EXPRESSION,
    ASSIGNMENT_EXPRESSION, NEW_EXPRESSION, ARRAY_ACCESS_EXPRESSION, ARRAY_INITIALIZER_EXPRESSION, INSTANCE_OF_EXPRESSION,
    CLASS_OBJECT_ACCESS_EXPRESSION, METHOD_REF_EXPRESSION, LAMBDA_EXPRESSION, EMPTY_EXPRESSION);

  TokenSet ANNOTATION_MEMBER_VALUE_BIT_SET = TokenSet.orSet(EXPRESSION_BIT_SET, TokenSet.create(ANNOTATION, ANNOTATION_ARRAY_INITIALIZER));

  TokenSet ARRAY_DIMENSION_BIT_SET = TokenSet.create(
    REFERENCE_EXPRESSION, LITERAL_EXPRESSION, THIS_EXPRESSION, SUPER_EXPRESSION, PARENTH_EXPRESSION, METHOD_CALL_EXPRESSION,
    TYPE_CAST_EXPRESSION, PREFIX_EXPRESSION, POSTFIX_EXPRESSION, BINARY_EXPRESSION, POLYADIC_EXPRESSION, CONDITIONAL_EXPRESSION,
    ASSIGNMENT_EXPRESSION, NEW_EXPRESSION, ARRAY_ACCESS_EXPRESSION, INSTANCE_OF_EXPRESSION, CLASS_OBJECT_ACCESS_EXPRESSION,
    EMPTY_EXPRESSION);

  TokenSet JAVA_STATEMENT_BIT_SET = TokenSet.create(
    EMPTY_STATEMENT, BLOCK_STATEMENT, EXPRESSION_STATEMENT, EXPRESSION_LIST_STATEMENT, DECLARATION_STATEMENT, IF_STATEMENT,
    WHILE_STATEMENT, FOR_STATEMENT, FOREACH_STATEMENT, DO_WHILE_STATEMENT, SWITCH_STATEMENT, SWITCH_LABEL_STATEMENT, BREAK_STATEMENT,
    CONTINUE_STATEMENT, RETURN_STATEMENT, THROW_STATEMENT, SYNCHRONIZED_STATEMENT, TRY_STATEMENT, LABELED_STATEMENT, ASSERT_STATEMENT);

  TokenSet IMPORT_STATEMENT_BASE_BIT_SET = TokenSet.create(IMPORT_STATEMENT, IMPORT_STATIC_STATEMENT);
  TokenSet CLASS_KEYWORD_BIT_SET = TokenSet.create(CLASS_KEYWORD, INTERFACE_KEYWORD, ENUM_KEYWORD);
  TokenSet MEMBER_BIT_SET = TokenSet.create(CLASS, FIELD, ENUM_CONSTANT, METHOD, ANNOTATION_METHOD);
  TokenSet FULL_MEMBER_BIT_SET = TokenSet.orSet(MEMBER_BIT_SET, TokenSet.create(CLASS_INITIALIZER));
}
