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
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.project.Project;
import org.jetbrains.idea.maven.embedder.MavenConsole;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

import java.util.Collection;
import java.util.List;

public class MavenProjectsProcessorArtifactsDownloadingTask implements MavenProjectsProcessorTask {
  private final Collection<MavenProject> myProjects;
  private final Collection<MavenArtifact> myArtifacts;
  private final MavenProjectsTree myTree;
  private final boolean myDownloadSources;
  private final boolean myDownloadDocs;

  public MavenProjectsProcessorArtifactsDownloadingTask(Collection<MavenProject> projects,
                                                        Collection<MavenArtifact> artifacts, 
                                                        MavenProjectsTree tree,
                                                        boolean downloadSources,
                                                        boolean downloadDocs) {
    myProjects = projects;
    myArtifacts = artifacts;
    myTree = tree;
    myDownloadSources = downloadSources;
    myDownloadDocs = downloadDocs;
  }

  public void perform(Project project, MavenEmbeddersManager embeddersManager, MavenConsole console, MavenProgressIndicator indicator)
    throws MavenProcessCanceledException {
    myTree.downloadArtifacts(myProjects, myArtifacts, myDownloadSources, myDownloadDocs, embeddersManager, console, indicator);
  }
}