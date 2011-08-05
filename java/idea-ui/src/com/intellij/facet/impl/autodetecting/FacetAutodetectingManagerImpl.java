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

package com.intellij.facet.impl.autodetecting;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.FacetType;
import com.intellij.facet.FacetTypeRegistry;
import com.intellij.facet.autodetecting.FacetDetector;
import com.intellij.facet.autodetecting.UnderlyingFacetSelector;
import com.intellij.facet.impl.autodetecting.model.FacetInfo2;
import com.intellij.facet.impl.autodetecting.model.ProjectFacetInfoSet;
import com.intellij.facet.pointers.FacetPointersManager;
import com.intellij.framework.detection.impl.exclude.old.DisabledAutodetectionByTypeElement;
import com.intellij.framework.detection.impl.exclude.old.DisabledAutodetectionInfo;
import com.intellij.ide.caches.FileContent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.SmartList;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

/**
 * @author nik
 */
public class FacetAutodetectingManagerImpl extends FacetAutodetectingManager implements AutodetectionFilter, ProjectComponent {
  private final MultiValuesMap<FileType, FacetDetectorWrapper> myDetectors = new MultiValuesMap<FileType, FacetDetectorWrapper>();
  private final Map<String, FacetDetector<?,?>> myId2Detector = new HashMap<String, FacetDetector<?,?>>();
  private final Project myProject;
  private final PsiManager myPsiManager;
  private final FacetPointersManager myFacetPointersManager;
  private FacetDetectionIndex myFileIndex;
  private MergingUpdateQueue myMergingUpdateQueue;
  private final ProjectFacetInfoSet myDetectedFacetSet;
  private DetectedFacetManager myDetectedFacetManager;
  private DisabledAutodetectionInfo myDisabledAutodetectionInfo = new DisabledAutodetectionInfo();
  private boolean myDetectionInProgress;
  private final Set<FacetType<?,?>> myFacetTypesWithDetectors = new THashSet<FacetType<?,?>>();
  private final EnableAutodetectionWorker myEnableAutodetectionWorker;
  private final Object myLock = new Object();

  public FacetAutodetectingManagerImpl(final Project project, PsiManager psiManager, FacetPointersManager facetPointersManager) {
    myProject = project;
    myPsiManager = psiManager;
    myFacetPointersManager = facetPointersManager;
    myDetectedFacetSet = new ProjectFacetInfoSet(project, project);
    myEnableAutodetectionWorker = new EnableAutodetectionWorker(project, this);
  }

