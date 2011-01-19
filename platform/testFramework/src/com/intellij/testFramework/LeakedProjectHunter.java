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
package com.intellij.testFramework;

import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.util.Processor;
import com.intellij.util.containers.Stack;
import com.intellij.util.io.PersistentEnumerator;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: cdr
 */
public class LeakedProjectHunter {
  private static final Map<Class, List<Field>> allFields = new THashMap<Class, List<Field>>();
  private static List<Field> getAllFields(Class aClass) {
    List<Field> cached = allFields.get(aClass);
    if (cached == null) {
      Field[] declaredFields = aClass.getDeclaredFields();
      cached =new ArrayList<Field>(declaredFields.length + 5);
      for (Field declaredField : declaredFields) {
        declaredField.setAccessible(true);
        cached.add(declaredField);
      }
      Class superclass = aClass.getSuperclass();
      if (superclass != null) {
        for (Field sup : getAllFields(superclass)) {
          if (!cached.contains(sup)) {
            cached.add(sup);
          }
        }
      }
      allFields.put(aClass, cached);
    }
    return cached;
  }

  private static final Set<Object> visited = new THashSet<Object>(TObjectHashingStrategy.IDENTITY);
  static class BackLink {
    Class aClass;
    Object value;
    Field field;
    BackLink backLink;

    BackLink(Class aClass, Object value, Field field, BackLink backLink) {
      this.aClass = aClass;
      this.value = value;
      this.field = field;
      this.backLink = backLink;
    }
  }

  private static final Stack<BackLink> toVisit = new Stack<BackLink>();
  private static void walkObjects(Processor<BackLink> processor, Class lookFor) throws Exception {
    while (true) {
      if (toVisit.isEmpty()) return;
      BackLink backLink = toVisit.pop();
      Object root = backLink.value;
      if (!visited.add(root)) continue;
      Class rootClass = backLink.aClass;
      List<Field> fields = getAllFields(rootClass);
      for (Field field : fields) {
        Object value = field.get(root);
        if (value == null) continue;
        Class valueClass = value.getClass();
        if (valueClass == lookFor) {
          BackLink newBackLink = new BackLink(valueClass, value, field, backLink);
          processor.process(newBackLink);
        }
        else {
          if (root instanceof Reference && "referent".equals(field.getName())) continue; // do not follow weak/soft refs
          BackLink newBackLink = new BackLink(valueClass, value, field, backLink);
          if (toFollow(valueClass)) {
            toVisit.push(newBackLink);
          }
        }
      }
      if (rootClass.isArray()) {
        if (toFollow(rootClass.getComponentType())) {
          try {
            for (Object o : (Object[])root) {
              if (o == null) continue;
              Class oClass = o.getClass();
              toVisit.push(new BackLink(oClass, o, null, backLink));
            }
          }
          catch (ClassCastException e) {
          }
        }
      }
    }
  }

  private static final Set<String> noFollowClasses = new THashSet<String>();
  static {
    noFollowClasses.add("java.lang.Boolean");
    noFollowClasses.add("java.lang.Byte");
    noFollowClasses.add("java.lang.Class");
    noFollowClasses.add("java.lang.Character");
    noFollowClasses.add("java.lang.Double");
    noFollowClasses.add("java.lang.Float");
    noFollowClasses.add("java.lang.Integer");
    noFollowClasses.add("java.lang.Long");
    noFollowClasses.add("java.lang.Object");
    noFollowClasses.add("java.lang.Short");
    noFollowClasses.add("java.lang.String");
    noFollowClasses.add("java.lang.ref.WeakReference");
  }

  private static boolean toFollow(Class oClass) {
    String name = oClass.getName();
    return !noFollowClasses.contains(name);
  }

  private static final Key<Boolean> REPORTED_LEAKED = Key.create("REPORTED_LEAKED");
  @TestOnly
  public static void checkProjectLeak(Object root) throws Exception {
    checkLeak(root, ProjectImpl.class);
  }
  @TestOnly
  public static void checkLeak(Object root, Class suspectClass) throws Exception {
    if (SwingUtilities.isEventDispatchThread()) {
      UIUtil.dispatchAllInvocationEvents();
    }
    else {
      UIUtil.pump();
    }
    PersistentEnumerator.clearCacheForTests();
    toVisit.push(new BackLink(ApplicationImpl.class, root, null,null));
    try {
      walkObjects(new Processor<BackLink>() {
        @Override
        public boolean process(BackLink backLink) {
          UserDataHolder leaked = (UserDataHolder)backLink.value;
          if (leaked.getUserData(REPORTED_LEAKED) == null) {
            System.out.println("LEAK: hash: "+System.identityHashCode(leaked) + "; " +
                               "place: "+ (leaked instanceof Project ? PlatformTestCase.getCreationPlace((Project)leaked) : ""));
            while (backLink != null) {
              System.out.println("-->"+backLink.field+"; Value: "+backLink.value+"; "+backLink.aClass);
              backLink = backLink.backLink;
            }
            System.out.println(";-----");

            leaked.putUserData(REPORTED_LEAKED, Boolean.TRUE);
            throw new RuntimeException();
          }
          return true;
        }
      }, suspectClass);
    }
    finally {
      visited.clear();
      toVisit.clear();
    }
  }
}
