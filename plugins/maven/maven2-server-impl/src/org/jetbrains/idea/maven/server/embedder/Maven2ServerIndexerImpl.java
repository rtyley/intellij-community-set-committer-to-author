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
package org.jetbrains.idea.maven.server.embedder;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.text.StringUtil;
import gnu.trove.THashSet;
import gnu.trove.TIntObjectHashMap;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.maven.archetype.catalog.Archetype;
import org.apache.maven.archetype.catalog.ArchetypeCatalog;
import org.apache.maven.archetype.source.ArchetypeDataSource;
import org.apache.maven.archetype.source.ArchetypeDataSourceException;
import org.apache.maven.artifact.manager.WagonManager;
import org.apache.maven.wagon.events.TransferEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenArchetype;
import org.jetbrains.idea.maven.model.MavenArtifactInfo;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.server.*;
import org.sonatype.nexus.index.*;
import org.sonatype.nexus.index.context.IndexUtils;
import org.sonatype.nexus.index.context.IndexingContext;
import org.sonatype.nexus.index.updater.IndexUpdateRequest;
import org.sonatype.nexus.index.updater.IndexUpdater;

import java.io.File;
import java.lang.reflect.Method;
import java.rmi.RemoteException;
import java.util.*;

public class Maven2ServerIndexerImpl extends MavenRemoteObject implements MavenServerIndexer {
  private Maven2ServerEmbedderImpl myEmbedder;
  private final NexusIndexer myIndexer;
  private final IndexUpdater myUpdater;
  private final ArtifactContextProducer myArtifactContextProducer;

  private final TIntObjectHashMap<IndexingContext> myIndices = new TIntObjectHashMap<IndexingContext>();

  public Maven2ServerIndexerImpl() throws RemoteException {
    myEmbedder = Maven2ServerEmbedderImpl.create(new MavenServerSettings());

    myIndexer = myEmbedder.getComponent(NexusIndexer.class);
    myUpdater = myEmbedder.getComponent(IndexUpdater.class);
    myArtifactContextProducer = myEmbedder.getComponent(ArtifactContextProducer.class);

    ShutDownTracker.getInstance().registerShutdownTask(new Runnable() {
      @Override
      public void run() {
        release();
      }
    });
  }

  public int createIndex(@NotNull String indexId,
                         @Nullable String repositoryId,
                         @Nullable File file,
                         @Nullable String url,
                         @NotNull File indexDir) throws MavenServerIndexerException {
    try {
      IndexingContext context = myIndexer.addIndexingContextForced(indexId,
                                                                   repositoryId,
                                                                   file,
                                                                   indexDir,
                                                                   url,
                                                                   null, // repo update url
                                                                   NexusIndexer.FULL_INDEX);
      int id = System.identityHashCode(context);
      myIndices.put(id, context);
      return id;
    }
    catch (Exception e) {
      throw new MavenServerIndexerException(wrapException(e));
    }
  }

  public void releaseIndex(int id) throws MavenServerIndexerException {
    try {
      myIndexer.removeIndexingContext(getIndex(id), false);
    }
    catch (Exception e) {
      throw new MavenServerIndexerException(wrapException(e));
    }
  }

  @NotNull
  private IndexingContext getIndex(int id) {
    IndexingContext index = myIndices.get(id);
    if (index == null) throw new RuntimeException("Index not found for id: " + id);
    return index;
  }

  private String getRepositoryPathOrUrl(IndexingContext index) {
    File file = index.getRepository();
    return file == null ? index.getRepositoryUrl() : file.getPath();
  }

  private boolean isLocal(IndexingContext index) {
    return index.getRepository() != null;
  }

  public int getIndexCount() {
    return myIndexer.getIndexingContexts().size();
  }

  public void updateIndex(int id, MavenServerSettings settings, MavenServerProgressIndicator indicator) throws
                                                                                                        MavenServerIndexerException,
                                                                                                        MavenServerProcessCanceledException,
                                                                                                        RemoteException {
    IndexingContext index = getIndex(id);

    try {
      if (isLocal(index)) {
        indicator.setIndeterminate(true);
        try {
          myIndexer.scan(index, new MyScanningListener(indicator), false);
        }
        finally {
          indicator.setIndeterminate(false);
        }
      }
      else {
        IndexUpdateRequest request = new IndexUpdateRequest(index);
        Maven2ServerEmbedderImpl embedder = Maven2ServerEmbedderImpl.create(settings);
        try {
          request.setResourceFetcher(new Maven2ServerIndexFetcher(index.getRepositoryId(),
                                                                  index.getRepositoryUrl(),
                                                                  embedder.getComponent(WagonManager.class),
                                                                  new TransferListenerAdapter(indicator) {
                                                                    @Override
                                                                    protected void downloadProgress(long downloaded, long total) {
                                                                      super.downloadProgress(downloaded, total);
                                                                      try {
                                                                        myIndicator.setFraction(((double)downloaded) / total);
                                                                      }
                                                                      catch (RemoteException e) {
                                                                        throw new RuntimeRemoteException(e);
                                                                      }
                                                                    }

                                                                    @Override
                                                                    public void transferCompleted(TransferEvent event) {
                                                                      super.transferCompleted(event);
                                                                      try {
                                                                        myIndicator.setText2("Processing indices...");
                                                                      }
                                                                      catch (RemoteException e) {
                                                                        throw new RuntimeRemoteException(e);
                                                                      }
                                                                    }
                                                                  }));
          myUpdater.fetchAndUpdateIndex(request);
        }
        finally {
          embedder.release();
        }
      }
    }
    catch (RuntimeRemoteException e) {
      throw e.getCause();
    }
    catch (ProcessCanceledException e) {
      throw new MavenServerProcessCanceledException();
    }
    catch (Exception e) {
      throw new MavenServerIndexerException(wrapException(e));
    }
  }

