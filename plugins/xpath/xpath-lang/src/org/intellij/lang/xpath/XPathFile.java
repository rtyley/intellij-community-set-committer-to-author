/*
 * Copyright 2005 Sascha Weinreuter
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
package org.intellij.lang.xpath;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import org.intellij.lang.xpath.context.ContextProvider;
import org.intellij.lang.xpath.context.XPathVersion;
import org.intellij.lang.xpath.psi.XPathElement;
import org.intellij.lang.xpath.psi.XPathExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class XPathFile extends PsiFileBase implements XPathElement {

  private final XPathFileType myType;

  public XPathFile(FileViewProvider provider, XPathFileType type) {
    super(provider, type.getLanguage());
    myType = type;
  }

  @NotNull
  public FileType getFileType() {
    return myType;
  }

  public String toString() {
    return "XPathFile:" + getName() + " {" + getText() + "}";
  }

  @Override
  public ContextProvider getXPathContext() {
    return ContextProvider.getContextProvider(this);
  }

  @Nullable
  public XPathExpression getExpression() {
    return findChildByClass(XPathExpression.class);
  }

  @Override
  public XPathVersion getXPathVersion() {
    return getLanguage() instanceof XPath2Language ? XPathVersion.V2 : XPathVersion.V1;
  }
}
