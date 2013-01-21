/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.testutil;

import com.intellij.openapi.roots.OrderRootType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.gradle.LibraryPathType;
import org.jetbrains.plugins.gradle.util.GradleLibraryPathTypeMapper;

import java.util.EnumMap;
import java.util.Map;

/**
 * @author Denis Zhdanov
 * @since 1/17/13 4:07 PM
 */
public class TestGradleLibraryPathTypeMapper implements GradleLibraryPathTypeMapper {

  private static final Map<LibraryPathType, OrderRootType> MAPPINGS = new EnumMap<LibraryPathType, OrderRootType>(LibraryPathType.class);

  static {
    MAPPINGS.put(LibraryPathType.BINARY, OrderRootType.CLASSES);
    MAPPINGS.put(LibraryPathType.SOURCE, OrderRootType.SOURCES);
    MAPPINGS.put(LibraryPathType.DOC, OrderRootType.DOCUMENTATION);
    assert LibraryPathType.values().length == MAPPINGS.size();
  }

  @NotNull
  @Override
  public OrderRootType map(@NotNull LibraryPathType type) {
    return MAPPINGS.get(type);
  }
}
