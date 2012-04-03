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
package com.intellij.uiDesigner.compiler;

import org.jetbrains.asm4.Type;
import org.jetbrains.asm4.commons.GeneratorAdapter;
import org.jetbrains.asm4.commons.Method;

import java.awt.*;

/**
 * @author yole
 */
public class InsetsPropertyCodeGenerator extends PropertyCodeGenerator {
  private final Type myInsetsType = Type.getType(Insets.class);

  public void generatePushValue(final GeneratorAdapter generator, final Object value) {
    final Insets insets = (Insets)value;
    generator.newInstance(myInsetsType);
    generator.dup();
    generator.push(insets.top);
    generator.push(insets.left);
    generator.push(insets.bottom);
    generator.push(insets.right);
    generator.invokeConstructor(myInsetsType, Method.getMethod("void <init>(int,int,int,int)"));
  }
}
