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
package com.intellij.patterns;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;
import com.intellij.util.ProcessingContext;

/**
 * @author peter
 */
public abstract class PropertyPatternCondition<T,P> extends PatternCondition<T>{
  private final ElementPattern<? extends P> myPropertyPattern;

  public PropertyPatternCondition(@NonNls String methodName, final ElementPattern<? extends P> propertyPattern) {
    super(methodName);
    myPropertyPattern = propertyPattern;
  }

  @Nullable
  public abstract P getPropertyValue(@NotNull Object o);

  public final boolean accepts(@NotNull final T t, final ProcessingContext context) {
    final P value = getPropertyValue(t);
    return myPropertyPattern.getCondition().accepts(value, context);
  }
}
