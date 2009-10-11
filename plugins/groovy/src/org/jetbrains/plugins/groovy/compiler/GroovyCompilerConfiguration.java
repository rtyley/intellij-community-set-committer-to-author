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

package org.jetbrains.plugins.groovy.compiler;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.compiler.options.ExcludedEntriesConfiguration;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.xmlb.annotations.Tag;
import org.jdom.Element;

/**
 * @author peter
 */
@State(
  name = "GroovyCompilerConfiguration",
  storages = {
    @Storage(id = "default", file = "$WORKSPACE_FILE$"),
    @Storage(id = "dir", file = "$PROJECT_CONFIG_DIR$/groovyc.xml", scheme = StorageScheme.DIRECTORY_BASED)
  }
)
public class GroovyCompilerConfiguration implements PersistentStateComponent<GroovyCompilerConfiguration.MyStateBean>, Disposable {
  private String myHeapSize = "400";
  private ExcludedEntriesConfiguration myExcludeFromStubGeneration = new ExcludedEntriesConfiguration();

  public MyStateBean getState() {
    final MyStateBean bean = new MyStateBean();
    bean.heapSize = myHeapSize;
    myExcludeFromStubGeneration.writeExternal(bean.excludes);
    return bean;
  }

  public static ExcludedEntriesConfiguration getExcludeConfiguration(Project project) {
    return getInstance(project).myExcludeFromStubGeneration;
  }

  public ExcludedEntriesConfiguration getExcludeFromStubGeneration() {
    return myExcludeFromStubGeneration;
  }

  public void loadState(MyStateBean state) {
    myHeapSize = state.heapSize;
    final Element oldStyle = state.option.getChild("ExcludedEntriesConfiguration");
    if (oldStyle != null) {
      myExcludeFromStubGeneration.readExternal(oldStyle);
      return;
    }

    myExcludeFromStubGeneration.readExternal(state.excludes);
  }

  public static GroovyCompilerConfiguration getInstance(Project project) {
    return ServiceManager.getService(project, GroovyCompilerConfiguration.class);
  }

  public String getHeapSize() {
    return myHeapSize;
  }

  public void setHeapSize(String heapSize) {
    myHeapSize = heapSize;
  }

  public void dispose() {
    Disposer.dispose(myExcludeFromStubGeneration);
  }

  public static class MyStateBean {
    public String heapSize = "400";

    @Tag("excludes") public Element excludes = new Element("aaa");

    //todo remove after Maia beta
    @Tag("option") public Element option = new Element("bbb");
  }
}
