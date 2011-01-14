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
package com.intellij.openapi.editor.ex;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.event.EditorEventMulticaster;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeListener;

public interface EditorEventMulticasterEx extends EditorEventMulticaster{
  void addErrorStripeListener(@NotNull ErrorStripeListener listener);
  void addErrorStripeListener(@NotNull ErrorStripeListener listener, @NotNull Disposable parentDisposable);
  void removeErrorStripeListener(@NotNull ErrorStripeListener listener);

  void addEditReadOnlyListener(@NotNull EditReadOnlyListener listener);
  void removeEditReadOnlyListener(@NotNull EditReadOnlyListener listener);

  void addPropertyChangeListener(@NotNull PropertyChangeListener listener);
  void removePropertyChangeListener(@NotNull PropertyChangeListener listener);

  void addFocusChangeListner(@NotNull FocusChangeListener listener);
  void addFocusChangeListner(@NotNull FocusChangeListener listener, @NotNull Disposable parentDisposable);
  void removeFocusChangeListner(@NotNull FocusChangeListener listener);
}
