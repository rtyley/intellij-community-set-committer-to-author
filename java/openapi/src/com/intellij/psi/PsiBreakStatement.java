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
package com.intellij.psi;

import org.jetbrains.annotations.Nullable;

/**
 * Represents a Java <code>break</code> statement.
 */
public interface PsiBreakStatement extends PsiStatement {
  /**
   * Returns the identifier representing the label specified on the statement.
   *
   * @return the identifier for the label, or null if the statement has no label.
   */
  @Nullable
  PsiIdentifier getLabelIdentifier();

  /**
   * Returns the statement instance ({@link PsiForStatement}, {@link PsiSwitchStatement} etc.) representing
   * the statement out of which <code>break</code> transfers control.
   *
   * @return the statement instance, or null if the statement is not valid in the context where it is located.
   */
  @Nullable
  PsiStatement findExitedStatement();
}
