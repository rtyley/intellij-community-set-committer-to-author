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
package com.intellij.vcsUtil;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.VcsBundle;

public class VcsSelection {
  private final Document myDocument;
  private final int mySelectionStartLineNumber;
  private final int mySelectionEndLineNumber;
  private final String myActionName;
  private final String myDialogTitle;

  public VcsSelection(Document document, SelectionModel selectionModel) {
    this(document, new TextRange(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd()),
         VcsBundle.message("action.name.show.history.for.selection"));
  }

  public VcsSelection(Document document, TextRange textRange, String actionName) {
    myDocument = document;
    int startOffset = textRange.getStartOffset();
    mySelectionStartLineNumber = document.getLineNumber(startOffset);
    int endOffset = textRange.getEndOffset();
    mySelectionEndLineNumber = endOffset >= document.getTextLength() ? document.getLineCount() - 1 : document.getLineNumber(endOffset);
    myActionName = VcsBundle.message("show.history.action.name.template", actionName);
    myDialogTitle = VcsBundle.message("show.history.dialog.title.template", actionName);
  }

  public Document getDocument() {
    return myDocument;
  }

  public int getSelectionStartLineNumber() {
    return mySelectionStartLineNumber;
  }

  public int getSelectionEndLineNumber() {
    return mySelectionEndLineNumber;
  }

  public String getActionName() {
    return myActionName;
  }

  public String getDialogTitle() {
    return myDialogTitle;
  }
}