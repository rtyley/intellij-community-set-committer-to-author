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

package com.intellij.xdebugger.stepping;

import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
* Created by IntelliJ IDEA.
* User: Maxim.Mossienko
* Date: 13.04.2009
* Time: 18:02:30
* To change this template use File | Settings | File Templates.
*/
public class PsiBackedSmartStepIntoVariant<T extends PsiNamedElement & NavigationItem> extends XSmartStepIntoVariant {
  private final T myElement;
  private final ItemPresentation myPresentation;

  public PsiBackedSmartStepIntoVariant(@NotNull T element) {
    myElement = element;
    myPresentation = element.getPresentation();
    assert myPresentation != null: "Invalid presentation:" + myElement;
  }

  public String getText() {
    String location = myPresentation.getLocationString();
    return myPresentation.getPresentableText() + (location != null ? " " + location: "");
  }

  @Override
  public Icon getIcon() {
    return myPresentation.getIcon(false);
  }

  public T getElement() {
    return myElement;
  }
}
