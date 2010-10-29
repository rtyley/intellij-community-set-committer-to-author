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
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.annotate.TextAnnotationPresentation;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;

import java.awt.*;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
class HistoryIdColumn extends AnnotationFieldGutter {
  private final Map<String, Integer> myHistoryIds;

  HistoryIdColumn(FileAnnotation annotation,
                  final Editor editor,
                  final TextAnnotationPresentation presentation,
                  Map<String, Color> colorScheme,
                  Map<String, Integer> ids) {
    super(annotation, editor, null, presentation, colorScheme);
    myHistoryIds = ids;
  }

  @Override
  public String getLineText(int line, Editor editor) {
    final VcsRevisionNumber revisionNumber = myAnnotation.getLineRevisionNumber(line);
    if (revisionNumber != null) {
      final Integer num = myHistoryIds.get(revisionNumber.asString());
      if (num != null) {
        final String size = String.valueOf(myHistoryIds.size());
        String value = num.toString();
        while (value.length() < size.length()) {
          value = " " + value;
        }
        return value;
      }
    }
    return "";
  }

  @Override
  public String getID() {
    return VcsBundle.message("annotation.commit.number");
  }
}
