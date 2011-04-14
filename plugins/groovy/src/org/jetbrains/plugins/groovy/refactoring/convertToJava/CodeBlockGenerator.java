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
package org.jetbrains.plugins.groovy.refactoring.convertToJava;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.formatter.GrControlStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrTraditionalForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
public class CodeBlockGenerator extends Generator {

  private final StringBuilder builder;
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.refactoring.convertToJava.CodeBlockGenerator");

  private final ExpressionContext context;

  public CodeBlockGenerator(StringBuilder builder, ExpressionContext context) {
    this.builder = builder;
    this.context = context;
  }

  public CodeBlockGenerator(StringBuilder builder, Project project) {
    this(builder, new ExpressionContext(project));
  }

  @Override
  public StringBuilder getBuilder() {
    return builder;
  }

  @Override
  public ExpressionContext getContext() {
    return context;
  }

  @Override
  public void visitOpenBlock(GrOpenBlock block) {
    builder.append("{\n");
    super.visitOpenBlock(block);
    builder.append("}\n");
  }

  @Override
  public void visitStatement(GrStatement statement) {
    super.visitStatement(statement);    //To change body of overridden methods use File | Settings | File Templates.
  }

  @Override
  public void visitFlowInterruptStatement(GrFlowInterruptingStatement statement) {
    builder.append(statement.getStatementText());
    final String name = statement.getLabelName();
    if (name != null) {
      builder.append(" ").append(name); //todo check incorrect labels
    }
    builder.append(";");
  }

  @Override
  public void visitReturnStatement(GrReturnStatement returnStatement) {
    final GrExpression returnValue = returnStatement.getReturnValue();
    if (returnValue == null) {
      builder.append("return;\n");
      return;
    }

    final ExpressionGenerator expressionGenerator = new ExpressionGenerator(context.project);
    returnValue.accept(expressionGenerator);
    StringBuilder builder = new StringBuilder();
    builder.append("return").append(expressionGenerator.getBuilder()).append(";"); //todo add casts to return type
    writeStatement(builder, returnStatement, expressionGenerator.getContext());
  }

  private void writeStatement(StringBuilder statementBuilder, GrStatement statement) {
    writeStatement(statementBuilder, statement, null);
  }

  private void writeStatement(StringBuilder statementBuilder,
                              GrStatement statement,
                              @Nullable ExpressionContext context) {
    GenerationUtil.writeStatement(builder, statementBuilder, statement, context);
  }

  @Override
  public void visitAssertStatement(GrAssertStatement assertStatement) {
    final GrExpression assertion = assertStatement.getAssertion();
    final ExpressionGenerator expressionGenerator = new ExpressionGenerator(context.project);
    assertion.accept(expressionGenerator);
    final StringBuilder builder = new StringBuilder("assert ").append(expressionGenerator.getBuilder()).append(";");
    writeStatement(builder, assertStatement, expressionGenerator.getContext());
  }

  @Override
  public void visitThrowStatement(GrThrowStatement throwStatement) {
    final GrExpression exception = throwStatement.getException();
    final ExpressionGenerator expressionGenerator = new ExpressionGenerator(context.project);
    exception.accept(expressionGenerator);
    final StringBuilder builder =
      new StringBuilder("throw ").append(expressionGenerator.getBuilder()).append(";"); //todo add exception to method 'throws' list
    writeStatement(builder, throwStatement, expressionGenerator.getContext());
  }

  @Override
  public void visitLabeledStatement(GrLabeledStatement labeledStatement) {
    final String label = labeledStatement.getLabelName();
    final GrStatement statement = labeledStatement.getStatement();

    StringBuilder statementBuilder = new StringBuilder();
    statementBuilder.append(label).append(": ");
    if (statement != null) {
      statement.accept(new CodeBlockGenerator(statementBuilder, context.project));
    }
    writeStatement(statementBuilder, labeledStatement);
  }

  @Override
  public void visitExpression(GrExpression expression) {
    final StringBuilder statementBuilder = new StringBuilder();
    final ExpressionContext context = new ExpressionContext(this.context.project, this.context.myUsedVarNames);
    expression.accept(new ExpressionGenerator(statementBuilder, context));
    statementBuilder.append(";");
    writeStatement(statementBuilder, expression, context);
  }

