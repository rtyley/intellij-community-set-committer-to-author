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
package com.intellij.util.xml.actions.generate;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * User: Sergey.Vasiliev
 */
public abstract class DomTemplateRunner {

  public static DomTemplateRunner getInstance(Project project) {
    return ServiceManager.getService(project, DomTemplateRunner.class);
  }
  
  public abstract <T extends DomElement> void  runTemplate(final T t, final String mappingId, final Editor editor);

  public abstract <T extends DomElement> void  runTemplate(final T t, final String mappingId, final Editor editor,@NotNull Map<String, String> predefinedVars);

}
