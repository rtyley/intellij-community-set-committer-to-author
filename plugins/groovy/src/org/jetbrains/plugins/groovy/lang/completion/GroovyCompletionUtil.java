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

package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.completion.util.ParenthesesInsertHandler;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClassTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils.*;

/**
 * @author ilyas
 */
public class GroovyCompletionUtil {

  private GroovyCompletionUtil() {
  }

  /**
   * Return true if last element of curren statement is expression
   *
   * @param statement
   * @return
   */
  public static boolean endsWithExpression(PsiElement statement) {
    while (statement != null &&
           !(statement instanceof GrExpression)) {
      statement = statement.getLastChild();
    }
    return statement != null;
  }

  @Nullable
  public static PsiElement nearestLeftSibling(PsiElement elem) {
    elem = elem.getPrevSibling();
    while (elem != null &&
           (elem instanceof PsiWhiteSpace ||
            elem instanceof PsiComment ||
            GroovyTokenTypes.mNLS.equals(elem.getNode().getElementType()))) {
      elem = elem.getPrevSibling();
    }
    return elem;
  }

  /**
   * Shows wether keyword may be placed asas a new statement beginning
   *
   * @param element
   * @param canBeAfterBrace May be after '{' symbol or not
   * @return
   */
  public static boolean isNewStatement(PsiElement element, boolean canBeAfterBrace) {
    PsiElement previousLeaf = getLeafByOffset(element.getTextRange().getStartOffset() - 1, element);
    previousLeaf = PsiImplUtil.realPrevious(previousLeaf);
    if (previousLeaf != null && canBeAfterBrace && GroovyElementTypes.mLCURLY.equals(previousLeaf.getNode().getElementType())) {
      return true;
    }
    return (previousLeaf == null || SEPARATORS.contains(previousLeaf.getNode().getElementType()));
  }

  @Nullable
  public static PsiElement getLeafByOffset(int offset, PsiElement element) {
    if (offset < 0) {
      return null;
    }
    PsiElement candidate = element.getContainingFile();
    while (candidate.getNode().getChildren(null).length > 0) {
      candidate = candidate.findElementAt(offset);
    }
    return candidate;
  }

  /**
   * return true, if the element is first element after modifiers and there is no type element
   */
  public static boolean isFirstElementAfterModifiersInVariableDeclaration(PsiElement element, boolean acceptParameter) {
    final PsiElement parent = element.getParent();
    if (!(parent instanceof GrVariable)) return false;

    if (acceptParameter && parent instanceof GrParameter) {
      return ((GrParameter)parent).getTypeElementGroovy() == null;
    }

    final PsiElement parent1 = parent.getParent();
    if (!(parent1 instanceof GrVariableDeclaration)) return false;

    final GrVariableDeclaration variableDeclaration = (GrVariableDeclaration)parent1;
    if (variableDeclaration.getTypeElementGroovy() != null) return false;

    return variableDeclaration.getVariables()[0] == parent;
  }

  private static final TokenSet SEPARATORS = TokenSet.create(GroovyElementTypes.mNLS,
                                                             GroovyElementTypes.mSEMI);

  public static boolean asSimpleVariable(PsiElement context) {
    return isInTypeDefinitionBody(context) &&
           isNewStatement(context, true);
  }

  public static boolean isInTypeDefinitionBody(PsiElement context) {
    return (context.getParent() instanceof GrCodeReferenceElement &&
            context.getParent().getParent() instanceof GrClassTypeElement &&
            context.getParent().getParent().getParent() instanceof GrTypeDefinitionBody) ||
           context.getParent() instanceof GrTypeDefinitionBody;
  }

  public static boolean asVariableInBlock(PsiElement context) {
    if (context.getParent() instanceof GrReferenceExpression &&
        (context.getParent().getParent() instanceof GrOpenBlock ||
         context.getParent().getParent() instanceof GrClosableBlock) &&
        isNewStatement(context, true)) {
      return true;
    }

    if (context.getParent() instanceof GrReferenceExpression &&
        context.getParent().getParent() instanceof GrApplicationStatement &&
        (context.getParent().getParent().getParent() instanceof GrOpenBlock ||
         context.getParent().getParent().getParent() instanceof GrClosableBlock) &&
        isNewStatement(context, true)) {
      return true;
    }

    return context.getParent() instanceof GrTypeDefinitionBody &&
           isNewStatement(context, true);
  }

  public static boolean asTypedMethod(PsiElement context) {
    return context.getParent() instanceof GrReferenceElement &&
           context.getParent().getParent() instanceof GrTypeElement &&
           context.getParent().getParent().getParent() instanceof GrMethod &&
           context.getParent().getParent().getParent().getParent() instanceof GrTypeDefinitionBody &&
           context.getTextRange().getStartOffset() ==
           context.getParent().getParent().getParent().getParent().getTextRange().getStartOffset();

  }


