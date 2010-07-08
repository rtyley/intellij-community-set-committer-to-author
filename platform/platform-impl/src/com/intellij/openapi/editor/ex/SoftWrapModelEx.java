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
package com.intellij.openapi.editor.ex;

import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.SoftWrapModel;
import com.intellij.openapi.editor.TextChange;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapDrawingType;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;

/**
 * Extends {@link SoftWrapModel} in order to define implementation-specific contract.
 *
 * @author Denis Zhdanov
 * @since Jun 16, 2010 10:53:59 AM
 */
public interface SoftWrapModelEx extends SoftWrapModel {

  /**
   * Asks current model to adjust logical position for the given visual position if necessary.
   * <p/>
   * Given logical position is allowed to be non-soft wrap aware, i.e. the one calculated as there are no soft wraps at the moment.
   *
   * @param defaultLogical    default logical position that corresponds to the given visual position
   * @param visual            target visual position for which logical position should be adjusted if necessary
   * @return                  logical position that corresponds to the given visual position
   */
  @NotNull
  LogicalPosition adjustLogicalPosition(@NotNull LogicalPosition defaultLogical, @NotNull VisualPosition visual);

  /**
   * Asks current model to adjust logical position for the given document offset.
   *
   * @param defaultLogical  default logical position that corresponds to the given document offset
   * @param offset    target editor document offset
   * @return          logical position for the given editor document offset
   */
  @NotNull
  LogicalPosition adjustLogicalPosition(LogicalPosition defaultLogical, int offset);

  /**
   * Asks current model to adjust visual position that corresponds to the given logical position if necessary.
   * <p/>
   * Given visual position is assumed to be the one that is obtained during soft wraps unaware processing.
   *
   * @param logical         target logical position for {@code 'logical' -> visual} conversion
   * @param defaultVisual   visual position of {@code 'logical' -> visual} conversion that is unaware about soft wraps
   * @return                resulting visual position for the given logical position
   */
  @NotNull
  VisualPosition adjustVisualPosition(@NotNull LogicalPosition logical, @NotNull VisualPosition defaultVisual);

  /**
   * @return    unmodifiable collection of soft wraps currently registered within the current model
   */
  List<? extends TextChange> getRegisteredSoftWraps();

  /**
   * Asks to paint drawing of target type at the given graphics buffer at the given position.
   *
   * @param g             target graphics buffer to draw in
   * @param drawingType   target drawing type
   * @param x             target <code>'x'</code> coordinate to use
   * @param y             target <code>'y'</code> coordinate to use
   * @param lineHeight    line height used at editor
   * @return              painted drawing width
   */
  int paint(@NotNull Graphics g, @NotNull SoftWrapDrawingType drawingType, int x, int y, int lineHeight);

  /**
   * Allows to ask for the minimal width in pixels required for painting of the given type.
   *
   * @param drawingType   target drawing type
   * @return              width in pixels required for the painting of the given type
   */
  int getMinDrawingWidth(@NotNull SoftWrapDrawingType drawingType);
}
