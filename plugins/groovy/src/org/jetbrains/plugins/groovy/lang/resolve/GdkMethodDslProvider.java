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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.dsl.GdslMembersHolderConsumer;
import org.jetbrains.plugins.groovy.dsl.GroovyClassDescriptor;
import org.jetbrains.plugins.groovy.dsl.dsltop.GdslMembersProvider;
import org.jetbrains.plugins.groovy.dsl.holders.CustomMembersHolder;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrGdkMethodImpl;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
@SuppressWarnings({"MethodMayBeStatic"})
public class GdkMethodDslProvider implements GdslMembersProvider {
  private static final Key<CachedValue<GdkMethodHolder>> METHOD_KEY = Key.create("Category methods");

  public void category(String className, GdslMembersHolderConsumer consumer) {
    processCategoryMethods(className, consumer, false);
  }

  public void category(String className, final boolean isStatic, GdslMembersHolderConsumer consumer) {
    processCategoryMethods(className, consumer, isStatic);
  }

  public static void processCategoryMethods(final String className, final GdslMembersHolderConsumer consumer, final boolean isStatic) {
    final GlobalSearchScope scope = consumer.getResolveScope();
    final PsiClass categoryClass = JavaPsiFacade.getInstance(consumer.getProject()).findClass(className, scope);
    if (categoryClass == null) {
      return;
    }

    final VolatileNotNullLazyValue<GdkMethodHolder> methodsMap = new VolatileNotNullLazyValue<GdkMethodHolder>() {
      @NotNull
      @Override
      protected GdkMethodHolder compute() {
        return retrieveMethodMap(consumer.getProject(), scope, isStatic, categoryClass);
      }
    };

    consumer.addMemberHolder(new CustomMembersHolder() {

      @Override
      public boolean processMembers(GroovyClassDescriptor descriptor, PsiScopeProcessor processor, ResolveState state) {
        return methodsMap.getValue().processMethods(descriptor, processor, state);
      }
    });
  }

  public static GdkMethodHolder retrieveMethodMap(final Project project,
                                                              final GlobalSearchScope scope,
                                                              final boolean isStatic,
                                                              @NotNull final PsiClass categoryClass) {
    return CachedValuesManager.getManager(project)
      .getCachedValue(categoryClass, METHOD_KEY, new CachedValueProvider<GdkMethodHolder>() {
        @Override
        public Result<GdkMethodHolder> compute() {
          GdkMethodHolder result = new GdkMethodHolder(categoryClass, isStatic, scope);

          final ProjectRootManager rootManager = ProjectRootManager.getInstance(project);
          final VirtualFile vfile = categoryClass.getContainingFile().getVirtualFile();
          if (vfile != null && (rootManager.getFileIndex().isInLibraryClasses(vfile) || rootManager.getFileIndex().isInLibrarySource(vfile))) {
            return Result.create(result, rootManager);
          }

          return Result.create(result, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT, rootManager);
        }
      }, false);
  }
  
  private static class GdkMethodHolder {
    private final Set<String> methodNames;
    private final MultiMap<String, PsiMethod> map;

    GdkMethodHolder(PsiClass categoryClass, boolean isStatic, GlobalSearchScope scope) {
      Set<String> methodNames = new HashSet<String>();
      MultiMap<String, PsiMethod> map = new MultiMap<String, PsiMethod>();
      PsiManager manager = PsiManager.getInstance(categoryClass.getProject());
      for (PsiMethod m : categoryClass.getMethods()) {
        final PsiParameter[] params = m.getParameterList().getParameters();
        if (params.length == 0) continue;
        final PsiType parameterType = params[0].getType();
        PsiType targetType = TypesUtil.boxPrimitiveType(TypeConversionUtil.erasure(parameterType), manager, scope);
        methodNames.add(m.getName());
        map.putValue(targetType.getCanonicalText(), new GrGdkMethodImpl(m, isStatic));
      }
      this.methodNames = methodNames;
      this.map = map;
    }

    boolean processMethods(GroovyClassDescriptor descriptor, PsiScopeProcessor processor, ResolveState state) {
      final PsiType psiType = descriptor.getPsiType();
      if (psiType == null) return true;

      NameHint nameHint = processor.getHint(NameHint.KEY);
      if (nameHint != null && !methodNames.contains(nameHint.getName(state))) {
        return true;
      }

      for (String superType : ResolveUtil.getAllSuperTypes(psiType, descriptor.getProject()).keySet()) {
        for (PsiMethod method : map.get(superType)) {
          if (!processor.execute(method, state)) {
            return false;
          }
        }
      }

      return true;

    }
  }
}
