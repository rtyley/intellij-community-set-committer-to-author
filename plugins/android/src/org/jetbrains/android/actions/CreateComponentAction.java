/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.android.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.annotations.NotNull;

/**
 * @author coyote
 */
public abstract class CreateComponentAction extends CreateComponentActionBase {
  private CreateComponentDialog myDialog;

  public CreateComponentAction(String text, String description) {
    super(text, description);
  }

  @NotNull
  protected final CreateComponentDialog createDialog(Project project, InputValidator validator, PsiDirectory directory) {
    myDialog = new CreateComponentDialog(project, validator);
    return myDialog;
  }

  @NotNull
  protected String getLabel() {
    return myDialog.getLabel(); 
  }
}
