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
package org.jetbrains.plugins.groovy.formatter;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;

/**
 * @author Max Medvedev
 */
public class GroovyBracePostFormatProcessor implements PostFormatProcessor {
  @Override
  public PsiElement processElement(PsiElement source, CodeStyleSettings settings) {
    if (source instanceof GroovyPsiElement) {
      return new GroovyBraceEnforcer(settings).process(((GroovyPsiElement)source));
    }
    else {
      return source;
    }
  }

  @Override
  public TextRange processText(PsiFile source, TextRange rangeToReformat, CodeStyleSettings settings) {
    if (source instanceof GroovyFile) {
      return new GroovyBraceEnforcer(settings).processText(((GroovyFile)source), rangeToReformat);
    }
    else {
      return rangeToReformat;
    }
  }
}
