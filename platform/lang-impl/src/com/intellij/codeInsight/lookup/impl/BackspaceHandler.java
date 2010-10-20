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

package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.completion.CompletionProcess;
import com.intellij.codeInsight.completion.CompletionProgressIndicator;
import com.intellij.codeInsight.completion.CompletionService;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;

public class BackspaceHandler extends EditorActionHandler {
  private final EditorActionHandler myOriginalHandler;

  public BackspaceHandler(EditorActionHandler originalHandler){
    myOriginalHandler = originalHandler;
  }

  public void execute(final Editor editor, final DataContext dataContext){
    LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(editor);
    if (lookup == null){
      myOriginalHandler.execute(editor, dataContext);
      return;
    }

    boolean toRestart = false;
    final String prefix = lookup.getAdditionalPrefix();
    if (prefix.length() > 0) {
      lookup.setAdditionalPrefix(prefix.substring(0, prefix.length() - 1));
    }
    else {
      toRestart = lookup.getLookupStart() < editor.getCaretModel().getOffset();
    }

    lookup.performGuardedChange(new Runnable() {
      @Override
      public void run() {
        myOriginalHandler.execute(editor, dataContext);
      }
    });

    if (prefix.length() > 0) {
      return;
    }

    if (toRestart) {
      final CompletionProcess process = CompletionService.getCompletionService().getCurrentCompletion();
      if (process instanceof CompletionProgressIndicator) {
        ((CompletionProgressIndicator)process).restartCompletion();
        return;
      }
    }

    lookup.hide();
  }
}
