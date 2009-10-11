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
package com.intellij.packaging.impl.artifacts;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jdom.Element;

/**
 * @author nik
 */
@Tag("properties")
public class ArtifactPropertiesState {
  private String myId;
  private Element myOptions;

  @Attribute("id")
  public String getId() {
    return myId;
  }

  @Tag("options")
  public Element getOptions() {
    return myOptions;
  }

  public void setId(String id) {
    myId = id;
  }

  public void setOptions(Element options) {
    myOptions = options;
  }
}
