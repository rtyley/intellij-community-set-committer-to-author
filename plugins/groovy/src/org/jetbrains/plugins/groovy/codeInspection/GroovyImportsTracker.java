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
package org.jetbrains.plugins.groovy.codeInspection;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.editor.GroovyImportOptimizer;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;

import java.util.*;

/**
 * @author ven
 */
public class GroovyImportsTracker {

  private final Map<GroovyFile, Set<GrImportStatement>> myUsedImportStatements = new HashMap<GroovyFile, Set<GrImportStatement>>();
  private final Map<GroovyFile, Set<GrImportStatement>> myUnusedImportStatements = new HashMap<GroovyFile, Set<GrImportStatement>>();

  public synchronized void registerImportUsed(GrImportStatement importStatement) {
    if (importStatement.getParent() == null) return;
    PsiFile file = importStatement.getContainingFile();
    if (file == null || !(file instanceof GroovyFile)) return;
    GroovyFile groovyFile = (GroovyFile) file;
    Set<GrImportStatement> importInfos = myUsedImportStatements.get(groovyFile);
    if (importInfos == null) {
      importInfos = new HashSet<GrImportStatement>();
    }
    importInfos.add(importStatement);
    myUsedImportStatements.put(groovyFile, importInfos);
  }

  @NotNull
  public synchronized Set<GrImportStatement> getUnusedImportStatements(GroovyFile file) {
    Set<GrImportStatement> unused = myUnusedImportStatements.get(file);
    if (unused == null) {
      Set<GrImportStatement> used = myUsedImportStatements.get(file);
      unused = new HashSet<GrImportStatement>(GroovyImportOptimizer.getValidImportStatements(file));
      if (used != null && used.size() > 0) {
        unused.removeAll(used);
      }

      myUnusedImportStatements.put(file, unused);
      myUsedImportStatements.remove(file);
    }
    return unused;
  }

  public static GroovyImportsTracker getInstance(Project project) {
    return ServiceManager.getService(project, GroovyImportsTracker.class);
  }

  public synchronized void markFileAnnotated(GroovyFile file) {
    myUnusedImportStatements.remove(file);
  }
}
