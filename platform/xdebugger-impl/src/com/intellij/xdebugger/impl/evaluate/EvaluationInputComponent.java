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
package com.intellij.xdebugger.impl.evaluate;

import com.intellij.xdebugger.impl.ui.XDebuggerEditorBase;

import javax.swing.*;

/**
 * @author nik
 */
public abstract class EvaluationInputComponent {
  private final String myTitle;

  protected EvaluationInputComponent(String title) {
    myTitle = title;
  }

  public String getTitle() {
    return myTitle;
  }

  protected abstract XDebuggerEditorBase getInputEditor();

  public abstract void addComponent(JPanel contentPanel, JPanel resultPanel);
}
