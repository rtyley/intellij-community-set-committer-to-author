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
package com.intellij.lang.ant.dom;

import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 16, 2010
 */
public class AntDomAttributeValueConverter extends ResolvingConverter<XmlAttributeValue>{
  @NotNull
  public Collection<? extends XmlAttributeValue> getVariants(ConvertContext context) {
    return Collections.emptyList();
  }

  @Nullable
  public XmlAttributeValue fromString(@Nullable @NonNls String s, ConvertContext context) {
    final DomElement invocationElement = context.getInvocationElement();
    if (invocationElement instanceof GenericAttributeValue) {
      return ((GenericAttributeValue)invocationElement).getXmlAttributeValue();
    }
    return null;
  }


  public String toString(@Nullable XmlAttributeValue attribValue, ConvertContext context) {
    if (attribValue == null) {
      return null;
    }
    return attribValue.getValue();
  }
}
