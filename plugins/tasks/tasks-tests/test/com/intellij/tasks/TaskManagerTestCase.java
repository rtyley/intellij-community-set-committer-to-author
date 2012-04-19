package com.intellij.tasks;

import com.intellij.tasks.impl.LocalTaskImpl;
import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

import java.util.Collections;

/**
 * @author Dmitry Avdeev
 */
public abstract class TaskManagerTestCase extends LightCodeInsightFixtureTestCase {

  protected TaskManagerTestCase() {
    super(UsefulTestCase.IDEA_MARKER_CLASS, "PlatformLangXml");
  }

  protected TaskManagerImpl myManager;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myManager = (TaskManagerImpl)TaskManager.getManager(getProject());
    removeAllTasks();
  }

  @Override
  protected void tearDown() throws Exception {
    myManager.setRepositories(Collections.<TaskRepository>emptyList());
    removeAllTasks();
    super.tearDown();
  }

  private void removeAllTasks() {
    LocalTaskImpl[] tasks = myManager.getLocalTasks();
    for (LocalTaskImpl task : tasks) {
      myManager.removeTask(task);
    }
  }
}
