/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.changeSignature;

import com.intellij.lang.Language;
import com.intellij.psi.*;
import com.intellij.refactoring.changeSignature.JavaChangeInfo;
import com.intellij.refactoring.changeSignature.JavaParameterInfo;
import com.intellij.refactoring.changeSignature.ThrownExceptionInfo;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

import java.util.List;

/**
 * @author Maxim.Medvedev
 */
class GrChangeInfoImpl implements JavaChangeInfo {
  GrMethod method;
  final String newName;
  final CanonicalTypes.Type returnType;
  final String visibilityModifier;
  final List<GrParameterInfo> parameters;
  boolean changeParameters = false;
  private boolean myIsParameterTypesChanged = false;
  private boolean myIsParameterNamesChanged = false;
  private boolean myIsNameChanged = false;
  private boolean myIsVisibilityChanged = false;
  private boolean myIsReturnTypeChanged = false;
  private boolean myIsRetainVarargs;
  private boolean myIsArrayToVarargs;
  private boolean myIsObtainVarargs;
  private boolean myWasVarargs;
  private String myOldName;
  private PsiIdentifier myNewNameIdentifier;
  private PsiExpression[] defaultValues;

  public GrChangeInfoImpl(GrMethod method,
                          String visibilityModifier,
                          CanonicalTypes.Type returnType,
                          String newName,
                          List<GrParameterInfo> parameters) {
    this.method = method;
    this.visibilityModifier = visibilityModifier;
    this.returnType = returnType;
    this.parameters = parameters;
    this.newName = newName;
    myOldName = method.getName();

    if (!method.getName().equals(newName)) {
      myIsNameChanged = true;
    }

    myIsVisibilityChanged = !method.hasModifierProperty(visibilityModifier);

    if (!method.isConstructor()) {
      PsiType oldReturnType = method.getReturnType();
      try {
        PsiType newReturnType = returnType.getType(method, getMethod().getManager());
        if ((oldReturnType == null && newReturnType != null) || (oldReturnType != null && !oldReturnType.equals(newReturnType))) {
          myIsReturnTypeChanged = true;
        }
      }
      catch (IncorrectOperationException e) {
        myIsReturnTypeChanged = true;
      }
    }

    GrParameter[] params = method.getParameters();
    final int oldParameterCount = this.method.getParameters().length;
    if (oldParameterCount != this.parameters.size()) {
      changeParameters = true;
    }
    else {
      for (int i = 0, parametersSize = parameters.size(); i < parametersSize; i++) {
        GrParameterInfo parameter = parameters.get(i);
        if (parameter.getOldIndex() != i) {
          changeParameters = true;
          break;
        }
        if (!params[i].getName().equals(parameter.getName())) {
          myIsParameterNamesChanged = true;
        }
        try {
          PsiType type = parameter.createType(method, method.getManager());
          PsiType oldType = params[i].getType();
          if (!oldType.equals(type)) {
            myIsParameterTypesChanged = true;
          }
        }
        catch (IncorrectOperationException e) {
          myIsParameterTypesChanged = true;
        }
      }
    }

    myWasVarargs = method.isVarArgs();
    if (parameters.size()==0) {
      myIsObtainVarargs=false;
      myIsRetainVarargs=false;
      myIsArrayToVarargs=false;
    } else {
      GrParameterInfo lastNewParam = parameters.get(parameters.size() - 1);
      myIsObtainVarargs= lastNewParam.isVarargType();
      myIsRetainVarargs = lastNewParam.getOldIndex() >= 0 && myIsObtainVarargs;
      if (myIsRetainVarargs) {
        final PsiType oldTypeForVararg = params[lastNewParam.getOldIndex()].getType();
        myIsArrayToVarargs = (oldTypeForVararg instanceof PsiArrayType && !(oldTypeForVararg instanceof PsiEllipsisType));
      }
      else {
        myIsArrayToVarargs = false;
      }
    }

    if (myIsNameChanged) {
      myNewNameIdentifier = JavaPsiFacade.getElementFactory(getMethod().getProject()).createIdentifier(newName);
    }

    PsiElementFactory factory = JavaPsiFacade.getInstance(method.getProject()).getElementFactory();
    defaultValues = new PsiExpression[parameters.size()];
    for(int i = 0; i < parameters.size(); i++){
      JavaParameterInfo info = parameters.get(i);
      if (info.getOldIndex() < 0 && !info.isVarargType()){
        if (info.getDefaultValue() == null) continue;
        try{
          defaultValues[i] = factory.createExpressionFromText(info.getDefaultValue(), method);
        }
        catch(IncorrectOperationException e){
//          LOG.error(e);
        }
      }
    }
  }

  @NotNull
  public JavaParameterInfo[] getNewParameters() {
    return parameters.toArray(new GrParameterInfo[parameters.size()]);
  }

  @Modifier
  public String getNewVisibility() {
    return visibilityModifier;
  }

  public boolean isParameterSetOrOrderChanged() {
    return changeParameters;
  }

  public boolean isParameterTypesChanged() {
    return myIsParameterTypesChanged;
  }

  public boolean isParameterNamesChanged() {
    return myIsParameterNamesChanged;
  }

  public boolean isGenerateDelegate() {
    return false;
  }

  public boolean isNameChanged() {
    return myIsNameChanged;
  }

  public boolean isVisibilityChanged() {
    return myIsVisibilityChanged;
  }

  public boolean isExceptionSetChanged() {
    return false;
  }

  public boolean isExceptionSetOrOrderChanged() {
    return false;
  }

  public PsiMethod getMethod() {
    return method;
  }

  public boolean isReturnTypeChanged() {
    return myIsReturnTypeChanged;
  }

  public CanonicalTypes.Type getNewReturnType() {
    return returnType;
  }

  public boolean isChangeVisibility() {
    return !method.getModifierList().hasModifierProperty(visibilityModifier);
  }

  public boolean isChangeName() {
    return !method.getName().equals(newName);
  }

  public String getNewName() {
    return newName;
  }

  public Language getLanguage() {
    return GroovyFileType.GROOVY_LANGUAGE;
  }

  public String getVisibilityModifier() {
    return visibilityModifier;
  }

  @NotNull
  public String[] getOldParameterNames() {
    return new String[0];  //To change body of implemented methods use File | Settings | File Templates.
  }

  @NotNull
  public String[] getOldParameterTypes() {
    return new String[0];  //To change body of implemented methods use File | Settings | File Templates.
  }

  public ThrownExceptionInfo[] getNewExceptions() {
    return new ThrownExceptionInfo[0];  //To change body of implemented methods use File | Settings | File Templates.
  }

  public boolean isRetainsVarargs() {
    return myIsRetainVarargs;
  }

  public boolean isObtainsVarags() {
    return myIsObtainVarargs;
  }

  public boolean isArrayToVarargs() {
    return myIsArrayToVarargs;
  }

  public PsiIdentifier getNewNameIdentifier() {
    return myNewNameIdentifier;
  }

  public String getOldName() {
    return myOldName;
  }

  public boolean wasVararg() {
    return myWasVarargs;
  }

  public boolean[] toRemoveParm() {
    return new boolean[0];  //To change body of implemented methods use File | Settings | File Templates.
  }

  public PsiExpression getValue(int i, PsiCallExpression callExpression) {
    if (defaultValues[i] != null) return defaultValues[i];
    return parameters.get(i).getValue(callExpression);
  }

  public void updateMethod(PsiMethod psiMethod) {
    if (psiMethod instanceof GrMethod) {
      method = (GrMethod)psiMethod;
    }
  }
}
