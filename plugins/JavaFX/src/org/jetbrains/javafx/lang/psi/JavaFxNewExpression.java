package org.jetbrains.javafx.lang.psi;

import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   12.05.2010
 * Time:   18:14:11
 */
public interface JavaFxNewExpression extends JavaFxValueExpression {
  @Nullable
  JavaFxReferenceElement getReferenceElement();
}
