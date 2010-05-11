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
package org.jetbrains.idea.maven.facade;

import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class MavenFacadeUtil {
  private static volatile Properties mySystemPropertiesCache;

  public static Properties collectSystemProperties() {

    if (mySystemPropertiesCache == null) {
      Properties result = new Properties();
      result.putAll(getSystemProperties());

      Properties envVars = getEnvProperties();
      for (Map.Entry<Object, Object> each : envVars.entrySet()) {
        result.setProperty("env." + each.getKey().toString(), each.getValue().toString());
      }
      mySystemPropertiesCache = result;
    }

    return mySystemPropertiesCache;
  }

  public static void resetSystemPropertiesCache() {
    mySystemPropertiesCache = null;
  }

  @SuppressWarnings({"unchecked"})
  private static Properties getSystemProperties() {
    Properties result = (Properties)System.getProperties().clone();
    for (String each : new HashSet<String>((Set)result.keySet())) {
      if (each.startsWith("idea.")) {
        result.remove(each);
      }
    }
    return result;
  }

  private static Properties getEnvProperties() {
    Properties result = new Properties();
    for (Map.Entry<String, String> each : System.getenv().entrySet()) {
      if (isMagicalProperty(each.getKey())) continue;
      result.put(each.getKey(), each.getValue());
    }
    return result;
  }

  private static boolean isMagicalProperty(String key) {
    return key.startsWith("=");
  }
}