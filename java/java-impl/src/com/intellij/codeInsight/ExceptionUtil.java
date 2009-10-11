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
package com.intellij.codeInsight;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.TypeConversionUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author mike
 */
public class ExceptionUtil {
  @NonNls private static final String CLONE_METHOD_NAME = "clone";

  private ExceptionUtil() {}

  @NotNull
  public static List<PsiClassType> getThrownExceptions(@NotNull PsiElement[] elements) {
    List<PsiClassType> array = new ArrayList<PsiClassType>();
    for (PsiElement element : elements) {
      List<PsiClassType> exceptions = getThrownExceptions(element);
      addExceptions(array, exceptions);
    }

    return array;
  }

  @NotNull
  public static List<PsiClassType> getThrownCheckedExceptions(@NotNull PsiElement[] elements) {
    List<PsiClassType> exceptions = getThrownExceptions(elements);
    if (exceptions.isEmpty()) return exceptions;
    exceptions = filterOutUncheckedExceptions(exceptions);
    return exceptions;
  }

  @NotNull
  private static List<PsiClassType> filterOutUncheckedExceptions(List<PsiClassType> exceptions) {
    List<PsiClassType> array = new ArrayList<PsiClassType>();
    for (PsiClassType exception : exceptions) {
      if (!isUncheckedException(exception)) array.add(exception);
    }
    return array;
  }

  @NotNull
  private static List<PsiClassType> getThrownExceptions(@NotNull PsiElement element) {
    if (element instanceof PsiClass) {
      if (element instanceof PsiAnonymousClass) {
        final PsiExpressionList argumentList = ((PsiAnonymousClass)element).getArgumentList();
        if (argumentList != null){
          return getThrownExceptions(argumentList);
        }
      }
      // filter class declaration in code
      return Collections.emptyList();
    }
    else if (element instanceof PsiMethodCallExpression) {
      PsiReferenceExpression methodRef = ((PsiMethodCallExpression)element).getMethodExpression();
      JavaResolveResult result = methodRef.advancedResolve(false);
      return getExceptionsByMethodAndChildren(element, result);
    }
    else if (element instanceof PsiNewExpression) {
      JavaResolveResult result = ((PsiNewExpression)element).resolveMethodGenerics();
      return getExceptionsByMethodAndChildren(element, result);
    }
    else if (element instanceof PsiThrowStatement) {
      PsiExpression expr = ((PsiThrowStatement)element).getException();
      if (expr == null) return Collections.emptyList();
      PsiType exception = expr.getType();
      List<PsiClassType> array = new ArrayList<PsiClassType>();
      if (exception instanceof PsiClassType) {
        array.add((PsiClassType)exception);
      }
      addExceptions(array, getThrownExceptions(expr));
      return array;
    }
    else if (element instanceof PsiTryStatement) {
      PsiTryStatement tryStatement = (PsiTryStatement)element;
      final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
      List<PsiClassType> array = new ArrayList<PsiClassType>();
      if (tryBlock != null) {
        List<PsiClassType> exceptions = getThrownExceptions(tryBlock);
        array.addAll(exceptions);
      }

      PsiParameter[] parameters = tryStatement.getCatchBlockParameters();
      for (PsiParameter parm : parameters) {
        PsiType exception = parm.getType();
        for (int j = array.size() - 1; j >= 0; j--) {
          PsiClassType exception1 = array.get(j);
          if (exception.isAssignableFrom(exception1)) {
            array.remove(exception1);
          }
        }
      }

      PsiCodeBlock[] catchBlocks = tryStatement.getCatchBlocks();
      for (PsiCodeBlock catchBlock : catchBlocks) {
        addExceptions(array, getThrownExceptions(catchBlock));
      }

      PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
      if (finallyBlock != null) {
        // if finally block completes normally, exception not catched
        // if finally block completes abruptly, exception gets lost
        try {
          ControlFlow flow = ControlFlowFactory.getInstance(finallyBlock.getProject()).getControlFlow(finallyBlock, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance(), false);
          int completionReasons = ControlFlowUtil.getCompletionReasons(flow, 0, flow.getSize());
          List<PsiClassType> thrownExceptions = getThrownExceptions(finallyBlock);
          if ((completionReasons & ControlFlowUtil.NORMAL_COMPLETION_REASON) == 0) {
            array = new ArrayList<PsiClassType>(thrownExceptions);
          }
          else {
            addExceptions(array, thrownExceptions);
          }
        }
        catch (AnalysisCanceledException e) {
          // incomplete code
        }
      }

      return array;
    }
    return getThrownExceptions(element.getChildren());
  }

