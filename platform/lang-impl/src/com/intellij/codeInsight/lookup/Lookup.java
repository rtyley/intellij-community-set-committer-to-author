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

package com.intellij.codeInsight.lookup;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;

public interface Lookup extends UserDataHolder{
  char NORMAL_SELECT_CHAR = '\n';
  char REPLACE_SELECT_CHAR = '\t';
  char COMPLETE_STATEMENT_SELECT_CHAR = '\r';
  char AUTO_INSERT_SELECT_CHAR = (char) 0;

  @Nullable
  LookupElement getCurrentItem();
  void setCurrentItem(LookupElement item);

  void addLookupListener(LookupListener listener);
  void removeLookupListener(LookupListener listener);

  /**
   * @return bounds in layered pane coordinate system
   */
  Rectangle getBounds();

  /**
   * @return bounds of the current item in the layered pane coordinate system.
   */
  Rectangle getCurrentItemBounds();
  boolean isPositionedAboveCaret();

  @Nullable
  PsiElement getPsiElement();

  Editor getEditor();

  PsiFile getPsiFile();

  boolean isCompletion();

  java.util.List<LookupElement> getItems();

  boolean isFocused();

  void showItemPopup(JBPopup hint);
}
