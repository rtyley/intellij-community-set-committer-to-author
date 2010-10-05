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
package com.intellij.lang.java.parser;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.lang.PsiBuilder;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.lang.PsiBuilderUtil.expect;
import static com.intellij.lang.PsiBuilderUtil.nextTokenType;
import static com.intellij.lang.java.parser.JavaParserUtil.*;
import static com.intellij.lang.java.parser.JavaParserUtil.emptyElement;


public class ReferenceParser {
  public static class TypeInfo {
    public boolean isPrimitive = false;
    public boolean isParameterized = false;
    public boolean isArray = false;
    public boolean hasErrors = false;
    public PsiBuilder.Marker marker = null;
  }

  private static final TokenSet WILDCARD_KEYWORD_SET = TokenSet.create(JavaTokenType.EXTENDS_KEYWORD, JavaTokenType.SUPER_KEYWORD);

  private ReferenceParser() { }

  @Nullable
  public static TypeInfo parseType(final PsiBuilder builder) {
    return parseTypeWithInfo(builder, true, true, false);
  }

  @Nullable
  public static PsiBuilder.Marker parseType(final PsiBuilder builder, final boolean eatLastDot, final boolean wildcard, final boolean diamonds) {
    final TypeInfo typeInfo = parseTypeWithInfo(builder, eatLastDot, wildcard, diamonds);
    return typeInfo != null ? typeInfo.marker : null;
  }

  @Nullable
  public static PsiBuilder.Marker parseTypeWithEllipsis(final PsiBuilder builder, final boolean eatLastDot, final boolean wildcard) {
    final TypeInfo typeInfo = parseTypeWithInfo(builder, eatLastDot, wildcard, false);
    if (typeInfo == null) return null;

    PsiBuilder.Marker type = typeInfo.marker;
    if (builder.getTokenType() == JavaTokenType.ELLIPSIS) {
      type = typeInfo.marker.precede();
      builder.advanceLexer();
      type.done(JavaElementType.TYPE);
    }

    return type;
  }

  @Nullable
  private static TypeInfo parseTypeWithInfo(final PsiBuilder builder, final boolean eatLastDot, final boolean wildcard, final boolean diamonds) {
    if (builder.getTokenType() == null) return null;

    final TypeInfo typeInfo = new TypeInfo();

    final boolean annotationsSupported = areTypeAnnotationsSupported(builder);
    PsiBuilder.Marker type = builder.mark();
    if (annotationsSupported) {
      DeclarationParser.parseAnnotations(builder);
    }

    final IElementType tokenType = builder.getTokenType();
    if (expect(builder, ElementType.PRIMITIVE_TYPE_BIT_SET)) {
      typeInfo.isPrimitive = true;
    }
    else if (tokenType == JavaTokenType.IDENTIFIER) {
      parseJavaCodeReference(builder, eatLastDot, true, annotationsSupported, false, false, diamonds, typeInfo);
    }
    else if (wildcard && tokenType == JavaTokenType.QUEST) {
      type.drop();
      typeInfo.marker = parseWildcardType(builder);
      return typeInfo.marker != null ? typeInfo : null;
    }
    else if (diamonds && tokenType == JavaTokenType.GT) {
      emptyElement(builder, JavaElementType.DIAMOND_TYPE);
      type.done(JavaElementType.TYPE);
      typeInfo.marker = type;
      return typeInfo;
    }
    else {
      type.drop();
      return null;
    }

    while (true) {
      type.done(JavaElementType.TYPE);
      if (annotationsSupported) {
        DeclarationParser.parseAnnotations(builder);
      }

      final PsiBuilder.Marker bracket = builder.mark();
      if (!expect(builder, JavaTokenType.LBRACKET)) {
        bracket.drop();
        break;
      }
      if (!expect(builder, JavaTokenType.RBRACKET)) {
        bracket.rollbackTo();
        break;
      }
      bracket.drop();
      typeInfo.isArray = true;

      type = type.precede();
    }

    typeInfo.marker = type;
    return typeInfo;
  }

  @NotNull
  private static PsiBuilder.Marker parseWildcardType(final PsiBuilder builder) {
    final PsiBuilder.Marker type = builder.mark();
    builder.advanceLexer();

    if (expect(builder, WILDCARD_KEYWORD_SET)) {
      final PsiBuilder.Marker boundType = parseType(builder, true, false, false);
      if (boundType == null) {
        error(builder, JavaErrorMessages.message("expected.type"));
      }
    }

    type.done(JavaElementType.TYPE);
    return type;
  }

  @Nullable
  public static PsiBuilder.Marker parseJavaCodeReference(final PsiBuilder builder, final boolean eatLastDot,
                                                         final boolean parameterList, final boolean annotations, final boolean diamonds) {
    return parseJavaCodeReference(builder, eatLastDot, parameterList, annotations, false, false, diamonds, new TypeInfo());
  }

  public static boolean parseImportCodeReference(final PsiBuilder builder, final boolean isStatic) {
    final TypeInfo typeInfo = new TypeInfo();
    parseJavaCodeReference(builder, true, false, false, true, isStatic, false, typeInfo);
    return !typeInfo.hasErrors;
  }

