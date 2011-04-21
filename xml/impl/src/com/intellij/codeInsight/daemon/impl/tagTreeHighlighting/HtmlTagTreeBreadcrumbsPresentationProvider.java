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
package com.intellij.codeInsight.daemon.impl.tagTreeHighlighting;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.breadcrumbs.BreadcrumbsPresentationProvider;
import com.intellij.xml.breadcrumbs.CrumbPresentation;
import com.intellij.xml.breadcrumbs.DefaultCrumbsPresentation;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * @author Eugene.Kudelevsky
 */
public class HtmlTagTreeBreadcrumbsPresentationProvider extends BreadcrumbsPresentationProvider {
  private static boolean isMyContext(@NotNull PsiElement deepestElement) {
    final PsiFile file = deepestElement.getContainingFile();
    if (file == null || !HtmlTagTreeHighlightingUtil.isTagTreeHighlightingActive(file)) {
      return false;
    }
    return true;
  }

  @Override
  public CrumbPresentation[] getCrumbPresentations(@NotNull PsiElement[] elements) {
    if (elements.length == 0 || !isMyContext(elements[elements.length - 1])) {
      return null;
    }

    if (!HtmlTagTreeHighlightingUtil.containsTagsWithSameName(elements)) {
      return null;
    }

    final CrumbPresentation[] result = new CrumbPresentation[elements.length];
    final Color[] baseColors = HtmlTagTreeHighlightingUtil.getBaseColors();
    int index = 0;

    for (int i = result.length - 1; i >= 0; i--) {
      if (elements[i] instanceof XmlTag) {
        result[i] = new MyCrumbPresentation(baseColors[index % baseColors.length]);
        index++;
      }
    }
    return result;
  }

  private static class MyCrumbPresentation extends DefaultCrumbsPresentation {
    private final Color myColor;

    private MyCrumbPresentation(Color color) {
      myColor = color;
    }

    @Override
    public Color getBackgroundColor(boolean selected, boolean hovered, boolean light) {
      final Color baseColor = super.getBackgroundColor(selected, hovered, light);
      return HtmlTagTreeHighlightingUtil.makeTransparent(myColor, baseColor, 0.1);
    }
  }
}
