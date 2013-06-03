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
package com.intellij.codeInsight.completion.scope;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInspection.SuppressManager;
import com.intellij.codeInspection.accessStaticViaInstance.AccessStaticViaInstance;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.BaseScopeProcessor;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.JavaScopeProcessorEvent;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 20.01.2003
 * Time: 16:13:27
 * To change this template use Options | File Templates.
 */
public class JavaCompletionProcessor extends BaseScopeProcessor implements ElementClassHint {

  private boolean myStatic = false;
  private PsiElement myDeclarationHolder = null;
  private final Set<Object> myResultNames = new THashSet<Object>();
  private final List<CompletionElement> myResults = new ArrayList<CompletionElement>();
  private final List<CompletionElement> myFilteredResults = new ArrayList<CompletionElement>();
  private final PsiElement myElement;
  private final PsiElement myScope;
  private final ElementFilter myFilter;
  private boolean myMembersFlag = false;
  private PsiType myQualifierType = null;
  private PsiClass myQualifierClass = null;
  private final Condition<String> myMatcher;
  private final Options myOptions;
  private final Set<PsiField> myNonInitializedFields = new HashSet<PsiField>();
  private boolean myAllowStaticWithInstanceQualifier;

  public JavaCompletionProcessor(@NotNull PsiElement element, ElementFilter filter, Options options, @NotNull Condition<String> nameCondition) {
    myOptions = options;
    myElement = element;
    myMatcher = nameCondition;
    myFilter = filter;
    PsiElement scope = element;
    if (JavaResolveUtil.isInJavaDoc(myElement)) myMembersFlag = true;
    while(scope != null && !(scope instanceof PsiFile) && !(scope instanceof PsiClass)){
      scope = scope.getContext();
    }
    myScope = scope;
    if (!(element.getContainingFile() instanceof PsiJavaFile)) {
      myMembersFlag = true;
    }

    PsiElement elementParent = element.getContext();
    if (elementParent instanceof PsiReferenceExpression) {
      PsiExpression qualifier = ((PsiReferenceExpression)elementParent).getQualifierExpression();
      if (qualifier instanceof PsiSuperExpression) {
        final PsiJavaCodeReferenceElement qSuper = ((PsiSuperExpression)qualifier).getQualifier();
        if (qSuper == null) {
          myQualifierClass = JavaResolveUtil.getContextClass( myElement);
        } else {
          final PsiElement target = qSuper.resolve();
          myQualifierClass = target instanceof PsiClass ? (PsiClass)target : null;
        }
        if (myQualifierClass != null) {
          myQualifierType = JavaPsiFacade.getInstance(element.getProject()).getElementFactory().createType(myQualifierClass);
        }
      }
      else if (qualifier != null) {
        myQualifierType = qualifier.getType();
        myQualifierClass = PsiUtil.resolveClassInType(myQualifierType);
        if (myQualifierType == null && qualifier instanceof PsiJavaCodeReferenceElement) {
          final PsiElement target = ((PsiJavaCodeReferenceElement)qualifier).resolve();
          if (target instanceof PsiClass) {
            myQualifierClass = (PsiClass)target;
          }
        }
      }
    }

    if (myOptions.checkInitialized) {
      myNonInitializedFields.addAll(getNonInitializedFields(element));
    }

    myAllowStaticWithInstanceQualifier = !options.filterStaticAfterInstance || CodeInsightSettings.getInstance().SHOW_STATIC_AFTER_INSTANCE ||
                                         SuppressManager.getInstance()
                                           .isSuppressedFor(element, AccessStaticViaInstance.ACCESS_STATIC_VIA_INSTANCE);

  }

  private static boolean isInitializedImplicitly(PsiField field) {
    field = CompletionUtil.getOriginalOrSelf(field);
    for(ImplicitUsageProvider provider: ImplicitUsageProvider.EP_NAME.getExtensions()) {
      if (provider.isImplicitWrite(field)) {
        return true;
      }
    }
    return false;
  }

