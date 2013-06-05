/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.externalSystem.util;

import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 4/16/13 11:44 AM
 */
public class ExternalSystemConstants {

  @NonNls @NotNull public static final String NEWLY_IMPORTED_PROJECT  = "external.system.newly.imported";
  @NonNls @NotNull public static final String EXTERNAL_SYSTEM_ID_KEY  = "external.system.id";
  @NonNls @NotNull public static final String LINKED_PROJECT_PATH_KEY = "external.linked.project.path";

  @NonNls @NotNull public static final String TOOL_WINDOW_TOOLBAR_ACTIONS_GROUP_ID = "ExternalSystem.ToolWindow.Toolbar";
  @NonNls @NotNull public static final String TREE_ACTIONS_GROUP_ID                = "ExternalSystem.Tree.Context";

  @NonNls @NotNull public static final String TOOL_WINDOW_PLACE = "ExternalSystem.ToolWindow";
  @NonNls @NotNull public static final String TREE_PLACE        = "ExternalSystem.Tree";

  public static final boolean VERBOSE_PROCESSING = SystemProperties.getBooleanProperty("external.system.verbose.processing", false);
  public static final char    PATH_SEPARATOR     = '/';

  // Order.
  public static final int BUILTIN_SERVICE_ORDER             = 42;
  public static final int BUILTIN_TOOL_WINDOW_SERVICE_ORDER = 62;
  public static final int UNORDERED                         = 1000;

  public static final int TEXT_FIELD_WIDTH_IN_COLUMNS = 20;
}
