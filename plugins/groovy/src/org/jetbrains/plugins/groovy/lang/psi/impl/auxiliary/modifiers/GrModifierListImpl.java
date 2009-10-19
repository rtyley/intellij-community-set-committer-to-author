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

package org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.annotator.inspections.GroovyImmutableAnnotationInspection;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 18.03.2007
 */
public class GrModifierListImpl extends GroovyPsiElementImpl implements GrModifierList {
  public GrModifierListImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitModifierList(this);
  }

  public String toString() {
    return "Modifiers";
  }

  @NotNull
  public PsiElement[] getModifiers() {
    List<PsiElement> modifiers = new ArrayList<PsiElement>();
    PsiElement[] modifiersKeywords = findChildrenByType(TokenSets.MODIFIERS, PsiElement.class);
    GrAnnotation[] modifiersAnnotations = findChildrenByClass(GrAnnotation.class);
    PsiElement defKeyword = findChildByType(GroovyTokenTypes.kDEF);

    if (modifiersKeywords.length != 0) modifiers.addAll(Arrays.asList(modifiersKeywords));

    if (modifiersAnnotations.length != 0) modifiers.addAll(Arrays.asList(modifiersAnnotations));

    if (defKeyword != null) modifiers.add(defKeyword);

    return modifiers.toArray(new PsiElement[modifiers.size()]);
  }

  public boolean hasExplicitVisibilityModifiers() {
    return findChildByType(TokenSets.VISIBILITY_MODIFIERS) != null;
  }

  public boolean hasModifierProperty(@NotNull @NonNls String modifier) {
    final PsiElement parent = getParent();
    if (parent instanceof GrVariableDeclaration &&
        parent.getParent() instanceof GrTypeDefinitionBody &&
        !hasExplicitVisibilityModifiers()) { //properties are backed by private fields
      PsiElement pParent = parent.getParent().getParent();
      if (!(pParent instanceof PsiClass) || !((PsiClass)pParent).isInterface()) {
        if (modifier.equals(PsiModifier.PUBLIC)) return true;
        if (modifier.equals(PsiModifier.PROTECTED)) return false;
        if (modifier.equals(PsiModifier.PRIVATE)) return false;
      }
      else {
        if (modifier.equals(PsiModifier.STATIC)) return true;
        if (modifier.equals(PsiModifier.FINAL)) return true;
      }
    }

    if (modifier.equals(PsiModifier.PUBLIC)) {
      //groovy type definitions and methods are public by default
      return findChildByType(GroovyElementTypes.kPRIVATE) == null && findChildByType(GroovyElementTypes.kPROTECTED) == null;
    }

    if (hasOtherModifiers(modifier)) {
      return true;
    }

    if (!(parent instanceof GrVariableDeclaration)) {
      if (modifier.equals(PsiModifier.ABSTRACT)) {
        return (parent instanceof GrTypeDefinition && ((GrTypeDefinition)parent).isInterface()) ||
               findChildByType(GroovyElementTypes.kABSTRACT) != null;
      }
      if (modifier.equals(PsiModifier.NATIVE)) return findChildByType(GroovyElementTypes.kNATIVE) != null;
    }

    if (!(parent instanceof GrTypeDefinition)) {
      //check how type def annotations influent on members annotation
      ASTNode classDefNode = TreeUtil.findParent(getNode(), GroovyElementTypes.CLASS_DEFINITION);
      if (classDefNode != null) {
        PsiElement psiClass = classDefNode.getPsi();

        assert psiClass instanceof GrTypeDefinition;
        GrTypeDefinition typeDefinition = (GrTypeDefinition)psiClass;

        PsiModifierList psiClassModifierList = typeDefinition.getModifierList();

        if (psiClassModifierList != null) {
          PsiAnnotation[] psiClassAnnotations = psiClassModifierList.getAnnotations();

          for (PsiAnnotation psiClassAnnotation : psiClassAnnotations) {
            assert psiClassAnnotation instanceof GrAnnotation;

            if (GroovyImmutableAnnotationInspection.IMMUTABLE.equals(psiClassAnnotation.getQualifiedName())) {
              if (modifier.equals(PsiModifier.FINAL)) return true;
              if (modifier.equals(PsiModifier.PRIVATE)) return true;
            }
          }
        }
      }
    }

    return false;
  }

  public boolean hasExplicitModifier(@NotNull @NonNls String name) {

    if (name.equals(PsiModifier.PUBLIC)) return findChildByType(GroovyElementTypes.kPUBLIC) != null;
    if (name.equals(PsiModifier.ABSTRACT)) return findChildByType(GroovyElementTypes.kABSTRACT) != null;
    if (name.equals(PsiModifier.NATIVE)) return findChildByType(GroovyElementTypes.kNATIVE) != null;
    return hasOtherModifiers(name);
  }

  private boolean hasOtherModifiers(String name) {
    if (name.equals(PsiModifier.PRIVATE)) return findChildByType(GroovyElementTypes.kPRIVATE) != null;
    if (name.equals(PsiModifier.PROTECTED)) return findChildByType(GroovyElementTypes.kPROTECTED) != null;
    if (name.equals(PsiModifier.SYNCHRONIZED)) return findChildByType(GroovyElementTypes.kSYNCHRONIZED) != null;
    if (name.equals(PsiModifier.STRICTFP)) return findChildByType(GroovyElementTypes.kSTRICTFP) != null;
    if (name.equals(PsiModifier.STATIC)) return findChildByType(GroovyElementTypes.kSTATIC) != null;
    if (name.equals(PsiModifier.FINAL)) return findChildByType(GroovyElementTypes.kFINAL) != null;
    if (name.equals(PsiModifier.TRANSIENT)) return findChildByType(GroovyElementTypes.kTRANSIENT) != null;
    return name.equals(PsiModifier.VOLATILE) && findChildByType(GroovyElementTypes.kVOLATILE) != null;
  }

  public void setModifierProperty(@NotNull @NonNls String name, boolean doSet) throws IncorrectOperationException {
    if (PsiModifier.PACKAGE_LOCAL.equals(name)) {
      return;
    }
    if (doSet) {
      final ASTNode modifierNode = GroovyPsiElementFactory.getInstance(getProject()).createModifierFromText(name).getNode();
      if (!"def".equals(name)) {
        final PsiElement[] modifiers = getModifiers();
        if (modifiers.length == 1 && modifiers[0].getText().equals("def")) {
          getNode().replaceChild(findChildByType(GroovyTokenTypes.kDEF).getNode(), modifierNode);
          return;
        }
      }
      addInternal(modifierNode, modifierNode, null, null);
    }
    else {
      final PsiElement[] modifiers = findChildrenByType(TokenSets.MODIFIERS, PsiElement.class);
      for (PsiElement modifier : modifiers) {
        if (name.equals(modifier.getText())) {
          getNode().removeChild(modifier.getNode());
        }
      }
    }
  }

  public void checkSetModifierProperty(@NotNull @NonNls String name, boolean value) throws IncorrectOperationException {
  }

  @NotNull
  public GrAnnotation[] getAnnotations() {
    return findChildrenByClass(GrAnnotation.class);
  }
  @NotNull
  public PsiAnnotation[] getApplicableAnnotations() {
    return getAnnotations();
  }

  @Nullable
  public PsiAnnotation findAnnotation(@NotNull @NonNls String qualifiedName) {
    PsiAnnotation[] annotations = getAnnotations();
    for (PsiAnnotation annotation : annotations) {
      if (qualifiedName.equals(annotation.getQualifiedName())) return annotation;
    }

    return null;
  }

  @NotNull
  public GrAnnotation addAnnotation(@NotNull @NonNls String qualifiedName) {
    final PsiClass psiClass = JavaPsiFacade.getInstance(getProject()).findClass(qualifiedName, getResolveScope());
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(getProject());
    if (psiClass != null && psiClass.isAnnotationType()) {
      final GrAnnotation annotation = (GrAnnotation)addAfter(factory.createModifierFromText("@xxx"), null);
      annotation.getClassReference().bindToElement(psiClass);
      return annotation;
    }

    return (GrAnnotation)addAfter(factory.createModifierFromText("@" + qualifiedName), null);
  }
}
