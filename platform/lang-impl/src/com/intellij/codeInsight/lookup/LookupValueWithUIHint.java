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

package com.intellij.codeInsight.lookup;

import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 10.12.2004
 * Time: 13:25:56
 * To change this template use File | Settings | File Templates.
 * @deprecated use InsertHandler
 */
public interface LookupValueWithUIHint extends PresentableLookupValue {
  String getTypeHint();
  Color getColorHint();
  boolean isBold();
}
