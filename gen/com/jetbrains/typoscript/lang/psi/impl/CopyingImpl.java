// This is a generated file. Not intended for manual editing.
package com.jetbrains.typoscript.lang.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.jetbrains.typoscript.lang.TypoScriptElementTypes.*;
import com.jetbrains.typoscript.lang.psi.TypoScriptCompositeElementImpl;
import com.jetbrains.typoscript.lang.psi.*;

public class CopyingImpl extends TypoScriptCompositeElementImpl implements Copying {

  public CopyingImpl(ASTNode node) {
    super(node);
  }

  @Override
  @NotNull
  public List<ObjectPath> getObjectPathList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, ObjectPath.class);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof Visitor) ((Visitor)visitor).visitCopying(this);
    else super.accept(visitor);
  }

}
