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
package org.jetbrains.plugins.groovy.lang.documentation;

import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

import java.util.List;

/**
 * @author ven
 */
public class GroovyDocumentationProvider implements DocumentationProvider {
  @Nullable
  public String getQuickNavigateInfo(PsiElement element) {
    if (element instanceof GrVariable) {
      GrVariable variable = (GrVariable) element;
      StringBuffer buffer = new StringBuffer();
      final PsiType type = variable.getDeclaredType();
      appendTypeString(buffer, type);
      buffer.append(" ");
      buffer.append(variable.getName());
      return buffer.toString();
    }
    else if (element instanceof GrReferenceExpression) {
      GrReferenceExpression refExpr = (GrReferenceExpression) element;
      StringBuffer buffer = new StringBuffer();
      PsiType type = null;
      if (refExpr.getParent() instanceof GrAssignmentExpression) {
        GrAssignmentExpression assignment = (GrAssignmentExpression) refExpr.getParent();
        if (refExpr.equals(assignment.getLValue())) {
          GrExpression rvalue = assignment.getRValue();
          if (rvalue != null) {
            type = rvalue.getType();
          }
        }
      }
      appendTypeString(buffer, type);
      buffer.append(" ");
      buffer.append(refExpr.getReferenceName());
      return buffer.toString();
    } else if (element instanceof PsiMethod) {
      StringBuffer buffer = new StringBuffer();
      PsiMethod method = (PsiMethod) element;
      if (method instanceof GrGdkMethod) {
        buffer.append("[GDK] ");
      } else {
        PsiClass hisClass = method.getContainingClass();
        if (hisClass != null) {
          String qName = hisClass.getQualifiedName();
          if (qName != null) {
            buffer.append(qName).append("\n");
          }
        }
      }

      if (!method.isConstructor()) {
        appendTypeString(buffer, method.getReturnType());
        buffer.append(" ");
      }
      buffer.append(method.getName()).append(" ");
      buffer.append("(");
      PsiParameter[] parameters = method.getParameterList().getParameters();
      for (int i = 0; i < parameters.length; i++) {
        PsiParameter parameter = parameters[i];
        if (i > 0) buffer.append(", ");
        if (parameter instanceof GrParameter) {
          buffer.append(GroovyPresentationUtil.getParameterPresentation((GrParameter) parameter, PsiSubstitutor.EMPTY));
        } else {
          PsiType type = parameter.getType();
          appendTypeString(buffer, type);
          buffer.append(" ");
          buffer.append(parameter.getName());
        }
      }
      buffer.append(")");
      return buffer.toString();
    } else if (element instanceof GrTypeDefinition) {
      return generateClassInfo((GrTypeDefinition)element);
    }

    //todo
    return null;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private String generateClassInfo(PsiClass aClass) {
    StringBuffer buffer = new StringBuffer();
    GroovyFile file = (GroovyFile) aClass.getContainingFile();

    String packageName = file.getPackageName();
    if (packageName.length() > 0) {
      buffer.append(packageName).append("\n");
    }

    final String classString =
      aClass.isInterface() ? "interface" :
      aClass instanceof PsiTypeParameter ? "type parameter" :
      aClass.isEnum() ? "enum" : "class";
    buffer.append(classString).append(" ");

    buffer.append(aClass.getName());

    if (aClass.hasTypeParameters()) {
      PsiTypeParameter[] typeParameters = aClass.getTypeParameters();

      buffer.append("<");

      for (int i = 0; i < typeParameters.length; i++) {
        if (i > 0) buffer.append(", ");

        PsiTypeParameter tp = typeParameters[i];

        buffer.append(tp.getName());

        PsiClassType[] refs = tp.getExtendsListTypes();

        if (refs.length > 0) {
          buffer.append(" extends ");

          for (int j = 0; j < refs.length; j++) {
            if (j > 0) buffer.append(" & ");
            appendTypeString(buffer, refs[j]);
          }
        }
      }

      buffer.append(">");
    }

    PsiClassType[] refs = aClass.getExtendsListTypes();
    if (refs.length > 0 || !aClass.isInterface() && !"java.lang.Object".equals(aClass.getQualifiedName())) {
      buffer.append(" extends ");
      if (refs.length == 0) {
        buffer.append("Object");
      } else {
        for (int i = 0; i < refs.length; i++) {
          if (i > 0) buffer.append(", ");
          appendTypeString(buffer, refs[i]);
        }
      }
    }

    refs = aClass.getImplementsListTypes();
    if (refs.length > 0) {
      buffer.append("\nimplements ");
      for (int i = 0; i < refs.length; i++) {
        if (i > 0) buffer.append(", ");
        appendTypeString(buffer, refs[i]);

      }
    }

    return buffer.toString();
  }


  private void appendTypeString(StringBuffer buffer, PsiType type) {
    if (type != null) {
      buffer.append(type.getCanonicalText());
    } else {
      buffer.append("def");
    }
  }

  @Nullable
  public List<String> getUrlFor(PsiElement element, PsiElement originalElement) {
    return null;
  }

  @Nullable
  public String generateDoc(PsiElement element, PsiElement originalElement) {
    //todo
    return null;
  }

  @Nullable
  public PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement element) {
    return null;
  }

  @Nullable
  public PsiElement getDocumentationElementForLink(PsiManager psiManager, String link, PsiElement context) {
    return null;
  }
}
