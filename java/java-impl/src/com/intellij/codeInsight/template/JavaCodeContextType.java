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
package com.intellij.codeInsight.template;

import com.intellij.codeInsight.completion.JavaCompletionData;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.ide.highlighter.JavaFileHighlighter;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.patterns.PlatformPatterns.psiElement;

public abstract class JavaCodeContextType extends TemplateContextType {

  protected JavaCodeContextType(@NotNull @NonNls String id,
                                @NotNull String presentableName,
                                @Nullable Class<? extends TemplateContextType> baseContextType) {
    super(id, presentableName, baseContextType);
  }

  public boolean isInContext(@NotNull final PsiFile file, final int offset) {
    if (PsiUtilBase.getLanguageAtOffset(file, offset).isKindOf(StdLanguages.JAVA)) {
      PsiElement element = file.findElementAt(offset);
      if (element instanceof PsiWhiteSpace) {
        return false;
      }
      return element != null && isInContext(element);
    }

    return false;
  }
  
  protected abstract boolean isInContext(@NotNull PsiElement element);

  @Override
  public boolean isInContext(@NotNull final FileType fileType) {
    return false;
  }

  @NotNull
  @Override
  public SyntaxHighlighter createHighlighter() {
    return new JavaFileHighlighter();
  }

  @Override
  public Document createDocument(CharSequence text, Project project) {
    if (project == null) {
      return super.createDocument(text, project);
    }
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiElementFactory factory = psiFacade.getElementFactory();
    final JavaCodeFragment fragment = factory.createCodeBlockCodeFragment((String)text, psiFacade.findPackage(""), true);
    DaemonCodeAnalyzer.getInstance(project).setHighlightingEnabled(fragment, false);
    return PsiDocumentManager.getInstance(project).getDocument(fragment);
  }
  
  public static class Generic extends JavaCodeContextType {
    public Generic() {
      super("JAVA_CODE", "Java", EverywhereContextType.class);
    }

    @Override
    protected boolean isInContext(@NotNull PsiElement element) {
      return true;
    }
  }

  public static class Statement extends JavaCodeContextType {
    public Statement() {
      super("JAVA_STATEMENT", "Statement", Generic.class);
    }

    @Override
    protected boolean isInContext(@NotNull PsiElement element) {
      return isStatementContext(element);
    }

    private static boolean isStatementContext(PsiElement element) {
      if (!Expression.isExpressionContext(element)) {
        return false;
      }

      PsiStatement statement = PsiTreeUtil.getParentOfType(element, PsiStatement.class);
      return statement != null && statement.getTextRange().getStartOffset() == element.getTextRange().getStartOffset();
    }
  }
  public static class Expression extends JavaCodeContextType {
    public Expression() {
      super("JAVA_EXPRESSION", "Expression", Generic.class);
    }

    @Override
    protected boolean isInContext(@NotNull PsiElement element) {
      return isExpressionContext(element);
    }

    private static boolean isExpressionContext(PsiElement element) {
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiJavaCodeReferenceElement)) {
        return false;
      }
      if (((PsiJavaCodeReferenceElement)parent).isQualified()) {
        return false;
      }
      if (parent.getParent() instanceof PsiMethodCallExpression) {
        return false;
      }

      if (psiElement().withParents(PsiTypeElement.class, PsiClass.class).accepts(parent)) {
        return false;
      }

      if (JavaCompletionData.INSIDE_PARAMETER_LIST.accepts(element)) {
        return false;
      }

      ProcessingContext context = new ProcessingContext();
      if (psiElement().inside(PsiExpression.class).afterLeaf(psiElement().inside(psiElement(PsiExpression.class).save("prevExpr"))).accepts(element, context)) {
        PsiExpression prevExpr = (PsiExpression)context.get("prevExpr");
        if (prevExpr.getTextRange().getEndOffset() <= element.getTextRange().getStartOffset()) {
          return false;
        }
      }
      
      return true;
    }
  }
  public static class Declaration extends JavaCodeContextType {
    public Declaration() {
      super("JAVA_DECLARATION", "Declaration", Generic.class);
    }

    @Override
    protected boolean isInContext(@NotNull PsiElement element) {
      if (Statement.isStatementContext(element) || Expression.isExpressionContext(element)) {
        return false;
      }

      return JavaCompletionData.CLASS_START.isAcceptable(element, element) || JavaCompletionData.INSIDE_PARAMETER_LIST.accepts(element);
    }
  }


}
