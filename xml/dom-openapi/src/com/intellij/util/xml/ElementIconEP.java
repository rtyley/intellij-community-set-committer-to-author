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
package com.intellij.util.xml;

import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author yole
 */
public class ElementIconEP extends AbstractExtensionPointBean {
  public static ExtensionPointName<ElementIconEP> EP_NAME = ExtensionPointName.create("com.intellij.dom.elementIcon");

  @Attribute("className")
  public String className;

  @Attribute("icon")
  public String icon;

  private final NullableLazyValue<Icon> myIcon = new NullableLazyValue<Icon>() {
    @Override
    protected Icon compute() {
      return IconLoader.findIcon(icon, getLoaderForClass());
    }
  };

  private NotNullLazyValue<Class> myClass = new NotNullLazyValue<Class>() {
    @NotNull
    @Override
    protected Class compute() {
      try {
        return findClass(className);
      }
      catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }
  };

  @Nullable
  public Icon getIcon(Class aClass) {
    if (myClass.getValue().isAssignableFrom(aClass)) {
      return myIcon.getValue();
    }
    return null;
  }
}
