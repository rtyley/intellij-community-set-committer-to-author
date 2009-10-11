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
package org.jetbrains.idea.svn;

import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public enum ConflictState {
  none(false, false, false, null),
  tree(true, false, false, "/icons/conflictc.png"),
  text(false, true, false, "/icons/conflictt.png"),
  prop(false, false, true, "/icons/conflictp.png"),
  tree_text(true, true, false, "/icons/conflictct.png"), // ? -
  tree_prop(true, false, true, "/icons/conflictcp.png"), // now falls but marked
  text_prop(false, true, true, "/icons/conflicttp.png"),
  all3(true, true, true, "/icons/conflictctp.png");       // ? -

  private final boolean myTree;
  private final boolean myText;
  private final boolean myProperty;
  @Nullable
  private final Icon myIcon;
  private final String myDescription;

  private ConflictState(final boolean tree, final boolean text, final boolean property, final String iconPath) {
    myTree = tree;
    myText = text;
    myProperty = property;

    if (iconPath != null) {
      myIcon = IconLoader.getIcon(iconPath);
    } else {
      myIcon = null;
    }

    myDescription = createDescription();
  }

  @Nullable
  private String createDescription() {
    int cnt = 0;
    final StringBuilder sb = new StringBuilder();
    cnt = checkOne(myTree, cnt, sb, "tree");
    cnt = checkOne(myText, cnt, sb, "text");
    cnt = checkOne(myProperty, cnt, sb, "property");
    if (cnt == 0) {
      return null;
    }
    return sb.toString();
  }

  private int checkOne(final boolean value, final int init, final StringBuilder sb, final String text) {
    if (value) {
      if (sb.length() > 0) {
        sb.append(", ");
      }
      sb.append(text);
      return init + 1;
    }
    return init;
  }

  public boolean isTree() {
    return myTree;
  }

  public boolean isText() {
    return myText;
  }

  public boolean isProperty() {
    return myProperty;
  }

  public boolean isConflict() {
    return myProperty || myText || myTree;
  }

  @Nullable
  public Icon getIcon() {
    return myIcon;
  }

  public String getDescription() {
    return myDescription;
  }

  public static ConflictState mergeState(final ConflictState leftState, final ConflictState rightState) {
    return getInstance(leftState.myTree | rightState.myTree, leftState.myText | rightState.myText,
                             leftState.myProperty | rightState.myProperty);
  }

  public static ConflictState getInstance(final boolean tree, final boolean text, final boolean property) {
    final ConflictState[] conflictStates = values();
    for (ConflictState state : conflictStates) {
      if ((state.isTree() == tree) && (state.isText() == text) && (state.isProperty() == property)) {
        return state;
      }
    }
    // all combinations are defined
    assert false;
    return null;
  }
}
