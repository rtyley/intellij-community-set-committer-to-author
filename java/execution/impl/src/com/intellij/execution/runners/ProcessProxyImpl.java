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
package com.intellij.execution.runners;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import org.jetbrains.annotations.NonNls;

/**
 * @author ven
 */

class ProcessProxyImpl implements ProcessProxy {
  public static final Key<ProcessProxyImpl> KEY = Key.create("ProcessProxyImpl");
  private final int myPortNumber;

  private static final int SOCKET_NUMBER_START = 7532;
  private static final int SOCKET_NUMBER = 100;
  private static final boolean[] ourUsedSockets = new boolean[SOCKET_NUMBER];

  private PrintWriter myWriter;
  private Socket mySocket;
  @NonNls private static final String DONT_USE_LAUNCHER_PROPERTY = "idea.no.launcher";
  @NonNls public static final String PROPERTY_BINPATH = "idea.launcher.bin.path";
  @NonNls public static final String PROPERTY_PORT_NUMBER = "idea.launcher.port";
  @NonNls public static final String LAUNCH_MAIN_CLASS = "com.intellij.rt.execution.application.AppMain";
  @NonNls
  protected static final String LOCALHOST = "localhost";

  public int getPortNumber() {
    return myPortNumber;
  }

  public static class NoMoreSocketsException extends Exception {
  }

  public ProcessProxyImpl () throws NoMoreSocketsException {
    myPortNumber = getPortNumer();
    if (myPortNumber == -1) throw new NoMoreSocketsException();
  }

  private static int getPortNumer() {
    synchronized (ourUsedSockets) {
      for (int j = 0; j < SOCKET_NUMBER; j++) {
        if (ourUsedSockets[j]) continue;
        try {
          ServerSocket s = new ServerSocket(j + SOCKET_NUMBER_START);
          s.close();
          ourUsedSockets[j] = true;
          return j + SOCKET_NUMBER_START;
        } catch (IOException e) {
          continue;
        }
      }
    }
    return -1;
  }

  public void finalize () throws Throwable {
    if (myWriter != null) {
      myWriter.close();
    }
    ourUsedSockets[myPortNumber - SOCKET_NUMBER_START] = false;
    super.finalize();
  }

  public void attach(final ProcessHandler processHandler) {
    processHandler.putUserData(KEY, this);
  }

  private synchronized void writeLine (@NonNls final String s) {
    if (myWriter == null) {
      try {
        if (mySocket == null)
          mySocket = new Socket(InetAddress.getByName(LOCALHOST), myPortNumber);
        myWriter = new PrintWriter(new BufferedWriter(new OutputStreamWriter(mySocket.getOutputStream())));
      } catch (IOException e) {
        return;
      }
    }
    myWriter.println(s);
    myWriter.flush();
  }

  public void sendBreak () {
    writeLine("BREAK");
  }

  public void sendStop () {
    writeLine("STOP");
  }

  public static boolean useLauncher() {
    if (Boolean.valueOf(System.getProperty(DONT_USE_LAUNCHER_PROPERTY))) {
      return false;
    }

    if (!SystemInfo.isWindows && !SystemInfo.isLinux) {
      return false;
    }
    return new File(getLaunchertLibName()).exists();
  }

  public static String getLaunchertLibName() {
    @NonNls final String libName = SystemInfo.isWindows ? "breakgen.dll" : "libbreakgen.so";
    return PathManager.getBinPath() + File.separator + libName;
  }
}
