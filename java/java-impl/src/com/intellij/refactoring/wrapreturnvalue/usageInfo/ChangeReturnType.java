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
package com.intellij.refactoring.wrapreturnvalue.usageInfo;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiTypeElement;
import com.intellij.refactoring.psi.MutationUtils;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class ChangeReturnType extends FixableUsageInfo {
    @NotNull
    private final PsiMethod method;
    @NotNull
    private final String type;

    public ChangeReturnType(@NotNull PsiMethod method, @NotNull String type) {
        super(method);
        this.type = type;
        this.method = method;
    }

    public void fixUsage() throws IncorrectOperationException {
        final PsiTypeElement returnType = method.getReturnTypeElement();
        MutationUtils.replaceType(type, returnType);
    }
}
