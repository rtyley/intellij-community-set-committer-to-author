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

package com.intellij.execution.testframework;

import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.ui.ConsoleViewContentType;

public class DeferingPrinter extends CompositePrintable implements Printer.Intermediate {
  private final boolean myCollectOutput;

  public DeferingPrinter(final boolean collectOutput) {
    myCollectOutput = collectOutput;
  }

  public void print(final String text, final ConsoleViewContentType contentType) {
    addLast(new ExternalOutput(text, contentType));
  }

  public void onNewAvailable(final Printable printable) {
    addLast(printable);
  }

  public void printHyperlink(final String text, final HyperlinkInfo info) {
    addLast(new HyperLink(text, info));
  }

  public void mark() {
    addLast(new PrinterMark());
  }

  public void printOn(final Printer printer) {
    super.printOn(printer);
    if (!myCollectOutput)
      clear();
  }
}
