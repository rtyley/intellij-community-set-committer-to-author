
/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.impl.statements;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.stubs.EmptyStub;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.GrReferenceAdjuster;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrStubElementBase;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

/**
 * @author: Dmitry.Krasilschikov
 */
public class GrVariableDeclarationImpl extends GrStubElementBase<EmptyStub> implements GrVariableDeclaration, StubBasedPsiElement<EmptyStub> {
  private static final Logger LOG = Logger.getInstance(GrVariableDeclarationImpl.class);

  public GrVariableDeclarationImpl(@NotNull ASTNode node) {
    super(node);
  }

  public GrVariableDeclarationImpl(EmptyStub stub) {
    super(stub, GroovyElementTypes.VARIABLE_DEFINITION);
  }

  @Override
  public PsiElement getParent() {
    return getDefinitionParent();
  }

  @Override
  public <T extends GrStatement> T replaceWithStatement(T statement) {
    return GroovyPsiElementImpl.replaceWithStatement(this, statement);
  }

  @Override
  public void removeStatement() throws IncorrectOperationException {
    GroovyPsiElementImpl.removeStatement(this);
  }

  @NotNull
  public GrModifierList getModifierList() {
    return (GrModifierList)findNotNullChildByType(GroovyElementTypes.MODIFIERS);
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    return getModifierList().hasModifierProperty(name);
  }

  public void setType(@Nullable PsiType type) {
    final GrTypeElement typeElement = getTypeElementGroovy();
    if (type == null) {
      if (typeElement == null) return;
      getModifierList().setModifierProperty(GrModifier.DEF, true);
      typeElement.delete();
      return;
    }

    type = TypesUtil.unboxPrimitiveTypeWrapper(type);
    GrTypeElement newTypeElement;
    try {
      newTypeElement = GroovyPsiElementFactory.getInstance(getProject()).createTypeElement(type);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return;
    }

    if (typeElement == null) {
      getModifierList().setModifierProperty(GrModifier.DEF, false);
      final GrVariable[] variables = getVariables();
      if (variables.length == 0) return;
      newTypeElement = (GrTypeElement)addBefore(newTypeElement, variables[0]);
    }
    else {
      newTypeElement = (GrTypeElement)typeElement.replace(newTypeElement);
    }

    GrReferenceAdjuster.shortenReferences(newTypeElement);
  }

  @Override
  public boolean isTuple() {
    return findChildByType(GroovyTokenTypes.mLPAREN) != null;
  }

  @Nullable
  @Override
  public GrExpression getTupleInitializer() {
    return findChildByClass(GrExpression.class);
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    final PsiElement psi = child.getPsi();
    if (psi == getTupleInitializer()) {
      deleteChildInternal(findNotNullChildByType(GroovyTokenTypes.mASSIGN).getNode());
    }
    super.deleteChildInternal(child);
  }

  @Override
  public GrTypeElement getTypeElementGroovyForVariable(GrVariable var) {
    if (isTuple()) {
      final PsiElement psiElement = PsiUtil.skipWhitespacesAndComments(var.getPrevSibling(), false);
      if (psiElement instanceof GrTypeElement) {
        return (GrTypeElement)psiElement;
      }
      return null;
    }
    else {
      return getTypeElementGroovy();
    }
  }

  @Nullable
  public GrTypeElement getTypeElementGroovy() {
    if (isTuple()) return null;
    return findChildByClass(GrTypeElement.class);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitVariableDeclaration(this);
  }

  public String toString() {
    return "Variable definitions";
  }

  public GrMember[] getMembers() {
    return findChildrenByClass(GrMember.class);
  }

  @NotNull
  public GrVariable[] getVariables() {
    return getStubOrPsiChildren(GroovyElementTypes.VARIABLES, GrVariable.ARRAY_FACTORY);
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    if (lastParent != null && lastParent == getTupleInitializer()) {
      return true;
    }

    for (final GrVariable variable : getVariables()) {
      if (lastParent == variable) break;
      if (lastParent instanceof GrMethod && !(variable instanceof GrField)) break;
      if (!ResolveUtil.processElement(processor, variable, state)) return false;
    }

    return true;
  }

  @Override
  public PsiReference getReference() {
    return CachedValuesManager.getManager(getProject()).getCachedValue(this, new CachedValueProvider<PsiReference>() {
      @Nullable
      @Override
      public Result<PsiReference> compute() {
        return Result.create(getReferenceInner(), PsiModificationTracker.MODIFICATION_COUNT);
      }
    });
  }

  private PsiReference getReferenceInner() {
    if (getTypeElementGroovy() != null) return null;
    final TextRange range = getRangeForReference();

    if (range == null) return null;

    final GrVariable[] variables = getVariables();
    if (variables.length == 0) return null;

    String canonicalText = getTypeText(variables[0]);
    if (canonicalText == null) return null;

    final PsiElement resolved = variables.length > 1 ? this : variables[0];
    return new PsiReferenceBase<GrVariableDeclaration>(this, range, true) {
      @Nullable
      @Override
      public PsiElement resolve() {
        return resolved;
      }

      @NotNull
      @Override
      public Object[] getVariants() {
        return EMPTY_ARRAY;
      }
    };
  }

  private TextRange getRangeForReference() {
    PsiElement modifier = findSuitableModifier();
    if (modifier == null) return null;

    return modifier.getTextRange().shiftRight(-getTextRange().getStartOffset());
  }

  private PsiElement findSuitableModifier() {
    final GrModifierList list = getModifierList();

    PsiElement modifier = PsiUtil.findModifierInList(list, GrModifier.DEF);
    if (modifier != null) return modifier;

    modifier = PsiUtil.findModifierInList(list, PsiModifier.FINAL);
    if (modifier != null) return modifier;

    for (PsiElement element : list.getModifiers()) {
      if (!(element instanceof GrAnnotation)) {
        return element;
      }
    }

    return null;
  }

  @Nullable
  private static String getTypeText(GrVariable var) {
    final PsiType type = var.getTypeGroovy();
    if (type == null) return null;

    return type.getCanonicalText();
  }
}
