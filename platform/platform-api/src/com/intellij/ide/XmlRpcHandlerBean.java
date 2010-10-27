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
package com.intellij.ide;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.xmlb.annotations.Attribute;

/**
 * @author yole
 */
public class XmlRpcHandlerBean extends AbstractExtensionPointBean {
  public static final ExtensionPointName<XmlRpcHandlerBean> EP_NAME = ExtensionPointName.create("com.intellij.xmlRpcHandler");

  @Attribute("name")
  public String name;

  @Attribute("implementation")
  public String implementation;

  public Object instantiate() throws ClassNotFoundException {
    return instantiate(implementation, ApplicationManager.getApplication().getPicoContainer());
  }
}