  @Override
  public void visitApplicationStatement(GrApplicationStatement applicationStatement) {
    visitExpression(applicationStatement);
  }

  @Override
  public void visitTypeDefinition(GrTypeDefinition typeDefinition) {
    //todo ???????
  }

  @Override
  public void visitIfStatement(GrIfStatement ifStatement) {
    final GrCondition condition = ifStatement.getCondition();
    final GrStatement thenBranch = ifStatement.getThenBranch();
    final GrStatement elseBranch = ifStatement.getElseBranch();

    StringBuilder statementBuilder = new StringBuilder();
    final CodeBlockGenerator innerGenerator = new CodeBlockGenerator(statementBuilder, context.project);
    final ExpressionContext expressionContext = new ExpressionContext(context.project, context.myUsedVarNames);

    statementBuilder.append("if (");
    if (condition != null) condition.accept(new ExpressionGenerator(statementBuilder, expressionContext));
    statementBuilder.append(")");
    if (thenBranch != null) thenBranch.accept(innerGenerator);
    if (ifStatement.getElseKeyword() != null) statementBuilder.append("else");
    if (elseBranch != null) elseBranch.accept(innerGenerator);

    writeStatement(statementBuilder, ifStatement, expressionContext);
  }

  @Override
  public void visitForStatement(GrForStatement forStatement) {
    final StringBuilder builder = new StringBuilder();

    final GrForClause clause = forStatement.getClause();
    ExpressionContext forContext = context.extend();
    if (clause instanceof GrForInClause) {
      final GrExpression expression = ((GrForInClause)clause).getIteratedExpression();
      final GrVariable declaredVariable = clause.getDeclaredVariable();
      LOG.assertTrue(declaredVariable != null);

      builder.append("for(");
      writeVariableWithoutSemicolonAndInitializer(builder, declaredVariable);
      builder.append(" : ");
      if (expression != null) {
        final ExpressionContext context = forContext.copy();
        expression.accept(new ExpressionGenerator(builder, context));
      }
      builder.append(")");
    }
    else {
      final GrTraditionalForClause cl = (GrTraditionalForClause)clause;
      final GrCondition initialization = cl.getInitialization();
      final GrExpression condition = cl.getCondition();
      final GrExpression update = cl.getUpdate();

      if (initialization instanceof GrParameter) {
        StringBuilder partBuilder = new StringBuilder();
        writeVariableWithoutSemicolonAndInitializer(partBuilder, (GrParameter)initialization);
        final GrExpression initializer = ((GrParameter)initialization).getDefaultInitializer();
        if (initializer != null) {
          final ExpressionContext partContext = forContext.copy();
          initializer.accept(new ExpressionGenerator(partBuilder, partContext));
          for (String statement : partContext.myStatements) {
            builder.append(statement).append(", ");
          }
          builder.append(partBuilder);
        }
      }
      else {
        if (initialization != null) {
          StringBuilder partBuilder = new StringBuilder();
          final ExpressionContext partContext = forContext.copy();
          genForPart(builder, initialization, new CodeBlockGenerator(partBuilder, partContext));
        }
      }

      builder.append(";");
      if (condition != null) {
        genForPart(builder, condition, forContext.copy());
      }

      builder.append(";");
      if (update != null) {
        genForPart(builder, update, forContext.copy());
      }
      builder.append(")");
    }

    forStatement.getBody().accept(new CodeBlockGenerator(builder, forContext));
  }

  private static void genForPart(StringBuilder builder, GrExpression part, final ExpressionContext context) {
    genForPart(builder, part, new ExpressionGenerator(new StringBuilder(), context));
  }

  private static void genForPart(StringBuilder builder, GroovyPsiElement part, final Generator visitor) {
    part.accept(visitor);
    for (String statement : visitor.getContext().myStatements) {
      builder.append(statement).append(", ");
    }
    builder.append(visitor.getBuilder());
  }

  private static void writeVariableWithoutSemicolonAndInitializer(StringBuilder builder, GrVariable var) {
    if (GenerationUtil.writeModifiers(builder, var.getModifierList())) {
      builder.append(" ");
    }
    GenerationUtil.writeType(builder, getVarType(var));
    builder.append(" ").append(var.getName());
  }

