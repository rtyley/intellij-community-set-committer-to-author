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
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.presentation.java.JavaPresentationUtil;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.ui.RowIcon;
import com.intellij.util.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.gpp.GppTypeConverter;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.impl.GrDocCommentUtil;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrThrowsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrNamedArgumentSearchVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameterList;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrStubElementBase;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.params.GrParameterListImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrMethodStub;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.MethodTypeInferencer;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import javax.swing.*;
import java.util.List;

/**
 * @author ilyas
 */
public abstract class GrMethodBaseImpl extends GrStubElementBase<GrMethodStub> implements GrMethod, StubBasedPsiElement<GrMethodStub> {

  protected GrMethodBaseImpl(final GrMethodStub stub, IStubElementType nodeType) {
    super(stub, nodeType);
  }

  public GrMethodBaseImpl(final ASTNode node) {
    super(node);
  }

  @Override
  public PsiElement getParent() {
    return getDefinitionParent();
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitMethod(this);
  }

  public int getTextOffset() {
    return getNameIdentifierGroovy().getTextRange().getStartOffset();
  }

  @NotNull
  public PsiElement getNameIdentifierGroovy() {
    return findNotNullChildByType(TokenSets.PROPERTY_NAMES);
  }

  @Nullable
  public GrOpenBlock getBlock() {
    return this.findChildByClass(GrOpenBlock.class);
  }

  public void setBlock(GrCodeBlock newBlock) {
    ASTNode newNode = newBlock.getNode().copyElement();
    final GrOpenBlock oldBlock = getBlock();
    if (oldBlock == null) {
      getNode().addChild(newNode);
      return;
    }
    getNode().replaceChild(oldBlock.getNode(), newNode);
  }

  public GrParameter[] getParameters() {
    GrParameterListImpl parameterList = findChildByClass(GrParameterListImpl.class);
    if (parameterList != null) {
      return parameterList.getParameters();
    }

    return GrParameter.EMPTY_ARRAY;
  }

  public GrTypeElement getReturnTypeElementGroovy() {
    return findChildByClass(GrTypeElement.class);
  }

