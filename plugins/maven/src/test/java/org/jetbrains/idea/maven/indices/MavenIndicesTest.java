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

import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.util.io.FileUtil;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.WildcardQuery;
import org.jetbrains.idea.maven.embedder.MavenEmbedderFactory;
import org.jetbrains.idea.maven.embedder.MavenEmbedderWrapper;
import org.sonatype.nexus.index.ArtifactInfo;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MavenIndicesTest extends MavenIndicesTestCase {
  private MavenCustomRepositoryHelper myRepositoryHelper;
  private MavenIndices myIndices;
  private MavenEmbedderWrapper myEmbedder;
  private File myIndicesDir;
  private boolean isBroken;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myRepositoryHelper = new MavenCustomRepositoryHelper(myDir, "local1", "local2", "remote");
    initIndices();
  }

  @Override
  protected void tearDown() throws Exception {
    shutdownIndices();
    super.tearDown();
  }

  private void initIndices() throws Exception {
    initIndices("indices");
  }

  private void initIndices(String relativeDir) {
    myEmbedder = MavenEmbedderFactory.createEmbedder(getMavenGeneralSettings());
    myIndicesDir = new File(myDir, relativeDir);
    myIndices = new MavenIndices(myEmbedder, myIndicesDir, new MavenIndex.IndexListener() {
      public void indexIsBroken(MavenIndex index) {
        isBroken = true;
      }
    });
  }

  private void shutdownIndices() throws Exception {
    myIndices.close();
    myEmbedder.release();
  }

  public void testCreatingAndUpdatingLocal() throws Exception {
    MavenIndex i = myIndices.add("id", myRepositoryHelper.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    myIndices.updateOrRepair(i, myEmbedder, true, new EmptyProgressIndicator());

    assertUnorderedElementsAreEqual(i.getGroupIds(), "junit");
  }

  public void testCreatingSeveral() throws Exception {
    MavenIndex i1 = myIndices.add("id1", myRepositoryHelper.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    MavenIndex i2 = myIndices.add("id2", myRepositoryHelper.getTestDataPath("local2"), MavenIndex.Kind.LOCAL);
    myIndices.updateOrRepair(i1, myEmbedder, true, new EmptyProgressIndicator());
    myIndices.updateOrRepair(i2, myEmbedder, true, new EmptyProgressIndicator());

    assertUnorderedElementsAreEqual(i1.getGroupIds(), "junit");
    assertUnorderedElementsAreEqual(i2.getGroupIds(), "jmock");
  }

  public void testCreatingSeveralWithSameIdAndDifferentUrl() throws Exception {
    MavenIndex i1 = myIndices.add("id", myRepositoryHelper.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    MavenIndex i2 = myIndices.add("id", myRepositoryHelper.getTestDataPath("local2"), MavenIndex.Kind.LOCAL);
    assertNotSame(i1, i2);
    myIndices.updateOrRepair(i1, myEmbedder, true, new EmptyProgressIndicator());
    myIndices.updateOrRepair(i2, myEmbedder, true, new EmptyProgressIndicator());

    assertUnorderedElementsAreEqual(i1.getGroupIds(), "junit");
    assertUnorderedElementsAreEqual(i2.getGroupIds(), "jmock");
  }

  public void testCreatingSeveralWithDifferentIdAndSameUrl() throws Exception {
    MavenIndex i1 = myIndices.add("id1", myRepositoryHelper.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    MavenIndex i2 = myIndices.add("id2", myRepositoryHelper.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    assertNotSame(i1, i2);
    myIndices.updateOrRepair(i1, myEmbedder, true, new EmptyProgressIndicator());
    myIndices.updateOrRepair(i2, myEmbedder, true, new EmptyProgressIndicator());

    assertUnorderedElementsAreEqual(i1.getGroupIds(), "junit");
    assertUnorderedElementsAreEqual(i2.getGroupIds(), "junit");
  }

  public void testAddingWithoutUpdate() throws Exception {
    MavenIndex i = myIndices.add("id", myRepositoryHelper.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    assertTrue(i.getGroupIds().isEmpty());
  }

  public void testUpdatingLocalClearsPreviousIndex() throws Exception {
    MavenIndex i = myIndices.add("id", myRepositoryHelper.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);

    myIndices.updateOrRepair(i, myEmbedder, true, new EmptyProgressIndicator());
    assertUnorderedElementsAreEqual(i.getGroupIds(), "junit");

    myRepositoryHelper.delete("local1");
    myRepositoryHelper.copy("local2", "local1");

    myIndices.updateOrRepair(i, myEmbedder, true, new EmptyProgressIndicator());
    assertUnorderedElementsAreEqual(i.getGroupIds(), "jmock");
  }

  public void testClearingUpdateDirAfterUpdate() throws Exception {
    ignore();
    //MavenIndex i = myIndices.add("id", myRepositoryHelper.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    //
    //myIndices.updateOrRepair(i, myEmbedder, true, new EmptyProgressIndicator());
    //assertUnorderedElementsAreEqual(i.getGroupIds(), "junit");
    //
    //assertFalse(i.getUpdateDir().exists());
  }

  public void testAddingRemote() throws Exception {
    MavenIndex i = myIndices.add("id", "file:///" + myRepositoryHelper.getTestDataPath("remote"), MavenIndex.Kind.REMOTE);
    myIndices.updateOrRepair(i, myEmbedder, true, new EmptyProgressIndicator());

    assertUnorderedElementsAreEqual(i.getGroupIds(), "junit");
  }

  public void testUpdatingRemote() throws Exception {
    MavenIndex i = myIndices.add("id", "file:///" + myRepositoryHelper.getTestDataPath("remote"), MavenIndex.Kind.REMOTE);
    myIndices.updateOrRepair(i, myEmbedder, true, new EmptyProgressIndicator());

    //shouldn't throw 'The existing index is for repository [remote] and not for repository [xxx]'
    myIndices.updateOrRepair(i, myEmbedder, true, new EmptyProgressIndicator());
    assertUnorderedElementsAreEqual(i.getGroupIds(), "junit");
  }

  public void testDoNotAddSameIndexTwice() throws Exception {
    MavenIndex local = myIndices.add("local", myRepositoryHelper.getTestDataPath("foo"), MavenIndex.Kind.LOCAL);

    assertSame(local, myIndices.add("local", myRepositoryHelper.getTestDataPath("FOO"), MavenIndex.Kind.LOCAL));
    assertSame(local, myIndices.add("local", myRepositoryHelper.getTestDataPath("foo") + "/\\", MavenIndex.Kind.LOCAL));
    assertSame(local, myIndices.add("local", "  " + myRepositoryHelper.getTestDataPath("foo") + "  ", MavenIndex.Kind.LOCAL));

    MavenIndex remote = myIndices.add("remote", "http://foo.bar", MavenIndex.Kind.REMOTE);

    assertSame(remote, myIndices.add("remote", "HTTP://FOO.BAR", MavenIndex.Kind.REMOTE));
    assertSame(remote, myIndices.add("remote", "http://foo.bar/\\", MavenIndex.Kind.REMOTE));
    assertSame(remote, myIndices.add("remote", "  http://foo.bar  ", MavenIndex.Kind.REMOTE));
  }

  public void testAddingInAbsenceOfParentDirectories() throws Exception {
    String subDir = "subDir1/subDir2/index";
    initIndices(subDir);
    myIndices.add("id", myRepositoryHelper.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
  }

  public void testAddingCorrectlyToIndexer() throws Exception {
    assertEquals(0, myIndices.getIndexer().getIndexingContexts().size());

    MavenIndex i1 = myIndices.add("local", myRepositoryHelper.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    assertEquals(1, myIndices.getIndexer().getIndexingContexts().size());

    MavenIndex i2 = myIndices.add("local", myRepositoryHelper.getTestDataPath("local2"), MavenIndex.Kind.LOCAL);
    assertEquals(2, myIndices.getIndexer().getIndexingContexts().size());

    myIndices.updateOrRepair(i1, myEmbedder, true, new EmptyProgressIndicator());
    assertEquals(2, myIndices.getIndexer().getIndexingContexts().size());

    myIndices.updateOrRepair(i2, myEmbedder, true, new EmptyProgressIndicator());
    assertEquals(2, myIndices.getIndexer().getIndexingContexts().size());
  }

  public void testClearingIndexDirOnLoadError() throws Exception {
    MavenIndex i = myIndices.add("id", myRepositoryHelper.getTestDataPath("local2"), MavenIndex.Kind.LOCAL);
    shutdownIndices();

    FileWriter w = new FileWriter(new File(i.getDir(), MavenIndex.INDEX_INFO_FILE));
    w.write("bad content");
    w.close();

    initIndices();

    assertTrue(myIndices.getIndices().isEmpty());
    assertFalse(i.getDir().exists());
  }

  public void testDoNotClearAlreadyLoadedIndexesOnLoadError() throws Exception {
    myIndices.add("id1", myRepositoryHelper.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    MavenIndex i2 = myIndices.add("id2", myRepositoryHelper.getTestDataPath("local2"), MavenIndex.Kind.LOCAL);
    shutdownIndices();

    FileWriter w = new FileWriter(new File(i2.getDir(), MavenIndex.INDEX_INFO_FILE));
    w.write("bad content");
    w.close();

    initIndices();

    assertEquals(1, myIndices.getIndices().size());
    assertEquals("local1", myIndices.getIndices().get(0).getRepositoryFile().getName());
  }

  public void testLoadingIndexIfCachesAreBroken() throws Exception {
    MavenIndex i1 = myIndices.add("id", myRepositoryHelper.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    MavenIndex i2 = myIndices.add("id", myRepositoryHelper.getTestDataPath("local2"), MavenIndex.Kind.LOCAL);
    myIndices.updateOrRepair(i1, myEmbedder, true, new EmptyProgressIndicator());
    myIndices.updateOrRepair(i2, myEmbedder, true, new EmptyProgressIndicator());

    assertUnorderedElementsAreEqual(i1.getGroupIds(), "junit");
    assertUnorderedElementsAreEqual(i2.getGroupIds(), "jmock");

    shutdownIndices();
    damageFile(i1, "groupIds.dat", true);
    initIndices();

    assertEquals(2, myIndices.getIndices().size());

    assertTrue(myIndices.getIndices().get(0).getGroupIds().isEmpty());
    assertUnorderedElementsAreEqual(myIndices.getIndices().get(1).getGroupIds(), "jmock");

    myIndices.updateOrRepair(myIndices.getIndices().get(0), myEmbedder, false, new EmptyProgressIndicator());
    assertUnorderedElementsAreEqual(myIndices.getIndices().get(0).getGroupIds(), "junit");
  }

  public void testDoNotLoadSameIndexTwice() throws Exception {
    MavenIndex index = myIndices.add("id", myRepositoryHelper.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    File dir = index.getDir();
    shutdownIndices();

    File copy = new File(dir.getParentFile(), "INDEX_COPY");
    FileUtil.copyDir(dir, copy);

    initIndices();

    assertEquals(1, myIndices.getIndices().size());
    assertFalse(copy.exists());
  }

  public void testAddingIndexWithExistingDirectoryDoesNotThrowException() throws Exception {
    MavenIndex i = myIndices.add("id", myRepositoryHelper.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    myIndices.updateOrRepair(i, myEmbedder, true, new EmptyProgressIndicator());

    shutdownIndices();

    initIndices();
    i = myIndices.add("id", myRepositoryHelper.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    myIndices.updateOrRepair(i, myEmbedder, true, new EmptyProgressIndicator());
  }

  public void testSavingFailureMessage() throws Exception {
    MavenIndex i = myIndices.add("id", "xxx", MavenIndex.Kind.REMOTE);
    myIndices.updateOrRepair(i, myEmbedder, true, new EmptyProgressIndicator());

    String message = i.getFailureMessage();
    assertNotNull(message);

    shutdownIndices();
    initIndices();

    assertEquals(message, myIndices.getIndices().get(0).getFailureMessage());
  }

  public void testRepairingIndicesOnReadError() throws Exception {
    MavenIndex index = myIndices.add("id", myRepositoryHelper.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    myIndices.updateOrRepair(index, myEmbedder, true, new EmptyProgressIndicator());

    shutdownIndices();
    damageFile(index, "groupIds.dat", false);
    initIndices();

    index = myIndices.getIndices().get(0);

    index.getGroupIds();
    assertTrue(isBroken);

    assertTrue(index.getGroupIds().isEmpty());

    myIndices.updateOrRepair(myIndices.getIndices().get(0), myEmbedder, false, new EmptyProgressIndicator());
    assertUnorderedElementsAreEqual(index.getGroupIds(), "junit");
  }

  public void testRepairingIndicesOnReadWhileAddingArtifact() throws Exception {
    MavenIndex index = myIndices.add("id", myRepositoryHelper.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    myIndices.updateOrRepair(index, myEmbedder, true, new EmptyProgressIndicator());

    shutdownIndices();
    damageFile(index, "artifactIds-map.dat", false);
    initIndices();

    index = myIndices.getIndices().get(0);

    index.addArtifact(null);
    assertTrue(isBroken);

    assertTrue(index.getGroupIds().isEmpty());

    myIndices.updateOrRepair(myIndices.getIndices().get(0), myEmbedder, false, new EmptyProgressIndicator());
    assertUnorderedElementsAreEqual(index.getGroupIds(), "junit");
  }

  private void damageFile(MavenIndex index, String fileName, boolean fullDamage) throws IOException {
    File cachesDir = index.getCurrentDataDir();
    File file = new File(cachesDir, fileName);
    assertTrue(file.exists());

    if (fullDamage) {
      FileWriter w = new FileWriter(file);
      w.write("bad content");
      w.close();
    }
    else {
      byte[] content = FileUtil.loadFileBytes(file);
      for (int i = content.length / 2; i < content.length; i++) {
        content[i] = -1;
      }
      FileUtil.writeToFile(file, content);
    }
  }

  public void testGettingArtifactInfos() throws Exception {
    myRepositoryHelper.copy("local2", "local1");
    MavenIndex i = myIndices.add("id", myRepositoryHelper.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    myIndices.updateOrRepair(i, myEmbedder, true, new EmptyProgressIndicator());

    assertUnorderedElementsAreEqual(i.getGroupIds(), "junit", "jmock");

    assertUnorderedElementsAreEqual(i.getArtifactIds("junit"), "junit");
    assertUnorderedElementsAreEqual(i.getArtifactIds("jmock"), "jmock");
    assertUnorderedElementsAreEqual(i.getArtifactIds("unknown"));

    assertUnorderedElementsAreEqual(i.getVersions("junit", "junit"), "3.8.1", "3.8.2", "4.0");
    assertUnorderedElementsAreEqual(i.getVersions("junit", "jmock"));
    assertUnorderedElementsAreEqual(i.getVersions("unknown", "unknown"));
  }

  public void testGettingArtifactInfosFromNotUpdatedRepositories() throws Exception {
    MavenIndex i = myIndices.add("id", myRepositoryHelper.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    assertUnorderedElementsAreEqual(i.getGroupIds()); // shouldn't throw
  }

  public void testGettingArtifactInfosAfterReload() throws Exception {
    myIndices.updateOrRepair(myIndices.add("id", myRepositoryHelper.getTestDataPath("local1"), MavenIndex.Kind.LOCAL),
                             myEmbedder,
                             true,
                             new EmptyProgressIndicator());

    shutdownIndices();
    initIndices();

    assertUnorderedElementsAreEqual(myIndices.getIndices().get(0).getGroupIds(), "junit");
  }

  public void testHasArtifactInfo() throws Exception {
    myRepositoryHelper.copy("local2", "local1");
    MavenIndex i = myIndices.add("id", myRepositoryHelper.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    myIndices.updateOrRepair(i, myEmbedder, true, new EmptyProgressIndicator());

    assertTrue(i.hasGroupId("junit"));
    assertTrue(i.hasGroupId("jmock"));
    assertFalse(i.hasGroupId("xxx"));

    assertTrue(i.hasArtifactId("junit", "junit"));
    assertTrue(i.hasArtifactId("jmock", "jmock"));
    assertFalse(i.hasArtifactId("junit", "jmock"));

    assertTrue(i.hasVersion("junit", "junit", "4.0"));
    assertTrue(i.hasVersion("jmock", "jmock", "1.0.0"));
    assertFalse(i.hasVersion("junit", "junit", "666"));
  }

  public void testSearching() throws Exception {
    MavenIndex i = myIndices.add("id", myRepositoryHelper.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    myIndices.updateOrRepair(i, myEmbedder, true, new EmptyProgressIndicator());

    assertSearchResults(i, new TermQuery(new Term(ArtifactInfo.GROUP_ID, "junit")),
                        "junit:junit:3.8.1", "junit:junit:3.8.2", "junit:junit:4.0");
  }

  public void testSearchingAfterArtifactAddition() throws Exception {
    MavenIndex i = myIndices.add("id", myRepositoryHelper.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    myIndices.updateOrRepair(i, myEmbedder, true, new EmptyProgressIndicator());

    i.addArtifact(new File(myRepositoryHelper.getTestDataPath("local2/jmock/jmock/1.0.0/jmock-1.0.0.jar")));

    assertSearchResults(i, new TermQuery(new Term(ArtifactInfo.GROUP_ID, "jmock")),
                        "jmock:jmock:1.0.0");
  }

  private void assertSearchResults(MavenIndex i, Query query, String... expectedArtifacts) {
    List<String> actualArtifacts = new ArrayList<String>();
    for (ArtifactInfo each : i.search(query, 100)) {
      actualArtifacts.add(each.groupId + ":" + each.artifactId + ":" + each.version);
    }
    assertUnorderedElementsAreEqual(actualArtifacts, expectedArtifacts);
  }

  public void testSearchingForClasses() throws Exception {
    MavenIndex i = myIndices.add("id", myRepositoryHelper.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    myIndices.updateOrRepair(i, myEmbedder, true, new EmptyProgressIndicator());

    assertSearchResults(i, new WildcardQuery(new Term(ArtifactInfo.NAMES, "*runwith*")),
                        "junit:junit:4.0");
  }

  public void testSearchingForClassesAfterArtifactAddition() throws Exception {
    MavenIndex i = myIndices.add("id", myRepositoryHelper.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    myIndices.updateOrRepair(i, myEmbedder, true, new EmptyProgressIndicator());

    i.addArtifact(new File(myRepositoryHelper.getTestDataPath("local2/jmock/jmock/1.0.0/jmock-1.0.0.jar")));

    assertSearchResults(i, new WildcardQuery(new Term(ArtifactInfo.NAMES, "*mock*")),
                        "jmock:jmock:1.0.0");
  }
}
