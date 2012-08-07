/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.util.xml.stubs;

import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.stubs.Stubbed;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.io.StringRef;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomElementVisitor;

/**
 * @author Dmitry Avdeev
 *         Date: 8/7/12
 */
public class DomStubBuilderVisitor implements DomElementVisitor {

  @Override
  public void visitDomElement(DomElement element) {
    if (myRoot == null || element.getChildDescription().getAnnotation(Stubbed.class) != null) {
      XmlElement xmlElement = element.getXmlElement();
      if (xmlElement instanceof XmlTag) {
        ElementStub old = myRoot;
        myRoot = new ElementStub(myRoot, StringRef.fromString(((PsiNamedElement)xmlElement).getName()));
        element.acceptChildren(this);
        if (old != null) {
          myRoot = old;
        }
      }
      else if (xmlElement instanceof XmlAttribute) {
        new AttributeStub(myRoot, StringRef.fromString(((XmlAttribute)xmlElement).getLocalName()),
                          StringRef.fromString(((XmlAttribute)xmlElement).getValue()));
      }
    }
  }

  private ElementStub myRoot;

  public ElementStub getRoot() {
    return myRoot;
  }
}
