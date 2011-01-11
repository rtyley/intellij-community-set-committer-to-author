/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package org.jetbrains.plugins.groovy.lang.psi.impl.statements;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveState;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.stubs.EmptyStub;
import com.intellij.psi.stubs.EmptyStubElementType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrVariableDeclarationOwner;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrStubElementBase;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

/**
 * @author: Dmitry.Krasilschikov
 */
public abstract class GrVariableDeclarationBase extends GrStubElementBase<EmptyStub> implements GrVariableDeclaration {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrVariableDeclarationBase");

  public GrVariableDeclarationBase(@NotNull ASTNode node) {
    super(node);
  }

  public GrVariableDeclarationBase(EmptyStub stub, final EmptyStubElementType<GrVariableDeclaration> type) {
    super(stub, type);
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

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
    for (final GrVariable variable : getVariables()) {
      if (lastParent == variable) break;
      if (lastParent instanceof GrMethod && !(variable instanceof GrField)) break;
      if (!ResolveUtil.processElement(processor, variable, state)) return false;
    }

    return true;
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

    PsiUtil.shortenReferences(newTypeElement);
  }

  @Override
  public boolean isTuple() {
    return findChildByClass(GrTupleDeclaration.class) != null;
  }

  @Override
  public GrTypeElement getTypeElementGroovyForVariable(GrVariable var) {
    if (!isTuple()) {
      return getTypeElementGroovy();
    }

    final PsiElement psiElement = PsiUtil.skipWhitespaces(var.getPrevSibling(), false);
    if (psiElement instanceof GrTypeElement) {
      return (GrTypeElement)psiElement;
    }
    return null;
  }

  @Nullable
  public GrTypeElement getTypeElementGroovy() {
    return findChildByClass(GrTypeElement.class);
  }

  @Override
  public void delete() throws IncorrectOperationException {
    PsiElement parent = getParent();
    if (parent instanceof GrVariableDeclarationOwner) {
      PsiElement next = PsiUtil.getNextNonSpace(this);
      ASTNode astNode = parent.getNode();
      if (astNode != null) {
        astNode.removeChild(getNode());
      }
      if (next instanceof LeafPsiElement && next.getNode()!= null && next.getNode().getElementType() == GroovyTokenTypes.mSEMI) {
        next.delete();
      }
      return;
    }
    throw new IncorrectOperationException("Invalid enclosing variable declaration owner");

  }

  public static class GrVariables extends GrVariableDeclarationBase implements StubBasedPsiElement<EmptyStub> {

    public GrVariables(@NotNull ASTNode node) {
      super(node);
    }

    public GrVariables(EmptyStub stub) {
      super(stub, GroovyElementTypes.VARIABLE_DEFINITION);
    }

    public void accept(GroovyElementVisitor visitor) {
      visitor.visitVariableDeclaration(this);
    }

    public String toString() {
      return "Variable definitions";
    }

    public GrVariable[] getVariables() {
      return getStubOrPsiChildren(GroovyElementTypes.VARIABLES, GrVariable.ARRAY_FACTORY);
    }

    public GrMember[] getMembers() {
      return findChildrenByClass(GrMember.class);
    }

  }

  public static class GrMultipleVariables extends GrVariableDeclarationBase {
    public GrMultipleVariables(@NotNull ASTNode node) {
      super(node);
    }

    public String toString() {
      return "Multiple variable definitions";
    }

    @NotNull
    public GrTupleDeclaration getTuple(){
      return findChildByClass(GrTupleDeclaration.class);
    }

    @Nullable
    public GrExpression getInitializerGroovy(){
      return findChildByClass(GrExpression.class);
    }

    public GrVariable[] getVariables() {
      return getTuple().getVariables();
    }

    public GrMember[] getMembers() {
      return GrMember.EMPTY_ARRAY;
    }
  }
}
