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
package org.jetbrains.plugins.groovy.lang.psi.patterns;

import com.intellij.openapi.util.Condition;
import com.intellij.patterns.*;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrLiteralImpl;

import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mGSTRING_LITERAL;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mSTRING_LITERAL;

public class GroovyPatterns extends PsiJavaPatterns {

  public static GroovyElementPattern groovyElement() {
    return new GroovyElementPattern.Capture<GroovyPsiElement>(GroovyPsiElement.class);
  }

  public static GroovyBinaryExpressionPattern groovyBinaryExpression() {
    return new GroovyBinaryExpressionPattern();
  }

  public static GroovyAssignmentExpressionPattern groovyAssignmentExpression() {
    return new GroovyAssignmentExpressionPattern();
  }

  public static GroovyElementPattern.Capture<GrLiteral> groovyLiteralExpression() {
    return groovyLiteralExpression(null);
  }

  public static GroovyElementPattern.Capture<GrLiteral> groovyLiteralExpression(final ElementPattern value) {
    return new GroovyElementPattern.Capture<GrLiteral>(new InitialPatternCondition<GrLiteral>(GrLiteral.class) {
      public boolean accepts(@Nullable final Object o, final ProcessingContext context) {
        return o instanceof GrLiteral
               && (value == null || value.accepts(((GrLiteral)o).getValue(), context));
      }
    });
  }

  public static GroovyElementPattern.Capture<GroovyPsiElement> rightOfAssignment(final ElementPattern<? extends GroovyPsiElement> value,
                                                                                 final GroovyAssignmentExpressionPattern assignment) {
    return new GroovyElementPattern.Capture<GroovyPsiElement>(new InitialPatternCondition<GroovyPsiElement>(GroovyPsiElement.class) {
      @Override
      public boolean accepts(@Nullable Object o, ProcessingContext context) {
        if (!(o instanceof GroovyPsiElement)) return false;

        PsiElement parent = ((GroovyPsiElement)o).getParent();
        if (!(parent instanceof GrAssignmentExpression)) return false;

        if (((GrAssignmentExpression)parent).getRValue() != o) return false;

        return assignment.getCondition().accepts(parent, context) && value.getCondition().accepts(o, context);
      }
    });
  }

  public static GroovyElementPattern.Capture<GrLiteralImpl> stringLiteral() {
    return new GroovyElementPattern.Capture<GrLiteralImpl>(new InitialPatternCondition<GrLiteralImpl>(GrLiteralImpl.class) {
      public boolean accepts(@Nullable final Object o, final ProcessingContext context) {
        if (!(o instanceof GrLiteralImpl)) return false;
        return ((GrLiteralImpl)o).isStringLiteral();
      }
    });
  }

  public static GroovyElementPattern.Capture<GrLiteralImpl> namedArgumentStringLiteral() {
    return stringLiteral().withParent(psiElement(GrNamedArgument.class));
  }

  public static GroovyElementPattern.Capture<GrArgumentLabel> namedArgumentLabel(final ElementPattern<? extends String> namePattern) {
    return new GroovyElementPattern.Capture<GrArgumentLabel>(new InitialPatternCondition<GrArgumentLabel>(GrArgumentLabel.class) {
      public boolean accepts(@Nullable final Object o, final ProcessingContext context) {
        if (o instanceof GrArgumentLabel) {
          PsiElement nameElement = ((GrArgumentLabel)o).getNameElement();
          if (nameElement instanceof LeafPsiElement) {
            IElementType elementType = ((LeafPsiElement)nameElement).getElementType();
            if (elementType == GroovyElementTypes.mIDENT ||
                CommonClassNames.JAVA_LANG_STRING.equals(TypesUtil.getPsiTypeName(elementType))) {
              return namePattern.accepts(((GrArgumentLabel)o).getName());
            }
          }
        }

        return false;
      }
    });
  }

