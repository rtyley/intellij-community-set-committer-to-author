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
package com.intellij.openapi.diff.impl.dir;

import com.intellij.openapi.util.IconLoader;
import com.intellij.util.Icons;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public interface DirDiffIcons {
  Icon FOLDER = Icons.FOLDER_ICON;
  Icon MOVE_RIGHT = IconLoader.getIcon("/vcs/arrow_right.png");
  Icon MOVE_LEFT = IconLoader.getIcon("/vcs/arrow_left.png");
  Icon EQUAL = IconLoader.getIcon("/vcs/equal.png");
  Icon NOT_EQUAL = IconLoader.getIcon("/vcs/not_equal.png");
}
