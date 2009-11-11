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
package org.intellij.lang.xpath.validation.inspections;

import com.intellij.codeInspection.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.SmartList;
import org.intellij.lang.xpath.XPathFileType;
import org.intellij.lang.xpath.context.ContextProvider;
import org.intellij.lang.xpath.psi.XPathElement;
import org.intellij.lang.xpath.psi.XPathExpression;
import org.intellij.lang.xpath.psi.XPathNodeTest;
import org.intellij.lang.xpath.psi.XPathPredicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class XPathInspection extends LocalInspectionTool implements CustomSuppressableInspectionTool {

    @NotNull
    public String getGroupDisplayName() {
        return "XPath";
    }

    public SuppressIntentionAction[] getSuppressActions(@Nullable PsiElement element) {
        final XPathElement e = PsiTreeUtil.getContextOfType(element, XPathElement.class, false);
        return ContextProvider.getContextProvider(e != null ? e : element).getQuickFixFactory().getSuppressActions(this);
    }

    @Nullable
    public SuppressIntentionAction[] getSuppressActions() {
        return new SuppressIntentionAction[0];
    }

    public boolean isSuppressedFor(PsiElement element) {
        return ContextProvider.getContextProvider(element.getContainingFile()).getQuickFixFactory().isSuppressedFor(element, this);
    }

    protected abstract Visitor createVisitor(InspectionManager manager, boolean isOnTheFly);

    @Nullable
    public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
        if (file.getLanguage() != XPathFileType.XPATH.getLanguage()) return null;
        final Visitor visitor = createVisitor(manager, isOnTheFly);

        file.accept(visitor);

        return visitor.getProblems();
    }

    protected static abstract class Visitor extends PsiRecursiveElementVisitor {
        protected final InspectionManager myManager;
      protected boolean myOnTheFly;
      private SmartList<ProblemDescriptor> myProblems;

      public Visitor(InspectionManager manager, boolean isOnTheFly) {
            myManager = manager;
          this.myOnTheFly = isOnTheFly;
        }

        public void visitElement(PsiElement psiElement) {
            super.visitElement(psiElement);

            if (myProblems != null) {
                final TextRange textRange = psiElement.getTextRange();
                for (ProblemDescriptor problem : myProblems) {
                    if (textRange.contains(problem.getPsiElement().getTextRange())) {
                        return;
                    }
                }
            }

            if (psiElement instanceof XPathExpression) {
                checkExpression(((XPathExpression)psiElement));
            } else if (psiElement instanceof XPathNodeTest) {
                checkNodeTest(((XPathNodeTest)psiElement));
            } else if (psiElement instanceof XPathPredicate) {
                checkPredicate((XPathPredicate)psiElement);
            }
        }

        protected void checkExpression(XPathExpression expression) { }

        protected void checkPredicate(XPathPredicate predicate) { }

        protected void checkNodeTest(XPathNodeTest nodeTest) { }

        @Nullable
        public ProblemDescriptor[] getProblems() {
            return myProblems == null ? null : myProblems.toArray(new ProblemDescriptor[myProblems.size()]);
        }

        protected void addProblem(ProblemDescriptor problem) {
            if (myProblems == null) {
                myProblems = new SmartList<ProblemDescriptor>();
            }
            myProblems.add(problem);
        }
    }
}
