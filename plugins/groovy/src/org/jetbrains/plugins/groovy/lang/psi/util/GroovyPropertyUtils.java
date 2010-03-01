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
package org.jetbrains.plugins.groovy.lang.psi.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;

import java.beans.Introspector;

/**
 * @author ilyas
 */
public class GroovyPropertyUtils {
  private GroovyPropertyUtils() {
  }

  @Nullable
  public static PsiMethod findSetterForField(PsiField field) {
    final PsiClass containingClass = field.getContainingClass();
    final Project project = field.getProject();
    final String propertyName = PropertyUtil.suggestPropertyName(project, field);
    final boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
    return findPropertySetter(containingClass, propertyName, isStatic, true);
  }

  @Nullable
  public static PsiMethod findGetterForField(PsiField field) {
    final PsiClass containingClass = field.getContainingClass();
    final Project project = field.getProject();
    final String propertyName = PropertyUtil.suggestPropertyName(project, field);
    final boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
    return PropertyUtil.findPropertyGetter(containingClass, propertyName, isStatic, true);
  }

  @Nullable
  public static PsiMethod findPropertySetter(PsiClass aClass, String propertyName, boolean isStatic, boolean checkSuperClasses) {
    if (aClass == null) return null;
    PsiMethod[] methods;
    if (checkSuperClasses) {
      methods = aClass.getAllMethods();
    }
    else {
      methods = aClass.getMethods();
    }

    for (PsiMethod method : methods) {
      if (method.hasModifierProperty(PsiModifier.STATIC) != isStatic) continue;

      if (isSimplePropertySetter(method)) {
        if (getPropertyNameBySetter(method).equals(propertyName)) {
          return method;
        }
      }
    }

    return null;
  }

  @Nullable
  public static PsiMethod findPropertyGetter(PsiClass aClass, String propertyName, boolean isStatic, boolean checkSuperClasses) {
    if (aClass == null) return null;
    PsiMethod[] methods;
    if (checkSuperClasses) {
      methods = aClass.getAllMethods();
    }
    else {
      methods = aClass.getMethods();
    }

    for (PsiMethod method : methods) {
      if (method.hasModifierProperty(PsiModifier.STATIC) != isStatic) continue;

      if (isSimplePropertyGetter(method)) {
        if (getPropertyNameByGetter(method).equals(propertyName)) {
          return method;
        }
      }
    }

    return null;
  }

  public static boolean isSimplePropertyAccessor(PsiMethod method) {
    return isSimplePropertyGetter(method) || isSimplePropertySetter(method);
  }//do not check return type

  public static boolean isSimplePropertyGetter(PsiMethod method) {
    return isSimplePropertyGetter(method, null);
  }//do not check return type

  public static boolean isSimplePropertyGetter(PsiMethod method, String propertyName) {
    if (method == null || method.isConstructor()) return false;
    if (method.getParameterList().getParametersCount() != 0) return false;
    if (!isGetterName(method.getName())) return false;
    return (propertyName == null || propertyName.equals(getPropertyNameByGetter(method))) && method.getReturnType() != PsiType.VOID;
  }

  public static boolean isSimplePropertySetter(PsiMethod method) {
    return isSimplePropertySetter(method, null);
  }

  public static boolean isSimplePropertySetter(PsiMethod method, String propertyName) {
    if (method == null || method.isConstructor()) return false;
    if (method.getParameterList().getParametersCount() != 1) return false;
    if (!isSetterName(method.getName())) return false;
    return propertyName == null || propertyName.equals(getPropertyNameBySetter(method));
  }

  public static String getPropertyNameByGetter(PsiMethod getterMethod) {
    if (getterMethod instanceof GrAccessorMethod) {
      return ((GrAccessorMethod)getterMethod).getProperty().getName();
    }

    @NonNls String methodName = getterMethod.getName();
    if (methodName.startsWith("get") && methodName.length() > 3) {
      return decapitalize(methodName.substring(3));
    }
    else if (methodName.startsWith("is") && methodName.length() > 2 && PsiType.BOOLEAN.equals(getterMethod.getReturnType())) {
      return decapitalize(methodName.substring(2));
    }
    return methodName;
  }

  public static String getPropertyNameBySetter(PsiMethod setterMethod) {
    if (setterMethod instanceof GrAccessorMethod) {
      return ((GrAccessorMethod)setterMethod).getProperty().getName();
    }

    @NonNls String methodName = setterMethod.getName();
    if (methodName.startsWith("set") && methodName.length() > 3) {
      return StringUtil.decapitalize(methodName.substring(3));
    }
    else {
      return methodName;
    }
  }

  @Nullable
  public static String getPropertyName(PsiMethod accessor) {
    if (isSimplePropertyGetter(accessor)) return getPropertyNameByGetter(accessor);
    if (isSimplePropertySetter(accessor)) return getPropertyNameBySetter(accessor);
    return null;
  }

  public static boolean isGetterName(@NotNull String name) {
    if (name.startsWith("get") && name.length() > 3 && isUpperCase(name.charAt(3))) return true;
    if (name.startsWith("is") && name.length() > 2 && isUpperCase(name.charAt(2))) return true;
    return false;
  }

  public static String[] suggestGettersName(@NotNull String name) {
    return new String[]{"get"+capitalize(name), "is"+capitalize(name)};
  }

  public static String[] suggestSettersName(@NotNull String name) {
    return new String[]{"set"+capitalize(name)};
  }

  public static boolean isSetterName(String name) {
    return name != null && name.startsWith("set") && name.length() > 3 && isUpperCase(name.charAt(3));
  }

  public static boolean isProperty(@Nullable PsiClass aClass, @Nullable String propertyName, boolean isStatic) {
    if (aClass == null || propertyName == null) return false;
    final PsiField field = aClass.findFieldByName(propertyName, true);
    if (field instanceof GrField && ((GrField)field).isProperty() && field.hasModifierProperty(PsiModifier.STATIC) == isStatic) return true;

    final PsiMethod getter = findPropertyGetter(aClass, propertyName, isStatic, true);
    if (getter != null && getter.hasModifierProperty(PsiModifier.PUBLIC)) return true;

    final PsiMethod setter = findPropertySetter(aClass, propertyName, isStatic, true);
    return setter != null && setter.hasModifierProperty(PsiModifier.PUBLIC);
  }

  public static boolean isProperty(GrField field) {
    final PsiClass clazz = field.getContainingClass();
    return isProperty(clazz, field.getName(), field.hasModifierProperty(PsiModifier.STATIC));
  }

  private static boolean isUpperCase(char c) {
    return Character.toUpperCase(c) == c;
  }

  /*public static boolean canBePropertyName(String name) {
    return !(name.length() > 1 && Character.isUpperCase(name.charAt(1)) && Character.isLowerCase(name.charAt(0)));
  }*/

  public static String capitalize(String s) {
    if (s.length() == 0) return s;
    if (s.length() == 1) return s.toUpperCase();
    if (Character.isUpperCase(s.charAt(1))) return s;
    final char[] chars = s.toCharArray();
    chars[0] = Character.toUpperCase(chars[0]);
    return new String(chars);
  }

  public static String decapitalize(String s) {
    return Introspector.decapitalize(s);
  }
}
