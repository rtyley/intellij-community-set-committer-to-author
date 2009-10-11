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
package org.jetbrains.plugins.groovy.lang.psi.expectedTypes;

import com.intellij.psi.PsiType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiClassType;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil.createType;

/**
 * @author ven
 */
public  class SupertypeConstraint extends TypeConstraint {
  private final PsiType myDefaultType;

  protected SupertypeConstraint(PsiType type, PsiType defaultType) {
    super(type);
    myDefaultType = defaultType;
  }

  public boolean satisfied(PsiType type){
    return type.isAssignableFrom(myType);
  }

  public PsiType getDefaultType() {
    return myDefaultType;
  }

  public static SupertypeConstraint create (PsiType type, PsiType defaultType) {
    return new SupertypeConstraint(type, defaultType);
  }

  public static SupertypeConstraint create (String fqName, String defaultFqName, PsiElement context) {
    return new SupertypeConstraint(createType(fqName, context),
                                 createType(defaultFqName, context));
  }

  public static SupertypeConstraint create (PsiType type) {
    return new SupertypeConstraint(type, type);
  }

  public static SupertypeConstraint create (String fqName, PsiElement context) {
    PsiClassType type = createType(fqName, context);
    return new SupertypeConstraint(type, type);
  }
}
