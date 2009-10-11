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
package com.intellij.util;

/**
 * Utility wrappers for accessing system properties.
 *
 * @author yole
 */

@SuppressWarnings({"HardCodedStringLiteral"})
public class SystemProperties {
  private SystemProperties() {
  }

  /**
   * Returns the value of the user.home system property.
   *
   * @return the property value
   */
  public static String getUserHome() {
    return System.getProperty("user.home");
  }

  public static String getUserName() {
    return System.getProperty("user.name");
  }

  /**
   * Returns the value of the line.separator system property.
   *
   * @return the property value
   */
  public static String getLineSeparator() {
    return System.getProperty("line.separator");
  }

  /**
   * Returns the value of the os.name system property.
   *
   * @return the property value
   */
  public static String getOsName() {
    return System.getProperty("os.name");
  }

  /**
   * Returns the value of the java.version system property.
   *
   * @return the property value
   */
  public static String getJavaVersion() {
    return System.getProperty("java.version");
  }

  /**
   * Returns the value of the java.vm.vendor system property.
   *
   * @return the property value
   */
  public static String getJavaVmVendor() {
    return System.getProperty("java.vm.vendor");
  }

  /**
   * Returns the value of the java.home system property.
   *
   * @return the property value
   */
  public static String getJavaHome() {
    return System.getProperty("java.home");
  }
}
