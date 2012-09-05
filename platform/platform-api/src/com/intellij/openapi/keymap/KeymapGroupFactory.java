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
package com.intellij.openapi.keymap;

import com.intellij.openapi.components.ServiceManager;

import javax.swing.*;

/**
 * @author yole
 */
public abstract class KeymapGroupFactory {
  public static KeymapGroupFactory getInstance() {
    return ServiceManager.getService(KeymapGroupFactory.class);
  }

  public abstract KeymapGroup createGroup(String name);
  public abstract KeymapGroup createGroup(String name, Icon icon);

  /**
   * closed/open icons supposed to be the same
   */
  @Deprecated
  public KeymapGroup createGroup(String name, Icon closedIcon, @SuppressWarnings("unused") Icon openIcon) {
    return createGroup(name, closedIcon);
  }
}
