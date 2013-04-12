package com.intellij.openapi.externalSystem.service.remote.wrapper;

import com.intellij.openapi.externalSystem.model.DataHolder;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.service.remote.ExternalSystemExecutionSettings;
import com.intellij.openapi.externalSystem.service.remote.RemoteExternalSystemProgressNotificationManager;
import com.intellij.openapi.externalSystem.service.remote.RemoteExternalSystemProjectResolver;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.rmi.RemoteException;

/**
 * Intercepts calls to the target {@link RemoteExternalSystemProjectResolver} and
 * {@link ExternalSystemTaskNotificationListener#onQueued(ExternalSystemTaskId) updates 'queued' task status}.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 2/8/12 7:21 PM
 */
public class ExternalSystemProjectResolverWrapper<S extends ExternalSystemExecutionSettings>
  extends AbstractRemoteExternalSystemServiceWrapper<S, RemoteExternalSystemProjectResolver<S>>
  implements RemoteExternalSystemProjectResolver<S>
{

  @NotNull private final RemoteExternalSystemProgressNotificationManager myProgressManager;

  public ExternalSystemProjectResolverWrapper(@NotNull RemoteExternalSystemProjectResolver<S> delegate,
                                              @NotNull RemoteExternalSystemProgressNotificationManager progressManager)
  {
    super(delegate);
    myProgressManager = progressManager;
  }

  @Nullable
  @Override
  public DataHolder<ProjectData> resolveProjectInfo(@NotNull ExternalSystemTaskId id,
                                                    @NotNull String projectPath,
                                                    boolean downloadLibraries,
                                                    @Nullable S settings)
    throws ExternalSystemException, IllegalArgumentException, IllegalStateException, RemoteException
  {
    myProgressManager.onQueued(id);
    try {
      return getDelegate().resolveProjectInfo(id, projectPath, downloadLibraries, settings);
    }
    finally {
      myProgressManager.onEnd(id);
    }
  }
}
