/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.codeInspection.assignment;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.annotator.GroovyAnnotator;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.extensions.GroovyNamedArgumentProvider;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;
import org.jetbrains.plugins.groovy.findUsages.LiteralConstructorReference;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrThrowStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.*;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrReferenceResolveUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyConstantExpressionEvaluator;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.MixinMemberContributor;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Maxim.Medvedev
 */
public class GroovyAssignabilityCheckInspection extends BaseInspection {
  private static final Logger LOG = Logger.getInstance(GroovyAssignabilityCheckInspection.class);

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return ASSIGNMENT_ISSUES;
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Incompatible type assignments";
  }

  @Override
  protected String buildErrorString(Object... args) {
    return (String)args[0];
  }

  @Override
  protected BaseInspectionVisitor buildVisitor() {
    return new MyVisitor();
  }

  private static class MyVisitor extends BaseInspectionVisitor {
    private void checkAssignability(@NotNull PsiType expectedType, @NotNull GrExpression expression) {
      if (PsiUtil.isRawClassMemberAccess(expression)) return;
      if (checkForImplicitEnumAssigning(expectedType, expression, expression)) return;
      final PsiType rType = expression.getType();
      if (rType == null || rType == PsiType.VOID) return;

      if (!TypesUtil.isAssignable(expectedType, rType, expression)) {
        final LocalQuickFix[] fixes = {new GrCastFix(expectedType)};
        final String message = GroovyBundle.message("cannot.assign", rType.getPresentableText(), expectedType.getPresentableText());
        registerError(expression, message, fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
      }
    }

    private boolean checkForImplicitEnumAssigning(PsiType expectedType, GrExpression expression, GroovyPsiElement element) {
      if (!(expectedType instanceof PsiClassType)) return false;

      if (!GroovyConfigUtils.getInstance().isVersionAtLeast(element, GroovyConfigUtils.GROOVY1_8)) return false;

      final PsiClass resolved = ((PsiClassType)expectedType).resolve();
      if (resolved == null || !resolved.isEnum()) return false;

      final PsiType type = expression.getType();
      if (type == null) return false;

      if (!type.equalsToText(GroovyCommonClassNames.GROOVY_LANG_GSTRING) && !type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        return false;
      }

      final Object result = GroovyConstantExpressionEvaluator.evaluate(expression);
      if (result == null || !(result instanceof String)) {
        registerError(element, ProblemHighlightType.WEAK_WARNING,
                      GroovyBundle.message("cannot.assign.string.to.enum.0", expectedType.getPresentableText()));
      }
      else {
        final PsiField field = resolved.findFieldByName((String)result, true);
        if (!(field instanceof PsiEnumConstant)) {
          registerError(element, GroovyBundle.message("cannot.find.enum.constant.0.in.enum.1", result, expectedType.getPresentableText()));
        }
      }
      return true;
    }

    @Override
    public void visitMethod(GrMethod method) {
      if (!shouldProcess(method)) return;

      super.visitMethod(method);
      final GrOpenBlock block = method.getBlock();
      if (block == null) return;
      final PsiType expectedType = method.getReturnType();
      if (expectedType == null || PsiType.VOID.equals(expectedType)) return;

      ControlFlowUtils.visitAllExitPoints(block, new ControlFlowUtils.ExitPointVisitor() {
        @Override
        public boolean visitExitPoint(Instruction instruction, @Nullable GrExpression returnValue) {
          if (returnValue != null &&
              !isNewInstanceInitialingByTuple(returnValue)) {
            checkAssignability(expectedType, returnValue);
          }
          return true;
        }
      });
    }

    protected boolean shouldProcess(GrMethod method) {
      return !GroovyPsiManager.getInstance(method.getProject()).isCompileStatic(method);
    }

    @Override
    public void visitField(GrField field) {
      if (GroovyPsiManager.getInstance(field.getProject()).isCompileStatic(field)) return;
      super.visitField(field);
    }

    @Override
    public void visitTypeDefinition(GrTypeDefinition typeDefinition) {
      if (GroovyPsiManager.getInstance(typeDefinition.getProject()).isCompileStatic(typeDefinition)) return;
      super.visitTypeDefinition(typeDefinition);
    }

    @Override
    public void visitAssignmentExpression(GrAssignmentExpression assignment) {
      super.visitAssignmentExpression(assignment);

      GrExpression lValue = assignment.getLValue();
      if (lValue instanceof GrIndexProperty) return;
      if (!PsiUtil.mightBeLValue(lValue)) return;

      IElementType opToken = assignment.getOperationToken();
      if (opToken != GroovyTokenTypes.mASSIGN) return;

      GrExpression rValue = assignment.getRValue();
      if (rValue == null) return;

      PsiType lType = lValue.getNominalType();
      PsiType rType = rValue.getType();
      // For assignments with spread dot
      if (isListAssignment(lValue) && lType != null && lType instanceof PsiClassType) {
        final PsiClassType pct = (PsiClassType)lType;
        final PsiClass clazz = pct.resolve();
        if (clazz != null && CommonClassNames.JAVA_UTIL_LIST.equals(clazz.getQualifiedName())) {
          final PsiType[] types = pct.getParameters();
          if (types.length == 1 && types[0] != null && rType != null) {
            checkAssignability(types[0], rValue);
          }
        }
        return;
      }
      if (lValue instanceof GrReferenceExpression && ((GrReferenceExpression)lValue).resolve() instanceof GrReferenceExpression) {
        //lvalue is not-declared variable
        return;
      }

      if (isNewInstanceInitialingByTuple(rValue)) {
        // new instance initializing e.g.: X x; x = [1, 2]
        return;
      }

      if (lType != null && rType != null) {
        checkAssignability(lType, rValue);
      }
    }

    @Override
    public void visitVariable(GrVariable variable) {
      super.visitVariable(variable);

      PsiType varType = variable.getType();
      GrExpression initializer = variable.getInitializerGroovy();
      if (initializer == null) return;

      PsiType rType = initializer.getType();
      if (rType == null) return;
      if (isNewInstanceInitialingByTuple(initializer)) {
        return;
      }

      checkAssignability(varType, initializer);
    }

    private static boolean isNewInstanceInitialingByTuple(GrExpression initializer) {
      return initializer instanceof GrListOrMap && initializer.getReference() instanceof LiteralConstructorReference;
    }

    @Override
    public void visitNewExpression(GrNewExpression newExpression) {
      super.visitNewExpression(newExpression);
      if (newExpression.getArrayCount() > 0) return;

      GrCodeReferenceElement refElement = newExpression.getReferenceElement();
      if (refElement == null) return;

      checkConstructorCall(newExpression, refElement);
    }

    private void checkConstructorCall(GrConstructorCall constructorCall, GroovyPsiElement refElement) {
      final GrArgumentList argList = constructorCall.getArgumentList();
      if (!checkCannotInferArgumentTypes(refElement)) return;
      final GroovyResolveResult constructorResolveResult = constructorCall.advancedResolve();
      final PsiElement constructor = constructorResolveResult.getElement();

      if (constructor != null) {
        if (!checkConstructorApplicability(constructorResolveResult, refElement, true)) return;
      }
      else {
        final GroovyResolveResult[] results = constructorCall.multiResolve(false);
        if (results.length > 0) {
          for (GroovyResolveResult result : results) {
            PsiElement resolved = result.getElement();
            if (resolved instanceof PsiMethod) {
              if (!checkConstructorApplicability(result, refElement, false)) return;
            }
          }
          registerError(getElementToHighlight(refElement, argList), GroovyBundle.message("constructor.call.is.ambiguous"));
        }
        else {
          final GrExpression[] expressionArguments = constructorCall.getExpressionArguments();
          final GrClosableBlock[] closureArguments = constructorCall.getClosureArguments();
          final GrNamedArgument[] namedArgs = constructorCall.getNamedArguments();
          if (closureArguments.length > 0 ||
              namedArgs.length > 0 && expressionArguments.length > 0 ||
              namedArgs.length == 0 && expressionArguments.length > 0 && !isOnlyOneMapParam(expressionArguments)) {
            final GroovyResolveResult[] resolveResults = constructorCall.multiResolveClass();
            if (resolveResults.length == 1) {
              final PsiElement element = resolveResults[0].getElement();
              if (element instanceof PsiClass) {
                registerError(getElementToHighlight(refElement, argList),
                              GroovyBundle.message("cannot.apply.default.constructor", ((PsiClass)element).getName()));
                return;
              }
            }
          }
        }
      }

      checkNamedArgumentsType(constructorCall);
    }

    private static boolean isOnlyOneMapParam(GrExpression[] exprs) {
      if (!(exprs.length == 1)) return false;

      final GrExpression e = exprs[0];
      return TypesUtil.isAssignable(TypesUtil.createTypeByFQClassName(CommonClassNames.JAVA_UTIL_MAP, e), e.getType(), e.getManager(),
                                    e.getResolveScope());
    }

    private static PsiElement getElementToHighlight(PsiElement refElement, GrArgumentList argList) {
      PsiElement elementToHighlight = argList;
      if (elementToHighlight == null || elementToHighlight.getTextLength() == 0) elementToHighlight = refElement;
      return elementToHighlight;
    }

    @Override
    public void visitListOrMap(GrListOrMap listOrMap) {
      super.visitListOrMap(listOrMap);

      final PsiReference reference = listOrMap.getReference();
      if (!(reference instanceof LiteralConstructorReference)) return;

      final GroovyResolveResult[] results = ((LiteralConstructorReference)reference).multiResolve(false);
      if (results.length == 0) return;

      if (results.length == 1) {
        final GroovyResolveResult result = results[0];
        final PsiElement element = result.getElement();
        if (element instanceof PsiClass) {
          if (!listOrMap.isMap()) {
            registerError(listOrMap, GroovyBundle.message("cannot.apply.default.constructor", ((PsiClass)element).getName()));
          }
        }
        else if (element instanceof PsiMethod && ((PsiMethod)element).isConstructor()) {
          checkLiteralConstructorApplicability(result, listOrMap, true);
        }
      }
      else {
        for (GroovyResolveResult result : results) {
          PsiElement resolved = result.getElement();
          if (resolved instanceof PsiMethod) {
            if (!checkLiteralConstructorApplicability(result, listOrMap, false)) return;
          }
          registerError(listOrMap, GroovyBundle.message("constructor.call.is.ambiguous"));
        }
      }
    }

    @Override
    public void visitThrowStatement(GrThrowStatement throwStatement) {
      super.visitThrowStatement(throwStatement);

      final GrExpression exception = throwStatement.getException();
      if (exception != null) {
        checkAssignability(PsiType.getJavaLangThrowable(throwStatement.getManager(), throwStatement.getResolveScope()), exception
        );
      }
    }

    private boolean checkLiteralConstructorApplicability(GroovyResolveResult result, GrListOrMap listOrMap, boolean checkUnknownArgs) {
      final PsiElement element = result.getElement();
      LOG.assertTrue(element instanceof PsiMethod && ((PsiMethod)element).isConstructor());
      final PsiMethod constructor = (PsiMethod)element;

      final GrExpression[] exprArgs;
      final GrNamedArgument[] namedArgs;
      if (listOrMap.isMap()) {
        exprArgs = GrExpression.EMPTY_ARRAY;
        namedArgs = listOrMap.getNamedArguments();
      }
      else {
        exprArgs = listOrMap.getInitializers();
        namedArgs = GrNamedArgument.EMPTY_ARRAY;
      }

      if (exprArgs.length == 0 && !PsiUtil.isConstructorHasRequiredParameters(constructor)) return true;

      PsiType[] argumentTypes = PsiUtil.getArgumentTypes(namedArgs, exprArgs, GrClosableBlock.EMPTY_ARRAY, false, null);
      if (listOrMap.isMap() && namedArgs.length == 0) {
        argumentTypes = new PsiType[]{listOrMap.getType()};
      }

      GrClosureSignatureUtil.ApplicabilityResult applicable =
        PsiUtil.isApplicableConcrete(argumentTypes, constructor, result.getSubstitutor(), listOrMap, false);
      switch (applicable) {
        case inapplicable:
          highlightInapplicableMethodUsage(result, listOrMap, constructor, argumentTypes);
          return false;
        case canBeApplicable:
          if (checkUnknownArgs) {
            highlightUnknownArgs(listOrMap);
          }
          return !checkUnknownArgs;
        default:
          return true;
      }
    }

    private boolean checkConstructorApplicability(GroovyResolveResult constructorResolveResult,
                                                  GroovyPsiElement place,
                                                  boolean checkUnknownArgs) {
      final PsiElement element = constructorResolveResult.getElement();
      LOG.assertTrue(element instanceof PsiMethod && ((PsiMethod)element).isConstructor());
      final PsiMethod constructor = (PsiMethod)element;

      final GrArgumentList argList = PsiUtil.getArgumentsList(place);
      if (argList != null) {
        final GrExpression[] exprArgs = argList.getExpressionArguments();

        if (exprArgs.length == 0 && !PsiUtil.isConstructorHasRequiredParameters(constructor)) return true;
      }
      return checkMethodApplicability(constructorResolveResult, place, checkUnknownArgs);
    }

    @Override
    public void visitConstructorInvocation(GrConstructorInvocation invocation) {
      super.visitConstructorInvocation(invocation);
      checkConstructorCall(invocation, invocation.getThisOrSuperKeyword());
    }

    @Override
    public void visitReferenceExpression(GrReferenceExpression referenceExpression) {
      super.visitReferenceExpression(referenceExpression);

      final PsiElement parent = referenceExpression.getParent();
      if (parent instanceof GrCall) {
        GrCall call = (GrCall)parent;
        GroovyResolveResult resolveResult = call.advancedResolve();
        GroovyResolveResult[] results = call.multiResolve(false); //cached

        PsiElement resolved = resolveResult.getElement();
        if (resolved == null) {
          GrExpression qualifier = referenceExpression.getQualifierExpression();
          if (qualifier == null && GroovyAnnotator.isDeclarationAssignment(referenceExpression)) return;
        }

        if (!checkCannotInferArgumentTypes(referenceExpression)) return;

        final PsiType type = referenceExpression.getType();
        if (resolved != null) {
          if (resolved instanceof PsiMethod && !resolveResult.isInvokedOnProperty()) {
            checkMethodApplicability(resolveResult, referenceExpression, true);
          }
          else {
            checkCallApplicability(type, referenceExpression, true);
          }
        }
        else if (results.length > 0) {
          for (GroovyResolveResult result : results) {
            resolved = result.getElement();
            if (resolved instanceof PsiMethod && !resolveResult.isInvokedOnProperty()) {
              if (!checkMethodApplicability(result, referenceExpression, false)) return;
            }
            else {
              if (!checkCallApplicability(type, referenceExpression, false)) return;
            }
          }

          registerError(getElementToHighlight(referenceExpression, PsiUtil.getArgumentsList(referenceExpression)),
                        GroovyBundle.message("method.call.is.ambiguous"));
        }
      }
    }

    private boolean checkCannotInferArgumentTypes(PsiElement referenceExpression) {
      if (PsiUtil.getArgumentTypes(referenceExpression, true) != null) return true;

      registerError(getElementToHighlight(referenceExpression, PsiUtil.getArgumentsList(referenceExpression)),
                    GroovyBundle.message("cannot.infer.argument.types"), LocalQuickFix.EMPTY_ARRAY, ProblemHighlightType.WEAK_WARNING);
      return false;
    }

    @Override
    public void visitMethodCallExpression(GrMethodCallExpression methodCallExpression) {
      super.visitMethodCallExpression(methodCallExpression);
      checkMethodCall(methodCallExpression);
    }

    @Override
    public void visitApplicationStatement(GrApplicationStatement applicationStatement) {
      super.visitApplicationStatement(applicationStatement);
      checkMethodCall(applicationStatement);
    }

    @Override
    public void visitEnumConstant(GrEnumConstant enumConstant) {
      super.visitEnumConstant(enumConstant);
      checkConstructorCall(enumConstant, enumConstant);
    }

    private void checkNamedArgumentsType(GrCall call) {
      GrNamedArgument[] namedArguments = PsiUtil.getFirstMapNamedArguments(call);

      if (namedArguments.length == 0) return;

      Map<String, NamedArgumentDescriptor> map = GroovyNamedArgumentProvider.getNamedArgumentsFromAllProviders(call, null, false);
      if (map == null) return;

      for (GrNamedArgument namedArgument : namedArguments) {
        String labelName = namedArgument.getLabelName();

        NamedArgumentDescriptor descriptor = map.get(labelName);

        if (descriptor == null) continue;

        GrExpression namedArgumentExpression = namedArgument.getExpression();
        if (namedArgumentExpression == null) continue;

        if (PsiUtil.isRawClassMemberAccess(namedArgumentExpression)) continue;

        PsiType expressionType = namedArgumentExpression.getType();
        if (expressionType == null) continue;

        expressionType = TypesUtil.boxPrimitiveType(expressionType, call.getManager(), call.getResolveScope());

        if (!descriptor.checkType(expressionType, call)) {
          registerError(namedArgumentExpression,
                        "Type of argument '" + labelName + "' can not be '" + expressionType.getPresentableText() + "'");
        }
      }
    }

    private void checkMethodCall(GrMethodCall call) {
      final GrExpression expression = call.getInvokedExpression();
      if (!(expression instanceof GrReferenceExpression)) { //it checks in visitRefExpr(...)
        final PsiType type = expression.getType();
        checkCallApplicability(type, expression, true);
      }

      checkNamedArgumentsType(call);
    }

    private void highlightInapplicableMethodUsage(GroovyResolveResult methodResolveResult,
                                                  GroovyPsiElement place,
                                                  PsiMethod method,
                                                  PsiType[] argumentTypes) {
      final PsiClass containingClass =
        method instanceof GrGdkMethod ? ((GrGdkMethod)method).getStaticMethod().getContainingClass() : method.getContainingClass();

      if (containingClass == null) {
        registerCannotApplyError(place, argumentTypes, method.getName());
        return;
      }
      final String typesString = buildArgTypesList(argumentTypes);
      final PsiElementFactory factory = JavaPsiFacade.getInstance(method.getProject()).getElementFactory();
      final PsiClassType containingType = factory.createType(containingClass, methodResolveResult.getSubstitutor());
      final String canonicalText = containingType.getInternalCanonicalText();
      String message;
      if (method.isConstructor()) {
        message = GroovyBundle.message("cannot.apply.constructor", method.getName(), canonicalText, typesString);
      }
      else {
        message = GroovyBundle.message("cannot.apply.method1", method.getName(), canonicalText, typesString);
      }

      registerError(getElementToHighlight(place, PsiUtil.getArgumentsList(place)), message,
                    genCastFixes(GrClosureSignatureUtil.createSignature(methodResolveResult), argumentTypes, place),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
    }

    private static LocalQuickFix[] genCastFixes(GrSignature signature, PsiType[] argumentTypes, GroovyPsiElement context) {

      final List<GrClosureSignature> signatures = GrClosureSignatureUtil.generateSimpleSignature(signature);

      List<Pair<Integer, PsiType>> errors = new ArrayList<Pair<Integer, PsiType>>();
      for (GrClosureSignature closureSignature : signatures) {
        final GrClosureSignatureUtil.MapResultWithError<PsiType> map =
          GrClosureSignatureUtil.mapSimpleSignatureWithErrors(closureSignature, argumentTypes, Function.ID, context, 1);
        if (map != null) {
          errors.addAll(map.getErrors());
        }
      }

      final ArrayList<LocalQuickFix> fixes = new ArrayList<LocalQuickFix>();
      for (Pair<Integer, PsiType> error : errors) {
        fixes.add(new ParameterCastFix(error.first, error.second));
      }

      return fixes.toArray(new LocalQuickFix[fixes.size()]);
    }

    private boolean checkCallApplicability(PsiType type, GroovyPsiElement invokedExpr, boolean checkUnknownArgs) {

      PsiType[] argumentTypes = PsiUtil.getArgumentTypes(invokedExpr, true);
      if (type instanceof GrClosureType) {
        if (argumentTypes == null) return true;

        GrClosureSignatureUtil.ApplicabilityResult result = PsiUtil.isApplicableConcrete(argumentTypes, (GrClosureType)type, invokedExpr);
        switch (result) {
          case inapplicable:
            registerCannotApplyError(invokedExpr, argumentTypes, invokedExpr.getText());
            return false;
          case canBeApplicable:
            if (checkUnknownArgs) {
              highlightUnknownArgs(invokedExpr);
            }
            return !checkUnknownArgs;
          default:
            return true;
        }
      }
      else if (type != null) {
        final GroovyResolveResult[] calls = ResolveUtil.getMethodCandidates(type, "call", invokedExpr, argumentTypes);
        for (GroovyResolveResult result : calls) {
          PsiElement resolved = result.getElement();
          if (resolved instanceof PsiMethod && !result.isInvokedOnProperty()) {
            if (!checkMethodApplicability(result, invokedExpr, checkUnknownArgs && calls.length == 1)) return false;
          }
          else if (resolved instanceof PsiField) {
            if (!checkCallApplicability(((PsiField)resolved).getType(), invokedExpr, checkUnknownArgs && calls.length == 1)) return false;
          }
        }
        if (calls.length == 0 && !(invokedExpr instanceof GrString)) {
          registerCannotApplyError(invokedExpr, argumentTypes, invokedExpr.getText());
        }
        return true;
      }
      return true;
    }

    private void registerCannotApplyError(PsiElement place, PsiType[] argumentTypes, String invokedText) {
      final String typesString = buildArgTypesList(argumentTypes);
      String message = GroovyBundle.message("cannot.apply.method.or.closure", invokedText, typesString);
      PsiElement elementToHighlight = PsiUtil.getArgumentsList(place);
      if (elementToHighlight == null || elementToHighlight.getTextRange().getLength() == 0) elementToHighlight = place;
      registerError(elementToHighlight, message);
    }

    private static String buildArgTypesList(PsiType[] argTypes) {
      StringBuilder builder = new StringBuilder();
      builder.append("(");
      for (int i = 0; i < argTypes.length; i++) {
        if (i > 0) {
          builder.append(", ");
        }
        PsiType argType = argTypes[i];
        builder.append(argType != null ? argType.getInternalCanonicalText() : "?");
      }
      builder.append(")");
      return builder.toString();
    }

    private boolean checkMethodApplicability(GroovyResolveResult methodResolveResult, GroovyPsiElement place, boolean checkUnknownArgs) {
      final PsiElement element = methodResolveResult.getElement();
      if (!(element instanceof PsiMethod)) return true;
      if (element instanceof GrBuilderMethod) return true;

      final PsiMethod method = (PsiMethod)element;
      PsiType[] argumentTypes = PsiUtil.getArgumentTypes(place, true);
      if ("call".equals(method.getName()) && place instanceof GrReferenceExpression) {
        final GrExpression qualifierExpression = ((GrReferenceExpression)place).getQualifierExpression();
        if (qualifierExpression != null) {
          final PsiType type = qualifierExpression.getType();
          if (type instanceof GrClosureType) {
            GrClosureSignatureUtil.ApplicabilityResult result = PsiUtil.isApplicableConcrete(argumentTypes, (GrClosureType)type, place);
            switch (result) {
              case inapplicable:
                highlightInapplicableMethodUsage(methodResolveResult, place, method, argumentTypes);
                return false;
              case canBeApplicable:
                if (checkUnknownArgs) {
                  highlightUnknownArgs(place);
                }
                return !checkUnknownArgs;
              default:
                return true;
            }
          }
        }
      }

      if (method instanceof GrGdkMethod && place instanceof GrReferenceExpression) {
        final PsiMethod staticMethod = ((GrGdkMethod)method).getStaticMethod();
        final PsiType qualifier = inferQualifierTypeByPlace((GrReferenceExpression)place);
        if (qualifier != null && !MixinMemberContributor.isCategoryMethod(staticMethod, qualifier, methodResolveResult.getSubstitutor())) {
          registerError(((GrReferenceExpression)place).getReferenceNameElement(),
                        GroovyInspectionBundle.message("category,method.0.cannot.be.applied.to.1", method.getName(),
                                                       qualifier.getCanonicalText()));
          return false;
        }
      }

      if (argumentTypes == null) return true;

      GrClosureSignatureUtil.ApplicabilityResult applicable = PsiUtil.isApplicableConcrete(argumentTypes, method, methodResolveResult.getSubstitutor(), place, false);
      switch (applicable) {
        case inapplicable:
          //check for implicit use of property getter which returns closure
          if (GroovyPropertyUtils.isSimplePropertyGetter(method)) {
            if (method instanceof GrMethod || method instanceof GrAccessorMethod) {
              final PsiType returnType = PsiUtil.getSmartReturnType(method);
              if (returnType instanceof GrClosureType) {
                if (PsiUtil.isApplicable(argumentTypes, ((GrClosureType)returnType), place)) {
                  return true;
                }
              }
            }

            PsiType returnType = method.getReturnType();
            if (returnType != null) {
              if (TypesUtil.isAssignable(TypesUtil.createType(GroovyCommonClassNames.GROOVY_LANG_CLOSURE, element), returnType, place)) {
                return true;
              }
            }
          }

          highlightInapplicableMethodUsage(methodResolveResult, place, method, argumentTypes);
          return false;
        case canBeApplicable:
          if (checkUnknownArgs) {
            highlightUnknownArgs(place);
          }
          return !checkUnknownArgs;
        default:
          return true;
      }
    }

    private void highlightUnknownArgs(GroovyPsiElement place) {
      registerError(getElementToHighlight(place, PsiUtil.getArgumentsList(place)), GroovyBundle.message("cannot.infer.argument.types"),
                    LocalQuickFix.EMPTY_ARRAY, ProblemHighlightType.WEAK_WARNING);
    }
  }

  private static boolean isListAssignment(GrExpression lValue) {
    if (lValue instanceof GrReferenceExpression) {
      GrReferenceExpression expression = (GrReferenceExpression)lValue;
      final PsiElement dot = expression.getDotToken();
      //noinspection ConstantConditions
      if (dot != null && dot.getNode().getElementType() == GroovyTokenTypes.mSPREAD_DOT) {
        return true;
      }
      else {
        final GrExpression qualifier = expression.getQualifierExpression();
        if (qualifier != null) return isListAssignment(qualifier);
      }
    }
    return false;
  }

  @Nullable
  private static PsiType inferQualifierTypeByPlace(GrReferenceExpression place) {
    if (place.getParent() instanceof GrIndexProperty) {
      return place.getType();
    }
    return GrReferenceResolveUtil.getQualifierType(place);
  }


  private static class AnnotatingVisitor extends MyVisitor {
    private AnnotationHolder myHolder;

    @Override
    protected boolean shouldProcess(GrMethod method) {
      return true;
    }

    @Override
    protected void registerError(@NotNull final PsiElement location,
                                 final String description,
                                 final LocalQuickFix[] fixes,
                                 final ProblemHighlightType highlightType) {
      Annotation annotation = myHolder.createErrorAnnotation(location, description);
      for (final LocalQuickFix fix : fixes) {
        annotation.registerFix(new IntentionAction() {
          @NotNull
          @Override
          public String getText() {
            return fix.getName();
          }

          @NotNull
          @Override
          public String getFamilyName() {
            return fix.getFamilyName();
          }

          @Override
          public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
            return true;
          }

          @Override
          public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
            InspectionManager manager = InspectionManager.getInstance(project);
            ProblemDescriptor descriptor = manager.createProblemDescriptor(location, description, fixes, highlightType, fixes.length == 1, false);
            fix.applyFix(project, descriptor);
          }

          @Override
          public boolean startInWriteAction() {
            return true;
          }
        });
      }
    }

    protected void registerError(@NotNull PsiElement location,
                                 ProblemHighlightType highlightType,
                                 Object... args) {
      registerError(location, (String)args[0], LocalQuickFix.EMPTY_ARRAY, highlightType);
    }


    @Override
    public void visitElement(GroovyPsiElement element) {
      //do nothing
    }
  }

  private static final ThreadLocal<AnnotatingVisitor> visitor = new ThreadLocal<AnnotatingVisitor>() {
    @Override
    protected AnnotatingVisitor initialValue() {
      return new AnnotatingVisitor();
    }
  };

  public static void checkElement(GroovyPsiElement e, AnnotationHolder holder) {
    AnnotatingVisitor annotatingVisitor = visitor.get();

    AnnotationHolder oldHolder = annotatingVisitor.myHolder;
    try {
      annotatingVisitor.myHolder = holder;
      e.accept(annotatingVisitor);
    }
    finally {
      annotatingVisitor.myHolder = oldHolder;
    }
  }
}
