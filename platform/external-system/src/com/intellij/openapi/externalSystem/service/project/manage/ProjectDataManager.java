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
package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.openapi.diagnostic.Logger;
<<<<<<< HEAD
import com.intellij.openapi.extensions.ExtensionPointName;
=======
>>>>>>> 5fd2c47... IDEA-104500 Gradle: Allow to reuse common logic for other external systems
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Aggregates all {@link ProjectDataService#EP_NAME registered data services} and provides entry points for project data management.
 * 
 * @author Denis Zhdanov
 * @since 4/16/13 11:38 AM
 */
public class ProjectDataManager {

  private static final Logger LOG = Logger.getInstance("#" + ProjectDataManager.class.getName());

  @NotNull private final NotNullLazyValue<Map<Key<?>, List<ProjectDataService<?>>>> myServices =
    new NotNullLazyValue<Map<Key<?>, List<ProjectDataService<?>>>>() {
      @NotNull
      @Override
      protected Map<Key<?>, List<ProjectDataService<?>>> compute() {
        Map<Key<?>, List<ProjectDataService<?>>> result = ContainerUtilRt.newHashMap();
        for (ProjectDataService<?> service : ProjectDataService.EP_NAME.getExtensions()) {
          List<ProjectDataService<?>> services = result.get(service.getTargetDataKey());
          if (services == null) {
            result.put(service.getTargetDataKey(), services = ContainerUtilRt.newArrayList());
          }
          services.add(service);
        }

        for (List<ProjectDataService<?>> services : result.values()) {
          ExternalSystemUtil.orderAwareSort(services);
        }
<<<<<<< HEAD

        return result;
      }
    };

  @SuppressWarnings("unchecked")
  public <T> void importData(@NotNull Collection<DataNode<?>> nodes, @NotNull Project project, boolean synchronous) {
    Map<Key<?>, Collection<DataNode<?>>> grouped = ExternalSystemUtil.group(nodes);
    for (Map.Entry<Key<?>, Collection<DataNode<?>>> entry : grouped.entrySet()) {
      // Simple class cast makes ide happy but compiler fails.
      Collection<DataNode<T>> dummy = ContainerUtilRt.newArrayList();
      for (DataNode<?> node : entry.getValue()) {
        dummy.add((DataNode<T>)node);
      }
      importData((Key<T>)entry.getKey(), dummy, project, synchronous);
    }
  }

  @SuppressWarnings("unchecked")
  public <T> void importData(@NotNull Key<T> key, @NotNull Collection<DataNode<T>> nodes, @NotNull Project project, boolean synchronous) {
    List<ProjectDataService<?>> services = myServices.getValue().get(key);
    if (services == null) {
      LOG.warn(String.format(
        "Can't import data nodes '%s'. Reason: no service is registered for key %s. Available services for %s",
        nodes, key, myServices.getValue().keySet()
      ));
    }
    else {
      for (ProjectDataService<?> service : services) {
        ((ProjectDataService<T>)service).importData(nodes, project, synchronous);
      }
    }

    Collection<DataNode<?>> children = ContainerUtilRt.newArrayList();
    for (DataNode<T> node : nodes) {
      children.addAll(node.getChildren());
    }
    importData(children, project, synchronous);
  }
  void importData(@NotNull Collection<DataNode<T>> toImport, @NotNull Project project, boolean synchronous);
=======
>>>>>>> 5fd2c47... IDEA-104500 Gradle: Allow to reuse common logic for other external systems

        return result;
      }
    };

  @SuppressWarnings("unchecked")
  public <T> void importData(@NotNull Collection<DataNode<?>> nodes, @NotNull Project project, boolean synchronous) {
    Map<Key<?>, Collection<DataNode<?>>> grouped = ExternalSystemUtil.group(nodes);
    for (Map.Entry<Key<?>, Collection<DataNode<?>>> entry : grouped.entrySet()) {
      // Simple class cast makes ide happy but compiler fails.
      Collection<DataNode<T>> dummy = ContainerUtilRt.newArrayList();
      for (DataNode<?> node : entry.getValue()) {
        dummy.add((DataNode<T>)node);
      }
      importData((Key<T>)entry.getKey(), dummy, project, synchronous);
    }
  }

  @SuppressWarnings("unchecked")
  public <T> void importData(@NotNull Key<T> key, @NotNull Collection<DataNode<T>> nodes, @NotNull Project project, boolean synchronous) {
    List<ProjectDataService<?>> services = myServices.getValue().get(key);
    if (services == null) {
      LOG.warn(String.format(
        "Can't import data nodes '%s'. Reason: no service is registered for key %s. Available services for %s",
        nodes, key, myServices.getValue().keySet()
      ));
    }
    else {
      for (ProjectDataService<?> service : services) {
        ((ProjectDataService<T>)service).importData(nodes, project, synchronous);
      }
    }

    Collection<DataNode<?>> children = ContainerUtilRt.newArrayList();
    for (DataNode<T> node : nodes) {
      children.addAll(node.getChildren());
    }
    importData(children, project, synchronous);
  }
}
