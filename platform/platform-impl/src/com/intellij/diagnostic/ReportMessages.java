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

/*
 * @author: Eugene Zhuravlev
 * Date: Mar 18, 2003
 * Time: 1:25:33 PM
 */
package com.intellij.diagnostic;

import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;

public class ReportMessages {

  public static final String ERROR_REPORT = DiagnosticBundle.message("error.report.title");
  public static final NotificationGroup GROUP = new NotificationGroup(ERROR_REPORT, NotificationDisplayType.BALLOON, false);
}
