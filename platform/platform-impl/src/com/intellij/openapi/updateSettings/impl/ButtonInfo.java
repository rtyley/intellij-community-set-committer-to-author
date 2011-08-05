/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.updateSettings.impl;

import org.jdom.Element;

/**
 * @author yole
 */
public class ButtonInfo {
  private final String myName;
  private final String myUrl;

  public ButtonInfo(Element element) {
    myName = element.getAttributeValue("name");
    myUrl = element.getAttributeValue("url");
  }

  public String getName() {
    return myName;
  }

  public String getUrl() {
    return myUrl;
  }
}
