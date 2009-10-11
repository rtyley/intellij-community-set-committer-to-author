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

package com.intellij.openapi.roots.impl;

import com.intellij.openapi.roots.RootProvider;
import com.intellij.openapi.Disposable;
import com.intellij.util.EventDispatcher;

/**
 *  @author dsl
 */
public abstract class RootProviderBaseImpl implements RootProvider {
  private final EventDispatcher<RootSetChangedListener> myDispatcher = EventDispatcher.create(RootSetChangedListener.class);
  public void addRootSetChangedListener(RootSetChangedListener listener) {
    myDispatcher.addListener(listener);
  }

  public void removeRootSetChangedListener(RootSetChangedListener listener) {
    myDispatcher.removeListener(listener);
  }

  public void addRootSetChangedListener(RootSetChangedListener listener, Disposable parentDisposable) {
    myDispatcher.addListener(listener, parentDisposable);
  }

  protected void fireRootSetChanged() {
    myDispatcher.getMulticaster().rootSetChanged(this);
  }

}
