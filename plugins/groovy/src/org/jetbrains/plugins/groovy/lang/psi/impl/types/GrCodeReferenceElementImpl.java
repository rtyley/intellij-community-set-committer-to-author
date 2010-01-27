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

package org.jetbrains.plugins.groovy.lang.psi.impl.types;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementFactory;
import com.intellij.codeInsight.lookup.MutableLookupElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.completion.GroovyCompletionUtil;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocReferenceElement;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrReferenceElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassResolverProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.processors.CompletionProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ResolverProcessor;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.jetbrains.plugins.groovy.lang.psi.impl.types.GrCodeReferenceElementImpl.ReferenceKind.*;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 26.03.2007
 */
public class GrCodeReferenceElementImpl extends GrReferenceElementImpl implements GrCodeReferenceElement {
  public GrCodeReferenceElementImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  protected PsiElement bindWithQualifiedRef(String qName) {
    final GrTypeArgumentList list = getTypeArgumentList();
    final String typeArgs = (list != null) ? list.getText() : "";
    final String text = qName + typeArgs;
    final GrCodeReferenceElement qualifiedRef = GroovyPsiElementFactory.getInstance(getProject()).createTypeOrPackageReference(text);
    getNode().getTreeParent().replaceChild(getNode(), qualifiedRef.getNode());
    PsiUtil.shortenReference(qualifiedRef);
    return qualifiedRef;
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitCodeReferenceElement(this);
  }

  public String toString() {
    return "Reference element";
  }

  public GrCodeReferenceElement getQualifier() {
    return (GrCodeReferenceElement) findChildByType(GroovyElementTypes.REFERENCE_ELEMENT);
  }

  public void setQualifier(@Nullable GrCodeReferenceElement newQualifier) {
    final GrCodeReferenceElement qualifier = getQualifier();
    if (newQualifier == null) {
      if (qualifier == null) return;
      getNode().removeRange(getNode().getFirstChildNode(), getReferenceNameElement().getNode());
    } else {
      if (qualifier == null) {
        final ASTNode refNameNode = getReferenceNameElement().getNode();
        getNode().addChild(newQualifier.getNode(), refNameNode);
        getNode().addLeaf(GroovyTokenTypes.mDOT, ".", refNameNode);
      } else {
        getNode().replaceChild(qualifier.getNode(), newQualifier.getNode());
      }
    }
  }

  enum ReferenceKind {
    CLASS,
    CLASS_OR_PACKAGE,
    PACKAGE_FQ,
    CLASS_FQ,
    CLASS_OR_PACKAGE_FQ,
    STATIC_MEMBER_FQ,
    CLASS_IN_QUALIFIED_NEW
  }

  @Nullable
  public PsiElement resolve() {
    ResolveResult[] results = getManager().getResolveCache().resolveWithCaching(this, RESOLVER, true, false);
    return results.length == 1 ? results[0].getElement() : null;
  }

  private ReferenceKind getKind(boolean forCompletion) {
    PsiElement parent = getParent();
    if (parent instanceof GrCodeReferenceElement) {
      ReferenceKind parentKind = ((GrCodeReferenceElementImpl) parent).getKind(forCompletion);
      if (parentKind == CLASS) return CLASS_OR_PACKAGE;
      else if (parentKind == STATIC_MEMBER_FQ) return CLASS_FQ;
      else if (parentKind == CLASS_FQ) return CLASS_OR_PACKAGE_FQ;
      return parentKind;
    } else if (parent instanceof GrPackageDefinition) {
      return PACKAGE_FQ;
    } else if (parent instanceof GrDocReferenceElement) {
      return CLASS_OR_PACKAGE;
    } else if (parent instanceof GrImportStatement) {
      final GrImportStatement importStatement = (GrImportStatement) parent;
      if (importStatement.isStatic()) {
        return importStatement.isOnDemand() ? CLASS_FQ : STATIC_MEMBER_FQ;
      }
      else {
        return forCompletion || importStatement.isOnDemand() ? CLASS_OR_PACKAGE_FQ : CLASS_FQ;
      }
    }
    else if (parent instanceof GrNewExpression || parent instanceof GrAnonymousClassDefinition) {
      if (parent instanceof GrAnonymousClassDefinition) {
        parent = parent.getParent();
      }
      assert parent instanceof GrNewExpression;
      final GrNewExpression newExpression = (GrNewExpression)parent;
      if (newExpression.getQualifier() != null) return CLASS_IN_QUALIFIED_NEW;
    }

    return CLASS;
  }

