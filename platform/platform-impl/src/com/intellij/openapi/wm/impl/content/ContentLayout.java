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
package com.intellij.openapi.wm.impl.content;

import com.intellij.ui.content.ContentManagerEvent;

import java.awt.*;

abstract class ContentLayout {

  ToolWindowContentUi myUi;

  ContentLayout(ToolWindowContentUi ui) {
    myUi = ui;
  }

  public abstract void layout();

  public abstract void paintComponent(Graphics g);

  public abstract void paintChildren(Graphics g);

  public abstract void update();

  public abstract void rebuild();

  public abstract void contentAdded(ContentManagerEvent event);

  public abstract void contentRemoved(ContentManagerEvent event);
}
