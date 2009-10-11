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

package com.intellij.openapi.util.registry;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;

public class ShowRegistryAction extends AnAction implements DumbAware {

  private RegistryUi myUi;

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(myUi == null);
  }

  public void actionPerformed(AnActionEvent e) {
    myUi = new RegistryUi() {
      @Override
      public void dispose() {
        myUi = null;
      }
    };
    myUi.show();
  }
}