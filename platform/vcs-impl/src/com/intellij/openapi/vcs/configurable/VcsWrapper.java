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
package com.intellij.openapi.vcs.configurable;

import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.project.Project;

class VcsWrapper {
  private final AbstractVcs myOriginal;

  public VcsWrapper(final AbstractVcs original) {
    myOriginal = original;
  }

  public AbstractVcs getOriginal() {
    return myOriginal;
  }

  public String toString() {
    return getDisplayName(myOriginal);
  }

  protected String getDisplayName(final AbstractVcs vcs) {
    if (vcs == null) {
      return VcsBundle.message("none.vcs.presentation");
    }
    else {
      return vcs.getDisplayName();
    }
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final VcsWrapper that = (VcsWrapper)o;

    return !(myOriginal != null ? !myOriginal.equals(that.myOriginal) : that.myOriginal != null);

  }

  public int hashCode() {
    return (myOriginal != null ? myOriginal.hashCode() : 0);
  }

  public static VcsWrapper fromName(Project project, String name) {
    if (name.length() == 0) {
      return new VcsWrapper(null);
    }
    return new VcsWrapper(ProjectLevelVcsManager.getInstance(project).findVcsByName(name));
  }
}
