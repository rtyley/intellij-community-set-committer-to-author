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

package org.jetbrains.android.resourceManagers;

import com.android.AndroidConstants;
import com.android.resources.ResourceType;
import com.intellij.CommonBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.android.AndroidFileTemplateProvider;
import org.jetbrains.android.AndroidValueResourcesIndex;
import org.jetbrains.android.actions.CreateResourceFileAction;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.dom.resources.Attr;
import org.jetbrains.android.dom.resources.DeclareStyleable;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.dom.resources.Resources;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.android.util.ResourceEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

import static org.jetbrains.android.util.AndroidUtils.loadDomElement;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Mar 30, 2009
 * Time: 7:44:03 PM
 * To change this template use File | Settings | File Templates.
 */
public class LocalResourceManager extends ResourceManager {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.resourceManagers.ResourceManager");
  private AttributeDefinitions myAttrDefs;

  public LocalResourceManager(@NotNull Module module) {
    super(module);
  }

  @NotNull
  @Override
  public VirtualFile[] getAllResourceDirs() {
    Set<VirtualFile> result = new HashSet<VirtualFile>();
    collectResourceDirs(getModule(), result, new HashSet<Module>());
    return VfsUtil.toVirtualFileArray(result);
  }

  @Override
  public boolean isResourceDir(@NotNull VirtualFile dir) {
    if (dir.equals(getResourceDir())) {
      return true;
    }
    for (VirtualFile dir1 : getResourceOverlayDirs()) {
      if (dir.equals(dir1)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  @Override
  public VirtualFile getResourceDir() {
    return AndroidRootUtil.getResourceDir(getModule());
  }

  public List<Resources> getResourceElements() {
    return getResourceElements(null);
  }

  @NotNull
  @Override
  public VirtualFile[] getResourceOverlayDirs() {
    return AndroidRootUtil.getResourceOverlayDirs(getModule());
  }

  @NotNull
  public List<ResourceElement> getValueResources(@NotNull final String resourceType) {
    return getValueResources(resourceType, null);
  }

  private static void collectResourceDirs(Module module, Set<VirtualFile> result, Set<Module> visited) {
    if (!visited.add(module)) {
      return;
    }

    VirtualFile resDir = AndroidRootUtil.getResourceDir(module);
    if (resDir != null && !result.add(resDir)) {
      return;
    }
    for (AndroidFacet depFacet : AndroidUtils.getAllAndroidDependencies(module, false)) {
      collectResourceDirs(depFacet.getModule(), result, visited);
    }
  }

  @Nullable
  public static LocalResourceManager getInstance(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    return facet != null ? facet.getLocalResourceManager() : null;
  }

  @Nullable
  public static LocalResourceManager getInstance(@NotNull PsiElement element) {
    AndroidFacet facet = AndroidFacet.getInstance(element);
    return facet != null ? facet.getLocalResourceManager() : null;
  }

  @NotNull
  public Set<String> getValueResourceTypes() {
    final Map<VirtualFile, Set<String>> file2Types = new HashMap<VirtualFile, Set<String>>();
    final FileBasedIndex index = FileBasedIndex.getInstance();
    final GlobalSearchScope scope = GlobalSearchScope.projectScope(myModule.getProject());

    for (String resourceType : ResourceType.getNames()) {
      final ResourceEntry typeMarkerEntry = AndroidValueResourcesIndex.createTypeMarkerEntry(resourceType);

      for (Set<ResourceEntry> entrySet : index.getValues(AndroidValueResourcesIndex.INDEX_ID, typeMarkerEntry, scope)) {
        for (ResourceEntry entry : entrySet) {
          final Collection<VirtualFile> files = index.getContainingFiles(AndroidValueResourcesIndex.INDEX_ID, entry, scope);

          for (VirtualFile file : files) {
            Set<String> resourcesInFile = file2Types.get(file);

            if (resourcesInFile == null) {
              resourcesInFile = new HashSet<String>();
              file2Types.put(file, resourcesInFile);
            }
            resourcesInFile.add(entry.getType());
          }
        }
      }
    }
    final Set<String> result = new HashSet<String>();

    for (VirtualFile file : getAllValueResourceFiles()) {
      final Set<String> types = file2Types.get(file);

      if (types != null) {
        result.addAll(types);
      }
    }
    return result;
  }

  @NotNull
  public AttributeDefinitions getAttributeDefinitions() {
    if (myAttrDefs == null) {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        @Override
        public void run() {
          List<XmlFile> xmlResFiles = new ArrayList<XmlFile>();
          for (PsiFile file : findResourceFiles("values")) {
            if (file instanceof XmlFile) {
              xmlResFiles.add((XmlFile)file);
            }
          }
          myAttrDefs = new AttributeDefinitions(xmlResFiles.toArray(new XmlFile[xmlResFiles.size()]));
        }
      });
    }
    return myAttrDefs;
  }

  public void invalidateAttributeDefinitions() {
    myAttrDefs = null;
  }

  @NotNull
  public List<Attr> findAttrs(@NotNull String name) {
    List<Attr> list = new ArrayList<Attr>();
    for (Resources res : getResourceElements()) {
      for (Attr attr : res.getAttrs()) {
        if (name.equals(attr.getName().getValue())) {
          list.add(attr);
        }
      }
      for (DeclareStyleable styleable : res.getDeclareStyleables()) {
        for (Attr attr : styleable.getAttrs()) {
          if (name.equals(attr.getName().getValue())) {
            list.add(attr);
          }
        }
      }
    }
    return list;
  }

  public List<DeclareStyleable> findStyleables(@NotNull String name) {
    List<DeclareStyleable> list = new ArrayList<DeclareStyleable>();
    for (Resources res : getResourceElements()) {
      for (DeclareStyleable styleable : res.getDeclareStyleables()) {
        if (name.equals(styleable.getName().getValue())) {
          list.add(styleable);
        }
      }
    }
    return list;
  }

  @Nullable
  private VirtualFile findOrCreateResourceFile(@NotNull final String fileName) {
    VirtualFile dir = getResourceDir();
    if (dir == null) {
      Messages.showErrorDialog(myModule.getProject(), AndroidBundle.message("check.resource.dir.error", myModule.getName()),
                               CommonBundle.getErrorTitle());
      return null;
    }
    final VirtualFile valuesDir = findOrCreateChildDir(dir, AndroidConstants.FD_RES_VALUES);
    if (valuesDir == null) {
      String errorMessage = AndroidBundle.message("android.directory.cannot.be.found.error", AndroidConstants.FD_RES_VALUES);
      Messages.showErrorDialog(myModule.getProject(), errorMessage, CommonBundle.getErrorTitle());
      return null;
    }
    VirtualFile child = valuesDir.findChild(fileName);
    if (child != null) return child;
    try {
      AndroidFileTemplateProvider
        .createFromTemplate(myModule.getProject(), valuesDir, AndroidFileTemplateProvider.VALUE_RESOURCE_FILE_TEMPLATE, fileName);
    }
    catch (Exception e) {
      LOG.error(e);
      return null;
    }
    VirtualFile result = valuesDir.findChild(fileName);
    if (result == null) {
      LOG.error("Can't create resource file");
    }
    return result;
  }

  // must be invoked in a write action
  @Nullable
  public VirtualFile addResourceFileAndNavigate(@NotNull final String fileOrResourceName, @NotNull String resType) {
    VirtualFile resDir = getResourceDir();
    Project project = myModule.getProject();
    if (resDir == null) {
      Messages
        .showErrorDialog(project, AndroidBundle.message("check.resource.dir.error", myModule.getName()), CommonBundle.getErrorTitle());
      return null;
    }
    PsiElement[] createdElements = CreateResourceFileAction.createResourceFile(project, resDir, resType, fileOrResourceName);
    if (createdElements.length == 0) return null;
    assert createdElements.length == 1;
    PsiElement element = createdElements[0];
    assert element instanceof PsiFile;
    return ((PsiFile)element).getVirtualFile();
  }

  // must be invoked in a write action
  @Nullable
  public ResourceElement addValueResource(@NotNull final String type, @NotNull final String name, @Nullable final String value) {
    String resourceFileName = getDefaultResourceFileName(type);
    if (resourceFileName == null) {
      throw new IllegalArgumentException("Incorrect resource type");
    }
    VirtualFile resFile = findOrCreateResourceFile(resourceFileName);
    if (resFile == null) return null;
    final Resources resources = loadDomElement(myModule, resFile, Resources.class);
    if (resources == null) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        throw new IncorrectOperationException("invalid strings.xml");
      }
      Messages.showErrorDialog(myModule.getProject(), AndroidBundle.message("not.resource.file.error", resourceFileName),
                               CommonBundle.getErrorTitle());
      return null;
    }
    ResourceElement element = AndroidResourceUtil.addValueResource(type, resources);
    element.getName().setValue(name);
    if (value != null) {
      element.setStringValue(value);
    }
    return element;
  }

  @Nullable
  private VirtualFile findOrCreateChildDir(@NotNull final VirtualFile dir, @NotNull final String name) {
    try {
      return AndroidUtils.createChildDirectoryIfNotExist(myModule.getProject(), dir, name);
    }
    catch (IOException e) {
      LOG.error(e);
      return null;
    }
  }
}
