/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl.softwrap;

import com.intellij.openapi.editor.ex.PrioritizedDocumentListener;

/**
 * //TODO den remove
 * Common place to store soft wrap-related constants.
 *
 * @author Denis Zhdanov
 * @since Aug 23, 2010 6:16:23 PM
 */
public class SoftWrapConstants {

  /** {@link PrioritizedDocumentListener#getPriority() document listener's priority} to use with {@link SoftWrapDocumentChangeManager} */
  public static final int DOCUMENT_CHANGE_LISTENER_PRIORITY = 5;

  /** {@link PrioritizedDocumentListener#getPriority() document listener's priority} to use with {@link SoftWrapApplianceManagerAAA} */
  public static final int SOFT_WRAP_APPLIANCE_LISTENER_PRIORITY = 4;

  private SoftWrapConstants() {
  }
}
