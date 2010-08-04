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
package com.intellij.lang.ant.dom;

import com.intellij.lang.ant.psi.impl.AntIntrospector;
import com.intellij.lang.ant.psi.impl.ReflectedProject;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.pom.PomTarget;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.*;
import org.apache.tools.ant.Task;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 9, 2010
 */
public class AntDomExtender extends DomExtender<AntDomElement>{
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.ant.dom.AntDomExtender");
  
  private static final Key<Class> ELEMENT_IMPL_CLASS_KEY = Key.create("_element_impl_class_");
  private static final Map<String, Class<? extends AntDomElement>> TAG_MAPPING = new HashMap<String, Class<? extends AntDomElement>>();
  static {
    TAG_MAPPING.put("property", AntDomProperty.class);
    TAG_MAPPING.put("dirname", AntDomDirname.class);
    TAG_MAPPING.put("fileset", AntDomFileSet.class);
    TAG_MAPPING.put("dirset", AntDomDirSet.class);
    TAG_MAPPING.put("filelist", AntDomFileList.class);
    TAG_MAPPING.put("path", AntDomPath.class);
    TAG_MAPPING.put("classpath", AntDomPath.class);
    TAG_MAPPING.put("typedef", AntDomTypeDef.class);
    TAG_MAPPING.put("taskdef", AntDomTaskdef.class);
    TAG_MAPPING.put("presetdef", AntDomPresetDef.class);
    TAG_MAPPING.put("macrodef", AntDomMacroDef.class);
    TAG_MAPPING.put("scriptdef", AntDomScriptDef.class);
    TAG_MAPPING.put("antlib", AntDomAntlib.class);
    TAG_MAPPING.put("ant", AntDomAnt.class);
  }

  public void registerExtensions(@NotNull final AntDomElement antDomElement, @NotNull DomExtensionsRegistrar registrar) {
    final XmlElement xmlElement = antDomElement.getXmlElement();
    if (xmlElement instanceof XmlTag) {
      final XmlTag xmlTag = (XmlTag)xmlElement;
      final String tagName = xmlTag.getName(); 

      final AntDomProject antProject = antDomElement.getAntProject();
      if (antProject == null) {
        return;
      }
      final ReflectedProject reflected = ReflectedProject.getProject(antProject.getClassLoader());

      final DomGenericInfo genericInfo = antDomElement.getGenericInfo();
      AntIntrospector parentElementIntrospector = null;
      final Hashtable<String,Class> coreTaskDefs = reflected.getTaskDefinitions();
      final Hashtable<String, Class> coreTypeDefs = reflected.getDataTypeDefinitions();
      if ("project".equals(tagName)) {
        parentElementIntrospector = getIntrospector(reflected.getProject().getClass());
      }
      else if ("target".equals(tagName)) {
        parentElementIntrospector = getIntrospector(reflected.getTargetClass());
      }
      else {
        if (antDomElement instanceof AntDomCustomElement) {
          final AntDomCustomElement custom = (AntDomCustomElement)antDomElement;
          final Class definitionClass = custom.getDefinitionClass();
          if (definitionClass != null) {
            parentElementIntrospector = getIntrospector(definitionClass);
          }
        }
        else {
          Class elemType = antDomElement.getChildDescription().getUserData(ELEMENT_IMPL_CLASS_KEY);

          if (elemType == null) {
            if (coreTaskDefs != null){
              elemType = coreTaskDefs.get(tagName);
            }
          }

          if (elemType == null) {
            if (coreTypeDefs != null){
              elemType = coreTypeDefs.get(tagName);
            }
          }

          if (elemType != null) {
            parentElementIntrospector = getIntrospector(elemType);
          }
        }
      }

      if (parentElementIntrospector != null) {

        defineAttributes(xmlTag, registrar, genericInfo, parentElementIntrospector);

        if ("project".equals(tagName) || parentElementIntrospector.isContainer()) { // can contain any task or/and type definition
          if (coreTaskDefs != null) {
            for (Map.Entry<String, Class> entry : coreTaskDefs.entrySet()) {
              final DomExtension extension = registerChild(registrar, genericInfo, entry.getKey());
              if (extension != null) {
                final Class type = entry.getValue();
                if (type != null) {
                  extension.putUserData(ELEMENT_IMPL_CLASS_KEY, type);
                }
                extension.putUserData(AntDomElement.ROLE, AntDomElement.Role.TASK);
              }
            }
          }
          if (coreTypeDefs != null) {
            for (Map.Entry<String, Class> entry : coreTypeDefs.entrySet()) {
              final DomExtension extension = registerChild(registrar, genericInfo, entry.getKey());
              if (extension != null) {
                final Class type = entry.getValue();
                if (type != null) {
                  extension.putUserData(ELEMENT_IMPL_CLASS_KEY, type);
                }
                extension.putUserData(AntDomElement.ROLE, AntDomElement.Role.DATA_TYPE);
              }
            }
          }
          registrar.registerCustomChildrenExtension(AntDomCustomElement.class, new AntCustomTagNameDescriptor());
        }
        else {
          final Enumeration<String> nested = parentElementIntrospector.getNestedElements();
          while (nested.hasMoreElements()) {
            final String nestedElementName = nested.nextElement();
            final DomExtension extension = registerChild(registrar, genericInfo, nestedElementName);
            if (extension != null) {
              final Class type = parentElementIntrospector.getElementType(nestedElementName);
              if (type != null) {
                extension.putUserData(ELEMENT_IMPL_CLASS_KEY, type);
              }
              AntDomElement.Role role = null;
              if (coreTaskDefs != null && coreTaskDefs.containsKey(nestedElementName)) {
                role = AntDomElement.Role.TASK;
              }
              else if (coreTypeDefs != null && coreTypeDefs.containsKey(nestedElementName)) {
                role = AntDomElement.Role.DATA_TYPE;
              }
              else if (type != null && isAssignableFrom(Task.class.getName(), type)) {
                role = AntDomElement.Role.TASK;
              }
              if (role != null) {
                extension.putUserData(AntDomElement.ROLE, role);
              }
            }
          }
        }
      }
    }
  }

