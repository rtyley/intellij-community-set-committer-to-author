/*
 * Copyright 2005 Sascha Weinreuter
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
package org.intellij.lang.xpath.validation;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import org.intellij.lang.xpath.XPath2TokenTypes;
import org.intellij.lang.xpath.XPathElementType;
import org.intellij.lang.xpath.XPathFileType;
import org.intellij.lang.xpath.context.functions.Function;
import org.jetbrains.annotations.NotNull;

import org.intellij.lang.xpath.XPathTokenTypes;
import org.intellij.lang.xpath.context.ContextProvider;
import org.intellij.lang.xpath.context.NamespaceContext;
import org.intellij.lang.xpath.context.VariableContext;
import org.intellij.lang.xpath.context.functions.Parameter;
import org.intellij.lang.xpath.psi.*;
import org.intellij.lang.xpath.psi.impl.PrefixedNameImpl;

import javax.xml.namespace.QName;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class XPathAnnotator extends XPath2ElementVisitor implements Annotator {

  private AnnotationHolder myHolder;

  public void annotate(@NotNull PsiElement psiElement, @NotNull AnnotationHolder holder) {

    try {
      myHolder = holder;
      psiElement.accept(this);
    } finally {
      myHolder = null;
    }
  }

  @Override
  public void visitXPathNodeTest(XPathNodeTest o) {
    final ContextProvider contextProvider = o.getXPathContext();
    checkNodeTest(contextProvider, myHolder, o);
  }

  @Override
  public void visitXPathStep(XPathStep o) {
    checkSillyStep(myHolder, o);
    super.visitXPathStep(o);
  }

  @Override
  public void visitXPathNodeTypeTest(XPathNodeTypeTest o) {
    checkNodeTypeTest(myHolder, o);
    visitXPathExpression(o);
  }

  @Override
  public void visitXPathFunctionCall(XPathFunctionCall o) {
    final ContextProvider contextProvider = o.getXPathContext();
    checkFunctionCall(myHolder, o, contextProvider);
    super.visitXPathFunctionCall(o);
  }

  @Override
  public void visitXPathString(XPathString o) {
    checkString(myHolder, o);
    super.visitXPathString(o);
  }

  @Override
  public void visitXPathVariableReference(XPathVariableReference o) {
    final ContextProvider contextProvider = o.getXPathContext();
    checkVariableReference(myHolder, o, contextProvider);
    super.visitXPathVariableReference(o);
  }

  @Override
  public void visitXPath2TypeElement(XPath2TypeElement o) {
    final ContextProvider contextProvider = o.getXPathContext();
    checkPrefixReferences(myHolder, o, contextProvider);
    super.visitXPath2TypeElement(o);
  }

  @Override
  public void visitXPathBinaryExpression(final XPathBinaryExpression o) {
    if (o.getContainingFile().getLanguage() == XPathFileType.XPATH2.getLanguage()) {
      final XPathExpression operand = o.getLOperand();
      final XPathElementType operator = o.getOperator();
      if (operand instanceof XPathNumber) {
        if (operator != XPathTokenTypes.STAR && XPath2TokenTypes.KEYWORDS.contains(operator)) {
          final String op = o.getOperationSign();
          if (o.getText().startsWith(operand.getText() + op)) {
            myHolder.createErrorAnnotation(o, "Number literal must be followed by whitespace in XPath 2");
          }
        }
      }
      if (XPath2TokenTypes.COMP_OPS.contains(operator)) {
        if (operand instanceof XPathBinaryExpression && XPath2TokenTypes.COMP_OPS.contains(((XPathBinaryExpression)operand).getOperator())) {
          final Annotation annotation = myHolder.createErrorAnnotation(o, "Consecutive comparison is not allowed in XPath 2");

          final XPathExpression rOperand = o.getROperand();
          if (rOperand != null) {
            final String replacement = "(" + operand.getText() + ") " + o.getOperationSign() + " " + rOperand.getText();
            annotation.registerFix(new ConsecutiveComparisonFix(replacement, o));
          }
        }
      }
    }

    checkExpression(myHolder, o);
    super.visitXPathBinaryExpression(o);
  }

  @Override
  public void visitXPathExpression(XPathExpression o) {
    checkExpression(myHolder, o);
  }

  private static void checkString(AnnotationHolder holder, XPathString string) {
    if (!string.isWellFormed()) {
      holder.createErrorAnnotation(string, "Malformed string literal");
    }
  }

  private static void checkVariableReference(AnnotationHolder holder, XPathVariableReference reference, @NotNull ContextProvider contextProvider) {
    if (reference.resolve() == null) {
      final VariableContext variableResolver = contextProvider.getVariableContext();
      if (variableResolver == null) return;

      if (!variableResolver.canResolve()) {
        final Object[] variablesInScope = variableResolver.getVariablesInScope(reference);
        if (variablesInScope instanceof String[]) {
          final Set<String> variables = new HashSet<String>(Arrays.asList((String[])variablesInScope));
          if (!variables.contains(reference.getReferencedName())) {
            markUnresolvedVariable(reference, holder);
          }
        } else if (variablesInScope instanceof QName[]) {
          final Set<QName> variables = new HashSet<QName>(Arrays.asList((QName[])variablesInScope));
          if (!variables.contains(contextProvider.getQName(reference))) {
            markUnresolvedVariable(reference, holder);
          }
        }
      } else {
        markUnresolvedVariable(reference, holder);
      }
    }
  }

  private static void markUnresolvedVariable(XPathVariableReference reference, AnnotationHolder holder) {
    final String referencedName = reference.getReferencedName();
    // missing name is already flagged by parser
    if (referencedName.length() > 0) {
      final TextRange range = reference.getTextRange().shiftRight(1).grown(-1);
      final Annotation ann = holder.createErrorAnnotation(range, "Unresolved variable '" + referencedName + "'");
      ann.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
      final VariableContext variableContext = ContextProvider.getContextProvider(reference).getVariableContext();
      if (variableContext != null) {
        final IntentionAction[] fixes = variableContext.getUnresolvedVariableFixes(reference);
        for (IntentionAction fix : fixes) {
          ann.registerFix(fix);
        }
      }
    }
  }

  private static void checkSillyStep(AnnotationHolder holder, XPathStep step) {
    final XPathExpression previousStep = step.getPreviousStep();
    if (previousStep instanceof XPathStep) {
      final XPathNodeTest nodeTest = ((XPathStep)previousStep).getNodeTest();
      if (nodeTest != null) {
        final XPathNodeTest.PrincipalType principalType = nodeTest.getPrincipalType();
        if (principalType != XPathNodeTest.PrincipalType.ELEMENT) {
          XPathNodeTest test = step.getNodeTest();
          if (test != null) {
            holder.createWarningAnnotation(test, "Silly location step on " + principalType.getType() + " axis");
          }
        }
      }
    }
  }

  private static void checkFunctionCall(AnnotationHolder holder, XPathFunctionCall call, @NotNull ContextProvider contextProvider) {
    final ASTNode node = call.getNode().findChildByType(XPathTokenTypes.FUNCTION_NAME);

    final QName name = contextProvider.getQName(call);
    final XPathFunction function = call.resolve();
    final Function functionDecl = function != null ? function.getDeclaration() : null;
    if (functionDecl == null) {
      final PrefixedNameImpl qName = ((PrefixedNameImpl)call.getQName());

      // need special check for extension functions
      if (call.getQName().getPrefix() != null && contextProvider.getFunctionContext().allowsExtensions()) {
        final PsiReference[] references = call.getReferences();
        if (references.length > 1 && references[1].resolve() == null) {
          final Annotation ann = holder.createErrorAnnotation(qName.getPrefixNode(), "Extension namespace prefix '" + qName.getPrefix() + "' has not been declared");
          ann.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
        }
      } else {
        if (name != null) {
          holder.createWarningAnnotation(node, "Unknown function '" + name + "'");
        } else if (qName.getPrefixNode() != null) {
          final Annotation ann = holder.createErrorAnnotation(qName.getPrefixNode(), "Extension namespace prefix '" + qName.getPrefix() + "' has not been declared");
          ann.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
        }
      }
    } else {
      final XPathExpression[] arguments = call.getArgumentList();
      for (int i = 0; i < arguments.length; i++) {
        checkArgument(holder, arguments[i], i, functionDecl.getParameters());
      }
      if (arguments.length < functionDecl.getMinArity()) {
        if (functionDecl.getMinArity() == 1) {
          holder.createErrorAnnotation(node, "Missing argument for function '" + name + "'");
        } else {
          final Parameter last = functionDecl.getParameters()[functionDecl.getParameters().length - 1];
          final String atLeast =
                  last.kind == Parameter.Kind.OPTIONAL ||
                          last.kind == Parameter.Kind.VARARG ?
                          "at least " : "";
          holder.createErrorAnnotation(node, "Function '" + name + "' requires " + atLeast + functionDecl.getMinArity() + " arguments");
        }
      }
    }
  }

  private static void checkArgument(AnnotationHolder holder, XPathExpression argument, int i, Parameter[] parameters) {
    if (i >= parameters.length) {
      if (parameters.length > 0 && parameters[parameters.length - 1].kind == Parameter.Kind.VARARG) {
        // OK. Validate types against the last declared - vararg - param.
      } else {
        holder.createErrorAnnotation(argument, "Too many arguments");
      }
    }
  }

  private static void checkNodeTypeTest(AnnotationHolder holder, XPathNodeTypeTest test) {
    final XPathExpression[] arguments = test.getArgumentList();
    if (arguments.length == 0) {
      return;
    }
    if (test.getNodeType() == NodeType.PROCESSING_INSTRUCTION && arguments.length == 1) {
      if (!(arguments[0] instanceof XPathString)) {
        holder.createErrorAnnotation(arguments[0], "String literal expected");
      }
      return;
    }
    holder.createErrorAnnotation(test, "Invalid number of arguments for node type test '" + test.getNodeType().getType() + "'");
  }

  private static void checkNodeTest(@NotNull ContextProvider myProvider, AnnotationHolder holder, XPathNodeTest nodeTest) {
    checkSillyNodeTest(holder, nodeTest);

    checkPrefixReferences(holder, nodeTest, myProvider);
  }

  private static void checkPrefixReferences(AnnotationHolder holder, QNameElement element, ContextProvider myProvider) {
    final PsiReference[] references = element.getReferences();
    for (PsiReference reference : references) {
      if (reference instanceof PrefixReference) {
        final PrefixReference pr = ((PrefixReference)reference);
        if (!pr.isSoft() && pr.isUnresolved()) {
          final TextRange range = pr.getRangeInElement().shiftRight(pr.getElement().getTextRange().getStartOffset());
          final Annotation a = holder.createErrorAnnotation(range, "Unresolved namespace prefix '" + pr.getCanonicalText() + "'");
          a.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);

          final NamespaceContext namespaceContext = myProvider.getNamespaceContext();
          final PrefixedName qName = element.getQName();
          if (namespaceContext != null && qName != null) {
            final IntentionAction[] fixes = namespaceContext.getUnresolvedNamespaceFixes(reference, qName.getLocalName());
            for (IntentionAction fix : fixes) {
              a.registerFix(fix);
            }
          }
        }
      }
    }
  }

  private static void checkSillyNodeTest(AnnotationHolder holder, XPathNodeTest nodeTest) {
    if (nodeTest.getPrincipalType() != XPathNodeTest.PrincipalType.ELEMENT) {
      final XPathNodeTypeTest typeTest = PsiTreeUtil.getChildOfType(nodeTest, XPathNodeTypeTest.class);
      if (typeTest != null) {
        holder.createWarningAnnotation(typeTest, "Silly node type test on axis '" + nodeTest.getPrincipalType().getType() + "'");
      }
    }
  }

  private static void checkExpression(AnnotationHolder holder, @NotNull XPathExpression expression) {
    final XPathType expectedType = ExpectedTypeUtil.getExpectedType(expression);
    final XPathType opType = ExpectedTypeUtil.mapType(expression, expression.getType());
    if (!XPathType.isAssignable(expectedType, opType)) {
      holder.createErrorAnnotation(expression, "Expected type '" + expectedType.getName() + "', got '" + opType.getName() + "'");
    }
  }
}