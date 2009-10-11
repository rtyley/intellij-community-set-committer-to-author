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

package com.intellij.openapi.fileTypes;

import com.intellij.lang.Language;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Mar 15, 2006
 * Time: 7:42:03 PM
 * To change this template use File | Settings | File Templates.
 */
public class PlainTextLanguage extends Language {

  public static final PlainTextLanguage INSTANCE = new PlainTextLanguage();

  private PlainTextLanguage() {
    super("TEXT", "text/plain");
  }

  public String getDisplayName() {
    return "Plain text";
  }
}
