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
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.generation.ClassMember;
import com.intellij.codeInsight.generation.MemberChooserObject;
import com.intellij.codeInsight.generation.PsiMethodMember;
import com.intellij.ide.util.MemberChooser;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ConcurrentWeakHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author Danila Ponomarenko
 */
public class CreateAssignFieldsFromParametersAction extends BaseIntentionAction {
  private static final Logger LOG = Logger.getInstance(CreateFieldFromParameterAction.class);
  private static final Key<Map<SmartPsiElementPointer<PsiParameter>, Boolean>> PARAMS = Key.create("FIELDS_FROM_PARAMS");

  private static final Object LOCK = new Object();

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    PsiParameter psiParameter = FieldFromParameterUtils.findParameterAtCursor(file, editor);
    PsiMethod method = findMethod(psiParameter, editor, file);
    if (method == null) return false;

    final List<PsiParameter> parameters = getAvailableParameters(method);

    synchronized (LOCK) {
      final Collection<SmartPsiElementPointer<PsiParameter>> params = getUnboundedParams(method);
      params.clear();
      for (PsiParameter parameter : parameters) {
        params.add(SmartPointerManager.getInstance(project).createSmartPsiElementPointer(parameter));
      }
      if (params.isEmpty()) return false;
      if (params.size() == 1 && psiParameter != null) return false;
      if (psiParameter == null) {
        psiParameter = params.iterator().next().getElement();
        LOG.assertTrue(psiParameter != null);
      }

      setText(CodeInsightBundle.message("intention.create.assign.fields.from.parameters.text", method.isConstructor() ? "Constructor" : "Method"));
    }
    return isAvailable(psiParameter);
  }

  @Nullable
  private static PsiMethod findMethod(@Nullable PsiParameter parameter, @NotNull Editor editor, @NotNull PsiFile file) {
    if (parameter == null) {
      final PsiElement elementAt = file.findElementAt(editor.getCaretModel().getOffset());
      if (elementAt instanceof PsiIdentifier) {
        final PsiElement parent = elementAt.getParent();
        if (parent instanceof PsiMethod) {
          return (PsiMethod)parent;
        }
      }
    }
    else {
      final PsiElement declarationScope = parameter.getDeclarationScope();
      if (declarationScope instanceof PsiMethod) {
        return (PsiMethod)declarationScope;
      }
    }

    return null;
  }

  @NotNull
  private static List<PsiParameter> getAvailableParameters(@NotNull PsiMethod method) {
    final List<PsiParameter> parameters = new ArrayList<PsiParameter>();
    for (PsiParameter parameter : method.getParameterList().getParameters()) {
      if (isAvailable(parameter)) {
        parameters.add(parameter);
      }
    }
    return parameters;
  }

  private static boolean isAvailable(PsiParameter psiParameter) {
    final PsiType type = FieldFromParameterUtils.getSubstitutedType(psiParameter);
    final PsiClass targetClass = PsiTreeUtil.getParentOfType(psiParameter, PsiClass.class);
    return FieldFromParameterUtils.isAvailable(psiParameter, type, targetClass) &&
           psiParameter.getLanguage().isKindOf(JavaLanguage.INSTANCE);
  }

  @NotNull
  private static Collection<SmartPsiElementPointer<PsiParameter>> getUnboundedParams(PsiMethod psiMethod) {
    Map<SmartPsiElementPointer<PsiParameter>, Boolean> params = psiMethod.getUserData(PARAMS);
    if (params == null) psiMethod.putUserData(PARAMS, params = new ConcurrentWeakHashMap<SmartPsiElementPointer<PsiParameter>, Boolean>(1));
    final Map<SmartPsiElementPointer<PsiParameter>, Boolean> finalParams = params;
    return new AbstractCollection<SmartPsiElementPointer<PsiParameter>>() {
      @Override
      public boolean add(SmartPsiElementPointer<PsiParameter> psiVariable) {
        return finalParams.put(psiVariable, Boolean.TRUE) == null;
      }

      @Override
      public Iterator<SmartPsiElementPointer<PsiParameter>> iterator() {
        return finalParams.keySet().iterator();
      }

      @Override
      public int size() {
        return finalParams.size();
      }

      @Override
      public void clear() {
        finalParams.clear();
      }
    };
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.create.assign.fields.from.parameters.family");
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    invoke(project, editor, file, !ApplicationManager.getApplication().isUnitTestMode());
  }

  private static void invoke(final Project project, Editor editor, PsiFile file, boolean isInteractive) {
    PsiParameter myParameter = FieldFromParameterUtils.findParameterAtCursor(file, editor);
    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return;
    final PsiMethod method = myParameter != null ? (PsiMethod)myParameter.getDeclarationScope() : PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PsiMethod.class);
    LOG.assertTrue(method != null);
    final Collection<SmartPsiElementPointer<PsiParameter>> unboundedParams;
    synchronized (LOCK) {
      unboundedParams = getUnboundedParams(method);
      if (unboundedParams.isEmpty()) return;
      if (myParameter == null) {
        myParameter = unboundedParams.iterator().next().getElement();
      }
    }
    if (unboundedParams.size() > 1 && isInteractive) {
      ClassMember[] members = new ClassMember[unboundedParams.size()];
      ClassMember selection = null;
      int i = 0;
      for (SmartPsiElementPointer<PsiParameter> pointer : unboundedParams) {
        final PsiParameter parameter = pointer.getElement();
        final ParameterClassMember classMember = new ParameterClassMember(parameter);
        members[i++] = classMember;
        if (parameter == myParameter) {
          selection = classMember;
        }
      }
      final PsiParameterList parameterList = method.getParameterList();
      Arrays.sort(members, new Comparator<ClassMember>() {
        @Override
        public int compare(ClassMember o1, ClassMember o2) {
          return parameterList.getParameterIndex(((ParameterClassMember)o1).getParameter()) -
                 parameterList.getParameterIndex(((ParameterClassMember)o2).getParameter());
        }
      });

      final MemberChooser<ClassMember> chooser = new MemberChooser<ClassMember>(members, false, true, project);
      if (selection != null) {
        chooser.selectElements(new ClassMember[]{selection});
      }
      chooser.setTitle("Choose " + (method.isConstructor() ? "Constructor" : "Method") + " Parameters");
      chooser.setCopyJavadocVisible(false);
      chooser.show();
      if (chooser.getExitCode() != DialogWrapper.OK_EXIT_CODE) return;
      final List<ClassMember> selectedElements = chooser.getSelectedElements();
      if (selectedElements == null) return;

      final HashSet<String> usedNames = new HashSet<String>();
      for (ClassMember selectedElement : selectedElements) {
        processParameter(project, ((ParameterClassMember)selectedElement).getParameter(), usedNames);
      }
    }
    else {
      processParameter(project, myParameter);
    }
    synchronized (LOCK) {
      unboundedParams.clear();
    }
  }

  private static void processParameter(final Project project,
                                       final PsiParameter myParameter) {
    processParameter(project, myParameter, new HashSet<String>());
  }

  private static void processParameter(final Project project,
                                       final PsiParameter myParameter,
                                       final Set<String> usedNames) {
    IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();
    final PsiType type = FieldFromParameterUtils.getSubstitutedType(myParameter);
    final JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(project);
    final String parameterName = myParameter.getName();
    String propertyName = styleManager.variableNameToPropertyName(parameterName, VariableKind.PARAMETER);

    final PsiClass targetClass = PsiTreeUtil.getParentOfType(myParameter, PsiClass.class);
    final PsiMethod method = (PsiMethod)myParameter.getDeclarationScope();

    final boolean isMethodStatic = method.hasModifierProperty(PsiModifier.STATIC);

    VariableKind kind = isMethodStatic ? VariableKind.STATIC_FIELD : VariableKind.FIELD;
    SuggestedNameInfo suggestedNameInfo = styleManager.suggestVariableName(kind, propertyName, null, type);
    String[] names = suggestedNameInfo.names;

    final boolean isFinal = !isMethodStatic && method.isConstructor();
    final String fieldName = usedNames.add(names[0]) ? names[0] : JavaCodeStyleManager.getInstance(project).suggestUniqueVariableName(names[0], myParameter, true);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        try {
          FieldFromParameterUtils.createFieldAndAddAssignment(
            project,
            targetClass,
            method,
            myParameter,
            type,
            fieldName,
            isMethodStatic,
            isFinal);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    });
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  private static class ParameterClassMember implements ClassMember {
    private PsiParameter myParameter;

    private ParameterClassMember(PsiParameter parameter) {
      myParameter = parameter;
    }

    @Override
    public MemberChooserObject getParentNodeDelegate() {
      return new PsiMethodMember((PsiMethod)myParameter.getDeclarationScope());
    }

    @Override
    public void renderTreeNode(SimpleColoredComponent component, JTree tree) {
      SpeedSearchUtil.appendFragmentsForSpeedSearch(tree, getText(), SimpleTextAttributes.REGULAR_ATTRIBUTES, false, component);
      component.setIcon(myParameter.getIcon(0));
    }

    @Override
    public String getText() {
      return myParameter.getName();
    }

    public PsiParameter getParameter() {
      return myParameter;
    }
  }
}