  public static Object[] getCompletionVariants(GroovyResolveResult[] candidates) {
    Object[] result = new Object[candidates.length];
    Outer:
    for (int i = 0; i < result.length; i++) {
      final PsiElement element = candidates[i].getElement();
      final PsiElement context = candidates[i].getCurrentFileResolveContext();
      if (context instanceof GrImportStatement && element != null) {
        final String importedName = ((GrImportStatement)context).getImportedName();
        if (importedName != null) {
          final GrCodeReferenceElement importReference = ((GrImportStatement)context).getImportReference();
          if (importReference != null) {
            final GroovyResolveResult[] results = importReference.multiResolve(false);
            for (GroovyResolveResult r : results) {
              final PsiElement resolved = r.getElement();
              if (PsiManager.getInstance(context.getProject()).areElementsEquivalent(resolved, element)) {
                result[i] = generateLookupForImportedElement(candidates[i], importedName);
                continue Outer;
              }
              else {
                if (resolved instanceof PsiField && element instanceof PsiMethod && isAccessorFor((PsiMethod)element, (PsiField)resolved)) {
                  result[i] =
                    generateLookupForImportedElement(candidates[i], getAccessorPrefix((PsiMethod)element) + capitalize(importedName));
                  continue Outer;
                }
              }

            }
          }
        }
      }
      else if (context instanceof GrMethodCallExpression && element instanceof PsiMethod) {
        final PsiMethod method = generateMethodInCategory(candidates[i]);
        result[i] = setupLookupBuilder(method, candidates[i].getSubstitutor(), LookupElementBuilder.create((PsiNamedElement)element));
        continue;
      }
      if (element instanceof PsiNamedElement) {
        result[i] = setupLookupBuilder(element, candidates[i].getSubstitutor(), LookupElementBuilder.create((PsiNamedElement)element));
        continue;
      }
      result[i] = element;
    }

    return result;
  }

  private static LookupElementBuilder generateLookupForImportedElement(GroovyResolveResult resolveResult, String importedName) {
    final PsiElement element = resolveResult.getElement();
    assert element != null;
    final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    LookupElementBuilder builder = LookupElementBuilder.create(element, importedName).setPresentableText(importedName);
    return setupLookupBuilder(element, substitutor, builder);
  }

  private static PsiMethod generateMethodInCategory(GroovyResolveResult result) {
    final PsiElement element = result.getElement();
    assert element instanceof PsiMethod;
    final LightMethodBuilder builder = new LightMethodBuilder(element.getManager(), ((PsiMethod)element).getName());
    final PsiParameter[] params = ((PsiMethod)element).getParameterList().getParameters();
    for (int i = 1; i < params.length; i++) {
      builder.addParameter(params[i]);
    }
    builder.setBaseIcon(GroovyIcons.METHOD);
    return builder;
  }

  public static LookupElement getLookupElement(Object o) {
    if (o instanceof LookupElement) return (LookupElement)o;
    if (o instanceof PsiNamedElement) return generateLookupElement((PsiNamedElement)o);
    if (o instanceof PsiElement) return setupLookupBuilder((PsiElement)o, PsiSubstitutor.EMPTY, LookupElementBuilder.create(o, ((PsiElement)o).getText()));
    return LookupElementBuilder.create(o, o.toString());
  }
  public static LookupElementBuilder generateLookupElement(PsiNamedElement element) {
    LookupElementBuilder builder = LookupElementBuilder.create(element);
    return setupLookupBuilder(element, PsiSubstitutor.EMPTY, builder);
  }

  public static LookupElementBuilder setupLookupBuilder(PsiElement element, PsiSubstitutor substitutor, LookupElementBuilder builder) {
    builder = builder.setIcon(element.getIcon(Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS))
      .setInsertHandler(new GroovyInsertHandler());
    builder = setTailText(element, builder, substitutor);
    builder = setTypeText(element, builder, substitutor);
    return builder;
  }

  private static LookupElementBuilder setTailText(PsiElement element, LookupElementBuilder builder, PsiSubstitutor substitutor) {
    if (element instanceof PsiMethod) {
      builder = builder.setTailText(PsiFormatUtil.formatMethod((PsiMethod)element, substitutor, PsiFormatUtil.SHOW_PARAMETERS,
                                                               PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE));
    }
    else if (element instanceof PsiClass) {
      String tailText = getPackageText((PsiClass)element);
      final PsiClass psiClass = (PsiClass)element;
      if ((substitutor == null || substitutor.getSubstitutionMap().size() == 0) && psiClass.getTypeParameters().length > 0) {
        tailText = "<" + StringUtil.join(psiClass.getTypeParameters(), new Function<PsiTypeParameter, String>() {
          public String fun(PsiTypeParameter psiTypeParameter) {
            return psiTypeParameter.getName();
          }
        }, "," + (showSpaceAfterComma(psiClass) ? " " : "")) + ">" + tailText;
      }
      builder = builder.setTailText(tailText, true);
    }
    return builder;
  }

  private static String getPackageText(PsiClass psiClass) {
    @NonNls String packageName = PsiFormatUtil.getPackageDisplayName(psiClass);
    return " (" + packageName + ")";
  }


  private static boolean showSpaceAfterComma(PsiClass element) {
    return CodeStyleSettingsManager.getSettings(element.getProject()).SPACE_AFTER_COMMA;
  }


  private static LookupElementBuilder setTypeText(PsiElement element, LookupElementBuilder builder, PsiSubstitutor substitutor) {
    if (element instanceof PsiVariable) {
      builder = builder.setTypeText(substitutor.substitute(((PsiVariable)element).getType()).getPresentableText());
    }
    else if (element instanceof PsiMethod) {
      final PsiType type = substitutor.substitute(((PsiMethod)element).getReturnType());
      if (type != null) {
        builder = builder.setTypeText(type.getPresentableText());
      }
    }
    return builder;
  }

  public static boolean hasConstructorParameters(PsiClass clazz) {
    final PsiMethod[] constructors;
    if (clazz instanceof GroovyPsiElement) {
      constructors = ResolveUtil.getAllClassConstructors(clazz, (GroovyPsiElement)clazz, PsiSubstitutor.EMPTY);
    }
    else {
      constructors = clazz.getConstructors();
    }

    for (PsiMethod constructor : constructors) {
      if (constructor.getParameterList().getParametersCount() > 0) return true;
    }

    return false;
  }

  public static LookupElement setTailTypeForConstructor(PsiClass clazz, LookupElement lookupElement) {
    return LookupElementDecorator.withInsertHandler(lookupElement, ParenthesesInsertHandler.getInstance(hasConstructorParameters(clazz)));
  }
}
