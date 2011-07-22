/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.psi.jsp;

import com.intellij.jsp.impl.JavaInJspHandler;
import com.intellij.lang.jsp.JspFileViewProviderImpl;
import com.intellij.lang.jsp.JspxFileViewProvider;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

/**
* @author gregsh
*/
public class MockJavaInJspHandler extends JavaInJspHandler {
  @NotNull
  @Override
  public PsiFile createJavaFile(@NotNull JspxFileViewProvider provider) {
    final PsiFile file = super.createJavaFile(provider);
    file.putUserData(PsiUtil.FILE_LANGUAGE_LEVEL_KEY, LanguageLevel.JDK_1_6);
    return file;
  }

  @Override
  public void fillContextPrefixes(JspFileViewProviderImpl viewProvider,
                                  JspFileViewProviderImpl.PrefixesWithLanguage prefixes,
                                  boolean includesOnly) {
    viewProvider.fillLocalPrefixes(prefixes);
  }
}
