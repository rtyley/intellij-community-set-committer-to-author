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
package com.intellij.psi

import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

/**
 * @author peter
 */
class StubAstSwitchTest extends LightCodeInsightFixtureTestCase {

  public void "test modifying file with stubs via VFS"() {
    PsiFileImpl file = (PsiFileImpl)myFixture.addFileToProject('Foo.java', 'class Foo {}')
    assert file.stub
    def cls = ((PsiJavaFile)file).classes[0]
    assert file.stub

    ApplicationManager.application.runWriteAction { file.virtualFile.setBinaryContent(file.virtualFile.contentsToByteArray()) }
    assert file.stub

    //todo
    //either: assert !cls.valid
    //or: assert cls == PsiTreeUtil.findElementOfClassAtOffset(file, 1, PsiClass, false)
    //assert !file.stub

  }
}
