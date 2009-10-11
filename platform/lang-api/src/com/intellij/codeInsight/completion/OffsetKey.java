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
package com.intellij.codeInsight.completion;

import org.jetbrains.annotations.NonNls;

/**
 * @author peter
 */
public class OffsetKey {
  private final String myName; // for debug purposes only
  private final boolean myMoveableToRight;

  private OffsetKey(@NonNls String name, final boolean moveableToRight) {
    myName = name;
    myMoveableToRight = moveableToRight;
  }

  public String toString() {
    return myName;
  }

  public boolean isMoveableToRight() {
    return myMoveableToRight;
  }

  public static OffsetKey create(@NonNls String name) {
    return create(name, true);
  }

  public static OffsetKey create(@NonNls String name, final boolean moveableToRight) {
    return new OffsetKey(name, moveableToRight);
  }
}
