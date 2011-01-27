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
package org.intellij.lang.xpath.psi.impl;

import com.intellij.lang.ASTNode;
import org.intellij.lang.xpath.psi.XPath2Castable;
import org.intellij.lang.xpath.psi.XPath2Type;
import org.intellij.lang.xpath.psi.XPath2TypeElement;
import org.intellij.lang.xpath.psi.XPathType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XPath2CastableImpl extends XPathElementImpl implements XPath2Castable {
  public XPath2CastableImpl(ASTNode node) {
    super(node);
  }

  @Override
  @Nullable
  public XPathType getTargetType() {
    final XPath2TypeElement node = findChildByClass(XPath2TypeElement.class);
    return node != null ? node.getDeclaredType() : null;
  }

  @NotNull
  @Override
  public XPathType getType() {
    return XPath2Type.BOOLEAN;
  }
}
