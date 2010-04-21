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
package org.jetbrains.idea.maven.embedder;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.repository.ComponentDescriptor;
import org.jetbrains.idea.maven.MavenImportingTestCase;
import org.jetbrains.idea.maven.project.MavenId;
import org.jetbrains.idea.maven.execution.SoutMavenConsole;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

public class MavenEmbedderWrapperTest extends MavenImportingTestCase {
  private MavenEmbedderWrapper myEmbedder;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initEmbedder();
  }

  @Override
  protected void tearDown() throws Exception {
    releaseEmbedder();
    super.tearDown();
  }

  private void initEmbedder() {
    if (myEmbedder != null) releaseEmbedder();
    myEmbedder = MavenEmbedderWrapper.create(getMavenGeneralSettings());
  }

  private void releaseEmbedder() {
    myEmbedder.release();
    myEmbedder = null;
  }

  public void testSettingLocalRepository() throws Exception {
    assertEquals(getRepositoryFile(), myEmbedder.getLocalRepositoryFile());
  }

  public void testReleasingTwice() throws Exception {
    myEmbedder.release();
    myEmbedder.release();
  }

  public void testExecutionGoals() throws Exception {
    createProjectSubFile("src/main/java/A.java", "public class A {}");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>");

    myEmbedder.customizeForResolve(new SoutMavenConsole(), EMPTY_MAVEN_PROCESS);
    MavenWrapperExecutionResult result = myEmbedder.execute(myProjectPom, Collections.EMPTY_LIST, Arrays.asList("compile"));

    assertNotNull(new File(getProjectPath(), "target").exists());

    assertOrderedElementsAreEqual(result.getExceptions());
    assertOrderedElementsAreEqual(result.getUnresolvedArtifactIds());

    MavenProject project = result.getMavenProject();
    assertNotNull(project);
    assertEquals("project", project.getArtifactId());
  }

  public void testResolvingProject() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>junit</groupId>" +
                     "    <artifactId>junit</artifactId>" +
                     "    <version>4.0</version>" +
                     "  </dependency>" +
                     "</dependencies>");

    myEmbedder.customizeForResolve(new SoutMavenConsole(), EMPTY_MAVEN_PROCESS);
    MavenWrapperExecutionResult result = myEmbedder.resolveProject(myProjectPom, Collections.EMPTY_LIST);

    assertOrderedElementsAreEqual(result.getExceptions());
    assertOrderedElementsAreEqual(result.getUnresolvedArtifactIds());

    MavenProject project = result.getMavenProject();
    assertNotNull(project);
    assertEquals("project", project.getArtifactId());
    assertEquals(1, project.getArtifacts().size());
  }

  public void testResolvingProjectPropertiesInFolders() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>");

    myEmbedder.customizeForResolve(new SoutMavenConsole(), EMPTY_MAVEN_PROCESS);
    MavenWrapperExecutionResult result = myEmbedder.resolveProject(myProjectPom, Collections.EMPTY_LIST);

    MavenProject project = result.getMavenProject();
    assertNotNull(project);
    assertEquals("project", project.getArtifactId());
    assertPathEquals(myProjectRoot.getPath() + "/target", project.getBuild().getDirectory());
    assertPathEquals(myProjectRoot.getPath() + "/src/main/java", (String)project.getCompileSourceRoots().get(0));
  }

  public void testResolvingProjectWithExtensions() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>com.adobe.flex.framework</groupId>" +
                     "    <artifactId>framework</artifactId>" +
                     "    <version>3.2.0.3958</version>" +
                     "    <type>resource-bundle</type>" +
                     "    <classifier>en_US</classifier>" +
                     "  </dependency>" +
                     "</dependencies>" +

                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <groupId>org.sonatype.flexmojos</groupId>" +
                     "      <artifactId>flexmojos-maven-plugin</artifactId>" +
                     "      <version>3.5.0</version>" +
                     "      <extensions>true</extensions>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");

    myEmbedder.customizeForResolve(new SoutMavenConsole(), EMPTY_MAVEN_PROCESS);
    MavenWrapperExecutionResult result = myEmbedder.resolveProject(myProjectPom, Collections.EMPTY_LIST);

    assertOrderedElementsAreEqual(result.getExceptions());

    assertEquals(1, result.getMavenProject().getArtifacts().size());
    assertEquals("rb.swc", ((Artifact)result.getMavenProject().getArtifacts().iterator().next()).getArtifactHandler().getExtension());
  }

  public void testResolvingProjectWithRegisteredExtensions() throws Exception {
    ComponentDescriptor desc = new ComponentDescriptor();
    desc.setRole(ArtifactHandler.ROLE);
    desc.setRoleHint("foo");
    desc.setImplementation(MyArtifactHandler.class.getName());
    myEmbedder.getContainer().addComponentDescriptor(desc);

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>junit</groupId>" +
                     "    <artifactId>junit</artifactId>" +
                     "    <version>3.8.1</version>" +
                     "    <scope>test</scope>" +
                     "    <type>foo</type>" +
                     "  </dependency>" +
                     "</dependencies>");

    myEmbedder.customizeForResolve(new SoutMavenConsole(), EMPTY_MAVEN_PROCESS);
    MavenWrapperExecutionResult result = myEmbedder.resolveProject(myProjectPom, Collections.EMPTY_LIST);

    assertOrderedElementsAreEqual(result.getExceptions());
    assertOrderedElementsAreEqual(result.getUnresolvedArtifactIds());

    assertEquals(1, result.getMavenProject().getArtifacts().size());
    assertEquals("pom", ((Artifact)result.getMavenProject().getArtifacts().iterator().next()).getArtifactHandler().getExtension());
  }

  public void testUnresolvedArtifacts() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>fff</groupId>" +
                     "    <artifactId>zzz</artifactId>" +
                     "    <version>666</version>" +
                     "  </dependency>" +
                     "</dependencies>");

    myEmbedder.customizeForResolve(new SoutMavenConsole(), EMPTY_MAVEN_PROCESS);
    MavenWrapperExecutionResult result = myEmbedder.resolveProject(myProjectPom, Collections.EMPTY_LIST);

    assertOrderedElementsAreEqual(result.getExceptions());
    assertOrderedElementsAreEqual(result.getUnresolvedArtifactIds(), new MavenId("fff", "zzz", "666"));
  }

  public void testUnresolvedSystemArtifacts() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>fff</groupId>" +
                     "    <artifactId>zzz</artifactId>" +
                     "    <version>666</version>" +
                     "    <scope>system</scope>" +
                     "    <systemPath>" + myProjectRoot.getPath() + "/foo.jar</systemPath>" +
                     "  </dependency>" +
                     "</dependencies>");

    myEmbedder.customizeForResolve(new SoutMavenConsole(), EMPTY_MAVEN_PROCESS);
    MavenWrapperExecutionResult result = myEmbedder.resolveProject(myProjectPom, Collections.EMPTY_LIST);

    assertOrderedElementsAreEqual(result.getExceptions());
    assertOrderedElementsAreEqual(result.getUnresolvedArtifactIds(), new MavenId("fff", "zzz", "666"));
  }

  public void testDependencyWithUnresolvedParent() throws Exception {
    File repo = new File(myDir, "/repo");
    setRepositoryPath(repo.getPath());

    initEmbedder();

    VirtualFile m = createModulePom("foo-parent",
                                    "<groupId>test</groupId>" +
                                    "<artifactId>foo-parent</artifactId>" +
                                    "<version>1</version>" +
                                    "<packaging>pom</packaging>");
    myEmbedder.customizeForResolve(new SoutMavenConsole(), EMPTY_MAVEN_PROCESS);
    myEmbedder.execute(m, Collections.EMPTY_LIST, Arrays.asList("install"));
    myEmbedder.reset();
    File fooParentFile = new File(repo, "test/foo-parent/1/foo-parent-1.pom");
    assertTrue(fooParentFile.exists());

    m = createModulePom("foo",
                        "<artifactId>foo</artifactId>" +
                        "<version>1</version>" +

                        "<parent>" +
                        "  <groupId>test</groupId>" +
                        "  <artifactId>foo-parent</artifactId>" +
                        "  <version>1</version>" +
                        "</parent>");
    myEmbedder.customizeForResolve(new SoutMavenConsole(), EMPTY_MAVEN_PROCESS);
    myEmbedder.execute(m, Collections.EMPTY_LIST, Arrays.asList("install"));
    myEmbedder.reset();
    assertTrue(new File(repo, "test/foo/1/foo-1.pom").exists());

    FileUtil.delete(fooParentFile);
    initEmbedder(); // reset all caches

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>test</groupId>" +
                     "    <artifactId>foo</artifactId>" +
                     "    <version>1</version>" +
                     "  </dependency>" +
                     "</dependencies>");

    myEmbedder.customizeForResolve(new SoutMavenConsole(), EMPTY_MAVEN_PROCESS);
    MavenWrapperExecutionResult result = myEmbedder.resolveProject(myProjectPom, Collections.EMPTY_LIST);

    assertOrderedElementsAreEqual(result.getExceptions());
    assertOrderedElementsAreEqual(result.getUnresolvedArtifactIds(), new MavenId("test", "foo-parent", "1"));
  }

  public void testUnresolvedSystemArtifactsWithoutPath() throws Exception {
    if (ignore()) return; // need to repair model before resolving
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>fff</groupId>" +
                     "    <artifactId>zzz</artifactId>" +
                     "    <version>666</version>" +
                     "    <scope>system</scope>" +
                     "  </dependency>" +
                     "</dependencies>");

    myEmbedder.customizeForResolve(new SoutMavenConsole(), EMPTY_MAVEN_PROCESS);
    MavenWrapperExecutionResult result = myEmbedder.resolveProject(myProjectPom, Collections.EMPTY_LIST);

    assertOrderedElementsAreEqual(result.getExceptions());
    assertOrderedElementsAreEqual(result.getUnresolvedArtifactIds(), new MavenId("fff", "zzz", "666"));
  }

  public static class MyArtifactHandler implements ArtifactHandler {
    public String getExtension() {
      return "pom";
    }

    public String getDirectory() {
      throw new UnsupportedOperationException();
    }

    public String getClassifier() {
      return null;
    }

    public String getPackaging() {
      return "foo";
    }

    public boolean isIncludesDependencies() {
      return false;
    }

    public String getLanguage() {
      return "java";
    }

    public boolean isAddedToClasspath() {
      return true;
    }
  }
}
