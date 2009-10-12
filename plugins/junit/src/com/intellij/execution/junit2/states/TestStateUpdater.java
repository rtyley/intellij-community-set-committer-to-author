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

package com.intellij.execution.junit2.states;

import com.intellij.execution.junit2.TestProxy;
import com.intellij.execution.junit2.TestProxyParent;
import com.intellij.execution.junit2.TestRoot;
import com.intellij.execution.junit2.TestRootImpl;
import com.intellij.execution.junit2.segments.ObjectReader;
import com.intellij.execution.junit2.segments.PacketConsumer;
import com.intellij.execution.testframework.Filter;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.rt.execution.junit.segments.PoolOfDelimiters;
import com.intellij.rt.execution.junit.states.PoolOfTestStates;
import com.intellij.util.containers.HashMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class TestStateUpdater implements PacketConsumer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.junit2.states.TestStateUpdater");
  private static final Map<Integer, StateChanger> STATE_CLASSES = new HashMap<Integer, StateChanger>();
  private TestRoot myTestRoot = new CollectingRoot();

  public static final Filter RUNNING = new Filter() {
    public boolean shouldAccept(final AbstractTestProxy test) {
      return test.getMagnitude() == PoolOfTestStates.RUNNING_INDEX;
    }
  };
  public static final Filter RUNNING_LEAF = RUNNING.and(Filter.LEAF);

  private static abstract class StateChanger {
    abstract void changeStateOf(TestProxy testProxy, ObjectReader reader);

    void setMagnitude(final int magnitude) {
    }

    void modifyTestStack(final TestProxyParent globalRoot, final TestProxy testProxy) {
    }
  }

  private static class RunningStateSetter extends StateChanger {
    public void changeStateOf(final TestProxy testProxy, final ObjectReader reader) {
      testProxy.setState(TestState.RUNNING_STATE);
    }

    public void modifyTestStack(final TestProxyParent globalRoot, final TestProxy test) {
      if (test.getParent() == null)
        globalRoot.addChild(test);
    }
  }

  private static class StateReader extends StateChanger {
    private final Class<? extends ReadableState> myStateClass;
    private int myInstanceMagnitude;

    public StateReader(final Class<? extends ReadableState> stateClass) {
      myStateClass = stateClass;
    }

    public void changeStateOf(final TestProxy testProxy, final ObjectReader reader) {
      final ReadableState state;
      try {
        state = myStateClass.newInstance();
      } catch (Exception e) {
        LOG.error(e);
        return;
      }
      state.setMagitude(myInstanceMagnitude);
      state.initializeFrom(reader);
      testProxy.setState(state);
    }

    public void setMagnitude(final int magnitude) {
      myInstanceMagnitude = magnitude;
    }
  }

  private static class TestCompleter extends StateChanger {
    public void changeStateOf(final TestProxy testProxy, final ObjectReader reader) {
      TestState state = testProxy.getState();
      if (!testProxy.getState().isFinal()) {
        state = NotFailedState.createPassed();
      }
      testProxy.setState(state);
      testProxy.setStatistics(new Statistics(reader));
    }
  }

  static {
    mapClass(PoolOfTestStates.RUNNING_INDEX, new RunningStateSetter());
    mapClass(PoolOfTestStates.COMPLETE_INDEX, new TestCompleter());
    mapClass(PoolOfTestStates.FAILED_INDEX, new StateReader(FaultyState.class));
    mapClass(PoolOfTestStates.ERROR_INDEX, new StateReader(FaultyState.class));
    mapClass(PoolOfTestStates.IGNORED_INDEX, new StateReader(IgnoredState.class));
    mapClass(PoolOfTestStates.SKIPPED_INDEX, new StateReader(SkippedState.class));
    mapClass(PoolOfTestStates.COMPARISON_FAILURE, new StateReader(ComparisonFailureState.class));
  }

  private static void mapClass(final int magnitude, final StateChanger factory) {
    factory.setMagnitude(magnitude);
    STATE_CLASSES.put(new Integer(magnitude), factory);
  }

  public void readPacketFrom(final ObjectReader reader) {
    final TestProxy testProxy = reader.readObject();
    final StateChanger stateChanger = STATE_CLASSES.get(new Integer(reader.readInt()));
    stateChanger.modifyTestStack(myTestRoot, testProxy);
    stateChanger.changeStateOf(testProxy, reader);
  }

  public void onFinished() {
    if (myTestRoot == null) return;
    final List runningTests = RUNNING_LEAF.select(myTestRoot.getAllTests());
    for (final Object runningTest : runningTests) {
      final TestProxy test = (TestProxy)runningTest;
      test.setState(NotFailedState.createTerminated());
    }
  }

  public String getPrefix() {
    return PoolOfDelimiters.CHANGE_STATE;
  }

  public void setRoot(final TestProxy testProxy) {
    final TestRootImpl root = new TestRootImpl(testProxy);
    final List<TestProxy> allTests = myTestRoot.getAllTests();
    for (final TestProxy test : allTests) {
      root.addChild(test);
    }
    myTestRoot = root;
  }

  private static class CollectingRoot implements TestRoot {
    private final ArrayList<TestProxy> myTests = new ArrayList<TestProxy>();
    public List<TestProxy> getAllTests() {
      return Collections.unmodifiableList(myTests);
    }

    public void addChild(final TestProxy child) {
      myTests.add(child);
    }
  }
}
