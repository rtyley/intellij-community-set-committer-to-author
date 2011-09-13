/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.android.uipreview;

import com.android.ide.common.rendering.LayoutLibrary;
import com.android.ide.common.rendering.api.*;
import com.android.ide.common.rendering.legacy.LegacyCallback;
import com.android.ide.common.resources.IntArrayWrapper;
import com.android.resources.ResourceType;
import com.android.sdklib.SdkConstants;
import com.android.util.Pair;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Computable;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.*;
import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
class ProjectCallback extends LegacyCallback implements IProjectCallback {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.uipreview.ProjectCallback");

  private final Module myModule;
  private final ProjectResources myProjectResources;

  public static final String FRAGMENT_TAG_NAME = "fragment";

  private final Set<String> myMissingClasses = new TreeSet<String>();
  private final Set<String> myBrokenClasses = new TreeSet<String>();

  private final Map<String, Class<?>> myLoadedClasses = new HashMap<String, Class<?>>();

  private ProjectClassLoader myProjectClassLoader = null;
  private final ClassLoader myParentClassLoader;

  ProjectCallback(@NotNull LayoutLibrary layoutLib, @NotNull Module module, @NotNull ProjectResources projectResources) {
    myParentClassLoader = layoutLib.getClassLoader();
    myModule = module;
    myProjectResources = projectResources;
    loadAndParseRClass();
  }

  @Nullable
  public AdapterBinding getAdapterBinding(ResourceReference adapterViewRef, Object adapterCookie, Object viewObject) {
    return null;
  }

  @Nullable
  public Object getAdapterItemValue(ResourceReference adapterView,
                                    Object adapterCookie,
                                    ResourceReference itemRef,
                                    int fullPosition,
                                    int positionPerType,
                                    int fullParentPosition,
                                    int parentPositionPerType,
                                    ResourceReference viewRef,
                                    ViewAttribute viewAttribute,
                                    Object defaultValue) {
    return null;
  }

  @Nullable
  public String getNamespace() {
    return null;
  }

  @Nullable
  public ILayoutPullParser getParser(String layoutName) {
    // don't support custom parser for included files.
    return null;
  }

  @Nullable
  @Override
  public ILayoutPullParser getParser(ResourceValue layoutResource) {
    return null;
  }

  @Nullable
  public Integer getResourceId(ResourceType type, String name) {
    return myProjectResources.getResourceId(type, name);
  }

  @Nullable
  public Object loadView(String className, Class[] constructorSignature, Object[] constructorArgs)
    throws ClassNotFoundException, InvocationTargetException, NoSuchMethodException, IllegalAccessException, InstantiationException {

    Class<?> aClass = myLoadedClasses.get(className);
    if (aClass != null) {
      return createNewInstance(aClass, constructorSignature, constructorArgs);
    }

    aClass = loadClass(className);

    try {
      if (aClass != null) {
        final Object viewObject = createNewInstance(aClass, constructorSignature, constructorArgs);
        myLoadedClasses.put(className, aClass);
        return viewObject;
      }
    }
    catch (ClassNotFoundException e) {
      LOG.info(e);
      myBrokenClasses.add(className);
    }
    catch (InvocationTargetException e) {
      LOG.info(e);
      myBrokenClasses.add(className);
    }
    catch (IllegalAccessException e) {
      LOG.info(e);
      myBrokenClasses.add(className);
    }
    catch (InstantiationException e) {
      LOG.info(e);
      myBrokenClasses.add(className);
    }
    catch (NoSuchMethodException e) {
      LOG.info(e);
      myBrokenClasses.add(className);
    }

    try {
      return createMockView(className, constructorSignature, constructorArgs);
    }
    catch (ClassNotFoundException e) {
      throw new ClassNotFoundException(className, e);
    }
    catch (InvocationTargetException e) {
      throw new ClassNotFoundException(className, e);
    }
    catch (NoSuchMethodException e) {
      throw new ClassNotFoundException(className, e);
    }
    catch (IllegalAccessException e) {
      throw new ClassNotFoundException(className, e);
    }
    catch (InstantiationException e) {
      throw new ClassNotFoundException(className, e);
    }
    catch (NoSuchFieldException e) {
      throw new ClassNotFoundException(className, e);
    }
  }

