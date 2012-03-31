/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.intelliLang.pattern.compiler;

import org.jetbrains.asm4.ClassVisitor;
import org.jetbrains.asm4.Opcodes;

public abstract class Instrumenter extends ClassVisitor {
  protected Instrumenter() {
    super(Opcodes.ASM4);
  }
  protected Instrumenter(ClassVisitor visitor) {
    super(Opcodes.ASM4, visitor);
  }

  public abstract boolean instrumented();
}
