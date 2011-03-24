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
package com.intellij.codeInsight.completion.impl;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.Classifier;
import com.intellij.codeInsight.lookup.ClassifierFactory;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementWeigher;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.Weigher;
import com.intellij.psi.WeighingService;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * @author peter
 */
public class CompletionServiceImpl extends CompletionService{
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.impl.CompletionServiceImpl");
  private Throwable myTrace = null;
  private CompletionProgressIndicator myCurrentCompletion;
  private static CompletionPhase ourPhase = CompletionPhase.NoCompletion;
  private static String ourPhaseTrace;

  public CompletionServiceImpl() {
    ProjectManager.getInstance().addProjectManagerListener(new ProjectManagerAdapter() {
      @Override
      public void projectClosing(Project project) {
        setCompletionPhase(CompletionPhase.NoCompletion);
      }
    });
  }

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass"})
  public static CompletionServiceImpl getCompletionService() {
    return (CompletionServiceImpl)CompletionService.getCompletionService();
  }

  @Override
  public String getAdvertisementText() {
    final CompletionProgressIndicator completion = getCompletionService().getCurrentCompletion();
    return completion == null ? null : completion.getLookup().getAdvertisementText();
  }

  public void setAdvertisementText(@Nullable final String text) {
    final CompletionProgressIndicator completion = getCompletionService().getCurrentCompletion();
    if (completion != null) {
      completion.getLookup().setAdvertisementText(text);
    }
  }

  public CompletionResultSet createResultSet(final CompletionParameters parameters, final Consumer<LookupElement> consumer,
                                             @NotNull final CompletionContributor contributor) {
    final PsiElement position = parameters.getPosition();
    final String prefix = CompletionData.findPrefixStatic(position, parameters.getOffset());
    final String textBeforePosition = parameters.getPosition().getContainingFile().getText().substring(0, parameters.getOffset());
    return new CompletionResultSetImpl(consumer, textBeforePosition, new CamelHumpMatcher(prefix, true, parameters.relaxMatching()), contributor, defaultSorter(parameters), null);
  }

  @Override
  public CompletionProgressIndicator getCurrentCompletion() {
    return myCurrentCompletion;
  }

