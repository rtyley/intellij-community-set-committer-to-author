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

import com.intellij.execution.junit2.segments.ObjectReader;
import com.intellij.execution.ExecutionBundle;

class DefaultTestInfo extends ClassBasedInfo {
  public DefaultTestInfo() {
    super(DisplayTestInfoExtractor.CLASS_FULL_NAME);
  }

  public void readFrom(final ObjectReader reader) {
    reader.readInt(); //TODO remove test count from packet
    readClass(reader);
  }

  public String getName() {
    return ExecutionBundle.message("test.cases.count.message", getTestsCount());
  }
}
