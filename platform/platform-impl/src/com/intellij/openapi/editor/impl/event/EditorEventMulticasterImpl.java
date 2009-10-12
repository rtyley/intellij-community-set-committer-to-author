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
package com.intellij.openapi.editor.impl.event;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.TestOnly;

import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EditorEventMulticasterImpl implements EditorEventMulticasterEx {
  private final EventDispatcher<DocumentListener> myDocumentMulticaster = EventDispatcher.create(DocumentListener.class);
  private final EventDispatcher<EditReadOnlyListener> myEditReadOnlyMulticaster = EventDispatcher.create(EditReadOnlyListener.class);

  private final EventDispatcher<EditorMouseListener> myEditorMouseMulticaster = EventDispatcher.create(EditorMouseListener.class);
  private final EventDispatcher<EditorMouseMotionListener> myEditorMouseMotionMulticaster = EventDispatcher.create(EditorMouseMotionListener.class);
  private final EventDispatcher<ErrorStripeListener> myErrorStripeMulticaster = EventDispatcher.create(ErrorStripeListener.class);
  private final EventDispatcher<CaretListener> myCaretMulticaster = EventDispatcher.create(CaretListener.class);
  private final EventDispatcher<SelectionListener> mySelectionMulticaster = EventDispatcher.create(SelectionListener.class);
  private final EventDispatcher<VisibleAreaListener> myVisibleAreaMulticaster = EventDispatcher.create(VisibleAreaListener.class);
  private final EventDispatcher<PropertyChangeListener> myPropertyChangeMulticaster = EventDispatcher.create(PropertyChangeListener.class);
  private final EventDispatcher<FocusChangeListener> myFocusChangeListenerMulticaster = EventDispatcher.create(FocusChangeListener.class);

  public void registerDocument(DocumentEx document) {
    document.addDocumentListener(myDocumentMulticaster.getMulticaster());
    document.addEditReadOnlyListener(myEditReadOnlyMulticaster.getMulticaster());
  }

  public void registerEditor(EditorEx editor) {
    editor.addEditorMouseListener(myEditorMouseMulticaster.getMulticaster());
    editor.addEditorMouseMotionListener(myEditorMouseMotionMulticaster.getMulticaster());
    ((EditorMarkupModel) editor.getMarkupModel()).addErrorMarkerListener(myErrorStripeMulticaster.getMulticaster());
    editor.getCaretModel().addCaretListener(myCaretMulticaster.getMulticaster());
    editor.getSelectionModel().addSelectionListener(mySelectionMulticaster.getMulticaster());
    editor.getScrollingModel().addVisibleAreaListener(myVisibleAreaMulticaster.getMulticaster());
    editor.addPropertyChangeListener(myPropertyChangeMulticaster.getMulticaster());
    editor.addFocusListener(myFocusChangeListenerMulticaster.getMulticaster());
  }

  public void addDocumentListener(DocumentListener listener) {
    myDocumentMulticaster.addListener(listener);
  }

  public void addDocumentListener(final DocumentListener listener, Disposable parentDisposable) {
    addDocumentListener(listener);
    Disposer.register(parentDisposable, new Disposable() {
      public void dispose() {
        removeDocumentListener(listener);
      }
    });
  }

  public void removeDocumentListener(DocumentListener listener) {
    myDocumentMulticaster.removeListener(listener);
  }

  public void addEditorMouseListener(EditorMouseListener listener) {
    myEditorMouseMulticaster.addListener(listener);
  }

  public void addEditorMouseListener(final EditorMouseListener listener, final Disposable parentDisposable) {
    addEditorMouseListener(listener);
    Disposer.register(parentDisposable, new Disposable() {
      public void dispose() {
        removeEditorMouseListener(listener);
      }
    });
  }

  public void removeEditorMouseListener(EditorMouseListener listener) {
    myEditorMouseMulticaster.removeListener(listener);
  }

  public void addEditorMouseMotionListener(EditorMouseMotionListener listener) {
    myEditorMouseMotionMulticaster.addListener(listener);
  }

  public void addEditorMouseMotionListener(final EditorMouseMotionListener listener, final Disposable parentDisposable) {
    addEditorMouseMotionListener(listener);
    Disposer.register(parentDisposable, new Disposable() {
      public void dispose() {
        removeEditorMouseMotionListener(listener);
      }
    });
  }

  public void removeEditorMouseMotionListener(EditorMouseMotionListener listener) {
    myEditorMouseMotionMulticaster.removeListener(listener);
  }

  public void addCaretListener(CaretListener listener) {
    myCaretMulticaster.addListener(listener);
  }

  public void addCaretListener(final CaretListener listener, final Disposable parentDisposable) {
    addCaretListener(listener);
    Disposer.register(parentDisposable, new Disposable() {
      public void dispose() {
        removeCaretListener(listener);
      }
    });
  }

  public void removeCaretListener(CaretListener listener) {
    myCaretMulticaster.removeListener(listener);
  }

  public void addSelectionListener(SelectionListener listener) {
    mySelectionMulticaster.addListener(listener);
  }

  public void removeSelectionListener(SelectionListener listener) {
    mySelectionMulticaster.removeListener(listener);
  }

  public void addErrorStripeListener(ErrorStripeListener listener) {
    myErrorStripeMulticaster.addListener(listener);
  }

  public void removeErrorStripeListener(ErrorStripeListener listener) {
    myErrorStripeMulticaster.removeListener(listener);
  }

  public void addVisibleAreaListener(VisibleAreaListener listener) {
    myVisibleAreaMulticaster.addListener(listener);
  }

  public void removeVisibleAreaListener(VisibleAreaListener listener) {
    myVisibleAreaMulticaster.removeListener(listener);
  }

  public void addEditReadOnlyListener(EditReadOnlyListener listener) {
    myEditReadOnlyMulticaster.addListener(listener);
  }

  public void removeEditReadOnlyListener(EditReadOnlyListener listener) {
    myEditReadOnlyMulticaster.removeListener(listener);
  }

  public void addPropertyChangeListener(PropertyChangeListener listener) {
    myPropertyChangeMulticaster.addListener(listener);
  }

  public void removePropertyChangeListener(PropertyChangeListener listener) {
    myPropertyChangeMulticaster.removeListener(listener);
  }

  public void addFocusChangeListner(FocusChangeListener listener) {
    myFocusChangeListenerMulticaster.addListener(listener);
  }

  public void removeFocusChangeListner(FocusChangeListener listener) {
    myFocusChangeListenerMulticaster.removeListener(listener);
  }

  @TestOnly
  public Map<Class, List> getListeners() {
    Map<Class, List> myCopy = new LinkedHashMap<Class, List>();
    myCopy.put(DocumentListener.class, new ArrayList(myDocumentMulticaster.getListeners()));
    myCopy.put(EditReadOnlyListener.class, new ArrayList(myEditReadOnlyMulticaster.getListeners()));

    myCopy.put(EditorMouseListener.class, new ArrayList(myEditorMouseMulticaster.getListeners()));
    myCopy.put(EditorMouseMotionListener.class, new ArrayList(myEditorMouseMotionMulticaster.getListeners()));
    myCopy.put(ErrorStripeListener.class, new ArrayList(myErrorStripeMulticaster.getListeners()));
    myCopy.put(CaretListener.class, new ArrayList(myCaretMulticaster.getListeners()));
    myCopy.put(SelectionListener.class, new ArrayList(mySelectionMulticaster.getListeners()));
    myCopy.put(VisibleAreaListener.class, new ArrayList(myVisibleAreaMulticaster.getListeners()));
    myCopy.put(PropertyChangeListener.class, new ArrayList(myPropertyChangeMulticaster.getListeners()));
    myCopy.put(FocusChangeListener.class, new ArrayList(myFocusChangeListenerMulticaster.getListeners()));
    return myCopy;
  }
}