  public void setCurrentCompletion(@Nullable final CompletionProgressIndicator indicator) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (indicator != null) {
      final CompletionProgressIndicator oldCompletion = myCurrentCompletion;
      final Throwable oldTrace = myTrace;
      myCurrentCompletion = indicator;
      myTrace = new Throwable();
      if (oldCompletion != null) {
        throw new RuntimeException(
          "SHe's not dead yet!\nthis=" + indicator + "\ncurrent=" + oldCompletion + "\ntrace=" + StringUtil.getThrowableText(oldTrace));
      }
    } else {
      myCurrentCompletion = null;
    }
  }

  private static class CompletionResultSetImpl extends CompletionResultSet {
    private final String myTextBeforePosition;
    private final CompletionSorterImpl mySorter;
    @Nullable private final CompletionResultSetImpl myOriginal;

    public CompletionResultSetImpl(final Consumer<LookupElement> consumer, final String textBeforePosition,
                                   final PrefixMatcher prefixMatcher,
                                   CompletionContributor contributor,
                                   @NotNull CompletionSorterImpl sorter,
                                   CompletionResultSetImpl original) {
      super(prefixMatcher, consumer, contributor);
      myTextBeforePosition = textBeforePosition;
      mySorter = sorter;
      myOriginal = original;
    }

    @Override
    public void addElement(@NotNull LookupElement element) {
      element.putUserDataIfAbsent(CompletionLookupArranger.SORTER_KEY, mySorter);
      super.addElement(element);
    }

    @NotNull
    public CompletionResultSet withPrefixMatcher(@NotNull final PrefixMatcher matcher) {
      if (!myTextBeforePosition.endsWith(matcher.getPrefix())) {
        final int len = myTextBeforePosition.length();
        final String fragment = len > 100 ? myTextBeforePosition.substring(len - 100) : myTextBeforePosition;
        LOG.error("prefix should be some actual file string just before caret: " + matcher.getPrefix() + "\n text=" + fragment);
      }
      return new CompletionResultSetImpl(getConsumer(), myTextBeforePosition, matcher, myContributor, mySorter, this);
    }

    @Override
    public void stopHere() {
      super.stopHere();
      if (myOriginal != null) {
        myOriginal.stopHere();
      }
    }

    @NotNull
    public CompletionResultSet withPrefixMatcher(@NotNull final String prefix) {
      return withPrefixMatcher(new CamelHumpMatcher(prefix));
    }

    @NotNull
    @Override
    public CompletionResultSet withRelevanceSorter(@NotNull CompletionSorter sorter) {
      return new CompletionResultSetImpl(getConsumer(), myTextBeforePosition, getPrefixMatcher(), myContributor, (CompletionSorterImpl)sorter, this);
    }

    @NotNull
    @Override
    public CompletionResultSet caseInsensitive() {
      return withPrefixMatcher(new CamelHumpMatcher(getPrefixMatcher().getPrefix(), false, false));
    }

    @Override
    public void restartCompletionOnPrefixChange(ElementPattern<String> prefixCondition) {
      final CompletionProgressIndicator indicator = getCompletionService().getCurrentCompletion();
      if (indicator != null) {
        indicator.addWatchedPrefix(myTextBeforePosition.length() - getPrefixMatcher().getPrefix().length(), prefixCondition);
      }
    }
  }

  public void correctCaseInsensitiveString(@NotNull final LookupElement element, InsertionContext context) {
    if (!element.isPrefixMatched()) {
      return;
    }

    final String prefix = element.getPrefixMatcher().getPrefix();
    final String oldLookupString = element.getLookupString();
    if (StringUtil.startsWithIgnoreCase(oldLookupString, prefix)) {
      final String newLookupString = handleCaseInsensitiveVariant(prefix, oldLookupString);
      if (!newLookupString.equals(oldLookupString)) {
        final Document document = context.getEditor().getDocument();
        int startOffset = context.getStartOffset();
        int tailOffset = context.getTailOffset();

        assert startOffset >= 0 : "stale startOffset";
        assert tailOffset >= 0 : "stale tailOffset";

        document.replaceString(startOffset, tailOffset, newLookupString);
        PsiDocumentManager.getInstance(context.getProject()).commitDocument(document);
      }
    }
  }

  private static String handleCaseInsensitiveVariant(final String prefix, @NotNull final String lookupString) {
    final int length = prefix.length();
    if (length == 0) return lookupString;
    boolean isAllLower = true;
    boolean isAllUpper = true;
    boolean sameCase = true;
    for (int i = 0; i < length && (isAllLower || isAllUpper || sameCase); i++) {
      final char c = prefix.charAt(i);
      isAllLower = isAllLower && Character.isLowerCase(c);
      isAllUpper = isAllUpper && Character.isUpperCase(c);
      sameCase = sameCase && Character.isLowerCase(c) == Character.isLowerCase(lookupString.charAt(i));
    }
    if (sameCase) return lookupString;
    if (isAllLower) return lookupString.toLowerCase();
    if (isAllUpper) return lookupString.toUpperCase();
    return lookupString;
  }

  public static void assertPhase(Class<? extends CompletionPhase>... possibilities) {
    if (!isPhase(possibilities)) {
      LOG.error(ourPhase + "; set at " + ourPhaseTrace);
    }
  }

  public static boolean isPhase(Class<? extends CompletionPhase>... possibilities) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    for (Class<? extends CompletionPhase> possibility : possibilities) {
      if (possibility.isInstance(ourPhase)) {
        return true;
      }
    }
    return false;
  }

  public static void setCompletionPhase(@NotNull CompletionPhase phase) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    Disposer.dispose(ourPhase);
    ourPhase = phase;
    ourPhaseTrace = DebugUtil.currentStackTrace();
  }

  public static CompletionPhase getCompletionPhase() {
//    ApplicationManager.getApplication().assertIsDispatchThread();
    return ourPhase;
  }

  public CompletionSorterImpl defaultSorter(CompletionParameters parameters) {
    final CompletionLocation location = new CompletionLocation(parameters);

    CompletionSorterImpl sorter = emptySorter().weigh(new LookupElementWeigher("prefixHumps") {
      @NotNull
      @Override
      public Boolean weigh(@NotNull LookupElement element) {
        final String prefix = element.getPrefixMatcher().getPrefix();
        if (!prefix.isEmpty()) {
          final String prefixHumps = StringUtil.capitalsOnly(prefix);
          if (prefixHumps.length() > 0) {
            for (String itemString : element.getAllLookupStrings()) {
              if (StringUtil.capitalsOnly(itemString).startsWith(prefixHumps)) {
                return false;
              }
            }
          }
        }
        return true;
      }
    });


    for (final Weigher weigher : WeighingService.getWeighers(CompletionService.RELEVANCE_KEY)) {
      sorter = sorter.weigh(new LookupElementWeigher(weigher.toString()) {
        @NotNull
        @Override
        public Comparable weigh(@NotNull LookupElement element) {
          return new NegatingComparable(weigher.weigh(element, location));
        }
      });
    }

    return sorter.withClassifier("priority", true, new ClassifierFactory<LookupElement>("liftShorter") {
      @Override
      public Classifier<LookupElement> createClassifier(final Classifier<LookupElement> next) {
        return new LiftShorterItemsClassifier(next);
      }
    });
  }

  public CompletionSorterImpl emptySorter() {
    return new CompletionSorterImpl(new ArrayList<ClassifierFactory<LookupElement>>());
  }

  private static class NegatingComparable<T extends NegatingComparable<T>> implements Comparable<T> {
    private final Comparable myWeigh;

    public NegatingComparable(Comparable weigh) {
      myWeigh = weigh;
    }

    @Override
    public int compareTo(T o) {
      final Comparable w1 = myWeigh;
      final Comparable w2 = o.myWeigh;
      if (w1 == null && w2 == null) return 0;
      if (w1 == null) return 1;
      if (w2 == null) return -1;

      return -w1.compareTo(w2);
    }

    @Override
    public String toString() {
      return String.valueOf(myWeigh);
    }
  }
}
