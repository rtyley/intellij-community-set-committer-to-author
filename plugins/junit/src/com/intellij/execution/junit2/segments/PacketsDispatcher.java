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

package com.intellij.execution.junit2.segments;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.rt.execution.junit.segments.PacketProcessor;
import com.intellij.util.containers.ContainerUtil;

import java.util.List;

public class PacketsDispatcher implements PacketProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.junit2.segments.PacketsDispatcher");
  private final List<PacketConsumer> myListeners = ContainerUtil.createEmptyCOWList();
  private final InputObjectRegistry myObjectRegistry;

  public PacketsDispatcher() {
    this(new InputObjectRegistryImpl());
  }

  public PacketsDispatcher(final InputObjectRegistry objectRegistry) {
    myObjectRegistry = objectRegistry;
    addListener(objectRegistry);
  }

  public void addListener(final PacketConsumer objectConsumer) {
    if (myListeners.contains(objectConsumer)) return;
    myListeners.add(objectConsumer);
  }

  public void processPacket(final String packet) {
    assertIsDispatchThread();
    for (final PacketConsumer listener : myListeners) {
      final String prefix = listener.getPrefix();
      if (packet.startsWith(prefix)) {
        try {
          listener.readPacketFrom(new ObjectReader(packet, prefix.length(), myObjectRegistry));
        }
        catch (Throwable e) {
          LOG.error("Dispatching: " + packet, e);
        }
      }
    }
  }

  public static void assertIsDispatchThread() {
    final Application application = ApplicationManager.getApplication();
    if (application != null && !application.isUnitTestMode())
      application.assertIsDispatchThread();
  }

  public void onFinished() {
    for (final PacketConsumer listener : myListeners) {
      listener.onFinished();
    }
  }
}