  private static void defineAttributes(XmlTag xmlTag, DomExtensionsRegistrar registrar, DomGenericInfo genericInfo, AntIntrospector parentElementIntrospector) {
    final Map<String, Pair<Type, Class>> registeredAttribs = getStaticallyRegisteredAttributes(genericInfo);
    // define attributes discovered by introspector and not yet defined statically
    final Enumeration introspectedAttributes = parentElementIntrospector.getAttributes();
    while (introspectedAttributes.hasMoreElements()) {
      final String attribName = (String)introspectedAttributes.nextElement();
      if (genericInfo.getAttributeChildDescription(attribName) == null) { // if not defined yet 
        final String _attribName = attribName.toLowerCase(Locale.US);
        final Pair<Type, Class> types = registeredAttribs.get(_attribName);
        Type type = types != null? types.getFirst() : null;
        Class converterClass = types != null ? types.getSecond() : null;
        if (type == null) {
          type = String.class; // use String by default
          final Class attributeType = parentElementIntrospector.getAttributeType(attribName);
          if (attributeType != null) {
            // handle well-known types
            if (File.class.isAssignableFrom(attributeType)) {
              type = PsiFileSystemItem.class;
              converterClass = AntPathConverter.class;
            }
            else if (Boolean.class.isAssignableFrom(attributeType)){
              type = Boolean.class;
              converterClass = AntBooleanConverter.class;
            }
          }
        }
        
        LOG.assertTrue(type != null);
        
        registerAttribute(registrar, attribName, type, converterClass);
        if (types == null) { // augment the map if this was a newly added attribute
          registeredAttribs.put(_attribName, new Pair<Type, Class>(type, converterClass));
        }
      }
    }
    // handle attribute case problems: 
    // additionaly register all attributes that exist in XML but differ from the registered ones only in case
    for (XmlAttribute xmlAttribute : xmlTag.getAttributes()) {
      final String existingAttribName = xmlAttribute.getName();
      if (genericInfo.getAttributeChildDescription(existingAttribName) == null) {
        final Pair<Type, Class> pair = registeredAttribs.get(existingAttribName.toLowerCase(Locale.US));
        if (pair != null) { // if such attribute should actually be here
          registerAttribute(registrar, existingAttribName, pair.getFirst(), pair.getSecond());
        }
      }
    }
  }

  private static void registerAttribute(DomExtensionsRegistrar registrar, String attribName, final @NotNull Type attributeType, final @Nullable Class converterType) {
    final DomExtension extension = registrar.registerGenericAttributeValueChildExtension(new XmlName(attribName), attributeType);
    if (converterType != null) {
      try {
        extension.setConverter((Converter)converterType.newInstance());
      }
      catch (InstantiationException e) {
        LOG.info(e);
      }
      catch (IllegalAccessException e) {
        LOG.info(e);
      }
    }
  }

