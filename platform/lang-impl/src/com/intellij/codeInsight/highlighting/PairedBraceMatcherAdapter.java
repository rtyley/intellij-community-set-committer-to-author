package com.intellij.codeInsight.highlighting;

import com.intellij.lang.BracePair;
import com.intellij.lang.Language;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class PairedBraceMatcherAdapter implements NontrivialBraceMatcher {
  private final PairedBraceMatcher myMatcher;
  private final Language myLanguage;

  public PairedBraceMatcherAdapter(final PairedBraceMatcher matcher, Language language) {
    myMatcher = matcher;
    myLanguage = language;
  }

  public int getBraceTokenGroupId(IElementType tokenType) {
    final BracePair[] pairs = myMatcher.getPairs();
    for (BracePair pair : pairs) {
      if (tokenType == pair.getLeftBraceType() || tokenType == pair.getRightBraceType()) return myLanguage.hashCode();
    }
    return -1;
  }

  public boolean isLBraceToken(HighlighterIterator iterator, CharSequence fileText, FileType fileType) {
    final IElementType tokenType = iterator.getTokenType();
    final BracePair[] pairs = myMatcher.getPairs();
    for (BracePair pair : pairs) {
      if (tokenType == pair.getLeftBraceType()) return true;
    }
    return false;
  }

  public boolean isRBraceToken(HighlighterIterator iterator, CharSequence fileText, FileType fileType) {
    final IElementType tokenType = iterator.getTokenType();
    final BracePair[] pairs = myMatcher.getPairs();
    for (BracePair pair : pairs) {
      if (tokenType == pair.getRightBraceType()) return true;
    }
    return false;
  }

  public IElementType getOppositeBraceTokenType(@NotNull final IElementType type) {
    final BracePair[] pairs = myMatcher.getPairs();
    for (BracePair pair : pairs) {
      if (type == pair.getRightBraceType()) return pair.getLeftBraceType();
      if (type == pair.getLeftBraceType()) return pair.getRightBraceType();
    }

    return null;
  }

  public boolean isPairBraces(IElementType tokenType, IElementType tokenType2) {
    final BracePair[] pairs = myMatcher.getPairs();
    for (BracePair pair : pairs) {
      if (tokenType == pair.getLeftBraceType() && tokenType2 == pair.getRightBraceType() ||
          tokenType == pair.getRightBraceType() && tokenType2 == pair.getLeftBraceType()) {
        return true;
      }
    }
    return false;
  }

  public boolean isStructuralBrace(HighlighterIterator iterator, CharSequence text, FileType fileType) {
    final IElementType tokenType = iterator.getTokenType();
    final BracePair[] pairs = myMatcher.getPairs();
    for (BracePair pair : pairs) {
      if (tokenType == pair.getRightBraceType() || tokenType == pair.getLeftBraceType()) return pair.isStructural();
    }
    return false;
  }

  public boolean isPairedBracesAllowedBeforeType(@NotNull IElementType lbraceType, @Nullable IElementType contextType) {
    return myMatcher.isPairedBracesAllowedBeforeType(lbraceType, contextType);
  }

  public int getCodeConstructStart(final PsiFile file, int openingBraceOffset) {
    return myMatcher.getCodeConstructStart(file, openingBraceOffset);
  }

  @NotNull
  public List<IElementType> getOppositeBraceTokenTypes(@NotNull IElementType type) {
    List<IElementType> result = null;

    for (BracePair pair : myMatcher.getPairs()) {
      IElementType match = null;

      if (type == pair.getRightBraceType()) match = pair.getLeftBraceType();
      if (type == pair.getLeftBraceType()) match = pair.getRightBraceType();

      if (match != null) {
        if (result == null) result = new ArrayList<IElementType>(2);
        result.add(match);
      }
    }

    return result != null ? result : Collections.<IElementType>emptyList();
  }
}
