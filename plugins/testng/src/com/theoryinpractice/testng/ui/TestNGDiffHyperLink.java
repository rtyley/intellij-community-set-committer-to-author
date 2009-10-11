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
package com.theoryinpractice.testng.ui;

import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.testframework.Printer;
import com.intellij.execution.testframework.stacktrace.DiffHyperlink;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.theoryinpractice.testng.model.TestNGConsoleProperties;

/**
 * @author Hani Suleiman Date: Dec 1, 2006 Time: 12:14:04 PM
 */
public class TestNGDiffHyperLink extends DiffHyperlink implements Printable {

  public TestNGDiffHyperLink(final String expected, final String actual, final String filePath, final TestNGConsoleProperties consoleProperties) {
    super(expected, actual, filePath);
  }

  public void print(final ConsoleView printer) {
    printOn(new Printer() {
      public void print(final String text, final ConsoleViewContentType contentType) {
        printer.print(text, contentType);
      }

      public void printHyperlink(final String text, final HyperlinkInfo info) {
        printer.printHyperlink(text, info);
      }

      public void onNewAvailable(final com.intellij.execution.testframework.Printable printable) {}
      public void mark() {}
    });
  }
}