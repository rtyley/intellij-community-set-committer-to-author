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

package org.jetbrains.plugins.groovy.config;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.util.GroovyUtils;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

/**
 * @author ilyas
 */
public abstract class GroovyConfigUtils extends AbstractConfigUtils {
  @NonNls public static final String GROOVY_ALL_JAR_PATTERN = "groovy-all-(.*)\\.jar";

  private static GroovyConfigUtils myGroovyConfigUtils;
  @NonNls public static final String GROOVY_JAR_PATTERN = "groovy-(\\d.*)\\.jar";
  public static final String NO_VERSION = "<no version>";

  private GroovyConfigUtils() {
  }

  public static GroovyConfigUtils getInstance() {
    if (myGroovyConfigUtils == null) {
      myGroovyConfigUtils = new GroovyConfigUtils() {
        {
          STARTER_SCRIPT_FILE_NAME = "groovy";
        }};
    }
    return myGroovyConfigUtils;
  }

  @NotNull
  public String getSDKVersion(@NotNull final String path) {
    String groovyJarVersion = getSDKJarVersion(path + "/lib", GROOVY_JAR_PATTERN, MANIFEST_PATH);
    if (groovyJarVersion != null) {
      return groovyJarVersion;
    }

    groovyJarVersion = getSDKJarVersion(path + "/lib", GROOVY_ALL_JAR_PATTERN, MANIFEST_PATH);
    if (groovyJarVersion != null) {
      return groovyJarVersion;
    }
    
    return UNDEFINED_VERSION;
  }

  public boolean isSDKLibrary(Library library) {
    if (library == null) return false;
    return isGroovyLibrary(library.getFiles(OrderRootType.CLASSES));
  }

  public static boolean isGroovyLibrary(VirtualFile[] classFiles) {
    for (VirtualFile file : classFiles) {
      if (isAnyGroovyJar(file.getName())) {
        return true;
      }
    }
    return false;
  }

  public String getSDKVersion(@NotNull Library library) {
    return getSDKVersion(LibrariesUtil.getGroovyLibraryHome(library));
  }

  public static boolean isAnyGroovyJar(@NonNls final String name) {
    return name.matches(GROOVY_ALL_JAR_PATTERN) || name.matches(GROOVY_JAR_PATTERN);
  }

  @Nullable
  public String getSDKVersion(@NotNull Module module) {
    final String path = LibrariesUtil.getGroovyHomePath(module);
    if (path == null) return null;
    return getSDKVersion(path);
  }

  public boolean isAtLeastGroovy1_7(Module module) {
    if (module == null) return false;
    final String version = getSDKVersion(module);
    if (version == null) return false;
    return version.compareTo("1.7") >= 0;
  }

  public  boolean isAtLeastGroovy1_7(PsiElement psiElement) {
    return isAtLeastGroovy1_7(ModuleUtil.findModuleForPsiElement(psiElement));
  }

  @NotNull
  public String getSDKVersion(PsiElement psiElement) {
    final Module module = ModuleUtil.findModuleForPsiElement(psiElement);
    if (module == null) {
      return NO_VERSION;
    }
    final String s = getSDKVersion(module);
    return s != null ? s : NO_VERSION;
  }


  @NotNull
  public String getSDKInstallPath(Module module) {
    if (module == null) return "";
    Library[] libraries = getSDKLibrariesByModule(module);
    if (libraries.length == 0) return "";
    Library library = libraries[0];
    return LibrariesUtil.getGroovyLibraryHome(library);
  }

  @Override
  public boolean isSDKHome(VirtualFile file) {
    if (file != null && file.isDirectory()) {
      final String path = file.getPath();
      if (GroovyUtils.getFilesInDirectoryByPattern(path + "/lib", GROOVY_JAR_PATTERN).length > 0) {
        return true;
      }
      if (GroovyUtils.getFilesInDirectoryByPattern(path + "/embeddable", GROOVY_ALL_JAR_PATTERN).length > 0) {
        return true;
      }
    }
    return false;
  }

  public boolean tryToSetUpGroovyFacetOntheFly(final Module module) {
    final Project project = module.getProject();
    final Library[] libraries = getAllSDKLibraries(project);
    if (libraries.length > 0) {
      final Library library = libraries[0];
      int result = Messages
        .showOkCancelDialog(GroovyBundle.message("groovy.like.library.found.text", module.getName(), library.getName(), getSDKLibVersion(library)),
                            GroovyBundle.message("groovy.like.library.found"), GroovyIcons.GROOVY_ICON_32x32);
      final Ref<Boolean> ref = new Ref<Boolean>();
      ref.set(false);
      if (result == 0) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
            LibraryOrderEntry entry = model.addLibraryEntry(libraries[0]);
            LibrariesUtil.placeEntryToCorrectPlace(model, entry);
            model.commit();
            ref.set(true);
          }
        });
      }
      return ref.get().booleanValue();
    }
    return false;
  }
}
