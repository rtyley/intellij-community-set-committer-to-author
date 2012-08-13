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
package com.intellij.psi.codeStyle.arrangement;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryType;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementModifier;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementMatcherSettings;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementStandardSettingsAware;
import com.intellij.psi.codeStyle.arrangement.sort.ArrangementEntrySortType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 7/20/12 2:31 PM
 */
public class JavaRearranger implements Rearranger<JavaElementArrangementEntry>, ArrangementStandardSettingsAware {

  @NotNull
  @Override
  public Collection<JavaElementArrangementEntry> parse(@NotNull PsiElement root,
                                                       @NotNull Document document,
                                                       @NotNull Collection<TextRange> ranges)
  {
    // Following entries are subject to arrangement: class, interface, field, method.
    List<JavaElementArrangementEntry> result = new ArrayList<JavaElementArrangementEntry>();
    root.accept(new JavaArrangementVisitor(result, document, ranges));
    return result;
  }

  @Override
  public boolean isNameFilterEnabled(@NotNull ArrangementMatcherSettings settings) {
    // TODO den implement 
    return true;
  }

  @Override
  public boolean isEnabled(@NotNull ArrangementEntryType type, @Nullable ArrangementMatcherSettings settings) {
    // TODO den implement 
    return true;
  }

  @Override
  public boolean isEnabled(@NotNull ArrangementModifier modifier, @Nullable ArrangementMatcherSettings settings) {
    // TODO den implement 
    return true;
  }

  @Override
  public boolean isEnabled(@NotNull ArrangementEntrySortType type, @Nullable ArrangementMatcherSettings settings) {
    // TODO den implement 
    return true;
  }
}
