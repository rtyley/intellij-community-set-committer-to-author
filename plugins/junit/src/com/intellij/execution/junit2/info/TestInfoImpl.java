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

package com.intellij.execution.junit2.info;

import com.intellij.execution.junit2.segments.PacketReader;

abstract class TestInfoImpl implements TestInfo, PacketReader {
  private int myTestCount;

  public boolean shouldRun() {
    return false;
  }

  public int getTestsCount() {
    return myTestCount;
  }

  public void setTestCount(final int testCount) {
    myTestCount = testCount;
  }

  public void onFinished() {
  }
}
