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
 * User: anna
 * Date: 13-Jul-2007
 */
package com.intellij.ide.util.projectWizard;

import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.ModifiableRootModel;

public class EmptyModuleBuilder extends ModuleBuilder{
  public boolean isOpenProjectSettingsAfter() {
    return true;
  }

  public boolean canCreateModule() {
    return false;
  }

  public void setupRootModel(ModifiableRootModel modifiableRootModel) throws ConfigurationException {
    //do nothing
  }

  public ModuleType getModuleType() {
    return ModuleType.EMPTY;
  }
}