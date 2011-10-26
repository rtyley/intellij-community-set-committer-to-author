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

/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Aug 22, 2007
 * Time: 9:09:57 PM
 */
package com.intellij.injected.editor;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

public class RangeMarkerWindow implements RangeMarkerEx {
  private final DocumentWindow myDocumentWindow;
  private final RangeMarkerEx myHostMarker;

  public RangeMarkerWindow(@NotNull DocumentWindow documentWindow, RangeMarkerEx hostMarker) {
    myDocumentWindow = documentWindow;
    myHostMarker = hostMarker;
  }

  @Override
  @NotNull
  public Document getDocument() {
    return myDocumentWindow;
  }

  @Override
  public int getStartOffset() {
    int hostOffset = myHostMarker.getStartOffset();
    return myDocumentWindow.hostToInjected(hostOffset);
  }

  @Override
  public int getEndOffset() {
    int hostOffset = myHostMarker.getEndOffset();
    return myDocumentWindow.hostToInjected(hostOffset);
  }

  @Override
  public boolean isValid() {
    return myHostMarker.isValid() && myDocumentWindow.isValid();
  }

  @Override
  public boolean setValid(boolean value) {
    return myHostMarker.setValid(value);
  }

  @Override
  public void trackInvalidation(boolean track) {
    myHostMarker.trackInvalidation(track);
  }

  @Override
  public boolean isTrackInvalidation() {
    return myHostMarker.isTrackInvalidation();
  }

  ////////////////////////////delegates
  @Override
  public void setGreedyToLeft(final boolean greedy) {
    myHostMarker.setGreedyToLeft(greedy);
  }

  @Override
  public void setGreedyToRight(final boolean greedy) {
    myHostMarker.setGreedyToRight(greedy);
  }

  @Override
  public <T> T getUserData(@NotNull final Key<T> key) {
    return myHostMarker.getUserData(key);
  }

  @Override
  public <T> void putUserData(@NotNull final Key<T> key, final T value) {
    myHostMarker.putUserData(key, value);
  }

  @Override
  public void documentChanged(final DocumentEvent e) {
    myHostMarker.documentChanged(e);
  }
  @Override
  public long getId() {
    return myHostMarker.getId();
  }

  public RangeMarkerEx getDelegate() {
    return myHostMarker;
  }

  @Override
  public boolean isGreedyToRight() {
    return myHostMarker.isGreedyToRight();
  }

  @Override
  public boolean isGreedyToLeft() {
    return myHostMarker.isGreedyToLeft();
  }

  @Override
  public int intervalStart() {
    return getStartOffset();
  }

  @Override
  public int intervalEnd() {
    return getEndOffset();
  }

  @Override
  public int setIntervalStart(int start) {
    throw new IllegalStateException();
  }

  @Override
  public int setIntervalEnd(int end) {
    throw new IllegalStateException();
  }

  @Override
  public void dispose() {
    myHostMarker.dispose();
  }
}
