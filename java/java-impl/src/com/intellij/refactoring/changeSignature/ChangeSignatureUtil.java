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
package com.intellij.refactoring.changeSignature;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.List;

/**
 * @author dsl
 */
public class ChangeSignatureUtil {
  private ChangeSignatureUtil() {}

  public static <Parent extends PsiElement, Child extends PsiElement>
  void synchronizeList(Parent list, final List<Child> newElements, ChildrenGenerator<Parent, Child> generator, final boolean[] shouldRemoveChild)
    throws IncorrectOperationException {

    ArrayList<Child> elementsToRemove = null;
    List<Child> elements;

    int index = 0;
    while (true) {
      elements = generator.getChildren(list);
      if (index == newElements.size()) break;

      if (elementsToRemove == null) {
        elementsToRemove = new ArrayList<Child>();
        for (int i = 0; i < shouldRemoveChild.length; i++) {
          if (shouldRemoveChild[i] && i < elements.size()) {
            elementsToRemove.add(elements.get(i));
          }
        }
      }

      Child oldElement = index < elements.size() ? elements.get(index) : null;
      Child newElement = newElements.get(index);
      if (!newElement.equals(oldElement)) {
        if (oldElement != null && elementsToRemove.contains(oldElement)) {
          oldElement.delete();
          index--;
        }
        else {
          assert list.isWritable() : PsiUtilBase.getVirtualFile(list);
          list.addBefore(newElement, oldElement);
          if (list.equals(newElement.getParent())) {
            newElement.delete();
          }
        }
      }
      index++;
    }
    for (int i = newElements.size(); i < elements.size(); i++) {
      Child element = elements.get(i);
      element.delete();
    }
  }

  public static interface ChildrenGenerator<Parent extends PsiElement, Child extends PsiElement> {
    List<Child> getChildren(Parent parent);
  }
}
