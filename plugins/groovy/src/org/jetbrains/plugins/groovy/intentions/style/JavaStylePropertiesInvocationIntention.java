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

package org.jetbrains.plugins.groovy.intentions.style;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.ErrorUtil;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;

import static org.jetbrains.plugins.groovy.codeInspection.utils.JavaStylePropertiesUtil.fixJavaStyleProperty;
import static org.jetbrains.plugins.groovy.codeInspection.utils.JavaStylePropertiesUtil.isPropertyAccessor;

/**
 * @author ilyas
 */
public class JavaStylePropertiesInvocationIntention extends Intention {
  @Override
  protected boolean isStopElement(PsiElement element) {
    return element instanceof GrClosableBlock || super.isStopElement(element);
  }

  protected void processIntention(@NotNull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
    if (element instanceof GrMethodCall) {
      fixJavaStyleProperty(((GrMethodCall)element));
    }
  }

  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      public boolean satisfiedBy(PsiElement element) {
        return element instanceof GrMethodCall && isPropertyAccessor((GrMethodCall)element) && !ErrorUtil.containsError(element);
      }
    };
  }
}
