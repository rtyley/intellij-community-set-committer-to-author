/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.android.designer.inspection;

import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.inspections.lint.AndroidLintExternalAnnotator;
import org.jetbrains.android.inspections.lint.ProblemData;
import org.jetbrains.android.inspections.lint.State;

/**
 * @author Alexander Lobas
 */
public class ErrorAnalyzer {
  public static void load(XmlFile xmlFile, RadComponent rootComponent, ProgressIndicator progress) {
    AndroidLintExternalAnnotator annotator = new AndroidLintExternalAnnotator();
    State state = annotator.collectionInformation(xmlFile);
    if (state == null) {
      System.out.println("==== No inspections(" + rootComponent + ") ====");
    }
    else {
      state = annotator.doAnnotate(state);
      System.out.println("==== Problems(" + rootComponent + ") ====");
      for (ProblemData problem : state.getProblems()) {
        System.out.println(problem.getIssue() + " | " + problem.getMessage() + " | " + problem.getTextRange());
      }
    }
  }
}