  public void projectOpened() {
    if (ApplicationManager.getApplication().isUnitTestMode() || ApplicationManager.getApplication().isHeadlessEnvironment()) return;

    myDetectedFacetSet.loadDetectedFacets(FacetDetectionIndex.getDetectedFacetsFile(myProject));
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        myDetectedFacetManager.initUI();
      }
    });
  }

  public void projectClosed() {
    if (ApplicationManager.getApplication().isUnitTestMode() || ApplicationManager.getApplication().isHeadlessEnvironment()) return;

    myDetectedFacetSet.saveDetectedFacets(FacetDetectionIndex.getDetectedFacetsFile(myProject));
    if (myDetectedFacetManager != null) {
      myDetectedFacetManager.disposeUI();
    }
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "OldFacetAutodetectingManagerImpl";
  }

  public void initComponent() {
    boolean unitTestMode = ApplicationManager.getApplication().isUnitTestMode();
    if (!unitTestMode && !myProject.isDefault()) {
      initialize();
    }
  }

  public void initialize() {
    myDetectedFacetManager = new DetectedFacetManager(myProject, this, myDetectedFacetSet);
    FacetType[] types = FacetTypeRegistry.getInstance().getFacetTypes();
    for (FacetType<?,?> type : types) {
      registerDetectors(type);
    }
    myFileIndex = new FacetDetectionIndex(myProject, this, myDetectors.keySet());
    myFileIndex.initialize();
    MyPsiTreeChangeListener psiTreeChangeListener = new MyPsiTreeChangeListener();
    myPsiManager.addPsiTreeChangeListener(psiTreeChangeListener, myProject);
    myMergingUpdateQueue = new MergingUpdateQueue("FacetAutodetectionQueue", 500, true, null, myProject);
  }

  private <F extends Facet<C>, C extends FacetConfiguration> void registerDetectors(final FacetType<F, C> type) {
    FacetOnTheFlyDetectorRegistryImpl<C, F> detectorRegistry = new FacetOnTheFlyDetectorRegistryImpl<C, F>(type);
    type.registerDetectors(new FacetDetectorRegistryEx<C>(null, detectorRegistry));
    if (detectorRegistry.hasDetectors()) {
      myFacetTypesWithDetectors.add(type);
      myDetectedFacetManager.registerListeners(type);
    }
  }

  @Nullable
  public DisabledAutodetectionByTypeElement getDisabledAutodetectionState(@NotNull FacetType<?,?> type) {
    return myDisabledAutodetectionInfo.findElement(type.getStringId());
  }

  public void processFile(FileContent fileContent) {
    final VirtualFile file = fileContent.getVirtualFile();
    if (!file.isValid() || file.isDirectory() || myProject.isDisposed()
        || !file.exists() || !myFileIndex.getProjectFileIndex().isInContent(file)) return;

    FileType fileType = file.getFileType();
    Collection<FacetDetectorWrapper> detectors = myDetectors.get(fileType);
    if (detectors == null) return;

    synchronized (myLock) {
      List<FacetInfo2<Module>> facets = null;
      for (FacetDetectorWrapper<?,?,?,?> detector : detectors) {
        facets = process(file, fileContent, detector, facets);
      }

      String url = file.getUrl();
      FacetDetectionIndexEntry indexEntry = myFileIndex.getIndexEntry(url);
      if (indexEntry == null) {
        indexEntry = new FacetDetectionIndexEntry(file.getTimeStamp());
      }

      Collection<Integer> removed = indexEntry.update(myFacetPointersManager, facets);
      myFileIndex.putIndexEntry(url, indexEntry);

      if (removed != null) {
        removeObsoleteFacets(removed);
      }
    }
  }

  public void removeObsoleteFacets(final Collection<Integer> ids) {
    for (Integer id : ids) {
      Set<String> urls = myFileIndex.getFiles(id);
      if (urls == null || urls.isEmpty()) {
        myDetectedFacetSet.removeDetectedFacetWithSubFacets(id);
      }
    }
  }

  public ProjectFacetInfoSet getDetectedFacetSet() {
    return myDetectedFacetSet;
  }

  private List<FacetInfo2<Module>> process(final VirtualFile virtualFile,
                                           FileContent fileContent,
                                           final FacetDetectorWrapper<?, ?, ?, ?> detector,
                                           List<FacetInfo2<Module>> facets) {
    if (!myDetectionInProgress && detector.getFileContentPattern().accepts(fileContent)) {
      try {
        myDetectionInProgress = true;
        FacetInfo2<Module> facet = detector.detectFacet(virtualFile, myPsiManager);

        if (facet != null) {
          if (facets == null) {
            facets = new SmartList<FacetInfo2<Module>>();
          }
          facets.add(facet);
        }
      }
      finally {
        myDetectionInProgress = false;
      }
    }
    return facets;
  }

  public void disposeComponent() {
    if (!ApplicationManager.getApplication().isUnitTestMode() && !myProject.isDefault()) {
      dispose();
    }
  }

  public void dispose() {
    if (myFileIndex != null) {
      myFileIndex.dispose();
    }
  }

  public void redetectFacets() {
    myEnableAutodetectionWorker.redetectFacets();
  }

  @TestOnly
  public EnableAutodetectionWorker getEnableAutodetectionWorker() {
    return myEnableAutodetectionWorker;
  }

  public boolean isAutodetectionEnabled(final Module module, final FacetType facetType, final String url) {
    return !myDisabledAutodetectionInfo.isDisabled(facetType.getStringId(), module.getName(), url);
  }

  private void queueUpdate(final PsiFile psiFile) {
    if (!myDetectors.keySet().contains(psiFile.getFileType())) return;

    VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile != null) {
      queueUpdate(virtualFile);
    }
  }

  public void queueUpdate(final VirtualFile file) {
    Update update = new Update("file:" + file.getUrl()) {
      public void run() {
        processFile(new FileContent(file));
      }
    };

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      update.run();
    }
    else {
      myMergingUpdateQueue.queue(update);
    }
  }

  @Nullable
  public Set<String> getFiles(final Facet facet) {
    return myFileIndex.getFiles(facet);
  }

  public void removeFacetFromCache(final Facet facet) {
    myFileIndex.removeFacetFromCache(myFacetPointersManager.create(facet));
  }

  public Set<FileType> getFileTypes(final Set<FacetType> facetTypes) {
    THashSet<FileType> fileTypes = new THashSet<FileType>();
    for (FileType type : myDetectors.keySet()) {
      Collection<FacetDetectorWrapper> detectorWrappers = myDetectors.get(type);
      if (detectorWrappers != null) {
        for (FacetDetectorWrapper detectorWrapper : detectorWrappers) {
          if (facetTypes.contains(detectorWrapper.getFacetType())) {
            fileTypes.add(type);
            break;
          }
        }
      }
    }
    return fileTypes;
  }

  public void disableAutodetectionInModule(final FacetType type, final Module module) {
    myDisabledAutodetectionInfo.addDisabled(type.getStringId(), module.getName());
  }

  public void disableAutodetectionInFiles(@NotNull final FacetType type, @NotNull final Module module, @NotNull final String... fileUrls) {
    myDisabledAutodetectionInfo.addDisabled(type.getStringId(), module.getName(), fileUrls);
  }

  public void setDisabledAutodetectionState(final @NotNull FacetType<?, ?> facetType, final @Nullable DisabledAutodetectionByTypeElement element) {
    String id = facetType.getStringId();
    DisabledAutodetectionByTypeElement oldElement = myDisabledAutodetectionInfo.findElement(id);
    myEnableAutodetectionWorker.queueChanges(facetType, oldElement, element);
    myDisabledAutodetectionInfo.replaceElement(id, element);
  }

  public DetectedFacetManager getDetectedFacetManager() {
    return myDetectedFacetManager;
  }

  @Nullable
  public FacetDetector<?,?> findDetector(final String detectorId) {
    return myId2Detector.get(detectorId);
  }

  public FacetDetectionIndex getFileIndex() {
    return myFileIndex;
  }

  private class FacetOnTheFlyDetectorRegistryImpl<C extends FacetConfiguration, F extends Facet<C>> implements FacetOnTheFlyDetectorRegistry<C> {
    private final FacetType<F, C> myType;
    private boolean myHasDetectors;

    public FacetOnTheFlyDetectorRegistryImpl(final FacetType<F, C> type) {
      myType = type;
    }

    public <U extends FacetConfiguration> void register(@NotNull final FileType fileType, @NotNull final OldFileContentPattern fileContentFilter,
                                                        @NotNull final FacetDetector<VirtualFile, C> facetDetector,
                                                        final UnderlyingFacetSelector<VirtualFile, U> selector) {
      myHasDetectors = true;
      myId2Detector.put(facetDetector.getId(), facetDetector);
      myDetectors.put(fileType, new FacetByVirtualFileDetectorWrapper<C, F, U>(myDetectedFacetSet, myType,
                                                                               FacetAutodetectingManagerImpl.this, fileContentFilter,
                                                                               facetDetector, selector));
    }

    public <U extends FacetConfiguration> void register(@NotNull final FileType fileType, @NotNull final OldFileContentPattern fileContentFilter,
                                                        @NotNull final Condition<PsiFile> psiFileFilter, @NotNull final FacetDetector<PsiFile, C> facetDetector,
                                                        final UnderlyingFacetSelector<VirtualFile, U> selector) {
      myHasDetectors = true;
      myId2Detector.put(facetDetector.getId(), facetDetector);
      myDetectors.put(fileType, new FacetByPsiFileDetectorWrapper<C, F, U>(myDetectedFacetSet, myType, FacetAutodetectingManagerImpl.this,
                                                                           fileContentFilter, facetDetector, psiFileFilter, selector));
    }

    public boolean hasDetectors() {
      return myHasDetectors;
    }
  }

  private class MyPsiTreeChangeListener extends PsiTreeChangeAdapter {
    public void childAdded(final PsiTreeChangeEvent event) {
      PsiElement child = event.getChild();
      if (child instanceof PsiFile) {
        queueUpdate((PsiFile)child);
      }
      else {
        processChangedElement(event);
      }
    }

    private void processChangedElement(final PsiTreeChangeEvent event) {
      PsiFile psiFile = event.getFile();
      if (psiFile != null) {
        queueUpdate(psiFile);
      }
    }

    public void childRemoved(final PsiTreeChangeEvent event) {
      PsiElement child = event.getChild();
      if (child instanceof PsiFile) {
        VirtualFile virtualFile = ((PsiFile)child).getVirtualFile();
        if (virtualFile != null) {
          myFileIndex.removeIndexEntry(virtualFile);
        }
      }
      else {
        processChangedElement(event);
      }
    }

    public void childReplaced(final PsiTreeChangeEvent event) {
      processChangedElement(event);
    }

    public void childMoved(final PsiTreeChangeEvent event) {
      processChangedElement(event);
    }
  }
}
