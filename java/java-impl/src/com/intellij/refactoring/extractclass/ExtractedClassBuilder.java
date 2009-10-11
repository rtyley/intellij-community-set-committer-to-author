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
package com.intellij.refactoring.extractclass;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.psi.MethodInheritanceUtils;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

class ExtractedClassBuilder {
  private static final Logger LOGGER = Logger.getInstance("com.siyeh.rpp.extractclass.ExtractedClassBuilder");

  private String className = null;
  private String packageName = null;
  private final List<PsiField> fields = new ArrayList<PsiField>(5);
  private final List<PsiMethod> methods = new ArrayList<PsiMethod>(5);
  private final List<PsiClassInitializer> initializers = new ArrayList<PsiClassInitializer>(5);
  private final List<PsiClass> innerClasses = new ArrayList<PsiClass>(5);
  private final List<PsiClass> innerClassesToMakePublic = new ArrayList<PsiClass>(5);
  private final List<PsiTypeParameter> typeParams = new ArrayList<PsiTypeParameter>();
  private final List<PsiClass> interfaces = new ArrayList<PsiClass>();

  private boolean requiresBackPointer = false;
  private String originalClassName = null;
  private String backPointerName = null;
  private Project myProject;
  private JavaCodeStyleManager myJavaCodeStyleManager;
  private Set<PsiField> myFieldsNeedingSetters;
  private Set<PsiField> myFieldsNeedingGetter;

  public void setClassName(String className) {
    this.className = className;
  }

  public void setPackageName(String packageName) {
    this.packageName = packageName;
  }

  public void setOriginalClassName(String originalClassName) {
    this.originalClassName = originalClassName;
  }

  public void addField(PsiField field) {
    fields.add(field);
  }

  public void addMethod(PsiMethod method) {
    methods.add(method);
  }

  public void addInitializer(PsiClassInitializer initializer) {
    initializers.add(initializer);
  }

  public void addInnerClass(PsiClass innerClass, boolean makePublic) {
    innerClasses.add(innerClass);
    if (makePublic) {
      innerClassesToMakePublic.add(innerClass);
    }
  }

  public void setTypeArguments(List<PsiTypeParameter> typeParams) {
    this.typeParams.clear();
    this.typeParams.addAll(typeParams);
  }

  public void setInterfaces(List<PsiClass> interfaces) {
    this.interfaces.clear();
    this.interfaces.addAll(interfaces);
  }


  public String buildBeanClass() {
    if (requiresBackPointer) {
      calculateBackpointerName();
    }
    @NonNls final StringBuffer out = new StringBuffer(1024);
    if (packageName.length() > 0) out.append("package " + packageName + ';');

    out.append("public ");
    if (hasAbstractMethod()) {
      out.append("abstract ");
    }
    out.append("class ");
    out.append(className);
    if (!typeParams.isEmpty()) {
      out.append('<');
      boolean first = true;
      for (PsiTypeParameter typeParam : typeParams) {
        if (!first) {
          out.append(',');
        }
        out.append(typeParam.getText());
        first = false;
      }
      out.append('>');
    }
    if (!interfaces.isEmpty()) {
      out.append(" implements ");
      boolean first = true;
      for (PsiClass implemented : interfaces) {
        if (!first) {
          out.append(',');
        }
        out.append(implemented.getQualifiedName());
        first = false;
      }
    }
    out.append('{');

    if (requiresBackPointer) {
      out.append("private final " + originalClassName);
      if (!typeParams.isEmpty()) {
        out.append('<');
        boolean first = true;
        for (PsiTypeParameter typeParam : typeParams) {
          if (!first) {
            out.append(',');
          }
          out.append(typeParam.getName());
          first = false;
        }
        out.append('>');
      }
      out.append(' ' + backPointerName + ";");
    }
    outputFieldsAndInitializers(out);
    if (hasNonStatic() || requiresBackPointer) {
      outputConstructor(out);
    }
    outputMethods(out);
    outputInnerClasses(out);
    out.append("}");
    return out.toString();
  }

