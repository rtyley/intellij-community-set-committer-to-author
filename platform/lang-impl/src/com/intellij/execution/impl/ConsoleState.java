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

package com.intellij.execution.impl;

import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.util.Key;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

public abstract class ConsoleState {
  public static final ConsoleState NOT_STARTED = new ConsoleState(){
    public ConsoleState attachTo(final ConsoleViewImpl console, final ProcessHandler processHandler) {
      return new RunningState(console, processHandler);
    }
  };

  public ConsoleState dispose() {
    return NOT_STARTED;
  }

  public boolean isFinished() {
    return false;
  }

  public boolean isRunning() {
    return false;
  }

  public void sendUserInput(final String input) throws IOException {}

  public abstract ConsoleState attachTo(ConsoleViewImpl console, ProcessHandler processHandler);

  private static class RunningState extends ConsoleState {
    private final ConsoleViewImpl myConsole;
    private final ProcessAdapter myProcessListener = new ProcessAdapter() {
      public void onTextAvailable(final ProcessEvent event, final Key outputType) {
            myConsole.print(event.getText(), ConsoleViewContentType.getConsoleViewType(outputType));
      }
    };
    private final ProcessHandler myProcessHandler;
    private final Writer myUserInputWriter;

    public RunningState(final ConsoleViewImpl console, final ProcessHandler processHandler) {
      myConsole = console;
      myProcessHandler = processHandler;
      processHandler.addProcessListener(myProcessListener);
      final OutputStream processInput = myProcessHandler.getProcessInput();
      myUserInputWriter = processInput != null ? new OutputStreamWriter(processInput) : null;
    }

    public ConsoleState dispose() {
      if (myProcessHandler != null) {
        myProcessHandler.removeProcessListener(myProcessListener);
      }
      return NOT_STARTED;
    }

    public boolean isFinished() {
      return myProcessHandler == null || myProcessHandler.isProcessTerminated();
    }

    public boolean isRunning() {
      return myProcessHandler != null && !myProcessHandler.isProcessTerminated();
    }

    public void sendUserInput(final String input) throws IOException {
      if (myUserInputWriter == null)
        throw new IOException(ExecutionBundle.message("no.user.process.input.error.message"));
      myUserInputWriter.write(input);
      myUserInputWriter.flush();
    }

    public ConsoleState attachTo(final ConsoleViewImpl console, final ProcessHandler processHandler) {
      return dispose().attachTo(console, processHandler);
    }
  }
}
