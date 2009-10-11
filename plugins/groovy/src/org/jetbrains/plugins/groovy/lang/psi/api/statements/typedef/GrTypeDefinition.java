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

package org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef;

import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocCommentOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrClassInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrTopLevelDefintion;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMembersDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrWildcardTypeArgument;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 18.03.2007
 */
public interface GrTypeDefinition extends GrTopStatement, NavigatablePsiElement, PsiClass, GrTopLevelDefintion, GrMemberOwner,
                                          GrDocCommentOwner, GrMember {
  String DEFAULT_BASE_CLASS_NAME = "groovy.lang.GroovyObject";

  GrTypeDefinition[] EMPTY_ARRAY = new GrTypeDefinition[0];

  GrTypeDefinitionBody getBody();

  @NotNull
  GrField[] getFields();

  @NotNull
  GrClassInitializer[] getInitializersGroovy();

  @NotNull
  GrMembersDeclaration[] getMemberDeclarations();

  @Nullable
  String getQualifiedName();

  GrWildcardTypeArgument[] getTypeParametersGroovy();

  @Nullable
  GrExtendsClause getExtendsClause();

  @Nullable
  GrImplementsClause getImplementsClause();

  String[] getSuperClassNames();

  @NotNull
  GrMethod[] getGroovyMethods();

  @NotNull
  PsiMethod[] findCodeMethodsByName(@NonNls String name, boolean checkBases);

  @NotNull
  PsiMethod[] findCodeMethodsBySignature(PsiMethod patternMethod, boolean checkBases);

  @Nullable
  PsiElement getLBraceGroovy();

  @Nullable
  PsiElement getRBraceGroovy();

  boolean isAnonymous();

  String getName();
}
