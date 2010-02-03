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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.arguments;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.PropertyResolverProcessor;

/**
 * @author ilyas
 */
public class GrArgumentLabelImpl extends GroovyPsiElementImpl implements GrArgumentLabel {

  public GrArgumentLabelImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitArgumentLabel(this);
  }

  public String toString() {
    return "Argument label";
  }

  public PsiReference getReference() {
    PsiReference[] otherReferences = ReferenceProvidersRegistry.getReferencesFromProviders(this, GrArgumentLabel.class);
    PsiReference[] thisReference = {this};
    return new PsiMultiReference(otherReferences.length == 0 ? thisReference : ArrayUtil.mergeArrays(thisReference, otherReferences, PsiReference.class), this);
  }

  @Nullable
  public String getName() {
    final PsiElement element = getNameElement();
    if (element instanceof GrExpression) {
      return null;
    }
    else {
      return GrStringUtil.removeQuotes(element.getText());
    }
  }

  public PsiElement getElement() {
    return this;
  }

  public TextRange getRangeInElement() {
    return new TextRange(0, getTextLength());
  }

  @Nullable
  public PsiElement resolve() {
    String propName = getText();
    String setterName = PropertyUtil.suggestSetterName(propName);
    PsiElement context = getParent().getParent();
    if (context instanceof GrArgumentList) {
      final PsiElement parent = context.getParent();
      if (parent instanceof GrCallExpression) {
        final PsiMethod resolvedMethod = ((GrCallExpression) parent).resolveMethod();
        if (resolvedMethod != null) {
          final PsiParameter[] parameters = resolvedMethod.getParameterList().getParameters();
          if (parameters.length > 0) {
            if (PsiUtil.createMapType(resolvedMethod.getManager(), resolvedMethod.getResolveScope()).isAssignableFrom(parameters[0].getType())) {
              //call with named argument, not setting property
              return null;
            }
          }
        }
      }

      if (parent instanceof GrExpression || parent instanceof GrAnonymousClassDefinition) {
        PsiType type =
          parent instanceof GrExpression ? ((GrExpression)parent).getType() : ((GrAnonymousClassDefinition)parent).getBaseClassType();
        if (type instanceof PsiClassType) {
          PsiClass clazz = ((PsiClassType) type).resolve();
          if (clazz != null) {
            PsiMethod[] byName = clazz.findMethodsByName(setterName, true);
            if (byName.length > 0) return byName[0];
            final PsiField field = clazz.findFieldByName(propName, true);
            if (field != null) return field;
            final PropertyResolverProcessor processor = new PropertyResolverProcessor(propName, this);
            ResolveUtil
              .processNonCodeMethods(JavaPsiFacade.getElementFactory(getProject()).createType(clazz), processor, getProject(), this, false);
            final GroovyResolveResult[] candidates = processor.getCandidates();
            if (candidates.length == 0) return null;
            return candidates[0].getElement();
          }
        }
      }
    }
    return null;
  }

  public String getCanonicalText() {
    PsiElement resolved = resolve();
    if (resolved instanceof PsiMember && resolved instanceof PsiNamedElement) {
      PsiClass clazz = ((PsiMember) resolved).getContainingClass();
      if (clazz != null) {
        String qName = clazz.getQualifiedName();
        if (qName != null) {
          return qName + "." + ((PsiNamedElement) resolved).getName();
        }
      }
    }

    return getText();
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    final PsiElement resolved = resolve();
    if (resolved instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod) resolved;
      final String oldName = getNameElement().getText();
      if (!method.getName().equals(oldName)) { //was property reference to accessor
        if (PropertyUtil.isSimplePropertySetter(method)) {
          final String newPropertyName = PropertyUtil.getPropertyName(newElementName);
          if (newPropertyName != null) {
            return doHandleElementRename(newPropertyName);
          } else {
            //todo encapsulate fields:)
          }
        }
      }
    }
    return doHandleElementRename(newElementName);
  }

  private PsiElement doHandleElementRename(String newElementName) {
    PsiElement nameElement = getNameElement();
    ASTNode node = nameElement.getNode();
    ASTNode newNameNode = GroovyPsiElementFactory.getInstance(getProject()).createReferenceNameFromText(newElementName).getNode();
    assert newNameNode != null && node != null;
    node.getTreeParent().replaceChild(node, newNameNode);
    return this;
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException("NIY");
  }

  public boolean isReferenceTo(PsiElement element) {
    return (element instanceof PsiMethod || element instanceof PsiField) &&
        getManager().areElementsEquivalent(element, resolve());

  }

  @NotNull
  public Object[] getVariants() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  public boolean isSoft() {
    return false;
  }

  @NotNull
  public PsiElement getNameElement() {
    final PsiElement element = getFirstChild();
    assert element != null;
    return element;
  }

  @Nullable
  public PsiType getExpectedArgumentType() {
    final PsiElement resolved = resolve();
    if (resolved instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod) resolved;
      if (PropertyUtil.isSimplePropertyGetter(method))
        return method.getReturnType();
      if (PropertyUtil.isSimplePropertySetter(method))
        return method.getParameterList().getParameters()[0].getType();

    } else if (resolved instanceof PsiField) {
      return ((PsiField) resolved).getType();
    }

    return null;
  }

  public GrNamedArgument getNamedArgument() {
    final PsiElement parent = getParent();
    assert parent instanceof GrNamedArgument;
    return (GrNamedArgument)parent;
  }
}