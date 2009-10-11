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
package com.intellij.psi;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Allows to create references to PSI elements that can survive a reparse and return the corresponding
 * element in the PSI tree after the reparse.
 */
public abstract class SmartPointerManager {
  public static SmartPointerManager getInstance(Project project) {
    return ServiceManager.getService(project, SmartPointerManager.class);
  }

  /**
   * Creates a smart pointer to the specified PSI element.
   *
   * @param element the element to create a pointer to.
   * @return the smart pointer instance.
   */
  @NotNull public abstract <E extends PsiElement> SmartPsiElementPointer<E> createSmartPsiElementPointer(E element);

  /**
   * Creates a smart pointer to the specified PSI element which doesn't hold a strong reference to the PSI
   * element.
   *
   * @param element the element to create a pointer to.
   * @return the smart pointer instance.
   */
  @NotNull public abstract <E extends PsiElement> SmartPsiElementPointer<E> createLazyPointer(E element);
}
