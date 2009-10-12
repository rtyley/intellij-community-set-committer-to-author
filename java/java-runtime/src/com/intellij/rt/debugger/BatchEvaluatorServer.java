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
 * @author: Eugene Zhuravlev
 * Date: Sep 16, 2002
 * Time: 10:56:58 PM
 */
package com.intellij.rt.debugger;

public class BatchEvaluatorServer {
  Object[] myObjects;

  public Object[] evaluate(Object[] objects) {
    myObjects = objects;
    Object[] result = new Object[objects.length];
    for (int idx = 0; idx < objects.length; idx++) {
      try {
        result[idx] = objects[idx].toString();
      }
      catch (Throwable e) {
        result[idx] = e;
      }
    }
    return result;
  }
}