  @NotNull
  private static List<PsiClassType> getExceptionsByMethodAndChildren(PsiElement element, JavaResolveResult resolveResult) {
    PsiMethod method = (PsiMethod)resolveResult.getElement();
    List<PsiClassType> result = new ArrayList<PsiClassType>();
    if (method != null) {
      PsiSubstitutor substitutor = resolveResult.getSubstitutor();
      final PsiClassType[] referenceTypes = method.getThrowsList().getReferencedTypes();
      for (PsiType type : referenceTypes) {
        type = substitutor.substitute(type);
        if (type instanceof PsiClassType) {
          result.add((PsiClassType)type);
        }
      }
    }

    PsiElement[] children = element.getChildren();
    for (PsiElement child : children) {
      addExceptions(result, getThrownExceptions(child));
    }
    return result;
  }

  private static void addExceptions(List<PsiClassType> array, Collection<PsiClassType> exceptions) {
    for (PsiClassType exception : exceptions) {
      addException(array, exception);
    }
  }

  private static void addException(List<PsiClassType> array, PsiClassType exception) {
    if (exception == null) return ;
    for (int i = array.size()-1; i>=0; i--) {
      PsiClassType exception1 = array.get(i);
      if (exception1.isAssignableFrom(exception)) return;
      if (exception.isAssignableFrom(exception1)) {
        array.remove(i);
      }
    }
    array.add(exception);
  }

  @NotNull
  public static Collection<PsiClassType> collectUnhandledExceptions(@NotNull PsiElement element, PsiElement topElement) {
    final Set<PsiClassType> set = collectUnhandledExceptions(element, topElement, null);
    return set == null ? Collections.<PsiClassType>emptyList() : set;
  }

  @Nullable
  private static Set<PsiClassType> collectUnhandledExceptions(@NotNull PsiElement element, PsiElement topElement, Set<PsiClassType> foundExceptions) {
    Collection<PsiClassType> unhandledExceptions = null;
    if (element instanceof PsiCallExpression) {
      PsiCallExpression expression = (PsiCallExpression)element;
      unhandledExceptions = getUnhandledExceptions(expression, topElement);
    }
    else if (element instanceof PsiThrowStatement) {
      PsiThrowStatement statement = (PsiThrowStatement)element;
      PsiClassType exception = getUnhandledException(statement, topElement);
      unhandledExceptions = exception == null ? Collections.<PsiClassType>emptyList() : Collections.singletonList(exception);
    }
    else if (element instanceof PsiCodeBlock
             && element.getParent() instanceof PsiMethod
             && ((PsiMethod)element.getParent()).isConstructor()
             && !firstStatementIsConstructorCall((PsiCodeBlock)element)) {
      // there is implicit parent constructor call
      final PsiMethod constructor = (PsiMethod)element.getParent();
      final PsiClass aClass = constructor.getContainingClass();
      final PsiClass superClass = aClass == null ? null : aClass.getSuperClass();
      final PsiMethod[] superConstructors = superClass == null ? PsiMethod.EMPTY_ARRAY : superClass.getConstructors();
      Set<PsiClassType> unhandled = new HashSet<PsiClassType>();
      for (PsiMethod superConstructor : superConstructors) {
        if (!superConstructor.hasModifierProperty(PsiModifier.PRIVATE) && superConstructor.getParameterList().getParametersCount() == 0) {
          final PsiClassType[] exceptionTypes = superConstructor.getThrowsList().getReferencedTypes();
          for (PsiClassType exceptionType : exceptionTypes) {
            if (!isUncheckedException(exceptionType) && !isHandled(element, exceptionType, topElement)) {
              unhandled.add(exceptionType);
            }
          }
          break;
        }
      }

      // plus all exceptions thrown in instance class initializers
      if (aClass != null) {
        final PsiClassInitializer[] initializers = aClass.getInitializers();
        final Set<PsiClassType> thrownByInitializer = new THashSet<PsiClassType>();
        for (PsiClassInitializer initializer : initializers) {
          if (initializer.hasModifierProperty(PsiModifier.STATIC)) continue;
          thrownByInitializer.clear();
          collectUnhandledExceptions(initializer.getBody(), initializer, thrownByInitializer);
          for (PsiClassType thrown : thrownByInitializer) {
            if (!isHandled(constructor.getBody(), thrown, topElement)) {
              unhandled.add(thrown);
            }
          }
        }
      }
      unhandledExceptions = unhandled;
    }

    if (unhandledExceptions != null) {
      if (foundExceptions == null) {
        foundExceptions = new THashSet<PsiClassType>();
      }
      foundExceptions.addAll(unhandledExceptions);
    }

    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      foundExceptions = collectUnhandledExceptions(child, topElement, foundExceptions);
    }

