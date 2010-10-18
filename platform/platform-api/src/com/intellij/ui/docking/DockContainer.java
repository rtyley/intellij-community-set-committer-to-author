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
package com.intellij.ui.docking;

import com.intellij.openapi.Disposable;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.awt.RelativeRectangle;

import javax.swing.*;

public interface DockContainer {

  RelativeRectangle getAcceptArea();
  boolean canAccept(DockableContent content, RelativePoint point);

  JComponent getComponent();

  void add(DockableContent content, RelativePoint dropTarget);

  void closeAll();

  void addListener(Listener listener, Disposable parent);

  boolean isEmpty();

  void startDropOver(DockableContent content, RelativePoint point);
  void processDropOver(DockableContent content, RelativePoint point);
  void resetDropOver(DockableContent content);


  interface Listener {
    void contentAdded(Object key);
    void contentRemoved(Object key);
    
    class Adapter implements Listener {
      @Override
      public void contentAdded(Object key) {
      }

      @Override
      public void contentRemoved(Object key) {
      }
    }
  }
}
