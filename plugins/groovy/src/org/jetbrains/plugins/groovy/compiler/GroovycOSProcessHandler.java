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

package org.jetbrains.plugins.groovy.compiler;

import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.TranslatingCompiler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.groovy.compiler.rt.CompilerMessage;
import org.jetbrains.groovy.compiler.rt.GroovycRunner;

import java.io.File;
import java.util.*;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 16.04.2007
 */
public class GroovycOSProcessHandler extends OSProcessHandler {
  private final List<TranslatingCompiler.OutputItem> myCompiledItems = new ArrayList<TranslatingCompiler.OutputItem>();
  private final Set<File> toRecompileFiles = new HashSet<File>();
  private final List<CompilerMessage> compilerMessages = new ArrayList<CompilerMessage>();
  private final StringBuffer unparsedOutput = new StringBuffer();
  private final CompileContext myContext;

  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.compiler.GroovycOSProcessHandler");
  public static final String GROOVY_COMPILER_IN_OPERATION = "Groovy compiler in operation...";

  public GroovycOSProcessHandler(CompileContext context, Process process, String s) {
    super(process, s);
    myContext = context;
  }

  public void notifyTextAvailable(final String text, final Key outputType) {
    super.notifyTextAvailable(text, outputType);
//    System.out.println("text: " + text);

    parseOutput(text, outputType == ProcessOutputTypes.STDERR);
  }

  private final StringBuffer outputBuffer = new StringBuffer();

  private void parseOutput(String text, boolean error) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Received from groovyc: " + text);
    }

    final String trimmed = text.trim();


    if (trimmed.startsWith(GroovycRunner.PRESENTABLE_MESSAGE)) {
      myContext.getProgressIndicator().setText(trimmed.substring(GroovycRunner.PRESENTABLE_MESSAGE.length()));
      return;
    }

    if (GroovycRunner.CLEAR_PRESENTABLE.equals(trimmed)) {
      myContext.getProgressIndicator().setText(GROOVY_COMPILER_IN_OPERATION);
      return;
    }


    if (StringUtil.isNotEmpty(text)) {
      outputBuffer.append(text);

      //compiled start marker have to be in the beginning on each string
      if (outputBuffer.indexOf(GroovycRunner.COMPILED_START) != -1) {
        unparsedOutput.setLength(0);

        if (!(outputBuffer.indexOf(GroovycRunner.COMPILED_END) != -1)) {
          return;
        }

        {
          text = handleOutputBuffer(GroovycRunner.COMPILED_START, GroovycRunner.COMPILED_END);

          StringTokenizer tokenizer = new StringTokenizer(text, GroovycRunner.SEPARATOR, false);

          String token;
          /*
          * output path
          * source file
          * output root directory
          */

          String outputPath = "";
          String sourceFile = "";
          String outputRootDirectory = "";

          if (tokenizer.hasMoreTokens()) {
            token = tokenizer.nextToken();
            outputPath = token;
          }

          if (tokenizer.hasMoreTokens()) {
            token = tokenizer.nextToken();
            sourceFile = token;
          }

          LocalFileSystem.getInstance().refreshAndFindFileByPath(outputPath);
          final TranslatingCompiler.OutputItem item = getOutputItem(outputPath, sourceFile);
          if (item != null) {
            myCompiledItems.add(item);
          }
        }

      }
      else if (outputBuffer.indexOf(GroovycRunner.TO_RECOMPILE_START) != -1) {
        unparsedOutput.setLength(0);
        if (!(outputBuffer.indexOf(GroovycRunner.TO_RECOMPILE_END) != -1)) {
          return;
        }

        if (outputBuffer.indexOf(GroovycRunner.TO_RECOMPILE_END) != -1) {
          text = handleOutputBuffer(GroovycRunner.TO_RECOMPILE_START, GroovycRunner.TO_RECOMPILE_END);

          myContext.getProgressIndicator().setText(text);
          toRecompileFiles.add(new File(text));
        }
      }

      /* Cathegory
      * Message
      * Url
      * Linenum
      * Colomnnum
      */

      else if (outputBuffer.indexOf(GroovycRunner.MESSAGES_START) != -1) {
        unparsedOutput.setLength(0);
        if (!(outputBuffer.indexOf(GroovycRunner.MESSAGES_END) != -1)) {
          return;
        }

        text = handleOutputBuffer(GroovycRunner.MESSAGES_START, GroovycRunner.MESSAGES_END);

        String category;
        String message;
        String url;
        String linenum;
        String colomnnum;

        List<String> tokens = StringUtil.split(text, GroovycRunner.SEPARATOR);
        LOG.assertTrue(tokens.size() > 4, "Wrong number of output params");

        category = tokens.get(0);
        message = tokens.get(1);
        url = tokens.get(2);
        linenum = tokens.get(3);
        colomnnum = tokens.get(4);

        int linenumInt;
        int colomnnumInt;

        try {
          linenumInt = Integer.parseInt(linenum);
          colomnnumInt = Integer.parseInt(colomnnum);
        }
        catch (NumberFormatException e) {
          LOG.error(e);
          linenumInt = 0;
          colomnnumInt = 0;
        }

        myContext.getProgressIndicator().setText(url);

        compilerMessages.add(new CompilerMessage(category, message, url, linenumInt, colomnnumInt));
      }
      else {
        if (error) unparsedOutput.append(outputBuffer).append("\n");
        outputBuffer.setLength(0);
      }
    }
  }

  private String handleOutputBuffer(String START_MARKER, String END_MARKER) {
    String text;
    text = outputBuffer.substring(
        outputBuffer.indexOf(START_MARKER) + START_MARKER.length(),
        outputBuffer.indexOf(END_MARKER));

    outputBuffer.delete(
        outputBuffer.indexOf(START_MARKER),
        outputBuffer.indexOf(END_MARKER) + END_MARKER.length());

    return text.trim();
  }

  @Nullable
  private TranslatingCompiler.OutputItem getOutputItem(final String outputPath, final String sourceFile) {

    final VirtualFile sourceVirtualFile = LocalFileSystem.getInstance().findFileByIoFile(new File(sourceFile));
    if (sourceVirtualFile == null) return null; //the source might already have been deleted

    return new TranslatingCompiler.OutputItem() {
      public String getOutputPath() {
        return outputPath;
      }

      public VirtualFile getSourceFile() {
        return sourceVirtualFile;
      }
    };
  }

  public List<TranslatingCompiler.OutputItem> getSuccessfullyCompiled() {
    return myCompiledItems;
  }

  public Set<File> getToRecompileFiles() {
    return toRecompileFiles;
  }

  public List<CompilerMessage> getCompilerMessages() {
    return compilerMessages;
  }

  public StringBuffer getUnparsedOutput() {
    return unparsedOutput;
  }
}
