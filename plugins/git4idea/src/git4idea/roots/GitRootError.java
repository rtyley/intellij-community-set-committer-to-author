/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package git4idea.roots;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Kirill Likhodedov
 */
class GitRootError {

  private final Type myType;
  @NotNull private final VirtualFile myRoot;

  enum Type {
    EXTRA_ROOT,
    UNREGISTERED_ROOT
  }

  GitRootError(@NotNull Type type, @NotNull VirtualFile root) {
    myType = type;
    myRoot = root;
  }

  @Override
  public String toString() {
    return "GitRootError{" +
           "myType=" + myType +
           ", myRoot=" + myRoot +
           '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GitRootError error = (GitRootError)o;

    if (!myRoot.equals(error.myRoot)) return false;
    if (myType != error.myType) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myType != null ? myType.hashCode() : 0;
    result = 31 * result + myRoot.hashCode();
    return result;
  }
}
