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
package com.intellij.util;

import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PatternConditionPlus;
import com.intellij.patterns.ValuePatternCondition;
import gnu.trove.THashSet;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Set;

/**
 * @author Gregory.Shrago
 */
public class PatternValuesIndex {

  public static Set<String> buildStringIndex(Collection<ElementPattern<?>> patterns) {
    final THashSet<String> result = new THashSet<String>();
    processStringValues(patterns, new PairProcessor<ElementPattern<?>, Collection<Object>>() {
      public boolean process(ElementPattern<?> elementPattern, Collection<Object> value) {
        for (Object o : value) {
          if (o instanceof String) {
            result.add((String)o);
          }
        }
        return true;
      }
    });
    return result;
  }

  public static boolean processStringValues(Collection<ElementPattern<?>> patterns, final PairProcessor<ElementPattern<?>, Collection<Object>> valueProcessor) {
    final LinkedList<ElementPattern<?>> stack = new LinkedList<ElementPattern<?>>();
    for (final ElementPattern<?> next : patterns) {
      stack.add(next);
      while (!stack.isEmpty()) {
        final ElementPattern<?> pattern = stack.removeFirst();
        for (PatternCondition<?> condition : pattern.getCondition().getConditions()) {
          if (condition instanceof PatternConditionPlus) {
            stack.add(((PatternConditionPlus)condition).getValuePattern());
          }
          else if (condition instanceof ValuePatternCondition) {
            if (!valueProcessor.process(next, ((ValuePatternCondition)condition).getValues())) return false;
          }
        }
      }
    }
    return true;
  }
}
