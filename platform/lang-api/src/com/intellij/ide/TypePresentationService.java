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
package com.intellij.ide;

import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author peter
 */
public abstract class TypePresentationService {

  public static TypePresentationService getService() {
    return ServiceManager.getService(TypePresentationService.class);
  }

  @Nullable
  public abstract Icon getIcon(Object o);

  @Nullable
  public abstract Icon getTypeIcon(Class type);

  @Nullable
  public abstract String getTypePresentableName(Class type);

  @Nullable
  public abstract String getTypeName(Object o);
}
