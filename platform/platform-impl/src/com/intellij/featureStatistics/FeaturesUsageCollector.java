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
package com.intellij.featureStatistics;

import com.intellij.openapi.project.Project;
import com.intellij.internal.statistic.UsagesCollector;
import com.intellij.internal.statistic.beans.*;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class FeaturesUsageCollector extends UsagesCollector {

  @NotNull
  @Override
  public String getGroupId() {
    return "productivity";
  }

  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages(@Nullable Project project) {
    Set<UsageDescriptor> usages = new HashSet<UsageDescriptor>();

    final FeatureUsageTracker usageTracker = FeatureUsageTracker.getInstance(); //

    final ProductivityFeaturesRegistry registry = ProductivityFeaturesRegistry.getInstance();
    for (String featureId : registry.getFeatureIds()) {
      final FeatureDescriptor featureDescriptor = registry.getFeatureDescriptor(featureId);
      if (featureDescriptor != null) {
        usages.add(new UsageDescriptor(
           GroupDescriptor.create(getGroupId(),  GroupDescriptor.LOWER_PRIORITY), featureId, featureDescriptor.getUsageCount()));
      }
    }

    return usages;
  }
}