  public static GroovyElementPattern.Capture<GrNamedArgument> methodNamedParameter(@Nullable final ElementPattern<? extends GrCall> methodCall) {
    return new GroovyElementPattern.Capture<GrNamedArgument>(new InitialPatternCondition<GrNamedArgument>(GrNamedArgument.class) {
      public boolean accepts(@Nullable final Object o, final ProcessingContext context) {
        if (!(o instanceof GrNamedArgument)) return false;

        PsiElement parent = ((GrNamedArgument)o).getParent();

        PsiElement eMethodCall;

        if (parent instanceof GrArgumentList) {
          eMethodCall = parent.getParent();
        }
        else {
          if (!(parent instanceof GrListOrMap)) return false;

          PsiElement eArgumentList = parent.getParent();
          if (!(eArgumentList instanceof GrArgumentList)) return false;

          GrArgumentList argumentList = (GrArgumentList)eArgumentList;

          if (argumentList.getNamedArguments().length > 0) return false;
          if (argumentList.getExpressionArgumentIndex((GrListOrMap)parent) != 0) return false;

          eMethodCall = eArgumentList.getParent();
        }

        if (!(eMethodCall instanceof GrCall)) return false;

        return methodCall == null || methodCall.accepts(eMethodCall);
      }
    });
  }

  public static GroovyMethodCallPattern methodCall(final Condition<PsiMethod> methodCondition) {
    return new GroovyMethodCallPattern().with(new PatternCondition<GrCallExpression>("methodCall") {
      public boolean accepts(@NotNull GrCallExpression callExpression, ProcessingContext context) {
        if (!(callExpression instanceof GrMethodCall)) return false;

        GrExpression expression = ((GrMethodCall)callExpression).getInvokedExpression();
        if (!(expression instanceof GrReferenceExpression)) return false;

        GrReferenceExpression refExpression = (GrReferenceExpression)expression;

        for (GroovyResolveResult result : refExpression.multiResolve(false)) {
          PsiElement element = result.getElement();

          if (element instanceof PsiMethod) {
            if (methodCondition.value((PsiMethod)element)) {
              return true;
            }
          }
        }

        return false;
      }
    });
  }

  public static GroovyMethodCallPattern methodCall(final ElementPattern<? extends String> names, final String className) {
    return new GroovyMethodCallPattern().with(new PatternCondition<GrCallExpression>("methodCall") {
      public boolean accepts(@NotNull GrCallExpression callExpression, ProcessingContext context) {
        if (!(callExpression instanceof GrMethodCall)) return false;

        GrExpression expression = ((GrMethodCall)callExpression).getInvokedExpression();
        if (!(expression instanceof GrReferenceExpression)) return false;

        GrReferenceExpression refExpression = (GrReferenceExpression)expression;

        if (!names.accepts(refExpression.getName(), context)) return false;

        for (GroovyResolveResult result : refExpression.multiResolve(false)) {
          PsiElement element = result.getElement();

          if (element instanceof PsiMethod) {
            PsiClass containingClass = ((PsiMethod)element).getContainingClass();
            if (containingClass != null) {
              if (InheritanceUtil.isInheritor(containingClass, className)) {
                return true;
              }
            }
          }
        }

        return false;
      }
    });
  }

  public static GroovyMethodCallPattern methodCall(final ElementPattern<? extends PsiMethod> method) {
    return new GroovyMethodCallPattern().with(new PatternCondition<GrCallExpression>("methodCall") {
      public boolean accepts(@NotNull GrCallExpression callExpression, ProcessingContext context) {
        final GroovyResolveResult[] results = callExpression.getCallVariants(null);
        for (GroovyResolveResult result : results) {
          if (method.getCondition().accepts(result.getElement(), context)) {
            return true;
          }
        }
        return false;
      }
    });
  }

  public static PsiFilePattern.Capture<GroovyFile> groovyScript() {
    return new PsiFilePattern.Capture<GroovyFile>(new InitialPatternCondition<GroovyFile>(GroovyFile.class) {
      @Override
      public boolean accepts(@Nullable Object o, ProcessingContext context) {
        return o instanceof GroovyFileBase && ((GroovyFileBase)o).isScript();
      }
    });
  }
}
