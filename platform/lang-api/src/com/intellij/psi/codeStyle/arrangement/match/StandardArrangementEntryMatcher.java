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
package com.intellij.psi.codeStyle.arrangement.match;

import com.intellij.psi.codeStyle.arrangement.ArrangementEntry;
import com.intellij.psi.codeStyle.arrangement.ArrangementOperator;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementCompositeMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchConditionVisitor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * {@link ArrangementEntryMatcher} which is based on standard match conditions like {@link ArrangementEntryType entry type}
 * or {@link ArrangementModifier modifier}.
 * <p/>
 * Implementations of this interface are expected to be thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/26/12 11:07 PM
 */
public class StandardArrangementEntryMatcher implements ArrangementEntryMatcher {

  @NotNull private final ArrangementMatchCondition myCondition;
  @NotNull private final ArrangementEntryMatcher   myDelegate;

  public StandardArrangementEntryMatcher(@NotNull ArrangementMatchCondition condition) {
    myCondition = condition;
    myDelegate = doBuildMatcher(condition);
  }

  @NotNull
  public ArrangementMatchCondition getCondition() {
    return myCondition;
  }

  @Override
  public boolean isMatched(@NotNull ArrangementEntry entry) {
    return myDelegate.isMatched(entry);
  }

  @NotNull
  private static ArrangementEntryMatcher doBuildMatcher(@NotNull ArrangementMatchCondition condition) {
    MyVisitor visitor = new MyVisitor();
    condition.invite(visitor);
    return visitor.getMatcher();
  }

  private static class MyVisitor implements ArrangementMatchConditionVisitor {

    @NotNull private final List<ArrangementEntryMatcher> myMatchers  = new ArrayList<ArrangementEntryMatcher>();
    @NotNull private final Set<ArrangementEntryType>     myTypes     = EnumSet.noneOf(ArrangementEntryType.class);
    @NotNull private final Set<ArrangementModifier>      myModifiers = EnumSet.noneOf(ArrangementModifier.class);

    private ArrangementOperator myOperator;
    private boolean             nestedComposite;

    @Override
    public void visit(@NotNull ArrangementAtomMatchCondition condition) {
      switch (condition.getType()) {
        case TYPE:
          myTypes.add((ArrangementEntryType)condition.getValue());
          break;
        case MODIFIER:
          myModifiers.add((ArrangementModifier)condition.getValue());
      }
    }

    @Override
    public void visit(@NotNull ArrangementCompositeMatchCondition condition) {
      if (!nestedComposite) {
        myOperator = condition.getOperator();
        nestedComposite = true;
        for (ArrangementMatchCondition c : condition.getOperands()) {
          c.invite(this);
        }
      }
      else {
        myMatchers.add(doBuildMatcher(condition));
      }
    }

    @SuppressWarnings("ConstantConditions")
    @NotNull
    public ArrangementEntryMatcher getMatcher() {
      ByTypeArrangementEntryMatcher byType = myTypes.isEmpty() ? null : new ByTypeArrangementEntryMatcher(myTypes);
      ByModifierArrangementEntryMatcher byModifiers = myModifiers.isEmpty() ? null : new ByModifierArrangementEntryMatcher(myModifiers);
      assert byType != null || byModifiers != null || (myOperator != null && !myMatchers.isEmpty());
      if (myMatchers.isEmpty() && (byType == null ^ byModifiers == null)) {
        return byModifiers == null ? byType : byModifiers;
      }
      else if (myMatchers.size() == 1) {
        return myMatchers.get(0);
      }
      else {
        CompositeArrangementEntryMatcher result = new CompositeArrangementEntryMatcher(myOperator);
        for (ArrangementEntryMatcher matcher : myMatchers) {
          result.addMatcher(matcher);
        }
        if (byType != null) {
          result.addMatcher(byType);
        }
        if (byModifiers != null) {
          result.addMatcher(byModifiers);
        }
        return result;
      }
    }
  }
}
