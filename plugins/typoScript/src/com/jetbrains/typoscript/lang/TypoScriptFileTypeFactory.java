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
package com.jetbrains.typoscript.lang;

import com.intellij.openapi.fileTypes.*;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;


public class TypoScriptFileTypeFactory extends FileTypeFactory {

  public static final List<? extends FileNameMatcher> FILE_NAME_MATCHERS =
    Collections.unmodifiableList(Arrays.asList(new ExtensionFileNameMatcher(TypoScriptFileType.DEFAULT_EXTENSION),
                                               new ExactFileNameMatcher("setup.txt"),
                                               new ExactFileNameMatcher("constants.txt"),
                                               new ExactFileNameMatcher("ext_conf_template.txt"),
                                               new ExactFileNameMatcher("ext_typoscript_setup.txt"),
                                               new ExactFileNameMatcher("ext_typoscript_constants.txt")));

  @Override
  public void createFileTypes(@NotNull FileTypeConsumer consumer) {
    consumer.consume(TypoScriptFileType.INSTANCE, FILE_NAME_MATCHERS.toArray(new FileNameMatcher[FILE_NAME_MATCHERS.size()]));
  }
}