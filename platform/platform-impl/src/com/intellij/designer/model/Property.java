/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.designer.model;

import com.intellij.designer.propertyTable.PropertyEditor;
import com.intellij.designer.propertyTable.PropertyRenderer;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public abstract class Property<T extends PropertiesContainer> {
  private final Property myParent;
  private final String myName;
  private boolean myImportant;
  private boolean myExpert;
  private boolean myDeprecated;

  public Property(@Nullable Property parent, @NotNull String name) {
    myParent = parent;
    myName = name;
  }

  @Nullable
  public Property<T> createForNewPresentation() {
    return createForNewPresentation(myParent, myName);
  }

  @Nullable
  public Property<T> createForNewPresentation(@Nullable Property parent, @NotNull String name) {
    return null;
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Hierarchy
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  @Nullable
  public final Property getParent() {
    return myParent;
  }

  @NotNull
  public List<Property<T>> getChildren(@Nullable T component) {
    return Collections.emptyList();
  }

  public int getIndent() {
    if (myParent != null) {
      return myParent.getParent() != null ? 2 : 1;
    }
    return 0;
  }

  public String getPath() {
    return myParent == null ? myName : myParent.getPath() + "/" + myName;
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Value
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  @Nullable
  public Object getValue(T component) throws Exception {
    return null;
  }

  public void setValue(T component, @Nullable Object value) throws Exception {
  }

  public boolean isDefaultValue(T component) throws Exception {
    return false;
  }

  public void setDefaultValue(T component) throws Exception {
  }

  public boolean availableFor(List<PropertiesContainer> components) {
    return true;
  }

  public boolean needRefreshPropertyList() {
    return false;
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Presentation
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  @NotNull
  public final String getName() {
    return myName;
  }

  @Nullable
  public String getTooltip() {
    return null;
  }

  public boolean isImportant() {
    return myImportant;
  }

  public void setImportant(boolean important) {
    myImportant = important;
  }

  public boolean isExpert() {
    return myExpert;
  }

  public void setExpert(boolean expert) {
    myExpert = expert;
  }

  public boolean isDeprecated() {
    return myDeprecated;
  }

  public void setDeprecated(boolean deprecated) {
    myDeprecated = deprecated;
  }

  @NotNull
  public abstract PropertyRenderer getRenderer();

  @Nullable
  public abstract PropertyEditor getEditor();

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Javadoc
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  @Nullable
  public PsiElement getJavadocElement() {
    return null;
  }

  @Nullable
  public String getJavadocText() {
    return null;
  }
}