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
package com.intellij.codeInsight;

import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NonNls;

/**
 * @author peter
*/
public class CharTailType extends TailType {
  private final char myChar;

  public CharTailType(final char aChar) {
    myChar = aChar;
  }

  public int processTail(final Editor editor, final int tailOffset) {
    return insertChar(editor, tailOffset, myChar);
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof CharTailType)) return false;

    final CharTailType that = (CharTailType)o;

    if (myChar != that.myChar) return false;

    return true;
  }

  public int hashCode() {
    return (int)myChar;
  }

  @NonNls
  public String toString() {
    return "CharTailType:\'" + myChar + "\'";
  }
}
