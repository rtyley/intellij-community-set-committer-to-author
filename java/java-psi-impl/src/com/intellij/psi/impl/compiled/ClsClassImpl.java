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
package com.intellij.psi.impl.compiled;

import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProviders;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.impl.InheritanceImplUtil;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiClassStub;
import com.intellij.psi.impl.source.*;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class ClsClassImpl extends ClsRepositoryPsiElement<PsiClassStub<?>> implements PsiExtensibleClass, PsiQualifiedNamedElement, Queryable {
  public static final Key<PsiClass> DELEGATE_KEY = Key.create("DELEGATE");

  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.compiled.ClsClassImpl");

  private final ClassInnerStuffCache myInnersCache = new ClassInnerStuffCache(this);
  private final PsiIdentifier myNameIdentifier;
  private final PsiDocComment myDocComment;

  public ClsClassImpl(final PsiClassStub stub) {
    super(stub);
    myDocComment = isDeprecated() ? new ClsDocCommentImpl(this) : null;
    myNameIdentifier = new ClsIdentifierImpl(this, getShortName());
  }

  @Override
  @NotNull
  public PsiElement[] getChildren() {
    PsiIdentifier name = getNameIdentifier();
    PsiDocComment docComment = getDocComment();
    PsiModifierList modifierList = getModifierList();
    PsiReferenceList extendsList = getExtendsList();
    PsiReferenceList implementsList = getImplementsList();
    List<PsiField> fields = getOwnFields();
    List<PsiMethod> methods = getOwnMethods();
    List<PsiClass> classes = getOwnInnerClasses();

    int count =
      (docComment != null ? 1 : 0)
      + 1 // modifierList
      + 1 // name
      + 1 // extends list
      + 1 // implementsList
      + fields.size()
      + methods.size()
      + classes.size();
    PsiElement[] children = new PsiElement[count];

    int offset = 0;
    if (docComment != null) {
      children[offset++] = docComment;
    }

    children[offset++] = modifierList;
    children[offset++] = name;
    children[offset++] = extendsList;
    children[offset++] = implementsList;

    ArrayUtil.copy(fields, children, offset);
    offset += fields.size();
    ArrayUtil.copy(methods, children, offset);
    offset += methods.size();
    ArrayUtil.copy(classes, children, offset);
    /*offset += classes.size();*/

    return children;
  }

  @Override
  @NotNull
  public PsiIdentifier getNameIdentifier() {
    return myNameIdentifier;
  }

  private String getShortName() {
    String qName = getQualifiedName();
    String name = PsiNameHelper.getShortClassName(qName);
    if (name.length() == 0) {
      name = "_";
    }
    return name;
  }

  @Override
  @NotNull
  public String getName() {
    return getStub().getName();
  }

  @Override
  @NotNull
  public PsiTypeParameterList getTypeParameterList() {
    return getStub().findChildStubByType(JavaStubElementTypes.TYPE_PARAMETER_LIST).getPsi();
  }

  @Override
  public boolean hasTypeParameters() {
    return PsiImplUtil.hasTypeParameters(this);
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    PsiImplUtil.setName(getNameIdentifier(), name);
    return this;
  }

  @Override
  @NotNull
  public String getQualifiedName() {
    return getStub().getQualifiedName();
  }

  @Override
  @NotNull
  public PsiModifierList getModifierList() {
    return getStub().findChildStubByType(JavaStubElementTypes.MODIFIER_LIST).getPsi();
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    return getModifierList().hasModifierProperty(name);
  }

  @Override
  @NotNull
  public PsiReferenceList getExtendsList() {
    return getStub().findChildStubByType(JavaStubElementTypes.EXTENDS_LIST).getPsi();
  }

  @Override
  @NotNull
  public PsiReferenceList getImplementsList() {
    return getStub().findChildStubByType(JavaStubElementTypes.IMPLEMENTS_LIST).getPsi();
  }

  @Override
  @NotNull
  public PsiClassType[] getExtendsListTypes() {
    return PsiClassImplUtil.getExtendsListTypes(this);
  }

  @Override
  @NotNull
  public PsiClassType[] getImplementsListTypes() {
    return PsiClassImplUtil.getImplementsListTypes(this);
  }

  @Override
  public PsiClass getSuperClass() {
    return PsiClassImplUtil.getSuperClass(this);
  }

  @Override
  public PsiClass[] getInterfaces() {
    return PsiClassImplUtil.getInterfaces(this);
  }

  @Override
  @NotNull
  public PsiClass[] getSupers() {
    return PsiClassImplUtil.getSupers(this);
  }

  @Override
  @NotNull
  public PsiClassType[] getSuperTypes() {
    return PsiClassImplUtil.getSuperTypes(this);
  }

  @Override
  public PsiClass getContainingClass() {
    PsiElement parent = getParent();
    return parent instanceof PsiClass ? (PsiClass)parent : null;
  }

  @Override
  @NotNull
  public Collection<HierarchicalMethodSignature> getVisibleSignatures() {
    return PsiSuperMethodImplUtil.getVisibleSignatures(this);
  }

  @Override
  @NotNull
  public PsiField[] getFields() {
    return myInnersCache.getFields();
  }

  @Override
  @NotNull
  public PsiMethod[] getMethods() {
    return myInnersCache.getMethods();
  }

  @Override
  @NotNull
  public PsiMethod[] getConstructors() {
    return myInnersCache.getConstructors();
  }

  @Override
  @NotNull
  public PsiClass[] getInnerClasses() {
    return myInnersCache.getInnerClasses();
  }

  @NotNull
  @Override
  public List<PsiField> getOwnFields() {
    return Arrays.asList(getStub().getChildrenByType(Constants.FIELD_BIT_SET, PsiField.ARRAY_FACTORY));
  }

  @NotNull
  @Override
  public List<PsiMethod> getOwnMethods() {
    return Arrays.asList(getStub().getChildrenByType(Constants.METHOD_BIT_SET, PsiMethod.ARRAY_FACTORY));
  }

  @NotNull
  @Override
  public List<PsiClass> getOwnInnerClasses() {
    return Arrays.asList(getStub().getChildrenByType(JavaStubElementTypes.CLASS, PsiClass.ARRAY_FACTORY));
  }

  @Override
  @NotNull
  public PsiClassInitializer[] getInitializers() {
    return PsiClassInitializer.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public PsiTypeParameter[] getTypeParameters() {
    return PsiImplUtil.getTypeParameters(this);
  }

  @Override
  @NotNull
  public PsiField[] getAllFields() {
    return PsiClassImplUtil.getAllFields(this);
  }

  @Override
  @NotNull
  public PsiMethod[] getAllMethods() {
    return PsiClassImplUtil.getAllMethods(this);
  }

  @Override
  @NotNull
  public PsiClass[] getAllInnerClasses() {
    return PsiClassImplUtil.getAllInnerClasses(this);
  }

  @Override
  public PsiField findFieldByName(String name, boolean checkBases) {
    return myInnersCache.findFieldByName(name, checkBases);
  }

  @Override
  public PsiMethod findMethodBySignature(PsiMethod patternMethod, boolean checkBases) {
    return PsiClassImplUtil.findMethodBySignature(this, patternMethod, checkBases);
  }

  @Override
  @NotNull
  public PsiMethod[] findMethodsBySignature(PsiMethod patternMethod, boolean checkBases) {
    return PsiClassImplUtil.findMethodsBySignature(this, patternMethod, checkBases);
  }

  @Override
  @NotNull
  public PsiMethod[] findMethodsByName(String name, boolean checkBases) {
    return myInnersCache.findMethodsByName(name, checkBases);
  }

  @Override
  @NotNull
  public List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(String name, boolean checkBases) {
    return PsiClassImplUtil.findMethodsAndTheirSubstitutorsByName(this, name, checkBases);
  }

  @Override
  @NotNull
  public List<Pair<PsiMethod, PsiSubstitutor>> getAllMethodsAndTheirSubstitutors() {
    return PsiClassImplUtil.getAllWithSubstitutorsByMap(this, PsiMethod.class);
  }

  @Override
  public PsiClass findInnerClassByName(String name, boolean checkBases) {
    return myInnersCache.findInnerClassByName(name, checkBases);
  }

  @Override
  public boolean isDeprecated() {
    return getStub().isDeprecated();
  }

  public String getSourceFileName() {
    final String sfn = getStub().getSourceFileName();
    return sfn != null ? sfn : obtainSourceFileNameFromClassFileName();
  }

  @NonNls
  private String obtainSourceFileNameFromClassFileName() {
    final String name = getContainingFile().getName();
    int i = name.indexOf('$');
    if (i < 0) {
      i = name.indexOf('.');
      if (i < 0) {
        i = name.length();
      }
    }
    return name.substring(0, i) + ".java";
  }

  @Override
  public PsiDocComment getDocComment() {
    return myDocComment;
  }

  @Override
  public PsiJavaToken getLBrace() {
    return null;
  }

  @Override
  public PsiJavaToken getRBrace() {
    return null;
  }

  @Override
  public boolean isInterface() {
    return getStub().isInterface();
  }

  @Override
  public boolean isAnnotationType() {
    return getStub().isAnnotationType();
  }

  @Override
  public boolean isEnum() {
    return getStub().isEnum();
  }

  @Override
  public void appendMirrorText(final int indentLevel, @NonNls final StringBuilder buffer) {
    ClsDocCommentImpl docComment = (ClsDocCommentImpl)getDocComment();
    if (docComment != null) {
      docComment.appendMirrorText(indentLevel, buffer);
      goNextLine(indentLevel, buffer);
    }
    ((ClsElementImpl)getModifierList()).appendMirrorText(indentLevel, buffer);
    buffer.append(isEnum() ? "enum " : isAnnotationType() ? "@interface " : isInterface() ? "interface " : "class ");
    ((ClsElementImpl)getNameIdentifier()).appendMirrorText(indentLevel, buffer);
    ((ClsElementImpl)getTypeParameterList()).appendMirrorText(indentLevel, buffer);
    buffer.append(' ');
    if (!isEnum() && !isAnnotationType()) {
      ((ClsElementImpl)getExtendsList()).appendMirrorText(indentLevel, buffer);
      buffer.append(' ');
    }
    if (!isInterface()) {
      ((ClsElementImpl)getImplementsList()).appendMirrorText(indentLevel, buffer);
    }
    buffer.append('{');
    final int newIndentLevel = indentLevel + getIndentSize();
    List<PsiField> fields = getOwnFields();
    if (fields.size() > 0) {
      goNextLine(newIndentLevel, buffer);
      for (int i = 0; i < fields.size(); i++) {
        PsiField field = fields.get(i);
        ((ClsElementImpl)field).appendMirrorText(newIndentLevel, buffer);
        if (field instanceof ClsEnumConstantImpl) {
          if (i < fields.size() - 1 && fields.get(i + 1) instanceof ClsEnumConstantImpl) {
            buffer.append(", ");
          }
          else {
            buffer.append(";");
            if (i < fields.size() - 1) {
              goNextLine(newIndentLevel, buffer);
            }
          }
        } else if (i < fields.size() - 1) {
          goNextLine(newIndentLevel, buffer);
        }
      }
    }

    List<PsiMethod> methods = getOwnMethods();
    if (methods.size() > 0) {
      goNextLine(newIndentLevel, buffer);
      goNextLine(newIndentLevel, buffer);
      for (int i = 0; i < methods.size(); i++) {
        PsiMethod method = methods.get(i);
        ((ClsElementImpl)method).appendMirrorText(newIndentLevel, buffer);
        if (i < methods.size() - 1) {
          goNextLine(newIndentLevel, buffer);
          goNextLine(newIndentLevel, buffer);
        }
      }
    }

    List<PsiClass> classes = getOwnInnerClasses();
    if (classes.size() > 0) {
      goNextLine(newIndentLevel, buffer);
      goNextLine(newIndentLevel, buffer);
      for (int i = 0; i < classes.size(); i++) {
        PsiClass aClass = classes.get(i);
        ((ClsElementImpl)aClass).appendMirrorText(newIndentLevel, buffer);
        if (i < classes.size() - 1) {
          goNextLine(newIndentLevel, buffer);
          goNextLine(newIndentLevel, buffer);
        }
      }
    }
    goNextLine(indentLevel, buffer);
    buffer.append('}');
  }

  @Override
  public void setMirror(@NotNull TreeElement element) {
    setMirrorCheckingType(element, null);

    PsiClass mirror = SourceTreeToPsiMap.treeToPsiNotNull(element);

    PsiDocComment docComment = getDocComment();
    if (docComment != null) {
      ((ClsElementImpl)docComment).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getDocComment()));
    }
    ((ClsElementImpl)getModifierList()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getModifierList()));
    ((ClsElementImpl)getNameIdentifier()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getNameIdentifier()));
    if (!isAnnotationType() && !isEnum()) {
      ((ClsElementImpl)getExtendsList()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getExtendsList()));
    }
    ((ClsElementImpl)getImplementsList()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getImplementsList()));
    ((ClsElementImpl)getTypeParameterList()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getTypeParameterList()));

    Ref<Boolean> extLog = Ref.create(true);

    List<PsiField> fields = getOwnFields();
    PsiField[] mirrorFields = mirror.getFields();
    if (fields.size() == mirrorFields.length) {
      for (int i = 0; i < fields.size(); i++) {
        ((ClsElementImpl)fields.get(i)).setMirror(SourceTreeToPsiMap.psiToTreeNotNull(mirrorFields[i]));
      }
    }
    else {
      log(this, mirror, "fields:" + fields.size() + "!=" + mirrorFields.length, extLog);
    }

    List<PsiMethod> methods = getOwnMethods();
    PsiMethod[] mirrorMethods = mirror.getMethods();
    if (methods.size() == mirrorMethods.length) {
      for (int i = 0; i < methods.size(); i++) {
        ((ClsElementImpl)methods.get(i)).setMirror(SourceTreeToPsiMap.psiToTreeNotNull(mirrorMethods[i]));
      }
    }
    else {
      log(this, mirror, "methods:" + methods.size() + "!=" + mirrorMethods.length, extLog);
    }

    List<PsiClass> classes = getOwnInnerClasses();
    PsiClass[] mirrorClasses = mirror.getInnerClasses();
    if (classes.size() == mirrorClasses.length) {
      for (int i = 0; i < classes.size(); i++) {
        ((ClsElementImpl)classes.get(i)).setMirror(SourceTreeToPsiMap.psiToTreeNotNull(mirrorClasses[i]));
      }
    }
    else {
      log(this, mirror, "classes:" + classes.size() + "!=" + mirrorClasses.length, extLog);
    }
  }

  private static void log(@NotNull ClsClassImpl stub, @NotNull PsiClass mirror, @NotNull String message, @NotNull Ref<Boolean> verbose) {
    LOG.error(message + "; file:" + stub.getContainingFile().getName());
    if (verbose.get()) {
      StringBuilder builder = new StringBuilder();
      stub.appendMirrorText(0, builder);
      LOG.error("\nStub:\n" + builder.toString() + "\nMirror:\n" + mirror.getText());
      verbose.set(false);
    }
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitClass(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @NonNls
  public String toString() {
    return "PsiClass:" + getName();
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    return PsiClassImplUtil.processDeclarationsInClass(this, processor, state, null, lastParent, place, false);
  }

  @Override
  public PsiElement getScope() {
    return getParent();
  }

  @Override
  public boolean isInheritorDeep(PsiClass baseClass, PsiClass classToByPass) {
    return InheritanceImplUtil.isInheritorDeep(this, baseClass, classToByPass);
  }

  @Override
  public boolean isInheritor(@NotNull PsiClass baseClass, boolean checkDeep) {
    return InheritanceImplUtil.isInheritor(this, baseClass, checkDeep);
  }

  @Nullable
  public PsiClass getSourceMirrorClass() {
    PsiClass delegate = getUserData(DELEGATE_KEY);
    if (delegate instanceof ClsClassImpl) {
      return ((ClsClassImpl)delegate).getSourceMirrorClass();
    }

    PsiElement parent = getParent();
    final String name = getName();
    if (parent instanceof PsiFile) {
      if (!(parent instanceof PsiClassOwner)) return null;

      PsiClassOwner fileNavigationElement = (PsiClassOwner)parent.getNavigationElement();
      for (PsiClass aClass : fileNavigationElement.getClasses()) {
        if (name.equals(aClass.getName())) return aClass;
      }
    }
    else if (parent != null) {
      ClsClassImpl parentClass = (ClsClassImpl)parent;
      PsiClass parentSourceMirror = parentClass.getSourceMirrorClass();
      if (parentSourceMirror == null) return null;
      PsiClass[] innerClasses = parentSourceMirror.getInnerClasses();
      for (PsiClass innerClass : innerClasses) {
        if (name.equals(innerClass.getName())) return innerClass;
      }
    }
    else {
      throw new PsiInvalidElementAccessException(this);
    }

    return null;
  }

  @Override
  @NotNull
  public PsiElement getNavigationElement() {
    for (ClsCustomNavigationPolicy customNavigationPolicy : Extensions.getExtensions(ClsCustomNavigationPolicy.EP_NAME)) {
      PsiElement navigationElement = customNavigationPolicy.getNavigationElement(this);
      if (navigationElement != null) {
        return navigationElement;
      }
    }

    PsiClass aClass = getSourceMirrorClass();
    return aClass != null && aClass != this ? aClass.getNavigationElement() : this;
  }

  @Override
  public ItemPresentation getPresentation() {
    PsiClass aClass = getSourceMirrorClass();
    return aClass != null && aClass != this ? aClass.getPresentation() : ItemPresentationProviders.getItemPresentation(this);
  }

  @Override
  public Icon getElementIcon(final int flags) {
    PsiClass aClass = getSourceMirrorClass();
    return aClass != null && aClass != this ? aClass.getIcon(flags) : PsiClassImplUtil.getClassIcon(flags, this);
  }

  @Override
  public boolean isEquivalentTo(final PsiElement another) {
    return PsiClassImplUtil.isClassEquivalentTo(this, another);
  }

  @Override
  @NotNull
  public SearchScope getUseScope() {
    return PsiClassImplUtil.getClassUseScope(this);
  }

  @Override
  public PsiQualifiedNamedElement getContainer() {
    final PsiFile file = getContainingFile();
    final PsiDirectory dir;
    return file == null ? null : (dir = file.getContainingDirectory()) == null
                                 ? null : JavaDirectoryService.getInstance().getPackage(dir);
  }

  @Override
  public void putInfo(@NotNull Map<String, String> info) {
    PsiClassImpl.putInfo(this, info);
  }

  @Override
  protected boolean isVisibilitySupported() {
    return true;
  }
}
