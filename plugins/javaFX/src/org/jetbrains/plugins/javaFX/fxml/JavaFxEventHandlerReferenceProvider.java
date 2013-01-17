/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 * Date: 1/16/13
 */
class JavaFxEventHandlerReferenceProvider extends JavaFxControllerBasedReferenceProvider {
  private static final Logger LOG = Logger.getInstance("#" + JavaFxEventHandlerReferenceProvider.class.getName());

  @Override
  protected PsiReference[] getReferencesByElement(@NotNull PsiClass controllerClass,
                                                  XmlAttributeValue xmlAttributeValue,
                                                  ProcessingContext context) {
    final String attValueString = xmlAttributeValue.getValue();
    LOG.assertTrue(attValueString.startsWith("#"));
    final String eventHandlerName = attValueString.substring(1);
    final PsiMethod[] methods = controllerClass.findMethodsByName(eventHandlerName, true);

    PsiMethod handlerMethod = null;
    for (PsiMethod psiMethod : methods) {
      if (JavaFxEventHandlerReference.isHandlerMethod(psiMethod)) {
        handlerMethod = psiMethod;
        break;
      }
    }
    return new PsiReference[]{new JavaFxEventHandlerReference(xmlAttributeValue, handlerMethod, controllerClass)};
  }
}
