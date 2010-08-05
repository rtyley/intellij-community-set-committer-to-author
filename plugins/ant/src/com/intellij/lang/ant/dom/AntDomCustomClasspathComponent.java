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

import com.intellij.util.xml.Attribute;
import com.intellij.util.xml.Convert;
import com.intellij.util.xml.GenericAttributeValue;

import java.io.File;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Aug 5, 2010
 */
public abstract class AntDomCustomClasspathComponent extends AntDomNamedElement {
  @Attribute("uri")
  public abstract GenericAttributeValue<String> getUri();
  
  @Attribute("classpath")
  @Convert(value = AntMultiPathStringConverter.class)
  public abstract GenericAttributeValue<List<File>> getClasspath();

  @Attribute("classpathref")
  @Convert(value = AntDomRefIdConverter.class)
  public abstract GenericAttributeValue<AntDomElement> getClasspathRef();

  @Attribute("loaderref")
  public abstract GenericAttributeValue<String> getLoaderRef();

}
