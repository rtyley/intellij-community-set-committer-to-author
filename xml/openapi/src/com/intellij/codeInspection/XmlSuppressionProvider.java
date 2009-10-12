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

package com.intellij.codeInspection;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

/**
 * @author Dmitry Avdeev
 */
public abstract class XmlSuppressionProvider {

  public static ExtensionPointName<XmlSuppressionProvider> EP_NAME = new ExtensionPointName<XmlSuppressionProvider>("com.intellij.xml.xmlSuppressionProvider");

  public static boolean isSuppressed(PsiElement element, String inspectionId) {
    for (XmlSuppressionProvider provider : Extensions.getExtensions(EP_NAME)) {
      if (provider.isSuppressedFor(element, inspectionId)) {
        return true;
      }
    }
    return false;
  }

  public static XmlSuppressionProvider getProvider(PsiFile file) {
    for (XmlSuppressionProvider provider : Extensions.getExtensions(EP_NAME)) {
      if (provider.isProviderAvailable(file)) {
        return provider;
      }
    }
    throw new RuntimeException("No providers found for " + file);
  }

  public abstract boolean isProviderAvailable(PsiFile file);

  public abstract boolean isSuppressedFor(PsiElement element, String inspectionId);

  public abstract void suppressForFile(PsiElement element, String inspectionId);

  public abstract void suppressForTag(PsiElement element, String inspectionId);

}
