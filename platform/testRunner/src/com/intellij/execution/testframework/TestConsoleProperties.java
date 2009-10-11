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

import com.intellij.execution.configurations.RuntimeConfiguration;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.util.StoringPropertyContainer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.util.config.AbstractProperty;
import com.intellij.util.config.BooleanProperty;
import com.intellij.util.config.Storage;

import java.util.ArrayList;
import java.util.HashMap;

public abstract class TestConsoleProperties extends StoringPropertyContainer implements Disposable {
  public static final BooleanProperty SCROLL_TO_STACK_TRACE = new BooleanProperty("scrollToStackTrace", false);
  public static final BooleanProperty SELECT_FIRST_DEFECT = new BooleanProperty("selectFirtsDefect", false);
  public static final BooleanProperty TRACK_RUNNING_TEST = new BooleanProperty("trackRunningTest", true);
  public static final BooleanProperty HIDE_PASSED_TESTS = new BooleanProperty("hidePassedTests", true);
  public static final BooleanProperty SCROLL_TO_SOURCE = new BooleanProperty("scrollToSource", false);
  public static final BooleanProperty OPEN_FAILURE_LINE = new BooleanProperty("openFailureLine", false);
  public static final BooleanProperty TRACK_CODE_COVERAGE = new BooleanProperty("trackCodeCoverage", false);
  public static final BooleanProperty SHOW_STATISTICS = new BooleanProperty("showStatistics", false);

  private final Project myProject;
  private ConsoleView myConsole;

  protected final HashMap<AbstractProperty, ArrayList<TestFrameworkPropertyListener>> myListeners = new HashMap<AbstractProperty, ArrayList<TestFrameworkPropertyListener>>();

  public TestConsoleProperties(final Storage storage, Project project) {
    super(storage);
    myProject = project;
  }

  public Project getProject() { return myProject; }

  public <T> void addListener(final AbstractProperty<T> property, final TestFrameworkPropertyListener<T> listener) {
    ArrayList<TestFrameworkPropertyListener> listeners = myListeners.get(property);
    if (listeners == null) {
      listeners = new ArrayList<TestFrameworkPropertyListener>();
      myListeners.put(property, listeners);
    }
    listeners.add(listener);
  }

  public <T> void addListenerAndSendValue(final AbstractProperty<T> property, final TestFrameworkPropertyListener<T> listener) {
    addListener(property, listener);
    listener.onChanged(property.get(this));
  }

  public <T> void removeListener(final AbstractProperty<T> property, final TestFrameworkPropertyListener listener) {
    final ArrayList<TestFrameworkPropertyListener> listeners = myListeners.get(property);
    if (listeners != null) {
      listeners.remove(listener);
    }
  }

  public abstract boolean isDebug();

  public abstract boolean isPaused();

  protected <T> void onPropertyChanged(final AbstractProperty<T> property, final T value) {
    final ArrayList<TestFrameworkPropertyListener> listeners = myListeners.get(property);
    if (listeners == null) return;
    final Object[] propertyListeners = listeners.toArray();
    for (Object propertyListener : propertyListeners) {
      final TestFrameworkPropertyListener<T> listener = (TestFrameworkPropertyListener<T>)propertyListener;
      listener.onChanged(value);
    }
  }

  public void setConsole(final ConsoleView console) {
    myConsole = console;
  }


  public void dispose() {
    myListeners.clear();
  }

  public abstract RuntimeConfiguration getConfiguration();

  protected ExecutionConsole getConsole() {
    return myConsole;
  }
}
