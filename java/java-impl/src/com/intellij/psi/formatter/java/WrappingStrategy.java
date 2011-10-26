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
package com.intellij.psi.formatter.java;

import com.intellij.formatting.Wrap;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.impl.source.tree.ElementType;

public abstract class WrappingStrategy {

  public static final WrappingStrategy DO_NOT_WRAP = new WrappingStrategy(null) {
    @Override
    protected boolean shouldWrap(final IElementType type) {
      return false;
    }
  };

  public static WrappingStrategy createDoNotWrapCommaStrategy(Wrap wrap) {
    return new WrappingStrategy(wrap) {
      @Override
      protected boolean shouldWrap(final IElementType type) {
        return type != ElementType.COMMA && type != ElementType.SEMICOLON;
      }
    };
  }

  private final Wrap myWrap;

  public WrappingStrategy(final Wrap wrap) {
    myWrap = wrap;
  }

  public Wrap getWrap(IElementType type) {
    if (shouldWrap(type)) {
      return myWrap;
    } else {
      return null;
    }
  }

  protected abstract boolean shouldWrap(final IElementType type);
}
