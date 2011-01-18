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

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.SortedList;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * @author nik
*/
@Tag("facet-type")
public class DisabledAutodetectionByTypeElement {
  public static final Comparator<DisabledAutodetectionByTypeElement> COMPARATOR = new Comparator<DisabledAutodetectionByTypeElement>() {
    @Override
    public int compare(DisabledAutodetectionByTypeElement o1, DisabledAutodetectionByTypeElement o2) {
      return StringUtil.compare(o1.getFacetTypeId(), o2.getFacetTypeId(), true);
    }
  };
  private String myFacetTypeId;
  private List<DisabledAutodetectionInModuleElement> myModuleElements = new SortedList<DisabledAutodetectionInModuleElement>(DisabledAutodetectionInModuleElement.COMPARATOR);
  
  public DisabledAutodetectionByTypeElement() {
  }

  public DisabledAutodetectionByTypeElement(final String facetTypeId) {
    myFacetTypeId = facetTypeId;
  }

  public DisabledAutodetectionByTypeElement(String facetTypeId, String moduleName) {
    this(facetTypeId);
    myModuleElements.add(new DisabledAutodetectionInModuleElement(moduleName));
  }

  public DisabledAutodetectionByTypeElement(String facetTypeId, String moduleName, String url, final boolean recursively) {
    this(facetTypeId);
    myModuleElements.add(new DisabledAutodetectionInModuleElement(moduleName, url, recursively));
  }

  @Attribute("id")
  public String getFacetTypeId() {
    return myFacetTypeId;
  }

  @Tag("modules")
  @AbstractCollection(surroundWithTag = false)
  public List<DisabledAutodetectionInModuleElement> getModuleElements() {
    return myModuleElements;
  }

  public void setFacetTypeId(final String facetTypeId) {
    myFacetTypeId = facetTypeId;
  }

  public void setModuleElements(final List<DisabledAutodetectionInModuleElement> moduleElements) {
    myModuleElements = moduleElements;
  }

  public void addDisabled(@NotNull String moduleName) {
    if (myModuleElements.isEmpty()) return;

    DisabledAutodetectionInModuleElement element = findElement(moduleName);
    if (element != null) {
      element.getFiles().clear();
      element.getDirectories().clear();
      return;
    }

    myModuleElements.add(new DisabledAutodetectionInModuleElement(moduleName));
  }

  public void disableInProject() {
    myModuleElements.clear();
  }

  public void addDisabled(@NotNull String moduleName, @NotNull String fileUrl, final boolean recursively) {
    if (myModuleElements.isEmpty()) return;

    DisabledAutodetectionInModuleElement element = findElement(moduleName);
    if (element != null) {
      if (!element.isDisableInWholeModule()) {
        if (recursively) {
          element.getDirectories().add(fileUrl);
        }
        else {
          element.getFiles().add(fileUrl);
        }
      }
      return;
    }
    myModuleElements.add(new DisabledAutodetectionInModuleElement(moduleName, fileUrl, recursively));
  }

  @Nullable
  public DisabledAutodetectionInModuleElement findElement(final @NotNull String moduleName) {
    for (DisabledAutodetectionInModuleElement element : myModuleElements) {
      if (moduleName.equals(element.getModuleName())) {
        return element;
      }
    }
    return null;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final DisabledAutodetectionByTypeElement that = (DisabledAutodetectionByTypeElement)o;
    return myFacetTypeId.equals(that.myFacetTypeId) && myModuleElements.equals(that.myModuleElements);

  }

  public int hashCode() {
    return myFacetTypeId.hashCode()+ 31 * myModuleElements.hashCode();
  }

  public boolean isDisabled(final String moduleName, final String url) {
    if (myModuleElements.isEmpty()) return true;

    DisabledAutodetectionInModuleElement element = findElement(moduleName);
    if (element == null) return false;

    if (element.isDisableInWholeModule() || element.getFiles().contains(url)) {
      return true;
    }
    for (String directoryUrl : element.getDirectories()) {
      if (!directoryUrl.endsWith("/")) {
        directoryUrl += "/";
      }
      if (url.startsWith(directoryUrl) || !SystemInfo.isFileSystemCaseSensitive && StringUtil.startsWithIgnoreCase(url, directoryUrl)) {
        return true;
      }
    }
    return false;
  }

  public boolean removeDisabled(final String moduleName) {
    Iterator<DisabledAutodetectionInModuleElement> iterator = myModuleElements.iterator();
    while (iterator.hasNext()) {
      DisabledAutodetectionInModuleElement element = iterator.next();
      if (element.getModuleName().equals(moduleName)) {
        iterator.remove();
        break;
      }
    }
    return myModuleElements.size() > 0;
  }
}
