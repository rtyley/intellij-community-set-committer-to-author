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

package com.intellij.internal.statistic.persistence;

import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.PatchedUsage;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;

public abstract class SentUsagesPersistence {

  public abstract void persistPatch(@NotNull Map<GroupDescriptor, Set<PatchedUsage>> patchedDescriptors);

  @NotNull
  public abstract Map<GroupDescriptor, Set<UsageDescriptor>> getSentUsages();

  public abstract boolean isAllowed();

  public abstract boolean isShowNotification();

  public abstract long getLastTimeSent();


}
