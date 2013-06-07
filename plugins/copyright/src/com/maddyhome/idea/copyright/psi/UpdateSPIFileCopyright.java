/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.maddyhome.idea.copyright.psi;

import com.intellij.lang.spi.SPILanguage;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.maddyhome.idea.copyright.CopyrightProfile;

/**
 * User: anna
 */
public class UpdateSPIFileCopyright extends UpdateCopyrightsProvider {
  @Override
  public UpdateCopyright createInstance(Project project, Module module, VirtualFile file, FileType base, CopyrightProfile options) {
    return new UpdatePsiFileCopyright(project, module, file, options) {
      @Override
      protected void scanFile() {
        final PsiElement firstChild = getFile().getFirstChild();
        if (firstChild != null) {
          checkComments(firstChild, firstChild, true);
        }
      }

      @Override
      protected boolean accept() {
        return getFile().getLanguage() == SPILanguage.INSTANCE;
      }
    };
  }
}
