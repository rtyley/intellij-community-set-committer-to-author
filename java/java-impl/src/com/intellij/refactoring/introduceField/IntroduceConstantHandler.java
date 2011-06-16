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
package com.intellij.refactoring.introduceField;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.classMembers.ClassMemberReferencesVisitor;
import com.intellij.refactoring.util.occurences.ExpressionOccurenceManager;
import com.intellij.refactoring.util.occurences.OccurenceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class IntroduceConstantHandler extends BaseExpressionToFieldHandler {
  public static final String REFACTORING_NAME = RefactoringBundle.message("introduce.constant.title");

  public IntroduceConstantHandler() {
    super(true);
  }

  protected String getHelpID() {
    return HelpID.INTRODUCE_CONSTANT;
  }

  public void invoke(Project project, PsiExpression[] expressions) {
    for (PsiExpression expression : expressions) {
      final PsiFile file = expression.getContainingFile();
      if (!CommonRefactoringUtil.checkReadOnlyStatus(project, file)) return;
    }
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    super.invoke(project, expressions, null);
  }

  public void invoke(@NotNull final Project project, final Editor editor, PsiFile file, DataContext dataContext) {
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, file)) return;

    PsiDocumentManager.getInstance(project).commitAllDocuments();
    ElementToWorkOn.processElementToWorkOn(editor, file, REFACTORING_NAME, getHelpID(), project, getElementProcessor(project, editor));
  }

  protected boolean invokeImpl(final Project project, final PsiLocalVariable localVariable, final Editor editor) {
    final PsiElement parent = localVariable.getParent();
    if (!(parent instanceof PsiDeclarationStatement)) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("error.wrong.caret.position.local.or.expression.name"));
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, getHelpID());
      return false;
    }
    final LocalToFieldHandler localToFieldHandler = new LocalToFieldHandler(project, true){
      @Override
      protected Settings showRefactoringDialog(PsiClass aClass,
                                               PsiLocalVariable local,
                                               PsiExpression[] occurences,
                                               boolean isStatic) {
        return IntroduceConstantHandler.this.showRefactoringDialog(project, editor, aClass, local.getInitializer(), local.getType(), occurences, local, null);
      }
    };
    return localToFieldHandler.convertLocalToField(localVariable, editor);
  }


  protected Settings showRefactoringDialog(Project project,
                                           Editor editor,
                                           PsiClass parentClass,
                                           PsiExpression expr,
                                           PsiType type,
                                           PsiExpression[] occurences,
                                           PsiElement anchorElement,
                                           PsiElement anchorElementIfAll) {
    final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(expr != null ? expr : anchorElement, PsiMethod.class);
    for (PsiExpression occurrence : occurences) {
      if (RefactoringUtil.isAssignmentLHS(occurrence)) {
        String message =
          RefactoringBundle.getCannotRefactorMessage("Selected expression is used for write");
        CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, getHelpID());
        highlightError(project, editor, occurrence);
        return null;
      }
    }
    PsiLocalVariable localVariable = null;
    if (expr instanceof PsiReferenceExpression) {
      PsiElement ref = ((PsiReferenceExpression)expr).resolve();
      if (ref instanceof PsiLocalVariable) {
        localVariable = (PsiLocalVariable)ref;
      }
    } else if (anchorElement instanceof PsiLocalVariable) {
      localVariable = (PsiLocalVariable)anchorElement;
    }

    if (localVariable == null) {
      final PsiElement errorElement = isStaticFinalInitializer(expr);
      if (errorElement != null) {
        String message =
          RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("selected.expression.cannot.be.a.constant.initializer"));
        CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, getHelpID());
        highlightError(project, editor, errorElement);
        return null;
      }
    }
    else {
      final PsiExpression initializer = localVariable.getInitializer();
      if (initializer == null) {
        String message = RefactoringBundle
          .getCannotRefactorMessage(RefactoringBundle.message("variable.does.not.have.an.initializer", localVariable.getName()));
        CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, getHelpID());
        return null;
      }
      final PsiElement errorElement = isStaticFinalInitializer(initializer);
      if (errorElement != null) {
        String message = RefactoringBundle.getCannotRefactorMessage(
          RefactoringBundle.message("initializer.for.variable.cannot.be.a.constant.initializer", localVariable.getName()));
        CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, getHelpID());
        highlightError(project, editor, errorElement);
        return null;
      }
    }

    final TypeSelectorManagerImpl typeSelectorManager = new TypeSelectorManagerImpl(project, type, containingMethod, expr, occurences);
    if (editor != null && editor.getSettings().isVariableInplaceRenameEnabled() && (expr == null || expr.isPhysical())) {
      if (new InplaceIntroduceConstantPopup(project, editor, parentClass, expr, localVariable, occurences, typeSelectorManager,
                                        anchorElement, anchorElementIfAll,
                                        expr != null ? createOccurenceManager(expr, parentClass) : null).startInplaceIntroduceTemplate() ){
        return null;
      }
    }


    final IntroduceConstantDialog dialog =
      new IntroduceConstantDialog(project, parentClass, expr, localVariable, localVariable != null, occurences, getParentClass(),
                                  typeSelectorManager);
    dialog.show();
    if (!dialog.isOK()) {
      if (occurences.length > 1) {
        WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
      }
      return null;
    }
    return new Settings(dialog.getEnteredName(), dialog.isReplaceAllOccurrences(), true, true,
                        BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION, dialog.getFieldVisibility(), localVariable,
                        dialog.getSelectedType(), dialog.isDeleteVariable(), dialog.getDestinationClass(), dialog.isAnnotateAsNonNls(),
                        dialog.introduceEnumConstant());
  }

  private static void highlightError(Project project, Editor editor, PsiElement errorElement) {
    if (editor != null) {
      final TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
      final TextRange textRange = errorElement.getTextRange();
      HighlightManager.getInstance(project).addRangeHighlight(editor, textRange.getStartOffset(), textRange.getEndOffset(), attributes, true, new ArrayList<RangeHighlighter>());
    }
  }

  protected String getRefactoringName() {
    return REFACTORING_NAME;
  }

  @Nullable
  private PsiElement isStaticFinalInitializer(PsiExpression expr) {
    PsiClass parentClass = expr != null ? getParentClass(expr) : null;
    if (parentClass == null) return null;
    IsStaticFinalInitializerExpression visitor = new IsStaticFinalInitializerExpression(parentClass, expr);
    expr.accept(visitor);
    return visitor.getElementReference();
  }

  protected OccurenceManager createOccurenceManager(final PsiExpression selectedExpr, final PsiClass parentClass) {
    return new ExpressionOccurenceManager(selectedExpr, parentClass, null);
  }

  private static class IsStaticFinalInitializerExpression extends ClassMemberReferencesVisitor {
    private PsiElement myElementReference = null;
    private final PsiExpression myInitializer;

    public IsStaticFinalInitializerExpression(PsiClass aClass, PsiExpression initializer) {
      super(aClass);
      myInitializer = initializer;
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      final PsiElement psiElement = expression.resolve();
      if ((psiElement instanceof PsiLocalVariable || psiElement instanceof PsiParameter) &&
          !PsiTreeUtil.isAncestor(myInitializer, psiElement, false)) {
        myElementReference = expression;
      }
      else {
        super.visitReferenceExpression(expression);
      }
    }


    protected void visitClassMemberReferenceElement(PsiMember classMember, PsiJavaCodeReferenceElement classMemberReference) {
      if (!classMember.hasModifierProperty(PsiModifier.STATIC)) {
        myElementReference = classMemberReference;
      }
    }

    @Override
    public void visitElement(PsiElement element) {
      if (myElementReference != null) return;
      super.visitElement(element);
    }

    @Nullable
    public PsiElement getElementReference() {
      return myElementReference;
    }
  }

  public PsiClass getParentClass(@NotNull PsiExpression initializerExpression) {
    final PsiType type = initializerExpression.getType();

    if (type != null && PsiUtil.isConstantExpression(initializerExpression)) {
      if (type instanceof PsiPrimitiveType ||
          PsiType.getJavaLangString(initializerExpression.getManager(), initializerExpression.getResolveScope()).equals(type)) {
        return super.getParentClass(initializerExpression);
      }
    }

    PsiElement parent = initializerExpression.getUserData(ElementToWorkOn.PARENT);
    if (parent == null) parent = initializerExpression;
    PsiClass aClass = PsiTreeUtil.getParentOfType(parent, PsiClass.class);
    while (aClass != null) {
      if (aClass.hasModifierProperty(PsiModifier.STATIC)) return aClass;
      if (aClass.getParent() instanceof PsiJavaFile) return aClass;
      aClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class);
    }
    return null;
  }

  protected boolean validClass(PsiClass parentClass, Editor editor) {
    return true;
  }

}
