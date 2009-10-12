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
package com.intellij.util.xml;

import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public interface DomFileElement<T extends DomElement> extends DomElement, UserDataHolder, ModificationTracker {
  @NotNull
  XmlFile getFile();

  @NotNull
  XmlFile getOriginalFile();

  @Nullable
  XmlTag getRootTag();

  @NotNull
  T getRootElement();

  @NotNull
  Class<T> getRootElementClass();

  @NotNull
  DomFileDescription<T> getFileDescription();

}
