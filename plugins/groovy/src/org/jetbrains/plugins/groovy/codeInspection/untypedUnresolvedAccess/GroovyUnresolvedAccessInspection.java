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

package org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import static org.jetbrains.plugins.groovy.annotator.GroovyAnnotator.isDeclarationAssignment;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Medvedev
 * Date: Mar 10, 2009
 * Time: 5:04:31 PM
 * To change this template use File | Settings | File Templates.
 */
public class GroovyUnresolvedAccessInspection extends BaseInspection {
  protected BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return PROBABLE_BUGS;
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return "Access to unresolved expression";
  }


  @Override
  protected String buildErrorString(Object... args) {
    return "Can not resolve symbol '#ref'";
  }

  private static class Visitor extends BaseInspectionVisitor {
    @Override
    public void visitReferenceExpression(GrReferenceExpression refExpr) {
      super.visitReferenceExpression(refExpr);

      GroovyResolveResult resolveResult = refExpr.advancedResolve();

      PsiElement resolved = resolveResult.getElement();
      if (resolved != null) {
        if (isDeclarationAssignment(refExpr) || resolved instanceof PsiPackage) return;
      }
      else {
        GrExpression qualifier = refExpr.getQualifierExpression();
        if (qualifier == null && isDeclarationAssignment(refExpr)) return;
      }

      if (resolved == null) {
        PsiElement refNameElement = refExpr.getReferenceNameElement();
        registerError(refNameElement == null ? refExpr : refNameElement);
      }
    }

  }
}
