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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrConstructor;

/**
 * @author Dmitry.Krasilschikov
 * @date 26.03.2007
 */
public class GrConstructorImpl extends GrMethodBaseImpl implements GrConstructor {
  public GrConstructorImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Constructor";
  }

  public boolean isConstructor() {
    return true;
  }

  @Nullable
  public GrConstructorInvocation getChainingConstructorInvocation() {
    GrOpenBlock body = getBlock();
    if (body == null) return null;

    GrStatement[] statements = body.getStatements();

    if (statements.length > 0 && statements[0] instanceof GrConstructorInvocation) {
      return (GrConstructorInvocation) statements[0];
    }

    return null;
  }
}
