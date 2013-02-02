/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package hg4idea.test.diff;

import com.intellij.dvcs.test.Executor;
import com.intellij.dvcs.test.MockVirtualFile;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import hg4idea.test.HgExecutor;
import hg4idea.test.HgLightTest;
import org.junit.After;
import org.junit.Test;
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.HgFileRevision;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.util.HgUtil;

import java.io.File;
import java.util.List;

import static junit.framework.Assert.assertEquals;

/**
 * @author Nadya Zabrodina
 */

public class HgGetDiffForDirTest extends HgLightTest {
  private final String SHORT_TEMPLATE_REVISION = "{rev}:{node|short}";
  MockVirtualFile myRepository;


  @Override
  public void setUp() {
    super.setUp();
    myRepository = createRepository(myProjectRoot);
  }

  @After
  public void tearDown() {
    super.tearDown();
  }

  @Test
  public void testDiffForDir() {
    Executor.cd(myProjectRoot);
    Executor.touch("A.txt", "dsfdfdsf");
    HgExecutor.hg("add A.txt");
    Executor.touch("B.txt");
    HgExecutor.hg("add B.txt");
    HgExecutor.hg("commit -m 2files_added");
    Executor.mkdir("dir");
    Executor.cd("dir");
    Executor.touch("C.txt");
    Executor.touch("D.txt");
    HgExecutor.hg("add C.txt");
    HgExecutor.hg("add D.txt");
    HgExecutor.hg("commit -m createDir");
    File dirFile = new File(myProjectRoot, "dir");
    String[] hash1 = HgExecutor.hg("log -l 1 --template=" + SHORT_TEMPLATE_REVISION).split(":");
    HgRevisionNumber r1number = HgRevisionNumber.getInstance(hash1[0], hash1[1]);
    HgFileRevision rev1 =
      new HgFileRevision(myProject, new HgFile(myRepository, dirFile), r1number, "", null, "", "", null, null, null, null);
    Executor.echo("C.txt", "aaaa");
    Executor.echo("D.txt", "dddd");
    HgExecutor.hg("commit -m modifyDir");
    String[] hash2 = HgExecutor.hg("log -l 1 --template=" + SHORT_TEMPLATE_REVISION).split(":");
    HgRevisionNumber r2number = HgRevisionNumber.getInstance(hash2[0], hash2[1]);
    HgFileRevision rev2 =
      new HgFileRevision(myProject, new HgFile(myRepository, dirFile), r2number, "", null, "", "", null, null, null, null);
    FilePath dirPath = new com.intellij.openapi.vcs.FilePathImpl(dirFile, dirFile.isDirectory());
    List<Change> changes = HgUtil.getDiff(myProject, myRepository, dirPath, rev1, rev2, myPlatformFacade);
    assertEquals(changes.size(), 2);
  }
}
