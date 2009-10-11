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

package com.intellij.conversion.impl;

import com.intellij.conversion.CannotConvertException;
import com.intellij.conversion.ComponentManagerSettings;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author nik
 */
public class ComponentManagerSettingsImpl implements ComponentManagerSettings {
  protected final SettingsXmlFile mySettingsFile;
  protected final ConversionContextImpl myContext;

  protected ComponentManagerSettingsImpl(File file, ConversionContextImpl context) throws CannotConvertException {
    myContext = context;
    mySettingsFile = context.getOrCreateFile(file);
  }

  @NotNull
  public Document getDocument() {
    return mySettingsFile.getDocument();
  }

  @NotNull
  public Element getRootElement() {
    return mySettingsFile.getRootElement();
  }

  public Element getComponentElement(@NotNull @NonNls String componentName) {
    return mySettingsFile.findComponent(componentName);
  }

  public File getFile() {
    return mySettingsFile.getFile();
  }
}