  @Override
  public void visitWhileStatement(GrWhileStatement whileStatement) {
    final GrCondition condition = whileStatement.getCondition();
    final GrStatement body = whileStatement.getBody();

    StringBuilder builder = new StringBuilder();
    builder.append("while (");
    final ExpressionContext copy = context.copy();
    if (condition != null) {
      condition.accept(new ExpressionGenerator(builder, copy));
    }
    builder.append(" )");
    if (body != null) {
      body.accept(new CodeBlockGenerator(builder, copy.extend()));
    }
    writeStatement(builder, whileStatement, copy);
  }

  @Override
  public void visitSwitchStatement(GrSwitchStatement switchStatement) {
    final StringBuilder builder = new StringBuilder();
    final ExpressionContext copy = context.copy();
    SwitchStatementGenerator.generate(builder, copy, switchStatement);
    writeStatement(builder, switchStatement, copy);
  }

  @Override
  public void visitTryStatement(GrTryCatchStatement tryCatchStatement) {
    final GrOpenBlock tryBlock = tryCatchStatement.getTryBlock();
    final GrCatchClause[] catchClauses = tryCatchStatement.getCatchClauses();
    final GrFinallyClause finallyClause = tryCatchStatement.getFinallyClause();
    builder.append("try");
    tryBlock.accept(this);
    for (GrCatchClause catchClause : catchClauses) {
      catchClause.accept(this);
    }
    if (finallyClause != null) {
      finallyClause.accept(this);
    }
  }

  @Override
  public void visitCatchClause(GrCatchClause catchClause) {
    final GrParameter parameter = catchClause.getParameter();
    builder.append("catch (");
    writeVariableWithoutSemicolonAndInitializer(builder, parameter);
    builder.append(") ");
    final GrOpenBlock body = catchClause.getBody();
    if (body != null) {
      body.accept(this);
    }
  }

  @Override
  public void visitFinallyClause(GrFinallyClause finallyClause) {
    builder.append("finally ");
    finallyClause.getBody().accept(this);
  }

  @Override
  public void visitBlockStatement(GrBlockStatement blockStatement) {
    //todo
  }

  @Override
  public void visitSynchronizedStatement(GrSynchronizedStatement synchronizedStatement) {
    final GrExpression monitor = synchronizedStatement.getMonitor();
    final GrOpenBlock body = synchronizedStatement.getBody();

    StringBuilder statementBuilder = new StringBuilder();
    final ExpressionContext expressionContext = context.copy();

    statementBuilder.append("synchronized(");
    monitor.accept(new ExpressionGenerator(statementBuilder, expressionContext));
    statementBuilder.append(")");
    body.accept(new CodeBlockGenerator(statementBuilder, context));
    writeStatement(statementBuilder, synchronizedStatement, expressionContext);
  }

  @Override
  public void visitVariableDeclaration(GrVariableDeclaration variableDeclaration) {

    StringBuilder builder = new StringBuilder();
    ExpressionContext expressionContext = context.copy();


    if (variableDeclaration.isTuple()) {
      writeTupleDeclaration(variableDeclaration, builder, expressionContext);
    }
    else {
      writeSimpleVarDeclaration(variableDeclaration, builder, expressionContext);
    }

    writeStatement(builder, variableDeclaration, expressionContext);
  }

