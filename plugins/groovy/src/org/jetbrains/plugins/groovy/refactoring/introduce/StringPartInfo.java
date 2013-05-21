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
package org.jetbrains.plugins.groovy.refactoring.introduce;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

/**
 * @author Max Medvedev
 */
public class StringPartInfo {
  private final GrLiteral myLiteral;
  private final TextRange myRange;

  public StringPartInfo(@NotNull GrLiteral literal, @NotNull TextRange range) {
    myLiteral = literal;
    myRange = range.shiftRight(-literal.getTextRange().getStartOffset());
  }

  public GrLiteral getLiteral() {
    return myLiteral;
  }

  public TextRange getRange() {
    return myRange;
  }
}
