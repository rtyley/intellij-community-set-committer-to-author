/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon;

import com.intellij.AbstractBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

/**
 * @author max
 */
public class JavaErrorMessages extends AbstractBundle {
  public static final JavaErrorMessages INSTANCE = new JavaErrorMessages();

  @NonNls public static final String BUNDLE = "messages.JavaErrorMessages";

  private JavaErrorMessages() {
    super(BUNDLE);
  }

  public static String message(@PropertyKey(resourceBundle = BUNDLE)String key, Object... params) {
    return INSTANCE.getMessage(key, params);
  }
}
