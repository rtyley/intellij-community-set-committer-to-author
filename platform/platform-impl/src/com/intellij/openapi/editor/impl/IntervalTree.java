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
package com.intellij.openapi.editor.impl;

import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

/**
 * User: cdr
 */
public interface IntervalTree<T extends Interval> extends Iterable<T> {
  boolean process(@NotNull Processor<? super T> processor);
  boolean processOverlappingWith(int start, int end, @NotNull Processor<? super T> processor);
  boolean processOverlappingWith(int offset, @NotNull Processor<? super T> processor);

  Object addInterval(@NotNull T interval, int start, int end, Object data);
  boolean removeInterval(@NotNull T interval);
}