    return foundExceptions;
  }

  private static boolean firstStatementIsConstructorCall(PsiCodeBlock constructorBody) {
    final PsiStatement[] statements = constructorBody.getStatements();
    if (statements.length == 0) return false;
    if (!(statements[0] instanceof PsiExpressionStatement)) return false;

    final PsiExpression expression = ((PsiExpressionStatement)statements[0]).getExpression();
    if (!(expression instanceof PsiMethodCallExpression)) return false;
    final PsiMethod method = (PsiMethod)((PsiMethodCallExpression)expression).getMethodExpression().resolve();
    return method != null && method.isConstructor();
  }

  @NotNull
  public static List<PsiClassType> getUnhandledExceptions(PsiElement[] elements) {
    final List<PsiClassType> array = new ArrayList<PsiClassType>();
    PsiElementVisitor visitor = new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitCallExpression(PsiCallExpression expression) {
        addExceptions(array, getUnhandledExceptions(expression, null));
        visitElement(expression);
      }

      @Override public void visitThrowStatement(PsiThrowStatement statement) {
        addException(array, getUnhandledException(statement, null));
        visitElement(statement);
      }
    };

    for (PsiElement element : elements) {
      element.accept(visitor);
    }

    return array;
  }

  @NotNull
  public static List<PsiClassType> getUnhandledExceptions(PsiElement element) {
    if (element instanceof PsiCallExpression) {
      PsiCallExpression expression = (PsiCallExpression)element;
      return getUnhandledExceptions(expression, null);
    }
    else if (element instanceof PsiThrowStatement) {
      PsiThrowStatement throwStatement = (PsiThrowStatement)element;
      PsiClassType exception = getUnhandledException(throwStatement, null);
      if (exception != null) return Collections.singletonList(exception);
    }

    return Collections.emptyList();
  }

  @NotNull
  public static List<PsiClassType> getUnhandledExceptions(PsiCallExpression methodCall, PsiElement topElement) {
    final JavaResolveResult result = methodCall.resolveMethodGenerics();
    PsiMethod method = (PsiMethod)result.getElement();
    return getUnhandledExceptions(method, methodCall, topElement,
                                  ApplicationManager.getApplication().runReadAction(new Computable<PsiSubstitutor>() {
                                    public PsiSubstitutor compute() {
                                      return result.getSubstitutor();
                                    }
                                  }));
  }

  @Nullable
  public static PsiClassType getUnhandledException(PsiThrowStatement throwStatement, PsiElement topElement){
    final PsiExpression exception = throwStatement.getException();
    if (exception != null) {
      final PsiType type = exception.getType();
      if (type instanceof PsiClassType) {
        PsiClassType classType = (PsiClassType)type;
        if (!isUncheckedException(classType) && !isHandled(throwStatement, classType, topElement)) {
          return classType;
        }
      }
    }
    return null;
  }


  @NotNull
  private static List<PsiClassType> getUnhandledExceptions(PsiMethod method,
                                                       PsiElement element,
                                                       PsiElement topElement,
                                                       PsiSubstitutor substitutor) {
    if (method == null || isArrayClone(method, element)) {
      return Collections.emptyList();
    }
    final PsiClassType[] referencedTypes = method.getThrowsList().getReferencedTypes();
    if (referencedTypes.length > 0) {
      List<PsiClassType> result = new ArrayList<PsiClassType>();

      for (PsiClassType referencedType : referencedTypes) {
        final PsiType type = substitutor.substitute(referencedType);
        if (!(type instanceof PsiClassType)) continue;
        PsiClassType classType = (PsiClassType)type;
        PsiClass exceptionClass = ((PsiClassType)type).resolve();
        if (exceptionClass == null) continue;

        if (isUncheckedException(classType)) continue;
        if (isHandled(element, classType, topElement)) continue;

        result.add((PsiClassType)type);
      }

      return result;
    }
    return Collections.emptyList();
  }

  private static boolean isArrayClone(PsiMethod method, PsiElement element) {
    if (!(element instanceof PsiMethodCallExpression)) return false;
    if (!method.getName().equals(CLONE_METHOD_NAME)) return false;
    PsiClass containingClass = method.getContainingClass();
    if (containingClass == null || !"java.lang.Object".equals(containingClass.getQualifiedName())) {
      return false;
    }

    PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)element;
    final PsiExpression qualifierExpression = methodCallExpression.getMethodExpression().getQualifierExpression();
    return qualifierExpression != null && qualifierExpression.getType() instanceof PsiArrayType;
  }

  public static boolean isUncheckedException(PsiClassType type) {
    final GlobalSearchScope searchScope = type.getResolveScope();
    final PsiClass aClass = type.resolve();
    if (aClass == null) return false;
    final PsiClass runtimeExceptionClass = ApplicationManager.getApplication().runReadAction(
        new Computable<PsiClass>() {
          public PsiClass compute() {
            return JavaPsiFacade.getInstance(aClass.getProject()).findClass("java.lang.RuntimeException", searchScope);
          }
        }
    );
    if (runtimeExceptionClass != null &&
        InheritanceUtil.isInheritorOrSelf(aClass, runtimeExceptionClass, true)) {
      return true;
    }

    final PsiClass errorClass = ApplicationManager.getApplication().runReadAction(
        new Computable<PsiClass>() {
          public PsiClass compute() {
            return JavaPsiFacade.getInstance(aClass.getProject()).findClass("java.lang.Error", searchScope);
          }
        }
    );
    return errorClass != null && InheritanceUtil.isInheritorOrSelf(aClass, errorClass, true);
  }

  public static boolean isUncheckedExceptionOrSuperclass(@NotNull PsiClassType type) {
    String canonicalText = type.getCanonicalText();
    return "java.lang.Throwable".equals(canonicalText) ||
           "java.lang.Exception".equals(canonicalText) ||
           isUncheckedException(type);
  }

  public static boolean isHandled(PsiClassType exceptionType, PsiElement throwPlace) {
    return isHandled(throwPlace, exceptionType, throwPlace.getContainingFile());
  }

  private static boolean isHandled(PsiElement element, PsiClassType exceptionType, PsiElement topElement) {
    if (element == null || element.getParent() == topElement || element.getParent() == null) return false;

    final PsiElement parent = element.getParent();

    if (parent instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)parent;
      return isHandledByMethodThrowsClause(method, exceptionType);
    }
    else if (parent instanceof PsiClass) {
      // arguments to anon class constructor should be handled higher
      // like in void f() throws XXX { new AA(methodThrowingXXX()) { ... }; }
      return parent instanceof PsiAnonymousClass && isHandled(parent, exceptionType, topElement);
    }
    else if (parent instanceof PsiClassInitializer) {
      if (((PsiClassInitializer)parent).hasModifierProperty(PsiModifier.STATIC)) return false;
      // anonymous class initializers can throw any exceptions
      if (!(parent.getParent() instanceof PsiAnonymousClass)) {
        // exception thrown from within class instance initializer must be handled in every class constructor
        // check each constructor throws exception or superclass (there must be at least one)
        final PsiClass aClass = ((PsiClassInitializer)parent).getContainingClass();
        return areAllConstructorsThrow(aClass, exceptionType);
      }
    }
    else if (parent instanceof PsiTryStatement) {
      PsiTryStatement tryStatement = (PsiTryStatement)parent;
      if (tryStatement.getTryBlock() == element && isCatched(tryStatement, exceptionType)) {
        return true;
      }
      PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
      if (element instanceof PsiCatchSection && finallyBlock != null && blockCompletesAbruptly(finallyBlock)) {
        // exception swallowed
        return true;
      }
    }
    else if (parent instanceof JavaCodeFragment) {
      JavaCodeFragment codeFragment = (JavaCodeFragment)parent;
      JavaCodeFragment.ExceptionHandler exceptionHandler = codeFragment.getExceptionHandler();
      return exceptionHandler != null && exceptionHandler.isHandledException(exceptionType);
    }
    else if (JspPsiUtil.isInJspFile(parent) && parent instanceof PsiFile) {
      return true;
    }
    else if (parent instanceof PsiFile) {
      return false;
    }
    else if (parent instanceof PsiField && ((PsiField)parent).getInitializer() == element) {
      final PsiClass aClass = ((PsiField)parent).getContainingClass();
      if (aClass != null && !(aClass instanceof PsiAnonymousClass) && !((PsiField)parent).hasModifierProperty(PsiModifier.STATIC)) {
        // exceptions thrown in field initalizers should be thrown in all class constructors
        return areAllConstructorsThrow(aClass, exceptionType);
      }
    }
    return isHandled(parent, exceptionType, topElement);
  }

  private static boolean areAllConstructorsThrow(final PsiClass aClass, PsiClassType exceptionType) {
    if (aClass == null) return false;
    final PsiMethod[] constructors = aClass.getConstructors();
    boolean thrown = constructors.length != 0;
    for (PsiMethod constructor : constructors) {
      if (!isHandledByMethodThrowsClause(constructor, exceptionType)) {
        thrown = false;
        break;
      }
    }
    return thrown;
  }

  private static boolean isCatched(PsiTryStatement tryStatement, PsiClassType exceptionType) {
    PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
    if (finallyBlock != null) {
      List<PsiClassType> exceptions = getUnhandledExceptions(finallyBlock);
      if (exceptions.contains(exceptionType)) return false;
      // if finally block completes normally, exception not catched
      // if finally block completes abruptly, exception gets lost
      if (blockCompletesAbruptly(finallyBlock)) return true;
    }

    final PsiParameter[] catchBlockParameters = tryStatement.getCatchBlockParameters();
    for (PsiParameter parameter : catchBlockParameters) {
      PsiType paramType = parameter.getType();
      if (paramType.isAssignableFrom(exceptionType)) return true;
    }

    return false;
  }

  private static boolean blockCompletesAbruptly(final PsiCodeBlock finallyBlock) {
    try {
      ControlFlow flow = ControlFlowFactory.getInstance(finallyBlock.getProject()).getControlFlow(finallyBlock, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance(), false);
      int completionReasons = ControlFlowUtil.getCompletionReasons(flow, 0, flow.getSize());
      if ((completionReasons & ControlFlowUtil.NORMAL_COMPLETION_REASON) == 0) return true;
    }
    catch (AnalysisCanceledException e) {
      return true;
    }
    return false;
  }

  private static boolean isHandledByMethodThrowsClause(PsiMethod method, PsiClassType exceptionType) {
    final PsiClassType[] referencedTypes = method.getThrowsList().getReferencedTypes();
    return isHandledBy(exceptionType, referencedTypes);
  }

  public static boolean isHandledBy(PsiClassType exceptionType, @NotNull PsiClassType[] referencedTypes) {
    for (PsiClassType classType : referencedTypes) {
      if (classType.isAssignableFrom(exceptionType)) return true;
    }
    return false;
  }

  public static void sortExceptionsByHierarchy(List<PsiClassType> exceptions) {
    if (exceptions.size() <= 1) return;
    sortExceptionsByHierarchy(exceptions.subList(1, exceptions.size()));
    for (int i=0; i<exceptions.size()-1;i++) {
      if (TypeConversionUtil.isAssignable(exceptions.get(i), exceptions.get(i+1))) {
        Collections.swap(exceptions, i,i+1);
      }
    }
  }
}
