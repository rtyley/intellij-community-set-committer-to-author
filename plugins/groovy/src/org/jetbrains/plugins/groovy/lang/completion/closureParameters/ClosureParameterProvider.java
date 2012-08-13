/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.completion.closureParameters;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Max Medvedev
 */
public class ClosureParameterProvider {
  private final Map<String, ClosureParameterInfo> myMap = new HashMap<String, ClosureParameterInfo>();

  public ClosureParameterProvider(Project project) {
    myMap.put(GroovyCommonClassNames.DEFAULT_GROOVY_METHODS+"#eachWithIndex", new ClosureParameterInfo());

  }

  @Nullable
  public ClosureParameterInfo getParameterInfo(PsiMethod method) {
    final PsiClass aClass = method.getContainingClass();
    if (aClass == null) return null;

    final String qname = aClass.getQualifiedName();
    if (qname == null) return null;

    final String name = method.getName();

    return myMap.get(qname + "#" + name);
  }

  public static ClosureParameterProvider getInstance(Project project) {
    return ServiceManager.getService(project, ClosureParameterProvider.class);
  }
}
