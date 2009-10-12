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
package com.intellij.execution.junit;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.CommandLineBuilder;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.junit2.SegmentedInputStream;
import com.intellij.execution.junit2.segments.DeferedActionsQueue;
import com.intellij.execution.junit2.segments.DispatchListener;
import com.intellij.execution.junit2.segments.PacketExtractorBase;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.rt.execution.junit.segments.PacketProcessor;

import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;

/**
 * @author dyoma
 */
public class JUnitProcessHandler extends OSProcessHandler {
  private final Extractor myOut;
  private final Extractor myErr;
  private final Charset myCharset;

  public JUnitProcessHandler(final Process process, final String commandLine, final Charset charset) {
    super(process, commandLine);
    myOut = new Extractor(getProcess().getInputStream(), charset);
    myErr = new Extractor(getProcess().getErrorStream(), charset);
    myCharset = charset;
  }

  protected Reader createProcessOutReader() {
    return myOut.getReader();
  }

  protected Reader createProcessErrReader() {
    return myErr.getReader();
  }

  public PacketExtractorBase getErr() {
    return myErr;
  }

  public PacketExtractorBase getOut() {
    return myOut;
  }

  public Charset getCharset() {
    return myCharset;
  }

  public static JUnitProcessHandler runJava(final JavaParameters javaParameters) throws ExecutionException {
    return runJava(javaParameters, null);
  }

  public static JUnitProcessHandler runJava(final JavaParameters javaParameters, final Project project) throws ExecutionException {
    return runCommandLine(CommandLineBuilder.createFromJavaParameters(javaParameters, project, true));
  }

  public static JUnitProcessHandler runCommandLine(final GeneralCommandLine commandLine) throws ExecutionException {
    final JUnitProcessHandler processHandler = new JUnitProcessHandler(commandLine.createProcess(), commandLine.getCommandLineString(),
                                                                 commandLine.getCharset());
    ProcessTerminatedListener.attach(processHandler);
    return processHandler;
  }

  private class Extractor extends PacketExtractorBase {
    private final SegmentedInputStream myStream;

    public Extractor(final InputStream stream, final Charset charset) {
      myStream = new SegmentedInputStream(stream, charset);
    }

    public void setPacketProcessor(final PacketProcessor packetProcessor) {
      myStream.setEventsDispatcher(new PacketProcessor() {
        public void processPacket(final String packet) {
          perform(new Runnable() {
            public void run() {
              packetProcessor.processPacket(packet);
            }
          });
        }
      });
    }

    public void setFulfilledWorkGate(final DeferedActionsQueue fulfilledWorkGate) {
      super.setFulfilledWorkGate(new DeferedActionsQueue() {
        public void addLast(final Runnable runnable) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              fulfilledWorkGate.addLast(runnable);
            }
          }, ModalityState.NON_MODAL);
        }

        public void setDispactchListener(final DispatchListener listener) {
          fulfilledWorkGate.setDispactchListener(listener);
        }
      });
    }

    public Reader getReader() {
      return new SegmentedInputStreamReader(myStream);
      //return new InputStreamReader(myStream, myCharset);
    }
  }

}
