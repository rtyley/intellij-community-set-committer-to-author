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
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateEditingListener;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.ide.util.PsiClassListCellRenderer;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.components.JBList;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author Mike
 */
public abstract class CreateFromUsageBaseFix extends BaseIntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageBaseFix");

  protected CreateFromUsageBaseFix() {
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = getElement();
    if (element == null) {
      return false;
    }

    List<PsiClass> targetClasses = getTargetClasses(element);
    return !targetClasses.isEmpty() && !isValidElement(element) && isAvailableImpl(offset);
  }

  protected abstract boolean isAvailableImpl(int offset);

  protected abstract void invokeImpl(PsiClass targetClass);

  protected abstract boolean isValidElement(PsiElement result);

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    PsiElement element = getElement();

    if (LOG.isDebugEnabled()) {
      LOG.debug("CreateFromUsage: element =" + element);
    }

    if (element == null) {
      return;
    }

    List<PsiClass> targetClasses = getTargetClasses(element);
    if (targetClasses.isEmpty()) return;

    if (targetClasses.size() == 1) {
      doInvoke(project, targetClasses.get(0));
    } else {
      chooseTargetClass(targetClasses, editor);
    }
  }

  private void doInvoke(Project project, final PsiClass targetClass) {
    if (!CodeInsightUtilBase.prepareFileForWrite(targetClass.getContainingFile())) {
      return;
    }

    IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        invokeImpl(targetClass);
      }
    });
  }

  @Nullable
  protected abstract PsiElement getElement();

  private void chooseTargetClass(List<PsiClass> classes, final Editor editor) {
    final Project project = classes.get(0).getProject();

    final JList list = new JBList(classes);
    PsiElementListCellRenderer renderer = new PsiClassListCellRenderer();
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setCellRenderer(renderer);
    final PopupChooserBuilder builder = new PopupChooserBuilder(list);
    renderer.installSpeedSearch(builder);

    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        int index = list.getSelectedIndex();
        if (index < 0) return;
        final PsiClass aClass = (PsiClass) list.getSelectedValue();
        CommandProcessor.getInstance().executeCommand(project, new Runnable() {
          @Override
          public void run() {
            doInvoke(project, aClass);
          }
        }, getText(), null);
      }
    };

    builder.
      setTitle(QuickFixBundle.message("target.class.chooser.title")).
      setItemChoosenCallback(runnable).
      createPopup().
      showInBestPositionFor(editor);
  }

  @Nullable("null means unable to open the editor")
  protected static Editor positionCursor(@NotNull Project project, @NotNull PsiFile targetFile, @NotNull PsiElement element) {
    TextRange range = element.getTextRange();
    int textOffset = range.getStartOffset();
    VirtualFile file = targetFile.getVirtualFile();
    if (file == null) {
      file = PsiUtilCore.getVirtualFile(element);
      if (file == null) return null;
    }
    OpenFileDescriptor descriptor = new OpenFileDescriptor(project, file, textOffset);
    return FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
  }

  protected void setupVisibility(PsiClass parentClass, PsiClass targetClass, PsiModifierList list) throws IncorrectOperationException {
    if (targetClass.isInterface()) {
      list.deleteChildRange(list.getFirstChild(), list.getLastChild());
      return;
    }
    VisibilityUtil.setVisibility(list, getVisibility(parentClass, targetClass));
  }

  @PsiModifier.ModifierConstant
  protected String getVisibility(PsiClass parentClass, PsiClass targetClass) {
    if (parentClass != null && (parentClass.equals(targetClass) || PsiTreeUtil.isAncestor(targetClass, parentClass, true))) {
      return PsiModifier.PRIVATE;
    } else {
      return PsiModifier.PUBLIC;
    }
  }

  protected static boolean shouldCreateStaticMember(PsiReferenceExpression ref, PsiClass targetClass) {
    
    PsiExpression qualifierExpression = ref.getQualifierExpression();
    while (qualifierExpression instanceof PsiParenthesizedExpression) {
      qualifierExpression = ((PsiParenthesizedExpression) qualifierExpression).getExpression();
    }

    if (qualifierExpression instanceof PsiReferenceExpression) {
      PsiReferenceExpression referenceExpression = (PsiReferenceExpression) qualifierExpression;

      PsiElement resolvedElement = referenceExpression.resolve();

      return resolvedElement instanceof PsiClass;
    } else if (qualifierExpression != null) {
      return false;
    } else {
      assert PsiTreeUtil.isAncestor(targetClass, ref, true);
      PsiModifierListOwner owner = PsiTreeUtil.getParentOfType(ref, PsiModifierListOwner.class);
      if (owner instanceof PsiMethod && ((PsiMethod)owner).isConstructor()) {
        //usages inside delegating constructor call
        PsiExpression run = ref;
        while (true) {
          if (!(run.getParent() instanceof PsiExpression)) break;
          run = (PsiExpression)run.getParent();
        }
        if (run.getParent() instanceof PsiExpressionList &&
          run.getParent().getParent() instanceof PsiMethodCallExpression) {
          @NonNls String calleeText = ((PsiMethodCallExpression)run.getParent().getParent()).getMethodExpression().getText();
          if (calleeText.equals("this") || calleeText.equals("super")) return true;
        }
      }

      while (owner != null && owner != targetClass) {
        if (owner.hasModifierProperty(PsiModifier.STATIC)) return true;
        owner = PsiTreeUtil.getParentOfType(owner, PsiModifierListOwner.class);
      }
    }

    return false;
  }

  @Nullable
  private static PsiExpression getQualifier (PsiElement element) {
    if (element instanceof PsiNewExpression) {
      PsiJavaCodeReferenceElement ref = ((PsiNewExpression) element).getClassReference();
      if (ref instanceof PsiReferenceExpression) {
        return ((PsiReferenceExpression) ref).getQualifierExpression();
      }
    } else if (element instanceof PsiReferenceExpression) {
      return ((PsiReferenceExpression) element).getQualifierExpression();
    } else if (element instanceof PsiMethodCallExpression) {
      return ((PsiMethodCallExpression) element).getMethodExpression().getQualifierExpression();
    }

    return null;
  }

  protected static PsiSubstitutor getTargetSubstitutor (PsiElement element) {
    if (element instanceof PsiNewExpression) {
      JavaResolveResult result = ((PsiNewExpression)element).getClassOrAnonymousClassReference().advancedResolve(false);
      PsiSubstitutor substitutor = result.getSubstitutor();
      return substitutor == null ? PsiSubstitutor.EMPTY : substitutor;
    }

    PsiExpression qualifier = getQualifier(element);
    if (qualifier != null) {
      PsiType type = qualifier.getType();
      if (type instanceof PsiClassType) {
        return ((PsiClassType)type).resolveGenerics().getSubstitutor();
      }
    }

    return PsiSubstitutor.EMPTY;
  }

  protected boolean isAllowOuterTargetClass() {
    return true;
  }

  //Should return only valid inproject classes
  @NotNull
  protected List<PsiClass> getTargetClasses(PsiElement element) {
    PsiClass psiClass = null;
    PsiExpression qualifier = null;

    if (element instanceof PsiNameValuePair) {
      final PsiAnnotation annotation = PsiTreeUtil.getParentOfType(element, PsiAnnotation.class);
      if (annotation != null) {
        PsiJavaCodeReferenceElement nameRef = annotation.getNameReferenceElement();
        if (nameRef == null) {
          return Collections.emptyList();
        }
        else {
          final PsiElement resolve = nameRef.resolve();
          if (resolve instanceof PsiClass) {
            return Collections.singletonList((PsiClass)resolve);
          }
          else {
            return Collections.emptyList();
          }
        }
      }
    }
    if (element instanceof PsiNewExpression) {
      final PsiNewExpression newExpression = (PsiNewExpression)element;
      PsiJavaCodeReferenceElement ref = newExpression.getClassOrAnonymousClassReference();
      if (ref != null) {
        PsiElement refElement = ref.resolve();
        if (refElement instanceof PsiClass) psiClass = (PsiClass)refElement;
      }
    }
    else if (element instanceof PsiReferenceExpression) {
      qualifier = ((PsiReferenceExpression)element).getQualifierExpression();
      if (qualifier == null) {
        final PsiElement parent = element.getParent();
        if (parent instanceof PsiSwitchLabelStatement) {
          final PsiSwitchStatement switchStatement = PsiTreeUtil.getParentOfType(parent, PsiSwitchStatement.class);
          if (switchStatement != null) {
            final PsiExpression expression = switchStatement.getExpression();
            if (expression != null) {
              psiClass = PsiUtil.resolveClassInClassTypeOnly(expression.getType());
            }
          }
        }
      }
    }
    else if (element instanceof PsiMethodCallExpression) {
      final PsiReferenceExpression methodExpression = ((PsiMethodCallExpression)element).getMethodExpression();
      qualifier = methodExpression.getQualifierExpression();
      @NonNls final String referenceName = methodExpression.getReferenceName();
      if (referenceName == null) return Collections.emptyList();
    }
    boolean allowOuterClasses = false;
    if (qualifier != null) {
      PsiType type = qualifier.getType();
      if (type instanceof PsiClassType) {
        psiClass = ((PsiClassType)type).resolve();
      }

      if (qualifier instanceof PsiJavaCodeReferenceElement) {
        final PsiElement resolved = ((PsiJavaCodeReferenceElement)qualifier).resolve();
        if (resolved instanceof PsiClass) {
          if (psiClass == null) psiClass = (PsiClass)resolved;
        }
      }
    } else if (psiClass == null) {
      psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
      allowOuterClasses = true;
    }

    if (psiClass instanceof PsiTypeParameter) {
      PsiClass[] supers = psiClass.getSupers();
      List<PsiClass> filtered = new ArrayList<PsiClass>();
      for (PsiClass aSuper : supers) {
        if (!aSuper.getManager().isInProject(aSuper)) continue;
        if (!(aSuper instanceof PsiTypeParameter)) filtered.add(aSuper);
      }
      return filtered;
    }
    else {
      if (psiClass == null || !psiClass.getManager().isInProject(psiClass)) {
        return Collections.emptyList();
      }

      if (ApplicationManager.getApplication().isUnitTestMode()) {
        return Collections.singletonList(psiClass);
      }

      if (!allowOuterClasses || !isAllowOuterTargetClass()) {
        final ArrayList<PsiClass> classes = new ArrayList<PsiClass>();
        collectSupers(psiClass, classes);
        return classes;
      }

      List<PsiClass> result = new ArrayList<PsiClass>();

      while (psiClass != null) {
        result.add(psiClass);
        if (psiClass.hasModifierProperty(PsiModifier.STATIC)) break;
        psiClass = PsiTreeUtil.getParentOfType(psiClass, PsiClass.class);
      }
      return result;
    }
  }

  private void collectSupers(PsiClass psiClass, ArrayList<PsiClass> classes) {
    classes.add(psiClass);

    final PsiClass[] supers = psiClass.getSupers();
    for (PsiClass aSuper : supers) {
      if (classes.contains(aSuper)) continue;
      if (canBeTargetClass(aSuper)) {
        collectSupers(aSuper, classes);
      }
    }
  }
  
  protected boolean canBeTargetClass(PsiClass psiClass) {
    return psiClass.getManager().isInProject(psiClass);
  }

  protected static void startTemplate (@NotNull Editor editor, final Template template, @NotNull final Project project) {
    startTemplate(editor, template, project, null);
  }

  protected static void startTemplate(@NotNull final Editor editor,
                                      final Template template,
                                      @NotNull final Project project,
                                      final TemplateEditingListener listener) {
    startTemplate(editor, template, project, listener, null);
  }

  public static void startTemplate(@NotNull final Editor editor,
                                      final Template template,
                                      @NotNull final Project project,
                                      final TemplateEditingListener listener,
                                      final String commandName) {
    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        if (project.isDisposed() || editor.isDisposed()) return;
        CommandProcessor.getInstance().executeCommand(project, new Runnable() {
          @Override
          public void run() {
            TemplateManager.getInstance(project).startTemplate(editor, template, listener);
          }
        }, commandName, commandName);
      }
    };
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      runnable.run();
    }
    else {
      ApplicationManager.getApplication().invokeLater(runnable);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  public static void setupGenericParameters(PsiClass targetClass, PsiJavaCodeReferenceElement ref) {
    int numParams = ref.getTypeParameters().length;
    if (numParams == 0) return;
    final PsiElementFactory factory = JavaPsiFacade.getInstance(ref.getProject()).getElementFactory();
    final Set<String> typeParamNames = new HashSet<String>();
    for (PsiType type : ref.getTypeParameters()) {
      final PsiClass psiClass = PsiUtil.resolveClassInType(type);
      if (psiClass instanceof PsiTypeParameter) {
        typeParamNames.add(psiClass.getName());
      }
    }
    int idx = 0;
    for (PsiType type : ref.getTypeParameters()) {
      final PsiClass psiClass = PsiUtil.resolveClassInType(type);
      if (psiClass instanceof PsiTypeParameter) {
        targetClass.getTypeParameterList().add(factory.createTypeParameterFromText(psiClass.getName(), null));
      } else {
        while (true) {
          final String paramName = idx > 0 ? "T" + idx : "T";
          if (!typeParamNames.contains(paramName)) {
            targetClass.getTypeParameterList().add(factory.createTypeParameterFromText(paramName, null));
            break;
          }
          idx++;
        }
      }
    }
  }
}
