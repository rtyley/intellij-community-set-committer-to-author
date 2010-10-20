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

package org.jetbrains.plugins.groovy.refactoring.introduceVariable;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.IntroduceTargetChooser;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;
import org.jetbrains.plugins.groovy.refactoring.NameValidator;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ilyas
 */
public abstract class GroovyIntroduceVariableBase implements RefactoringActionHandler {

  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.refactoring.introduceVariable.groovyIntroduceVariableBase");
  protected static String REFACTORING_NAME = GroovyRefactoringBundle.message("introduce.variable.title");
  private PsiElement positionElement = null;

  public void invoke(final @NotNull Project project, final Editor editor, final PsiFile file, final @Nullable DataContext dataContext) {
    final SelectionModel selectionModel = editor.getSelectionModel();
    if (!selectionModel.hasSelection()) {
      final int offset = editor.getCaretModel().getOffset();


      if (!selectionModel.hasSelection()) {
        final List<GrExpression> expressions = collectExpressions(file, editor, offset);
        if (expressions.isEmpty()) {
          selectionModel.selectLineAtCaret();
        } else if (expressions.size() == 1) {
          final TextRange textRange = expressions.get(0).getTextRange();
          selectionModel.setSelection(textRange.getStartOffset(), textRange.getEndOffset());
        }
        else {
          IntroduceTargetChooser.showChooser(editor, expressions,
                                             new Pass<GrExpression>() {
                                               public void pass(final GrExpression selectedValue) {
                                                 invoke(project, editor, file, selectedValue.getTextRange().getStartOffset(),
                                                        selectedValue.getTextRange().getEndOffset());
                                               }
                                             },
                                             new Function<GrExpression, String>() {
                                               @Override
                                               public String fun(GrExpression grExpression) {
                                                 return grExpression.getText();
                                               }
                                             });
          return;
        }
      }
    }
    invoke(project, editor, file, selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
  }

  public static List<GrExpression> collectExpressions(final PsiFile file, final Editor editor, final int offset) {
    Document document = editor.getDocument();
    CharSequence text = document.getCharsSequence();
    int correctedOffset = offset;
    int textLength = document.getTextLength();
    if (offset >= textLength) {
      correctedOffset = textLength - 1;
    }
    else if (!Character.isJavaIdentifierPart(text.charAt(offset))) {
      correctedOffset--;
    }
    if (correctedOffset < 0) {
      correctedOffset = offset;
    }
    else if (!Character.isJavaIdentifierPart(text.charAt(correctedOffset))) {
      if (text.charAt(correctedOffset) == ';') {//initially caret on the end of line
        correctedOffset--;
      }
      if (text.charAt(correctedOffset) != ')') {
        correctedOffset = offset;
      }
    }
    final PsiElement elementAtCaret = file.findElementAt(correctedOffset);
    final List<GrExpression> expressions = new ArrayList<GrExpression>();

    for (GrExpression expression = PsiTreeUtil.getParentOfType(elementAtCaret, GrExpression.class);
         expression != null;
         expression = PsiTreeUtil.getParentOfType(expression, GrExpression.class)) {
      if (expressions.contains(expression) || expression instanceof GrParenthesizedExpression) continue;
      if (expression instanceof GrSuperReferenceExpression || expression.getType() == PsiType.VOID) continue;

      if (expression instanceof GrApplicationStatement) continue;
      if (expression instanceof GrReferenceExpression &&
          (expression.getParent() instanceof GrMethodCall && ((GrMethodCall)expression.getParent()).isCommandExpression() ||
           ((GrReferenceExpression)expression).resolve() instanceof PsiClass)) {
        continue;
      }
      if (expression instanceof GrAssignmentExpression) continue;

      expressions.add(expression);
    }
    return expressions;
  }


  private boolean invoke(final Project project, final Editor editor, PsiFile file, int startOffset, int endOffset) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    if (!(file instanceof GroovyFileBase)) {
      String message = RefactoringBundle.getCannotRefactorMessage(GroovyRefactoringBundle.message("only.in.groovy.files"));
      showErrorMessage(project, editor, message);
      return false;
    }
    // Expression or block to be introduced as a variable
    GrExpression tempExpr = GroovyRefactoringUtil.findElementInRange((GroovyFileBase) file, startOffset, endOffset, GrExpression.class);
// removed according to GRVY-1231
/*
    if (tempExpr != null) {
      PsiElement parent = tempExpr.getParent();
      if (parent instanceof GrMethodCallExpression || parent instanceof GrIndexProperty) {
        tempExpr = ((GrExpression) tempExpr.getParent());
        SelectionModel model = editor.getSelectionModel();
        model.setSelection(model.getSelectionStart(), tempExpr.getTextRange().getEndOffset());
      }
    }
*/
    return invokeImpl(project, tempExpr, editor);
  }

