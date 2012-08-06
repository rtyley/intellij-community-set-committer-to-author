/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.compiler

import com.intellij.compiler.CompilerConfiguration
import com.intellij.compiler.CompilerConfigurationImpl
import org.jetbrains.idea.maven.MavenImportingTestCase
import org.jetbrains.idea.maven.importing.MavenModuleImporter
/**
 * @author Sergey Evdokimov
 */
@SuppressWarnings("GroovyPointlessBoolean")
class AnnotationProcessorImportingTest extends MavenImportingTestCase {

  public void testSettingTargetLevel() throws Exception {
    createModulePom("module1", """
<groupId>test</groupId>
<artifactId>module1</artifactId>
<version>1</version>
""")

    createModulePom("module2", """
<groupId>test</groupId>
<artifactId>module2</artifactId>
<version>1</version>

  <build>
    <plugins>
      <plugin>
        <artifactId>maven-compiler-plugin</artifactId>
        <configuration>
          <annotationProcessors>
            <annotationProcessor>com.test.SourceCodeGeneratingAnnotationProcessor2</annotationProcessor>
          </annotationProcessors>
        </configuration>
      </plugin>
    </plugins>
  </build>
""")

    createModulePom("module3", """
<groupId>test</groupId>
<artifactId>module3</artifactId>
<version>1</version>

  <build>
    <plugins>
      <plugin>
        <artifactId>maven-compiler-plugin</artifactId>
        <configuration>
           <proc>none</proc>
        </configuration>
      </plugin>
    </plugins>
  </build>
""")

    importProject """
<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>

<modules>
  <module>module1</module>
  <module>module2</module>
  <module>module3</module>
</modules>

""";

    def compilerConfiguration = ((CompilerConfigurationImpl)CompilerConfiguration.getInstance(myProject))

    assert compilerConfiguration.findModuleProcessorProfile(MavenModuleImporter.MAVEN_DEFAULT_ANNOTATION_PROFILE).getModuleNames() == new HashSet<String>(["project", "module1"])
    assert compilerConfiguration.findModuleProcessorProfile(MavenModuleImporter.PROFILE_PREFIX + 'module2').isObtainProcessorsFromClasspath() == false
    assert compilerConfiguration.findModuleProcessorProfile(MavenModuleImporter.PROFILE_PREFIX + 'module2').getProcessors() == new HashSet<String>(["com.test.SourceCodeGeneratingAnnotationProcessor2"])
  }

  public void testOverrideGeneratedOutputDir() {
    importProject """
<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>

<build>
  <plugins>
    <plugin>
      <artifactId>maven-compiler-plugin</artifactId>
      <configuration>
        <generatedSourcesDirectory>out/generated</generatedSourcesDirectory>
      </configuration>
    </plugin>
  </plugins>
</build>
""";

    def compilerConfiguration = ((CompilerConfigurationImpl)CompilerConfiguration.getInstance(myProject))

    assert compilerConfiguration.findModuleProcessorProfile(MavenModuleImporter.MAVEN_DEFAULT_ANNOTATION_PROFILE) == null
    assert compilerConfiguration.findModuleProcessorProfile(MavenModuleImporter.PROFILE_PREFIX + "project").getGeneratedSourcesDirectoryName().endsWith("out/generated")
  }

}
