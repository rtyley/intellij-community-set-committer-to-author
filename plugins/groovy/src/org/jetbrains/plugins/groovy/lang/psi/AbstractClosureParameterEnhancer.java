package org.jetbrains.plugins.groovy.lang.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.ClosureSyntheticParameter;

import java.util.Arrays;
import java.util.List;

/**
 * @author peter
 */
public abstract class AbstractClosureParameterEnhancer extends GrVariableEnhancer {
  @Override
  public PsiType getVariableType(GrVariable variable) {
    if (!(variable instanceof GrParameter)) {
      return null;
    }

    assert variable.isValid();
    GrClosableBlock closure = variable instanceof ClosureSyntheticParameter
                              ? ((ClosureSyntheticParameter)variable).getClosure() : findClosureWithArgument(variable.getParent());
    if (closure == null) {
      return null;
    }

    assert closure.isValid();

    List<GrParameter> parameters = Arrays.asList(closure.getAllParameters());
    @SuppressWarnings({"SuspiciousMethodCalls"})
    int index = parameters.indexOf(variable);
    assert index >= 0 : parameters + "; " + variable;
    return TypesUtil.boxPrimitiveType(getClosureParameterType(closure, index), closure.getManager(), closure.getResolveScope());
  }

  @Nullable
  private static GrClosableBlock findClosureWithArgument(@NotNull PsiElement parent) {
    if (parent instanceof GrParameterList) {
      GrParameterList list = (GrParameterList)parent;
      if (list.getParent() instanceof GrClosableBlock) {
        return (GrClosableBlock)list.getParent();
      }
    }
    return null;
  }

  @Nullable
  protected abstract PsiType getClosureParameterType(GrClosableBlock closure, int index);
}