  public static Set<PsiField> getNonInitializedFields(PsiElement element) {
    final PsiStatement statement = PsiTreeUtil.getParentOfType(element, PsiStatement.class);
    final PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class, true, PsiClass.class);
    if (statement == null || method == null || !method.isConstructor()) {
      return Collections.emptySet();
    }

    PsiElement parent = element.getParent();
    while (parent != statement) {
      PsiElement next = parent.getParent();
      if (next instanceof PsiAssignmentExpression && parent == ((PsiAssignmentExpression)next).getLExpression()) {
        return Collections.emptySet();
      }
      if (parent instanceof PsiReferenceExpression && next instanceof PsiExpressionStatement) {
        return Collections.emptySet();
      }
      parent = next;
    }

    final Set<PsiField> fields = new HashSet<PsiField>();
    final PsiClass containingClass = method.getContainingClass();
    assert containingClass != null;
    for (PsiField field : containingClass.getFields()) {
      if (!field.hasModifierProperty(PsiModifier.STATIC) && field.getInitializer() == null && !isInitializedImplicitly(field)) {
        fields.add(field);
      }
    }

    method.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitAssignmentExpression(PsiAssignmentExpression expression) {
        if (expression.getTextRange().getStartOffset() < statement.getTextRange().getStartOffset()) {
          final PsiExpression lExpression = expression.getLExpression();
          if (lExpression instanceof PsiReferenceExpression) {
            //noinspection SuspiciousMethodCalls
            fields.remove(((PsiReferenceExpression)lExpression).resolve());
          }
        }
        super.visitAssignmentExpression(expression);
      }

      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        if (expression.getTextRange().getStartOffset() < statement.getTextRange().getStartOffset()) {
          final PsiReferenceExpression methodExpression = expression.getMethodExpression();
          if (methodExpression.textMatches("this")) {
            fields.clear();
          }
        }
        super.visitMethodCallExpression(expression);
      }
    });
    return fields;
  }

  @Override
  public void handleEvent(Event event, Object associated){
    if(event == JavaScopeProcessorEvent.START_STATIC){
      myStatic = true;
    }
    if(event == JavaScopeProcessorEvent.CHANGE_LEVEL){
      myMembersFlag = true;
    }
    if (event == JavaScopeProcessorEvent.SET_CURRENT_FILE_CONTEXT) {
      myDeclarationHolder = (PsiElement)associated;
    }
  }

  @Override
  public boolean execute(@NotNull PsiElement element, ResolveState state) {
    //noinspection SuspiciousMethodCalls
    if (myNonInitializedFields.contains(element)) {
      return true;
    }

    if (element instanceof PsiPackage && myScope instanceof PsiClass && !isQualifiedContext()) {
      return true;
    }

    if (satisfies(element, state) && isAccessible(element)) {
      CompletionElement element1 = new CompletionElement(element, state.get(PsiSubstitutor.KEY));
      if (myResultNames.add(element1.getUniqueId())) {
        StaticProblem sp = myElement.getParent() instanceof PsiMethodReferenceExpression ? StaticProblem.none : getStaticProblem(element);
        if (sp != StaticProblem.instanceAfterStatic) {
          (sp == StaticProblem.staticAfterInstance ? myFilteredResults : myResults).add(element1);
        }
      }
    }
    return true;
  }

  private boolean isQualifiedContext() {
    final PsiElement elementParent = myElement.getParent();
    return elementParent instanceof PsiQualifiedReference && ((PsiQualifiedReference)elementParent).getQualifier() != null;
  }

  private StaticProblem getStaticProblem(PsiElement element) {
    if (myOptions.showInstanceInStaticContext && !isQualifiedContext()) {
      return StaticProblem.none;
    }
    if (element instanceof PsiModifierListOwner) {
      PsiModifierListOwner modifierListOwner = (PsiModifierListOwner)element;
      if (myStatic) {
        if (!(element instanceof PsiClass) && !modifierListOwner.hasModifierProperty(PsiModifier.STATIC)) {
          // we don't need non static method in static context.
          return StaticProblem.instanceAfterStatic;
        }
      }
      else {
        if (!myAllowStaticWithInstanceQualifier
            && modifierListOwner.hasModifierProperty(PsiModifier.STATIC)
            && !myMembersFlag) {
          // according settings we don't need to process such fields/methods
          return StaticProblem.staticAfterInstance;
        }
      }
    }
    return StaticProblem.none;
  }

  public boolean satisfies(@NotNull PsiElement element, @NotNull ResolveState state) {
    final String name = PsiUtilCore.getName(element);
    if (name != null && StringUtil.isNotEmpty(name) && myMatcher.value(name)) {
      if (myFilter.isClassAcceptable(element.getClass()) && myFilter.isAcceptable(new CandidateInfo(element, state.get(PsiSubstitutor.KEY)), myElement)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  public PsiType getQualifierType() {
    return myQualifierType;
  }

  private boolean isAccessible(final PsiElement element) {
    if (!myOptions.checkAccess) return true;
    if (!(element instanceof PsiMember)) return true;

    PsiMember member = (PsiMember)element;
    return JavaPsiFacade.getInstance(element.getProject()).getResolveHelper().isAccessible(member, member.getModifierList(), myElement, myQualifierClass, myDeclarationHolder);
  }

  public void setCompletionElements(@NotNull Object[] elements) {
    for (Object element: elements) {
      myResults.add(new CompletionElement(element, PsiSubstitutor.EMPTY));
    }
  }

  public Iterable<CompletionElement> getResults() {
    if (myResults.isEmpty()) {
      return myFilteredResults;
    }
    return myResults;
  }

  public void clear() {
    myResults.clear();
    myFilteredResults.clear();
  }

  @Override
  public boolean shouldProcess(DeclarationKind kind) {
    switch (kind) {
      case CLASS:
        return myFilter.isClassAcceptable(PsiClass.class);

      case FIELD:
        return myFilter.isClassAcceptable(PsiField.class);

      case METHOD:
        return myFilter.isClassAcceptable(PsiMethod.class);

      case PACKAGE:
        return myFilter.isClassAcceptable(PsiPackage.class);

      case VARIABLE:
        return myFilter.isClassAcceptable(PsiVariable.class);

      case ENUM_CONST:
        return myFilter.isClassAcceptable(PsiEnumConstant.class);
    }

    return false;
  }

  @Override
  public <T> T getHint(@NotNull Key<T> hintKey) {
    if (hintKey == ElementClassHint.KEY) {
      //noinspection unchecked
      return (T)this;
    }
    if (hintKey == JavaCompletionHints.NAME_FILTER) {
      //noinspection unchecked
      return (T)myMatcher;
    }

    return super.getHint(hintKey);
  }

  public static class Options {
    public static final Options DEFAULT_OPTIONS = new Options(true, false, true, false);
    public static final Options CHECK_NOTHING = new Options(false, false, false, false);
    final boolean checkAccess;
    final boolean checkInitialized;
    final boolean filterStaticAfterInstance;
    final boolean showInstanceInStaticContext;

    private Options(boolean checkAccess, boolean checkInitialized, boolean filterStaticAfterInstance, boolean showInstanceInStaticContext) {
      this.checkAccess = checkAccess;
      this.checkInitialized = checkInitialized;
      this.filterStaticAfterInstance = filterStaticAfterInstance;
      this.showInstanceInStaticContext = showInstanceInStaticContext;
    }

    public Options withInitialized(boolean checkInitialized) {
      return new Options(checkAccess, checkInitialized, filterStaticAfterInstance, showInstanceInStaticContext);
    }
    public Options withCheckAccess(boolean checkAccess) {
      return new Options(checkAccess, checkInitialized, filterStaticAfterInstance, showInstanceInStaticContext);
    }
    public Options withFilterStaticAfterInstance(boolean filterStaticAfterInstance) {
      return new Options(checkAccess, checkInitialized, filterStaticAfterInstance, showInstanceInStaticContext);
    }
    public Options withShowInstanceInStaticContext(boolean showInstanceInStaticContext) {
      return new Options(checkAccess, checkInitialized, filterStaticAfterInstance, showInstanceInStaticContext);
    }
  }
  
  private enum StaticProblem { none, staticAfterInstance, instanceAfterStatic }
}
