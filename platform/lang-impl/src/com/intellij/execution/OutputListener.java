package com.intellij.execution;

import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.Key;
import org.apache.commons.lang.text.StrBuilder;
import org.jetbrains.annotations.NotNull;

/**
* @author oleg
*/
public class OutputListener extends ProcessAdapter {
  private final StringBuilder out;
  private final StringBuilder err;

  public OutputListener() {
    out = new StringBuilder();
    err = new StringBuilder();
  }

  public OutputListener(@NotNull final StringBuilder out, @NotNull final StringBuilder err) {
    this.out = out;
    this.err = err;
  }

  public void onTextAvailable(ProcessEvent event, Key outputType) {
    if (outputType == ProcessOutputTypes.STDOUT) {
      out.append(event.getText());
    }
    if (outputType == ProcessOutputTypes.STDERR) {
      err.append(event.getText());
    }
  }

  public Output getOutput() {
    return new Output(out.toString(), err.toString());
  }
}
