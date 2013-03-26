package org.jetbrains.plugins.javaFX.fxml.descriptors;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.FxmlConstants;
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonClassNames;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * User: anna
 * Date: 1/10/13
 */
public class JavaFxPropertyAttributeDescriptor implements XmlAttributeDescriptor {
  private final String myName;
  private final PsiClass myPsiClass;

  public JavaFxPropertyAttributeDescriptor(String name, PsiClass psiClass) {
    myName = name;
    myPsiClass = psiClass;
  }

  public PsiClass getPsiClass() {
    return myPsiClass;
  }

  @Override
  public boolean isRequired() {
    return false;
  }

  @Override
  public boolean isFixed() {
    return false;
  }

  @Override
  public boolean hasIdType() {
    return false;
  }

  @Override
  public boolean hasIdRefType() {
    return false;
  }

  @Nullable
  @Override
  public String getDefaultValue() {
    return null;
  }

  @Override
  public boolean isEnumerated() {
    return getEnum() != null;
  }

  @Nullable
  @Override
  public String[] getEnumeratedValues() {
    final PsiClass enumClass = getEnum();
    if (enumClass != null) {
      final PsiField[] fields = enumClass.getFields();
      final List<String> enumConstants = new ArrayList<String>();
      for (PsiField enumField : fields) {
        if (isConstant(enumField)) {
          enumConstants.add(enumField.getName());
        }
      }
      return ArrayUtil.toStringArray(enumConstants);
    }
    return null;
  }

  protected boolean isConstant(PsiField enumField) {
    return enumField instanceof PsiEnumConstant;
  }

  protected PsiClass getEnum() {
    final PsiClass aClass = JavaFxPsiUtil.getPropertyClass(getDeclaration());
    return aClass != null && aClass.isEnum() ? aClass : null;
  }

  public PsiField getEnumConstant(String attrValue) {
    if (isEnumerated()) {
      final PsiClass aClass = getEnum();
      final PsiField fieldByName = aClass.findFieldByName(attrValue, false);
      return fieldByName != null ? fieldByName : aClass.findFieldByName(attrValue.toUpperCase(), false);
    }
    return null;
  }

  @Nullable
  @Override
  public String validateValue(XmlElement context, String value) {
    if (context instanceof XmlAttributeValue) {
      final XmlAttributeValue xmlAttributeValue = (XmlAttributeValue)context;
      final PsiElement parent = xmlAttributeValue.getParent();
      if (parent instanceof XmlAttribute) {
        if (JavaFxPsiUtil.checkIfAttributeHandler((XmlAttribute)parent)) {
          if (value.startsWith("#")) {
            if (JavaFxPsiUtil.getControllerClass(context.getContainingFile()) == null) {
              return "No controller specified for top level element";
            }
          }
          else {
            if (JavaFxPsiUtil.parseInjectedLanguages((XmlFile)context.getContainingFile()).isEmpty()) {
              return "Page language not specified.";
            }
          }
        } else if (FxmlConstants.FX_ID.equals(((XmlAttribute)parent).getName())) {
          final PsiClass controllerClass = JavaFxPsiUtil.getControllerClass(context.getContainingFile());
          if (controllerClass != null) {
            final XmlTag xmlTag = ((XmlAttribute)parent).getParent();
            if (xmlTag != null) {
              final XmlElementDescriptor descriptor = xmlTag.getDescriptor();
              if (descriptor instanceof JavaFxClassBackedElementDescriptor) {
                final PsiElement declaration = descriptor.getDeclaration();
                if (declaration instanceof PsiClass) {
                  final PsiField fieldByName = controllerClass.findFieldByName(xmlAttributeValue.getValue(), false);
                  if (fieldByName != null && !InheritanceUtil.isInheritorOrSelf((PsiClass)declaration, PsiUtil.resolveClassInType(fieldByName.getType()), true)) {
                    return "Cannot set " + ((PsiClass)declaration).getQualifiedName() + " to field \'" + fieldByName.getName() + "\'";
                  }
                }
              }
            }
          }
        }
        else {
          final XmlAttributeDescriptor attributeDescriptor = ((XmlAttribute)parent).getDescriptor();
          if (attributeDescriptor != null) {
            final PsiElement declaration = attributeDescriptor.getDeclaration();
            final String boxedQName = getBoxedPropertyType(context, declaration);
            if (boxedQName != null) {
              try {
                final Class<?> aClass = Class.forName(boxedQName);
                final Method method = aClass.getMethod(JavaFxCommonClassNames.VALUE_OF, String.class);
                method.invoke(aClass, ((XmlAttributeValue)context).getValue());
              }
              catch (InvocationTargetException e) {
                final Throwable cause = e.getCause();
                if (cause instanceof NumberFormatException) {
                  return "Invalid value: unable to coerce to " + boxedQName;
                }
              }
              catch (Exception ignore) {
              }
            }
          }
        }
      }
    }
    return null;
  }

  @Nullable
  private static String getBoxedPropertyType(XmlElement context, PsiElement declaration) {
    PsiType attrType = null;
    if (declaration instanceof PsiField) {
      attrType = ((PsiField)declaration).getType();
      if (InheritanceUtil.isInheritor(attrType, JavaFxCommonClassNames.JAVAFX_BEANS_VALUE_OBSERVABLE_VALUE)) {
        attrType = JavaFxPsiUtil
          .getWrappedPropertyType((PsiField)declaration, context.getProject(), JavaFxCommonClassNames.ourWritableMap);
      }
    } else if (declaration instanceof PsiMethod) {
      final PsiParameter[] parameters = ((PsiMethod)declaration).getParameterList().getParameters();
      if (parameters.length == 2) {
        attrType = parameters[1].getType();
      }
    }

    String boxedQName = null;
    if (attrType instanceof PsiPrimitiveType) {
      boxedQName = ((PsiPrimitiveType)attrType).getBoxedTypeName();
    } else if (PsiPrimitiveType.getUnboxedType(attrType) != null) {
      final PsiClass attrClass = PsiUtil.resolveClassInType(attrType);
      boxedQName = attrClass != null ? attrClass.getQualifiedName() : null;
    }
    return boxedQName;
  }

  @Override
  public PsiElement getDeclaration() {
    if (myPsiClass != null) {
      final PsiField field = myPsiClass.findFieldByName(myName, true);
      if (field != null) {
        return field;
      }
      return JavaFxPsiUtil.findPropertySetter(myName, myPsiClass);
    }
    return null;
  }

  @Override
  public String getName(PsiElement context) {
    return getName();
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public void init(PsiElement element) {
  }

  @Override
  public Object[] getDependences() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }
}
