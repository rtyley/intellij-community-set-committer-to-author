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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author mike
 */
public class CreateClassFromNewFix extends CreateFromUsageBaseFix {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.CreateClassFromNewFix");
  private final SmartPsiElementPointer myNewExpression;

  public CreateClassFromNewFix(PsiNewExpression newExpression) {
    myNewExpression = SmartPointerManager.getInstance(newExpression.getProject()).createSmartPsiElementPointer(newExpression);
  }

  protected PsiNewExpression getNewExpression() {
    return (PsiNewExpression)myNewExpression.getElement();
  }

  protected void invokeImpl(PsiClass targetClass) {
    PsiNewExpression newExpression = getNewExpression();

    final PsiClass psiClass = CreateFromUsageUtils.createClass(getReferenceElement(newExpression),
                                                               CreateClassKind.CLASS,
                                                               null);
    setupClassFromNewExpression(psiClass, newExpression);
  }

  protected static void setupClassFromNewExpression(final PsiClass psiClass, final PsiNewExpression newExpression) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(newExpression.getProject()).getElementFactory();
    ApplicationManager.getApplication().runWriteAction(
      new Runnable() {
        public void run() {
          try {
            PsiClass aClass = psiClass;
            if (aClass == null) return;

            final PsiJavaCodeReferenceElement classReference = newExpression.getClassReference();
            if (classReference != null) {
              classReference.bindToElement(aClass);
            }
            setupInheritance(newExpression, aClass);
            setupGenericParameters(newExpression, aClass);

            PsiExpressionList argList = newExpression.getArgumentList();
            Project project = aClass.getProject();
            if (argList != null && argList.getExpressions().length > 0) {
              PsiMethod constructor = elementFactory.createConstructor();
              constructor = (PsiMethod) aClass.add(constructor);

              TemplateBuilderImpl templateBuilder = new TemplateBuilderImpl(aClass);
              CreateFromUsageUtils.setupMethodParameters(constructor, templateBuilder, argList, getTargetSubstitutor(newExpression));

              setupSuperCall(aClass, constructor, templateBuilder);

              getReferenceElement(newExpression).bindToElement(aClass);
              aClass = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(aClass);
              Template template = templateBuilder.buildTemplate();

              Editor editor = positionCursor(project, aClass.getContainingFile(), aClass);
              TextRange textRange = aClass.getTextRange();
              editor.getDocument().deleteString(textRange.getStartOffset(), textRange.getEndOffset());

              startTemplate(editor, template, project);
            }
            else {
              positionCursor(project, aClass.getContainingFile(), aClass);
            }
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      }
    );
  }

  public boolean startInWriteAction() {
    return false;
  }

  public static void setupSuperCall(PsiClass targetClass, PsiMethod constructor, TemplateBuilderImpl templateBuilder)
    throws IncorrectOperationException {
    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(targetClass.getProject()).getElementFactory();

    PsiClass superClass = targetClass.getSuperClass();
    if (superClass != null && !"java.lang.Object".equals(superClass.getQualifiedName()) &&
          !"java.lang.Enum".equals(superClass.getQualifiedName())) {
      PsiMethod[] constructors = superClass.getConstructors();
      boolean hasDefaultConstructor = false;

      for (PsiMethod superConstructor : constructors) {
        if (superConstructor.getParameterList().getParametersCount() == 0) {
          hasDefaultConstructor = true;
          break;
        }
      }

      if (!hasDefaultConstructor) {
        PsiExpressionStatement statement =
          (PsiExpressionStatement)elementFactory.createStatementFromText("super();", constructor);
        statement = (PsiExpressionStatement)constructor.getBody().add(statement);

        PsiMethodCallExpression call = (PsiMethodCallExpression)statement.getExpression();
        PsiExpressionList argumentList = call.getArgumentList();
        templateBuilder.setEndVariableAfter(argumentList.getFirstChild());
      }
    }

    templateBuilder.setEndVariableAfter(constructor.getBody().getLBrace());
  }

  private static void setupGenericParameters(PsiNewExpression expr, PsiClass targetClass) throws IncorrectOperationException {
    PsiJavaCodeReferenceElement ref = getReferenceElement(expr);
    int numParams = ref.getTypeParameters().length;
    if (numParams == 0) return;
    PsiElementFactory factory = JavaPsiFacade.getInstance(expr.getProject()).getElementFactory();
    targetClass.getTypeParameterList().add(factory.createTypeParameterFromText("T", null));
    for (int i = 2; i <= numParams; i++) {
      targetClass.getTypeParameterList().add(factory.createTypeParameterFromText("T" + (i-1), null));
    }
  }

  private static void setupInheritance(PsiNewExpression element, PsiClass targetClass) throws IncorrectOperationException {
    if (element.getParent() instanceof PsiReferenceExpression) return;

    ExpectedTypeInfo[] expectedTypes = ExpectedTypesProvider.getInstance(element.getProject()).getExpectedTypes(element, false);

    for (ExpectedTypeInfo expectedType : expectedTypes) {
      PsiType type = expectedType.getType();
      if (!(type instanceof PsiClassType)) continue;
      final PsiClassType classType = (PsiClassType)type;
      PsiClass aClass = classType.resolve();
      if (aClass == null) continue;
      if (aClass.equals(targetClass) || aClass.hasModifierProperty(PsiModifier.FINAL)) continue;
      PsiElementFactory factory = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory();

      if (aClass.isInterface()) {
        PsiReferenceList implementsList = targetClass.getImplementsList();
        implementsList.add(factory.createReferenceElementByType(classType));
      }
      else {
        PsiReferenceList extendsList = targetClass.getExtendsList();
        if (extendsList.getReferencedTypes().length == 0 && !"java.lang.Object".equals(classType.getCanonicalText())) {
          extendsList.add(factory.createReferenceElementByType(classType));
        }
      }
    }
  }


  private static PsiFile getTargetFile(PsiElement element) {
    PsiJavaCodeReferenceElement referenceElement = getReferenceElement((PsiNewExpression)element);

    PsiElement q = referenceElement.getQualifier();
    if (q instanceof PsiJavaCodeReferenceElement) {
      PsiJavaCodeReferenceElement qualifier = (PsiJavaCodeReferenceElement)q;
      PsiElement psiElement = qualifier.resolve();
      if (psiElement instanceof PsiClass) {
        PsiClass psiClass = (PsiClass)psiElement;
        return psiClass.getContainingFile();
      }
    }

    return null;
  }

  protected PsiElement getElement() {
    final PsiNewExpression expression = getNewExpression();
    if (expression == null || !expression.getManager().isInProject(expression)) return null;
    PsiJavaCodeReferenceElement referenceElement = getReferenceElement(expression);
    if (referenceElement == null) return null;
    if (referenceElement.getReferenceNameElement() instanceof PsiIdentifier) return expression;

    return null;
  }

  protected boolean isAllowOuterTargetClass() {
    return false;
  }

  protected boolean isValidElement(PsiElement element) {
    PsiJavaCodeReferenceElement ref = PsiTreeUtil.getChildOfType(element, PsiJavaCodeReferenceElement.class);
    return ref != null && ref.resolve() != null;
  }

  protected boolean isAvailableImpl(int offset) {
    PsiElement nameElement = getNameElement(getNewExpression());

    PsiFile targetFile = getTargetFile(getNewExpression());
    if (targetFile != null && !targetFile.getManager().isInProject(targetFile)) {
      return false;
    }

    if (CreateFromUsageUtils.shouldShowTag(offset, nameElement, getNewExpression())) {
      String varName = nameElement.getText();
      setText(getText(varName));
      return true;
    }

    return false;
  }

  protected String getText(final String varName) {
    return QuickFixBundle.message("create.class.from.new.text", varName);
  }

  private static PsiJavaCodeReferenceElement getReferenceElement(PsiNewExpression expression) {
    return expression.getClassOrAnonymousClassReference();
  }

  private static PsiElement getNameElement(PsiNewExpression targetElement) {
    PsiJavaCodeReferenceElement referenceElement = getReferenceElement(targetElement);
    if (referenceElement == null) return null;
    return referenceElement.getReferenceNameElement();
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("create.class.from.new.family");
  }
}
