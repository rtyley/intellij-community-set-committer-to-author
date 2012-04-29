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
package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.changeSignature.JavaChangeSignatureDialog;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PairFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParametersOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesProvider;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.refactoring.changeSignature.GrChangeSignatureDialog;
import org.jetbrains.plugins.groovy.refactoring.changeSignature.GrParameterTableModel;
import org.jetbrains.plugins.groovy.refactoring.changeSignature.GrTableParameterInfo;
import org.jetbrains.plugins.groovy.refactoring.convertToJava.GroovyToJavaGenerator;
import org.jetbrains.plugins.groovy.refactoring.ui.MethodOrClosureScopeChooser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * @author Max Medvedev
 */
public class CreateParameterFromUsageFix implements IntentionAction, MethodOrClosureScopeChooser.JBPopupOwner {
  private final String myName;
  private JBPopup myEnclosingMethodsPopup = null;

  public CreateParameterFromUsageFix(GrReferenceExpression ref) {
    myName = ref.getReferenceName();
  }

  @NotNull
  @Override
  public String getText() {
    return GroovyBundle.message("create.parameter.from.usage", myName);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return GroovyBundle.message("create.from.usage.family.name");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    GrReferenceExpression ref = findRef(editor, file);
    return ref != null && PsiTreeUtil.getParentOfType(ref, GrMethod.class) != null;
  }

  @Override
  public JBPopup get() {
    return myEnclosingMethodsPopup;
  }

  @Nullable
  private static GrReferenceExpression findRef(Editor editor, PsiFile file) {
    PsiElement at = file.findElementAt(editor.getCaretModel().getOffset());
    if (at == null) return null;

    GrReferenceExpression ref = PsiTreeUtil.getParentOfType(at, GrReferenceExpression.class, false, GrCodeBlock.class);
    if (ref == null) return null;
    if (ref.getQualifier() != null) return null;
    return ref;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    GrReferenceExpression ref = findRef(editor, file);
    if (ref == null) return;
    findScope(ref, editor, project);
  }

  private void findScope(@NotNull final GrReferenceExpression ref, @NotNull final Editor editor, final Project project) {
    PsiElement place = ref;
    final List<GrMethod> scopes = new ArrayList<GrMethod>();
    while (true) {
      final GrMethod parent = PsiTreeUtil.getParentOfType(place, GrMethod.class);
      if (parent == null) break;
      scopes.add(parent);
      place = parent;
    }

    if (scopes.size() == 1) {
      final GrMethod owner = scopes.get(0);
      final PsiMethod toSearchFor;
      toSearchFor = SuperMethodWarningUtil.checkSuperMethod(owner, RefactoringBundle.message("to.refactor"));
      if (toSearchFor == null) return; //if it is null, refactoring was canceled
      showDialog(toSearchFor, ref, project);
    }
    else if (scopes.size() > 1) {
      myEnclosingMethodsPopup = MethodOrClosureScopeChooser.create(scopes, editor, this, new PairFunction<GrParametersOwner, PsiElement, Object>() {
        @Override
        public Object fun(GrParametersOwner owner, PsiElement element) {
          showDialog((PsiMethod)owner, ref, project);
          return null;
        }
      });
      myEnclosingMethodsPopup.showInBestPositionFor(editor);
    }
  }

  private static void showDialog(final PsiMethod method, final GrReferenceExpression ref, final Project project) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        if (project.isDisposed()) return;

        final String name = ref.getName();
        final Set<PsiType> types = GroovyExpectedTypesProvider.getDefaultExpectedTypes(ref);
        PsiType _type = types.iterator().next();
        final PsiType type = TypesUtil.unboxPrimitiveTypeWrapper(_type);

        if (method instanceof GrMethod) {
          new GrChangeSignatureDialog(project, (GrMethod)method) {
            @Override
            protected GrParameterTableModel createParameterTableModel() {
              GrParameterTableModel model = super.createParameterTableModel();

              model.addRow(new GrTableParameterInfo(project, ref, name, type.getPresentableText(),
                                                    GroovyToJavaGenerator.getDefaultValueText(type.getCanonicalText()), ""));
              if (method.isVarArgs()) {
                model.exchangeRows(model.getRowCount() - 1, model.getRowCount() - 2);
              }
              return model;
            }
          }.show();
        }
        else if (method != null) {
          JavaChangeSignatureDialog dialog = new JavaChangeSignatureDialog(project, method, false, ref);
          final List<ParameterInfoImpl> parameterInfos = new ArrayList<ParameterInfoImpl>(Arrays.asList(ParameterInfoImpl.fromMethod(method)));
          ParameterInfoImpl parameterInfo = new ParameterInfoImpl(-1, name, type, PsiTypesUtil.getDefaultValueOfType(type), false);
          if (!method.isVarArgs()) {
            parameterInfos.add(parameterInfo);
          }
          else {
            parameterInfos.add(parameterInfos.size() - 1, parameterInfo);
          }
          dialog.setParameterInfos(parameterInfos);
          dialog.show();
        }
      }
    });
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
