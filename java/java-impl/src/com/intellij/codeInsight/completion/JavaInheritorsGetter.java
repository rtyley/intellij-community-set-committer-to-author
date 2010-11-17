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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.filters.getters.ExpectedTypesGetter;
import com.intellij.psi.statistics.JavaStatisticsManager;
import com.intellij.psi.statistics.StatisticsInfo;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ProcessingContext;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
* @author peter
*/
public class JavaInheritorsGetter extends CompletionProvider<CompletionParameters> {
  private final ConstructorInsertHandler myConstructorInsertHandler;

  public JavaInheritorsGetter(final ConstructorInsertHandler constructorInsertHandler) {
    myConstructorInsertHandler = constructorInsertHandler;
  }

  public void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext matchingContext, @NotNull final CompletionResultSet result) {
    final ExpectedTypeInfo[] infos = JavaSmartCompletionContributor.getExpectedTypes(parameters);

    addArrayTypes(result, parameters.getPosition(), infos);

    final List<ExpectedTypeInfo> infoCollection = Arrays.asList(infos);
    processInheritors(parameters, extractClassTypes(infos), result.getPrefixMatcher(), new Consumer<PsiType>() {
      public void consume(final PsiType type) {
        addExpectedType(result, type, parameters, infoCollection);
      }
    });
  }

  private static void addArrayTypes(CompletionResultSet result,
                                    PsiElement identifierCopy,
                                    ExpectedTypeInfo[] infos) {

    for (final PsiType type : ExpectedTypesGetter.extractTypes(infos, true)) {
      if (type instanceof PsiArrayType) {
        final LookupItem item = PsiTypeLookupItem.createLookupItem(JavaCompletionUtil.eliminateWildcards(type), identifierCopy);
        if (item.getObject() instanceof PsiClass) {
          JavaCompletionUtil.setShowFQN(item);
        }
        item.setInsertHandler(new DefaultInsertHandler()); //braces & shortening
        result.addElement(JavaSmartCompletionContributor.decorate(item, Arrays.asList(infos)));
      }
    }
  }

  public static List<PsiClassType> extractClassTypes(ExpectedTypeInfo[] infos) {
    final List<PsiClassType> expectedClassTypes = new SmartList<PsiClassType>();
    for (PsiType type : ExpectedTypesGetter.extractTypes(infos, true)) {
      if (type instanceof PsiClassType) {
        final PsiClassType classType = (PsiClassType)type;
        if (classType.resolve() != null) {
          expectedClassTypes.add(classType);
        }
      }
    }
    return expectedClassTypes;
  }

  private void addExpectedType(final CompletionResultSet result, final PsiType type, final CompletionParameters parameters, Collection<ExpectedTypeInfo> infos) {
    if (!JavaCompletionUtil.hasAccessibleConstructor(type)) return;

    final PsiClass psiClass = PsiUtil.resolveClassInType(type);
    if (psiClass == null) return;

    final PsiClass parentClass = psiClass.getContainingClass();
    if (parentClass != null && !psiClass.hasModifierProperty(PsiModifier.STATIC) &&
        !PsiTreeUtil.isAncestor(parentClass, parameters.getPosition(), false) &&
        !(parentClass.getContainingFile().equals(parameters.getOriginalFile()) &&
          parentClass.getTextRange().contains(parameters.getOffset()))) {
      return;
    }

    final LookupItem item = PsiTypeLookupItem.createLookupItem(JavaCompletionUtil.eliminateWildcards(type), parameters.getPosition());
    JavaCompletionUtil.setShowFQN(item);

    if (psiClass.isInterface() || psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
      item.setAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);
      item.setAttribute(LookupItem.INDICATE_ANONYMOUS, "");
    }

    result.addElement(JavaSmartCompletionContributor.decorate(type instanceof PsiClassType ? LookupElementDecorator
      .withInsertHandler(item, myConstructorInsertHandler) : item, infos));
  }

  public static void processInheritors(final CompletionParameters parameters,
                                       final Collection<PsiClassType> expectedClassTypes,
                                       final PrefixMatcher matcher, final Consumer<PsiType> consumer) {
    //quick
    if (!processMostProbableInheritors(parameters, expectedClassTypes, consumer)) return;

    //long
    final Condition<String> shortNameCondition = new Condition<String>() {
      public boolean value(String s) {
        return matcher.prefixMatches(s);
      }
    };
    for (final PsiClassType type : expectedClassTypes) {
      final PsiClass psiClass = type.resolve();
      if (psiClass != null && !psiClass.hasModifierProperty(PsiModifier.FINAL)) {
        for (final PsiType psiType : CodeInsightUtil.addSubtypes(type, parameters.getPosition(), false, shortNameCondition)) {
          consumer.consume(psiType);
        }
      }
    }
  }

  private static boolean processMostProbableInheritors(CompletionParameters parameters,
                                                       Collection<PsiClassType> expectedClassTypes,
                                                       Consumer<PsiType> consumer) {
    PsiFile file = parameters.getOriginalFile();
    for (final PsiClassType type : expectedClassTypes) {
      consumer.consume(type);

      final PsiClassType.ClassResolveResult baseResult = JavaCompletionUtil.originalize(type).resolveGenerics();
      final PsiClass baseClass = baseResult.getElement();
      if (baseClass == null) return false;

      final PsiSubstitutor baseSubstitutor = baseResult.getSubstitutor();

      final THashSet<PsiType> statVariants = new THashSet<PsiType>();
      final Processor<PsiClass> processor = CodeInsightUtil.createInheritorsProcessor(parameters.getPosition(), type, 0, false,
                                                                                      statVariants, baseClass, baseSubstitutor);
      final StatisticsInfo[] stats = StatisticsManager.getInstance().getAllValues(JavaStatisticsManager.getAfterNewKey(type));
      for (final StatisticsInfo statisticsInfo : stats) {
        final String value = statisticsInfo.getValue();
        if (value.startsWith(JavaStatisticsManager.CLASS_PREFIX)) {
          final String qname = value.substring(JavaStatisticsManager.CLASS_PREFIX.length());
          final PsiClass psiClass = JavaPsiFacade.getInstance(file.getProject()).findClass(qname, file.getResolveScope());
          if (psiClass != null && !PsiTreeUtil.isAncestor(file, psiClass, true) && !processor.process(psiClass)) break;
        }
      }

      for (final PsiType variant : statVariants) {
        consumer.consume(variant);
      }
    }
    return true;
  }
}
