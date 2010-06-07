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

package com.intellij.util.net;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;

/**
 * @author yole
 */
public class NetUtils {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.net.NetUtils");

  private NetUtils() {
  }

  public static int findAvailableSocketPort() throws IOException {
    final ServerSocket serverSocket = new ServerSocket(0);
    int port = serverSocket.getLocalPort();
    //workaround for linux : calling close() immediately after opening socket
    //may result that socket is not closed
    synchronized(serverSocket) {
      try {
        serverSocket.wait(1);
      }
      catch (InterruptedException e) {
        LOG.error(e);
      }
    }
    serverSocket.close();
    return port;
  }

  public static int[] findAvailableSocketPorts(int capacity) throws IOException {
    final int[] ports = new int[capacity];
    final ServerSocket[] sockets = new ServerSocket[capacity];

    for (int i = 0; i < capacity; i++) {
      final ServerSocket serverSocket = new ServerSocket(0);
      sockets[i] = serverSocket;
      ports[i] = serverSocket.getLocalPort();
    }
    //workaround for linux : calling close() immediately after opening socket
    //may result that socket is not closed
    synchronized(sockets) {
      try {
        sockets.wait(1);
      }
      catch (InterruptedException e) {
        LOG.error(e);
      }
    }

    for (ServerSocket socket : sockets) {
      socket.close();
    }
    return ports;
  }

  public static String getLocalHostString() {
    // HACK for Windows with ipv6
    String localHostString = "localhost";
    try {
      final InetAddress localHost = InetAddress.getByName(localHostString);
      if (localHost.getAddress().length != 4 && SystemInfo.isWindows){
        localHostString = "127.0.0.1";
      }
    }
    catch (UnknownHostException e) {
      // ignore
    }
    return localHostString;
  }
}
