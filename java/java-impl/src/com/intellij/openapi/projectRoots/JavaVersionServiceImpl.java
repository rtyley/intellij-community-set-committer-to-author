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
package com.intellij.openapi.projectRoots;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;

/**
 * User: anna
 * Date: 3/28/12
 */
public class JavaVersionServiceImpl extends JavaVersionService {
  private JavaSdkVersion myTestVersion = null;

  public void setTestVersion(JavaSdkVersion testVersion) {
    myTestVersion = testVersion;
  }

  @Override
  public boolean isAtLeast(PsiElement element, JavaSdkVersion version) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return myTestVersion != null && myTestVersion.isAtLeast(version);
    return JavaSdkVersionUtil.isAtLeast(element, version);
  }
}
