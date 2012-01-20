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
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.psi.xml.XmlTag;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.dom.Extensions;

/**
 * @author Dmitry Avdeev
 *         Date: 1/20/12
 */
public abstract class ExtensionsImpl implements Extensions {

  @Override
  public Extension addExtension(String name) {
    Extension extension = addExtension();
    XmlTag tag = extension.getXmlTag();
    tag.setName(name.substring(getDefaultExtensionNs().getStringValue().length() + 1));
    return extension;
  }
}
