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
package com.intellij.lang.ant;

import com.intellij.lang.CompositeLanguage;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.changes.AntChangeVisitor;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class AntSupport implements ApplicationComponent {
  private static LanguageFileType ourFileType = null;
  private static AntLanguage ourLanguage = null;
  private static AntChangeVisitor ourChangeVisitor = null;
  private static final Map<AntFile, WeakHashMap<AntFile, Boolean>> ourFileDependencies = new WeakHashMap<AntFile, WeakHashMap<AntFile, Boolean>>();

  public AntSupport(FileTypeManager fileTypeManager) {
    fileTypeManager.getRegisteredFileTypes();
    ((CompositeLanguage)StdLanguages.XML).registerLanguageExtension(new AntLanguageExtension());
  }

  public static synchronized AntLanguage getLanguage() {
    if (ourLanguage == null) {
      if (ourFileType == null) {
        ourFileType = new AntFileType();
      }
      ourLanguage = (AntLanguage)ourFileType.getLanguage();
    }
    return ourLanguage;
  }

  public static synchronized AntChangeVisitor getChangeVisitor() {
    if (ourChangeVisitor == null) {
      ourChangeVisitor = new AntChangeVisitor();
    }
    return ourChangeVisitor;
  }

  @NotNull
  @NonNls
  public String getComponentName() {
    return "AntSupport";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public static void markFileAsAntFile(final VirtualFile file, final FileViewProvider viewProvider, final boolean value) {
    if (file.isValid() && ForcedAntFileAttribute.isAntFile(file) != value) {
      ForcedAntFileAttribute.forceAntFile(file, value);
      viewProvider.contentsSynchronized();
    }
  }

  //
  // Managing ant files dependencies via the <import> task.
  //

  public static synchronized List<AntFile> getImportingFiles(final AntFile imported) {
    final WeakHashMap<AntFile, Boolean> files = ourFileDependencies.get(imported);
    if (files != null) {
      final int size = files.size();
      if (size > 0) {
        final List<AntFile> result = new ArrayList<AntFile>(size);
        for (final AntFile file : files.keySet()) {
          if (file != null) {
            result.add(file);
          }
        }
        return result;
      }
    }
    return Collections.emptyList();
  }

  public static synchronized void registerDependency(final AntFile importing, final AntFile imported) {
    final Map<AntFile, WeakHashMap<AntFile,Boolean>> dependencies = ourFileDependencies;
    WeakHashMap<AntFile, Boolean> files = dependencies.get(imported);
    if(files == null) {
      files = new WeakHashMap<AntFile, Boolean>();
      dependencies.put(imported, files);
    }
    files.put(importing, true);
  }

  public static AntFile getAntFile ( PsiFile psiFile ) {
    if (psiFile instanceof AntFile) {
      return (AntFile)psiFile;
    }
    else {
      return (AntFile)psiFile.getViewProvider().getPsi(getLanguage());
    }
  }

  @Nullable
  public static AntFile toAntFile(VirtualFile vFile, final Project project) {
    if (vFile == null) {
      return null;
    }
    final FileType fileType = vFile.getFileType();
    if (fileType instanceof AntFileType || XMLLanguage.INSTANCE.getAssociatedFileType() == fileType) {
      final PsiFile psiFile = PsiManager.getInstance(project).findFile(vFile);
      return psiFile != null? getAntFile(psiFile) : null;
    }
    return null;
  }
}
