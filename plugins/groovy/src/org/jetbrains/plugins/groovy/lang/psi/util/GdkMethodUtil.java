/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.scope.DelegatingScopeProcessor;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.containers.CollectionFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrReferenceResolveUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrGdkMethodImpl;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ResolverProcessor;

import java.util.Set;

/**
 * @author Max Medvedev
 */
public class GdkMethodUtil {

  private static final Logger LOG = Logger.getInstance(GdkMethodUtil.class);

  public static final Set<String> COLLECTION_METHOD_NAMES = CollectionFactory.newSet(
    "each", "eachWithIndex", "any", "every", "reverseEach", "collect", "collectAll", "find", "findAll", "retainAll", "removeAll", "split",
    "groupBy", "groupEntriesBy", "findLastIndexOf", "findIndexValues", "findIndexOf"
  );
  @NonNls public static final String WITH = "with";
  @NonNls public static final String USE = "use";
  @NonNls public static final String EACH_WITH_INDEX = "eachWithIndex";
  @NonNls public static final String INJECT = "inject";
  @NonNls public static final String EACH_PERMUTATION = "eachPermutation";
  @NonNls public static final String WITH_DEFAULT = "withDefault";
  @NonNls public static final String SORT = "sort";
  @NonNls public static final String WITH_STREAM = "withStream";
  @NonNls public static final String WITH_STREAMS = "withStreams";
  @NonNls public static final String WITH_OBJECT_STREAMS = "withObjectStreams";

  private GdkMethodUtil() {
  }

  public static boolean categoryIteration(GrClosableBlock place, final PsiScopeProcessor processor) {
    final GrMethodCall call = checkMethodCall(place, USE);
    if (call == null) return true;

    final GrClosableBlock[] closures = call.getClosureArguments();
    final GrExpression[] args = call.getExpressionArguments();
    int last = args.length - 1;
    if (!(closures.length == 1 && place.equals(closures[0])) &&
        !(args.length > 0 && place.equals(args[last]))) {
      return true;
    }

    if (!(call.resolveMethod() instanceof GrGdkMethod)) return true;

    final DelegatingScopeProcessor delegate = new DelegatingScopeProcessor(processor) {
      @Override
      public boolean execute(PsiElement element, ResolveState state) {
        if (element instanceof PsiMethod) {
          if (!((PsiMethod)element).hasModifierProperty(PsiModifier.STATIC)) return true;
          if (((PsiMethod)element).getParameterList().getParametersCount() == 0) return true;
          return processor.execute(new GrGdkMethodImpl((PsiMethod)element, false), state);
        }
        else {
          return processor.execute(element, state);
        }
      }
    };
    for (GrExpression arg : args) {
      if (arg instanceof GrReferenceExpression) {
        final PsiElement resolved = ((GrReferenceExpression)arg).resolve();
        if (resolved instanceof PsiClass) {
          if (!resolved.processDeclarations(delegate, ResolveState.initial().put(ResolverProcessor.RESOLVE_CONTEXT, call), null, place)) {
            return false;
          }
        }
      }
    }
    return true;
  }

  public static boolean withIteration(GrClosableBlock block, final PsiScopeProcessor processor, GroovyPsiElement place) {
    GrMethodCall call = checkMethodCall(block, WITH);
    if (call == null) return true;
    final GrExpression invoked = call.getInvokedExpression();
    LOG.assertTrue(invoked instanceof GrReferenceExpression);
    final GrExpression qualifier = ((GrReferenceExpression)invoked).getQualifier();
    if (qualifier == null) return true;
    if (!GrReferenceResolveUtil.processQualifier(processor, qualifier, place)) return false;

    return true;
  }

  @Nullable
  private static GrMethodCall checkMethodCall(GrClosableBlock place, String methodName) {
    final PsiElement context = place.getContext();
    GrMethodCall call = null;
    if (context instanceof GrMethodCall) {
      call = (GrMethodCall)context;
    }
    else if (context instanceof GrArgumentList) {
      final PsiElement ccontext = context.getContext();
      if (ccontext instanceof GrMethodCall) {
        call = (GrMethodCall)ccontext;
      }
    }
    if (call == null) return null;
    final GrExpression invoked = call.getInvokedExpression();
    if (!(invoked instanceof GrReferenceExpression) || !methodName.equals(((GrReferenceExpression)invoked).getReferenceName())) {
      return null;
    }
    return call;
  }

  public static boolean isInUseScope(GroovyResolveResult resolveResult) {
    if (resolveResult != null && resolveResult.getElement() instanceof GrGdkMethod) return false;
    return resolveResult != null && isInUseScope(resolveResult.getCurrentFileResolveContext(), resolveResult.getElement());
  }

  public static boolean isInUseScope(@Nullable PsiElement context, @Nullable PsiElement method) {
    if (method instanceof GrGdkMethod) return false;
    if (context instanceof GrMethodCall && context.isValid()) {
      final GrExpression expression = ((GrMethodCall)context).getInvokedExpression();
      if (expression instanceof GrReferenceExpression) {
        final PsiElement resolved = ((GrReferenceExpression)expression).resolve();
        if (resolved instanceof GrGdkMethod && USE.equals(((GrGdkMethod)resolved).getStaticMethod().getName())) {
          return true;
        }
      }
    }
    return false;
  }

  public static boolean isInWithContext(GroovyPsiElement resolveContext) {
    if (resolveContext instanceof GrExpression) {
      final PsiElement parent = resolveContext.getParent();
      if (parent instanceof GrReferenceExpression && ((GrReferenceExpression)parent).getQualifier() == resolveContext) {
        final PsiElement pparent = parent.getParent();
        if (pparent instanceof GrMethodCall) {
          final PsiMethod method = ((GrMethodCall)pparent).resolveMethod();
          if (method instanceof GrGdkMethod && WITH.equals(method.getName())) {
            return true;
          }
        }
      }
    }
    return false;
  }
}