  @Nullable
  private Class<?> loadClass(String className) {
    try {
      if (myProjectClassLoader == null) {
        myProjectClassLoader = new ProjectClassLoader(myParentClassLoader, myModule);
      }
      return myProjectClassLoader.loadClass(className);
    }
    catch (ClassNotFoundException e) {
      LOG.info(e);
      if (!className.equals(FRAGMENT_TAG_NAME)) {
        myMissingClasses.add(className);
      }
      return null;
    }
  }

  private Object createMockView(String className, Class[] constructorSignature, Object[] constructorArgs)
    throws
    ClassNotFoundException,
    InvocationTargetException,
    NoSuchMethodException,
    InstantiationException,
    IllegalAccessException,
    NoSuchFieldException {

    final Class<?> mockViewClass = myProjectClassLoader.loadClass(SdkConstants.CLASS_MOCK_VIEW);
    final Object viewObject = createNewInstance(mockViewClass, constructorSignature, constructorArgs);

    final Method setTextMethod = viewObject.getClass().getMethod("setText", CharSequence.class);
    String label = getShortClassName(className);
    if (label.equals(FRAGMENT_TAG_NAME)) {
      label = "<fragment>";
    }
    setTextMethod.invoke(viewObject, label);

    try {
      final Class<?> gravityClass = Class.forName("android.view.Gravity", true, viewObject.getClass().getClassLoader());
      final Field centerField = gravityClass.getField("CENTER");
      final int center = centerField.getInt(null);
      final Method setGravityMethod = viewObject.getClass().getMethod("setGravity", Integer.TYPE);
      setGravityMethod.invoke(viewObject, Integer.valueOf(center));
    }
    catch (ClassNotFoundException e) {
      LOG.info(e);
    }

    return viewObject;
  }

  private static String getShortClassName(String fqcn) {
    if (fqcn.startsWith("android.")) {
      // android.foo.Name -> android...Name
      int first = fqcn.indexOf('.');
      int last = fqcn.lastIndexOf('.');
      if (last > first) {
        return fqcn.substring(0, first) + ".." + fqcn.substring(last);
      }
    }
    else {
      // com.example.p1.p2.MyClass -> com.example...MyClass
      int first = fqcn.indexOf('.');
      first = fqcn.indexOf('.', first + 1);
      int last = fqcn.lastIndexOf('.');
      if (last > first) {
        return fqcn.substring(0, first) + ".." + fqcn.substring(last);
      }
    }

    return fqcn;
  }

  @SuppressWarnings("ConstantConditions")
  private static Object createNewInstance(Class<?> clazz, Class[] constructorSignature, Object[] constructorParameters)
    throws NoSuchMethodException, ClassNotFoundException, InvocationTargetException, IllegalAccessException, InstantiationException {
    Constructor<?> constructor = null;

    try {
      constructor = clazz.getConstructor(constructorSignature);
    }
    catch (NoSuchMethodException e) {
      // View class has 1-parameter, 2-parameter and 3-parameter constructors

      final int paramsCount = constructorSignature.length;
      if (paramsCount == 0) {
        throw e;
      }

      for (int i = 3; i >= 1; i--) {
        if (i == paramsCount) {
          continue;
        }

        final int k = paramsCount < i ? paramsCount : i;

        final Class[] sig = new Class[i];
        System.arraycopy(constructorSignature, 0, sig, 0, k);

        final Object[] params = new Object[i];
        System.arraycopy(constructorParameters, 0, params, 0, k);

        for (int j = k + 1; j <= i; j++) {
          if (j == 2) {
            sig[j - 1] = clazz.getClassLoader().loadClass("android.util.AttributeSet");
            params[j - 1] = null;
          }
          else if (j == 3) {
            // parameter 3: int defstyle
            sig[j - 1] = int.class;
            params[j - 1] = 0;
          }
        }

        constructorSignature = sig;
        constructorParameters = params;

        try {
          constructor = clazz.getConstructor(constructorSignature);
          if (constructor != null) {
            if (constructorSignature.length < 2) {
              LOG.warn("wrong_constructor: Custom view " +
                       clazz.getSimpleName() +
                       " is not using the 2- or 3-argument " +
                       "View constructors; XML attributes will not work");
            }
            break;
          }
        }
        catch (NoSuchMethodException ignored) {
        }
      }

      if (constructor == null) {
        throw e;
      }
    }

    constructor.setAccessible(true);
    return constructor.newInstance(constructorParameters);
  }

