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

/*
 * @author max
 */
package com.intellij.ide.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NonNls;

public class ModuleStructureComponent extends SimpleToolWindowPanel implements Disposable, DataProvider {
  private final ModuleStructurePane myStructurePane;

  public ModuleStructureComponent(Module module) {
    super(true, true);

    myStructurePane = new ModuleStructurePane(module);
    Disposer.register(this, myStructurePane);

    setContent(myStructurePane.createComponent());
  }

  public Object getData(@NonNls String dataId) {
    return myStructurePane.getData(dataId);
  }

  public void dispose() {
    
  }
}
