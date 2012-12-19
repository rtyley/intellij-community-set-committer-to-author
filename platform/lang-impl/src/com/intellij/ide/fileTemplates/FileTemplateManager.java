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

package com.intellij.ide.fileTemplates;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.Collection;
import java.util.Properties;

/**
 * @author MYakovlev
 * Date: Jul 24, 2002
 */
public abstract class FileTemplateManager{
  public static final Key<Properties> DEFAULT_TEMPLATE_PROPERTIES = Key.create("DEFAULT_TEMPLATE_PROPERTIES");
  public static final int RECENT_TEMPLATES_SIZE = 25;

  @NonNls 
  public static final String INTERNAL_HTML_TEMPLATE_NAME = "Html";
  @NonNls 
  public static final String INTERNAL_HTML5_TEMPLATE_NAME = "Html5";
  @NonNls 
  public static final String INTERNAL_XHTML_TEMPLATE_NAME = "Xhtml";
  @NonNls 
  public static final String FILE_HEADER_TEMPLATE_NAME = "File Header";
  public static final String DEFAULT_TEMPLATES_CATEGORY = "Default";
  public static final String INTERNAL_TEMPLATES_CATEGORY = "Internal";
  public static final String INCLUDES_TEMPLATES_CATEGORY = "Includes";
  public static final String CODE_TEMPLATES_CATEGORY = "Code";
  public static final String J2EE_TEMPLATES_CATEGORY = "J2EE";

  public static FileTemplateManager getInstance(){
    return ServiceManager.getService(FileTemplateManager.class);
  }

  @NotNull 
  public abstract FileTemplate[] getAllTemplates();

  public abstract FileTemplate getTemplate(@NotNull @NonNls String templateName);

  @NotNull 
  public abstract Properties getDefaultProperties();

  @NotNull
  public Properties getDefaultProperties(Project project) {
    Properties properties = getDefaultProperties();
    properties.setProperty("PROJECT_NAME", project.getName());
    return properties;
  }

  /**
   * Creates a new template with specified name.
   * @param name
   * @return created template
   */
  @NotNull 
  public abstract FileTemplate addTemplate(@NotNull @NonNls String name, @NotNull @NonNls String extension);

  public abstract void removeTemplate(@NotNull FileTemplate template);
  //public abstract void removeInternal(@NotNull FileTemplate template);

  @NotNull 
  public abstract Collection<String> getRecentNames();

  public abstract void addRecentName(@NotNull @NonNls String name);

  public abstract FileTemplate getInternalTemplate(@NotNull @NonNls String templateName);
  @NotNull 
  public abstract FileTemplate[] getInternalTemplates();

  public abstract FileTemplate getJ2eeTemplate(@NotNull @NonNls String templateName);
  public abstract FileTemplate getCodeTemplate(@NotNull @NonNls String templateName);

  @NotNull 
  public abstract FileTemplate[] getAllPatterns();

  @NotNull 
  public abstract FileTemplate[] getAllCodeTemplates();
  
  @NotNull 
  public abstract FileTemplate[] getAllJ2eeTemplates();

  @TestOnly
  public abstract FileTemplate addInternal(@NotNull @NonNls String name, @NotNull @NonNls String extension);

  @NotNull public abstract String internalTemplateToSubject(@NotNull @NonNls String templateName);

  @NotNull public abstract String localizeInternalTemplateName(final FileTemplate template);

  public abstract FileTemplate getPattern(@NotNull @NonNls String name);

  @NotNull
  public abstract FileTemplate getDefaultTemplate(@NotNull @NonNls String name);

  public abstract void setTemplates(@NotNull String templatesCategory, Collection<FileTemplate> templates);
  
}
