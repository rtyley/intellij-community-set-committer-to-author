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
package com.intellij.ide.impl.dataRules;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author Eugene Zhuravlev
 *         Date: Feb 10, 2004
 */
public class ProjectFileDirectoryRule implements GetDataRule {
  public Object getData(DataProvider dataProvider) {
    VirtualFile dir = (VirtualFile)dataProvider.getData(DataConstantsEx.PROJECT_FILE_DIRECTORY);
    if (dir == null) {
      final Project project = (Project)dataProvider.getData(DataConstants.PROJECT);
      if (project != null) {
        dir = project.getBaseDir();
      }
    }
    return dir;
  }
}
