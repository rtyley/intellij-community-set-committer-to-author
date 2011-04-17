
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrThisReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author ilyas
 */
public class GrThisReferenceExpressionImpl extends GrThisSuperReferenceExpressionBase implements GrThisReferenceExpression {
  public GrThisReferenceExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitThisExpression(this);
  }

  public String toString() {
    return "'this' reference expression";
  }

  public PsiType getType() {
    final GrReferenceExpression qualifier = getQualifier();
    if (qualifier == null) {
      GroovyPsiElement context = getFileContext();
      if (context instanceof GrTypeDefinition) {
        return createType((PsiClass)context);
      }
      else if (context instanceof GroovyFile) {
        return createType(((GroovyFile)context).getScriptClass());
      }
    }
    else {
      final PsiElement resolved = qualifier.resolve();
      if (resolved instanceof PsiClass) {
        return JavaPsiFacade.getElementFactory(getProject()).createType((PsiClass)resolved);
      }
      else {
        try {
          return JavaPsiFacade.getElementFactory(getProject()).createTypeFromText(qualifier.getText(), this);
        }
        catch (IncorrectOperationException e) {
          return null;
        }
      }
    }

    return null;
  }

  private GroovyPsiElement getFileContext() {
    GroovyPsiElement context = PsiTreeUtil.getContextOfType(this, GrTypeDefinition.class, GroovyFile.class);
    if (context instanceof GroovyFile && GroovyPsiElementFactory.DUMMY_FILE_NAME.equals(FileUtil.getNameWithoutExtension(
      ((GroovyFile)context).getName()))) {
      context = PsiTreeUtil.getContextOfType(context, true, GrTypeDefinition.class, GroovyFile.class);
    }
    return context;
  }

  private PsiType createType(PsiClass context) {
    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
    if (PsiUtil.isInStaticContext(this)) {
      return elementFactory.createTypeFromText(CommonClassNames.JAVA_LANG_CLASS + "<" + context.getName() + ">", this);
      //in case of anonymous class this code don't matter because anonymous classes can't have static methods
    }
    return elementFactory.createType(context);
  }

  @NotNull
  @Override
  public String getReferenceName() {
    return "this";
  }

  @Override
  protected PsiElement resolveInner() {
    final PsiElement resolved = super.resolveInner();
    if (resolved != null) return resolved;
    final GrReferenceExpression qualifier = getQualifier();
    if (qualifier != null) {
      return qualifier.resolve();
    }

    final GroovyPsiElement context = getFileContext();
    if (context instanceof GrTypeDefinition) {
      return context;
    }
    else if (context instanceof GroovyFile) {
      return ((GroovyFile)context).getScriptClass();
    }
    return null;
  }

}
