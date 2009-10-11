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
package com.intellij.openapi.vcs.ui;

import org.jetbrains.annotations.NonNls;
import com.intellij.openapi.actionSystem.DataKey;

/**
 * Base interface for user interface objects which can be refreshed and can save/restore
 * their state.
 *
 * @author lesya
 */
public interface Refreshable {
  /**
   * The data ID which can be used to retrieve the active <code>Refreshable</code>
   * instance from {@link com.intellij.openapi.actionSystem.DataContext}.
   *
   * @see com.intellij.openapi.actionSystem.DataContext#getData(String)
   */
  @NonNls @Deprecated String PANEL = "Panel";
  DataKey<Refreshable> PANEL_KEY = DataKey.create(PANEL);

  void refresh();

  void saveState();
  void restoreState();
}
