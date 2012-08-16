/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.devkit.model;

import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsTypedModule;
import org.jetbrains.jps.model.serialization.JpsSerializationTestCase;

/**
 * @author nik
 */
public class JpsPluginProjectSerializationTest extends JpsSerializationTestCase {
  public void testLoadProject() {
    loadProject("plugins/devkit/jps-plugin/testData/pluginProject/pluginProject.ipr");
    JpsModule module = assertOneElement(myProject.getModules());
    assertEquals(JpsPluginModuleType.INSTANCE, module.getModuleType());
    JpsTypedModule<JpsSimpleElement<JpsPluginModuleProperties>> pluginModule = module.asTyped(JpsPluginModuleType.INSTANCE);
    assertNotNull(pluginModule);
    String url = pluginModule.getProperties().getData().getPluginXmlUrl();
    assertEquals(getUrl("META-INF/plugin.xml"), url);
  }
}
