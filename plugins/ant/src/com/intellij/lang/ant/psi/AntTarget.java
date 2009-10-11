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
package com.intellij.lang.ant.psi;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface AntTarget extends AntStructuredElement {
  enum ConditionalAttribute {
    IF("if"), UNLESS("unless");

    private final String xmlName;

    ConditionalAttribute(@NonNls final String xmlName) {
      this.xmlName = xmlName;
    }

    public String getXmlName() {
      return xmlName;
    }
  }
  
  AntTarget[] EMPTY_ARRAY = new AntTarget[0];

  /**
   * @return If project is named, target name prefixed with project name and the dot,
   * otherwise target name equal to that returned by {@link #getName()}.
   */
  @NotNull
  String getQualifiedName();

  @Nullable
  String getDescription();

  @NotNull
  AntTarget[] getDependsTargets();

  @Nullable
  String getConditionalPropertyName(ConditionalAttribute attrib);
  
  void setDependsTargets(@NotNull AntTarget[] targets);

}
