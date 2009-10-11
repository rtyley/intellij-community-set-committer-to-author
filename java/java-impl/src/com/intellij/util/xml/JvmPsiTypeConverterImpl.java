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
package com.intellij.util.xml;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.util.containers.BidirectionalMap;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataCache;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author peter
 */
public class JvmPsiTypeConverterImpl extends JvmPsiTypeConverter implements CustomReferenceConverter<PsiType> {

  private static final BidirectionalMap<PsiType, Character> ourPrimitiveTypes = new BidirectionalMap<PsiType, Character>();
  private static final UserDataCache<JavaClassReferenceProvider, Project, Object> JVM_REFERENCE_PROVIDER = new UserDataCache<JavaClassReferenceProvider, Project, Object>("JvmPsiTypeConverterImplJVM") {
    @Override
    protected JavaClassReferenceProvider compute(Project project, Object p) {
      JavaClassReferenceProvider provider = new JavaClassReferenceProvider(project);
      provider.setOption(JavaClassReferenceProvider.JVM_FORMAT, Boolean.TRUE);
      provider.setSoft(true);
      return provider;
    }
  };
  private static final UserDataCache<JavaClassReferenceProvider, Project, Object> REFERENCE_PROVIDER = new UserDataCache<JavaClassReferenceProvider, Project, Object>("JvmPsiTypeConverterImplPlain") {
    @Override
    protected JavaClassReferenceProvider compute(Project project, Object p) {
      JavaClassReferenceProvider provider = new JavaClassReferenceProvider(project);
      provider.setSoft(true);
      return provider;
    }
  };

  static {
    ourPrimitiveTypes.put(PsiType.BYTE, 'B');
    ourPrimitiveTypes.put(PsiType.CHAR, 'C');
    ourPrimitiveTypes.put(PsiType.DOUBLE, 'D');
    ourPrimitiveTypes.put(PsiType.FLOAT, 'F');
    ourPrimitiveTypes.put(PsiType.INT, 'I');
    ourPrimitiveTypes.put(PsiType.LONG, 'L');
    ourPrimitiveTypes.put(PsiType.SHORT, 'S');
    ourPrimitiveTypes.put(PsiType.BOOLEAN, 'Z');
  }

  public PsiType fromString(final String s, final ConvertContext context) {
    return convertFromString(s, context);
  }

  @Nullable
  public static PsiType convertFromString(final String s, final ConvertContext context) {
    if (s == null) return null;

    if (s.startsWith("[")) {
      int arrayDimensions = getArrayDimensions(s);

      if (arrayDimensions >= s.length()) {
        return null;
      }

      final char c = s.charAt(arrayDimensions);
      if (c == 'L') {
        if (!s.endsWith(";")) return null;
        final PsiClass aClass =
          DomJavaUtil.findClass(s.substring(arrayDimensions + 1, s.length() - 1), context.getFile(), context.getModule(), null);
        return aClass == null ? null : makeArray(arrayDimensions, createType(aClass));
      }

      if (s.length() == arrayDimensions + 1) {
        final List<PsiType> list = ourPrimitiveTypes.getKeysByValue(c);
        return list == null || list.isEmpty() ? null : makeArray(arrayDimensions, list.get(0));
      }

      return null;
    }

    final PsiClass aClass1 = DomJavaUtil.findClass(s, context.getFile(), context.getModule(), null);
    return aClass1 == null ? null : createType(aClass1);
  }

  private static int getArrayDimensions(final String s) {
    int arrayDimensions = 0;

    while (arrayDimensions < s.length() && s.charAt(arrayDimensions) == '[') {
      arrayDimensions++;
    }
    return arrayDimensions;
  }

  private static PsiClassType createType(final PsiClass aClass) {
    return JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory().createType(aClass);
  }

  private static PsiType makeArray(final int dimensions, final PsiType type) {
    return dimensions == 0 ? type : makeArray(dimensions - 1, new PsiArrayType(type));
  }

  public String toString(final PsiType psiType, final ConvertContext context) {
    return convertToString(psiType);
  }

  @Nullable
  public static String convertToString(final PsiType psiType) {
    if (psiType instanceof PsiArrayType) {
      return '[' + toStringArray(((PsiArrayType)psiType).getComponentType());
    }
    else if (psiType instanceof PsiClassType) {
      return psiType.getCanonicalText();
    }
    return null;
  }

  @NonNls @Nullable
  private static String toStringArray(final PsiType psiType) {
    if (psiType instanceof PsiArrayType) {
      return '[' + toStringArray(((PsiArrayType)psiType).getComponentType());
    }
    else if (psiType instanceof PsiPrimitiveType) {
      return String.valueOf(ourPrimitiveTypes.get(psiType));
    }
    else if (psiType instanceof PsiClassType) {
      return "L" + psiType.getCanonicalText() + ";";
    }
    return null;
  }

  @NotNull
  public PsiReference[] createReferences(GenericDomValue<PsiType> value, PsiElement element, ConvertContext context) {
    final PsiType psiType = value.getValue();
    final String s = value.getStringValue();
    assert s != null;
    final int dimensions = getArrayDimensions(s);
    if (dimensions > 0) {
      if (s.charAt(dimensions) == 'L' && s.endsWith(";")) {
        return JVM_REFERENCE_PROVIDER.get(value.getManager().getProject(), null).getReferencesByString(s.substring(dimensions + 1), element,
                                                                                                       element.getText().indexOf(s) + dimensions + 1);
      }
      if (psiType != null) return PsiReference.EMPTY_ARRAY;
    }
    return REFERENCE_PROVIDER.get(value.getManager().getProject(), null).getReferencesByElement(element);
  }
}