  private boolean hasAbstractMethod() {
    for (PsiMethod method : methods) {
      if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return true;
      }
    }
    return false;
  }

  private void calculateBackpointerName() {
    final String baseName;
    if (originalClassName.indexOf((int)'.') == 0) {
      baseName = StringUtil.decapitalize(originalClassName);
    }
    else {
      final String simpleClassName = originalClassName.substring(originalClassName.lastIndexOf('.') + 1);
      baseName = StringUtil.decapitalize(simpleClassName);
    }
    String name = myJavaCodeStyleManager.propertyNameToVariableName(baseName, VariableKind.FIELD);
    if (!existsFieldWithName(name)) {
      backPointerName = name;
      return;
    }
    int counter = 1;
    while (true) {
      name = name + counter;
      if (!existsFieldWithName(name)) {
        backPointerName = name;
        return;
      }
      counter++;
    }
  }

  private boolean existsFieldWithName(String name) {
    for (PsiField field : fields) {
      final String fieldName = field.getName();
      if (name.equals(fieldName)) {
        return true;
      }
    }
    return false;
  }

  private boolean hasNonStatic() {
    for ( PsiField field : fields) {
      if (!field.hasModifierProperty(PsiModifier.STATIC)) {
        return true;
      }
    }
    for (PsiMethod method : methods) {
      if (!method.hasModifierProperty(PsiModifier.STATIC)) {
        return true;
      }
    }
    return false;
  }

  private void outputMethods(StringBuffer out) {
    for (PsiMethod method : methods) {
      outputMutatedMethod(out, method);
    }
  }

  private void outputInnerClasses(StringBuffer out) {
    for (PsiClass innerClass : innerClasses) {
      outputMutatedInnerClass(out, innerClass, innerClassesToMakePublic.contains(innerClass));
    }
  }

  private void outputMutatedInitializer(StringBuffer out, PsiClassInitializer initializer) {
    final PsiElementVisitor visitor = new Mutator(out);
    initializer.accept(visitor);
  }

  private void outputMutatedMethod(StringBuffer out, PsiMethod method) {
    final PsiElementVisitor visitor = new Mutator(out);
    method.accept(visitor);
  }

  private void outputMutatedInnerClass(StringBuffer out, PsiClass innerClass, boolean makePublic) {
    if (makePublic) {
      try {
        PsiUtil.setModifierProperty(innerClass, PsiModifier.PUBLIC, false);
      }
      catch (IncorrectOperationException e) {
        LOGGER.error(e);
      }
    }
    final PsiElementVisitor visitor = new Mutator(out);
    innerClass.accept(visitor);
  }


  private void outputFieldsAndInitializers(StringBuffer out) {
    final List<PsiClassInitializer> remainingInitializers = new ArrayList<PsiClassInitializer>(initializers);
    for (final PsiField field : fields) {
      final Iterator<PsiClassInitializer> initializersIterator = remainingInitializers.iterator();
      final int fieldOffset = field.getTextRange().getStartOffset();
      while (initializersIterator.hasNext()) {
        final PsiClassInitializer initializer = initializersIterator.next();
        if (initializer.getTextRange().getStartOffset() < fieldOffset) {
          outputMutatedInitializer(out, initializer);
          initializersIterator.remove();
        }
      }

      outputField(field, out);

      if (myFieldsNeedingGetter != null && myFieldsNeedingGetter.contains(field)) {
        out.append(PropertyUtil.generateGetterPrototype(field).getText());
        out.append("\n");
      }

      if (myFieldsNeedingSetters != null && myFieldsNeedingSetters.contains(field)) {
        out.append(PropertyUtil.generateSetterPrototype(field).getText());
        out.append("\n");
      }
    }
    for (PsiClassInitializer initializer : remainingInitializers) {
      outputMutatedInitializer(out, initializer);
    }
  }


  private String calculateStrippedName(PsiVariable variable) {
    String name = variable.getName();
    if (name == null) {
      return null;
    }
    if (variable instanceof PsiField) {
      name = myJavaCodeStyleManager.variableNameToPropertyName(name, variable.hasModifierProperty(PsiModifier.STATIC)
                                                                     ? VariableKind.STATIC_FIELD
                                                                     : VariableKind.FIELD);
    }
    return name;
  }


  private void outputConstructor(@NonNls StringBuffer out) {
    out.append("\tpublic " + className + '(');
    boolean isFirst = true;
    if (requiresBackPointer) {
      final String parameterName = myJavaCodeStyleManager.propertyNameToVariableName(backPointerName, VariableKind.PARAMETER);
      out.append(originalClassName);
      if (!typeParams.isEmpty()) {
        out.append('<');
        boolean first = true;
        for (PsiTypeParameter typeParam : typeParams) {
          if (!first) {
            out.append(',');
          }
          out.append(typeParam.getName());
          first = false;
        }
        out.append('>');
      }
      out.append(' ' + parameterName);
      isFirst = false;
    }
    for (final PsiField field : fields) {
      if (field.hasModifierProperty(PsiModifier.STATIC)) {
        continue;
      }
      if (!field.hasInitializer()) {
        continue;
      }
      final PsiExpression initializer = field.getInitializer();
      if (PsiUtil.isConstantExpression(initializer)) {
        continue;
      }
      if (!isFirst) {
        out.append(", ");
      }
      isFirst = false;
      final PsiType type = field.getType();
      final String typeText = type.getCanonicalText();
      final String name = calculateStrippedName(field);
      final String parameterName = myJavaCodeStyleManager.propertyNameToVariableName(name, VariableKind.PARAMETER);
      out.append(typeText + ' ' + parameterName);
    }

    out.append(")");
    out.append("\t{");
    if (requiresBackPointer) {
      final String parameterName = myJavaCodeStyleManager.propertyNameToVariableName(backPointerName, VariableKind.PARAMETER);
      if (backPointerName.equals(parameterName)) {
        out.append("\t\tthis." + backPointerName + " = " + parameterName + ";");
      }
      else {
        out.append("\t\t" + backPointerName + " = " + parameterName + ";");
      }

    }
    for (final PsiField field : fields) {
      if (field.hasModifierProperty(PsiModifier.STATIC)) {
        continue;
      }
      if (!field.hasInitializer()) {
        continue;
      }
      final PsiExpression initializer = field.getInitializer();
      if (PsiUtil.isConstantExpression(initializer)) {
        continue;
      }
      final String name = calculateStrippedName(field);
      final String parameterName = myJavaCodeStyleManager.propertyNameToVariableName(name, VariableKind.PARAMETER);
      final String fieldName = field.getName();
      if (fieldName.equals(parameterName)) {
        out.append("\t\tthis." + fieldName + " = " + parameterName + ";");
      }
      else {
        out.append("\t\t" + fieldName + " = " + parameterName + ";");
      }
    }
    out.append("\t}");
  }

  private void outputField(PsiField field, @NonNls StringBuffer out) {
    final PsiDocComment docComment = getJavadocForVariable(field);
    if (docComment != null) {
      out.append(docComment.getText());
    }
    final PsiType type = field.getType();
    final String typeText = type.getCanonicalText();
    final String name = field.getName();

    @NonNls String modifierString;
    if (field.hasModifierProperty(PsiModifier.PUBLIC) && field.hasModifierProperty(PsiModifier.STATIC)) {
      modifierString = "public ";
    }
    else {
      modifierString = "private ";
    }
    if (field.hasModifierProperty(PsiModifier.STATIC)) {
      modifierString += "static ";
    }
    if (field.hasModifierProperty(PsiModifier.FINAL) && (myFieldsNeedingSetters == null || !myFieldsNeedingSetters.contains(field))) {
      modifierString += "final ";
    }
    if (field.hasModifierProperty(PsiModifier.TRANSIENT)) {
      modifierString += "transient ";
    }
    final PsiModifierList modifierList = field.getModifierList();
    final PsiAnnotation[] annotations = modifierList.getAnnotations();
    for (PsiAnnotation annotation : annotations) {
      final String annotationText = annotation.getText();
      out.append(annotationText);
    }
    out.append('\t');
    out.append(modifierString);
    out.append(typeText);
    out.append(' ');
    out.append(name);
    if (field.hasInitializer()) {
      final PsiExpression initializer = field.getInitializer();
      if (PsiUtil.isConstantExpression(initializer)) {
        out.append('=');
        out.append(initializer.getText());
      }
    }
    out.append(";");
  }

  private static PsiDocComment getJavadocForVariable(PsiVariable variable) {
    final PsiElement[] children = variable.getChildren();
    for (PsiElement child : children) {
      if (child instanceof PsiDocComment) {
        return (PsiDocComment)child;
      }
    }
    return null;
  }

  public void setRequiresBackPointer(boolean requiresBackPointer) {
    this.requiresBackPointer = requiresBackPointer;
  }


  public void setProject(final Project project) {
    myProject = project;
    myJavaCodeStyleManager = JavaCodeStyleManager.getInstance(project);
  }

  public void setFieldsNeedingGetters(Set<PsiField> fieldsNeedingGetter) {
    myFieldsNeedingGetter = fieldsNeedingGetter;
  }

  public void setFieldsNeedingSetters(Set<PsiField> fieldsNeedingSetters) {
    myFieldsNeedingSetters = fieldsNeedingSetters;
  }

  private class Mutator extends JavaElementVisitor {
    @NonNls
    private final StringBuffer out;

    private Mutator(StringBuffer out) {
      super();
      this.out = out;
    }

    public void visitElement(PsiElement element) {

      super.visitElement(element);
      final PsiElement[] children = element.getChildren();
      if (children.length == 0) {
        final String text = element.getText();
        out.append(text);
      }
      else {
        for (PsiElement child : children) {
          child.accept(this);
        }
      }
    }

    public void visitReferenceExpression(PsiReferenceExpression expression) {

      final PsiElement qualifier = expression.getQualifier();
      if (qualifier == null || qualifier instanceof PsiThisExpression) {
        final PsiElement referent = expression.resolve();
        if (referent instanceof PsiField) {
          final PsiField field = (PsiField)referent;

          if (fieldIsExtracted(field)) {

            final String name = field.getName();

            if (qualifier != null && name.equals(expression.getReferenceName())) {
              out.append("this.");
            }
            out.append(name);
          }
          else {
            if (field.hasModifierProperty(PsiModifier.STATIC)) {
              out.append(originalClassName + '.' + field.getName());
            }
            else {
              out.append(backPointerName + '.' + PropertyUtil.suggestGetterName(field.getProject(), field) + "()");
            }
          }
        }
        else {
          visitElement(expression);
        }
      }
      else {
        visitElement(expression);
      }
    }

    public void visitAssignmentExpression(PsiAssignmentExpression expression) {
      PsiExpression lhs = expression.getLExpression();
      final PsiExpression rhs = expression.getRExpression();

      if (isBackpointerReference(lhs) && rhs != null) {
        while (lhs instanceof PsiParenthesizedExpression) {
          lhs = ((PsiParenthesizedExpression)lhs).getExpression();
        }

        final PsiReferenceExpression reference = (PsiReferenceExpression)lhs;
        assert reference != null;
        final PsiField field = (PsiField)reference.resolve();
        final PsiJavaToken sign = expression.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        assert field != null;
        if (!field.hasModifierProperty(PsiModifier.STATIC)) {
          delegate(rhs, field, sign, tokenType, backPointerName);
        }
        else {
          visitElement(expression);
        }
      }
      else {
        visitElement(expression);
      }
    }

    private void delegate(final PsiExpression rhs, final PsiField field, final PsiJavaToken sign, final IElementType tokenType,
                          final String fieldName) {
      if (tokenType.equals(JavaTokenType.EQ)) {
        final String setterName = PropertyUtil.suggestSetterName(field.getProject(), field);
        out.append(fieldName + '.' + setterName + '(');
        rhs.accept(this);
        out.append(')');
      }
      else {
        final String operator = sign.getText().substring(0, sign.getTextLength() - 1);
        final String setterName = PropertyUtil.suggestSetterName(field.getProject(), field);
        out.append(fieldName + '.' + setterName + '(');
        final String getterName = PropertyUtil.suggestGetterName(field.getProject(), field);
        out.append(fieldName + '.' + getterName + "()");
        out.append(operator);
        rhs.accept(this);
        out.append(')');
      }
    }


    public void visitPostfixExpression(PsiPostfixExpression expression) {
      outputUnaryExpression(expression, expression.getOperand(), expression.getOperationSign());
    }

    public void visitPrefixExpression(PsiPrefixExpression expression) {
      outputUnaryExpression(expression, expression.getOperand(), expression.getOperationSign());
    }

    private void outputUnaryExpression(final PsiExpression expression, PsiExpression operand, final PsiJavaToken sign) {
      final IElementType tokenType = sign.getTokenType();
      if (isBackpointerReference(operand) && (tokenType.equals(JavaTokenType.PLUSPLUS) || tokenType.equals(JavaTokenType.MINUSMINUS))) {
        while (operand instanceof PsiParenthesizedExpression) {
          operand = ((PsiParenthesizedExpression)operand).getExpression();
        }
        final PsiReferenceExpression reference = (PsiReferenceExpression)operand;

        final String operator;
        if (tokenType.equals(JavaTokenType.PLUSPLUS)) {
          operator = "+";
        }
        else {
          operator = "-";
        }
        final PsiField field = (PsiField)reference.resolve();
        assert field != null;
        if (!field.hasModifierProperty(PsiModifier.STATIC)) {
          out.append(backPointerName +
                     '.' + PropertyUtil.suggestSetterName(field.getProject(), field) +
                     '(' +
                     backPointerName +
                     '.' + PropertyUtil.suggestGetterName(field.getProject(), field) +
                     "()" +
                     operator +
                     "1)");
        }
        else {
          visitElement(expression);
        }
      }
      else {
        visitElement(expression);
      }
    }

    private boolean isBackpointerReference(PsiExpression expression) {
      return BackpointerUtil.isBackpointerReference(expression, new Condition<PsiField>() {
        public boolean value(final PsiField psiField) {
          return !fieldIsExtracted(psiField);
        }
      });
    }


    public void visitThisExpression(PsiThisExpression expression) {
      out.append(backPointerName);
    }

    private boolean fieldIsExtracted(PsiField field) {
      for (PsiField psiField :  fields) {
        if (psiField.equals(field)) {
          return true;
        }
      }
      final PsiClass containingClass = field.getContainingClass();
      return innerClasses.contains(containingClass);
    }

    public void visitMethodCallExpression(PsiMethodCallExpression call) {
      final PsiReferenceExpression expression = call.getMethodExpression();
      final PsiElement qualifier = expression.getQualifier();
      if (qualifier == null || qualifier instanceof PsiThisExpression) {
        final PsiMethod method = call.resolveMethod();
        if (method != null && !isCompletelyMoved(method)) {
          final String methodName = method.getName();
          if (method.hasModifierProperty(PsiModifier.STATIC)) {
            out.append(originalClassName + '.' + methodName);
          }
          else {
            out.append(backPointerName + '.' + methodName);
          }
          final PsiExpressionList argumentList = call.getArgumentList();
          argumentList.accept(this);
        }
        else {
          visitElement(call);
        }
      }
      else {
        visitElement(call);
      }
    }

    public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
      final String referenceText = reference.getCanonicalText();
      out.append(referenceText);
    }

    private boolean isCompletelyMoved(PsiMethod method) {
      return methods.contains(method) && !
        MethodInheritanceUtils.hasSiblingMethods(method);
    }
  }
}

