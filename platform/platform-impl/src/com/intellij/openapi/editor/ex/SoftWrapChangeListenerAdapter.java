/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.editor.ex;

import com.intellij.openapi.editor.SoftWrap;
import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 11/23/11 7:03 PM
 */
public class SoftWrapChangeListenerAdapter implements SoftWrapChangeListener {
  
  @Override
  public void recalculationStarts() {
  }

  @Override
  public void softWrapAdded(@NotNull SoftWrap softWrap) {
  }

  @Override
  public void softWrapsRemoved() {
  }

  @Override
  public void recalculationEnds() {
  }
}
