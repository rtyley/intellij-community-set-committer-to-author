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

package com.intellij.openapi.actionSystem;

import com.intellij.ide.IdeView;
import com.intellij.lang.Language;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

/**
 * @author yole
 */
public class LangDataKeys extends PlatformDataKeys {
  public static final DataKey<Module> MODULE = DataKey.create(DataConstants.MODULE);
  public static final DataKey<Module> MODULE_CONTEXT = DataKey.create(DataConstants.MODULE_CONTEXT);
  public static final DataKey<Module[]> MODULE_CONTEXT_ARRAY = DataKey.create(DataConstants.MODULE_CONTEXT_ARRAY);
  public static final DataKey<PsiElement> PSI_ELEMENT = DataKey.create(DataConstants.PSI_ELEMENT);
  public static final DataKey<PsiFile> PSI_FILE = DataKey.create(DataConstants.PSI_FILE);
  public static final DataKey<Language> LANGUAGE = DataKey.create(DataConstants.LANGUAGE);
  public static final DataKey<PsiElement[]> PSI_ELEMENT_ARRAY = DataKey.create(DataConstants.PSI_ELEMENT_ARRAY);
  public static final DataKey<IdeView> IDE_VIEW = DataKey.create(DataConstants.IDE_VIEW);
  public static final DataKey<Condition<AnAction>> PRESELECT_NEW_ACTION_CONDITION = DataKey.create("newElementAction.preselect.id");
}
