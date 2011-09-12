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
package com.intellij.openapi.fileTypes.impl;

import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.openapi.fileTypes.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PlatformFileTypeFactory extends FileTypeFactory {
  public void createFileTypes(final @NotNull FileTypeConsumer consumer) {
    consumer.consume(new ArchiveFileType(), "zip;jar;war;ear;swc;egg;apk");
    consumer.consume(PlainTextFileType.INSTANCE,
                     new ExtensionFileNameMatcher("txt"),
                     new ExtensionFileNameMatcher("sh"),
                     new ExtensionFileNameMatcher("bat"),
                     new ExtensionFileNameMatcher("cmd"),
                     new ExtensionFileNameMatcher("policy"),
                     new ExtensionFileNameMatcher("log"),
                     new ExtensionFileNameMatcher("cgi"),
                     new ExtensionFileNameMatcher("MF"),
                     new ExtensionFileNameMatcher("jad"),
                     new ExtensionFileNameMatcher("jam"),
                     new ExtensionFileNameMatcher("htaccess"),
                     new ExactFileNameMatcher("README", true),
                     new ExactFileNameMatcher("LICENSE", true));
    consumer.consume(NativeFileType.INSTANCE, "doc;xls;ppt;mdb;vsd;pdf;hlp;chm;odt");
    consumer.consume(UnknownFileType.INSTANCE);
  }
}
