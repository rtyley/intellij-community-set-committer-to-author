package com.intellij.tasks.timetracking;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.tasks.TaskManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;

/**
 * User: evgeny.zakrevsky
 * Date: 11/8/12
 */
public class TasksToolWindowFactory implements ToolWindowFactory, Condition<Project>, DumbAware {
  public static final String TOOL_WINDOW_ID = "Time Tracking";

  @Override
  public boolean value(final Project project) {
    return TaskManager.getManager(project).isTimeTrackingToolWindowAvailable();
  }

  @Override
  public void createToolWindowContent(final Project project, final ToolWindow toolWindow) {
    final ContentManager contentManager = toolWindow.getContentManager();
    final Content content = ContentFactory.SERVICE.getInstance().createContent(new TasksToolWindowPanel(project), null, false);
    contentManager.addContent(content);
  }
}
