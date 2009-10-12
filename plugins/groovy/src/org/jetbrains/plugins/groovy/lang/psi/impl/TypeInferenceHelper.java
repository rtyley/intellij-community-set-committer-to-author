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
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ConcurrentWeakHashMap;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrClassInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAEngine;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeDfaInstance;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypesSemilattice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * @author ven
 */
public class TypeInferenceHelper {
  public TypeInferenceHelper(Project project) {
    ((PsiManagerEx) PsiManager.getInstance(project)).registerRunnableToRunOnAnyChange(new Runnable() {
      public void run() {
        myCalculatedTypeInferences.clear();
      }
    });
  }

  private final ConcurrentWeakHashMap<GroovyPsiElement, Map<GrReferenceExpression, PsiType>> myCalculatedTypeInferences =
      new ConcurrentWeakHashMap<GroovyPsiElement, Map<GrReferenceExpression, PsiType>>();

  @Nullable
  public PsiType getInferredType(GrReferenceExpression refExpr) {

    GroovyPsiElement scope = PsiTreeUtil.getParentOfType(refExpr, GrMethod.class, GrClosableBlock.class, GrClassInitializer.class, GroovyFileBase.class);
    if (scope instanceof GrMethod) {
      scope = ((GrMethod) scope).getBlock();
    } else if (scope instanceof GrClassInitializer) {
      scope = ((GrClassInitializer) scope).getBlock();
    }


    if (scope != null) {
      Set<GroovyPsiElement> scopes = myScopesBeingInferred.get();
      if (scopes == null) {
        scopes = new HashSet<GroovyPsiElement>();
        myScopesBeingInferred.set(scopes);
      }

      if (scopes.contains(scope)) {
        return getTypeBinding(refExpr);
      }

      try {
        scopes.add(scope);
        Map<GrReferenceExpression, PsiType> map = myCalculatedTypeInferences.get(scope);
        if (map == null) {
          map = inferTypes((GrControlFlowOwner) scope);
          myCalculatedTypeInferences.put(scope, map);
        }
        return map.get(refExpr);
      } finally {
        scopes.remove(scope);
      }
    }

    return null;
  }

  private static final ThreadLocal<Map<String, PsiType>> myCurrentEnvironment = new ThreadLocal<Map<String, PsiType>>();
  private static final ThreadLocal<Set<GroovyPsiElement>> myScopesBeingInferred = new ThreadLocal<Set<GroovyPsiElement>>();

  public PsiType doWithInferenceDisabled(Computable<PsiType> computable) {
    return doInference(computable, Collections.<String, PsiType>emptyMap());
  }

  public PsiType doInference(Computable<PsiType> computable, @NotNull Map<String, PsiType> bindings) {
    final Map<String, PsiType> oldBindings = myCurrentEnvironment.get();
    try {
      myCurrentEnvironment.set(bindings);
      return computable.compute();
    } finally {
      myCurrentEnvironment.set(oldBindings);
    }
  }

  public PsiType getTypeBinding(GrReferenceExpression refExpr) {
    if (refExpr.getQualifierExpression() == null) {
      final String refName = refExpr.getReferenceName();
      final Map<String, PsiType> env = myCurrentEnvironment.get();
      if (env != null) {
        return env.get(refName);
      }
    }

    return null;
  }

  private Map<GrReferenceExpression, PsiType> inferTypes(GrControlFlowOwner owner) {
    final Instruction[] flow = owner.getControlFlow();

    final TypeDfaInstance dfaInstance = new TypeDfaInstance();
    final TypesSemilattice semilattice = new TypesSemilattice(owner.getManager());
    final DFAEngine<Map<String, PsiType>> engine = new DFAEngine<Map<String, PsiType>>(flow, dfaInstance, semilattice);

    final ArrayList<Map<String, PsiType>> infos = engine.performDFA();
    Map<GrReferenceExpression, PsiType> result = new HashMap<GrReferenceExpression, PsiType>();
    for (int i = 0; i < flow.length; i++) {
      Instruction instruction = flow[i];
      final PsiElement element = instruction.getElement();
      if (element instanceof GrReferenceExpression) {
        final GrReferenceExpression ref = (GrReferenceExpression) element;
        if (ref.getQualifierExpression() == null) {
          final String refName = ref.getReferenceName();
          if (refName != null) {
            final PsiType type = infos.get(i).get(refName);
            if (type != null) {
              result.put(ref, type);
            }
          }
        }
      }
    }

    return result;
  }

}
