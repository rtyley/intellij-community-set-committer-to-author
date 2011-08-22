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
 * @author max
 */
package com.intellij.psi;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.compiled.ClsFileImpl;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;

public class ClassFileViewProvider extends SingleRootFileViewProvider {
  public ClassFileViewProvider(@NotNull final PsiManager manager, @NotNull final VirtualFile file) {
    super(manager, file);
  }

  public ClassFileViewProvider(@NotNull final PsiManager manager, @NotNull final VirtualFile virtualFile, final boolean physical) {
    super(manager, virtualFile, physical);
  }

  @Override
  protected PsiFile createFile(final Project project, final VirtualFile vFile, final FileType fileType) {
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    if (fileIndex.isInLibraryClasses(vFile) || !fileIndex.isInSource(vFile)) {
      String name = vFile.getName();

      // skip inners & anonymous
      int dotIndex = name.lastIndexOf('.');
      if (dotIndex < 0) dotIndex = name.length();
      int index = name.lastIndexOf('$', dotIndex);
      if (index >= 0) return null;

      return new ClsFileImpl((PsiManagerImpl)PsiManager.getInstance(project), this);
    }
    return null;
  }

  @NotNull
  @Override
  public SingleRootFileViewProvider createCopy(final VirtualFile copy) {
    return new ClassFileViewProvider(getManager(), copy, false);
  }
}