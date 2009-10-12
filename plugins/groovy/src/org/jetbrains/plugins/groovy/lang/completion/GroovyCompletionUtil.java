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
import com.intellij.psi.*;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClassTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

/**
 * @author ilyas
 */
public abstract class GroovyCompletionUtil {

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

  public static boolean isIncomplete(PsiElement element) {
    while (element != null) {
      final PsiElement child = element.getLastChild();
      if (child instanceof PsiErrorElement) return true;
      element = child;
    }
    return false;
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

  /**
     * Checks next element after modifier candidate to be appropriate
     *
     * @param element
     * @return
     */
  public static boolean canBeModifier(PsiElement element) {
    PsiElement next = element.getNextSibling();
    while (next != null && (next instanceof PsiWhiteSpace ||
        next instanceof PsiComment)) {
      next = next.getNextSibling();
    }
    return (next == null || SEPARATORS.contains(next.getNode().getElementType()));
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

  public static boolean inMemberDeclaration(PsiElement context) {
    if (context.getParent() instanceof GrReferenceElement &&
        context.getParent().getParent() instanceof GrTypeElement) {
      PsiElement parent = context.getParent().getParent().getParent();
      if (parent instanceof GrMethod || parent instanceof GrVariableDeclaration) {
        return parent.getParent() instanceof GrTypeDefinitionBody || parent.getParent() instanceof GroovyFile;
      }
    }
    return false;
  }

  public static boolean asTypedMethod(PsiElement context) {
    return context.getParent() instanceof GrReferenceElement &&
        context.getParent().getParent() instanceof GrTypeElement &&
        context.getParent().getParent().getParent() instanceof GrMethod &&
        context.getParent().getParent().getParent().getParent() instanceof GrTypeDefinitionBody &&
        context.getTextRange().getStartOffset() == context.getParent().getParent().getParent().getParent().getTextRange().getStartOffset();

  }


  public static Object[] getCompletionVariants(GroovyResolveResult[] candidates) {
    Object[] result = new Object[candidates.length];
    for (int i = 0; i < result.length; i++) {
      final PsiElement element = candidates[i].getElement();
      final PsiElement context = candidates[i].getCurrentFileResolveContext();
      if (context instanceof GrImportStatement) {
        final String importedName = ((GrImportStatement) context).getImportedName();
        if (importedName != null && element != null) {
          result[i] = LookupElementBuilder.create(element, importedName).setPresentableText(importedName);
          continue;
        }
      }
      result[i] = element;
    }

    return result;
  }

  public static LookupElement setTailTypeForConstructor(PsiClass clazz, LookupElement lookupElement) {
    return LookupElementDecorator.withInsertHandler(lookupElement, ParenthesesInsertHandler.getInstance(hasConstructorParameters(clazz)));
  }

  public static boolean hasConstructorParameters(PsiClass clazz) {
    final PsiMethod[] constructors = clazz.getConstructors();
    for (PsiMethod constructor : constructors) {
      if (constructor.getParameterList().getParametersCount() > 0) return true;
    }

    return false;
  }


}
