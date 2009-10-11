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

/*
 * User: anna
 * Date: 11-Jun-2009
 */
package com.intellij.junit4;

import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runners.ParentRunner;
import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import java.lang.reflect.Method;
import java.util.List;

class IdeaSuite extends Suite {
  private final String myName;

  public IdeaSuite(final RunnerBuilder builder, Class[] classes, String name) throws InitializationError {
    super(builder, classes);
    myName = name;
  }

  public Description getDescription() {
    Description description = Description.createSuiteDescription(myName, getTestClass().getAnnotations());
    try {
      final Method getFilteredChildrenMethod = ParentRunner.class.getDeclaredMethod("getFilteredChildren", new Class[0]);
      getFilteredChildrenMethod.setAccessible(true);
      List filteredChildren = (List)getFilteredChildrenMethod.invoke(this, new Object[0]);
      for (int i = 0, filteredChildrenSize = filteredChildren.size(); i < filteredChildrenSize; i++) {
        Object child = filteredChildren.get(i);
        description.addChild(describeChild((Runner)child));
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    return description;
  }
}