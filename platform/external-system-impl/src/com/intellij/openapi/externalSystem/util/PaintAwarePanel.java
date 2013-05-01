/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.util;

import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Set;

/**
 * @author Denis Zhdanov
 * @since 4/30/13 10:17 PM
 */
public class PaintAwarePanel extends JPanel {

  @Nullable private Consumer<Graphics> myPaintCallback;

  public PaintAwarePanel(LayoutManager layout) {
    super(layout);
  }

  public PaintAwarePanel() {
  }

  @Override
  public void paint(Graphics g) {
    super.paint(g);
    if (myPaintCallback != null) {
      myPaintCallback.consume(g);
    }
  }

  @Nullable
  public Consumer<Graphics> getPaintCallback() {
    return myPaintCallback;
  }

  public void setPaintCallback(@Nullable Consumer<Graphics> paintCallback) {
    myPaintCallback = paintCallback;
  }
}
