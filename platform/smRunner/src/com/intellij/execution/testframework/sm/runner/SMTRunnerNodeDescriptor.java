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
package com.intellij.execution.testframework.sm.runner;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;

/**
 * @author: Roman Chernyatchik
 */
public class SMTRunnerNodeDescriptor extends NodeDescriptor<SMTestProxy>
{
  private final SMTestProxy myElement;

  public SMTRunnerNodeDescriptor(final Project project,
                                final SMTestProxy element,
                                final NodeDescriptor<SMTestProxy> parentDesc) {
    super(project, parentDesc);
    myElement = element;
    myName = element.getName();
  }

  @Override
  public boolean update() {
    return false;
  }

  @Override
  public SMTestProxy getElement() {
    return myElement;
  }

  public boolean expandOnDoubleClick() {
    return !myElement.isLeaf();
  }

  @Override
  public String toString() {
    return myName;
  }
}

