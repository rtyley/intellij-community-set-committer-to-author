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
package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TemplateContext;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.ide.highlighter.XmlLikeFileType;
import com.intellij.lang.Language;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.lang.xhtml.XHTMLLanguage;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ven
 */
public class TemplateSurrounder implements Surrounder {

  protected final TemplateImpl myTemplate;

  public TemplateSurrounder(final TemplateImpl template) {
    myTemplate = template;
  }

  public String getTemplateDescription() {
    return myTemplate.getDescription();
  }

  public boolean isApplicableForFileType(FileType fileType) {
    final TemplateContext templateContext = myTemplate.getTemplateContext();

    if (fileType instanceof XmlLikeFileType) {
      for(TemplateContextType contextType: Extensions.getExtensions(TemplateContextType.EP_NAME)) {
        if (contextType.isInContext(fileType)) {
          if (templateContext.isEnabled(contextType)) return true;
        }
      }

    }

    return false;
  }

  public boolean isApplicable(@NotNull PsiElement[] elements) {
    final FileType fileType = elements[0].getContainingFile().getFileType();
    return isApplicableForFileType(fileType);
  }

  @Nullable public TextRange surroundElements(@NotNull final Project project,
                                              @NotNull final Editor editor,
                                              @NotNull PsiElement[] elements) throws IncorrectOperationException {
    final boolean languageWithWSSignificant = isLanguageWithWSSignificant(elements[0]);

    final int startOffset = languageWithWSSignificant ?
                            editor.getSelectionModel().getSelectionStart():
                            elements[0].getTextRange().getStartOffset();

    final int endOffset = languageWithWSSignificant ?
                          editor.getSelectionModel().getSelectionEnd():
                          elements[elements.length - 1].getTextRange().getEndOffset();

    editor.getCaretModel().moveToOffset(startOffset);
    editor.getSelectionModel().setSelection(startOffset, endOffset);
    String text = editor.getDocument().getText().substring(startOffset, endOffset);

    if (!languageWithWSSignificant) text = text.trim();

    final String text1 = text;

    final Runnable action = new Runnable() {
      public void run() {
        TemplateManager.getInstance(project).startTemplate(editor, text1, myTemplate);
      }
    };

    if (languageWithWSSignificant) {
      CodeStyleManager.getInstance(project).performActionWithFormatterDisabled(action);
    } else {
      action.run();
    }

    return null;
  }

  private boolean isLanguageWithWSSignificant(PsiElement element) {
    return isLanguageWithWSSignificant(getLanguage(element)) ||
           element instanceof XmlToken && ((XmlToken)element).getTokenType() == XmlTokenType.XML_DATA_CHARACTERS;
  }

  protected boolean isLanguageWithWSSignificant(Language lang) {
    return lang == HTMLLanguage.INSTANCE ||
           lang == XHTMLLanguage.INSTANCE;
  }

  private static Language getLanguage(PsiElement element) {
    Language lang = element.getLanguage();
    if (lang == XMLLanguage.INSTANCE) lang = element.getParent().getLanguage();
    return lang;
  }

}
