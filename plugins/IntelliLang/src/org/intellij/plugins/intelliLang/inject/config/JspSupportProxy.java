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

package org.intellij.plugins.intelliLang.inject.config;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.psi.impl.source.jsp.JspManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Proxy class that allows to avoid a hard compile time dependency on the XPathView plugin.
 */
public abstract class JspSupportProxy {

  @NotNull
  public abstract String[] getPossibleTldUris(final Module module);

  private static JspSupportProxy ourInstance;
  private static boolean isInitialized;

  @Nullable
  public static synchronized JspSupportProxy getInstance() {
    if (isInitialized) {
      return ourInstance;
    }
    try {
      return ourInstance = ServiceManager.getService(JspSupportProxy.class);
    } finally {
      isInitialized = true;
    }
  }

  public static class Impl extends JspSupportProxy {
    @NotNull
    @Override
    public String[] getPossibleTldUris(Module module) {
      return JspManager.getInstance(module.getProject()).getPossibleTldUris(module);
    }
  }
}