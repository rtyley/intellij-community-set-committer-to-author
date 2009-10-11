
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
package com.intellij.refactoring.encapsulateFields;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EncapsulateFieldsProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.encapsulateFields.EncapsulateFieldsProcessor");

  private PsiClass myClass;
  private final EncapsulateFieldsDialog myDialog;
  private PsiField[] myFields;

  private HashMap<String,PsiMethod> myNameToGetter;
  private HashMap<String,PsiMethod> myNameToSetter;

  public EncapsulateFieldsProcessor(Project project, EncapsulateFieldsDialog dialog) {
    super(project);
    myDialog = dialog;
  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    PsiField[] fields = new PsiField[myFields.length];
    System.arraycopy(myFields, 0, fields, 0, myFields.length);
    return new EncapsulateFieldsViewDescriptor(fields);
  }

  protected String getCommandName() {
    return RefactoringBundle.message("encapsulate.fields.command.name", UsageViewUtil.getDescriptiveName(myClass));
  }

  public void doRun() {
    myFields = myDialog.getSelectedFields();
    if (myFields.length == 0){
      String message = RefactoringBundle.message("encapsulate.fields.no.fields.selected");
      CommonRefactoringUtil.showErrorMessage(EncapsulateFieldsHandler.REFACTORING_NAME, message, HelpID.ENCAPSULATE_FIELDS, myProject);
      return;
    }
    myClass = myFields[0].getContainingClass();

    super.doRun();
  }

  protected boolean preprocessUsages(Ref<UsageInfo[]> refUsages) {
    MultiMap<PsiElement, String> conflicts = new MultiMap<PsiElement, String>();

    if (myDialog != null) {
      checkExistingMethods(myDialog.getGetterPrototypes(), conflicts, true);
      checkExistingMethods(myDialog.getSetterPrototypes(), conflicts, false);

      if(conflicts.size() > 0) {
        ConflictsDialog dialog = new ConflictsDialog(myProject, conflicts);
        dialog.show();
        if(!dialog.isOK()){
          if (dialog.isShowConflicts()) prepareSuccessful();
          return false;
        }
      }
    }

    prepareSuccessful();
    return true;
  }

  private void checkExistingMethods(PsiMethod[] prototypes, MultiMap<PsiElement, String> conflicts, boolean isGetter) {
    if(prototypes == null) return;
    for (PsiMethod prototype : prototypes) {
      final PsiType prototypeReturnType = prototype.getReturnType();
      PsiMethod existing = myClass.findMethodBySignature(prototype, true);
      if (existing != null) {
        final PsiType returnType = existing.getReturnType();
        if (!RefactoringUtil.equivalentTypes(prototypeReturnType, returnType, myClass.getManager())) {
          final String descr = PsiFormatUtil.formatMethod(existing,
                                                          PsiSubstitutor.EMPTY,
                                                          PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS | PsiFormatUtil.SHOW_TYPE,
                                                          PsiFormatUtil.SHOW_TYPE
          );
          String message = isGetter ?
                           RefactoringBundle.message("encapsulate.fields.getter.exists", CommonRefactoringUtil.htmlEmphasize(descr),
                                                CommonRefactoringUtil.htmlEmphasize(prototype.getName())) :
                           RefactoringBundle.message("encapsulate.fields.setter.exists", CommonRefactoringUtil.htmlEmphasize(descr),
                                                CommonRefactoringUtil.htmlEmphasize(prototype.getName()));
          conflicts.putValue(existing, message);
        }
      }
    }
  }

  @NotNull protected UsageInfo[] findUsages() {
    boolean findGet = myDialog.isToEncapsulateGet();
    boolean findSet = myDialog.isToEncapsulateSet();
    PsiModifierList newModifierList = null;
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(myProject);
    if (!myDialog.isToUseAccessorsWhenAccessible()){
      PsiElementFactory factory = facade.getElementFactory();
      try{
        PsiField field = factory.createField("a", PsiType.INT);
        setNewFieldVisibility(field);
        newModifierList = field.getModifierList();
      }
      catch(IncorrectOperationException e){
        LOG.error(e);
      }
    }
    PsiMethod[] getterPrototypes = myDialog.getGetterPrototypes();
    PsiMethod[] setterPrototypes = myDialog.getSetterPrototypes();
    ArrayList<UsageInfo> array = new ArrayList<UsageInfo>();
    PsiField[] fields = myFields;
    for(int i = 0; i < fields.length; i++){
      PsiField field = fields[i];
      for (final PsiReference reference : ReferencesSearch.search(field, GlobalSearchScope.projectScope(myProject), true)) {
        if (!(reference instanceof PsiReferenceExpression)) continue;
        PsiReferenceExpression ref = (PsiReferenceExpression)reference;
        // [Jeka] to avoid recursion in the field's accessors
        if (findGet && isUsedInExistingAccessor(getterPrototypes[i], ref)) continue;
        if (findSet && isUsedInExistingAccessor(setterPrototypes[i], ref)) continue;
        if (!findGet) {
          if (!PsiUtil.isAccessedForWriting(ref)) continue;
        }
        if (!findSet || field.hasModifierProperty(PsiModifier.FINAL)) {
          if (!PsiUtil.isAccessedForReading(ref)) continue;
        }
        if (!myDialog.isToUseAccessorsWhenAccessible()) {
          PsiClass accessObjectClass = null;
          PsiExpression qualifier = ref.getQualifierExpression();
          if (qualifier != null) {
            accessObjectClass = (PsiClass)PsiUtil.getAccessObjectClass(qualifier).getElement();
          }
          if (facade.getResolveHelper()
            .isAccessible(field, newModifierList, ref, accessObjectClass, null)) {
            continue;
          }
        }
        UsageInfo usageInfo = new MyUsageInfo(ref, i);
        array.add(usageInfo);
      }
    }
    MyUsageInfo[] usageInfos = array.toArray(new MyUsageInfo[array.size()]);
    return UsageViewUtil.removeDuplicatedUsages(usageInfos);
  }

  protected void refreshElements(PsiElement[] elements) {
    LOG.assertTrue(elements.length == myFields.length);

    for (int idx = 0; idx < elements.length; idx++) {
      PsiElement element = elements[idx];

      LOG.assertTrue(element instanceof PsiField);

      myFields[idx] = (PsiField)element;
    }

    myClass = myFields[0].getContainingClass();
  }

  protected void performRefactoring(UsageInfo[] usages) {
    // change visibility of fields
    if (myDialog.getFieldsVisibility() != null){
      // "as is"
      for (PsiField field : myFields) {
        setNewFieldVisibility(field);
      }
    }

    // generate accessors
    myNameToGetter = new com.intellij.util.containers.HashMap<String, PsiMethod>();
    myNameToSetter = new com.intellij.util.containers.HashMap<String, PsiMethod>();
    for(int i = 0; i < myFields.length; i++){
      PsiField field = myFields[i];
      if (myDialog.isToEncapsulateGet()){
        PsiMethod[] prototypes = myDialog.getGetterPrototypes();
        addOrChangeAccessor(prototypes[i], myNameToGetter);
      }
      if (myDialog.isToEncapsulateSet() && !field.hasModifierProperty(PsiModifier.FINAL)){
        PsiMethod[] prototypes = myDialog.getSetterPrototypes();
        addOrChangeAccessor(prototypes[i], myNameToSetter);
      }
    }

    Map<PsiFile, List<MyUsageInfo>> usagesInFiles = new HashMap<PsiFile, List<MyUsageInfo>>();
    for (UsageInfo usage : usages) {
      PsiElement element = usage.getElement();
      if (element == null) continue;
      final PsiFile file = element.getContainingFile();
      List<MyUsageInfo> usagesInFile = usagesInFiles.get(file);
      if (usagesInFile == null) {
        usagesInFile = new ArrayList<MyUsageInfo>();
        usagesInFiles.put(file, usagesInFile);
      }
      usagesInFile.add(((MyUsageInfo)usage));
    }

    for (List<MyUsageInfo> usageInfos : usagesInFiles.values()) {
      //this is to avoid elements to become invalid as a result of processUsage
      RefactoringUtil.sortDepthFirstRightLeftOrder(usages);

      for (MyUsageInfo info : usageInfos) {
        processUsage(info);
      }
    }
  }

  private void setNewFieldVisibility(PsiField field) {
    try{
      if (myDialog.getFieldsVisibility() != null){
        field.normalizeDeclaration();
        PsiUtil.setModifierProperty(field, myDialog.getFieldsVisibility(), true);
      }
    }
    catch(IncorrectOperationException e){
      LOG.error(e);
    }
  }

  private void addOrChangeAccessor(PsiMethod prototype, HashMap<String,PsiMethod> nameToAncestor) {
    PsiMethod existing = myClass.findMethodBySignature(prototype, false);
    PsiMethod result = existing;
    try{
      if (existing == null){
        PsiUtil.setModifierProperty(prototype, myDialog.getAccessorsVisibility(), true);
        result = (PsiMethod) myClass.add(prototype);
      }
      else{
        //TODO : change visibility
      }
      nameToAncestor.put(prototype.getName(), result);
    }
    catch(IncorrectOperationException e){
      LOG.error(e);
    }
  }

  private boolean isUsedInExistingAccessor(PsiMethod prototype, PsiElement element) {
    PsiMethod existingAccessor = myClass.findMethodBySignature(prototype, false);
    if (existingAccessor != null) {
      PsiElement parent = element;
      while (parent != null) {
        if (existingAccessor.equals(parent)) return true;
        parent = parent.getParent();
      }
    }
    return false;
  }

  private void processUsage(MyUsageInfo usage) {
    PsiField field = myFields[usage.fieldIndex];
    boolean processGet = myDialog.isToEncapsulateGet();
    boolean processSet = myDialog.isToEncapsulateSet() && !field.hasModifierProperty(PsiModifier.FINAL);
    if (!processGet && !processSet) return;
    PsiElementFactory factory = JavaPsiFacade.getInstance(myProject).getElementFactory();

    try{
      final PsiReferenceExpression expr = (PsiReferenceExpression)usage.getElement();
      if (expr == null) return;
      final PsiElement parent = expr.getParent();
      if (parent instanceof PsiAssignmentExpression && expr.equals(((PsiAssignmentExpression)parent).getLExpression())){
        PsiAssignmentExpression assignment = (PsiAssignmentExpression)parent;
        if (assignment.getRExpression() == null) return;
        PsiJavaToken opSign = assignment.getOperationSign();
        IElementType opType = opSign.getTokenType();
        if (opType == JavaTokenType.EQ) {
          {
            if (!processSet) return;
            final int fieldIndex = usage.fieldIndex;
            final PsiExpression setterArgument = assignment.getRExpression();

            PsiMethodCallExpression methodCall = createSetterCall(fieldIndex, setterArgument, expr);

            if (methodCall != null) {
              assignment.replace(methodCall);
            }
            //TODO: check if value is used!!!
          }
        }
        else if (opType == JavaTokenType.ASTERISKEQ || opType == JavaTokenType.DIVEQ || opType == JavaTokenType.PERCEQ ||
                 opType == JavaTokenType.PLUSEQ ||
                 opType == JavaTokenType.MINUSEQ ||
                 opType == JavaTokenType.LTLTEQ ||
                 opType == JavaTokenType.GTGTEQ ||
                 opType == JavaTokenType.GTGTGTEQ ||
                 opType == JavaTokenType.ANDEQ ||
                 opType == JavaTokenType.OREQ ||
                 opType == JavaTokenType.XOREQ) {
          {
            // Q: side effects of qualifier??!

            String opName = opSign.getText();
            LOG.assertTrue(StringUtil.endsWithChar(opName, '='));
            opName = opName.substring(0, opName.length() - 1);

            PsiExpression getExpr = expr;
            if (processGet) {
              final int fieldIndex = usage.fieldIndex;
              final PsiMethodCallExpression getterCall = createGetterCall(fieldIndex, expr);
              if (getterCall != null) {
                getExpr = getterCall;
              }
            }

            @NonNls String text = "a" + opName + "b";
            PsiBinaryExpression binExpr = (PsiBinaryExpression)factory.createExpressionFromText(text, expr);
            binExpr = (PsiBinaryExpression)CodeStyleManager.getInstance(myProject).reformat(binExpr);
            binExpr.getLOperand().replace(getExpr);
            binExpr.getROperand().replace(assignment.getRExpression());

            PsiExpression setExpr;
            if (processSet) {
              setExpr = createSetterCall(usage.fieldIndex, binExpr, expr);
            }
            else {
              text = "a = b";
              PsiAssignmentExpression assignment1 = (PsiAssignmentExpression)factory.createExpressionFromText(text, null);
              assignment1 = (PsiAssignmentExpression)CodeStyleManager.getInstance(myProject).reformat(assignment1);
              assignment1.getLExpression().replace(expr);
              assignment1.getRExpression().replace(binExpr);
              setExpr = assignment1;
            }

            assignment.replace(setExpr);
            //TODO: check if value is used!!!
          }
        }
      }
      else if (RefactoringUtil.isPlusPlusOrMinusMinus(parent)){
        PsiJavaToken sign;
        if (parent instanceof PsiPrefixExpression){
          sign = ((PsiPrefixExpression)parent).getOperationSign();
        }
        else{
          sign = ((PsiPostfixExpression)parent).getOperationSign();
        }
        IElementType tokenType = sign.getTokenType();

        PsiExpression getExpr = expr;
        if (processGet){
          final int fieldIndex = usage.fieldIndex;
          final PsiMethodCallExpression getterCall = createGetterCall(fieldIndex, expr);
          if(getterCall != null) {
            getExpr = getterCall;
          }
        }

        @NonNls String text;
        if (tokenType == JavaTokenType.PLUSPLUS){
          text = "a+1";
        }
        else{
          text = "a-1";
        }
        PsiBinaryExpression binExpr = (PsiBinaryExpression)factory.createExpressionFromText(text, null);
        binExpr = (PsiBinaryExpression)CodeStyleManager.getInstance(myProject).reformat(binExpr);
        binExpr.getLOperand().replace(getExpr);

        PsiExpression setExpr;
        if (processSet){
          final int fieldIndex = usage.fieldIndex;
          setExpr = createSetterCall(fieldIndex, binExpr, expr);
        }
        else{
          text = "a = b";
          PsiAssignmentExpression assignment = (PsiAssignmentExpression)factory.createExpressionFromText(text, null);
          assignment = (PsiAssignmentExpression)CodeStyleManager.getInstance(myProject).reformat(assignment);
          assignment.getLExpression().replace(expr);
          assignment.getRExpression().replace(binExpr);
          setExpr = assignment;
        }
        parent.replace(setExpr);
      }
      else{
        if (!processGet) return;
        PsiMethodCallExpression methodCall = createGetterCall(usage.fieldIndex, expr);

        if (methodCall != null) {
          expr.replace(methodCall);
        }
      }
    }
    catch(IncorrectOperationException e){
      LOG.error(e);
    }
  }

  private PsiMethodCallExpression createSetterCall(final int fieldIndex, final PsiExpression setterArgument, PsiReferenceExpression expr) throws IncorrectOperationException {
    String[] setterNames = myDialog.getSetterNames();
    PsiElementFactory factory = JavaPsiFacade.getInstance(expr.getProject()).getElementFactory();
    final String setterName = setterNames[fieldIndex];
    @NonNls String text = setterName + "(a)";
    PsiExpression qualifier = expr.getQualifierExpression();
    if (qualifier != null){
      text = "q." + text;
    }
    PsiMethodCallExpression methodCall = (PsiMethodCallExpression)factory.createExpressionFromText(text, expr);
    methodCall = (PsiMethodCallExpression)CodeStyleManager.getInstance(myProject).reformat(methodCall);

    methodCall.getArgumentList().getExpressions()[0].replace(setterArgument);
    if (qualifier != null){
      methodCall.getMethodExpression().getQualifierExpression().replace(qualifier);
    }
    final PsiMethod targetMethod = myNameToSetter.get(setterName);
    methodCall = checkMethodResolvable(methodCall, targetMethod, expr);
    if (methodCall == null) {
      VisibilityUtil.escalateVisibility(myFields[fieldIndex], expr);
    }
    return methodCall;
  }

  @Nullable
  private PsiMethodCallExpression createGetterCall(final int fieldIndex, PsiReferenceExpression expr)
          throws IncorrectOperationException {
    String[] getterNames = myDialog.getGetterNames();
    PsiElementFactory factory = JavaPsiFacade.getInstance(expr.getProject()).getElementFactory();
    final String getterName = getterNames[fieldIndex];
    @NonNls String text = getterName + "()";
    PsiExpression qualifier = expr.getQualifierExpression();
    if (qualifier != null){
      text = "q." + text;
    }
    PsiMethodCallExpression methodCall = (PsiMethodCallExpression)factory.createExpressionFromText(text, expr);
    methodCall = (PsiMethodCallExpression)CodeStyleManager.getInstance(myProject).reformat(methodCall);

    if (qualifier != null){
      methodCall.getMethodExpression().getQualifierExpression().replace(qualifier);
    }

    final PsiMethod targetMethod = myNameToGetter.get(getterName);
    methodCall = checkMethodResolvable(methodCall, targetMethod, expr);
    if(methodCall == null) {
      VisibilityUtil.escalateVisibility(myFields[fieldIndex], expr);
    }
    return methodCall;
  }

  @Nullable
  private PsiMethodCallExpression checkMethodResolvable(PsiMethodCallExpression methodCall, final PsiMethod targetMethod, PsiReferenceExpression context) throws IncorrectOperationException {
    PsiElementFactory factory = JavaPsiFacade.getInstance(targetMethod.getProject()).getElementFactory();
    final PsiElement resolved = methodCall.getMethodExpression().resolve();
    if (resolved != targetMethod) {
      PsiClass containingClass;
      if (resolved instanceof PsiMethod) {
        containingClass = ((PsiMethod) resolved).getContainingClass();
      } else if (resolved instanceof PsiClass) {
        containingClass = (PsiClass)resolved;
      }
      else {
        return null;
      }
      if(containingClass.isInheritor(myClass, false)) {
        final PsiExpression newMethodExpression =
                factory.createExpressionFromText("super." + targetMethod.getName(), context);
        methodCall.getMethodExpression().replace(newMethodExpression);
      } else {
        methodCall = null;
      }
    }
    return methodCall;
  }



  private static class MyUsageInfo extends UsageInfo {
    public final int fieldIndex;

    public MyUsageInfo(PsiJavaCodeReferenceElement ref, int fieldIndex) {
      super(ref);
      this.fieldIndex = fieldIndex;
    }
  }
}