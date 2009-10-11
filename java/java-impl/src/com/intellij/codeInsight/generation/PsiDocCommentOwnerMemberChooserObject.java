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
package com.intellij.codeInsight.generation;

import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.ui.SimpleTextAttributes;

import javax.swing.*;

/**
 * @author peter
*/
public class PsiDocCommentOwnerMemberChooserObject extends PsiElementMemberChooserObject {
  public PsiDocCommentOwnerMemberChooserObject(final PsiDocCommentOwner owner, final String text, Icon icon) {
    super(owner, text, icon);
  }

  public PsiDocCommentOwner getPsiDocCommentOwner() {
    return (PsiDocCommentOwner)getPsiElement();
  }

  protected SimpleTextAttributes getTextAttributes(final JTree tree) {
    return new SimpleTextAttributes(
        getPsiDocCommentOwner().isDeprecated() ? SimpleTextAttributes.STYLE_STRIKEOUT : SimpleTextAttributes.STYLE_PLAIN,
        tree.getForeground());
  }
}
