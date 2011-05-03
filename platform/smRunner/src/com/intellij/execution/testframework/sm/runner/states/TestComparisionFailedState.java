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
package com.intellij.execution.testframework.sm.runner.states;

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.CompositePrintable;
import com.intellij.execution.testframework.Printer;
import com.intellij.execution.testframework.stacktrace.DiffHyperlink;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Roman.Chernyatchik
 */
public class TestComparisionFailedState extends TestFailedState implements AbstractTestProxy.AssertEqualsDiffViewerProvider {
  private final String myErrorMsgPresentation;
  private final String myStacktracePresentation;
  private DiffHyperlink myHyperlink;


  public TestComparisionFailedState(@Nullable final String localizedMessage,
                                    @Nullable final String stackTrace,
                                    @NotNull final String actualText,
                                    @NotNull final String expectedText) {
    super(localizedMessage, stackTrace);
    myHyperlink = new DiffHyperlink(expectedText, actualText, null);

    myErrorMsgPresentation = StringUtil.isEmptyOrSpaces(localizedMessage) ? ""
                                                                          : localizedMessage;
    myStacktracePresentation = StringUtil.isEmptyOrSpaces(stackTrace) ? ""
                                                                      : stackTrace;
  }

  @Override
  public void printOn(Printer printer) {
    printer.print(CompositePrintable.NEW_LINE, ConsoleViewContentType.ERROR_OUTPUT);
    printer.mark();

    // Error msg
    if (myErrorMsgPresentation != null) {
      printer.print(myErrorMsgPresentation, ConsoleViewContentType.ERROR_OUTPUT);
    }

    // Diff link
    myHyperlink.printOn(printer);

    // Stacktrace
    if (myStacktracePresentation != null) {
      printer.print(CompositePrintable.NEW_LINE, ConsoleViewContentType.ERROR_OUTPUT);

      printer.print(myStacktracePresentation, ConsoleViewContentType.ERROR_OUTPUT);
      printer.print(CompositePrintable.NEW_LINE, ConsoleViewContentType.ERROR_OUTPUT);
    }
  }

  public void openDiff(final Project project) {
    myHyperlink.openDiff(project);
  }

  @Override
  public String getExpected() {
    return myHyperlink.getLeft();
  }

  @Override
  public String getActual() {
    return myHyperlink.getRight();
  }
}
