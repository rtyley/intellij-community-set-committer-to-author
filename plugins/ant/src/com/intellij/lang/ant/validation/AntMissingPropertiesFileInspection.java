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
package com.intellij.lang.ant.validation;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.AntProject;
import com.intellij.lang.ant.psi.AntProperty;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.psi.impl.AntFileImpl;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class AntMissingPropertiesFileInspection extends AntInspection {

  @NonNls private static final String SHORT_NAME = "AntMissingPropertiesFileInspection";

  @Nls
  @NotNull
  public String getDisplayName() {
    return AntBundle.message("ant.missing.properties.file.inspection");
  }

  @NonNls
  @NotNull
  public String getShortName() {
    return SHORT_NAME;
  }

  @Nullable
  public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (file instanceof AntFile) {
      final AntProject project = ((AntFile)file).getAntProject();
      if (project != null) {
        final List<ProblemDescriptor> problems = new ArrayList<ProblemDescriptor>();
        checkElement(project, manager, problems, isOnTheFly);
        final int problemCount = problems.size();
        if (problemCount > 0) {
          return problems.toArray(new ProblemDescriptor[problemCount]);
        }
      }
    }
    return null;
  }

  private static void checkElement(final AntStructuredElement tag,
                                   @NotNull InspectionManager manager,
                                   final List<ProblemDescriptor> problems, boolean isOnTheFly) {
    for (final PsiElement element : tag.getChildren()) {
      if (element instanceof AntProperty) {
        final AntProperty prop = (AntProperty)element;
        if (AntFileImpl.PROPERTY.equals(prop.getSourceElement().getName())) {
          final String filename = prop.getFileName();
          if (filename != null && prop.getPropertiesFile() == null) {
            problems.add(manager.createProblemDescriptor(prop, AntBundle.message("file.doesnt.exist", filename), isOnTheFly, LocalQuickFix.EMPTY_ARRAY,
                                                         ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
          }
        }
      }
      else if (element instanceof AntStructuredElement) {
        checkElement((AntStructuredElement)element, manager, problems, isOnTheFly);
      }
    }
  }
}