  private boolean invokeImpl(final Project project, final GrExpression selectedExpr, final Editor editor) {

    if (selectedExpr == null || (selectedExpr instanceof GrClosableBlock && selectedExpr.getParent() instanceof GrStringInjection)) {
      String message =
        RefactoringBundle.getCannotRefactorMessage(GroovyRefactoringBundle.message("selected.block.should.represent.an.expression"));
      showErrorMessage(project, editor, message);
      return false;
    }

    if (selectedExpr instanceof GrReferenceExpression &&
        selectedExpr.getParent() instanceof GrMethodCall &&
        (((GrMethodCall)selectedExpr.getParent()).isCommandExpression() || selectedExpr.getParent() instanceof GrApplicationStatement) ||
        selectedExpr instanceof GrApplicationStatement) {
      String message = RefactoringBundle.getCannotRefactorMessage(GroovyRefactoringBundle.message("selected.expression.in.command.expression"));
      showErrorMessage(project, editor, message);
      return false;
    }

    final PsiFile file = selectedExpr.getContainingFile();
    LOG.assertTrue(file != null, "expr.getContainingFile() == null");
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);


    PsiType type = selectedExpr.getType();
    if (type != null) type = TypeConversionUtil.erasure(type);

    if (PsiType.VOID.equals(type)) {
      String message = RefactoringBundle.getCannotRefactorMessage(GroovyRefactoringBundle.message("selected.expression.has.void.type"));
      showErrorMessage(project, editor, message);
      return false;
    }

    // Cannot perform refactoring in parameter default values
    PsiElement parent = selectedExpr.getParent();
    while (parent != null &&
        !(parent instanceof GroovyFileBase) &&
        !(parent instanceof GrParameter)) {
      parent = parent.getParent();
    }

    if (checkInFieldInitializer(selectedExpr)) {
      String message = RefactoringBundle.getCannotRefactorMessage(GroovyRefactoringBundle.message("refactoring.is.not.supported.in.the.current.context"));
      showErrorMessage(project, editor, message);
      return false;
    }

    if (parent instanceof GrParameter) {
      String message = RefactoringBundle.getCannotRefactorMessage(GroovyRefactoringBundle.message("refactoring.is.not.supported.in.method.parameters"));
      showErrorMessage(project, editor, message);
      return false;
    }

