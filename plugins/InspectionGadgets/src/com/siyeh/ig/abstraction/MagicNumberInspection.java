/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.abstraction;

import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.IntroduceConstantFix;

public class MagicNumberInspection extends MagicNumberInspectionBase {

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new IntroduceConstantFix();
  }
}
