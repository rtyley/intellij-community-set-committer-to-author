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
package com.intellij.ide.plugins;

import com.intellij.openapi.project.Project;
import com.intellij.statistic.UsagesCollector;
import com.intellij.statistic.beans.GroupDescriptor;
import com.intellij.statistic.beans.UsageDescriptor;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class PluginsUsagesCollector extends UsagesCollector {
  private static final String GROUP_ID = "plugins";

  public static GroupDescriptor getGroupId() {
    return GroupDescriptor.create(GROUP_ID, GroupDescriptor.HIGHER_PRIORITY);
  }

  @NotNull
  public Set<UsageDescriptor> getUsages(@Nullable Project project) {
    return ContainerUtil.map2Set(PluginManager.getPlugins(), new Function<IdeaPluginDescriptor, UsageDescriptor>() {
      @Override
      public UsageDescriptor fun(IdeaPluginDescriptor descriptor) {
        return new UsageDescriptor(getGroupId(), descriptor.getName(), 1);
      }
    });
  }
}
