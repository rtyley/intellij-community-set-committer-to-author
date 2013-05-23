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
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceRegistrar;

/**
 * User: anna
 * Date: 10/7/11
 */
public class InspectionsPropertiesReferenceProviderContributor extends PsiReferenceContributor {

  private static final String[] EXTENSION_TAG_NAMES = new String[]{
    "localInspection", "globalInspection",
    "configurable", "applicationConfigurable", "projectConfigurable"
  };

  @Override
  public void registerReferenceProviders(PsiReferenceRegistrar registrar) {
    ElementPattern pattern = XmlPatterns.xmlAttributeValue()
      .withParent(XmlPatterns.xmlAttribute().withLocalName("key", "groupKey")
                    .withParent(XmlPatterns.xmlTag().withName(EXTENSION_TAG_NAMES)
                                  .withSuperParent(2, XmlPatterns.xmlTag().withName("idea-plugin"))));
    registrar.registerReferenceProvider(pattern, new InspectionsKeyPropertiesReferenceProvider(false),
                                        PsiReferenceRegistrar.DEFAULT_PRIORITY);
  }
}