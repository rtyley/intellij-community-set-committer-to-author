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

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.maven.model.Build;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Resource;
import org.jetbrains.idea.maven.MavenTestCase;
import org.jetbrains.idea.maven.embedder.MavenEmbedderFactory;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MavenProjectReaderTest extends MavenTestCase {
  public void testBasics() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>");

    org.apache.maven.project.MavenProject p = readProject(myProjectPom);

    assertEquals(new File(myProjectPom.getPath()), p.getFile());
    assertEquals(new File(myProjectRoot.getPath()), p.getBasedir());

    assertEquals("test", p.getGroupId());
    assertEquals("project", p.getArtifactId());
    assertEquals("1", p.getVersion());
  }

  public void testInvalidXml() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>");

    assertTrue(readProject(myProjectPom, new NullProjectLocator()).isValid);

    createProjectPom("<foo>" +
                     "</bar>" +
                     "<" +
                     "<groupId>test</groupId" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>");

    MavenProjectReaderResult result = readProject(myProjectPom, new NullProjectLocator());
    assertFalse(result.isValid);
    org.apache.maven.project.MavenProject p = result.nativeMavenProject;

    assertEquals(new File(myProjectPom.getPath()), p.getFile());
    assertEquals(new File(myProjectRoot.getPath()), p.getBasedir());

    assertEquals("test", p.getGroupId());
    assertEquals("project", p.getArtifactId());
    assertEquals("1", p.getVersion());
  }

  public void testInvalidParentXml() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>parent</artifactId>" +
                     "<version>1</version>" +
                     "<foo");

    VirtualFile module = createModulePom("module",
                                         "<parent>" +
                                         "  <groupId>test</groupId>" +
                                         "  <artifactId>parent</artifactId>" +
                                         "  <version>1</version>" +
                                         "</parent>");

    assertFalse(readProject(module, new NullProjectLocator()).isValid);
  }

  public void testProjectWithAbsentParentXmlIsValid() throws Exception {
    createProjectPom("<parent>" +
                     "  <groupId>test</groupId>" +
                     "  <artifactId>parent</artifactId>" +
                     "  <version>1</version>" +
                     "</parent>");
    assertTrue(readProject(myProjectPom, new NullProjectLocator()).isValid);
  }

  public void testProjectWithSelfParentIsInvalid() throws Exception {
    createProjectPom("<parent>" +
                     "  <groupId>test</groupId>" +
                     "  <artifactId>project</artifactId>" +
                     "  <version>1</version>" +
                     "</parent>" +

                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>");
    assertFalse(readProject(myProjectPom, new NullProjectLocator()).isValid);
  }

  public void testInvalidProfilesXml() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>");

    createProfilesXml("<profiles");

    assertFalse(readProject(myProjectPom, new NullProjectLocator()).isValid);
  }

  public void testInvalidSettingsXml() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>");

    updateSettingsXml("<settings");

    assertFalse(readProject(myProjectPom, new NullProjectLocator()).isValid);
  }

  public void testInvalidXmlWithNotClosedTag() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1" +
                     "<name>foo</name>");

    MavenProjectReaderResult readResult = readProject(myProjectPom, new NullProjectLocator());
    assertFalse(readResult.isValid);
    org.apache.maven.project.MavenProject p = readResult.nativeMavenProject;

    assertEquals(new File(myProjectPom.getPath()), p.getFile());
    assertEquals(new File(myProjectRoot.getPath()), p.getBasedir());

    assertEquals("test", p.getGroupId());
    assertEquals("project", p.getArtifactId());
    assertEquals("Unknown", p.getVersion());
    assertEquals("foo", p.getName());
  }

  public void testInvalidXmlWithWrongClosingTag() throws Exception {
    if (ignore()) return;

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</vers>" +
                     "<name>foo</name>");

    MavenProjectReaderResult readResult = readProject(myProjectPom, new NullProjectLocator());
    assertFalse(readResult.isValid);
    org.apache.maven.project.MavenProject p = readResult.nativeMavenProject;

    assertEquals(new File(myProjectPom.getPath()), p.getFile());
    assertEquals(new File(myProjectRoot.getPath()), p.getBasedir());

    assertEquals("test", p.getGroupId());
    assertEquals("project", p.getArtifactId());
    assertEquals("1", p.getVersion());
    assertEquals("foo", p.getName());
  }

  public void testEmpty() throws Exception {
    createProjectPom("");

    org.apache.maven.project.MavenProject p = readProject(myProjectPom);

    assertEquals("Unknown", p.getGroupId());
    assertEquals("Unknown", p.getArtifactId());
    assertEquals("Unknown", p.getVersion());
  }

  public void testSpacesInTest() throws Exception {
    createProjectPom("<name>foo bar</name>");

    org.apache.maven.project.MavenProject p = readProject(myProjectPom);
    assertEquals("foo bar", p.getName());
  }

  public void testTextInContainerTag() throws Exception {
    createProjectPom("foo <name>name</name> bar");

    org.apache.maven.project.MavenProject p = readProject(myProjectPom);
    assertEquals("name", p.getName());
  }

  public void testDefaults() throws Exception {
    VirtualFile file = myProjectRoot.createChildData(this, "pom.xml");
    VfsUtil.saveText(file, "<project>" +
                           "  <groupId>test</groupId>" +
                           "  <artifactId>project</artifactId>" +
                           "  <version>1</version>" +
                           "</project>");

    org.apache.maven.project.MavenProject p = readProject(file);

    assertEquals("4.0.0", p.getModelVersion());
    assertEquals("jar", p.getPackaging());
    assertNull(p.getModel().getName());
    assertNull(p.getParent());
    assertNull(p.getParentArtifact());

    Build build = p.getBuild();
    assertNotNull(build);
    assertEquals("project-1", build.getFinalName());
    assertEquals(null, build.getDefaultGoal());
    assertPathEquals(pathFromBasedir("src/main/java"), build.getSourceDirectory());
    assertPathEquals(pathFromBasedir("src/test/java"), build.getTestSourceDirectory());
    assertPathEquals(pathFromBasedir("src/main/scripts"), build.getScriptSourceDirectory());
    assertEquals(1, build.getResources().size());
    assertResource((Resource)build.getResources().get(0), pathFromBasedir("src/main/resources"),
                   false, null, Collections.EMPTY_LIST, Collections.EMPTY_LIST);
    assertEquals(1, build.getTestResources().size());
    assertResource((Resource)build.getTestResources().get(0), pathFromBasedir("src/test/resources"),
                   false, null, Collections.EMPTY_LIST, Collections.EMPTY_LIST);
    assertPathEquals(pathFromBasedir("target"), build.getDirectory());
    assertPathEquals(pathFromBasedir("target/classes"), build.getOutputDirectory());
    assertPathEquals(pathFromBasedir("target/test-classes"), build.getTestOutputDirectory());
  }

  public void testDefaultsForParent() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<parent>" +
                     "  dummy" +
                     "</parent>");

    org.apache.maven.project.MavenProject p = readProject(myProjectPom);

    assertParent(p, "Unknown", "Unknown", "Unknown", "../pom.xml");
  }

  public void testTakingCoordinatesFromParent() throws Exception {
    createProjectPom("<parent>" +
                     "  <groupId>test</groupId>" +
                     "  <artifactId>project</artifactId>" +
                     "  <version>1</version>" +
                     "</parent>");

    org.apache.maven.project.MavenProject p = readProject(myProjectPom);

    assertEquals("test", p.getGroupId());
    assertEquals("Unknown", p.getArtifactId());
    assertEquals("1", p.getVersion());
  }

  public void testCustomSettings() throws Exception {
    VirtualFile file = myProjectRoot.createChildData(this, "pom.xml");
    VfsUtil.saveText(file, "<project>" +
                           "  <modelVersion>1.2.3</modelVersion>" +
                           "  <groupId>test</groupId>" +
                           "  <artifactId>project</artifactId>" +
                           "  <version>1</version>" +
                           "  <name>foo</name>" +
                           "  <packaging>pom</packaging>" +

                           "  <parent>" +
                           "    <groupId>testParent</groupId>" +
                           "    <artifactId>projectParent</artifactId>" +
                           "    <version>2</version>" +
                           "    <relativePath>../parent/pom.xml</relativePath>" +
                           "  </parent>" +

                           "  <build>" +
                           "    <finalName>xxx</finalName>" +
                           "    <defaultGoal>someGoal</defaultGoal>" +
                           "    <sourceDirectory>mySrc</sourceDirectory>" +
                           "    <testSourceDirectory>myTestSrc</testSourceDirectory>" +
                           "    <scriptSourceDirectory>myScriptSrc</scriptSourceDirectory>" +
                           "    <resources>" +
                           "      <resource>" +
                           "        <directory>myRes</directory>" +
                           "        <filtering>true</filtering>" +
                           "        <targetPath>dir</targetPath>" +
                           "        <includes><include>**.properties</include></includes>" +
                           "        <excludes><exclude>**.xml</exclude></excludes>" +
                           "      </resource>" +
                           "    </resources>" +
                           "    <testResources>" +
                           "      <testResource>" +
                           "        <directory>myTestRes</directory>" +
                           "        <includes><include>**.properties</include></includes>" +
                           "      </testResource>" +
                           "    </testResources>" +
                           "    <directory>myOutput</directory>" +
                           "    <outputDirectory>myClasses</outputDirectory>" +
                           "    <testOutputDirectory>myTestClasses</testOutputDirectory>" +
                           "  </build>" +
                           "</project>");

    org.apache.maven.project.MavenProject p = readProject(file);

    assertEquals("1.2.3", p.getModelVersion());
    assertEquals("pom", p.getPackaging());
    assertEquals("foo", p.getName());

    assertParent(p, "testParent", "projectParent", "2", "../parent/pom.xml");

    Build build = p.getBuild();
    assertNotNull(build);
    assertEquals("xxx", build.getFinalName());
    assertEquals("someGoal", build.getDefaultGoal());
    assertPathEquals(pathFromBasedir("mySrc"), build.getSourceDirectory());
    assertPathEquals(pathFromBasedir("myTestSrc"), build.getTestSourceDirectory());
    assertPathEquals(pathFromBasedir("myScriptSrc"), build.getScriptSourceDirectory());
    assertEquals(1, build.getResources().size());
    assertResource((Resource)build.getResources().get(0), pathFromBasedir("myRes"),
                   true, "dir", Collections.singletonList("**.properties"), Collections.singletonList("**.xml"));
    assertEquals(1, build.getTestResources().size());
    assertResource((Resource)build.getTestResources().get(0), pathFromBasedir("myTestRes"),
                   false, null, Collections.singletonList("**.properties"), Collections.EMPTY_LIST);
    assertPathEquals(pathFromBasedir("myOutput"), build.getDirectory());
    assertPathEquals(pathFromBasedir("myClasses"), build.getOutputDirectory());
    assertPathEquals(pathFromBasedir("myTestClasses"), build.getTestOutputDirectory());
  }

  public void testOutputPathsAreBasedOnTargetPath() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<build>" +
                     "  <directory>my-target</directory>" +
                     "</build>");

    org.apache.maven.project.MavenProject p = readProject(myProjectPom);

    Build build = p.getBuild();
    assertNotNull(build);
    assertPathEquals(pathFromBasedir("my-target"), build.getDirectory());
    assertPathEquals(pathFromBasedir("my-target/classes"), build.getOutputDirectory());
    assertPathEquals(pathFromBasedir("my-target/test-classes"), build.getTestOutputDirectory());
  }

  public void testDoesNotIncludeResourcesWithoutDirectory() throws Exception {
    createProjectPom("<build>" +
                     "  <resources>" +
                     "    <resource>" +
                     "      <directory></directory>" +
                     "    </resource>" +
                     "  </resources>" +
                     "  <testResources>" +
                     "    <testResource>" +
                     "      <filtering>true</filtering>" +
                     "    </testResource>" +
                     "  </testResources>" +
                     "</build>");

    org.apache.maven.project.MavenProject p = readProject(myProjectPom);

    Build build = p.getBuild();
    assertEquals(0, build.getResources().size());
    assertEquals(0, build.getTestResources().size());
  }

  public void testPathsWithProperties() throws Exception {
    createProjectPom("<properties>" +
                     "  <foo>subDir</foo>" +
                     "</properties>" +
                     "<build>" +
                     "  <sourceDirectory>${foo}/mySrc</sourceDirectory>" +
                     "  <testSourceDirectory>${foo}/myTestSrc</testSourceDirectory>" +
                     "  <scriptSourceDirectory>${foo}/myScriptSrc</scriptSourceDirectory>" +
                     "  <resources>" +
                     "    <resource>" +
                     "      <directory>${foo}/myRes</directory>" +
                     "    </resource>" +
                     "  </resources>" +
                     "  <testResources>" +
                     "    <testResource>" +
                     "      <directory>${foo}/myTestRes</directory>" +
                     "    </testResource>" +
                     "  </testResources>" +
                     "  <directory>${foo}/myOutput</directory>" +
                     "  <outputDirectory>${foo}/myClasses</outputDirectory>" +
                     "  <testOutputDirectory>${foo}/myTestClasses</testOutputDirectory>" +
                     "</build>");

    org.apache.maven.project.MavenProject p = readProject(myProjectPom);

    Build build = p.getBuild();
    assertPathEquals(pathFromBasedir("subDir/mySrc"), build.getSourceDirectory());
    assertPathEquals(pathFromBasedir("subDir/myTestSrc"), build.getTestSourceDirectory());
    assertPathEquals(pathFromBasedir("subDir/myScriptSrc"), build.getScriptSourceDirectory());
    assertEquals(1, build.getResources().size());
    assertResource((Resource)build.getResources().get(0), pathFromBasedir("subDir/myRes"),
                   false, null, Collections.EMPTY_LIST, Collections.EMPTY_LIST);
    assertEquals(1, build.getTestResources().size());
    assertResource((Resource)build.getTestResources().get(0), pathFromBasedir("subDir/myTestRes"),
                   false, null, Collections.EMPTY_LIST, Collections.EMPTY_LIST);
    assertPathEquals(pathFromBasedir("subDir/myOutput"), build.getDirectory());
    assertPathEquals(pathFromBasedir("subDir/myClasses"), build.getOutputDirectory());
    assertPathEquals(pathFromBasedir("subDir/myTestClasses"), build.getTestOutputDirectory());
  }

  public void testExpandingProperties() throws Exception {
    createProjectPom("<properties>" +
                     "  <prop1>value1</prop1>" +
                     "  <prop2>value2</prop2>" +
                     "</properties>" +

                     "<name>${prop1}</name>" +
                     "<packaging>${prop2}</packaging>");
    org.apache.maven.project.MavenProject p = readProject(myProjectPom);

    assertEquals("value1", p.getName());
    assertEquals("value2", p.getPackaging());
  }

  public void testExpandingPropertiesRecursively() throws Exception {
    createProjectPom("<properties>" +
                     "  <prop1>value1</prop1>" +
                     "  <prop2>${prop1}2</prop2>" +
                     "</properties>" +

                     "<name>${prop1}</name>" +
                     "<packaging>${prop2}</packaging>");
    org.apache.maven.project.MavenProject p = readProject(myProjectPom);

    assertEquals("value1", p.getName());
    assertEquals("value12", p.getPackaging());
  }

  public void testHandlingRecursionProprietly() throws Exception {
    createProjectPom("<properties>" +
                     "  <prop1>${prop2}</prop1>" +
                     "  <prop2>${prop1}</prop2>" +
                     "</properties>" +

                     "<name>${prop1}</name>" +
                     "<packaging>${prop2}</packaging>");
    org.apache.maven.project.MavenProject p = readProject(myProjectPom);

    assertEquals("${prop1}", p.getName());
    assertEquals("${prop2}", p.getPackaging());
  }

  public void testHandlingRecursionProprietlyAndDoNotForgetCoClearRecursionGuard() throws Exception {
    File repositoryPath = new File(myDir, "repository");
    setRepositoryPath(repositoryPath.getPath());

    File parentFile = new File(repositoryPath, "test/parent/1/parent-1.pom");
    parentFile.getParentFile().mkdirs();
    FileUtil.writeToFile(parentFile, createPomXml("<groupId>test</groupId>" +
                                                  "<artifactId>parent</artifactId>" +
                                                  "<version>1</version>").getBytes());

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>not-a-project</artifactId>" +
                     "<version>1</version>" +

                     "<parent>" +
                     " <groupId>test</groupId>" +
                     " <artifactId>parent</artifactId>" +
                     " <version>1</version>" +
                     "</parent>");

    VirtualFile child = createModulePom("child",
                                        "<groupId>test</groupId>" +
                                        "<artifactId>child</artifactId>" +
                                        "<version>1</version>" +

                                        "<parent>" +
                                        " <groupId>test</groupId>" +
                                        " <artifactId>parent</artifactId>" +
                                        " <version>1</version>" +
                                        "</parent>");

    MavenProjectReaderResult readResult = readProject(child, new NullProjectLocator());
    assertTrue(readResult.isValid);
  }

  public void testExpandingSystemAndEnvProperties() throws Exception {
    createProjectPom("<name>${java.home}</name>" +
                     "<packaging>${env.TEMP}</packaging>");

    org.apache.maven.project.MavenProject p = readProject(myProjectPom);
    assertEquals(System.getProperty("java.home"), p.getName());
    assertEquals(System.getenv("TEMP"), p.getPackaging());
  }

  public void testExpandingPropertiesFromProfiles() throws Exception {
    createProjectPom("<name>${prop1}</name>" +
                     "<packaging>${prop2}</packaging>" +

                     "<profiles>" +
                     "  <profile>" +
                     "    <id>one</id>" +
                     "    <activation>" +
                     "      <activeByDefault>true</activeByDefault>" +
                     "    </activation>" +
                     "    <properties>" +
                     "      <prop1>value1</prop1>" +
                     "    </properties>" +
                     "  </profile>" +
                     "  <profile>" +
                     "    <id>two</id>" +
                     "    <properties>" +
                     "      <prop2>value2</prop2>" +
                     "    </properties>" +
                     "  </profile>" +
                     "</profiles>");

    org.apache.maven.project.MavenProject p = readProject(myProjectPom);
    assertEquals("value1", p.getName());
    assertEquals("${prop2}", p.getPackaging());
  }

  public void testExpandingPropertiesFromManuallyActivatedProfiles() throws Exception {
    createProjectPom("<name>${prop1}</name>" +
                     "<packaging>${prop2}</packaging>" +

                     "<profiles>" +
                     "  <profile>" +
                     "    <id>one</id>" +
                     "    <activation>" +
                     "      <activeByDefault>true</activeByDefault>" +
                     "    </activation>" +
                     "    <properties>" +
                     "      <prop1>value1</prop1>" +
                     "    </properties>" +
                     "  </profile>" +
                     "  <profile>" +
                     "    <id>two</id>" +
                     "    <properties>" +
                     "      <prop2>value2</prop2>" +
                     "    </properties>" +
                     "  </profile>" +
                     "</profiles>");

    org.apache.maven.project.MavenProject p = readProject(myProjectPom, "two");
    assertEquals("${prop1}", p.getName());
    assertEquals("value2", p.getPackaging());
  }

  public void testExpandingPropertiesFromParent() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>parent</artifactId>" +
                     "<version>1</version>" +

                     "<properties>" +
                     "  <prop>value</prop>" +
                     "</properties>");

    VirtualFile module = createModulePom("module",
                                         "<parent>" +
                                         "  <groupId>test</groupId>" +
                                         "  <artifactId>parent</artifactId>" +
                                         "  <version>1</version>" +
                                         "</parent>" +
                                         "<name>${prop}</name>");

    org.apache.maven.project.MavenProject p = readProject(module);
    assertEquals("value", p.getName());
  }

  public void testDoNotExpandPropertiesFromParentWithWrongCoordinates() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>parent</artifactId>" +
                     "<version>1</version>" +

                     "<properties>" +
                     "  <prop>value</prop>" +
                     "</properties>");

    VirtualFile module = createModulePom("module",
                                         "<parent>" +
                                         "  <groupId>test</groupId>" +
                                         "  <artifactId>invalid</artifactId>" +
                                         "  <version>1</version>" +
                                         "</parent>" +
                                         "<name>${prop}</name>");

    org.apache.maven.project.MavenProject p = readProject(module);
    assertEquals("${prop}", p.getName());
  }

  public void testExpandingPropertiesFromParentNotInVfs() throws Exception {
    FileUtil.writeToFile(new File(myProjectRoot.getPath(), "pom.xml"),
                         createPomXml("<groupId>test</groupId>" +
                                      "<artifactId>parent</artifactId>" +
                                      "<version>1</version>" +

                                      "<properties>" +
                                      "  <prop>value</prop>" +
                                      "</properties>").getBytes());

    VirtualFile module = createModulePom("module",
                                         "<parent>" +
                                         "  <groupId>test</groupId>" +
                                         "  <artifactId>parent</artifactId>" +
                                         "  <version>1</version>" +
                                         "</parent>" +
                                         "<name>${prop}</name>");

    org.apache.maven.project.MavenProject p = readProject(module);
    assertEquals("value", p.getName());
  }

  public void testExpandingPropertiesFromIndirectParent() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>parent</artifactId>" +
                     "<version>1</version>" +

                     "<properties>" +
                     "  <prop>value</prop>" +
                     "</properties>");

    createModulePom("module",
                    "<groupId>test</groupId>" +
                    "<artifactId>module</artifactId>" +
                    "<version>1</version>" +

                    "<parent>" +
                    "  <groupId>test</groupId>" +
                    "  <artifactId>parent</artifactId>" +
                    "  <version>1</version>" +
                    "</parent>");

    VirtualFile subModule = createModulePom("module/subModule",
                                            "<parent>" +
                                            "  <groupId>test</groupId>" +
                                            "  <artifactId>module</artifactId>" +
                                            "  <version>1</version>" +
                                            "</parent>" +
                                            "<name>${prop}</name>");

    org.apache.maven.project.MavenProject p = readProject(subModule);
    assertEquals("value", p.getName());
  }

  public void testExpandingPropertiesFromParentInSpecifiedLocation() throws Exception {
    createModulePom("parent",
                    "<groupId>test</groupId>" +
                    "<artifactId>parent</artifactId>" +
                    "<version>1</version>" +

                    "<properties>" +
                    "  <prop>value</prop>" +
                    "</properties>");

    VirtualFile module = createModulePom("module",
                                         "<parent>" +
                                         "  <groupId>test</groupId>" +
                                         "  <artifactId>parent</artifactId>" +
                                         "  <version>1</version>" +
                                         "  <relativePath>../parent/pom.xml</relativePath>" +
                                         "</parent>" +
                                         "<name>${prop}</name>");

    org.apache.maven.project.MavenProject p = readProject(module);
    assertEquals("value", p.getName());
  }

  public void testExpandingPropertiesFromParentInSpecifiedLocationWithoutFile() throws Exception {
    createModulePom("parent",
                    "<groupId>test</groupId>" +
                    "<artifactId>parent</artifactId>" +
                    "<version>1</version>" +

                    "<properties>" +
                    "  <prop>value</prop>" +
                    "</properties>");

    VirtualFile module = createModulePom("module",
                                         "<parent>" +
                                         "  <groupId>test</groupId>" +
                                         "  <artifactId>parent</artifactId>" +
                                         "  <version>1</version>" +
                                         "  <relativePath>../parent</relativePath>" +
                                         "</parent>" +
                                         "<name>${prop}</name>");

    org.apache.maven.project.MavenProject p = readProject(module);
    assertEquals("value", p.getName());
  }

  public void testExpandingPropertiesFromParentInRepository() throws Exception {
    File repositoryPath = new File(myDir, "repository");
    setRepositoryPath(repositoryPath.getPath());

    File parentFile = new File(repositoryPath, "org/test/parent/1/parent-1.pom");
    parentFile.getParentFile().mkdirs();
    FileUtil.writeToFile(parentFile,
                         createPomXml("<groupId>org.test</groupId>" +
                                      "<artifactId>parent</artifactId>" +
                                      "<version>1</version>" +

                                      "<properties>" +
                                      "  <prop>value</prop>" +
                                      "</properties>").getBytes());

    createProjectPom("<parent>" +
                     "  <groupId>org.test</groupId>" +
                     "  <artifactId>parent</artifactId>" +
                     "  <version>1</version>" +
                     "</parent>" +
                     "<name>${prop}</name>");

    org.apache.maven.project.MavenProject p = readProject(myProjectPom);
    assertEquals("value", p.getName());
  }

  public void testExpandingPropertiesFromParentInInvalidLocation() throws Exception {
    final VirtualFile parent = createModulePom("parent",
                                               "<groupId>test</groupId>" +
                                               "<artifactId>parent</artifactId>" +
                                               "<version>1</version>" +

                                               "<properties>" +
                                               "  <prop>value</prop>" +
                                               "</properties>");

    VirtualFile module = createModulePom("module",
                                         "<parent>" +
                                         "  <groupId>test</groupId>" +
                                         "  <artifactId>parent</artifactId>" +
                                         "  <version>1</version>" +
                                         "</parent>" +
                                         "<name>${prop}</name>");

    org.apache.maven.project.MavenProject p = readProject(module, new MavenProjectReaderProjectLocator() {
      public VirtualFile findProjectFile(MavenId coordinates) {
        return new MavenId("test", "parent", "1").equals(coordinates) ? parent : null;
      }
    }).nativeMavenProject;
    assertEquals("value", p.getName());
  }

  public void testInheritingSettingsFromParentAndAlignCorrectly() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>parent</artifactId>" +
                     "<version>1</version>" +
                     "<build>" +
                     "  <directory>custom</directory>" +
                     "</build>");

    VirtualFile module = createModulePom("module",
                                         "<parent>" +
                                         "  <groupId>test</groupId>" +
                                         "  <artifactId>parent</artifactId>" +
                                         "  <version>1</version>" +
                                         "</parent>");

    org.apache.maven.project.MavenProject p = readProject(module);
    assertPathEquals(pathFromBasedir(module.getParent(), "custom"), p.getBuild().getDirectory());
  }

  public void testExpandingPropertiesAfterInheritingSettingsFromParent() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>parent</artifactId>" +
                     "<version>1</version>" +

                     "<properties>" +
                     "  <prop>subDir</prop>" +
                     "</properties>" +
                     "<build>" +
                     "  <directory>${basedir}/${prop}/custom</directory>" +
                     "</build>");

    VirtualFile module = createModulePom("module",
                                         "<parent>" +
                                         "  <groupId>test</groupId>" +
                                         "  <artifactId>parent</artifactId>" +
                                         "  <version>1</version>" +
                                         "</parent>");

    org.apache.maven.project.MavenProject p = readProject(module);
    assertPathEquals(pathFromBasedir(module.getParent(), "subDir/custom"), p.getBuild().getDirectory());
  }

  public void testExpandingPropertiesAfterInheritingSettingsFromParentProfiles() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>parent</artifactId>" +
                     "<version>1</version>" +

                     "<profiles>" +
                     "  <profile>" +
                     "    <id>one</id>" +
                     "    <properties>" +
                     "      <prop>subDir</prop>" +
                     "    </properties>" +
                     "    <build>" +
                     "      <directory>${basedir}/${prop}/custom</directory>" +
                     "    </build>" +
                     "  </profile>" +
                     "</profiles>");

    VirtualFile module = createModulePom("module",
                                         "<parent>" +
                                         "  <groupId>test</groupId>" +
                                         "  <artifactId>parent</artifactId>" +
                                         "  <version>1</version>" +
                                         "</parent>");

    org.apache.maven.project.MavenProject p = readProject(module, "one");
    assertPathEquals(pathFromBasedir(module.getParent(), "subDir/custom"), p.getBuild().getDirectory());
  }

  public void testPropertiesFromProfilesXmlOldStyle() throws Exception {
    createProjectPom("<name>${prop}</name>");
    createProfilesXmlOldStyle("<profile>" +
                              "  <id>one</id>" +
                              "  <properties>" +
                              "    <prop>foo</prop>" +
                              "  </properties>" +
                              "</profile>");

    org.apache.maven.project.MavenProject mavenProject = readProject(myProjectPom);
    assertEquals("${prop}", mavenProject.getName());

    mavenProject = readProject(myProjectPom, "one");
    assertEquals("foo", mavenProject.getName());
  }

  public void testPropertiesFromProfilesXmlNewStyle() throws Exception {
    createProjectPom("<name>${prop}</name>");
    createProfilesXml("<profile>" +
                      "  <id>one</id>" +
                      "  <properties>" +
                      "    <prop>foo</prop>" +
                      "  </properties>" +
                      "</profile>");

    org.apache.maven.project.MavenProject mavenProject = readProject(myProjectPom);
    assertEquals("${prop}", mavenProject.getName());

    mavenProject = readProject(myProjectPom, "one");
    assertEquals("foo", mavenProject.getName());
  }

  public void testPropertiesFromSettingsXml() throws Exception {
    createProjectPom("<name>${prop}</name>");
    updateSettingsXml("<profiles>" +
                      "  <profile>" +
                      "    <id>one</id>" +
                      "    <properties>" +
                      "      <prop>foo</prop>" +
                      "    </properties>" +
                      "  </profile>" +
                      "</profiles>");

    org.apache.maven.project.MavenProject mavenProject = readProject(myProjectPom);
    assertEquals("${prop}", mavenProject.getName());

    mavenProject = readProject(myProjectPom, "one");
    assertEquals("foo", mavenProject.getName());
  }

  public void testDoNoInheritParentFinalNameIfUnspecified() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>parent</artifactId>" +
                     "<version>1</version>");

    VirtualFile module = createModulePom("module",
                                         "<groupId>test</groupId>" +
                                         "<artifactId>module</artifactId>" +
                                         "<version>2</version>" +

                                         "<parent>" +
                                         "  <groupId>test</groupId>" +
                                         "  <artifactId>parent</artifactId>" +
                                         "  <version>1</version>" +
                                         "</parent>");

    org.apache.maven.project.MavenProject p = readProject(module, "one");
    assertEquals("module-2", p.getBuild().getFinalName());
  }

  public void testDoInheritingParentFinalNameIfSpecified() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>parent</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <finalName>xxx</finalName>" +
                     "</build>");

    VirtualFile module = createModulePom("module",
                                         "<groupId>test</groupId>" +
                                         "<artifactId>module</artifactId>" +
                                         "<version>2</version>" +

                                         "<parent>" +
                                         "  <groupId>test</groupId>" +
                                         "  <artifactId>parent</artifactId>" +
                                         "  <version>1</version>" +
                                         "</parent>");

    org.apache.maven.project.MavenProject p = readProject(module, "one");
    assertEquals("xxx", p.getBuild().getFinalName());
  }

  public void testActivatingProfilesByOS() throws Exception {
    createProjectPom("<name>${prop1}</name>" +
                     "<packaging>${prop2}</packaging>" +

                     "<profiles>" +
                     "  <profile>" +
                     "    <id>one</id>" +
                     "    <activation>" +
                     "      <os><family>windows</family></os>" +
                     "    </activation>" +
                     "    <properties>" +
                     "      <prop1>value1</prop1>" +
                     "    </properties>" +
                     "  </profile>" +
                     "  <profile>" +
                     "    <id>two</id>" +
                     "    <activation>" +
                     "      <os><family>unix</family></os>" +
                     "    </activation>" +
                     "    <properties>" +
                     "      <prop2>value2</prop2>" +
                     "    </properties>" +
                     "  </profile>" +
                     "</profiles>");

    org.apache.maven.project.MavenProject p = readProject(myProjectPom);
    assertEquals("value1", p.getName());
    assertEquals("${prop2}", p.getPackaging());
  }

  public void testActivatingProfilesByJdk() throws Exception {
    createProjectPom("<name>${prop1}</name>" +
                     "<packaging>${prop2}</packaging>" +

                     "<profiles>" +
                     "  <profile>" +
                     "    <id>one</id>" +
                     "    <activation>" +
                     "      <jdk>[1.5,)</jdk>" +
                     "    </activation>" +
                     "    <properties>" +
                     "      <prop1>value1</prop1>" +
                     "    </properties>" +
                     "  </profile>" +
                     "  <profile>" +
                     "    <id>two</id>" +
                     "    <activation>" +
                     "      <jdk>(,1.5)</jdk>" +
                     "    </activation>" +
                     "    <properties>" +
                     "      <prop2>value2</prop2>" +
                     "    </properties>" +
                     "  </profile>" +
                     "</profiles>");

    org.apache.maven.project.MavenProject p = readProject(myProjectPom);
    assertEquals("value1", p.getName());
    assertEquals("${prop2}", p.getPackaging());
  }

  public void testActivatingProfilesByProperty() throws Exception {
    System.setProperty("maven.test.property", "foo");
    MavenEmbedderFactory.resetSystemPropertiesCacheInTests();

    createProjectPom("<name>${prop1}</name>" +
                     "<packaging>${prop2}</packaging>" +

                     "<profiles>" +
                     "  <profile>" +
                     "    <id>one</id>" +
                     "    <activation>" +
                     "      <property>" +
                     "        <name>maven.test.property</name>" +
                     "        <value>foo</value>" +
                     "      </property>" +
                     "    </activation>" +
                     "    <properties>" +
                     "      <prop1>value1</prop1>" +
                     "    </properties>" +
                     "  </profile>" +
                     "  <profile>" +
                     "    <id>two</id>" +
                     "    <activation>" +
                     "      <property>" +
                     "        <name>maven.test.property</name>" +
                     "        <value>bar</value>" +
                     "      </property>" +
                     "    </activation>" +
                     "    <properties>" +
                     "      <prop2>value2</prop2>" +
                     "    </properties>" +
                     "  </profile>" +
                     "</profiles>");

    org.apache.maven.project.MavenProject p = readProject(myProjectPom);
    assertEquals("value1", p.getName());
    assertEquals("${prop2}", p.getPackaging());
  }

  public void testActivatingProfilesByEnvProperty() throws Exception {
    String value = MavenUtil.getEnvProperties().getProperty("TMP");

    createProjectPom("<name>${prop1}</name>" +
                     "<packaging>${prop2}</packaging>" +

                     "<profiles>" +
                     "  <profile>" +
                     "    <id>one</id>" +
                     "    <activation>" +
                     "      <property>" +
                     "        <name>env.TMP</name>" +
                     "        <value>" + value + "</value>" +
                     "      </property>" +
                     "    </activation>" +
                     "    <properties>" +
                     "      <prop1>value1</prop1>" +
                     "    </properties>" +
                     "  </profile>" +
                     "  <profile>" +
                     "    <id>two</id>" +
                     "    <activation>" +
                     "      <property>" +
                     "        <name>ffffff</name>" +
                     "        <value>ffffff</value>" +
                     "      </property>" +
                     "    </activation>" +
                     "    <properties>" +
                     "      <prop2>value2</prop2>" +
                     "    </properties>" +
                     "  </profile>" +
                     "</profiles>");

    org.apache.maven.project.MavenProject p = readProject(myProjectPom);
    assertEquals("value1", p.getName());
    assertEquals("${prop2}", p.getPackaging());
  }

  public void testActivatingProfilesByFile() throws Exception {
    createProjectSubFile("dir/file.txt");

    createProjectPom("<name>${prop1}</name>" +
                     "<packaging>${prop2}</packaging>" +

                     "<profiles>" +
                     "  <profile>" +
                     "    <id>one</id>" +
                     "    <activation>" +
                     "      <file>" +
                     "        <exists>${basedir}/dir/file.txt</exists>" +
                     "      </file>" +
                     "    </activation>" +
                     "    <properties>" +
                     "      <prop1>value1</prop1>" +
                     "    </properties>" +
                     "  </profile>" +
                     "  <profile>" +
                     "    <id>two</id>" +
                     "    <activation>" +
                     "      <file>" +
                     "        <missing>${basedir}/dir/file.txt</missing>" +
                     "      </file>" +
                     "    </activation>" +
                     "    <properties>" +
                     "      <prop2>value2</prop2>" +
                     "    </properties>" +
                     "  </profile>" +
                     "</profiles>");

    org.apache.maven.project.MavenProject p = readProject(myProjectPom);
    assertEquals("value1", p.getName());
    assertEquals("${prop2}", p.getPackaging());
  }

  private org.apache.maven.project.MavenProject readProject(VirtualFile file, String... profiles) {
    MavenProjectReaderResult readResult = readProject(file, new NullProjectLocator(), profiles);
    assertTrue(readResult.isValid);
    return readResult.nativeMavenProject;
  }

  private MavenProjectReaderResult readProject(VirtualFile file,
                                               MavenProjectReaderProjectLocator locator,
                                               String... profiles) {
    MavenProjectReaderResult result = new MavenProjectReader().readProject(getMavenGeneralSettings(),
                                                                           file,
                                                                           Arrays.asList(profiles),
                                                                           locator);
    return result;
  }

  private void assertParent(org.apache.maven.project.MavenProject p,
                            String groupId,
                            String artifactId,
                            String version,
                            String relativePath) {
    Parent parent = p.getModel().getParent();
    assertNotNull(parent);
    assertEquals(groupId, parent.getGroupId());
    assertEquals(artifactId, parent.getArtifactId());
    assertEquals(version, parent.getVersion());
    assertEquals(relativePath, parent.getRelativePath());
  }

  private void assertResource(Resource resource,
                              String dir,
                              boolean filtered,
                              String targetPath,
                              List<String> includes,
                              List<String> excludes) {
    assertPathEquals(dir, resource.getDirectory());
    assertEquals(filtered, resource.isFiltering());
    assertPathEquals(targetPath, resource.getTargetPath());
    assertOrderedElementsAreEqual(resource.getIncludes(), includes);
    assertOrderedElementsAreEqual(resource.getExcludes(), excludes);
  }

  private static class NullProjectLocator implements MavenProjectReaderProjectLocator {
    public VirtualFile findProjectFile(MavenId coordinates) {
      return null;
    }
  }
}
