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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.PomDeclarationSearcher;
import com.intellij.pom.PomTarget;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.CollectConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.extensions.GroovyUnresolvedHighlightFilter;
import org.jetbrains.plugins.groovy.highlighter.DefaultHighlighter;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

/**
 * @author Max Medvedev
 */
public class GrHighlightUtil {
  private static final Logger LOG = Logger.getInstance(GrHighlightUtil.class);

  static boolean isReassigned(GrVariable var) {
    PsiMethod method = PsiTreeUtil.getParentOfType(var, PsiMethod.class);
    PsiNamedElement scope = method == null ? var.getContainingFile() : method;
    if (scope == null) {
      return false;
    }
    boolean hasAssignment = var.getInitializerGroovy() != null || var instanceof GrParameter;
    for (PsiReference reference : ReferencesSearch.search(var, new LocalSearchScope(scope)).findAll()) {
      if (reference instanceof GrReferenceExpression &&
          (PsiUtil.isLValue((GrReferenceExpression)reference) ||
           ((GrReferenceExpression)reference).getParent() instanceof GrUnaryExpression &&
           ((GrUnaryExpression)((GrReferenceExpression)reference).getParent()).isPostfix())) {
        if (hasAssignment) {
          return true;
        }
        hasAssignment = true;
      }
    }
    return false;
  }

  @Nullable
  static TextAttributesKey getDeclarationHighlightingAttribute(PsiElement resolved) {
    if (resolved instanceof PsiField || resolved instanceof GrVariable && ResolveUtil.isScriptField((GrVariable)resolved)) {
      boolean isStatic = ((PsiVariable)resolved).hasModifierProperty(PsiModifier.STATIC);
      return isStatic ? DefaultHighlighter.STATIC_FIELD : DefaultHighlighter.INSTANCE_FIELD;
    }
    else if (resolved instanceof GrAccessorMethod) {
      boolean isStatic = ((GrAccessorMethod)resolved).hasModifierProperty(PsiModifier.STATIC);
      return isStatic ? DefaultHighlighter.STATIC_PROPERTY_REFERENCE : DefaultHighlighter.INSTANCE_PROPERTY_REFERENCE;
    }
    else if (resolved instanceof PsiMethod) {
      if (!((PsiMethod)resolved).isConstructor()) {
        boolean isStatic = ((PsiMethod)resolved).hasModifierProperty(PsiModifier.STATIC);
        if (GroovyPropertyUtils.isSimplePropertyAccessor((PsiMethod)resolved)) {
          return isStatic ? DefaultHighlighter.STATIC_PROPERTY_REFERENCE : DefaultHighlighter.INSTANCE_PROPERTY_REFERENCE;
        }
        else {
          return isStatic ? DefaultHighlighter.STATIC_METHOD_ACCESS : DefaultHighlighter.METHOD_CALL;
        }
      }
    }
    else if (resolved instanceof PsiTypeParameter) {
      return DefaultHighlighter.TYPE_PARAMETER;
    }
    else if (resolved instanceof PsiClass) {
      if (((PsiClass)resolved).isAnnotationType()) {
        return DefaultHighlighter.ANNOTATION;
      }
      else {
        return DefaultHighlighter.CLASS_REFERENCE;
      }
    }
    else if (resolved instanceof GrParameter) {
      boolean reassigned = isReassigned((GrParameter)resolved);
      return reassigned ? DefaultHighlighter.REASSIGNED_PARAMETER : DefaultHighlighter.PARAMETER;
    }
    else if (resolved instanceof GrVariable) {
      boolean reassigned = isReassigned((GrVariable)resolved);
      return reassigned ? DefaultHighlighter.REASSIGNED_LOCAL_VARIABLE : DefaultHighlighter.LOCAL_VARIABLE;
    }
    return null;
  }

  public static boolean isDeclarationAssignment(GrReferenceExpression refExpr) {
    if (isAssignmentLhs(refExpr)) {
      return isExpandoQualified(refExpr);
    }
    return false;
  }

  private static boolean isAssignmentLhs(GrReferenceExpression refExpr) {
    return refExpr.getParent() instanceof GrAssignmentExpression &&
           refExpr.equals(((GrAssignmentExpression)refExpr.getParent()).getLValue());
  }

  private static boolean isExpandoQualified(GrReferenceExpression refExpr) {
    final GrExpression qualifier = refExpr.getQualifierExpression();
    if (qualifier == null) {
      final PsiClass clazz = PsiTreeUtil.getParentOfType(refExpr, PsiClass.class);
      if (clazz == null) { //script
        return true;
      }
      return false; //in class, a property should normally be defined, so it's not a declaration
    }

    final PsiType type = qualifier.getType();
    if (type instanceof PsiClassType) {
      final PsiClassType classType = (PsiClassType)type;
      final PsiClass psiClass = classType.resolve();
      if (psiClass instanceof GroovyScriptClass) {
        return true;
      }
    }
    return false;
  }

  public static boolean shouldHighlightAsUnresolved(@NotNull GrReferenceExpression referenceExpression) {
    PsiElement refNameElement = referenceExpression.getReferenceNameElement();
    if (refNameElement != null && referenceExpression.getQualifier() == null) {
      final IElementType type = refNameElement.getNode().getElementType();
      if (TokenSets.STRING_LITERAL_SET.contains(type)) return false;
    }

    if (!GroovyUnresolvedHighlightFilter.shouldHighlight(referenceExpression)) return false;

    CollectConsumer<PomTarget> consumer = new CollectConsumer<PomTarget>();

    for (PomDeclarationSearcher searcher : PomDeclarationSearcher.EP_NAME.getExtensions()) {
      searcher.findDeclarationsAt(referenceExpression, 0, consumer);
      if (consumer.getResult().size() > 0) return false;
    }

    return true;
  }

  public static TextRange getMethodHeaderTextRange(PsiMethod method) {
    final PsiModifierList modifierList = method.getModifierList();
    final PsiParameterList parameterList = method.getParameterList();

    final TextRange textRange = modifierList.getTextRange();
    LOG.assertTrue(textRange != null, method.getClass() + ":" + method.getText());
    int startOffset = textRange.getStartOffset();
    int endOffset = parameterList.getTextRange().getEndOffset() + 1;

    return new TextRange(startOffset, endOffset);
  }

  @NotNull
  public static PsiElement getElementToHighlight(@NotNull GrReferenceElement refElement) {
    final PsiElement refNameElement = refElement.getReferenceNameElement();
    return refNameElement != null ? refNameElement : refElement;
  }
}
