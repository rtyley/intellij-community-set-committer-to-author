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
package com.intellij.openapi.progress.util;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Apr 19, 2004
 * Time: 5:23:48 PM
 * To change this template use File | Settings | File Templates.
 */
public class ProgressIndicatorListenerAdapter implements ProgressIndicatorListener{
  //should return whether to stop processing
  public void cancelled() {
  }

  //should return whether to stop processing
  public void stopped() {
  }
}
