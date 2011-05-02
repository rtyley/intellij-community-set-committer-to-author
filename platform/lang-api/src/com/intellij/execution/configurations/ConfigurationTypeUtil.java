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

package com.intellij.execution.configurations;

import com.intellij.openapi.extensions.Extensions;

import java.util.Arrays;

/**
 * @author yole
 */
public class ConfigurationTypeUtil {
  private ConfigurationTypeUtil() {
  }

  public static <T extends ConfigurationType> T findConfigurationType(final Class<T> configurationTypeClass) {
    ConfigurationType[] types = Extensions.getExtensions(ConfigurationType.CONFIGURATION_TYPE_EP);
    for (ConfigurationType type : types) {
      if (configurationTypeClass.isInstance(type)) {
        //noinspection unchecked
        return (T)type;
      }
    }
    assert false : Arrays.toString(types) + " loader: " + configurationTypeClass.getClassLoader();
    return null;
  }
}
