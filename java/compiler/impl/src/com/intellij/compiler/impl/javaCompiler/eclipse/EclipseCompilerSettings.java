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
package com.intellij.compiler.impl.javaCompiler.eclipse;

import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import org.jdom.Element;

import java.nio.charset.Charset;
import java.util.StringTokenizer;

@State(
  name = "EclipseCompilerSettings",
  storages = {
    @Storage(id = "default", file = "$PROJECT_FILE$")
   ,@Storage(id = "dir", file = "$PROJECT_CONFIG_DIR$/compiler.xml", scheme = StorageScheme.DIRECTORY_BASED)
    }
)
public class EclipseCompilerSettings implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.javaCompiler.eclipse.EclipseCompilerSettings");

  public boolean DEBUGGING_INFO = true;
  public boolean GENERATE_NO_WARNINGS = true;
  public boolean DEPRECATION = false;
  public String ADDITIONAL_OPTIONS_STRING = "";
  public int MAXIMUM_HEAP_SIZE = 128;


  public Element getState() {
    try {
      final Element e = new Element("state");
      DefaultJDOMExternalizer.writeExternal(this, e);
      return e;
    }
    catch (WriteExternalException e1) {
      LOG.error(e1);
      return null;
    }
  }

  public void loadState(Element state) {
    try {
      DefaultJDOMExternalizer.readExternal(this, state);
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String getOptionsString() {
    StringBuilder options = new StringBuilder();
    if(DEBUGGING_INFO) {
      options.append("-g ");
    }
    if(DEPRECATION) {
      options.append("-deprecation ");
    }
    if(GENERATE_NO_WARNINGS) {
      options.append("-nowarn ");
    }
    boolean isEncodingSet = false;
    final StringTokenizer tokenizer = new StringTokenizer(ADDITIONAL_OPTIONS_STRING, " \t\r\n");
    while(tokenizer.hasMoreTokens()) {
      String token = tokenizer.nextToken();
      if("-g".equals(token)) {
        continue;
      }
      if("-deprecation".equals(token)) {
        continue;
      }
      if("-nowarn".equals(token)) {
        continue;
      }
      options.append(token);
      options.append(" ");
      if ("-encoding".equals(token)) {
        isEncodingSet = true;
      }
    }
    if (!isEncodingSet) {
      final Charset ideCharset = EncodingManager.getInstance().getDefaultCharset();
      if (CharsetToolkit.getDefaultSystemCharset() != ideCharset) {
        options.append("-encoding ");
        options.append(ideCharset.name());
      }
    }
    return options.toString();
  }

  public static EclipseCompilerSettings getInstance(Project project) {
    return ServiceManager.getService(project, EclipseCompilerSettings.class);
  }

}
