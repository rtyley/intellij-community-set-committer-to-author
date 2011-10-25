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

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.diff.Diff;
import com.intellij.util.diff.FilesTooBigForDiffException;
import org.jetbrains.annotations.NotNull;

public class DocumentEventImpl extends DocumentEvent {
  private final int myOffset;
  private final CharSequence myOldString;
  private final int myOldLength;
  private final CharSequence myNewString;
  private final int myNewLength;

  private boolean isOnlyOneLineChangedCalculated = false;
  private boolean isOnlyOneLineChanged;

  private boolean isStartOldIndexCalculated = false;
  private int myStartOldIndex;

  private final long myOldTimeStamp;
  private final boolean myIsWholeDocReplaced;
  private Diff.Change myChange;

  private int myOptimizedLineShift = -1;
  private boolean myOptimizedLineShiftCalculated;

  private int myOptimizedOldLineShift = -1;
  private boolean myOptimizedOldLineShiftCalculated;

  public DocumentEventImpl(Document document, int offset, CharSequence oldString, CharSequence newString, long oldTimeStamp,
                           boolean wholeTextReplaced) {
    super(document);
    myOffset = offset;

    myOldString = oldString == null ? "" : oldString;
    myOldLength = myOldString.length();

    myNewString = newString == null ? "" : newString;
    myNewLength = myNewString.length();

    myOldTimeStamp = oldTimeStamp;

    if (getDocument().getTextLength() == 0) {
      isOnlyOneLineChangedCalculated = true;
      isOnlyOneLineChanged = false;
      myIsWholeDocReplaced = false;
    }
    else {
      myIsWholeDocReplaced = wholeTextReplaced;
    }
  }

  @Override
  public int getOffset() {
    return myOffset;
  }

  @Override
  public int getOldLength() {
    return myOldLength;
  }

  @Override
  public int getNewLength() {
    return myNewLength;
  }

  @Override
  public CharSequence getOldFragment() {
    return myOldString;
  }

  @Override
  public CharSequence getNewFragment() {
    return myNewString;
  }

  @Override
  @NotNull
  public Document getDocument() {
    return (Document) getSource();
  }

  public int getStartOldIndex() {
    if(isStartOldIndexCalculated) return myStartOldIndex;

    isStartOldIndexCalculated = true;
    myStartOldIndex = getDocument().getLineNumber(myOffset);
    return myStartOldIndex;
  }

  public boolean isOnlyOneLineChanged() {
    if(isOnlyOneLineChangedCalculated) return isOnlyOneLineChanged;

    isOnlyOneLineChangedCalculated = true;
    isOnlyOneLineChanged = true;

    for(int i=0; i<myOldString.length(); i++) {
      if(myOldString.charAt(i) == '\n') {
        isOnlyOneLineChanged = false;
        break;
      }
    }

    if(isOnlyOneLineChanged) {
      for(int i=0; i<myNewString.length(); i++) {
        if(myNewString.charAt(i) == '\n') {
          isOnlyOneLineChanged = false;
          break;
        }
      }
    }
    return isOnlyOneLineChanged;
  }

  @Override
  public long getOldTimeStamp() {
    return myOldTimeStamp;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "DocumentEventImpl[myOffset=" + myOffset + ", myOldLength=" + myOldLength + ", myNewLength=" + myNewLength +
           ", myOldString='" + myOldString + "', myNewString='" + myNewString + "']" + (isWholeTextReplaced() ? " Whole." : ".");
  }

  @Override
  public boolean isWholeTextReplaced() {
    return myIsWholeDocReplaced;
  }

  public int translateLineViaDiff(int line) throws FilesTooBigForDiffException {
    if (myChange == null) buildDiff();
    if (myChange == null) return line;

    Diff.Change change = myChange;

    int newLine = line;

    while (change != null) {
      if (line < change.line0) break;
      if (line >= change.line0 + change.deleted) {
        newLine += change.inserted - change.deleted;
      } else {
        int delta = Math.min(change.inserted, line - change.line0);
        newLine = change.line1 + delta;
        break;
      }

      change = change.link;
    }

    return newLine;
  }

  public int translateLineViaDiffStrict(int line) throws FilesTooBigForDiffException {
    if (myChange == null) buildDiff();
    Diff.Change change = myChange;
    if (change == null) return line;
    return Diff.translateLine(change, line);
  }

  private void buildDiff() throws FilesTooBigForDiffException {
    //Diff diff = new Diff(strings1, strings2);
    //myChange = diff.diff_2(false);
    myChange = Diff.buildChanges(myOldString, myNewString);
  }

  public int getOptimizedLineShift() {
    if (!myOptimizedLineShiftCalculated) {
      myOptimizedLineShiftCalculated = true;

      if (myOldLength == 0) {
        int lineShift = StringUtil.countNewLines(myNewString);

        myOptimizedLineShift = lineShift == 0 ? -1 : lineShift;
      }
    }
    return myOptimizedLineShift;
  }

  public int getOptimizedOldLineShift() {
    if (!myOptimizedOldLineShiftCalculated) {
      myOptimizedOldLineShiftCalculated = true;

      if (myNewLength == 0) {
        int lineShift = StringUtil.countNewLines(myOldString);

        myOptimizedOldLineShift = lineShift == 0 ? -1 : lineShift;
      }
    }
    return myOptimizedOldLineShift;
  }
}
