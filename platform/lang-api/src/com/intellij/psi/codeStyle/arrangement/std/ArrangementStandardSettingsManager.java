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
package com.intellij.psi.codeStyle.arrangement.std;

import com.intellij.application.options.codeStyle.arrangement.color.ArrangementColorsProvider;
import com.intellij.psi.codeStyle.arrangement.ArrangementUtil;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryMatcher;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementStandardSettingsAware;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementSettingsToken;
import com.intellij.psi.codeStyle.arrangement.std.CompositeArrangementSettingsToken;
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementSettings;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.containers.ContainerUtilRt;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Wraps {@link ArrangementStandardSettingsAware} for the common arrangement UI managing code.
 * 
 * @author Denis Zhdanov
 * @since 3/7/13 3:11 PM
 */
public class ArrangementStandardSettingsManager {

  @NotNull private final TObjectIntHashMap<ArrangementSettingsToken> myWidths  = new TObjectIntHashMap<ArrangementSettingsToken>();
  @NotNull private final TObjectIntHashMap<ArrangementSettingsToken> myWeights = new TObjectIntHashMap<ArrangementSettingsToken>();

  @NotNull private final Comparator<ArrangementSettingsToken> myComparator = new Comparator<ArrangementSettingsToken>() {
    @Override
    public int compare(ArrangementSettingsToken t1, ArrangementSettingsToken t2) {
      if (myWeights.containsKey(t1)) {
        if (myWeights.containsKey(t2)) {
          return myWeights.get(t1) - myWeights.get(t2);
        }
        else {
          return -1;
        }
      }
      else if (myWeights.containsKey(t2)) {
        return 1;
      }
      else {
        return t1.compareTo(t2);
      }
    }
  };

  @NotNull private final ArrangementStandardSettingsAware          myDelegate;
  @NotNull private final ArrangementColorsProvider                 myColorsProvider;
  @NotNull private final Collection<Set<ArrangementSettingsToken>> myMutexes;

  @Nullable private final StdArrangementSettings                  myDefaultSettings;
  @Nullable private final List<CompositeArrangementSettingsToken> myGroupingTokens;
  @Nullable private final List<CompositeArrangementSettingsToken> myMatchingTokens;

  public ArrangementStandardSettingsManager(@NotNull ArrangementStandardSettingsAware delegate,
                                            @NotNull ArrangementColorsProvider colorsProvider)
  {
    myDelegate = delegate;
    myColorsProvider = colorsProvider;
    myMutexes = delegate.getMutexes();
    myDefaultSettings = delegate.getDefaultSettings();

    SimpleColoredComponent renderer = new SimpleColoredComponent();
    myGroupingTokens = delegate.getSupportedGroupingTokens();
    if (myGroupingTokens != null) {
      parseWidths(myGroupingTokens, renderer);
      buildWeights(myGroupingTokens);
    }

    myMatchingTokens = delegate.getSupportedMatchingTokens();
    if (myMatchingTokens != null) {
      parseWidths(myMatchingTokens, renderer);
      buildWeights(myMatchingTokens);
    }
  }

  private void parseWidths(@NotNull Collection<CompositeArrangementSettingsToken> compositeTokens,
                           @NotNull SimpleColoredComponent renderer)
  {
    int width = 0;
    for (CompositeArrangementSettingsToken compositeToken : compositeTokens) {
      width = Math.max(width, parseWidth(compositeToken.getToken(), renderer));
    }
    for (CompositeArrangementSettingsToken compositeToken : compositeTokens) {
      myWidths.put(compositeToken.getToken(), width);
      parseWidths(compositeToken.getChildren(), renderer);
    }
  }

  private void buildWeights(@NotNull Collection<CompositeArrangementSettingsToken> compositeTokens) {
    for (CompositeArrangementSettingsToken token : compositeTokens) {
      myWeights.put(token.getToken(), myWeights.size());
      buildWeights(token.getChildren());
    }
  }

  /**
   * @see ArrangementStandardSettingsAware#getDefaultSettings()
   */
  @Nullable
  public StdArrangementSettings getDefaultSettings() {
    return myDefaultSettings;
  }

  /**
   * @see ArrangementStandardSettingsAware#getSupportedGroupingTokens()
   */
  @Nullable
  public List<CompositeArrangementSettingsToken> getSupportedGroupingTokens() {
    return myGroupingTokens;
  }

  /**
   * @see ArrangementStandardSettingsAware#getSupportedMatchingTokens()
   */
  @Nullable
  public List<CompositeArrangementSettingsToken> getSupportedMatchingTokens() {
    return myMatchingTokens;
  }
  
  public boolean isEnabled(@NotNull ArrangementSettingsToken token, @Nullable ArrangementMatchCondition current) {
    return myDelegate.isEnabled(token, current);
  }

  @NotNull
  public ArrangementEntryMatcher buildMatcher(@NotNull ArrangementMatchCondition condition) throws IllegalArgumentException {
    ArrangementEntryMatcher matcher = ArrangementUtil.buildMatcher(condition);
    if (matcher == null) {
      matcher = myDelegate.buildMatcher(condition);
    }
    return matcher;
  }

  @NotNull
  public Collection<Set<ArrangementSettingsToken>> getMutexes() {
    return myMutexes;
  }

  public int getWidth(@NotNull ArrangementSettingsToken token) {
    if (myWidths.containsKey(token)) {
      return myWidths.get(token);
    }
    return parseWidth(token, new SimpleColoredComponent());
  }

  private int parseWidth(@NotNull ArrangementSettingsToken token, @NotNull SimpleColoredComponent renderer) {
    renderer.clear();
    renderer.append(token.getRepresentationValue(),
                    SimpleTextAttributes.fromTextAttributes(myColorsProvider.getTextAttributes(token, true)));
    int result = renderer.getPreferredSize().width;

    renderer.clear();
    renderer.append(token.getRepresentationValue(),
                    SimpleTextAttributes.fromTextAttributes(myColorsProvider.getTextAttributes(token, false)));
    return Math.max(result, renderer.getPreferredSize().width);
  }
  
  public List<ArrangementSettingsToken> sort(@NotNull Collection<ArrangementSettingsToken> tokens) {
    List<ArrangementSettingsToken> result = ContainerUtilRt.newArrayList(tokens);
    Collections.sort(result, myComparator);
    return result;
  }
}
