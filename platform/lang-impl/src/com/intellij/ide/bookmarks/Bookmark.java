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

package com.intellij.ide.bookmarks;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.lang.LanguageStructureViewBuilder;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.LightColors;
import com.intellij.util.PlatformIcons;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class Bookmark {
  private static final Icon TICK = PlatformIcons.CHECK_ICON;

  private final VirtualFile myFile;
  private final OpenFileDescriptor myTarget;
  private final Project myProject;

  private String myDescription;
  private char myMnemonic = 0;
  public static final Font MNEMONIC_FONT = new Font("Monospaced", 0, 11);

  public Bookmark(@NotNull Project project, @NotNull VirtualFile file, int line, @NotNull String description) {
    myFile = file;
    myProject = project;
    myDescription = description;

    myTarget = new OpenFileDescriptor(project, file, line, -1, true);

    Document document = FileDocumentManager.getInstance().getCachedDocument(getFile());
    if (document != null) {
      createHighlighter((MarkupModelEx)DocumentMarkupModel.forDocument(document, myProject, true));
    }
  }

  public RangeHighlighter createHighlighter(@NotNull MarkupModelEx markup) {
    final RangeHighlighter myHighlighter;
    int line = getLine();
    if (line >= 0) {
      myHighlighter = markup.addPersistentLineHighlighter(line, HighlighterLayer.ERROR + 1, null);
      if (myHighlighter != null) {
        myHighlighter.setGutterIconRenderer(new MyGutterIconRenderer(this));

        myHighlighter.setErrorStripeMarkColor(Color.black);
        myHighlighter.setErrorStripeTooltip(getBookmarkTooltip());
      }
    }
    else {
      myHighlighter = null;
    }
    return myHighlighter;
  }

  public Document getDocument() {
    return FileDocumentManager.getInstance().getDocument(getFile());
  }

  public void release() {
    int line = getLine();
    if (line < 0) {
      return;
    }
    final Document document = getDocument();
    if (document == null) return;
    MarkupModelEx markup = (MarkupModelEx)DocumentMarkupModel.forDocument(document, myProject, true);
    int startOffset = markup.getDocument().getLineStartOffset(line);
    int endOffset = markup.getDocument().getLineEndOffset(line);
    final RangeHighlighterEx[] found = new RangeHighlighterEx[1];
    markup.processRangeHighlightersOverlappingWith(startOffset, endOffset, new Processor<RangeHighlighterEx>() {
      @Override
      public boolean process(RangeHighlighterEx highlighter) {
        GutterIconRenderer renderer = highlighter.getGutterIconRenderer();
        if (renderer instanceof MyGutterIconRenderer && ((MyGutterIconRenderer)renderer).myBookmark == Bookmark.this) {
          found[0] = highlighter;
          return false;
        }
        return true;
      }
    });
    if (found[0] != null) found[0].dispose();
  }

  public Icon getIcon() {
    return myMnemonic == 0 ? TICK : new MnemonicIcon(myMnemonic);
  }

  public String getDescription() {
    return myDescription;
  }

  public void setDescription(String description) {
    myDescription = description;
  }

  public char getMnemonic() {
    return myMnemonic;
  }

  public void setMnemonic(char mnemonic) {
    myMnemonic = Character.toUpperCase(mnemonic);
  }

  @NotNull
  public VirtualFile getFile() {
    return myFile;
  }

  @Nullable
  public String getNotEmptyDescription() {
    return StringUtil.isEmpty(myDescription) ? null : myDescription;
  }

  public boolean isValid() {
    if (!getFile().isValid()) {
      return false;
    }

    // There is a possible case that target document line that is referenced by the current bookmark is removed. We assume
    // that corresponding range marker becomes invalid then.
    RangeMarker rangeMarker = myTarget.getRangeMarker();
    return rangeMarker == null || rangeMarker.isValid();
  }

  public void navigate() {
    myTarget.navigate(true);
  }

  public int getLine() {
    RangeMarker marker = myTarget.getRangeMarker();
    if (marker != null && marker.isValid()) {
      Document document = marker.getDocument();
      return document.getLineNumber(marker.getStartOffset());
    }
    return myTarget.getLine();
  }

  @Override
  public String toString() {
    return getQualifiedName();
  }

  public String getQualifiedName() {
    String presentableUrl = myFile.getPresentableUrl();
    if (myFile.isDirectory()) return presentableUrl;

    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    final PsiFile psiFile = PsiManager.getInstance(myProject).findFile(myFile);

    if (psiFile == null) return presentableUrl;

    StructureViewBuilder builder = LanguageStructureViewBuilder.INSTANCE.getStructureViewBuilder(psiFile);
    if (builder instanceof TreeBasedStructureViewBuilder) {
      StructureViewModel model = ((TreeBasedStructureViewBuilder)builder).createStructureViewModel();
      Object element = model.getCurrentEditorElement();
      if (element instanceof NavigationItem) {
        ItemPresentation presentation = ((NavigationItem)element).getPresentation();
        if (presentation != null) {
          presentableUrl = ((NavigationItem)element).getName() + " " + presentation.getLocationString();
        }
      }
    }

    return IdeBundle.message("bookmark.file.X.line.Y", presentableUrl, getLine() + 1);
  }

  private String getBookmarkTooltip() {
    StringBuilder result = new StringBuilder("Bookmark");
    if (myMnemonic != 0) {
      result.append(" ").append(myMnemonic);
    }
    String description = StringUtil.escapeXml(getNotEmptyDescription());
    if (description != null) {
      result.append(": ").append(description);
    }
    return result.toString();
  }
  
  private static class MnemonicIcon implements Icon {
    private final char myMnemonic;

    private MnemonicIcon(char mnemonic) {
      myMnemonic = mnemonic;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      g.setColor(LightColors.YELLOW);
      g.fillRect(x, y, getIconWidth(), getIconHeight());

      g.setColor(Color.gray);
      g.drawRect(x, y, getIconWidth(), getIconHeight());

      g.setColor(Color.black);
      final Font oldFont = g.getFont();
      g.setFont(MNEMONIC_FONT);

      g.drawString(Character.toString(myMnemonic), x + 2, y + getIconHeight() - 2);
      g.setFont(oldFont);
    }

    @Override
    public int getIconWidth() {
      return 10;
    }

    @Override
    public int getIconHeight() {
      return 12;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      MnemonicIcon that = (MnemonicIcon)o;

      return myMnemonic == that.myMnemonic;
    }
  }

  private static class MyGutterIconRenderer extends GutterIconRenderer {
    private final Bookmark myBookmark;

    public MyGutterIconRenderer(@NotNull Bookmark bookmark) {
      myBookmark = bookmark;
    }

    @Override
    @NotNull
    public Icon getIcon() {
      return myBookmark.getIcon();
    }

    @Override
    public String getTooltipText() {
      return myBookmark.getBookmarkTooltip();
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof MyGutterIconRenderer &&
             Comparing.equal(getTooltipText(), ((MyGutterIconRenderer)obj).getTooltipText()) &&
             Comparing.equal(getIcon(), ((MyGutterIconRenderer)obj).getIcon());
    }

    @Override
     public int hashCode() {
      return getIcon().hashCode();
    }
  }
}
