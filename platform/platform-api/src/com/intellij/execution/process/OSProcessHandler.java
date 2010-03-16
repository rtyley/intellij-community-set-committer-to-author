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
package com.intellij.execution.process;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.Semaphore;

import java.io.*;
import java.nio.charset.Charset;
import java.util.concurrent.*;

public class OSProcessHandler extends ProcessHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.process.OSProcessHandler");
  private final Process myProcess;
  private final String myCommandLine;

  private final ProcessWaitFor myWaitFor;

  private static class ExecutorServiceHolder {
    private static ExecutorService ourThreadExecutorsService = createServiceImpl();

    static ThreadPoolExecutor createServiceImpl() {
      return new ThreadPoolExecutor(10, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(), new ThreadFactory() {
        @SuppressWarnings({"HardCodedStringLiteral"})
        public Thread newThread(Runnable r) {
          return new Thread(r, "OSProcessHandler pooled thread");
        }
      });
    }
  }

  /**
   * Override this method in order to execute the task with a custom pool
   *
   * @param task a task to run
   */
  protected Future<?> executeOnPooledThread(Runnable task) {
    final Application application = ApplicationManager.getApplication();

    if (application != null) {
      return application.executeOnPooledThread(task);
    }

    if (ExecutorServiceHolder.ourThreadExecutorsService.isShutdown()) { // in tests: the service might be shut down by a previous test
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      ExecutorServiceHolder.ourThreadExecutorsService = ExecutorServiceHolder.createServiceImpl();
    }
    return ExecutorServiceHolder.ourThreadExecutorsService.submit(task);
  }

  protected static void shutdownExecutorService() {
    final Application application = ApplicationManager.getApplication();
    if (application == null) {
      ExecutorServiceHolder.ourThreadExecutorsService.shutdown();
    }
  }

  public OSProcessHandler(final Process process, final String commandLine) {
    myProcess = process;
    myCommandLine = commandLine;
    myWaitFor = new ProcessWaitFor(process);
  }

  private class ProcessWaitFor  {
    private final Semaphore myWaitSemaphore = new Semaphore();

    private final Future<?> myWaitForThreadFuture;
    private int myExitCode;

    public void detach() {
      myWaitForThreadFuture.cancel(true);
      myWaitSemaphore.up();
    }

    public ProcessWaitFor(final Process process) {
      myWaitSemaphore.down();

      myWaitForThreadFuture = executeOnPooledThread(new Runnable() {
        public void run() {
          try {
            myExitCode = process.waitFor();
          }
          catch (InterruptedException ignored) {
          }
          finally {
            myWaitSemaphore.up();
          }
        }
      });
    }

    public int waitFor() {
      myWaitSemaphore.waitFor();
      return myExitCode;
    }
  }

  public Process getProcess() {
    return myProcess;
  }

  public void startNotify() {
    final ReadProcessThread stdoutThread = new ReadProcessThread(createProcessOutReader()) {
      protected void textAvailable(String s) {
        notifyTextAvailable(s, ProcessOutputTypes.STDOUT);
      }
    };

    final ReadProcessThread stderrThread = new ReadProcessThread(createProcessErrReader()) {
      protected void textAvailable(String s) {
        notifyTextAvailable(s, ProcessOutputTypes.STDERR);
      }
    };

    notifyTextAvailable(myCommandLine + '\n', ProcessOutputTypes.SYSTEM);

    addProcessListener(new ProcessAdapter() {
      public void startNotified(final ProcessEvent event) {
        try {
          final Future<?> stdOutReadingFuture = executeOnPooledThread(stdoutThread);
          final Future<?> stdErrReadingFuture = executeOnPooledThread(stderrThread);

          final Runnable action = new Runnable() {
            public void run() {
              int exitCode = 0;

              try {
                exitCode = myWaitFor.waitFor();

                // tell threads that no more attempts to read process' output should be made
                stderrThread.setProcessTerminated(true);
                stdoutThread.setProcessTerminated(true);

                stdErrReadingFuture.get();
                stdOutReadingFuture.get();
              }
              catch (InterruptedException ignored) {
              }
              catch (ExecutionException ignored) {
              }
              finally {
                onOSProcessTerminated(exitCode);
              }
            }
          };

          executeOnPooledThread(action);
        }
        finally {
          removeProcessListener(this);
        }
      }
    });

    super.startNotify();
  }

  protected void onOSProcessTerminated(final int exitCode) {
    notifyProcessTerminated(exitCode);
  }

  protected Reader createProcessOutReader() {
    return new BufferedReader(new InputStreamReader(myProcess.getInputStream(), getCharset()));
  }

  protected Reader createProcessErrReader() {
    return new BufferedReader(new InputStreamReader(myProcess.getErrorStream(), getCharset()));
  }

  protected void destroyProcessImpl() {
    try {
      closeStreams();
    }
    finally {
      myProcess.destroy();
    }
  }

  protected void detachProcessImpl() {
    final Runnable runnable = new Runnable() {
      public void run() {
        closeStreams();

        myWaitFor.detach();
        notifyProcessDetached();
      }
    };

    executeOnPooledThread(runnable);
  }

  private void closeStreams() {
    try {
      myProcess.getOutputStream().close();
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  public boolean detachIsDefault() {
    return false;
  }

  public OutputStream getProcessInput() {
    return myProcess.getOutputStream();
  }

  // todo: to remove
  public String getCommandLine() {
    return myCommandLine;
  }


  public Charset getCharset() {
    return EncodingManager.getInstance().getDefaultCharset();
  }

  private abstract static class ReadProcessThread implements Runnable {
    private static final int NOTIFY_TEXT_DELAY = 300;

    private final Reader myReader;

    private final StringBuffer myBuffer = new StringBuffer();
    private final Alarm myAlarm;

    private boolean myIsClosed = false;
    private boolean myIsProcessTerminated = false;

    public ReadProcessThread(final Reader reader) {
      myReader = reader;
      myAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);
    }

    public synchronized boolean isProcessTerminated() {
      return myIsProcessTerminated;
    }

    public synchronized void setProcessTerminated(boolean isProcessTerminated) {
      myIsProcessTerminated = isProcessTerminated;
    }

    public void run() {
      Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
      try {
        myAlarm.addRequest(new Runnable() {
          public void run() {
            if(!isClosed()) {
              myAlarm.addRequest(this, NOTIFY_TEXT_DELAY);
              checkTextAvailable();
            }
          }
        }, NOTIFY_TEXT_DELAY);

        try {
          while (!isClosed()) {
            final int c = readNextByte();
            if (c == -1) {
              break;
            }
            synchronized (myBuffer) {
              myBuffer.append((char)c);
            }
            if (c == '\n') { // not by '\r' because of possible '\n'
              checkTextAvailable();
            }
          }
        }
        catch (Exception e) {
          LOG.error(e);
          e.printStackTrace();
        }

        close();
      }
      finally {
        Thread.currentThread().setPriority(Thread.NORM_PRIORITY);
      }
    }

    private int readNextByte() {
      try {
        while(!myReader.ready()) {
          if (isProcessTerminated()) {
            return -1;
          }
          try {
            Thread.sleep(1L);
          }
          catch (InterruptedException ignore) {
          }
        }
        return myReader.read();
      }
      catch (IOException e) {
        return -1; // When process terminated Process.getInputStream()'s underlaying stream becomes closed on Linux.
      }
    }

    private void checkTextAvailable() {
      synchronized (myBuffer) {
        if (myBuffer.length() == 0) return;
        // warning! Since myBuffer is reused, do not use myBuffer.toString() to fetch the string
        // because the created string will get StringBuffer's internal char array as a buffer which is possibly too large.
        final String s = myBuffer.substring(0, myBuffer.length());
        myBuffer.setLength(0);
        textAvailable(s);
      }
    }

    private void close() {
      synchronized (this) {
        if (isClosed()) {
          return;
        }
        myIsClosed = true;
      }
      //try {
      //  if(Thread.currentThread() != this) {
      //    join(0);
      //  }
      //}
      //catch (InterruptedException e) {
      //}
      // must close after the thread finished its execution, cause otherwise
      // the thread will try to read from the closed (and nulled) stream
      try {
        myReader.close();
      }
      catch (IOException e1) {
        // supressed
      }
      checkTextAvailable();
    }

    protected abstract void textAvailable(final String s);

    private synchronized boolean isClosed() {
      return myIsClosed;
    }

  }
}
