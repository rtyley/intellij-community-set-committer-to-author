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
package com.intellij.internal.psiView;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.util.Icons;

import javax.swing.*;

/**
 * @author yole
 */
public class PsiViewerCodeFragmentExtension implements PsiViewerExtension {
  public String getName() {
    return "Java Code Block";
  }

  public Icon getIcon() {
    return Icons.CLASS_INITIALIZER;
  }

  public PsiElement createElement(Project project, String text) {
    return JavaPsiFacade.getElementFactory(project).createCodeBlockFromText(text, null);
  }
}
