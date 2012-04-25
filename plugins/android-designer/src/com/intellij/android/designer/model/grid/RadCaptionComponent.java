/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.android.designer.model.grid;

import com.intellij.android.designer.designSurface.layout.CaptionStaticDecorator;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.designer.designSurface.StaticDecorator;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.model.RadVisualComponent;

import java.awt.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public abstract class RadCaptionComponent<T extends RadViewComponent> extends RadVisualComponent {
  private final StaticDecorator myDecorator;
  protected final T myContainer;
  protected final int myIndex;
  protected final int myOffset;
  protected final int myWidth;

  public RadCaptionComponent(T container, int index, int offset, int width, boolean empty) {
    myContainer = container;
    myIndex = index;
    myOffset = offset;
    myWidth = width;

    if (empty) {
      myDecorator = new CaptionStaticDecorator(this, Color.PINK);
    }
    else {
      myDecorator = new CaptionStaticDecorator(this);
    }

    setNativeComponent(container.getNativeComponent());
  }

  public int getIndex() {
    return myIndex;
  }

  @Override
  public void addStaticDecorators(List<StaticDecorator> decorators, List<RadComponent> selection) {
    decorators.add(myDecorator);
  }
}