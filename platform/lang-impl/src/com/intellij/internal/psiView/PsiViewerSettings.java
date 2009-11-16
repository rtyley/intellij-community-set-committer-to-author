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
package com.intellij.internal.psiView;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;

/**
 * @author Konstantin Bulenkov
 */
@State(
  name = "PsiViewerSettings",
  storages = {@Storage(id = "other",file = "$APP_CONFIG$/other.xml")})
public class PsiViewerSettings implements PersistentStateComponent<PsiViewerSettings> {
  public boolean showWhiteSpaces = true;
  public boolean showTreeNodes = true;
  public String type = "JAVA file";
  public String text = "";
  public String dialect = "";
  public int textDividerLocation = 250;
  public int treeDividerLocation = 400;

  public static PsiViewerSettings getSettings() {
    return ServiceManager.getService(PsiViewerSettings.class);
  }

  public PsiViewerSettings getState() {
    return this;
  }

  public void loadState(PsiViewerSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
