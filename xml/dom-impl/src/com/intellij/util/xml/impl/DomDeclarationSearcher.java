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
package com.intellij.util.xml.impl;

import com.intellij.pom.PomDeclarationSearcher;
import com.intellij.pom.PomTarget;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.*;
import com.intellij.util.Consumer;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class DomDeclarationSearcher extends PomDeclarationSearcher {
  public void findDeclarationsAt(@NotNull PsiElement psiElement, int offsetInElement, Consumer<PomTarget> consumer) {
    if (!(psiElement instanceof XmlToken)) return;

    final IElementType tokenType = ((XmlToken)psiElement).getTokenType();

    final DomManager domManager = DomManager.getDomManager(psiElement.getProject());
    final DomElement domElement;
    if (tokenType == XmlTokenType.XML_DATA_CHARACTERS && psiElement.getParent() instanceof XmlText && psiElement.getParent().getParent() instanceof XmlTag) {
      final XmlTag tag = (XmlTag)psiElement.getParent().getParent();
      for (XmlText text : tag.getValue().getTextElements()) {
        if (GenericValueReferenceProvider.hasInjections((PsiLanguageInjectionHost)text)) {
          return;
        }
      }

      domElement = domManager.getDomElement(tag);
    } else if (tokenType == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN && psiElement.getParent() instanceof XmlAttributeValue && psiElement.getParent().getParent() instanceof XmlAttribute) {
      final PsiElement attributeValue = psiElement.getParent();
      if (GenericValueReferenceProvider.hasInjections((PsiLanguageInjectionHost)attributeValue)) {
        return;
      }
      domElement = domManager.getDomElement((XmlAttribute)attributeValue.getParent());
    } else {
      return;
    }

    if (!(domElement instanceof GenericDomValue)) {
      return;
    }

    final NameValue nameValue = domElement.getAnnotation(NameValue.class);
    if (nameValue != null && nameValue.referencable()) {
      DomElement parent = domElement.getParent();
      assert parent != null;
      final DomTarget target = DomTarget.getTarget(parent);
      if (target != null) {
        consumer.consume(target);
      }
    }
  }

}