  @Nullable
  public String getCanonicalText() {
    PsiElement resolved = resolve();
    if (resolved instanceof PsiClass) {
      return ((PsiClass) resolved).getQualifiedName();
    }
    if (resolved instanceof PsiPackage) {
      return ((PsiPackage) resolved).getQualifiedName();
    }
    if (getKind(false) == STATIC_MEMBER_FQ) {
      final GrCodeReferenceElement qualifier = getQualifier();
      if (qualifier != null) {
        final String qualifierText = qualifier.getCanonicalText();
        if (qualifierText != null) {
          return qualifierText + "." + getReferenceName();
        }
      }
    }

    return null;
  }

  protected boolean bindsCorrectly(PsiElement element) {
    if (super.bindsCorrectly(element)) return true;
    if (element instanceof PsiClass) {
      final PsiElement resolved = resolve();
      if (resolved instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod) resolved;
        if (method.isConstructor() && getManager().areElementsEquivalent(element, method.getContainingClass())) {
          return true;
        }
      }
    }

    return false;
  }

  public boolean isReferenceTo(PsiElement element) {
    return getManager().areElementsEquivalent(element, resolve());
  }

  @NotNull
  public Object[] getVariants() {
    if (isClassReferenceForNew()) {
      return getVariantsForNewExpression();
    }

    return getVariantsImpl(getKind(true));
  }

  private boolean isClassReferenceForNew() {
    PsiElement parent = getParent();
    while (parent instanceof GrCodeReferenceElement) parent = parent.getParent();
    return parent instanceof GrNewExpression;
  }

  private Object[] getVariantsForNewExpression() {
    final Object[] classVariants = getVariantsImpl(CLASS_OR_PACKAGE);
    List<Object> result = new ArrayList<Object>();
    for (Object variant : classVariants) {
      if (variant instanceof PsiClass) {
        final PsiClass clazz = (PsiClass) variant;
        final MutableLookupElement<PsiClass> lookupElement = LookupElementFactory.getInstance().createLookupElement(clazz);
        result.add(GroovyCompletionUtil.setTailTypeForConstructor(clazz, lookupElement));
      } else if (variant instanceof LookupElement) {
        final LookupElement lookupElement = (LookupElement) variant;
        final Object obj = lookupElement.getObject();
        if (obj instanceof PsiClass) {
          result.add(GroovyCompletionUtil.setTailTypeForConstructor((PsiClass) obj, lookupElement));
        } else {
          result.add(lookupElement);
        }
      } else {
        result.add(variant);
      }
    }
    return result.toArray(new Object[result.size()]);
  }

  private Object[] getVariantsImpl(ReferenceKind kind) {
    switch (kind) {
      case STATIC_MEMBER_FQ: {
        final GrCodeReferenceElement qualifier = getQualifier();
        if (qualifier != null) {
          final PsiElement resolve = qualifier.resolve();
          if (resolve instanceof PsiClass) {
            final PsiClass clazz = (PsiClass) resolve;
            List<PsiElement> result = new ArrayList<PsiElement>();

            for (PsiField field : clazz.getFields()) {
              if (field.hasModifierProperty(PsiModifier.STATIC)) {
                result.add(field);
              }
            }

            for (PsiMethod method : clazz.getMethods()) {
              if (method.hasModifierProperty(PsiModifier.STATIC)) {
                result.add(method);
              }
            }

            return result.toArray(new PsiElement[result.size()]);
          }
        }
      }
      //fallthrough

      case PACKAGE_FQ:
      case CLASS_FQ:
      case CLASS_OR_PACKAGE_FQ: {
        final String refText = PsiUtil.getQualifiedReferenceText(this);
        final int lastDot = refText.lastIndexOf(".");
        String parentPackageFQName = lastDot > 0 ? refText.substring(0, lastDot) : "";
        final PsiPackage parentPackage = JavaPsiFacade.getInstance(getProject()).findPackage(parentPackageFQName);
        if (parentPackage != null) {
          final GlobalSearchScope scope = getResolveScope();
          if (kind == PACKAGE_FQ) {
            return parentPackage.getSubPackages(scope);
          } else {
            if (kind == CLASS_FQ) {
              return parentPackage.getClasses(scope);
            } else {
              final PsiPackage[] subpackages = parentPackage.getSubPackages(scope);
              final PsiClass[] classes = parentPackage.getClasses(scope);
              PsiElement[] result = new PsiElement[subpackages.length + classes.length];
              System.arraycopy(subpackages, 0, result, 0, subpackages.length);
              System.arraycopy(classes, 0, result, subpackages.length, classes.length);
              return result;
            }
          }
        }
      }

      case CLASS_OR_PACKAGE:
      case CLASS_IN_QUALIFIED_NEW:
      case CLASS: {
        GrCodeReferenceElement qualifier = getQualifier();
        if (qualifier != null) {
          PsiElement qualifierResolved = qualifier.resolve();
          if (qualifierResolved instanceof PsiPackage) {
            PsiPackage aPackage = (PsiPackage) qualifierResolved;
            PsiClass[] classes = aPackage.getClasses(getResolveScope());
            if (kind == CLASS) return classes;
            PsiPackage[] subpackages = aPackage.getSubPackages();
            return ArrayUtil.mergeArrays(classes, subpackages, Object.class);
          } else if (qualifierResolved instanceof PsiClass) {
            return ((PsiClass) qualifierResolved).getInnerClasses();
          }
        } else {
          ResolverProcessor processor = CompletionProcessor.createClassCompletionProcessor(this);
          ResolveUtil.treeWalkUp(this, processor, false);
          return GroovyCompletionUtil.getCompletionVariants(processor.getCandidates());
        }
      }
    }

    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  public boolean isSoft() {
    return false;
  }

  private static class OurResolver implements ResolveCache.PolyVariantResolver<GrCodeReferenceElementImpl> {

    public GroovyResolveResult[] resolve(GrCodeReferenceElementImpl reference, boolean incompleteCode) {
      if (reference.getReferenceName() == null) return GroovyResolveResult.EMPTY_ARRAY;
      final GroovyResolveResult[] results = _resolve(reference, reference.getManager(), reference.getKind(false));
      final PsiType[] args = reference.getTypeArguments();
      for (int i = 0; i < results.length; i++) {
        GroovyResolveResult result = results[i];
        final PsiElement element = result.getElement();
        if (element instanceof PsiClass) {
          final PsiSubstitutor substitutor = result.getSubstitutor();
          final PsiSubstitutor newSubstitutor = substitutor.putAll((PsiClass) element, args);
          results[i] = new GroovyResolveResultImpl(element, result.getCurrentFileResolveContext(), newSubstitutor, result.isAccessible(), result.isStaticsOK());
        }
      }
      return results;
    }

    private static GroovyResolveResult[] _resolve(GrCodeReferenceElementImpl ref, PsiManager manager,
                                           ReferenceKind kind) {
      final String refName = ref.getReferenceName();
      if (refName == null) {
        return GroovyResolveResult.EMPTY_ARRAY;
      }

      switch (kind) {
        case CLASS_OR_PACKAGE_FQ:
        case CLASS_FQ:
        case PACKAGE_FQ:
          String qName = PsiUtil.getQualifiedReferenceText(ref);

          JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
          if (kind == CLASS_OR_PACKAGE_FQ || kind == CLASS_FQ) {
            final PsiFile file = ref.getContainingFile();
            if (qName.indexOf('.') > 0 || file instanceof GroovyFile && ((GroovyFile)file).getPackageName().length() == 0) {
              PsiClass aClass = facade.findClass(qName, ref.getResolveScope());
              if (aClass != null) {
                boolean isAccessible = PsiUtil.isAccessible(ref, aClass);
                return new GroovyResolveResult[]{new GroovyResolveResultImpl(aClass, isAccessible)};
              }
            }
          }

          if (kind == CLASS_OR_PACKAGE_FQ || kind == PACKAGE_FQ) {
            PsiPackage aPackage = facade.findPackage(qName);
            if (aPackage != null) {
              return new GroovyResolveResult[]{new GroovyResolveResultImpl(aPackage, true)};
            }
          }

          break;

        case CLASS:
        case CLASS_OR_PACKAGE: {
          GrCodeReferenceElement qualifier = ref.getQualifier();
          if (qualifier != null) {
            PsiElement qualifierResolved = qualifier.resolve();
            if (qualifierResolved instanceof PsiPackage) {
              for (final PsiClass aClass : ((PsiPackage) qualifierResolved).getClasses(ref.getResolveScope())) {
                if (refName.equals(aClass.getName())) {
                  boolean isAccessible = PsiUtil.isAccessible(ref, aClass);
                  return new GroovyResolveResult[]{new GroovyResolveResultImpl(aClass, isAccessible)};
                }
              }

              if (kind == CLASS_OR_PACKAGE) {
                for (final PsiPackage subpackage : ((PsiPackage) qualifierResolved).getSubPackages()) {
                  if (refName.equals(subpackage.getName()))
                    return new GroovyResolveResult[]{new GroovyResolveResultImpl(subpackage, true)};
                }
              }
            } else if ((kind == CLASS || kind == CLASS_OR_PACKAGE) && qualifierResolved instanceof PsiClass) {
              PsiClass[] classes = ((PsiClass) qualifierResolved).getAllInnerClasses();
              for (final PsiClass aClass : classes) {
                if (refName.equals(aClass.getName())) {
                  boolean isAccessible = PsiUtil.isAccessible(ref, aClass);
                  return new GroovyResolveResult[]{new GroovyResolveResultImpl(aClass, isAccessible)};
                }
              }
            }
          } else {
            EnumSet<ClassHint.ResolveKind> kinds = kind == CLASS ? EnumSet.of(ClassHint.ResolveKind.CLASS) :
                EnumSet.of(ClassHint.ResolveKind.PACKAGE, ClassHint.ResolveKind.CLASS);
            ResolverProcessor processor = new ClassResolverProcessor(refName, ref, kinds);
            ResolveUtil.treeWalkUp(ref, processor, false);
            GroovyResolveResult[] candidates = processor.getCandidates();
            if (candidates.length > 0) return candidates;

            if (kind == CLASS_OR_PACKAGE) {
              PsiPackage defaultPackage = JavaPsiFacade.getInstance(ref.getProject()).findPackage("");
              if (defaultPackage != null) {
                for (final PsiPackage subpackage : defaultPackage.getSubPackages()) {
                  if (refName.equals(subpackage.getName()))
                    return new GroovyResolveResult[]{new GroovyResolveResultImpl(subpackage, true)};
                }
              }
            }
          }

          break;
        }

        case STATIC_MEMBER_FQ: {
          final GrCodeReferenceElement qualifier = ref.getQualifier();
          if (qualifier != null) {
            final PsiElement resolve = qualifier.resolve();
            if (resolve instanceof PsiClass) {
              final PsiClass clazz = (PsiClass) resolve;
              PsiResolveHelper helper = JavaPsiFacade.getInstance(clazz.getProject()).getResolveHelper();
              List<GroovyResolveResult> result = new ArrayList<GroovyResolveResult>();
              final PsiField field = clazz.findFieldByName(refName, false);
              if (field != null && field.hasModifierProperty(PsiModifier.STATIC)) {
                result.add(new GroovyResolveResultImpl(field, helper.isAccessible(field, ref, null)));
              }

              final PsiMethod[] methods = clazz.findMethodsByName(refName, false);
              for (PsiMethod method : methods) {
                result.add(new GroovyResolveResultImpl(method, helper.isAccessible(method, ref, null)));
              }

              return result.toArray(new GroovyResolveResult[result.size()]);
            }
          }
          break;
        }
        case CLASS_IN_QUALIFIED_NEW: {
          if (ref.getParent() instanceof GrCodeReferenceElement) return GroovyResolveResult.EMPTY_ARRAY;
          final GrNewExpression newExpression = PsiTreeUtil.getParentOfType(ref, GrNewExpression.class);
          assert newExpression != null;
          final GrExpression qualifier = newExpression.getQualifier();
          assert qualifier != null;

          final PsiType type = qualifier.getType();
          if (!(type instanceof PsiClassType)) break;

          final PsiClassType classType = (PsiClassType)type;
          final PsiClass psiClass = classType.resolve();
          if (psiClass == null) break;

          final PsiClass[] allInnerClasses = psiClass.getAllInnerClasses();
          ArrayList<GroovyResolveResult> result = new ArrayList<GroovyResolveResult>();
          PsiResolveHelper helper = JavaPsiFacade.getInstance(ref.getProject()).getResolveHelper();

          for (final PsiClass innerClass : allInnerClasses) {
            if (refName.equals(innerClass.getName())) {
              result.add(new GroovyResolveResultImpl(innerClass, helper.isAccessible(innerClass, ref, null)));
            }
          }
          return result.toArray(new GroovyResolveResult[result.size()]);
        }
      }

      return GroovyResolveResult.EMPTY_ARRAY;
    }
  }

  private static final OurResolver RESOLVER = new OurResolver();

  public GroovyResolveResult advancedResolve() {
    ResolveResult[] results = getManager().getResolveCache().resolveWithCaching(this, RESOLVER, true, false);
    return results.length == 1 ? (GroovyResolveResult) results[0] : GroovyResolveResult.EMPTY_RESULT;
  }

  @NotNull
  public GroovyResolveResult[] multiResolve(boolean incompleteCode) {
    final ResolveResult[] results = getManager().getResolveCache().resolveWithCaching(this, RESOLVER, true, incompleteCode);
    if (results.length == 0) {
      return GroovyResolveResult.EMPTY_ARRAY;
    }

    return (GroovyResolveResult[])results;
  }
}
