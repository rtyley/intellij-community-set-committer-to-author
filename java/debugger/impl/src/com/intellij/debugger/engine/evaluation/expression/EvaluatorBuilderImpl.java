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
 * Class EvaluatorBuilderImpl
 * @author Jeka
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.ContextUtil;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.JVMName;
import com.intellij.debugger.engine.JVMNameUtil;
import com.intellij.debugger.engine.evaluation.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EvaluatorBuilderImpl implements EvaluatorBuilder {
  private static final EvaluatorBuilderImpl ourInstance = new EvaluatorBuilderImpl();

  private EvaluatorBuilderImpl() {
  }

  public static EvaluatorBuilder getInstance() {
    return ourInstance;
  }

  public ExpressionEvaluator build(final TextWithImports text, final PsiElement contextElement, final SourcePosition position) throws EvaluateException {
    if (contextElement == null) {
      throw EvaluateExceptionUtil.CANNOT_FIND_SOURCE_CLASS;
    }

    final Project project = contextElement.getProject();

    PsiCodeFragment codeFragment = DefaultCodeFragmentFactory.getInstance().createCodeFragment(text, contextElement, project);
    if(codeFragment == null) {
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.invalid.expression", text.getText()));
    }
    codeFragment.forceResolveScope(GlobalSearchScope.allScope(project));
    DebuggerUtils.checkSyntax(codeFragment);

    return build(codeFragment, position);
  }

  public ExpressionEvaluator build(final PsiElement codeFragment, final SourcePosition position) throws EvaluateException {
    return new Builder(position).buildElement(codeFragment);
  }

  private static class Builder extends JavaElementVisitor {
    private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilderImpl");
    private Evaluator myResult = null;
    private PsiClass myContextPsiClass;
    private CodeFragmentEvaluator myCurrentFragmentEvaluator;
    private final Set<JavaCodeFragment> myVisitedFragments = new HashSet<JavaCodeFragment>();
    @Nullable
    private final SourcePosition myPosition;

    private Builder(@Nullable SourcePosition position) {
      myPosition = position;
    }

    @Override public void visitCodeFragment(JavaCodeFragment codeFragment) {
      myVisitedFragments.add(codeFragment);
      ArrayList<Evaluator> evaluators = new ArrayList<Evaluator>();

      CodeFragmentEvaluator oldFragmentEvaluator = myCurrentFragmentEvaluator;
      myCurrentFragmentEvaluator = new CodeFragmentEvaluator(oldFragmentEvaluator);

      for (PsiElement child = codeFragment.getFirstChild(); child != null; child = child.getNextSibling()) {
        child.accept(this);
        if(myResult != null) {
          evaluators.add(myResult);
        }
        myResult = null;
      }

      myCurrentFragmentEvaluator.setStatements(evaluators.toArray(new Evaluator[evaluators.size()]));
      myResult = myCurrentFragmentEvaluator;

      myCurrentFragmentEvaluator = oldFragmentEvaluator;
    }

    @Override public void visitErrorElement(PsiErrorElement element) {
      throw new EvaluateRuntimeException(EvaluateExceptionUtil
        .createEvaluateException(DebuggerBundle.message("evaluation.error.invalid.expression", element.getText())));
    }

    @Override public void visitAssignmentExpression(PsiAssignmentExpression expression) {
      PsiExpression rExpression = expression.getRExpression();
      if(rExpression == null) throw new EvaluateRuntimeException(EvaluateExceptionUtil
        .createEvaluateException(DebuggerBundle.message("evaluation.error.invalid.expression", expression.getText())));

      rExpression.accept(this);
      Evaluator rEvaluator = myResult;

      if(expression.getOperationSign().getTokenType() != JavaTokenType.EQ) {
        throw new EvaluateRuntimeException(EvaluateExceptionUtil.createEvaluateException(
          DebuggerBundle.message("evaluation.error.operation.not.supported", expression.getOperationSign().getText())));
      }

      PsiExpression lExpression = expression.getLExpression();

      if(lExpression.getType() == null) {
        throw new EvaluateRuntimeException(EvaluateExceptionUtil
          .createEvaluateException(DebuggerBundle.message("evaluation.error.unknown.expression.type", lExpression.getText())));
      }

      if(!TypeConversionUtil.areTypesAssignmentCompatible(lExpression.getType(), rExpression)) {
        throw new EvaluateRuntimeException(EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.incompatible.types", expression.getOperationSign().getText())));
      }
      lExpression.accept(this);
      Evaluator lEvaluator = myResult;

      myResult = new AssignmentEvaluator(lEvaluator, rEvaluator);
    }

    @Override public void visitStatement(PsiStatement statement) {
      throw new EvaluateRuntimeException(EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.statement.not.supported", statement.getText())));
    }

    @Override public void visitBlockStatement(PsiBlockStatement statement) {
      PsiStatement[] statements = statement.getCodeBlock().getStatements();
      Evaluator [] evaluators = new Evaluator[statements.length];
      for (int i = 0; i < statements.length; i++) {
        PsiStatement psiStatement = statements[i];
        psiStatement.accept(this);
        evaluators[i] = myResult;
        myResult = null;
      }
      myResult = new BlockStatementEvaluator(evaluators);
    }

    @Override public void visitWhileStatement(PsiWhileStatement statement) {
      PsiStatement body = statement.getBody();
      if(body == null) return;
      body.accept(this);
      Evaluator bodyEvaluator = myResult;

      PsiExpression condition = statement.getCondition();
      if(condition == null) return;
      condition.accept(this);
      String label = null;
      if(statement.getParent() instanceof PsiLabeledStatement) {
        label = ((PsiLabeledStatement)statement.getParent()).getLabelIdentifier().getText();
      }
      myResult = new WhileStatementEvaluator(myResult, bodyEvaluator, label);
    }

    @Override public void visitForStatement(PsiForStatement statement) {
      PsiStatement initializer = statement.getInitialization();
      Evaluator initializerEvaluator = null;
      if(initializer != null){
        initializer.accept(this);
        initializerEvaluator = myResult;
      }

      PsiExpression condition = statement.getCondition();
      Evaluator conditionEvaluator = null;
      if(condition != null) {
        condition.accept(this);
        conditionEvaluator = myResult;
      }

      PsiStatement update = statement.getUpdate();
      Evaluator updateEvaluator = null;
      if(update != null){
        update.accept(this);
        updateEvaluator = myResult;
      }

      PsiStatement body = statement.getBody();
      if(body == null) return;
      body.accept(this);
      Evaluator bodyEvaluator = myResult;

      String label = null;
      if(statement.getParent() instanceof PsiLabeledStatement) {
        label = ((PsiLabeledStatement)statement.getParent()).getLabelIdentifier().getText();
      }
      myResult = new ForStatementEvaluator(initializerEvaluator, conditionEvaluator, updateEvaluator, bodyEvaluator, label);
    }

    @Override public void visitIfStatement(PsiIfStatement statement) {
      PsiStatement thenBranch = statement.getThenBranch();
      if(thenBranch == null) return;
      thenBranch.accept(this);
      Evaluator thenEvaluator = myResult;

      PsiStatement elseBranch = statement.getElseBranch();
      Evaluator elseEvaluator = null;
      if(elseBranch != null){
        elseBranch.accept(this);
        elseEvaluator = myResult;
      }

      PsiExpression condition = statement.getCondition();
      if(condition == null) return;
      condition.accept(this);

      myResult = new IfStatementEvaluator(myResult, thenEvaluator, elseEvaluator);
    }

    @Override public void visitBreakStatement(PsiBreakStatement statement) {
      PsiIdentifier labelIdentifier = statement.getLabelIdentifier();
      myResult = BreakContinueStatementEvaluator.createBreakEvaluator(labelIdentifier != null ? labelIdentifier.getText() : null);
    }

    @Override public void visitContinueStatement(PsiContinueStatement statement) {
      PsiIdentifier labelIdentifier = statement.getLabelIdentifier();
      myResult = BreakContinueStatementEvaluator.createContinueEvaluator(labelIdentifier != null ? labelIdentifier.getText() : null);
    }

    @Override public void visitExpressionStatement(PsiExpressionStatement statement) {
      statement.getExpression().accept(this);
    }

    @Override public void visitExpression(PsiExpression expression) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("visitExpression " + expression);
      }
    }

    @Override public void visitBinaryExpression(PsiBinaryExpression expression) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("visitBinaryExpression " + expression);
      }
      expression.getLOperand().accept(this);
      Evaluator lResult = myResult;
      PsiExpression rOperand = expression.getROperand();
      if(rOperand == null) {
        throw new EvaluateRuntimeException(EvaluateExceptionUtil
          .createEvaluateException(DebuggerBundle.message("evaluation.error.invalid.expression", expression.getText())));
      }
      rOperand.accept(this);
      IElementType opType = expression.getOperationSign().getTokenType();
      PsiType type = expression.getType();
      if (type == null) {
        throw new EvaluateRuntimeException(EvaluateExceptionUtil
          .createEvaluateException(DebuggerBundle.message("evaluation.error.unknown.expression.type", expression.getText())));
      }
      myResult = new BinaryExpressionEvaluator(lResult, myResult, opType, type.getCanonicalText());
    }

    @Override public void visitDeclarationStatement(PsiDeclarationStatement statement) {
      List<Evaluator> evaluators = new ArrayList<Evaluator>();

      PsiElement[] declaredElements = statement.getDeclaredElements();
      for (PsiElement declaredElement : declaredElements) {
        if (declaredElement instanceof PsiLocalVariable) {
          if (myCurrentFragmentEvaluator != null) {
            PsiLocalVariable localVariable = ((PsiLocalVariable)declaredElement);

            PsiType type = localVariable.getType();

            PsiElementFactory elementFactory = JavaPsiFacade.getInstance(localVariable.getProject()).getElementFactory();
            try {
              PsiExpression initialValue = elementFactory.createExpressionFromText(PsiTypesUtil.getDefaultValueOfType(type), null);
              Object value = JavaConstantExpressionEvaluator.computeConstantExpression(initialValue, true);
              myCurrentFragmentEvaluator.setInitialValue(localVariable.getName(), value);
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
            catch (EvaluateException e) {
              throw new EvaluateRuntimeException(e);
            }

            PsiExpression initializer = localVariable.getInitializer();
            if (initializer != null) {
              try {
                if (!TypeConversionUtil.areTypesAssignmentCompatible(localVariable.getType(), initializer)) {
                  throw new EvaluateRuntimeException(EvaluateExceptionUtil.createEvaluateException(
                    DebuggerBundle.message("evaluation.error.incompatible.variable.initializer.type", localVariable.getName())));
                }
                initializer.accept(this);
                Evaluator rEvaluator = myResult;

                PsiExpression localVarReference = elementFactory.createExpressionFromText(localVariable.getName(), initializer);

                localVarReference.accept(this);
                Evaluator lEvaluator = myResult;

                evaluators.add(new AssignmentEvaluator(lEvaluator, rEvaluator));
              }
              catch (IncorrectOperationException e) {
                LOG.error(e);
              }
            }
          }
          else {
            throw new EvaluateRuntimeException(new EvaluateException(
              DebuggerBundle.message("evaluation.error.local.variable.declarations.not.supported"), null));
          }
        }
        else {
          throw new EvaluateRuntimeException(new EvaluateException(
            DebuggerBundle.message("evaluation.error.unsupported.declaration", declaredElement.getText()), null));
        }
      }

      if(evaluators.size() > 0) {
        CodeFragmentEvaluator codeFragmentEvaluator = new CodeFragmentEvaluator(myCurrentFragmentEvaluator);
        codeFragmentEvaluator.setStatements(evaluators.toArray(new Evaluator[0]));
        myResult = codeFragmentEvaluator;
      } else {
        myResult = null;
      }
    }

    @Override public void visitConditionalExpression(PsiConditionalExpression expression) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("visitConditionalExpression " + expression);
      }
      final PsiExpression thenExpression = expression.getThenExpression();
      final PsiExpression elseExpression = expression.getElseExpression();
      if (thenExpression == null || elseExpression == null){
        throw new EvaluateRuntimeException(EvaluateExceptionUtil
          .createEvaluateException(DebuggerBundle.message("evaluation.error.invalid.expression", expression.getText())));
      }
      PsiExpression condition = expression.getCondition();
      condition.accept(this);
      if (myResult == null) {
        throw new EvaluateRuntimeException(EvaluateExceptionUtil
          .createEvaluateException(DebuggerBundle.message("evaluation.error.invalid.expression", condition.getText())));
      }
      Evaluator conditionEvaluator = myResult;
      thenExpression.accept(this);
      if (myResult == null) {
        throw new EvaluateRuntimeException(EvaluateExceptionUtil
          .createEvaluateException(DebuggerBundle.message("evaluation.error.invalid.expression", thenExpression.getText())));
      }
      Evaluator thenEvaluator = myResult;
      elseExpression.accept(this);
      if (myResult == null) {
        throw new EvaluateRuntimeException(EvaluateExceptionUtil
          .createEvaluateException(DebuggerBundle.message("evaluation.error.invalid.expression", elseExpression.getText())));
      }
      Evaluator elseEvaluator = myResult;
      myResult = new ConditionalExpressionEvaluator(conditionEvaluator, thenEvaluator, elseEvaluator);
    }

    @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("visitReferenceExpression " + expression);
      }
      PsiExpression qualifier = expression.getQualifierExpression();
      PsiElement element = expression.resolve();

      if (element instanceof PsiLocalVariable || element instanceof PsiParameter) {
        //synthetic variable
        final PsiFile containingFile = element.getContainingFile();
        if(containingFile instanceof PsiCodeFragment && myCurrentFragmentEvaluator != null && myVisitedFragments.contains(((PsiCodeFragment)containingFile))) {
          // psiVariable may live in PsiCodeFragment not only in debugger editors, for example Fabrique has such variables.
          // So treat it as synthetic var only when this code fragment is located in DebuggerEditor,
          // that's why we need to check that containing code fragment is the one we visited
          myResult = new SyntheticVariableEvaluator(myCurrentFragmentEvaluator, ((PsiVariable)element).getName());
          return;
        }
        // local variable
        PsiVariable psiVar = (PsiVariable)element;
        String localName = psiVar.getName();
        PsiClass variableClass = getContainingClass(psiVar);
        if (getContextPsiClass() == null || getContextPsiClass().equals(variableClass)) {
          myResult = new LocalVariableEvaluator(localName, ContextUtil.isJspImplicit(element));
          return;
        }
        // the expression references final var outside the context's class (in some of the outer classes)
        int iterationCount = 0;
        PsiClass aClass = getOuterClass(getContextPsiClass());
        while (aClass != null && !aClass.equals(variableClass)) {
          iterationCount++;
          aClass = getOuterClass(aClass);
        }
        if (aClass != null) {
          if(psiVar.getInitializer() != null) {
            Object value = JavaPsiFacade.getInstance(psiVar.getProject()).getConstantEvaluationHelper().computeConstantExpression(psiVar.getInitializer());
            if(value != null) {
              myResult = new LiteralEvaluator(value, psiVar.getType().getCanonicalText());
              return;
            }
          }
          Evaluator objectEvaluator = new ThisEvaluator(iterationCount);
          //noinspection HardCodedStringLiteral
          final PsiClass classAt = myPosition != null? JVMNameUtil.getClassAt(myPosition) : null;
          FieldEvaluator.TargetClassFilter filter = FieldEvaluator.createClassFilter(classAt != null? classAt : getContextPsiClass());
          myResult = new FieldEvaluator(objectEvaluator, filter, "val$" + localName);
          return;
        }
        throw new EvaluateRuntimeException(EvaluateExceptionUtil.createEvaluateException(
          DebuggerBundle.message("evaluation.error.local.variable.missing.from.class.closure", localName))
        );
      }
      else if (element instanceof PsiField) {
        final PsiField psiField = (PsiField)element;
        final PsiClass fieldClass = psiField.getContainingClass();
        if(fieldClass == null) {
          throw new EvaluateRuntimeException(EvaluateExceptionUtil.createEvaluateException(
            DebuggerBundle.message("evaluation.error.cannot.resolve.field.class", psiField.getName())));
        }
        Evaluator objectEvaluator;
        if (psiField.hasModifierProperty(PsiModifier.STATIC)) {
          objectEvaluator = new TypeEvaluator(JVMNameUtil.getContextClassJVMQualifiedName(SourcePosition.createFromElement(psiField)));
        }
        else if(qualifier != null) {
          qualifier.accept(this);
          objectEvaluator = myResult;
        }
        else if (fieldClass.equals(getContextPsiClass()) || getContextPsiClass().isInheritor(fieldClass, true)) {
            objectEvaluator = new ThisEvaluator();
        }
        else {  // myContextPsiClass != fieldClass && myContextPsiClass is not a subclass of fieldClass
          int iterationCount = 0;
          PsiClass aClass = getContextPsiClass();
          while (aClass != null && !(aClass.equals(fieldClass) || aClass.isInheritor(fieldClass, true))) {
            iterationCount++;
            aClass = getOuterClass(aClass);
          }
          if (aClass == null) {
            throw new EvaluateRuntimeException(EvaluateExceptionUtil.createEvaluateException(
              DebuggerBundle.message("evaluation.error.cannot.sources.for.field.class", psiField.getName())));
          }
          objectEvaluator = new ThisEvaluator(iterationCount);
        }
        myResult = new FieldEvaluator(objectEvaluator, FieldEvaluator.createClassFilter(fieldClass), psiField.getName());
      } else {
        //let's guess what this could be
        PsiElement nameElement = expression.getReferenceNameElement(); // get "b" part
        String name;
        if (nameElement instanceof PsiIdentifier) {
          name = nameElement.getText();
        }
        else {
          //noinspection HardCodedStringLiteral
          final String elementDisplayString = (nameElement != null ? nameElement.getText() : "(null)");
          throw new EvaluateRuntimeException(EvaluateExceptionUtil.createEvaluateException(
            DebuggerBundle.message("evaluation.error.identifier.expected", elementDisplayString)));
        }

        if(qualifier != null) {
          final PsiElement qualifierTarget = (qualifier instanceof PsiReferenceExpression) ? ((PsiReferenceExpression)qualifier).resolve() : null;
          if (qualifierTarget instanceof PsiClass) {
            // this is a call to a 'static' field
            PsiClass psiClass = (PsiClass)qualifierTarget;
            final JVMName typeName = JVMNameUtil.getJVMQualifiedName(psiClass);
            myResult = new FieldEvaluator(new TypeEvaluator(typeName), FieldEvaluator.createClassFilter(psiClass), name);
          }
          else {
            PsiType type = qualifier.getType();
            if(type == null) {
              throw new EvaluateRuntimeException(EvaluateExceptionUtil.createEvaluateException(
                DebuggerBundle.message("evaluation.error.qualifier.type.unknown", qualifier.getText()))
              );
            }

            qualifier.accept(this);
            if (myResult == null) {
              throw new EvaluateRuntimeException(EvaluateExceptionUtil.createEvaluateException(
                DebuggerBundle.message("evaluation.error.cannot.evaluate.qualifier", qualifier.getText()))
              );
            }

            myResult = new FieldEvaluator(myResult, FieldEvaluator.createClassFilter(type), name);
          }
        }
        else {
          myResult = new LocalVariableEvaluator(name, false);
        }
      }
    }

    @Override public void visitSuperExpression(PsiSuperExpression expression) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("visitSuperExpression " + expression);
      }
      final int iterationCount = calcIterationCount(expression.getQualifier());
      myResult = new SuperEvaluator(iterationCount);
    }

    @Override public void visitThisExpression(PsiThisExpression expression) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("visitThisExpression " + expression);
      }
      final int iterationCount = calcIterationCount(expression.getQualifier());
      myResult = new ThisEvaluator(iterationCount);
    }

    private int calcIterationCount(final PsiJavaCodeReferenceElement qualifier) {
      int iterationCount = 0;
      if (qualifier != null) {
        PsiElement targetClass = qualifier.resolve();
        if (targetClass == null || getContextPsiClass() == null) {
          throw new EvaluateRuntimeException(EvaluateExceptionUtil
            .createEvaluateException(DebuggerBundle.message("evaluation.error.invalid.expression", qualifier.getText())));
        }
        try {
          PsiClass aClass = getContextPsiClass();
          while (aClass != null && !aClass.equals(targetClass)) {
            iterationCount++;
            aClass = getOuterClass(aClass);
          }
        }
        catch (Exception e) {
          throw new EvaluateRuntimeException(EvaluateExceptionUtil.createEvaluateException(e));
        }
      }
      return iterationCount;
    }

    @Override public void visitInstanceOfExpression(PsiInstanceOfExpression expression) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("visitInstanceOfExpression " + expression);
      }
      PsiTypeElement checkType = expression.getCheckType();
      if(checkType == null) {
        throw new EvaluateRuntimeException(EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.invalid.expression", expression.getText())));
      }
      PsiType type = checkType.getType();
      expression.getOperand().accept(this);
//    ClassObjectEvaluator typeEvaluator = new ClassObjectEvaluator(type.getCanonicalText());
      Evaluator operandEvaluator = myResult;
      myResult = new InstanceofEvaluator(operandEvaluator, new TypeEvaluator(JVMNameUtil.getJVMQualifiedName(type)));
    }

    @Override public void visitParenthesizedExpression(PsiParenthesizedExpression expression) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("visitParenthesizedExpression " + expression);
      }
      PsiExpression expr = expression.getExpression();
      if (expr != null){
        expr.accept(this);
      }
    }

    @Override public void visitPostfixExpression(PsiPostfixExpression expression) {
      expression.getOperand().accept(this);
      PsiType type = expression.getType();
      if(type == null) {
        throw new EvaluateRuntimeException(EvaluateExceptionUtil
          .createEvaluateException(DebuggerBundle.message("evaluation.error.unknown.expression.type", expression.getText())));
      }
      myResult = new PostfixOperationEvaluator(myResult, expression.getOperationSign().getTokenType(), type.getCanonicalText());
    }

    @Override public void visitPrefixExpression(final PsiPrefixExpression expression) {
      final PsiType type = expression.getType();
      if(type == null) {
        throw new EvaluateRuntimeException(
          EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.unknown.expression.type", expression.getText()))
        );
      }

      final PsiExpression operandExpression = expression.getOperand();
      if (operandExpression == null) {
        throw new EvaluateRuntimeException(
          EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.unknown.expression.operand", expression.getText()))
        );
      }
      
      operandExpression.accept(this);
      final Evaluator operand = myResult;

      final IElementType opType = expression.getOperationSign().getTokenType();

      if(opType == JavaTokenType.PLUSPLUS || opType == JavaTokenType.MINUSMINUS) {
        final boolean isPlus = opType == JavaTokenType.PLUSPLUS;

        try {
          PsiElementFactory elementFactory = JavaPsiFacade.getInstance(expression.getProject()).getElementFactory();
          PsiExpression one = elementFactory.createExpressionFromText("1", null);
          one.accept(this);

          myResult = new AssignmentEvaluator(
            operand,
            new BinaryExpressionEvaluator(operand, myResult, isPlus ? JavaTokenType.PLUS : JavaTokenType.MINUS, type.getCanonicalText())
          );
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
      else {
        myResult = new UnaryExpressionEvaluator(opType, type.getCanonicalText(), operand, expression.getOperationSign().getText());
      }
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("visitMethodCallExpression " + expression);
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] argExpressions = argumentList.getExpressions();
      List<Evaluator> argumentEvaluators = new ArrayList<Evaluator>(argExpressions.length);
      // evaluate arguments
      for (PsiExpression psiExpression : argExpressions) {
        psiExpression.accept(this);
        if (myResult == null) {
          // cannot build evaluator
          throw new EvaluateRuntimeException(
            EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.invalid.expression", psiExpression.getText()))
          );
        }
        argumentEvaluators.add(myResult);
      }
      PsiReferenceExpression methodExpr = expression.getMethodExpression();

      final JavaResolveResult resolveResult = methodExpr.advancedResolve(false);
      final PsiMethod psiMethod = (PsiMethod)resolveResult.getElement();

      PsiExpression qualifier = methodExpr.getQualifierExpression();
      Evaluator objectEvaluator;
      JVMName contextClass = null;

      if(psiMethod != null) {
        PsiClass methodPsiClass = psiMethod.getContainingClass();
        contextClass =  JVMNameUtil.getJVMQualifiedName(methodPsiClass);
        if (psiMethod.hasModifierProperty(PsiModifier.STATIC)) {
          objectEvaluator = new TypeEvaluator(contextClass);
        }
        else if (qualifier != null ) {
          qualifier.accept(this);
          objectEvaluator = myResult;
        }
        else {
          int iterationCount = 0;
          final PsiElement currentFileResolveScope = resolveResult.getCurrentFileResolveScope();
          if (currentFileResolveScope instanceof PsiClass) {
            PsiClass aClass = getContextPsiClass();
            while(aClass != null && !aClass.equals(currentFileResolveScope)) {
              aClass = getOuterClass(aClass);
              iterationCount++;
            }
          }
          objectEvaluator = new ThisEvaluator(iterationCount);
        }
      }
      else {
        //trying to guess
        if (qualifier != null) {
          PsiType type = qualifier.getType();

          if (type != null) {
            contextClass = JVMNameUtil.getJVMQualifiedName(type);
          }

          if (qualifier instanceof PsiReferenceExpression && ((PsiReferenceExpression)qualifier).resolve() instanceof PsiClass) {
            // this is a call to a 'static' method
            if (contextClass == null && type == null) {
              throw new EvaluateRuntimeException(
                EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.qualifier.type.unknown", qualifier.getText()))
              );
            }
            assert contextClass != null;
            objectEvaluator = new TypeEvaluator(contextClass);
          }
          else {
            qualifier.accept(this);
            objectEvaluator = myResult;
          }
        }
        else {
          objectEvaluator = new ThisEvaluator();
          contextClass = JVMNameUtil.getContextClassJVMQualifiedName(myPosition);
          if(contextClass == null && myContextPsiClass != null) {
            contextClass = JVMNameUtil.getJVMQualifiedName(myContextPsiClass);
          }
          //else {
          //  throw new EvaluateRuntimeException(EvaluateExceptionUtil.createEvaluateException(
          //    DebuggerBundle.message("evaluation.error.method.not.found", methodExpr.getReferenceName()))
          //  );
          //}
        }
      }

      if (objectEvaluator == null) {
        throw new EvaluateRuntimeException(EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.invalid.expression", expression.getText())));
      }

      if (psiMethod != null && !psiMethod.isConstructor()) {
        if (psiMethod.getReturnType() == null) {
          throw new EvaluateRuntimeException(EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.unknown.method.return.type", psiMethod.getText())));
        }
      }

      myResult = new MethodEvaluator(objectEvaluator, contextClass, methodExpr.getReferenceName(), psiMethod != null ? JVMNameUtil.getJVMSignature(psiMethod) : null, argumentEvaluators);
    }

    @Override public void visitLiteralExpression(PsiLiteralExpression expression) {
      Object value = expression.getValue();
      if(expression.getParsingError() != null) {
        throw new EvaluateRuntimeException(EvaluateExceptionUtil.createEvaluateException(expression.getParsingError()));
      }
      myResult = new LiteralEvaluator(value, expression.getType().getCanonicalText());
    }

    @Override public void visitArrayAccessExpression(PsiArrayAccessExpression expression) {
      final PsiExpression indexExpression = expression.getIndexExpression();
      if(indexExpression == null) {
        throw new EvaluateRuntimeException(EvaluateExceptionUtil
          .createEvaluateException(DebuggerBundle.message("evaluation.error.invalid.expression", expression.getText())));
      }
      indexExpression.accept(this);
      Evaluator indexEvaluator = myResult;
      expression.getArrayExpression().accept(this);
      Evaluator arrayEvaluator = myResult;
      myResult = new ArrayAccessEvaluator(arrayEvaluator, indexEvaluator);
    }

    @Override public void visitTypeCastExpression(PsiTypeCastExpression expression) {
      expression.getOperand().accept(this);
      PsiType castType = expression.getCastType().getType();
      myResult = new TypeCastEvaluator(myResult, castType.getCanonicalText(), castType instanceof PsiPrimitiveType);
    }

    @Override public void visitClassObjectAccessExpression(PsiClassObjectAccessExpression expression) {
      PsiType type = expression.getOperand().getType();

      if (type instanceof PsiPrimitiveType) {
        final JVMName typeName = JVMNameUtil.getJVMRawText(((PsiPrimitiveType)type).getBoxedTypeName());
        myResult = new FieldEvaluator(new TypeEvaluator(typeName), FieldEvaluator.TargetClassFilter.ALL, "TYPE");
      }
      else {
        myResult = new ClassObjectEvaluator(new TypeEvaluator(JVMNameUtil.getJVMQualifiedName(type)));
      }
    }

    @Override public void visitNewExpression(PsiNewExpression expression) {
      PsiType expressionPsiType = expression.getType();
      if (expressionPsiType instanceof PsiArrayType) {
        Evaluator dimensionEvaluator = null;
        PsiExpression[] dimensions = expression.getArrayDimensions();
        if (dimensions.length == 1){
          PsiExpression dimensionExpression = dimensions[0];
          dimensionExpression.accept(this);
          if (myResult != null) {
            dimensionEvaluator = myResult;
          }
          else {
            throw new EvaluateRuntimeException(EvaluateExceptionUtil.createEvaluateException(
              DebuggerBundle.message("evaluation.error.invalid.array.dimension.expression", dimensionExpression.getText())));
          }
        }
        else if (dimensions.length > 1){
          throw new EvaluateRuntimeException(EvaluateExceptionUtil.createEvaluateException(
            DebuggerBundle.message("evaluation.error.multi.dimensional.arrays.creation.not.supported"))
          );
        }

        Evaluator initializerEvaluator = null;
        PsiArrayInitializerExpression arrayInitializer = expression.getArrayInitializer();
        if (arrayInitializer != null) {
          if (dimensionEvaluator != null) { // initializer already exists
            throw new EvaluateRuntimeException(EvaluateExceptionUtil
              .createEvaluateException(DebuggerBundle.message("evaluation.error.invalid.expression", expression.getText())));
          }
          arrayInitializer.accept(this);
          if (myResult != null) {
            initializerEvaluator = myResult;
          }
          else {
            throw new EvaluateRuntimeException(EvaluateExceptionUtil
              .createEvaluateException(DebuggerBundle.message("evaluation.error.invalid.expression", arrayInitializer.getText())));
          }
          /*
          PsiExpression[] initializers = arrayInitializer.getInitializers();
          initializerEvaluators = new Evaluator[initializers.length];
          for (int idx = 0; idx < initializers.length; idx++) {
            PsiExpression initializer = initializers[idx];
            initializer.accept(this);
            if (myResult instanceof Evaluator) {
              initializerEvaluators[idx] = myResult;
            }
            else {
              throw new EvaluateException("Invalid expression for array initializer: " + initializer.getText(), true);
            }
          }
          */
        }
        if (dimensionEvaluator == null && initializerEvaluator == null) {
          throw new EvaluateRuntimeException(EvaluateExceptionUtil
            .createEvaluateException(DebuggerBundle.message("evaluation.error.invalid.expression", expression.getText())));
        }
        myResult = new NewArrayInstanceEvaluator(
          new TypeEvaluator(JVMNameUtil.getJVMQualifiedName(expressionPsiType)),
          dimensionEvaluator,
          initializerEvaluator
        );
      }
      else { // must be a class ref
        LOG.assertTrue(expressionPsiType instanceof PsiClassType);
        PsiClass aClass = ((PsiClassType)expressionPsiType).resolve();
        if(aClass instanceof PsiAnonymousClass) {
          throw new EvaluateRuntimeException(EvaluateExceptionUtil.createEvaluateException(
            DebuggerBundle.message("evaluation.error.anonymous.class.evaluation.not.supported"))
          );
        }
        PsiExpressionList argumentList = expression.getArgumentList();
        if (argumentList == null) {
          throw new EvaluateRuntimeException(EvaluateExceptionUtil
            .createEvaluateException(DebuggerBundle.message("evaluation.error.invalid.expression", expression.getText())));
        }
        PsiExpression[] argExpressions = argumentList.getExpressions();
        PsiMethod constructor = expression.resolveConstructor();
        if (constructor == null && argExpressions.length > 0) {
          throw new EvaluateRuntimeException(new EvaluateException(
            DebuggerBundle.message("evaluation.error.cannot.resolve.constructor", expression.getText()), null));
        }
        Evaluator[] argumentEvaluators = new Evaluator[argExpressions.length];
        // evaluate arguments
        for (int idx = 0; idx < argExpressions.length; idx++) {
          PsiExpression argExpression = argExpressions[idx];
          argExpression.accept(this);
          if (myResult != null) {
            argumentEvaluators[idx] = myResult;
          }
          else {
            throw new EvaluateRuntimeException(EvaluateExceptionUtil
              .createEvaluateException(DebuggerBundle.message("evaluation.error.invalid.expression", argExpression.getText())));
          }
        }
        //noinspection HardCodedStringLiteral
        JVMName signature = (constructor != null)? JVMNameUtil.getJVMSignature(constructor) : JVMNameUtil.getJVMRawText("()V");
        myResult = new NewClassInstanceEvaluator(
          new TypeEvaluator(JVMNameUtil.getJVMQualifiedName(expressionPsiType)),
          signature,
          argumentEvaluators
        );
      }
    }

    @Override public void visitArrayInitializerExpression(PsiArrayInitializerExpression expression) {
      PsiExpression[] initializers = expression.getInitializers();
      Evaluator[] evaluators = new Evaluator[initializers.length];
      for (int idx = 0; idx < initializers.length; idx++) {
        PsiExpression initializer = initializers[idx];
        initializer.accept(this);
        if (myResult != null) {
          evaluators[idx] = myResult;
        }
        else {
          throw new EvaluateRuntimeException(EvaluateExceptionUtil
            .createEvaluateException(DebuggerBundle.message("evaluation.error.invalid.expression", initializer.getText())));
        }
      }
      myResult = new ArrayInitializerEvaluator(evaluators);
    }

    private PsiClass getOuterClass(PsiClass aClass) {
      if(aClass == null) return null;
      return PsiTreeUtil.getContextOfType(aClass, PsiClass.class, true);
    }

    private PsiClass getContainingClass(PsiVariable variable) {
      PsiElement element = PsiTreeUtil.getParentOfType(variable.getParent(), PsiClass.class, false);
      return element == null ? getContextPsiClass() : (PsiClass)element;
    }

    public PsiClass getContextPsiClass() {
      return myContextPsiClass;
    }

    protected ExpressionEvaluator buildElement(final PsiElement element) throws EvaluateException {
      LOG.assertTrue(element.isValid());

      myContextPsiClass = PsiTreeUtil.getContextOfType(element, PsiClass.class, false);
      try {
        element.accept(this);
      }
      catch (EvaluateRuntimeException e) {
        throw e.getCause();
      }
      if (myResult == null) {
        throw EvaluateExceptionUtil
          .createEvaluateException(DebuggerBundle.message("evaluation.error.invalid.expression", element.toString()));
      }
      return new ExpressionEvaluatorImpl(myResult);
    }
  }
}
