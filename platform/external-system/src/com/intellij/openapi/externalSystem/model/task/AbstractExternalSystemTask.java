package com.intellij.openapi.externalSystem.model.task;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.service.ExternalSystemFacadeManager;
import com.intellij.openapi.externalSystem.service.RemoteExternalSystemFacade;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Encapsulates particular task performed by external system integration.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 1/24/12 7:03 AM
 */
public abstract class AbstractExternalSystemTask implements ExternalSystemTask {

  private static final Logger LOG = Logger.getInstance("#" + AbstractExternalSystemTask.class.getName());

  private final AtomicReference<ExternalSystemTaskState> myState =
    new AtomicReference<ExternalSystemTaskState>(ExternalSystemTaskState.NOT_STARTED);
  private final AtomicReference<Throwable>               myError = new AtomicReference<Throwable>();

  @NotNull transient private final Project myIdeProject;

  @NotNull private final ExternalSystemTaskId myId;
  @NotNull private final ProjectSystemId      myExternalSystemId;

  protected AbstractExternalSystemTask(@NotNull ProjectSystemId id,
                                       @NotNull ExternalSystemTaskType type,
                                       @NotNull Project project) {
    myExternalSystemId = id;
    myIdeProject = project;
    myId = ExternalSystemTaskId.create(type);
  }

  @NotNull
  public ProjectSystemId getExternalSystemId() {
    return myExternalSystemId;
  }

  @NotNull
  public ExternalSystemTaskId getId() {
    return myId;
  }

  @NotNull
  public ExternalSystemTaskState getState() {
    return myState.get();
  }

  protected void setState(@NotNull ExternalSystemTaskState state) {
    myState.set(state);
  }

  @Override
  public Throwable getError() {
    return myError.get();
  }

  @NotNull
  public Project getIdeProject() {
    return myIdeProject;
  }

  public void refreshState() {
    if (getState() != ExternalSystemTaskState.IN_PROGRESS) {
      return;
    }
    final ExternalSystemFacadeManager manager = ServiceManager.getService(ExternalSystemFacadeManager.class);
    try {
<<<<<<< HEAD:platform/external-system/src/com/intellij/openapi/externalSystem/model/task/AbstractExternalSystemTask.java
      final RemoteExternalSystemFacade facade = manager.getFacade(myIdeProject, myExternalSystemId);
      setState(facade.isTaskInProgress(getId()) ? ExternalSystemTaskState.IN_PROGRESS : ExternalSystemTaskState.FAILED);
=======
      // TODO den implement
//      final RemoteExternalSystemFacade facade = manager.getFacade(myIdeProject);
//      setState(facade.isTaskInProgress(getId()) ? GradleTaskState.IN_PROGRESS : GradleTaskState.FAILED);
>>>>>>> 38a9775... IDEA-104500 Gradle: Allow to reuse common logic for other external systems:plugins/gradle/src/org/jetbrains/plugins/gradle/internal/task/AbstractGradleTask.java
    }
    catch (Throwable e) {
      setState(ExternalSystemTaskState.FAILED);
      myError.set(e);
      if (myIdeProject == null || !myIdeProject.isDisposed()) {
        LOG.warn(e);
      }
    }
  }

  @Override
  public void execute(@NotNull final ProgressIndicator indicator, @NotNull ExternalSystemTaskNotificationListener... listeners) {
    indicator.setIndeterminate(true);
    ExternalSystemTaskNotificationListenerAdapter adapter = new ExternalSystemTaskNotificationListenerAdapter() {
      @Override
      public void onStatusChange(@NotNull ExternalSystemTaskNotificationEvent event) {
        indicator.setText(wrapProgressText(event.getDescription()));
      }
    };
    final ExternalSystemTaskNotificationListener[] ls;
    if (listeners.length > 0) {
      ls = ArrayUtil.append(listeners, adapter);
    }
    else {
      ls = new ExternalSystemTaskNotificationListener[] { adapter };
    }
    
    execute(ls);
  }
  
  @Override
  public void execute(@NotNull ExternalSystemTaskNotificationListener... listeners) {
    ExternalSystemProgressNotificationManager progressManager = ServiceManager.getService(ExternalSystemProgressNotificationManager.class);
    for (ExternalSystemTaskNotificationListener listener : listeners) {
      progressManager.addNotificationListener(getId(), listener);
    }
    try {
      doExecute();
    }
    catch (Throwable e) {
      setState(ExternalSystemTaskState.FAILED);
      myError.set(e);
      LOG.warn(e);
    }
    finally {
      for (ExternalSystemTaskNotificationListener listener : listeners) {
        progressManager.removeNotificationListener(listener);
      }
    }
  }

  protected abstract void doExecute() throws Exception;

  @NotNull
  protected String wrapProgressText(@NotNull String text) {
<<<<<<< HEAD:platform/external-system/src/com/intellij/openapi/externalSystem/model/task/AbstractExternalSystemTask.java
    return ExternalSystemBundle.message("progress.update.text", getExternalSystemId(), text);
=======
    // TODO den implement
    return "";
//    return ExternalSystemBundle.message("gradle.general.progress.update.text", text);
>>>>>>> 38a9775... IDEA-104500 Gradle: Allow to reuse common logic for other external systems:plugins/gradle/src/org/jetbrains/plugins/gradle/internal/task/AbstractGradleTask.java
  }
  
  @Override
  public int hashCode() {
    return myId.hashCode() + myExternalSystemId.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    AbstractExternalSystemTask task = (AbstractExternalSystemTask)o;
    return myId.equals(task.myId) && myExternalSystemId.equals(task.myExternalSystemId);
  }

  @Override
  public String toString() {
    return String.format("%s task %s: %s", ExternalSystemUtil.toReadableName(myExternalSystemId), myId, myState);
  }
}
