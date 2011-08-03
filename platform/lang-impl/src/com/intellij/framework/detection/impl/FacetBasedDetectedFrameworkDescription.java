/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.framework.detection.impl;

import com.intellij.facet.*;
import com.intellij.framework.FrameworkType;
import com.intellij.framework.detection.DetectedFrameworkDescription;
import com.intellij.framework.detection.FacetBasedFrameworkDetector;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableModelsProvider;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Set;

/**
 * @author nik
 */
public abstract class FacetBasedDetectedFrameworkDescription<F extends Facet, C extends FacetConfiguration> extends DetectedFrameworkDescription {
  private final FacetBasedFrameworkDetector<F, C> myDetector;
  private final C myConfiguration;
  private final Set<VirtualFile> myRelatedFiles;
  private final FacetType<F,C> myFacetType;
  private final FrameworkType myFrameworkType;

  public FacetBasedDetectedFrameworkDescription(FacetBasedFrameworkDetector<F, C> detector,
                                                @NotNull C configuration,
                                                Set<VirtualFile> files) {
    myDetector = detector;
    myConfiguration = configuration;
    myRelatedFiles = files;
    myFacetType = detector.getFacetType();
    myFrameworkType = createFrameworkType(myFacetType);
  }

  public static FrameworkType createFrameworkType(final FacetType<?, ?> facetType) {
    return new FrameworkType(facetType.getStringId(), facetType.getPresentableName(), facetType.getIcon());
  }

  @NotNull
  @Override
  public FrameworkType getFrameworkType() {
    return myFrameworkType;
  }

  @NotNull
  @Override
  public Collection<? extends VirtualFile> getRelatedFiles() {
    return myRelatedFiles;
  }

  public C getConfiguration() {
    return myConfiguration;
  }

  @NotNull
  @Override
  public String getSetupDescription() {
    return "'" + myFacetType.getPresentableName() + "' facet will be added to '" + getModuleName() + "' module";
  }

  protected abstract String getModuleName();

  protected void doConfigure(ModifiableModelsProvider modifiableModelsProvider, final Module module) {
    final ModifiableFacetModel model = modifiableModelsProvider.getFacetModifiableModel(module);
    final F facet = FacetManager.getInstance(module).createFacet(myFacetType, myFacetType.getDefaultFacetName(), myConfiguration, null);
    model.addFacet(facet);
    modifiableModelsProvider.commitFacetModifiableModel(module, model);
    myDetector.setupFacet(facet);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof FacetBasedDetectedFrameworkDescription)) {
      return false;
    }
    final FacetBasedDetectedFrameworkDescription other = (FacetBasedDetectedFrameworkDescription)obj;
    return getModuleName().equals(other.getModuleName()) && myFacetType.equals(other.myFacetType) && myRelatedFiles.equals(other.myRelatedFiles);
  }

  @Override
  public int hashCode() {
    return getModuleName().hashCode() + 31*myFacetType.hashCode() + 239*myRelatedFiles.hashCode();
  }
}
