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
package com.intellij.codeInsight.daemon;

import com.intellij.navigation.GotoRelatedItem;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

/**
 * @author nik
 */
public class RelatedItemLineMarkerInfo<T extends PsiElement> extends LineMarkerInfo<T> {
  private final NotNullLazyValue<Collection<? extends GotoRelatedItem>> myTargets;

  public RelatedItemLineMarkerInfo(@NotNull T element, @NotNull TextRange range, Icon icon, int updatePass,
                                   @Nullable Function<? super T, String> tooltipProvider,
                                   @Nullable GutterIconNavigationHandler<T> navHandler,
                                   GutterIconRenderer.Alignment alignment,
                                   @NotNull NotNullLazyValue<Collection<? extends GotoRelatedItem>> targets) {
    super(element, range, icon, updatePass, tooltipProvider, navHandler, alignment);
    myTargets = targets;
  }

  public RelatedItemLineMarkerInfo(@NotNull T element, @NotNull TextRange range, Icon icon, int updatePass,
                                   @Nullable Function<? super T, String> tooltipProvider,
                                   @Nullable GutterIconNavigationHandler<T> navHandler,
                                   GutterIconRenderer.Alignment alignment,
                                   @NotNull final Collection<? extends GotoRelatedItem> targets) {
    this(element, range, icon, updatePass, tooltipProvider, navHandler, alignment, new NotNullLazyValue<Collection<? extends GotoRelatedItem>>() {
      @NotNull
      @Override
      protected Collection<? extends GotoRelatedItem> compute() {
        return targets;
      }
    });
  }

  @NotNull
  public Collection<? extends GotoRelatedItem> createGotoRelatedItems() {
    return myTargets.getValue();
  }
}
