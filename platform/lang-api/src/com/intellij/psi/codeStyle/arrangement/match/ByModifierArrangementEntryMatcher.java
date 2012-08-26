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
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * @author Denis Zhdanov
 * @since 8/26/12 11:21 PM
 */
public class ByModifierArrangementEntryMatcher implements ArrangementEntryMatcher {

  @NotNull private final Set<ArrangementModifier> myModifiers = EnumSet.noneOf(ArrangementModifier.class);

  public ByModifierArrangementEntryMatcher(@NotNull Collection<ArrangementModifier> interestedModifiers) {
    myModifiers.addAll(interestedModifiers);
  }
  
  @Override
  public boolean isMatched(@NotNull ArrangementEntry entry) {
    if (entry instanceof ModifierAwareArrangementEntry) {
      return ((ModifierAwareArrangementEntry)entry).getModifiers().containsAll(myModifiers);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return myModifiers.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ByModifierArrangementEntryMatcher matcher = (ByModifierArrangementEntryMatcher)o;

    if (!myModifiers.equals(matcher.myModifiers)) {
      return false;
    }

    return true;
  }

  @Override
  public String toString() {
    return "with modifiers " + myModifiers;
  }
}
