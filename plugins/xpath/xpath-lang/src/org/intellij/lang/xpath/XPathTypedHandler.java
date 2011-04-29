/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.intellij.lang.xpath;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 28.04.11
*/
public class XPathTypedHandler extends TypedHandlerDelegate {
  @Override
  public Result checkAutoPopup(char charTyped, Project project, Editor editor, PsiFile file) {
    if (charTyped == '$') {
      if (!(file instanceof XPathFile)) return Result.CONTINUE;

      AutoPopupController.getInstance(editor.getProject()).autoPopupMemberLookup(editor, null);
      return Result.CONTINUE;
    } else {
      return super.checkAutoPopup(charTyped, project, editor, file);
    }
  }
}