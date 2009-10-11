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
package org.intellij.lang.xpath.xslt.context;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import org.jetbrains.annotations.NotNull;

import org.intellij.lang.xpath.XPathFile;
import org.intellij.lang.xpath.context.ContextProvider;
import org.intellij.lang.xpath.context.ContextProviderExtension;
import org.intellij.lang.xpath.xslt.XsltSupport;

public class XsltContextProviderExtension extends ContextProviderExtension {
    public boolean accepts(XPathFile file) {
        final PsiElement context = file.getContext();
        if (!(context instanceof XmlElement)) return false;
        final XmlAttribute att = PsiTreeUtil.getParentOfType(context, XmlAttribute.class);
        if (att == null) return false;
        return XsltSupport.isXPathAttribute(att);
    }

    @NotNull
    public ContextProvider getContextProvider(XPathFile file) {
        final XmlElement xmlElement = (XmlElement)file.getContext();
        assert xmlElement != null;
        return new XsltContextProvider(xmlElement);
    }
}