  public PsiType getInferredReturnType() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      //todo uncomment when EAP is on
      //LOG.assertTrue(!ApplicationManager.getApplication().isDispatchThread()); //this is a potentially long action
    }
    return GroovyPsiManager.getInstance(getProject()).getType(this, ourTypesCalculator);
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    for (final GrTypeParameter typeParameter : getTypeParameters()) {
      if (!ResolveUtil.processElement(processor, typeParameter, state)) return false;
    }

    for (final GrParameter parameter : getParameters()) {
      if (!ResolveUtil.processElement(processor, parameter, state)) return false;
    }

    processor.handleEvent(ResolveUtil.DECLARATION_SCOPE_PASSED, this);

    return true;
  }

  public GrMember[] getMembers() {
    return new GrMember[]{this};
  }

  private static final Function<GrMethod, PsiType> ourTypesCalculator = new NullableFunction<GrMethod, PsiType>() {
    public PsiType fun(GrMethod method) {
      PsiType nominal = method.getReturnType();
      if (nominal != null && nominal.equals(PsiType.VOID)) return nominal;

      if (GppTypeConverter.hasTypedContext(method)) {
        if (nominal != null) return nominal;

        return PsiType.getJavaLangObject(method.getManager(), method.getResolveScope());
      }

      PsiType inferred = getInferredType(method);
      if (nominal == null) {
        if (inferred == null) {
          return PsiType.getJavaLangObject(method.getManager(), method.getResolveScope());
        }
        return inferred;
      }
      if (inferred != null && inferred != PsiType.NULL) {
        if (inferred instanceof PsiClassType && nominal instanceof PsiClassType) {
          final PsiClassType.ClassResolveResult declaredResult = ((PsiClassType)nominal).resolveGenerics();
          final PsiClass declaredClass = declaredResult.getElement();
          if (declaredClass != null) {
            final PsiClassType.ClassResolveResult initializerResult = ((PsiClassType)inferred).resolveGenerics();
            final PsiClass initializerClass = initializerResult.getElement();
            if (initializerClass != null &&
                com.intellij.psi.util.PsiUtil.isRawSubstitutor(initializerClass, initializerResult.getSubstitutor())) {
              if (declaredClass == initializerClass) return nominal;
              final PsiSubstitutor declaredResultSubstitutor = declaredResult.getSubstitutor();
              final PsiSubstitutor superSubstitutor =
                TypeConversionUtil.getClassSubstitutor(declaredClass, initializerClass, declaredResultSubstitutor);

              if (superSubstitutor != null) {
                return JavaPsiFacade.getInstance(method.getProject()).getElementFactory()
                  .createType(declaredClass, TypesUtil.composeSubstitutors(declaredResultSubstitutor, superSubstitutor));
              }
            }
          }
        }
        if (nominal.isAssignableFrom(inferred)) return inferred;
      }
      return nominal;
    }

    @Nullable
    private PsiType getInferredType(GrMethod method) {
      final GrOpenBlock block = method.getBlock();
      if (block == null) return null;

      if (GroovyPsiManager.isTypeBeingInferred(method)) {
        return null;
      }

      return GroovyPsiManager.inferType(method, new MethodTypeInferencer(block));
    }
  };

  @Nullable
  public PsiType getReturnType() {
    if (isConstructor()) {
      return null;
    }

    final GrTypeElement element = getReturnTypeElementGroovy();
    if (element != null) {
      return element.getType();
    }

    return PsiType.getJavaLangObject(getManager(), getResolveScope());
  }

  @Nullable
  public GrTypeElement setReturnType(@Nullable PsiType newReturnType) {
    GrTypeElement typeElement = getReturnTypeElementGroovy();
    if (newReturnType == null) {
      if (typeElement != null) typeElement.delete();
      return null;
    }
    GrTypeElement newTypeElement = GroovyPsiElementFactory.getInstance(getProject()).createTypeElement(newReturnType);
    if (typeElement == null) {
      GrModifierList list = getModifierList();
      newTypeElement =  (GrTypeElement)addAfter(newTypeElement, list);
    }
    else {
      newTypeElement= (GrTypeElement)typeElement.replace(newTypeElement);
    }

    newTypeElement.accept(new GroovyRecursiveElementVisitor() {
      @Override
      public void visitCodeReferenceElement(GrCodeReferenceElement refElement) {
        super.visitCodeReferenceElement(refElement);
        PsiUtil.shortenReference(refElement);
      }
    });
    return newTypeElement;
  }

  @Override
  public Icon getIcon(int flags) {
    RowIcon baseIcon = createLayeredIcon(GroovyIcons.METHOD, ElementPresentationUtil.getFlags(this, false));
    return ElementPresentationUtil.addVisibilityIcon(this, flags, baseIcon);
  }

  @Override
  public ItemPresentation getPresentation() {
    return JavaPresentationUtil.getMethodPresentation(this);
  }

  @Nullable
  public PsiTypeElement getReturnTypeElement() {
    return null;
  }

  @NotNull
  public GrParameterList getParameterList() {
    final GrParameterList parameterList = getStubOrPsiChild(GroovyElementTypes.PARAMETERS_LIST);
    assert parameterList != null;
    return parameterList;
  }

  @NotNull
  public PsiReferenceList getThrowsList() {
    GrThrowsClause clause = findChildByClass(GrThrowsClause.class);
    assert clause != null : this;
    return clause;
  }

  @Nullable
  public PsiCodeBlock getBody() {
    return null;
  }

  public boolean isConstructor() {
    return false;
  }

  public boolean isVarArgs() {
    GrParameter[] parameters = getParameters();
    return parameters.length > 0 && parameters[parameters.length - 1].isVarArgs();
  }

  @NotNull
  public MethodSignature getSignature(@NotNull PsiSubstitutor substitutor) {
    return MethodSignatureBackedByPsiMethod.create(this, substitutor);
  }

  @Nullable
  public PsiIdentifier getNameIdentifier() {
    return PsiUtil.getJavaNameIdentifier(this);
  }

  @NotNull
  public PsiMethod[] findDeepestSuperMethods() {
    return PsiSuperMethodImplUtil.findDeepestSuperMethods(this);
  }

  @NotNull
  public PsiMethod[] findSuperMethods(boolean checkAccess) {
    return PsiSuperMethodImplUtil.findSuperMethods(this, checkAccess);

    /*PsiClass containingClass = getContainingClass();

    Set<PsiMethod> methods = new HashSet<PsiMethod>();
    findSuperMethodRecursively(methods, containingClass, false, new HashSet<PsiClass>(), createMethodSignature(this),
                                new HashSet<MethodSignature>());

    return methods.toArray(new PsiMethod[methods.size()]);*/
  }

  @NotNull
  public PsiMethod[] findSuperMethods(PsiClass parentClass) {
    return PsiSuperMethodImplUtil.findSuperMethods(this, parentClass);
  }

  @NotNull
  public List<MethodSignatureBackedByPsiMethod> findSuperMethodSignaturesIncludingStatic(boolean checkAccess) {
    return PsiSuperMethodImplUtil.findSuperMethodSignaturesIncludingStatic(this, checkAccess);
  }

  @NotNull
  public PsiMethod[] findSuperMethods() {
    return PsiSuperMethodImplUtil.findSuperMethods(this);
  }

  @Nullable
  public PsiMethod findDeepestSuperMethod() {
    final PsiMethod[] methods = findDeepestSuperMethods();
    if (methods.length > 0) return methods[0];
    return null;
  }

  @NotNull
  public GrModifierList getModifierList() {
    return ObjectUtils.assertNotNull(getStubOrPsiChild(GroovyElementTypes.MODIFIERS));
  }

  public boolean hasModifierProperty(@NonNls @NotNull String name) {
    if (name.equals(PsiModifier.ABSTRACT)) {
      final PsiClass containingClass = getContainingClass();
      if (containingClass != null && containingClass.isInterface()) return true;
    }

    return getModifierList().hasModifierProperty(name);
  }

  @NotNull
  public String getName() {
    final GrMethodStub stub = getStub();
    if (stub != null) {
      return stub.getName();
    }
    return PsiImplUtil.getName(this);
  }

  @NotNull
  public HierarchicalMethodSignature getHierarchicalMethodSignature() {
    return PsiSuperMethodImplUtil.getHierarchicalMethodSignature(this);
  }

  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    PsiImplUtil.setName(name, getNameIdentifierGroovy());
    return this;
  }

  public boolean hasTypeParameters() {
    return getTypeParameters().length > 0;
  }

  @Nullable
  public GrTypeParameterList getTypeParameterList() {
    return getStubOrPsiChild(GroovyElementTypes.TYPE_PARAMETER_LIST);
  }

  @NotNull
  public GrTypeParameter[] getTypeParameters() {
    final GrTypeParameterList list = getTypeParameterList();
    if (list != null) {
      return list.getTypeParameters();
    }

    return GrTypeParameter.EMPTY_ARRAY;
  }

  public PsiClass getContainingClass() {
    PsiElement parent = getParent();
    if (parent instanceof GrTypeDefinitionBody) {
      final PsiElement pparent = parent.getParent();
      if (pparent instanceof PsiClass) {
        return (PsiClass)pparent;
      }
    }


    final PsiFile file = getContainingFile();
    if (file instanceof GroovyFileBase) {
      return ((GroovyFileBase)file).getScriptClass();
    }

    return null;
  }

  @Nullable
  public GrDocComment getDocComment() {
    return GrDocCommentUtil.findDocComment(this);
  }

  public boolean isDeprecated() {
    return false;
  }

  @NotNull
  public SearchScope getUseScope() {
    return com.intellij.psi.impl.PsiImplUtil.getMemberUseScope(this);
  }

  public PsiElement getOriginalElement() {
    final PsiClass containingClass = getContainingClass();
    if (containingClass == null) return this;
    PsiClass originalClass = (PsiClass)containingClass.getOriginalElement();
    final PsiMethod originalMethod = originalClass.findMethodBySignature(this, false);
    return originalMethod != null ? originalMethod : this;
  }



  public void delete() throws IncorrectOperationException {
    PsiElement parent = getParent();
    if (parent instanceof GroovyFileImpl || parent instanceof GrTypeDefinitionBody) {
      super.delete();
      return;
    }
    throw new IncorrectOperationException("Invalid enclosing type definition");
  }

  @NotNull
  public String[] getNamedParametersArray() {
    final GrMethodStub stub = getStub();
    if (stub != null) {
      return stub.getNamedParameters();
    }

    GrOpenBlock body = getBlock();
    if (body == null) return ArrayUtil.EMPTY_STRING_ARRAY;

    GrParameter[] parameters = getParameters();
    if (parameters.length == 0) return ArrayUtil.EMPTY_STRING_ARRAY;
    GrParameter firstParameter = parameters[0];

    PsiType type = firstParameter.getTypeGroovy();
    GrTypeElement typeElement = firstParameter.getTypeElementGroovy();
    //equalsToText can't be called here because of stub creating

    if (type != null && typeElement != null && type.getPresentableText() != null && !type.getPresentableText().endsWith("Map")) {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }

    GrNamedArgumentSearchVisitor visitor = new GrNamedArgumentSearchVisitor(firstParameter.getNameIdentifierGroovy().getText());

    body.accept(visitor);
    return visitor.getResult();
  }

  public PsiMethodReceiver getMethodReceiver() {
    return null;
  }
  public PsiType getReturnTypeNoResolve() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isEquivalentTo(PsiElement another) {
    return PsiClassImplUtil.isMethodEquivalentTo(this, another);
  }
}