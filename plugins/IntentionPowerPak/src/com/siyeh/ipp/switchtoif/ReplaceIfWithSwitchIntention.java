/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.switchtoif;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ControlFlowUtils;
import com.siyeh.ipp.psiutils.DeclarationUtils;
import com.siyeh.ipp.psiutils.EquivalenceChecker;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ReplaceIfWithSwitchIntention extends Intention {

    @Override
    @NotNull
    public PsiElementPredicate getElementPredicate() {
        return new IfToSwitchPredicate();
    }

    @Override
    public void processIntention(@NotNull PsiElement element)
            throws IncorrectOperationException {
        final PsiJavaToken switchToken = (PsiJavaToken) element;
        PsiIfStatement ifStatement = (PsiIfStatement)switchToken.getParent();
        if (ifStatement == null) {
            return;
        }
        boolean breaksNeedRelabeled = false;
        PsiStatement breakTarget = null;
        String labelString = "";
        if (ControlFlowUtils.statementContainsExitingBreak(ifStatement)) {
            // what a pain.
            PsiElement ancestor = ifStatement.getParent();
            while (ancestor != null) {
                if (ancestor instanceof PsiForStatement ||
                        ancestor instanceof PsiDoWhileStatement ||
                        ancestor instanceof PsiWhileStatement ||
                        ancestor instanceof PsiSwitchStatement) {
                    breakTarget = (PsiStatement)ancestor;
                    break;
                }
                ancestor = ancestor.getParent();
            }
            if (breakTarget != null) {
                labelString = CaseUtil.findUniqueLabel(ifStatement, "Label");
                breaksNeedRelabeled = true;
            }
        }
        final PsiIfStatement statementToReplace = ifStatement;
        final PsiExpression caseExpression =
                CaseUtil.getCaseExpression(ifStatement);
        assert caseExpression != null;

        final List<IfStatementBranch> branches =
                new ArrayList<IfStatementBranch>(20);
        while (true) {
            final Set<String> topLevelVariables = new HashSet<String>(5);
            final Set<String> innerVariables = new HashSet<String>(5);
            final PsiExpression condition = ifStatement.getCondition();
            final List<PsiExpression> labels =
                    getValuesFromExpression(condition, caseExpression,
                            new ArrayList());
            final PsiStatement thenBranch = ifStatement.getThenBranch();
            DeclarationUtils.calculateVariablesDeclared(thenBranch,
                    topLevelVariables,
                    innerVariables,
                    true);
            final IfStatementBranch ifBranch = new IfStatementBranch();
            if (!branches.isEmpty()) {
                extractIfComments(ifStatement, ifBranch);
            }
            ifBranch.setInnerVariables(innerVariables);
            ifBranch.setTopLevelVariables(topLevelVariables);
            extractStatementComments(thenBranch, ifBranch);
            ifBranch.setStatement(thenBranch);
            for (final PsiExpression label : labels) {
                if (label instanceof PsiReferenceExpression) {
                    final PsiReferenceExpression reference =
                            (PsiReferenceExpression)label;
                    final PsiElement referent = reference.resolve();
                    if (referent instanceof PsiEnumConstant) {
                        final PsiEnumConstant constant =
                                (PsiEnumConstant)referent;
                        final String constantName = constant.getName();
                        ifBranch.addCondition(constantName);
                    } else {
                        final String labelText = label.getText();
                        ifBranch.addCondition(labelText);
                    }
                } else {
                    final String labelText = label.getText();
                    ifBranch.addCondition(labelText);
                }
            }
            branches.add(ifBranch);
            final PsiStatement elseBranch = ifStatement.getElseBranch();

            if (elseBranch instanceof PsiIfStatement) {
                ifStatement = (PsiIfStatement)elseBranch;
            } else if (elseBranch == null) {
                break;
            } else {
                final Set<String> elseTopLevelVariables = new HashSet<String>(5);
                final Set<String> elseInnerVariables = new HashSet<String>(5);
                DeclarationUtils.calculateVariablesDeclared(
                        elseBranch, elseTopLevelVariables, elseInnerVariables,
                        true);
                final IfStatementBranch elseIfBranch = new IfStatementBranch();
                final PsiKeyword elseKeyword = ifStatement.getElseElement();
                extractIfComments(elseKeyword, elseIfBranch);
                extractStatementComments(elseBranch, elseIfBranch);
                elseIfBranch.setInnerVariables(elseInnerVariables);
                elseIfBranch.setTopLevelVariables(elseTopLevelVariables);
                elseIfBranch.setElse();
                elseIfBranch.setStatement(elseBranch);
                branches.add(elseIfBranch);
                break;
            }
        }

        @NonNls final StringBuilder switchStatementText =
                new StringBuilder();
        switchStatementText.append("switch(");
        switchStatementText.append(caseExpression.getText());
        switchStatementText.append(')');
        switchStatementText.append('{');
        for (IfStatementBranch branch : branches) {
            boolean hasConflicts = false;
            for (IfStatementBranch testBranch : branches) {
                if (branch.topLevelDeclarationsConfictWith(testBranch)) {
                    hasConflicts = true;
                }
            }

            final PsiStatement branchStatement = branch.getStatement();
            if (branch.isElse()) {
                final List<String> comments = branch.getComments();
                final List<String> statementComments =
                        branch.getStatementComments();
                dumpDefaultBranch(switchStatementText, comments,
                        branchStatement, statementComments,
                        hasConflicts,
                        breaksNeedRelabeled, labelString);
            } else {
                final List<String> conditions = branch.getConditions();
                final List<String> comments = branch.getComments();
                final List<String> statementComments =
                        branch.getStatementComments();
                dumpBranch(switchStatementText,
                        comments, conditions, statementComments,
                        branchStatement, hasConflicts, breaksNeedRelabeled,
                        labelString);
            }
        }
        switchStatementText.append('}');
        final JavaPsiFacade psiFacade =
                JavaPsiFacade.getInstance(element.getProject());
        final PsiElementFactory factory = psiFacade.getElementFactory();
        if (breaksNeedRelabeled) {
            final StringBuilder out = new StringBuilder();
            out.append(labelString);
            out.append(':');
            termReplace(out, breakTarget, statementToReplace,
                    switchStatementText);
            final String newStatementText = out.toString();
            final PsiStatement newStatement =
                    factory.createStatementFromText(newStatementText, element);
            breakTarget.replace(newStatement);
        } else {
            final PsiStatement newStatement =
                    factory.createStatementFromText(
                            switchStatementText.toString(), element);
            statementToReplace.replace(newStatement);
        }
    }

    @Nullable
    public static <T extends PsiElement> T getPrevSiblingOfType(
            @Nullable PsiElement element,
            @NotNull Class<T> aClass,
            @NotNull Class<? extends PsiElement>... stopAt) {
        if (element == null) {
            return null;
        }
        PsiElement sibling = element.getPrevSibling();
        while (sibling != null && !aClass.isInstance(sibling)) {
            for (Class<? extends PsiElement> stopClass : stopAt) {
                if (stopClass.isInstance(sibling)) {
                    return null;
                }
            }
            sibling = sibling.getPrevSibling();
        }
        return (T)sibling;
    }

    private static void extractIfComments(PsiElement element,
                                          IfStatementBranch out) {
        PsiComment comment = getPrevSiblingOfType(element,
                PsiComment.class, PsiStatement.class);
        while (comment != null) {
            final PsiElement sibling = comment.getPrevSibling();
            final String commentText;
            if (sibling instanceof PsiWhiteSpace) {
                final String whiteSpaceText = sibling.getText();
                if (whiteSpaceText.startsWith("\n")) {
                    commentText = whiteSpaceText.substring(1) +
                            comment.getText();
                } else {
                    commentText = comment.getText();
                }
            } else {
                commentText = comment.getText();
            }
            out.addComment(commentText);
            comment = getPrevSiblingOfType(comment, PsiComment.class,
                    PsiStatement.class);
        }
    }

    private static void extractStatementComments(PsiElement element,
                                                  IfStatementBranch out) {
        PsiComment comment = getPrevSiblingOfType(element,
                PsiComment.class, PsiStatement.class, PsiKeyword.class);
        while (comment != null) {
            final PsiElement sibling = comment.getPrevSibling();
            final String commentText;
            if (sibling instanceof PsiWhiteSpace) {
                final String whiteSpaceText = sibling.getText();
                if (whiteSpaceText.startsWith("\n")) {
                    commentText = whiteSpaceText.substring(1) +
                            comment.getText();
                } else {
                    commentText = comment.getText();
                }
            } else {
                commentText = comment.getText();
            }
            out.addStatementComment(commentText);
            comment = getPrevSiblingOfType(comment, PsiComment.class,
                    PsiStatement.class, PsiKeyword.class);
        }
    }

    private static void termReplace(
            StringBuilder out, PsiElement target,
            PsiElement replace, StringBuilder stringToReplaceWith) {
        if (target.equals(replace)) {
            out.append(stringToReplaceWith);
        } else if (target.getChildren().length == 0) {
            final String text = target.getText();
            out.append(text);
        } else {
            final PsiElement[] children = target.getChildren();
            for (final PsiElement child : children) {
                termReplace(out, child, replace, stringToReplaceWith);
            }
        }
    }

    private static List<PsiExpression> getValuesFromExpression(
            PsiExpression expression, PsiExpression caseExpression,
            List<PsiExpression> values) {
        if (expression instanceof PsiMethodCallExpression) {
            final PsiMethodCallExpression methodCallExpression =
                    (PsiMethodCallExpression) expression;
            final PsiExpressionList argumentList =
                    methodCallExpression.getArgumentList();
            final PsiExpression[] arguments = argumentList.getExpressions();
            final PsiExpression argument = arguments[0];
            final PsiReferenceExpression methodExpression =
                    methodCallExpression.getMethodExpression();
            final PsiExpression qualifierExpression =
                    methodExpression.getQualifierExpression();
            if (EquivalenceChecker.expressionsAreEquivalent(caseExpression,
                    argument)) {
                values.add(qualifierExpression);
            } else {
                values.add(argument);
            }
        } else if (expression instanceof PsiBinaryExpression) {
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression)expression;
            final PsiExpression lhs = binaryExpression.getLOperand();
            final PsiExpression rhs = binaryExpression.getROperand();
            final PsiJavaToken sign = binaryExpression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if (JavaTokenType.OROR.equals(tokenType)) {
                getValuesFromExpression(lhs, caseExpression,
                        values);
                getValuesFromExpression(rhs, caseExpression,
                        values);
            } else {
                if (EquivalenceChecker.expressionsAreEquivalent(caseExpression,
                        rhs)) {
                    values.add(lhs);
                } else {
                    values.add(rhs);
                }
            }
        } else if (expression instanceof PsiParenthesizedExpression) {
            final PsiParenthesizedExpression parenExpression =
                    (PsiParenthesizedExpression)expression;
            final PsiExpression contents = parenExpression.getExpression();
            getValuesFromExpression(contents, caseExpression, values);
        }
        return values;
    }

    private static void dumpBranch(StringBuilder switchStatementString,
                                   List<String> comments,
                                   List<String> labels,
                                   List<String> statementComments,
                                   PsiStatement body,
                                   boolean wrap, boolean renameBreaks,
                                   String breakLabelName) {
        dumpComments(switchStatementString, comments);
        dumpLabels(switchStatementString, labels);
        dumpComments(switchStatementString, statementComments);
        dumpBody(switchStatementString, body, wrap, renameBreaks,
                breakLabelName);
    }

    private static void dumpComments(StringBuilder switchStatementString,
                                     List<String> comments) {
        if (!comments.isEmpty()) {
            switchStatementString.append('\n');
            for (String comment : comments) {
                switchStatementString.append(comment);
                switchStatementString.append('\n');
            }
        }
    }

    private static void dumpDefaultBranch(
            @NonNls StringBuilder switchStatementString,
            List<String> comments, PsiStatement body,
            List<String> statementComments, boolean wrap,
            boolean renameBreaks, String breakLabelName) {
        dumpComments(switchStatementString, comments);
        switchStatementString.append("default: ");
        dumpComments(switchStatementString, statementComments);
        dumpBody(switchStatementString, body, wrap, renameBreaks,
                breakLabelName);
    }

    private static void dumpLabels(@NonNls StringBuilder switchStatementString,
                                   List<String> labels) {
        for (String label : labels) {
            switchStatementString.append("case ");
            switchStatementString.append(label);
            switchStatementString.append(": ");
        }
    }

    private static void dumpBody(@NonNls StringBuilder switchStatementString,
                                 PsiStatement bodyStatement, boolean wrap,
                                 boolean renameBreaks, String breakLabelName) {
        if (bodyStatement instanceof PsiBlockStatement) {
            if (wrap) {
                appendElement(switchStatementString, bodyStatement,
                        renameBreaks, breakLabelName);
            } else {
                final PsiCodeBlock codeBlock =
                        ((PsiBlockStatement)bodyStatement).getCodeBlock();
                final PsiElement[] children = codeBlock.getChildren();
                //skip the first and last members, to unwrap the block
                for (int i = 1; i < children.length - 1; i++) {
                    final PsiElement child = children[i];
                    appendElement(switchStatementString, child, renameBreaks,
                            breakLabelName);
                }
            }
        } else {
            if (wrap) {
                switchStatementString.append('{');
                appendElement(switchStatementString, bodyStatement,
                        renameBreaks, breakLabelName);
                switchStatementString.append('}');
            } else {
                appendElement(switchStatementString, bodyStatement,
                        renameBreaks, breakLabelName);
            }
        }
        if (ControlFlowUtils.statementMayCompleteNormally(bodyStatement)) {
            switchStatementString.append("break; ");
        }
    }

    private static void appendElement(
            @NonNls StringBuilder switchStatementString,
            PsiElement element, boolean renameBreakElements,
            String breakLabelString) {
        final String text = element.getText();
        if (!renameBreakElements) {
            switchStatementString.append(text);
        } else if (element instanceof PsiBreakStatement) {
            final PsiIdentifier identifier =
                    ((PsiBreakStatement)element).getLabelIdentifier();
            if (identifier == null) {
                switchStatementString.append("break ");
                switchStatementString.append(breakLabelString);
                switchStatementString.append(';');
            } else {
                final String identifierText = identifier.getText();
                if ("".equals(identifierText)) {
                    switchStatementString.append("break ");
                    switchStatementString.append(breakLabelString);
                    switchStatementString.append(';');
                } else {
                    switchStatementString.append(text);
                }
            }
        } else if (element instanceof PsiBlockStatement ||
                element instanceof PsiCodeBlock ||
                element instanceof PsiIfStatement) {
            final PsiElement[] children = element.getChildren();
            for (final PsiElement child : children) {
                appendElement(switchStatementString, child, renameBreakElements,
                        breakLabelString);
            }
        } else {
            switchStatementString.append(text);
        }
    }
}
