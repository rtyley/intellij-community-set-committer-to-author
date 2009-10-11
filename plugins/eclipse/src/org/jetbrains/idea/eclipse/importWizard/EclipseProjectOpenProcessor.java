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

/*
 * User: anna
 * Date: 12-Jul-2007
 */
package org.jetbrains.idea.eclipse.importWizard;

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectOpenProcessorBase;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.eclipse.EclipseXml;

import java.util.List;

public class EclipseProjectOpenProcessor extends ProjectOpenProcessorBase {
  public EclipseProjectOpenProcessor(final EclipseImportBuilder builder) {
    super(builder);
  }

  public EclipseImportBuilder getBuilder() {
    return (EclipseImportBuilder)super.getBuilder();
  }

  @Nullable
  public String[] getSupportedExtensions() {
    return new String[] {EclipseXml.CLASSPATH_FILE, EclipseXml.PROJECT_FILE};
  }

  public boolean canOpenProject(final VirtualFile file) {
    return super.canOpenProject(file) && isEclipseProject(file);
  }

  private static boolean isEclipseProject(final VirtualFile file) {
    if (file.getName().equals(EclipseXml.DOT_CLASSPATH_EXT)) return true;
    final VirtualFile dir = file.getParent();
    return dir != null && dir.findChild(EclipseXml.DOT_CLASSPATH_EXT) != null;
  }

  public boolean doQuickImport(VirtualFile file, final WizardContext wizardContext) {
    //noinspection ConstantConditions
    getBuilder().setRootDirectory(file.getParent().getPath());

    final List<String> projects = getBuilder().getList();
    if (projects.size() != 1) {
      return false;
    }
    getBuilder().setList(projects);
    wizardContext.setProjectName(EclipseProjectFinder.findProjectName(projects.get(0)));
    return true;
  }
}