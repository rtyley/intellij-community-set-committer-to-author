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
package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.module.Module;
import org.jdom.Element;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.utils.MavenJDOMUtil;

import java.util.List;
import java.util.Map;

public class Groovy_1_1_plus_Importer extends GroovyImporter {
  public Groovy_1_1_plus_Importer() {
    super("org.codehaus.gmaven", "gmaven-plugin");
  }
}