  private static void writeSimpleVarDeclaration(GrVariableDeclaration variableDeclaration,
                                                StringBuilder builder,
                                                ExpressionContext expressionContext) {
    GrVariable[] variables = variableDeclaration.getVariables();

    final GrTypeElement typeElement = variableDeclaration.getTypeElementGroovy();
    PsiType type = null;
    Set<String> types = new HashSet<String>(variables.length);
    if (typeElement == null) {
      if (variables.length > 1) {
        for (GrVariable variable : variables) {
          final GrExpression initializer = variable.getInitializerGroovy();
          if (initializer != null) {
            final PsiType varType = initializer.getType();
            if (varType != null) {
              types.add(getTypeText(varType));
              type = varType;
            }
          }
        }
        if (type == null) {
          type = PsiType.getJavaLangObject(variableDeclaration.getManager(), variableDeclaration.getResolveScope());
        }
      }
      else {
        type = getVarType(variables[0]);
      }
    }
    else {
      type = typeElement.getType();
    }

    if (types.size() > 1) {
      if (variableDeclaration.getParent() instanceof GrControlStatement) {
        expressionContext.setInsertCurlyBrackets();
      }
      for (GrVariable variable : variables) {
        writeVariableSeparately(variable, builder, expressionContext);
        builder.append(";\n");
      }
      return;
    }

    GenerationUtil.writeType(builder, type);

    builder.append(" ");
    for (GrVariable variable : variables) {
      writeVariableWithoutType(builder, expressionContext, variable);
      builder.append(", ");
    }
    if (variables.length > 0) {
      builder.delete(builder.length() - 2, builder.length());
    }
    builder.append(";");
  }

  private static void writeVariableWithoutType(StringBuilder builder, ExpressionContext expressionContext, GrVariable variable) {
    builder.append(variable.getName());
    final GrExpression initializer = variable.getInitializerGroovy();
    if (initializer != null) {
      builder.append(" = ");
      initializer.accept(new ExpressionGenerator(builder, expressionContext));
    }
  }

  private static void writeVariableSeparately(GrVariable variable, StringBuilder builder, ExpressionContext expressionContext) {
    PsiType type = getVarType(variable);

    if (GenerationUtil.writeModifiers(builder, variable.getModifierList())) {
      builder.append(" ");
    }

    GenerationUtil.writeType(builder, type);
    builder.append(" ");

    writeVariableWithoutType(builder, expressionContext, variable);
  }

  private static PsiType getVarType(GrVariable variable) {
    PsiType type = variable.getDeclaredType();
    if (type == null) {
      type = variable.getTypeGroovy();
    }
    if (type == null) {
      type = variable.getType();
    }
    return type;
  }

  private void writeTupleDeclaration(GrVariableDeclaration variableDeclaration,
                                     StringBuilder builder,
                                     ExpressionContext expressionContext) {
    GrVariable[] variables = variableDeclaration.getVariables();
    final GrExpression tupleInitializer = variableDeclaration.getTupleDeclaration().getInitializerGroovy();
    if (tupleInitializer instanceof GrListOrMap) {
      for (GrVariable variable : variables) {
        writeVariableSeparately(variable, builder, expressionContext);
        builder.append(";\n");
      }
    }
    else {
      final PsiType iteratorType =
        JavaPsiFacade.getElementFactory(context.project).createTypeFromText(CommonClassNames.JAVA_UTIL_ITERATOR, variableDeclaration);
      final String iteratorName = GenerationUtil.suggestVarName(iteratorType, variableDeclaration, expressionContext);
      builder.append("final ").append(CommonClassNames.JAVA_UTIL_ITERATOR).append(" ").append(iteratorName).append(" = ");

      GenerationUtil.invokeMethodByName(tupleInitializer, "iterator", GrExpression.EMPTY_ARRAY, GrNamedArgument.EMPTY_ARRAY,
                                        GrClosableBlock.EMPTY_ARRAY, new ExpressionGenerator(builder, expressionContext),
                                        variableDeclaration);
      final GrModifierList modifierList = variableDeclaration.getModifierList();
      for (final GrVariable v : variables) {
        GenerationUtil.writeModifiers(builder, modifierList);
        final PsiType type = getVarType(v);
        GenerationUtil.writeType(builder, type);
        builder.append(" ").append(v.getName());
        builder.append(" = ").append(iteratorName).append(".hasNext() ? ").append(iteratorName).append(".next() : null;");
      }
    }
  }

  private void writeVariable(GrVariable variable) {
    //todo
    throw new UnsupportedOperationException();
  }

  private static String getTypeText(PsiType varType) {
    final StringBuilder builder = new StringBuilder();
    GenerationUtil.writeType(builder, varType);
    return builder.toString();
  }

  @Override
  public void visitVariable(GrVariable variable) {
    super.visitVariable(variable);    //To change body of overridden methods use File | Settings | File Templates.
  }
}
