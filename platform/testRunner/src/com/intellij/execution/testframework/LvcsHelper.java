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
 * Date: 25-May-2007
 */
package com.intellij.execution.testframework;

import com.intellij.execution.ExecutionBundle;
import com.intellij.history.LocalHistory;

import java.awt.*;

public class LvcsHelper {
  private static final Color RED = new Color(250, 220, 220);
  private static final Color GREEN = new Color(220, 250, 220);

  public static void addLabel(final TestFrameworkRunningModel model) {
    String label;
    int color;

    if (model.getRoot().isDefect()) {
      color = RED.getRGB();
      label = ExecutionBundle.message("junit.runing.info.tests.failed.label");
    }
    else {
      color = GREEN.getRGB();
      label = ExecutionBundle.message("junit.runing.info.tests.passed.label");
    }
    final TestConsoleProperties consoleProperties = model.getProperties();
    String name = label + " " + consoleProperties.getConfiguration().getName();
    LocalHistory.getInstance().putSystemLabel(consoleProperties.getProject(), name, color);
  }
}