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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.TailTypeDecorator;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
* @author peter
*/
public class SmartCompletionDecorator extends TailTypeDecorator<LookupElement> {
  @NotNull private final Collection<ExpectedTypeInfo> myExpectedTypeInfos;
  private PsiElement myPosition;

  public SmartCompletionDecorator(LookupElement item, Collection<ExpectedTypeInfo> expectedTypeInfos) {
    super(item);
    myExpectedTypeInfos = expectedTypeInfos;
  }

  protected TailType computeTailType(InsertionContext context) {
    if (context.getCompletionChar() == Lookup.COMPLETE_STATEMENT_SELECT_CHAR) {
      return TailType.NONE;
    }

    if (LookupItem.getDefaultTailType(context.getCompletionChar()) != null) {
      return null;
    }

    LookupElement delegate = getDelegate();
    LookupItem item = as(LookupItem.CLASS_CONDITION_KEY);
    Object object = delegate.getObject();
    if (!CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET && (object instanceof PsiMethod || object instanceof PsiClass)) {
      return TailType.NONE;
    }

    final PsiExpression enclosing = PsiTreeUtil.getContextOfType(myPosition, PsiExpression.class, true);

    if (enclosing != null && object instanceof PsiElement) {
      final PsiType type = JavaCompletionUtil.getLookupElementType(delegate);
      final TailType itemType = item != null ? item.getTailType() : TailType.NONE;
      if (type != null && type.isValid()) {
        Set<TailType> voidTyped = new HashSet<TailType>();
        Set<TailType> sameTyped = new HashSet<TailType>();
        Set<TailType> assignableTyped = new HashSet<TailType>();
        for (ExpectedTypeInfo info : myExpectedTypeInfos) {
          final PsiType infoType = info.getType();
          if (PsiType.VOID.equals(infoType)) {
            voidTyped.add(info.getTailType());
          } else if (infoType.equals(type)) {
            sameTyped.add(info.getTailType());
          } else if ((infoType.isAssignableFrom(type) && info.getKind() == ExpectedTypeInfo.TYPE_OR_SUBTYPE) ||
                     (type.isAssignableFrom(infoType) && info.getKind() == ExpectedTypeInfo.TYPE_OR_SUPERTYPE)) {
            assignableTyped.add(info.getTailType());
          }
        }

        if (!sameTyped.isEmpty()) {
          return sameTyped.size() == 1 ? sameTyped.iterator().next() : itemType;
        }
        if (!assignableTyped.isEmpty()) {
          return assignableTyped.size() == 1 ? assignableTyped.iterator().next() : itemType;
        }
        if (!voidTyped.isEmpty()) {
          return voidTyped.size() == 1 ? voidTyped.iterator().next() : itemType;
        }

      }
      else {
        if (myExpectedTypeInfos.size() == 1) {
          return myExpectedTypeInfos.iterator().next().getTailType();
        }
      }
      return itemType;
    }
    return null;
  }

  @Override
  public void handleInsert(InsertionContext context) {
    myPosition = getPosition(context, this);
    super.handleInsert(context);
  }

  public static boolean hasUnboundTypeParams(final PsiMethod method, PsiType expectedType) {
    final PsiTypeParameter[] typeParameters = method.getTypeParameters();
    if (typeParameters.length == 0) return false;

    final Set<PsiTypeParameter> set = new THashSet<PsiTypeParameter>(Arrays.asList(typeParameters));
    final PsiTypeVisitor<Boolean> typeParamSearcher = new PsiTypeVisitor<Boolean>() {
      public Boolean visitType(final PsiType type) {
        return true;
      }

      public Boolean visitArrayType(final PsiArrayType arrayType) {
        return arrayType.getComponentType().accept(this);
      }

      public Boolean visitClassType(final PsiClassType classType) {
        final PsiClass aClass = classType.resolve();
        if (aClass instanceof PsiTypeParameter && set.contains(aClass)) return false;

        final PsiType[] types = classType.getParameters();
        for (final PsiType psiType : types) {
          if (!psiType.accept(this).booleanValue()) return false;
        }
        return true;
      }

      public Boolean visitWildcardType(final PsiWildcardType wildcardType) {
        final PsiType bound = wildcardType.getBound();
        return bound == null || bound.accept(this).booleanValue();
      }
    };

    for (final PsiParameter parameter : method.getParameterList().getParameters()) {
      if (!parameter.getType().accept(typeParamSearcher).booleanValue()) return false;
    }

    PsiSubstitutor substitutor = calculateMethodReturnTypeSubstitutor(method, expectedType);
    for (PsiTypeParameter parameter : typeParameters) {
      if (!TypeConversionUtil.typeParameterErasure(parameter).equals(substitutor.substitute(parameter))) {
        return true;
      }
    }

    return false;
  }

  public static PsiSubstitutor calculateMethodReturnTypeSubstitutor(PsiMethod method, final PsiType expected) {
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    PsiResolveHelper helper = JavaPsiFacade.getInstance(method.getProject()).getResolveHelper();
    final PsiTypeParameter[] typeParameters = method.getTypeParameters();
    for (PsiTypeParameter typeParameter : typeParameters) {
      PsiType substitution = helper.getSubstitutionForTypeParameter(typeParameter, method.getReturnType(), expected,
                                                                    false, PsiUtil.getLanguageLevel(method));
      if (PsiType.NULL.equals(substitution)) {
        substitution = TypeConversionUtil.typeParameterErasure(typeParameter);
      }

      substitutor = substitutor.put(typeParameter, substitution);
    }
    return substitutor;
  }

  @Nullable
  public static PsiElement getPosition(InsertionContext context, LookupElement element) {
    PsiElement position = context.getFile().findElementAt(context.getStartOffset() + element.getLookupString().length() - 1);
    if (position instanceof PsiJavaToken && ">".equals(position.getText())) {
      // In case of generics class
      return position.getParent().getParent();
    }
    return position;
  }
}
