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

/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Aug 26, 2002
 * Time: 2:33:58 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightNamesUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateClassKind;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateConstructorMatchingSuperFix;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.generation.PsiMethodMember;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class CreateSubclassAction extends PsiElementBaseIntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.ImplementAbstractClassAction");
  private String myText = CodeInsightBundle.message("intention.implement.abstract.class.default.text");
  @NonNls private static final String IMPL_SUFFIX = "Impl";

  @NotNull
  public String getText() {
    return myText;
  }

  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.implement.abstract.class.family");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    if (psiClass == null || psiClass.isAnnotationType() || psiClass.isEnum() || psiClass instanceof PsiAnonymousClass ||
        psiClass.hasModifierProperty(PsiModifier.FINAL)) {
      return false;
    }
    final PsiMethod[] constructors = psiClass.getConstructors();
    if (constructors.length > 0) {
      boolean hasNonPrivateConstructor = false;
      for (PsiMethod constructor : constructors) {
        if (!constructor.hasModifierProperty(PsiModifier.PRIVATE)) {
          hasNonPrivateConstructor = true;
          break;
        }
      }
      if (!hasNonPrivateConstructor) return false;
    }
    PsiJavaToken lBrace = psiClass.getLBrace();
    if (lBrace == null) return false;
    if (element.getTextOffset() >= lBrace.getTextOffset()) return false;

    TextRange declarationRange = HighlightNamesUtil.getClassDeclarationTextRange(psiClass);
    if (!declarationRange.contains(element.getTextRange())) return false;

    myText = getTitle(psiClass);
    return true;
  }

  private static String getTitle(PsiClass psiClass) {
    return psiClass.isInterface()
             ? CodeInsightBundle.message("intention.implement.abstract.class.interface.text")
             : psiClass.hasModifierProperty(PsiModifier.ABSTRACT)
               ? CodeInsightBundle.message("intention.implement.abstract.class.default.text")
               : CodeInsightBundle.message("intention.implement.abstract.class.subclass.text");
  }

  public void invoke(@NotNull final Project project, Editor editor, final PsiFile file) throws IncorrectOperationException {
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    final PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);

    final CreateClassDialog dlg = chooseSubclassToCreate(psiClass);
    if (dlg != null) {
      createSubclass(psiClass, dlg.getTargetDirectory(), dlg.getClassName());
    }
  }

  @Nullable
  public static CreateClassDialog chooseSubclassToCreate(PsiClass psiClass) {
    final PsiDirectory sourceDir = psiClass.getContainingFile().getContainingDirectory();

    final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(sourceDir);
    final CreateClassDialog dialog = new CreateClassDialog(
      psiClass.getProject(), getTitle(psiClass),
      psiClass.getName() + IMPL_SUFFIX,
      aPackage != null ? aPackage.getQualifiedName() : "",
      CreateClassKind.CLASS, true, ModuleUtil.findModuleForPsiElement(psiClass)){
      @Override
      protected PsiDirectory getBaseDir(String packageName) {
        return sourceDir;
      }
    };
    dialog.show();
    if (!dialog.isOK()) return null;
    final PsiDirectory targetDirectory = dialog.getTargetDirectory();
    if (targetDirectory == null) return null;
    return dialog;
  }

  public static PsiClass createSubclass(final PsiClass psiClass, final PsiDirectory targetDirectory, final String className) {
    final Project project = psiClass.getProject();
    final PsiClass[] targetClass = new PsiClass[1];
    PostprocessReformattingAspect.getInstance(project).postponeFormattingInside(new Runnable () {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {

            IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();

            final PsiTypeParameterList oldTypeParameterList = psiClass.getTypeParameterList();

            try {
              targetClass[0] = JavaDirectoryService.getInstance().createClass(targetDirectory, className);
              if (psiClass.hasTypeParameters()) {
                final PsiTypeParameterList typeParameterList = targetClass[0].getTypeParameterList();
                assert typeParameterList != null;
                typeParameterList.replace(oldTypeParameterList);
              }
            }
            catch (final IncorrectOperationException e) {
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                public void run() {
                  Messages.showErrorDialog(project, CodeInsightBundle.message("intention.error.cannot.create.class.message", className) +
                                                    "\n"+e.getLocalizedMessage(),
                                           CodeInsightBundle.message("intention.error.cannot.create.class.title"));
                }
              });
              return;
            }
            final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
            PsiJavaCodeReferenceElement ref = elementFactory.createClassReferenceElement(psiClass);
            try {
              if (psiClass.isInterface()) {
                ref = (PsiJavaCodeReferenceElement)targetClass[0].getImplementsList().add(ref);
              }
              else {
                ref = (PsiJavaCodeReferenceElement)targetClass[0].getExtendsList().add(ref);
              }

              if (oldTypeParameterList != null) {
                for (PsiTypeParameter parameter : oldTypeParameterList.getTypeParameters()) {
                  ref.getParameterList().add(elementFactory.createTypeElement(elementFactory.createType(parameter)));
                }
              }
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        });
        if (targetClass[0] == null) return;
        if (!ApplicationManager.getApplication().isUnitTestMode()) {

          final Editor editor = CodeInsightUtil.positionCursor(project, targetClass[0].getContainingFile(), targetClass[0].getLBrace());
          if (editor == null) return;

          boolean hasNonTrivialConstructor = false;
          final PsiMethod[] constructors = psiClass.getConstructors();
          for (PsiMethod constructor : constructors) {
            if (constructor.getParameterList().getParametersCount() > 0) {
              hasNonTrivialConstructor = true;
              break;
            }
          }
          if (hasNonTrivialConstructor) {
            final PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(psiClass, targetClass[0], PsiSubstitutor.EMPTY);
            final List<PsiMethodMember> baseConstructors = new ArrayList<PsiMethodMember>();
            for (PsiMethod baseConstr : constructors) {
              if (PsiUtil.isAccessible(baseConstr, targetClass[0], targetClass[0])) {
                baseConstructors.add(new PsiMethodMember(baseConstr, substitutor));
              }
            }
            CreateConstructorMatchingSuperFix.chooseConstructor2Delegate(project, editor,
                                                                         substitutor,
                                                                         baseConstructors, constructors, targetClass[0]);
          }

          OverrideImplementUtil.chooseAndImplementMethods(project, editor, targetClass[0]);
        }
      }
    });
    return targetClass[0];
  }

  public boolean startInWriteAction() {
    return false;
  }

}
