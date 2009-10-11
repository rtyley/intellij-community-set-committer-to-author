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

package com.intellij.psi.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeChangeEvent;

public class PsiTreeChangeEventImpl extends PsiTreeChangeEvent{
  public enum PsiEventType {
    BEFORE_CHILD_ADDITION,
    BEFORE_CHILD_REMOVAL,
    BEFORE_CHILD_REPLACEMENT,
    BEFORE_CHILD_MOVEMENT,
    BEFORE_CHILDREN_CHANGE,
    BEFORE_PROPERTY_CHANGE,
    CHILD_ADDED,
    CHILD_REMOVED,
    CHILD_REPLACED,
    CHILD_MOVED,
    CHILDREN_CHANGED,
    PROPERTY_CHANGED
  }

  private PsiEventType myCode;

  public PsiTreeChangeEventImpl(PsiManager manager) {
    super(manager);
  }

  public PsiEventType getCode() {
    return myCode;
  }

  public void setCode(PsiEventType code) {
    myCode = code;
  }

  public void setParent(PsiElement parent) {
    myParent = parent;
  }

  public void setOldParent(PsiElement oldParent) {
    myOldParent = oldParent;
  }

  public void setNewParent(PsiElement newParent) {
    myNewParent = newParent;
  }

  public void setChild(PsiElement child) {
    myChild = child;
  }

  public void setOldChild(PsiElement oldChild) {
    myOldChild = oldChild;
  }

  public void setNewChild(PsiElement newChild) {
    myNewChild = newChild;
  }

  public void setElement(PsiElement element) {
    myElement = element;
  }

  public void setPropertyName(String propertyName) {
    myPropertyName = propertyName;
  }

  public void setOldValue(Object oldValue) {
    myOldValue = oldValue;
  }

  public void setNewValue(Object newValue) {
    myNewValue = newValue;
  }

  public void setFile(PsiFile file) {
    myFile = file;
  }

  public void setOffset(int offset) {
    myOffset = offset;
  }

  public int getOffset() {
    return myOffset;
  }

  public void setOldLength(int oldLength) {
    myOldLength = oldLength;
  }

  public int getOldLength() {
    return myOldLength;
  }
}