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

package com.intellij.util.xmlb;

public class XmlSerializerUtil {
  private XmlSerializerUtil() {
  }

  public static void copyBean(final Object from, final Object to) {
    assert from.getClass().isAssignableFrom(to.getClass()) : "Beans of different classes specified";

    final Accessor[] accessors = BeanBinding.getAccessors(to.getClass());
    for (Accessor accessor : accessors) {
      accessor.write(to, accessor.read(from));
    }
  }
}
