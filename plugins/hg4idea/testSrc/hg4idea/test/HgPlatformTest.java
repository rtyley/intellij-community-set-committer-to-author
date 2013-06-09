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
package hg4idea.test;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgVcs;

import java.io.File;
import java.io.IOException;

import static com.intellij.dvcs.test.Executor.cd;
import static com.intellij.dvcs.test.Executor.touch;
import static hg4idea.test.HgExecutor.hg;

/**
 * The base class for tests of hg4idea plugin.<br/>
 * Extend this test to write a test on Mercurial which has the following features/limitations:
 * <ul>
 * <li>This is a "platform test case", which means that IDEA [almost] production platform is set up before the test starts.</li>
 * <li>Project base directory is the root of everything. It can contain as much nested repositories as needed,
 * but if you need to test the case when hg repository is <b>above</b> the project dir, you need either to adjust this base class,
 * or create another one.</li>
 * <li>Initially one repository is created with the project dir as its root. I. e. all project is under Mercurial.</li>
 * </ul>
 *
 * @author Kirill Likhodedov
 */
public abstract class HgPlatformTest extends UsefulTestCase {

  protected Project myProject;
  protected VirtualFile myProjectRoot;
  protected VirtualFile myRepository;
  protected VirtualFile myChildRepo;

  protected static final String COMMIT_MESSAGE = "text";

  private IdeaProjectTestFixture myProjectFixture;


  @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
  protected HgPlatformTest() {
    PlatformTestCase.initPlatformLangPrefix();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myProjectFixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getTestName(true)).getFixture();
    myProjectFixture.setUp();

    myProject = myProjectFixture.getProject();
    myProjectRoot = myProject.getBaseDir();

    cd(myProjectRoot);
    hg("version");

    createRepository(myProjectRoot);
    HgVcs hgVcs = HgVcs.getInstance(myProject);
    assertNotNull(hgVcs);
    hgVcs.getGlobalSettings().setHgExecutable(HgExecutor.getHgExecutable());
    myRepository = myProjectRoot;
    setUpHgrc(myRepository);
  }

  @Override
  protected void tearDown() throws Exception {
    myProjectFixture.tearDown();
    super.tearDown();
  }

  private static void setUpHgrc(VirtualFile repository) {
    cd(".hg");
    File pluginRoot = new File(PluginPathManager.getPluginHomePath("hg4idea"));
    String pathToHgrc = "testData\\repo\\dot_hg";
    File hgrcFile = new File(new File(pluginRoot, FileUtil.toSystemIndependentName(pathToHgrc)), "hgrc");
    File hgrc = new File(new File(repository.getPath(), ".hg"), "hgrc");
    try {
      FileUtil.copy(hgrcFile, hgrc);
    }
    catch (IOException e) {
      e.printStackTrace();
      fail("Can not copy hgrc file.");
    }
    assertTrue(hgrc.exists());
  }

  protected void createRepository(VirtualFile root) {
    initRepo(root.getPath());
  }

  private static void initRepo(String repoRoot) {
    cd(repoRoot);
    hg("init");
    touch("file.txt");
    hg("add file.txt");
    hg("commit -m initial");
  }

  public void prepareSecondRepository() throws IOException {
    cd(myRepository);
    hg("clone " + myRepository.getCanonicalPath() + " childRepo");
    myChildRepo = myRepository.findChild("childRepo");
    cd(myChildRepo);
    hg("pull");
    hg("update");
    File childHgrc = new File(new File(myChildRepo.getPath(), ".hg"), "hgrc");
    switchOffMergeExternalTool(childHgrc);
    HgTestUtil.updateDirectoryMappings(myProject, myRepository);
    HgTestUtil.updateDirectoryMappings(myProject, myChildRepo);
  }

  /**
   * External merge tools should be switched off to reproduce conflict situations.
   * For Linux there are default merge tool.
   *
   * @param hgrcFile file to modify
   * @throws IOException
   */
  public static void switchOffMergeExternalTool(@NotNull File hgrcFile) throws IOException {
    FileUtil.appendToFile(hgrcFile, "[merge-patterns]\n" +
                                    "** = internal:merge\n");
  }
}
