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
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.bodies;

import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrEnumDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstantList;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author ven
 */
public class GrEnumDefinitionBodyImpl extends GrTypeDefinitionBodyImpl implements GrEnumDefinitionBody {
  public GrEnumDefinitionBodyImpl(@NotNull ASTNode node) {
    super(node);
  }

  public GrEnumConstantList getEnumConstantList() {
    return findChildByClass(GrEnumConstantList.class);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitEnumDefinitionBody(this);
  }
}
