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

package com.intellij.uiDesigner.quickFixes;

import com.intellij.psi.PsiClass;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.propertyInspector.properties.CustomCreateProperty;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class GenerateCreateComponentsFix extends QuickFix {
  private final PsiClass myClass;

  public GenerateCreateComponentsFix(@NotNull final GuiEditor editor, PsiClass aClass) {
    super(editor, UIDesignerBundle.message("quickfix.generate.custom.create"), null);
    myClass = aClass;
  }

  public void run() {
    CustomCreateProperty.generateCreateComponentsMethod(myClass);
  }
}