  @Nullable
  public Pair<ResourceType, String> resolveResourceId(int id) {
    return myProjectResources.resolveResourceId(id);
  }

  @Nullable
  public String resolveResourceId(int[] id) {
    return myProjectResources.resolveStyleable(id);
  }

  @NotNull
  public Set<String> getMissingClasses() {
    return myMissingClasses;
  }

  @NotNull
  public Set<String> getBrokenClasses() {
    return myBrokenClasses;
  }

  private void loadAndParseRClass() {
    try {
      final String className = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
        @Nullable
        @Override
        public String compute() {
          return getRClassName(myModule);
        }
      });
      if (className == null) {
        LOG.info("loadAndParseRClass: failed to find manifest package for project %1$s");
        return;
      }

      Class<?> aClass = myLoadedClasses.get(className);
      if (aClass == null) {
        ProjectClassLoader loader = new ProjectClassLoader(null, myModule);
        aClass = loader.loadClass(className);
        if (aClass != null) {
          myLoadedClasses.put(className, aClass);
        }
      }

      if (aClass != null) {
        final Map<ResourceType, TObjectIntHashMap<String>> res2id =
          new EnumMap<ResourceType, TObjectIntHashMap<String>>(ResourceType.class);
        final TIntObjectHashMap<Pair<ResourceType, String>> id2res = new TIntObjectHashMap<Pair<ResourceType, String>>();
        final Map<IntArrayWrapper, String> styleableId2res = new HashMap<IntArrayWrapper, String>();

        if (parseClass(aClass, id2res, styleableId2res, res2id)) {
          myProjectResources.setCompiledResources(id2res, styleableId2res, res2id);
        }
      }
    }
    catch (ClassNotFoundException e) {
      LOG.debug(e);
    }
  }

  private static boolean parseClass(Class<?> rClass,
                                    TIntObjectHashMap<Pair<ResourceType, String>> id2res,
                                    Map<IntArrayWrapper, String> styleableId2Res,
                                    Map<ResourceType, TObjectIntHashMap<String>> res2id) {
    try {
      for (Class<?> resClass : rClass.getDeclaredClasses()) {
        final ResourceType resType = ResourceType.getEnum(resClass.getSimpleName());

        if (resType != null) {
          final TObjectIntHashMap<String> resName2Id = new TObjectIntHashMap<String>();
          res2id.put(resType, resName2Id);

          for (Field field : resClass.getDeclaredFields()) {
            final int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)) {
              final Class<?> type = field.getType();
              if (type.isArray() && type.getComponentType() == int.class) {
                styleableId2Res.put(new IntArrayWrapper((int[])field.get(null)), field.getName());
              }
              else if (type == int.class) {
                final Integer value = (Integer)field.get(null);
                id2res.put(value, Pair.of(resType, field.getName()));
                resName2Id.put(field.getName(), value);
              }
              else {
                LOG.error("Unknown field type in R class: " + type);
              }
            }
          }
        }
      }
    }
    catch (IllegalAccessException e) {
      LOG.info(e);
      return false;
    }

    return true;
  }

  @Nullable
  private static String getRClassName(Module module) {
    final AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      return null;
    }

    final Manifest manifest = facet.getManifest();
    if (manifest == null) {
      return null;
    }

    final String aPackage = manifest.getPackage().getValue();
    return aPackage == null ? null : aPackage + ".R";
  }
}
