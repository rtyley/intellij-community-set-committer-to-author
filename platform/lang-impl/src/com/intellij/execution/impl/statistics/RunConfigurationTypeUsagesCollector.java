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
package com.intellij.execution.impl.statistics;

import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.internal.statistic.AbstractApplicationUsagesCollector;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author Nikolay Matveev
 */
public class RunConfigurationTypeUsagesCollector extends AbstractApplicationUsagesCollector {

  private static final String GROUP_ID = "run-configuration-type";

  @NotNull
  @Override
  public GroupDescriptor getGroupId() {
    return GroupDescriptor.create(GROUP_ID);
  }

  @NotNull
  @Override
  public Set<UsageDescriptor> getProjectUsages(@NotNull Project project) {
    final Set<String> runConfigurationTypes = new HashSet<String>();
    final RunManager runManager = RunManager.getInstance(project);
    for (RunConfiguration runConfiguration : runManager.getAllConfigurations()) {
      if ((runConfiguration != null) && (!runManager.isTemporary(runConfiguration))) {
        final ConfigurationFactory configurationFactory = runConfiguration.getFactory();
        final ConfigurationType configurationType = configurationFactory.getType();
        final StringBuilder keyBuilder = new StringBuilder();
        keyBuilder.append(configurationType.getId());
        if (configurationType.getConfigurationFactories().length > 1) {
          keyBuilder.append(".").append(configurationFactory.getName());
        }
        runConfigurationTypes.add(keyBuilder.toString());
      }
    }
    return ContainerUtil.map2Set(runConfigurationTypes, new Function<String, UsageDescriptor>() {
      @Override
      public UsageDescriptor fun(String runConfigurationType) {
        return new UsageDescriptor(runConfigurationType, 1);
      }
    });
  }
}
