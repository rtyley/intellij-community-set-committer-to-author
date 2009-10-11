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
package org.jetbrains.idea.maven.dom.converters;

import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.ResolvingConverter;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public abstract class MavenConstantListConverter extends ResolvingConverter<String> {
  private boolean myStrict;

  protected MavenConstantListConverter() {
    this(true);
  }

  protected MavenConstantListConverter(boolean strict) {
    myStrict = strict;
  }

  @Override
  public String fromString(@Nullable @NonNls String s, ConvertContext context) {
    if (!myStrict) return s;
    return getValues().contains(s) ? s : null;
  }

  public String toString(@Nullable String s, ConvertContext context) {
    return s;
  }

  @NotNull
  public Collection<String> getVariants(ConvertContext context) {
    return getValues();
  }

  protected abstract List<String> getValues();

  @Override
  public String getErrorMessage(@Nullable String s, ConvertContext context) {
    return "<html>Specified value is not acceptable here.<br>Acceptable values: " + StringUtil.join(getValues(), ", ") + "</html>";
  }
}