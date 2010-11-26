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
package com.intellij.debugger;

import com.intellij.debugger.requests.ClassPrepareRequestor;
import com.sun.jdi.Location;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.request.ClassPrepareRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface PositionManager {
  @Nullable
  SourcePosition getSourcePosition(@Nullable Location location) throws NoDataException;

  @NotNull
  List<ReferenceType> getAllClasses(SourcePosition classPosition) throws NoDataException;

  @NotNull
  List<Location> locationsOfLine(ReferenceType type, SourcePosition position) throws NoDataException;

  @Nullable
  ClassPrepareRequest createPrepareRequest(ClassPrepareRequestor requestor, SourcePosition position) throws NoDataException;
}
