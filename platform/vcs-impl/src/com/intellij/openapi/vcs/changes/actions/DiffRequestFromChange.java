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
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.util.BeforeAfter;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author irengrig
 *         Date: 6/10/11
 *         Time: 6:39 PM
 */
public interface DiffRequestFromChange<T extends DiffContent> {
  @Nullable
  List<BeforeAfter<T>> createRequestForChange(final Change change, int extraLines) throws VcsException;
}
