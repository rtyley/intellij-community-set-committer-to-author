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

package org.jetbrains.android.facet;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.GeneratingCompiler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.*;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.android.compiler.*;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.fileTypes.AndroidIdlFileType;
import org.jetbrains.android.fileTypes.AndroidRenderscriptFileType;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

import static org.jetbrains.android.util.AndroidUtils.findSourceRoot;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Aug 31, 2009
 * Time: 4:49:30 PM
 * To change this template use File | Settings | File Templates.
 */
class AndroidResourceFilesListener extends VirtualFileAdapter {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.facet.AndroidResourceFilesListener");

  private final MergingUpdateQueue myQueue;
  private final AndroidFacet myFacet;
  private String myCachedPackage = null;

  private Set<ResourceEntry> myResourceSet = new HashSet<ResourceEntry>();
  private static final Object RESOURCES_SET_LOCK = new Object();

  public AndroidResourceFilesListener(final AndroidFacet facet) {
    myFacet = facet;
    myQueue = new MergingUpdateQueue("AndroidResourcesCompilationQueue", 300, true, null, myFacet, null, false);
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        if (facet.getModule().getProject().isDisposed()) {
          return;
        }

        Manifest manifest = facet.getManifest();
        if (manifest != null) {
          myCachedPackage = manifest.getPackage().getValue();
        }
      }
    });
  }

  @Override
  public void fileCreated(VirtualFileEvent event) {
    fileChanged(event);
  }

  @Override
  public void fileDeleted(VirtualFileEvent event) {
    fileChanged(event);
  }

  @Override
  public void fileMoved(VirtualFileMoveEvent event) {
    fileChanged(event);
  }

  @Override
  public void fileCopied(VirtualFileCopyEvent event) {
    fileChanged(event);
  }

  @Override
  public void contentsChanged(VirtualFileEvent event) {
    fileChanged(event);
  }

  private String getResDirName() {
    return AndroidUtils.getSimpleNameByRelativePath(myFacet.getConfiguration().RES_FOLDER_RELATIVE_PATH);
  }

  private String getManifestFileName() {
    return AndroidUtils.getSimpleNameByRelativePath(myFacet.getConfiguration().MANIFEST_FILE_RELATIVE_PATH);
  }

  private void fileChanged(@NotNull final VirtualFileEvent e) {
    VirtualFile file = e.getFile();
    if (!file.isValid()) {
      return;
    }

    VirtualFile parent = e.getParent();
    VirtualFile gp = parent != null ? parent.getParent() : null;

    if (file.getFileType() == AndroidIdlFileType.ourFileType ||
        file.getFileType() == AndroidRenderscriptFileType.INSTANCE ||
        getManifestFileName().equals(file.getName()) ||
        (gp != null && gp.isDirectory() && getResDirName().equals(gp.getName()))) {
      ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        public void run() {
          myQueue.queue(new MyUpdate(e));
        }
      });
    }
  }

  public void setResourceSet(@NotNull Set<ResourceEntry> resourceSet) {
    synchronized (RESOURCES_SET_LOCK) {
      myResourceSet = resourceSet;
    }
  }

  private class MyUpdate extends Update {
    private final VirtualFileEvent myEvent;

    public MyUpdate(VirtualFileEvent event) {
      super(event.getParent());
      myEvent = event;
    }

    public void run() {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        return;
      }

      final GeneratingCompiler compilerToRun = ApplicationManager.getApplication().runReadAction(new Computable<GeneratingCompiler>() {
        @Nullable
        public GeneratingCompiler compute() {
          return computeCompilerToRun();
        }
      });

      if (compilerToRun == null) {
        return;
      }

      if (compilerToRun instanceof AndroidAptCompiler) {
        final HashSet<ResourceEntry> resourceSet = new HashSet<ResourceEntry>();
        AndroidCompileUtil.collectAllResources(myFacet, resourceSet);

        synchronized (RESOURCES_SET_LOCK) {
          if (resourceSet.equals(myResourceSet)) {
            return;
          }
          myResourceSet = resourceSet;
        }
      }
      AndroidCompileUtil.generate(myFacet.getModule(), compilerToRun, true);
    }

    @Nullable
    private GeneratingCompiler computeCompilerToRun() {
      if (myFacet.isDisposed()) {
        return null;
      }
      final Module myModule = myFacet.getModule();
      final Project project = myModule.getProject();

      if (project.isDisposed()) {
        return null;
      }
      final VirtualFile file = myEvent.getFile();
      final Module module = ModuleUtil.findModuleForFile(file, project);

      if (module != myModule) {
        return null;
      }

      VirtualFile parent = myEvent.getParent();
      if (parent == null) {
        return null;
      }

      parent = parent.getParent();
      if (AndroidAptCompiler.isToCompileModule(module, myFacet.getConfiguration()) &&
          (myFacet.getConfiguration().REGENERATE_R_JAVA && parent == AndroidRootUtil.getResourceDir(module) ||
           AndroidRootUtil.getManifestFile(module) == file)) {
        final Manifest manifest = myFacet.getManifest();
        final String aPackage = manifest != null ? manifest.getPackage().getValue() : null;

        if (myCachedPackage != null && !myCachedPackage.equals(aPackage)) {
          String aptGenDirPath = myFacet.getAptGenSourceRootPath();
          AndroidCompileUtil.removeDuplicatingClasses(myModule, myCachedPackage, AndroidUtils.R_CLASS_NAME, null, aptGenDirPath);
        }
        myCachedPackage = aPackage;
        myFacet.getLocalResourceManager().invalidateAttributeDefinitions();

        return new AndroidAptCompiler();
      }

      if (myFacet.getConfiguration().REGENERATE_JAVA_BY_AIDL && file.getFileType() == AndroidIdlFileType.ourFileType) {
        VirtualFile sourceRoot = findSourceRoot(myModule, file);
        if (sourceRoot != null && AndroidRootUtil.getAidlGenDir(module, myFacet) != sourceRoot) {
          return new AndroidIdlCompiler(project);
        }
      }

      if (file.getFileType() == AndroidRenderscriptFileType.INSTANCE) {
        final VirtualFile sourceRoot = findSourceRoot(myModule, file);
        if (sourceRoot != null && AndroidRootUtil.getRenderscriptGenDir(myModule) != sourceRoot) {
          return new AndroidRenderscriptCompiler();
        }
      }
      return null;
    }

    @Override
    public boolean canEat(Update update) {
      if (update instanceof MyUpdate) {
        VirtualFile hisFile = ((MyUpdate)update).myEvent.getFile();
        VirtualFile file = myEvent.getFile();
        if (hisFile == file) return true;
        if (hisFile.getFileType() == AndroidIdlFileType.ourFileType || file.getFileType() == AndroidIdlFileType.ourFileType) {
          return hisFile.getFileType() == file.getFileType();
        }
        return true;
      }
      return false;
    }
  }
}
