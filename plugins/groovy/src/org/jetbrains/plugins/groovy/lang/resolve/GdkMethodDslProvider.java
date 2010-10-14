/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.VolatileNotNullLazyValue;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.util.Function;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.dsl.GdslMembersHolderConsumer;
import org.jetbrains.plugins.groovy.dsl.GroovyClassDescriptor;
import org.jetbrains.plugins.groovy.dsl.dsltop.GdslMembersProvider;
import org.jetbrains.plugins.groovy.dsl.holders.CustomMembersHolder;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrGdkMethodImpl;

/**
 * @author Maxim.Medvedev
 */
@SuppressWarnings({"MethodMayBeStatic"})
public class GdkMethodDslProvider implements GdslMembersProvider {
  private static final Key<CachedValue<MultiMap<String, PsiMethod>>> METHOD_KEY = Key.create("Category methods");

  public void category(String className, GdslMembersHolderConsumer consumer) {
    processCategoryMethods(className, consumer, new Function<PsiMethod, PsiMethod>() {
      public PsiMethod fun(PsiMethod m) {
        return new GrGdkMethodImpl(m, false);
      }
    });
  }

  public void category(String className, final boolean isStatic, GdslMembersHolderConsumer consumer) {
    processCategoryMethods(className, consumer, new Function<PsiMethod, PsiMethod>() {
      public PsiMethod fun(PsiMethod param) {
        return new GrGdkMethodImpl(param, isStatic);
      }
    });
  }

  public void category(String className, Function<PsiMethod, PsiMethod> converter, GdslMembersHolderConsumer consumer) {
    processCategoryMethods(className, consumer, converter);
  }

  public static void processCategoryMethods(final String className, final GdslMembersHolderConsumer consumer, final Function<PsiMethod, PsiMethod> converter) {
    final GlobalSearchScope scope = consumer.getResolveScope();
    final PsiClass categoryClass = JavaPsiFacade.getInstance(consumer.getProject()).findClass(className, scope);
    if (categoryClass == null) {
      return;
    }

    final VolatileNotNullLazyValue<MultiMap<String, PsiMethod>> methodsMap = new VolatileNotNullLazyValue<MultiMap<String, PsiMethod>>() {
      @NotNull
      @Override
      protected MultiMap<String, PsiMethod> compute() {
        return retrieveMethodMap(consumer.getProject(), scope, converter, categoryClass);
      }
    };

    consumer.addMemberHolder(new CustomMembersHolder() {

      @Override
      public boolean processMembers(GroovyClassDescriptor descriptor, PsiScopeProcessor processor, ResolveState state) {
        final PsiType psiType = descriptor.getPsiType();
        if (psiType == null)  return true;

        final MultiMap<String, PsiMethod> map = methodsMap.getValue();
        for (String superType : ResolveUtil.getAllSuperTypes(psiType, descriptor.getProject()).keySet()) {
          for (PsiMethod method : map.get(superType)) {
            if (!processor.execute(method, state)) {
              return false;
            }
          }
        }

        return true;
      }
    });
  }

  public static MultiMap<String, PsiMethod> retrieveMethodMap(final Project project,
                                                              final GlobalSearchScope scope,
                                                              final Function<PsiMethod, PsiMethod> converter,
                                                              @NotNull final PsiClass categoryClass) {
    return CachedValuesManager.getManager(project)
      .getCachedValue(categoryClass, METHOD_KEY, new CachedValueProvider<MultiMap<String, PsiMethod>>() {
        @Override
        public Result<MultiMap<String, PsiMethod>> compute() {
          MultiMap<String, PsiMethod> map = new MultiMap<String, PsiMethod>();
          PsiManager manager = PsiManager.getInstance(project);
          for (PsiMethod m : categoryClass.getMethods()) {
            final PsiParameter[] params = m.getParameterList().getParameters();
            if (params.length == 0) continue;
            final PsiType parameterType = params[0].getType();
            PsiType targetType = TypesUtil.boxPrimitiveType(TypeConversionUtil.erasure(parameterType), manager, scope);
            map.putValue(targetType.getCanonicalText(), converter.fun(m));
          }
          return Result.create(map, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT, ProjectRootManager.getInstance(project));
        }
      }, false);
  }
}