    // Get container element
    final PsiElement enclosingContainer = GroovyRefactoringUtil.getEnclosingContainer(selectedExpr);
    if (enclosingContainer == null || !(enclosingContainer instanceof GroovyPsiElement)) {
      return tempContainerNotFound(project);
    }
    final GroovyPsiElement tempContainer = ((GroovyPsiElement) enclosingContainer);
    if (!GroovyRefactoringUtil.isAppropriateContainerForIntroduceVariable(tempContainer)) {
      String message = RefactoringBundle.getCannotRefactorMessage(GroovyRefactoringBundle.message("refactoring.is.not.supported.in.the.current.context", REFACTORING_NAME));
      showErrorMessage(project, editor, message);
      return false;
    }
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, file)) return false;

    // Find occurrences
    final PsiElement[] occurrences =
      GroovyRefactoringUtil.getExpressionOccurrences(PsiUtil.skipParentheses(selectedExpr, false), tempContainer);
    if (occurrences == null || occurrences.length == 0) {
      String message = RefactoringBundle.getCannotRefactorMessage(GroovyRefactoringBundle.message("no.occurences.found"));
      showErrorMessage(project, editor, message);
      return false;
    }
    
    // Getting settings
    Validator validator = new GroovyVariableValidator(this, project, selectedExpr, occurrences, tempContainer);
    GroovyIntroduceVariableDialog dialog = getDialog(project, editor, selectedExpr, type, occurrences, false, validator);

    if (!dialog.isOK()) {
      return false;
    }

    GroovyIntroduceVariableSettings settings = dialog.getSettings();

    final String varName = settings.getEnteredName();
    PsiType varType = settings.getSelectedType();
    final boolean isFinal = settings.isDeclareFinal();
    final boolean replaceAllOccurrences = settings.isReplaceAllOccurrences();

    // Generating varibable declaration
    final GrVariableDeclaration varDecl = factory.createVariableDeclaration(isFinal ? new String[]{PsiModifier.FINAL} : null,
                                                                            (GrExpression)PsiUtil.skipParentheses(selectedExpr, false), varType, varName
    );

    runRefactoring(selectedExpr, editor, tempContainer, occurrences, varName, varType, replaceAllOccurrences, varDecl);

    return true;

  }

  private static boolean checkInFieldInitializer(GrExpression expr) {
    PsiElement parent = expr.getParent();
    if (parent instanceof GrClosableBlock) {
      return false;
    }
    if (parent instanceof GrField && expr == ((GrField) parent).getInitializerGroovy()) {
      return true;
    }
    if (parent instanceof GrExpression) {
      return checkInFieldInitializer(((GrExpression) parent));
    }
    return false;
  }

  /**
   * Inserts new variable declaratrions and replaces occurrences
   */
  void runRefactoring(final GrExpression selectedExpr,
                      final Editor editor,
                      final GroovyPsiElement tempContainer,
                      final PsiElement[] occurrences,
                      final String varName,
                      final PsiType varType, final boolean replaceAllOccurrences,
                      final GrVariableDeclaration varDecl) {
    final Project project = selectedExpr.getProject();
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);
    // Marker for caret posiotion
    final Runnable runnable = new Runnable() {
      public void run() {
        try {
          /* insert new variable */
          GroovyRefactoringUtil.sortOccurrences(occurrences);
          if (occurrences.length == 0 || !(occurrences[0] instanceof GrExpression)) {
            throw new IncorrectOperationException("Wrong expression occurrence");
          }
          GrExpression firstOccurrence;
          if (replaceAllOccurrences) {
            firstOccurrence = ((GrExpression) occurrences[0]);
          } else {
            firstOccurrence = selectedExpr;
          }

          assert varDecl.getVariables().length > 0;

          resolveLocalConflicts(tempContainer, varDecl.getVariables()[0]);
          // Replace at the place of first occurrence

          GrVariable insertedVar = replaceOnlyExpression(firstOccurrence, selectedExpr, tempContainer, varDecl);
          boolean alreadyDefined = insertedVar != null;
          if (insertedVar == null) {
            // Insert before first occurrence
            insertedVar = insertVariableDefinition(tempContainer, selectedExpr, occurrences, replaceAllOccurrences, varDecl, factory);
          }

          insertedVar.setType(varType);

          //Replace other occurrences
          GrReferenceExpression refExpr = factory.createReferenceExpressionFromText(varName);
          if (replaceAllOccurrences) {
            ArrayList<PsiElement> replaced = new ArrayList<PsiElement>();
            for (PsiElement occurrence : occurrences) {
              if (!(alreadyDefined && firstOccurrence.equals(occurrence))) {
                if (occurrence instanceof GrExpression) {
                  GrExpression element = (GrExpression) occurrence;
                  if (element instanceof GrClosableBlock && element.getParent() instanceof GrMethodCallExpression) {
                    replaced.add(((GrMethodCallExpression) element.getParent()).replaceClosureArgument(((GrClosableBlock) element), refExpr));
                  } else {
                    replaced.add(element.replaceWithExpression(refExpr, true));
                  }
                  // For caret position
                  if (occurrence.equals(selectedExpr)) {
                    refreshPositionMarker(replaced.get(replaced.size() - 1));
                  }
                  refExpr = factory.createReferenceExpressionFromText(varName);
                } else {
                  throw new IncorrectOperationException("Expression occurrence to be replaced is not instance of GroovyPsiElement");
                }
              }
            }
            if (editor != null) {
              // todo implement it...
//              final PsiElement[] replacedOccurrences = replaced.toArray(new PsiElement[replaced.size()]);
//              highlightReplacedOccurrences(myProject, editor, replacedOccurrences);
            }
          } else {
            if (!alreadyDefined) {
              if (selectedExpr instanceof GrClosableBlock && selectedExpr.getParent() instanceof GrMethodCallExpression) {
                refreshPositionMarker(((GrMethodCallExpression) selectedExpr.getParent()).replaceClosureArgument(((GrClosableBlock) selectedExpr), refExpr));
              } else {
                refreshPositionMarker(selectedExpr.replaceWithExpression(refExpr, true));
              }
            }
          }
          // Setting caret to ogical position
          if (editor != null && getPositionMarker() != null) {
            editor.getCaretModel().moveToOffset(getPositionMarker().getTextRange().getEndOffset());
            editor.getSelectionModel().removeSelection();
          }
        } catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    };

    CommandProcessor.getInstance().executeCommand(
        project,
        new Runnable() {
          public void run() {
            ApplicationManager.getApplication().runWriteAction(runnable);
          }
        }, REFACTORING_NAME, null);
  }

  private static void resolveLocalConflicts(PsiElement tempContainer, GrVariable varDef) {
    for (PsiElement child : tempContainer.getChildren()) {
      if (child instanceof GrReferenceExpression &&
          !child.getText().contains(".")) {
        PsiReference psiReference = child.getReference();
        if (psiReference != null) {
          final PsiElement resolved = psiReference.resolve();
          if (resolved != null) {
            String fieldName = getFieldName(resolved);
            if (fieldName != null &&
                varDef.getName().equals(fieldName)) {
              GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(tempContainer.getProject());
              ((GrReferenceExpression) child).replaceWithExpression(factory.createExpressionFromText("this." + child.getText()), true);
            }
          }
        }
      } else {
        resolveLocalConflicts(child, varDef);
      }
    }
  }

  @Nullable
  private static String getFieldName(PsiElement element) {
    if (element instanceof GrAccessorMethod) element = ((GrAccessorMethod) element).getProperty();
    return element instanceof GrField ? ((GrField) element).getName() : null;
  }

  private void refreshPositionMarker(PsiElement position) {
    if (positionElement == null && position != null) {
      positionElement = position;
    }
  }

  private PsiElement getPositionMarker() {
    return positionElement;
  }

  private GrVariable insertVariableDefinition(GroovyPsiElement tempContainer, GrExpression selectedExpr,
                                              PsiElement[] occurrences, boolean replaceAllOccurrences,
                                              GrVariableDeclaration varDecl, GroovyPsiElementFactory factory) throws IncorrectOperationException {
    LOG.assertTrue(occurrences.length > 0);
    GrStatement anchorElement = (GrStatement)GroovyRefactoringUtil.calculatePositionToInsertBefore(tempContainer, selectedExpr, occurrences, replaceAllOccurrences);
    LOG.assertTrue(anchorElement != null);
    PsiElement realContainer = anchorElement.getParent();

    assert GroovyRefactoringUtil.isAppropriateContainerForIntroduceVariable(realContainer);

    if (!(realContainer instanceof GrLoopStatement)) {
      if (realContainer instanceof GrStatementOwner) {
        GrStatementOwner block = (GrStatementOwner) realContainer;
        varDecl = (GrVariableDeclaration) block.addStatementBefore(varDecl, (GrStatement) anchorElement);
      }
    } else {
      // To replace branch body correctly
      String refId = varDecl.getVariables()[0].getName();
      GrBlockStatement newBody;
      if (anchorElement.equals(selectedExpr)) {
        newBody = factory.createBlockStatement(varDecl);
      } else {
        replaceExpressionOccurrencesInStatement(anchorElement, selectedExpr, refId, replaceAllOccurrences);
        newBody = factory.createBlockStatement(varDecl, anchorElement);
      }

      varDecl = (GrVariableDeclaration) newBody.getBlock().getStatements()[0];

      GrCodeBlock tempBlock = ((GrBlockStatement) ((GrLoopStatement) realContainer).replaceBody(newBody)).getBlock();
      refreshPositionMarker(tempBlock.getStatements()[tempBlock.getStatements().length - 1]);
    }

    return varDecl.getVariables()[0];
  }

  private static void replaceExpressionOccurrencesInStatement(GrStatement stmt, GrExpression expr, String refText, boolean replaceAllOccurrences)
      throws IncorrectOperationException {
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(stmt.getProject());
    GrReferenceExpression refExpr = factory.createReferenceExpressionFromText(refText);
    if (!replaceAllOccurrences) {
      expr.replaceWithExpression(refExpr, true);
    } else {
      PsiElement[] occurrences = GroovyRefactoringUtil.getExpressionOccurrences(expr, stmt);
      for (PsiElement occurrence : occurrences) {
        if (occurrence instanceof GrExpression) {
          GrExpression grExpression = (GrExpression) occurrence;
          grExpression.replaceWithExpression(refExpr, true);
          refExpr = factory.createReferenceExpressionFromText(refText);
        } else {
          throw new IncorrectOperationException();
        }
      }
    }
  }

  /**
   * Replaces an expression occurrence by appropriate variable declaration
   */
  @Nullable
  private GrVariable replaceOnlyExpression(@NotNull GrExpression expr,
                                           GrExpression selectedExpr,
                                           @NotNull PsiElement context,
                                           @NotNull GrVariableDeclaration definition) throws IncorrectOperationException {
    if (context.equals(expr.getParent()) &&
        !(context instanceof GrLoopStatement) && !(context instanceof GrClosableBlock)) {
      definition = expr.replaceWithStatement(definition);
      if (expr.equals(selectedExpr)) {
        refreshPositionMarker(definition);
      }

      return definition.getVariables()[0];
    }
    return null;
  }

  private boolean tempContainerNotFound(final Project project) {
    String message = GroovyRefactoringBundle.message("refactoring.is.not.supported.in.the.current.context", REFACTORING_NAME);
    showErrorMessage(project, null, message);
    return false;
  }

  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, @Nullable DataContext dataContext) {
    // Does nothing
  }

  protected abstract GroovyIntroduceVariableDialog getDialog(final Project project, Editor editor, GrExpression expr,
                                                             PsiType type, PsiElement[] occurrences, boolean decalreFinal,
                                                             Validator validator);

  protected abstract void showErrorMessage(Project project, Editor editor, String message);

  protected abstract void highlightOccurrences(final Project project, Editor editor, final PsiElement[] replacedOccurrences);

  protected abstract boolean reportConflicts(final ArrayList<String> conflicts, final Project project);

  public interface Validator extends NameValidator {
    boolean isOK(GroovyIntroduceVariableDialog dialog);
  }


}
