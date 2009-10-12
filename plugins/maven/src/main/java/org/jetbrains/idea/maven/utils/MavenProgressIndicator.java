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
package org.jetbrains.idea.maven.utils;

import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.util.containers.ContainerUtil;

import java.util.List;

public class MavenProgressIndicator {
  private final List<ProgressIndicator> myIndicators = ContainerUtil.createEmptyCOWList();
  private String myText;
  private String myText2;
  private double myFraction;
  private boolean myCanceled;

  public MavenProgressIndicator() {
  }

  public MavenProgressIndicator(ProgressIndicator i) {
    myText = i.getText();
    myText2 = i.getText2();
    myFraction = i.getFraction();
    myCanceled = i.isCanceled();
    addIndicator(i);
  }

  public void addIndicator(ProgressIndicator i) {
    synchronized (this) {
      i.setText(myText);
      i.setText2(myText2);
      i.setFraction(myFraction);
      if (myCanceled) i.cancel();
    }
    myIndicators.add(i);
  }

  public ProgressIndicator getIndicator() {
    return myIndicators.isEmpty() ? new EmptyProgressIndicator() : myIndicators.get(0);
  }

  public void setText(String text) {
    synchronized (this) {
      myText = text;
    }
    for (ProgressIndicator each : myIndicators) {
      each.setText(text);
    }
  }

  public void setText2(String text) {
    synchronized (this) {
      myText2 = text;
    }
    for (ProgressIndicator each : myIndicators) {
      each.setText2(text);
    }
  }

  public void setFraction(double fraction) {
    synchronized (this) {
      myFraction = fraction;
    }
    for (ProgressIndicator each : myIndicators) {
      each.setFraction(fraction);
    }
  }

  public void cancel() {
    synchronized (this) {
      myCanceled = true;
    }
    for (ProgressIndicator each : myIndicators) {
      each.cancel();
    }
  }

  public boolean isCanceled() {
    for (ProgressIndicator each : myIndicators) {
      if (each.isCanceled()) {
        synchronized (this) {
          myCanceled = true;
        }
        break;
      }
    }
    return myCanceled;
  }

  public void checkCanceled() throws MavenProcessCanceledException {
    if (isCanceled()) throw new MavenProcessCanceledException();
  }

  public void checkCanceledNative() {
    if (isCanceled()) throw new ProcessCanceledException();
  }
}
