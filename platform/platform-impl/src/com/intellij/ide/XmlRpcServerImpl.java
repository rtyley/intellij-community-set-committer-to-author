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
package com.intellij.ide;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.application.ApplicationManager;
import org.apache.xmlrpc.IdeaAwareWebServer;
import org.apache.xmlrpc.IdeaAwareXmlRpcServer;
import org.apache.xmlrpc.WebServer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;

/**
 * @author mike
 */
public class XmlRpcServerImpl implements XmlRpcServer, ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.XmlRpcServerImpl");
  public static final int PORT_NUMBER = 63342;
  public static int detectedPortNumber = -1;
  private WebServer myWebServer;
  @NonNls private static final String PROPERTY_RPC_PORT = "rpc.port";

  @NotNull
  @NonNls
  public String getComponentName() {
    return "XmlRpcServer";
  }

  public void initComponent() {
    if (ApplicationManager.getApplication().isUnitTestMode() || !checkPort()) return;

    try {
      myWebServer = new IdeaAwareWebServer(getPortNumber(), null, new IdeaAwareXmlRpcServer());
      myWebServer.start();
    }
    catch (Exception e) {
      LOG.error(e);
      myWebServer = null;
    }
  }

  public int getPortNumber() {
    return detectedPortNumber == -1 ? getPortImpl() : detectedPortNumber;
  }

  private static int getPortImpl() {
    if (System.getProperty(PROPERTY_RPC_PORT) != null) return Integer.parseInt(System.getProperty(PROPERTY_RPC_PORT));
    return PORT_NUMBER;
  }

  private static boolean checkPort() {
    ServerSocket socket = null;
    try {
      try {
        socket = new ServerSocket(getPortImpl());
      }
      catch (BindException e) {
        try {
          // try any port
          socket = new ServerSocket(0);
          detectedPortNumber = socket.getLocalPort();
          return true;
        } catch (BindException e1) {
          // fallthrow
        }
        return false;
      }
    }
    catch (IOException e) {
      LOG.info(e);
      return false;
    }
    finally {
      if (socket != null) {
        try {
          socket.close();
        }
        catch (IOException e) {
          LOG.error(e);
          return false;
        }
      }
    }
    return true;
  }

  public void disposeComponent() {
    if (myWebServer != null) {
      myWebServer.shutdown();
    }
  }

  public void addHandler(String name, Object handler) {
    if (myWebServer != null) {
      myWebServer.addHandler(name, handler);
    }
    else {
      LOG.info("Handler not registered because XML-RPC server is not running");
    }
  }

  public void removeHandler(String name) {
    if (myWebServer != null) {
      myWebServer.removeHandler(name);
    }
  }
}
