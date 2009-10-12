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
package com.intellij.psi.filters.classes;

import com.intellij.psi.PsiClass;
import com.intellij.psi.filters.OrFilter;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.util.ReflectionCache;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 28.01.2003
 * Time: 20:49:29
 * To change this template use Options | File Templates.
 */
public class ThisOrAnyInnerFilter extends OrFilter{
  public ThisOrAnyInnerFilter(ElementFilter filter){
    super(filter, new AnyInnerFilter(filter));
  }

  public boolean isClassAcceptable(Class aClass){
    return ReflectionCache.isAssignable(PsiClass.class, aClass);
  }

  public String toString(){
    return "this-or-any-inner(" + getFilters().get(0).toString() + ")";
  }
}