  private static Map<String, Pair<Type, Class>> getStaticallyRegisteredAttributes(final DomGenericInfo genericInfo) {
    final Map<String, Pair<Type, Class>> map = new HashMap<String, Pair<Type, Class>>();
    for (DomAttributeChildDescription description : genericInfo.getAttributeChildrenDescriptions()) {
      final Type type = description.getType();
      if (type instanceof ParameterizedType) {
        final Type[] typeArguments = ((ParameterizedType)type).getActualTypeArguments();
        if (typeArguments.length == 1) {
          String name = description.getXmlElementName();
          final Type attribType = typeArguments[0];
          Class<? extends Converter> converterType = null;
          final Convert converterAnnotation = description.getAnnotation(Convert.class);
          if (converterAnnotation != null) {
            converterType = converterAnnotation.value();
          }
          map.put(name.toLowerCase(Locale.US), new Pair<Type, Class>(attribType, converterType));
        }
      }
    }
    return map;
  }

  @Nullable
  private static DomExtension registerChild(DomExtensionsRegistrar registrar, DomGenericInfo elementInfo, String childName) {
    if (elementInfo.getCollectionChildDescription(childName) == null) { // register if not yet defined statically
      Class<? extends AntDomElement> modelClass = getModelClass(childName);
      if (modelClass == null) {
        modelClass = AntDomElement.class;
      }
      return registrar.registerCollectionChildrenExtension(new XmlName(childName), modelClass);
    }
    return null;
  }

  @Nullable
  public static AntIntrospector getIntrospector(Class c) {
    try {
      return AntIntrospector.getInstance(c);
    }
    catch (Throwable ignored) {
    }
    return null;
  }

  @Nullable
  private static Class<? extends AntDomElement> getModelClass(@NotNull String tagName) {
    return TAG_MAPPING.get(tagName.toLowerCase(Locale.US));
  }

  private static boolean isAssignableFrom(final String baseClassName, final Class clazz) {
    try {
      final ClassLoader loader = clazz.getClassLoader();
      if (loader != null) {
        final Class baseClass = loader.loadClass(baseClassName);
        return baseClass.isAssignableFrom(clazz);
      }
    }
    catch (ClassNotFoundException ignored) {
    }
    return false;
  }

  private static class AntCustomTagNameDescriptor extends CustomDomChildrenDescription.TagNameDescriptor {

    public Set<EvaluatedXmlName> getCompletionVariants(@NotNull DomElement parent) {
      if (!(parent instanceof AntDomElement)) {
        return Collections.emptySet();
      }
      final AntDomElement element = (AntDomElement)parent;
      final AntDomProject antDomProject = element.getAntProject();
      if (antDomProject == null) {
        return Collections.emptySet();
      }
      final CustomAntElementsRegistry registry = CustomAntElementsRegistry.getInstance(antDomProject);
      final Set<EvaluatedXmlName> result = new HashSet<EvaluatedXmlName>();
      for (XmlName variant : registry.getCompletionVariants(element)) {
        final String ns = variant.getNamespaceKey();
        result.add(new DummyEvaluatedXmlName(variant, ns != null? ns : ""));
      }
      return result;
    }

    @Nullable
    public PomTarget findDeclaration(DomElement parent, @NotNull EvaluatedXmlName name) {
      final XmlName xmlName = name.getXmlName();
      return doFindDeclaration(parent, xmlName);
    }

    @Nullable
    public PomTarget findDeclaration(@NotNull DomElement child) {
      XmlName name = new XmlName(child.getXmlElementName(), child.getXmlElementNamespace());
      return doFindDeclaration(child.getParent(), name);
    }

    @Nullable
    private static PomTarget doFindDeclaration(DomElement parent, XmlName xmlName) {
      if (!(parent instanceof AntDomElement)) {
        return null;
      }
      final AntDomElement parentElement = (AntDomElement)parent;
      final AntDomProject antDomProject = parentElement.getAntProject();
      if (antDomProject == null) {
        return null;
      }
      final CustomAntElementsRegistry registry = CustomAntElementsRegistry.getInstance(antDomProject);
      final AntDomElement declaringElement = registry.findDeclaringElement(parentElement, xmlName);
      if (declaringElement == null) {
        return null;
      }
      DomTarget target = DomTarget.getTarget(declaringElement);
      if (target == null && declaringElement instanceof AntDomTypeDef) {
        final AntDomTypeDef typedef = (AntDomTypeDef)declaringElement;
        final GenericAttributeValue<PsiFileSystemItem> resource = typedef.getResource();
        if (resource != null) {
          target = DomTarget.getTarget(declaringElement, resource);
        }
        if (target == null) {
          final GenericAttributeValue<PsiFileSystemItem> file = typedef.getFile();
          if (file != null) {
            target = DomTarget.getTarget(declaringElement, file);
          }
        }
      }
      return target;
    }
  }
}
