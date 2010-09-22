/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.ide;

import java.awt.event.InputEvent;

public class TooltipEvent {

  private InputEvent myInputEvent;
  private boolean myIsEventInsideBalloon;

  public TooltipEvent(InputEvent inputEvent) {
    myInputEvent = inputEvent;
  }

  public TooltipEvent(InputEvent inputEvent, boolean isInside) {
    myInputEvent = inputEvent;
    myIsEventInsideBalloon = isInside;
  }

  public InputEvent getInputEvent() {
    return myInputEvent;
  }

  public boolean isIsEventInsideBalloon() {
    return myIsEventInsideBalloon;
  }
}
