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
package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.embedder.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.sonatype.nexus.index.ArtifactContextProducer;
import org.sonatype.nexus.index.NexusIndexer;
import org.sonatype.nexus.index.updater.IndexUpdater;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MavenIndices {
  private final MavenEmbedderWrapper myEmbedder;
  private final NexusIndexer myIndexer;
  private final IndexUpdater myUpdater;
  private final ArtifactContextProducer myArtifactContextProducer;

  private final File myIndicesDir;
  private final MavenIndex.IndexListener myListener;

  private final List<MavenIndex> myIndices = new ArrayList<MavenIndex>();
  private static final Object ourDirectoryLock = new Object();

  public MavenIndices(MavenEmbedderWrapper embedder, File indicesDir, MavenIndex.IndexListener listener) {
    myEmbedder = embedder;
    myIndicesDir = indicesDir;
    myListener = listener;

    myIndexer = myEmbedder.getComponent(NexusIndexer.class);
    myUpdater = myEmbedder.getComponent(IndexUpdater.class);
    myArtifactContextProducer = myEmbedder.getComponent(ArtifactContextProducer.class);

    load();
  }

  private void load() {
    if (!myIndicesDir.exists()) return;

    File[] indices = myIndicesDir.listFiles();
    if (indices == null) return;
    for (File each : indices) {
      if (!each.isDirectory()) continue;

      try {
        MavenIndex index = new MavenIndex(myIndexer, myArtifactContextProducer, each, myListener);
        if (find(index.getRepositoryId(), index.getRepositoryPathOrUrl(), index.getKind()) != null) {
          index.close();
          FileUtil.delete(each);
          continue;
        }
        myIndices.add(index);
      }
      catch (Exception e) {
        FileUtil.delete(each);
        MavenLog.LOG.warn(e);
      }
    }
  }

  @TestOnly
  public NexusIndexer getIndexer() {
    return myIndexer;
  }

  public synchronized void close() {
    for (MavenIndex each : myIndices) {
      each.close();
    }
    myIndices.clear();
  }

  public synchronized List<MavenIndex> getIndices() {
    return new ArrayList<MavenIndex>(myIndices);
  }

  public synchronized MavenIndex add(String repositoryId, String repositoryPathOrUrl, MavenIndex.Kind kind) throws MavenIndexException {
    MavenIndex index = find(repositoryId, repositoryPathOrUrl, kind);
    if (index != null) return index;

    File dir = getAvailableIndexDir();
    index = new MavenIndex(myIndexer, myArtifactContextProducer, dir, repositoryId, repositoryPathOrUrl, kind, myListener);
    myIndices.add(index);
    return index;
  }

  public MavenIndex find(String repositoryId, String repositoryPathOrUrl, MavenIndex.Kind kind) {
    File file = kind == MavenIndex.Kind.LOCAL ? new File(repositoryPathOrUrl.trim()) : null;
    for (MavenIndex each : myIndices) {
      switch (kind) {
        case LOCAL:
          if (each.isForLocal(repositoryId, file)) return each;
          break;
        case REMOTE:
          if (each.isForRemote(repositoryId, repositoryPathOrUrl)) return each;
          break;
      }
    }
    return null;
  }

  private File getAvailableIndexDir() {
    return findAvailableDir(myIndicesDir, "Index", 1000);
  }

  static File findAvailableDir(File parent, String prefix, int max) {
    synchronized (ourDirectoryLock) {
      for (int i = 0; i < max; i++) {
        String name = prefix + i;
        File f = new File(parent, name);
        if (!f.exists()) {
          f.mkdirs();
          assert f.exists();
          return f;
        }
      }
      throw new RuntimeException("No available dir found");
    }
  }

  public void updateOrRepair(MavenIndex index,
                             MavenEmbedderWrapper embedderToUse,
                             boolean fullUpdate,
                             ProgressIndicator progress)throws ProcessCanceledException {
    index.updateOrRepair(embedderToUse, myUpdater, fullUpdate, progress);
  }
}