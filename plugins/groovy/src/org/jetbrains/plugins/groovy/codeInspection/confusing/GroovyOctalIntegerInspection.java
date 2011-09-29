/*
 * Copyright 2007-2008 Dave Griffith
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
package org.jetbrains.plugins.groovy.codeInspection.confusing;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

public class GroovyOctalIntegerInspection extends BaseInspection {

    @Nls
    @NotNull
    public String getGroupDisplayName() {
        return CONFUSING_CODE_CONSTRUCTS;
    }

    @Nls
    @NotNull
    public String getDisplayName() {
        return "Octal integer";
    }

    @Nullable
    protected String buildErrorString(Object... args) {
        return "Octal integer #ref #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new Visitor();
    }

    private static class Visitor extends BaseInspectionVisitor {
        public void visitLiteralExpression(GrLiteral literal) {
            super.visitLiteralExpression(literal);
            @NonNls final String text = literal.getText();
            if (text.startsWith("0") && !"0".equals(text) &&
                    !text.startsWith("0x") && !text.startsWith("0X") &&
                    !text.startsWith("0b") && !text.startsWith("0B") &&
                    !text.contains(".") && !text.contains("e") && !text.contains("E")) {
                registerError(literal);
            }
        }
    }
}