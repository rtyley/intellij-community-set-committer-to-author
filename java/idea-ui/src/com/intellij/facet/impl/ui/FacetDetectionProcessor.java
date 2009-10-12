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

package com.intellij.facet.impl.ui;

import com.intellij.facet.*;
import com.intellij.facet.autodetecting.FacetDetector;
import com.intellij.facet.autodetecting.UnderlyingFacetSelector;
import com.intellij.facet.impl.autodetecting.FacetDetectorForWizardRegistry;
import com.intellij.facet.impl.autodetecting.FacetDetectorRegistryEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.openapi.module.ModuleType;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

/**
 * @author nik
 */
public class FacetDetectionProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.facet.impl.ui.FacetDetectionProcessor");
  private final Map<FacetConfiguration, DetectedInWizardFacetInfo> myDetectedFacets = new LinkedHashMap<FacetConfiguration, DetectedInWizardFacetInfo>();
  private final Map<FacetTypeId, List<FacetConfiguration>> myDetectedConfigurations = new HashMap<FacetTypeId, List<FacetConfiguration>>();
  private final ProgressIndicator myProgressIndicator;
  private final FileTypeManager myFileTypeManager;
  private final List<MultiValuesMap<FileType, MyFacetDetectorWrapper>> myDetectors = new ArrayList<MultiValuesMap<FileType, MyFacetDetectorWrapper>>();

  public FacetDetectionProcessor(final ProgressIndicator progressIndicator, final ModuleType moduleType) {
    myProgressIndicator = progressIndicator;
    myFileTypeManager = FileTypeManager.getInstance();
    FacetType[] types = FacetTypeRegistry.getInstance().getFacetTypes();
    for (FacetType<?,?> type : types) {
      if (type.isSuitableModuleType(moduleType)) {
        registerDetectors(type);
        myDetectedConfigurations.put(type.getId(), new ArrayList<FacetConfiguration>());
      }
    }
  }

  private <C extends FacetConfiguration> void registerDetectors(final FacetType<?, C> type) {
    type.registerDetectors(new FacetDetectorRegistryEx<C>(new MyFacetDetectorRegistry<C>(type), null));
  }

  public void process(final File root) {
    final VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(root);
    if (virtualFile == null) return;

    for (int i = 0; i < myDetectors.size(); i++) {
      MultiValuesMap<FileType, MyFacetDetectorWrapper> map = myDetectors.get(i);
      if (i > 0) {
        List<MyFacetDetectorWrapper> toRemove = new ArrayList<MyFacetDetectorWrapper>();
        for (MyFacetDetectorWrapper detector : map.values()) {
          FacetTypeId typeId = detector.getFacetType().getUnderlyingFacetType();
          LOG.assertTrue(typeId != null);
          List<FacetConfiguration> list = myDetectedConfigurations.get(typeId);
          if (list == null || list.isEmpty()) {
            toRemove.add(detector);
          }
        }

        for (MyFacetDetectorWrapper detectorWrapper : toRemove) {
          map.remove(detectorWrapper.getFileType(), detectorWrapper);
        }
      }

      if (map.isEmpty()) break;
      process(virtualFile, map);
    }
  }

  private void process(final VirtualFile file, MultiValuesMap<FileType, MyFacetDetectorWrapper> detectorsMap) {
    if (myProgressIndicator.isCanceled()) return;

    if (file.isDirectory()) {
      file.getChildren();//initialize myChildren field to ensure that refresh will be really performed 
      file.refresh(false, false);
      VirtualFile[] children = file.getChildren();
      for (VirtualFile child : children) {
        process(child, detectorsMap);
      }
      return;
    }

    myProgressIndicator.setText2(file.getPresentableUrl());
    FileType fileType = myFileTypeManager.getFileTypeByFile(file);
    Collection<MyFacetDetectorWrapper> detectors = detectorsMap.get(fileType);
    if (detectors == null) return;

    for (MyFacetDetectorWrapper detector : detectors) {
      detector.detectFacet(file);
    }
  }

  public List<DetectedInWizardFacetInfo> getDetectedFacetsInfos() {
    return new ArrayList<DetectedInWizardFacetInfo>(myDetectedFacets.values());
  }

  public List<FacetInfo> getDetectedFacets() {
    List<FacetInfo> list = new ArrayList<FacetInfo>();
    for (DetectedInWizardFacetInfo info : myDetectedFacets.values()) {
      list.add(info.getFacetInfo());
    }
    return list;
  }

  private class MyFacetDetectorWrapper<C extends FacetConfiguration, U extends FacetConfiguration> {
    private final FacetType<?, C> myFacetType;
    private final FileType myFileType;
    private final VirtualFileFilter myVirtualFileFilter;
    private final FacetDetector<VirtualFile, C> myDetector;
    private final UnderlyingFacetSelector<VirtualFile, U> myUnderlyingFacetSelector;

    public MyFacetDetectorWrapper(final FacetType<?, C> facetType, final FileType fileType, final VirtualFileFilter virtualFileFilter, final FacetDetector<VirtualFile, C> detector,
                                  final UnderlyingFacetSelector<VirtualFile, U> underlyingFacetSelector) {
      myUnderlyingFacetSelector = underlyingFacetSelector;
      myFacetType = facetType;
      myFileType = fileType;
      myVirtualFileFilter = virtualFileFilter;
      myDetector = detector;
    }

    public FileType getFileType() {
      return myFileType;
    }

    public FacetType<?, C> getFacetType() {
      return myFacetType;
    }

    public void detectFacet(VirtualFile file) {
      if (!myVirtualFileFilter.accept(file)) return;

      FacetInfo underlyingFacet = null;
      if (myUnderlyingFacetSelector != null) {
        List<U> list = (List<U>)myDetectedConfigurations.get(myFacetType.getUnderlyingFacetType());
        U underlying = myUnderlyingFacetSelector.selectUnderlyingFacet(file, list);
        if (underlying == null) {
          return;
        }
        underlyingFacet = myDetectedFacets.get(underlying).getFacetInfo();
      }

      List<C> configurations = (List<C>)myDetectedConfigurations.get(myFacetType.getId());
      C newConfiguration = myDetector.detectFacet(file, configurations);
      if (newConfiguration == null || configurations.contains(newConfiguration)) {
        return;
      }

      FacetInfo facetInfo = new FacetInfo(myFacetType, generateFacetName(), newConfiguration, underlyingFacet);
      configurations.add(newConfiguration);
      myDetectedFacets.put(newConfiguration, new DetectedInWizardFacetInfo(facetInfo, file, myDetector));
    }

    private String generateFacetName() {
      String baseName = myFacetType.getDefaultFacetName();

      String name = baseName;
      int i = 2;
      while (isUsed(name)) {
        name = baseName + i;
        i++;
      }

      return name;
    }

    private boolean isUsed(final String name) {
      List<FacetConfiguration> configurations = myDetectedConfigurations.get(myFacetType.getId());
      if (configurations != null) {
        for (FacetConfiguration configuration : configurations) {
          if (name.equals(myDetectedFacets.get(configuration).getFacetInfo().getName())) {
            return true;
          }
        }
      }
      return false;
    }

  }

  private class MyFacetDetectorRegistry<C extends FacetConfiguration> implements FacetDetectorForWizardRegistry<C> {
    private final FacetType<?, C> myFacetType;
    private final int myLevel;

    public MyFacetDetectorRegistry(final FacetType<?, C> facetType) {
      myFacetType = facetType;
      int level = 0;
      FacetTypeId<?> typeId = facetType.getUnderlyingFacetType();
      Set<FacetTypeId> parentTypes = new HashSet<FacetTypeId>();
      parentTypes.add(facetType.getId());
      while (typeId != null) {
        level++;
        FacetType<?,?> underlying = FacetTypeRegistry.getInstance().findFacetType(typeId);
        LOG.assertTrue(underlying != null, "Cannot find underlying facet type by id: " + typeId + " (for facet " + facetType.getId() + ")");
        typeId = underlying.getUnderlyingFacetType();
        if (!parentTypes.add(typeId)) {
          LOG.error("Circular dependency between facets: " + parentTypes);
          break;
        }
      }
      myLevel=level;
    }

    public void register(@NotNull final FileType fileType, @NotNull final VirtualFileFilter virtualFileFilter,
                         @NotNull final FacetDetector<VirtualFile, C> facetDetector) {
      LOG.assertTrue(myFacetType.getUnderlyingFacetType() == null, "This method must not be used for sub-facets");
      getDetectorsMap().put(fileType, new MyFacetDetectorWrapper<C, FacetConfiguration>(myFacetType, fileType, virtualFileFilter,
                                                                                        facetDetector, null));
    }

    private MultiValuesMap<FileType, MyFacetDetectorWrapper> getDetectorsMap() {
      while (myLevel >= myDetectors.size()) {
        myDetectors.add(new MultiValuesMap<FileType, MyFacetDetectorWrapper>());
      }
      return myDetectors.get(myLevel);
    }

    public <U extends FacetConfiguration> void register(final FileType fileType, @NotNull final VirtualFileFilter virtualFileFilter, final FacetDetector<VirtualFile, C> facetDetector,
                                                        final UnderlyingFacetSelector<VirtualFile, U> underlyingFacetSelector) {
      LOG.assertTrue(myFacetType.getUnderlyingFacetType() != null, "This method can be used only for sub-facets");
      MyFacetDetectorWrapper<C, U> detector = new MyFacetDetectorWrapper<C, U>(myFacetType, fileType, virtualFileFilter,
                                                                               facetDetector, underlyingFacetSelector);
      getDetectorsMap() .put(fileType, detector);
    }
  }

  //todo[nik] use DetectedFacetInfo instead
  public static class DetectedInWizardFacetInfo {
    private final FacetInfo myFacetInfo;
    private final VirtualFile myFile;
    private final FacetDetector myFacetDetector;

    public DetectedInWizardFacetInfo(final FacetInfo facetInfo, final VirtualFile file, final FacetDetector facetDetector) {
      myFacetInfo = facetInfo;
      myFile = file;
      myFacetDetector = facetDetector;
    }

    public FacetInfo getFacetInfo() {
      return myFacetInfo;
    }

    public VirtualFile getFile() {
      return myFile;
    }

    public FacetDetector getFacetDetector() {
      return myFacetDetector;
    }
  }
}
