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
package com.intellij.openapi.externalSystem.service.settings;

import com.intellij.openapi.externalSystem.util.PaintAwarePanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * Defines contract for a component which knows how to manage particular settings.
 * 
 * @author Denis Zhdanov
 * @since 4/28/13 12:15 AM
 * @param <S>  settings type
 */
public interface ExternalSettingsControl<S> {

  /**
   * Adds current control-specific UI controls to the given canvas.
   * <p/>
   * <b>Note:</b> given canvas component is expected to be managed by a {@link GridBagLayout}. That is the reason on why we use
   * this method instead of a method like 'JComponent getComponent()' - there is a possible case that given canvas has components
   * from more than one control and we might want them to be aligned.
   * 
   * @param canvas        container to use as a holder for UI components specific to the current control
   * @param indentLevel   a hint on how much UI components added by the current control should be indented
   */
  void fillUi(@NotNull PaintAwarePanel canvas, int indentLevel);

  /**
   * Asks current control to reset its state to the initial one.
   */
  void reset();

  /**
   * @return    <code>true</code> if settings exposed by the current control have been modified; <code>false</code> otherwise
   */
  boolean isModified();

  /**
   * Asks current control to fill given settings with the current user-defined values.
   * 
   * @param settings  settings holder
   * @return          <code>null</code> if all is ok; an error message describing the problem otherwise (e.g. an obligatory setting
   *                  is undefined)
   */
  @Nullable
  String apply(@NotNull S settings);

  void disposeUIResources();

  /**
   * Hides/shows {@link #fillUi(PaintAwarePanel, int) components added by the current control}.
   * 
   * @param show  flag which indicates if current control' components should be visible
   */
  void showUi(boolean show);
}
