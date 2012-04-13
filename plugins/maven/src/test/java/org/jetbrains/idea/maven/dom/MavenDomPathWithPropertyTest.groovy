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
package org.jetbrains.idea.maven.dom

import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiManager

/**
 * @author Sergey Evdokimov
 */
class MavenDomPathWithPropertyTest extends MavenDomTestCase {

  public void testRename() {
    importProject("""
<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>

<properties>
  <ppp>aaa</ppp>
  <rrr>res</rrr>
</properties>

<build>
  <resources>
    <resource>
      <directory>aaa/bbb/res</directory>
    </resource>
    <resource>
      <directory>\${pom.basedir}/aaa/bbb/res</directory>
    </resource>
    <resource>
      <directory>\${pom.basedir}/@ppp@/bbb/res</directory>
    </resource>
    <resource>
      <directory>@ppp@/bbb/res</directory>
    </resource>
    <resource>
      <directory>@ppp@/bbb/@rrr@</directory>
    </resource>
  </resources>
</build>
""")

    def dir = createProjectSubDir("aaa/bbb/res")

    def bbb = dir.parent
    myFixture.renameElement(PsiManager.getInstance(myFixture.project).findDirectory(bbb), "Z")

    assert PsiManager.getInstance(myFixture.project).findFile(myProjectPom).text.contains("""
<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>

<properties>
  <ppp>aaa</ppp>
  <rrr>res</rrr>
</properties>

<build>
  <resources>
    <resource>
      <directory>aaa/Z/res</directory>
    </resource>
    <resource>
      <directory>\${pom.basedir}/aaa/Z/res</directory>
    </resource>
    <resource>
      <directory>\${pom.basedir}/@ppp@/Z/res</directory>
    </resource>
    <resource>
      <directory>@ppp@/Z/res</directory>
    </resource>
    <resource>
      <directory>@ppp@/Z/@rrr@</directory>
    </resource>
  </resources>
</build>
""")
  }

}
