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

/*
 * @author max
 */
package com.intellij.psi;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;

public class IdentitySmartPointer<T extends PsiElement> implements SmartPsiElementPointer<T> {
  private T myElement;
  private final PsiFile myFile;

  public IdentitySmartPointer(T element, PsiFile file) {
    myElement = element;
    myFile = file;
  }

  public IdentitySmartPointer(final T element) {
    this(element, element.getContainingFile());
  }

  @NotNull
  public Project getProject() {
    return myFile.getProject();
  }

  public T getElement() {
    if (myElement != null && !myElement.isValid()) {
      myElement = null;
    }
    return myElement;
  }

  public int hashCode() {
    final T elt = getElement();
    return elt == null ? 0 : elt.hashCode();
  }

  public boolean equals(Object obj) {
    return obj instanceof SmartPsiElementPointer && Comparing.equal(getElement(), ((SmartPsiElementPointer)obj).getElement());
  }

  public PsiFile getContainingFile() {
    return myFile;
  }
}