  @Nullable
  private static PsiBuilder.Marker parseJavaCodeReference(final PsiBuilder builder, final boolean eatLastDot,
                                                          final boolean parameterList, final boolean annotations,
                                                          final boolean isImport, final boolean isStaticImport,
                                                          final boolean diamonds, final TypeInfo typeInfo) {
    PsiBuilder.Marker refElement = builder.mark();

    if (annotations) {
      DeclarationParser.parseAnnotations(builder);
    }

    if (!expect(builder, JavaTokenType.IDENTIFIER)) {
      refElement.rollbackTo();
      if (isImport) {
        error(builder, JavaErrorMessages.message("expected.identifier"));
      }
      typeInfo.hasErrors = true;
      return null;
    }

    if (parameterList) {
      typeInfo.isParameterized = parseReferenceParameterList(builder, true, diamonds);
    }
    else {
      if (!isStaticImport || builder.getTokenType() == JavaTokenType.DOT) {
        emptyElement(builder, JavaElementType.REFERENCE_PARAMETER_LIST);
      }
    }

    boolean hasIdentifier;
    while (builder.getTokenType() == JavaTokenType.DOT) {
      refElement.done(JavaElementType.JAVA_CODE_REFERENCE);

      final PsiBuilder.Marker dotPos = builder.mark();
      builder.advanceLexer();

      if (expect(builder, JavaTokenType.IDENTIFIER)) {
        hasIdentifier = true;
      }
      else if (isImport && expect(builder, JavaTokenType.ASTERISK)) {
        dotPos.drop();
        return refElement;
      }
      else {
        if (!eatLastDot) {
          dotPos.rollbackTo();
          return refElement;
        }
        hasIdentifier = false;
      }
      dotPos.drop();

      final PsiBuilder.Marker prevElement = refElement;
      refElement = refElement.precede();

      if (!hasIdentifier) {
        typeInfo.hasErrors = true;
        if (isImport) {
          error(builder, JavaErrorMessages.message("import.statement.identifier.or.asterisk.expected."));
          refElement.drop();
          return prevElement;
        }
        else {
          error(builder, JavaErrorMessages.message("expected.identifier"));
          emptyElement(builder, JavaElementType.REFERENCE_PARAMETER_LIST);
          break;
        }
      }

      if (parameterList) {
        typeInfo.isParameterized = parseReferenceParameterList(builder, true, diamonds);
      }
      else if (!isStaticImport || builder.getTokenType() == JavaTokenType.DOT) {
        emptyElement(builder, JavaElementType.REFERENCE_PARAMETER_LIST);
      }
    }

    refElement.done(isStaticImport ? JavaElementType.IMPORT_STATIC_REFERENCE : JavaElementType.JAVA_CODE_REFERENCE);
    return refElement;
  }

  public static boolean parseReferenceParameterList(final PsiBuilder builder, final boolean wildcard, final boolean diamonds) {
    final PsiBuilder.Marker list = builder.mark();
    if (!expect(builder, JavaTokenType.LT)) {
      list.done(JavaElementType.REFERENCE_PARAMETER_LIST);
      return false;
    }

    boolean isOk = true;
    while (true) {
      final PsiBuilder.Marker type = parseType(builder, true, wildcard, diamonds);
      if (type == null) {
        error(builder, JavaErrorMessages.message("expected.identifier"));
      }

      if (expect(builder, JavaTokenType.GT)) {
        break;
      }
      else if (!expectOrError(builder, JavaTokenType.COMMA, JavaErrorMessages.message("expected.gt.or.comma"))) {
        isOk = false;
        break;
      }
    }

    list.done(JavaElementType.REFERENCE_PARAMETER_LIST);
    return isOk;
  }

  @NotNull
  public static PsiBuilder.Marker parseTypeParameters(final PsiBuilder builder) {
    final PsiBuilder.Marker list = builder.mark();
    if (!expect(builder, JavaTokenType.LT)) {
      list.done(JavaElementType.TYPE_PARAMETER_LIST);
      return list;
    }

    while (true) {
      final PsiBuilder.Marker param = parseTypeParameter(builder);
      if (param == null) {
        error(builder, JavaErrorMessages.message("expected.type.parameter"));
      }
      if (!expect(builder, JavaTokenType.COMMA)) {
        break;
      }
    }

    if (!expect(builder, JavaTokenType.GT)) {
      // hack for completion
      if (builder.getTokenType() == JavaTokenType.IDENTIFIER) {
        if (nextTokenType(builder) == JavaTokenType.GT) {
          final PsiBuilder.Marker errorElement = builder.mark();
          builder.advanceLexer();
          errorElement.error(JavaErrorMessages.message("unexpected.identifier"));
          builder.advanceLexer();
        }
        else {
          error(builder, JavaErrorMessages.message("expected.gt"));
        }
      }
      else {
        error(builder, JavaErrorMessages.message("expected.gt"));
      }
    }

    list.done(JavaElementType.TYPE_PARAMETER_LIST);
    return list;
  }

  @Nullable
  private static PsiBuilder.Marker parseTypeParameter(final PsiBuilder builder) {
    final PsiBuilder.Marker param = builder.mark();

    DeclarationParser.parseAnnotations(builder);
    if (!expect(builder, JavaTokenType.IDENTIFIER)) {
      param.rollbackTo();
      return null;
    }

    parseReferenceList(builder, JavaTokenType.EXTENDS_KEYWORD, JavaElementType.EXTENDS_BOUND_LIST, JavaTokenType.AND);

    param.done(JavaElementType.TYPE_PARAMETER);
    return param;
  }

  @NotNull
  public static PsiBuilder.Marker parseReferenceList(final PsiBuilder builder, final IElementType start,
                                                     final IElementType type, final IElementType delimiter) {
    final PsiBuilder.Marker element = builder.mark();

    if (expect(builder, start)) {
      while (true) {
        final PsiBuilder.Marker classReference = parseJavaCodeReference(builder, true, true, true, false);
        if (classReference == null) {
          error(builder, JavaErrorMessages.message("expected.identifier"));
        }
        if (!expect(builder, delimiter)) {
          break;
        }
      }
    }

    element.done(type);
    return element;
  }
}
