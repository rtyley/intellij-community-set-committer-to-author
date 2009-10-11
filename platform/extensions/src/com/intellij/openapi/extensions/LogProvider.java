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
package com.intellij.openapi.extensions;

import org.jetbrains.annotations.NonNls;

/**
 * @author Alexander Kireyev
 */
public interface LogProvider {
  void error(@NonNls String message);
  void error(@NonNls String message, Throwable t);
  void error(Throwable t);

  void warn(@NonNls String message);
  void warn(@NonNls String message, Throwable t);
  void warn(Throwable t);
}
