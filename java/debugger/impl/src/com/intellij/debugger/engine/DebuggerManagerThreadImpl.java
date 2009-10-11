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
package com.intellij.debugger.engine;

import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.engine.managerThread.DebuggerCommand;
import com.intellij.debugger.engine.managerThread.DebuggerManagerThread;
import com.intellij.debugger.engine.managerThread.SuspendContextCommand;
import com.intellij.debugger.impl.InvokeAndWaitThread;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorListenerAdapter;
import com.intellij.openapi.progress.util.ProgressWindowWithNotification;
import com.intellij.util.Alarm;
import com.sun.jdi.VMDisconnectedException;
import org.jetbrains.annotations.NotNull;

/**
 * @author lex
 */
public class DebuggerManagerThreadImpl extends InvokeAndWaitThread<DebuggerCommandImpl> implements DebuggerManagerThread {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.DebuggerManagerThreadImpl");
  public static final int COMMAND_TIMEOUT = 3000;
  private static final int RESTART_TIMEOUT = 500;

  DebuggerManagerThreadImpl() {
    //noinspection HardCodedStringLiteral
    super();
  }

  public static DebuggerManagerThreadImpl createTestInstance() {
    return new DebuggerManagerThreadImpl();
  }

  public static boolean isManagerThread() {
    return currentThread() instanceof DebuggerManagerThreadImpl;
  }

  public static void assertIsManagerThread() {
    LOG.assertTrue(isManagerThread(), "Should be invoked in manager thread, use DebuggerManagerThreadImpl.getInstance(..).invoke...");
  }

  public void invokeAndWait(DebuggerCommandImpl managerCommand) {
    LOG.assertTrue(!ApplicationManager.getApplication().isDispatchThread());
    LOG.assertTrue(!(currentThread() instanceof DebuggerManagerThreadImpl),
                   "Should be invoked outside manager thread, use DebuggerManagerThreadImpl.getInstance(..).invoke...");
    super.invokeAndWait(managerCommand);
  }

  public void invoke(DebuggerCommandImpl managerCommand) {
    if (currentThread() instanceof DebuggerManagerThreadImpl) {
      processEvent(managerCommand);
    }
    else {
      schedule(managerCommand);
    }
  }

  public void pushBack(DebuggerCommandImpl managerCommand) {
    if(myEvents.isClosed()) {
      managerCommand.notifyCancelled();
    }
    else {
      super.pushBack(managerCommand);
    }
  }

  public void schedule(DebuggerCommandImpl managerCommand) {
    if(myEvents.isClosed()) {
      managerCommand.notifyCancelled();
    }
    else {
      super.schedule(managerCommand);
    }
  }

  /**
   * waits COMMAND_TIMEOUT milliseconds
   * if worker thread is still processing the same command
   * calls terminateCommand
   */

  public void terminateAndInvoke(DebuggerCommandImpl command, int terminateTimeout) {
    final DebuggerCommandImpl currentCommand = myEvents.getCurrentEvent();

    invoke(command);
    final Alarm alarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);
    alarm.addRequest(new Runnable() {
      public void run() {
        if (currentCommand != null) {
          terminateCommand(currentCommand);
        }
      }
    }, terminateTimeout);
  }

  /**
   * interrupts command in old worker thread and closes old thread.
   * then starts new worker thread
   * <p/>
   * Note: if old thread is working and InterruptedException is not thrown
   * command will continue execution in old thread simulteniously with
   * commands in new thread
   * <p/>
   * use very carefully!
   */

  public void terminateCommand(final DebuggerCommandImpl command) {
    if (command == myEvents.getCurrentEvent()) {
      getCurrentRequest().interrupt();
      try {
        getCurrentRequest().join(RESTART_TIMEOUT);
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
      startNewWorkerThread();
    }
  }

  public DebuggerCommandImpl getCurrentCommand() {
    return myEvents.getCurrentEvent();
  }


  public void processEvent(@NotNull DebuggerCommandImpl managerCommand) {
    assertIsManagerThread();
    try {
      if(myEvents.isClosed()) {
        managerCommand.notifyCancelled();
      }
      else {
        managerCommand.run();
      }
    }
    catch (VMDisconnectedException e) {
      LOG.debug(e);
    }
    catch (RuntimeException e) {
      throw e;
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  public void startProgress(final DebuggerCommandImpl command, final ProgressWindowWithNotification progressWindow) {
    progressWindow.addListener(new ProgressIndicatorListenerAdapter() {
      public void cancelled() {
        command.release();
      }
    });

    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        ProgressManager.getInstance().runProcess(new Runnable() {
          public void run() {
            invokeAndWait(command);
          }
        }, progressWindow);
      }
    });
  }


  public void startLongProcessAndFork(Runnable process) {
    startNewWorkerThread();

    try {
      process.run();
    }
    finally {
      final WorkerThreadRequest request = getCurrentThreadRequest();

      if (LOG.isDebugEnabled()) {
        LOG.debug("Switching back to " + request);
      }

      super.invokeAndWait(new DebuggerCommandImpl() {
        protected void action() throws Exception {
          switchToRequest(request);
        }

        protected void commandCancelled() {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Event queue was closed, killing request");
          }
          request.interrupt();
        }
      });
    }
  }

  public void invokeCommand(final DebuggerCommand command) {
    if(command instanceof SuspendContextCommand) {
      SuspendContextCommand suspendContextCommand = (SuspendContextCommand)command;
      schedule(new SuspendContextCommandImpl((SuspendContextImpl)suspendContextCommand.getSuspendContext()) {
          public void contextAction() throws Exception {
            command.action();
          }

          protected void commandCancelled() {
            command.commandCancelled();
          }
        });
    }
    else {
      schedule(new DebuggerCommandImpl() {
        protected void action() throws Exception {
          command.action();
        }

        protected void commandCancelled() {
          command.commandCancelled();
        }
      });
    }

  }
}
