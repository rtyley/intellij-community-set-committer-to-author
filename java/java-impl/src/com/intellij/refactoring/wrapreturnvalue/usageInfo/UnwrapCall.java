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

import com.intellij.psi.PsiCallExpression;
import com.intellij.refactoring.psi.MutationUtils;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class UnwrapCall extends FixableUsageInfo {
    @NotNull
    private final PsiCallExpression call;
    @NotNull
    private final String unwrapMethod;

    public UnwrapCall(@NotNull PsiCallExpression call, @NotNull String unwrapMethod) {
        super(call);
        this.call =call;
        this.unwrapMethod = unwrapMethod;
    }

    public void fixUsage() throws IncorrectOperationException {
        @NonNls final String newExpression = call.getText() + '.' + unwrapMethod +"()";
        MutationUtils.replaceExpression(newExpression, call);
    }
}