  public List<MavenId> getAllArtifacts(int indexId) throws MavenServerIndexerException {
    try {
      List<MavenId> result = new ArrayList<MavenId>();
      IndexReader r = getIndex(indexId).getIndexReader();
      int total = r.numDocs();
      for (int i = 0; i < total; i++) {
        if (r.isDeleted(i)) continue;

        Document doc = r.document(i);
        String uinfo = doc.get(SEARCH_TERM_COORDINATES);
        if (uinfo == null) continue;
        List<String> parts = StringUtil.split(uinfo, "|");
        String groupId = parts.get(0);
        String artifactId = parts.get(1);
        String version = parts.get(2);
        if (groupId == null || artifactId == null || version == null) continue;

        result.add(new MavenId(groupId, artifactId, version));
      }
      return result;
    }
    catch (Exception e) {
      throw new MavenServerIndexerException(wrapException(e));
    }
  }

  public MavenId addArtifact(int indexId, File artifactFile) throws MavenServerIndexerException {
    try {
      IndexingContext index = getIndex(indexId);
      ArtifactContext artifactContext = myArtifactContextProducer.getArtifactContext(index, artifactFile);
      if (artifactContext == null) return null;

      myIndexer.addArtifactToIndex(artifactContext, index);
      // this hack is necessary to invalidate searcher's and reader's cache (may not be required then lucene or nexus library change
      Method m = index.getClass().getDeclaredMethod("closeReaders");
      m.setAccessible(true);
      m.invoke(index);

      org.sonatype.nexus.index.ArtifactInfo a = artifactContext.getArtifactInfo();
      return new MavenId(a.groupId, a.artifactId, a.version);
    }
    catch (Exception e) {
      throw new MavenServerIndexerException(wrapException(e));
    }
  }

  public Set<MavenArtifactInfo> search(int indexId, Query query, int maxResult) throws MavenServerIndexerException {
    try {
      IndexingContext index = getIndex(indexId);

      TopDocs docs = null;
      try {
        BooleanQuery.setMaxClauseCount(Integer.MAX_VALUE);
        docs = index.getIndexSearcher().search(query, null, maxResult);
      }
      catch (BooleanQuery.TooManyClauses ignore) {
        // this exception occurs when too wide wildcard is used on too big data.
      }

      if (docs == null || docs.scoreDocs.length == 0) return Collections.emptySet();

      Set<MavenArtifactInfo> result = new THashSet<MavenArtifactInfo>();

      for (int i = 0; i < docs.scoreDocs.length; i++) {
        int docIndex = docs.scoreDocs[i].doc;
        Document doc = index.getIndexReader().document(docIndex);
        ArtifactInfo a = IndexUtils.constructArtifactInfo(doc, index);
        if (a == null) continue;

        a.repository = getRepositoryPathOrUrl(index);
        result.add(Maven2ModelConverter.convertArtifactInfo(a));
      }
      return result;
    }
    catch (Exception e) {
      throw new MavenServerIndexerException(wrapException(e));
    }
  }

  public Collection<MavenArchetype> getArchetypes() throws RemoteException {
    Set<MavenArchetype> result = new THashSet<MavenArchetype>();
    doCollectArchetypes("internal-catalog", result);
    doCollectArchetypes("nexus", result);
    return result;
  }

  private void doCollectArchetypes(String roleHint, Set<MavenArchetype> result) throws RemoteException {
    try {
      ArchetypeDataSource source = myEmbedder.getComponent(ArchetypeDataSource.class, roleHint);
      ArchetypeCatalog catalog = source.getArchetypeCatalog(new Properties());

      for (Archetype each : (Iterable<? extends Archetype>)catalog.getArchetypes()) {
        result.add(Maven2ModelConverter.convertArchetype(each));
      }
    }
    catch (ArchetypeDataSourceException e) {
      Maven2ServerGlobals.getLogger().warn(e);
    }
  }

  public void release() {
    try {
      myEmbedder.release();
    }
    catch (Exception e) {
      throw rethrowException(e);
    }
  }

  private static class MyScanningListener implements ArtifactScanningListener {
    private final MavenServerProgressIndicator p;

    public MyScanningListener(MavenServerProgressIndicator indicator) {
      p = indicator;
    }

    public void scanningStarted(IndexingContext ctx) {
      try {
        if (p.isCanceled()) throw new ProcessCanceledException();
      }
      catch (RemoteException e) {
        throw new RuntimeRemoteException(e);
      }
    }

    public void scanningFinished(IndexingContext ctx, ScanningResult result) {
      try {
        if (p.isCanceled()) throw new ProcessCanceledException();
      }
      catch (RemoteException e) {
        throw new RuntimeRemoteException(e);
      }
    }

    public void artifactError(ArtifactContext ac, Exception e) {
    }

    public void artifactDiscovered(ArtifactContext ac) {
      try {
        if (p.isCanceled()) throw new ProcessCanceledException();
        ArtifactInfo info = ac.getArtifactInfo();
        p.setText2(info.groupId + ":" + info.artifactId + ":" + info.version);
      }
      catch (RemoteException e) {
        throw new RuntimeRemoteException(e);
      }
    }
  }
}
