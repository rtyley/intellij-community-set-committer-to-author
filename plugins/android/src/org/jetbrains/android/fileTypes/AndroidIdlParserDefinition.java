package org.jetbrains.android.fileTypes;

import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.EmptyLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class AndroidIdlParserDefinition implements ParserDefinition {
  public static final IElementType AIDL_TEXT = new IElementType("AIDL_TEXT", AndroidIdlFileType.ourFileType.getLanguage());

  public static final IFileElementType AIDL_FILE_ELEMENT_TYPE = new IFileElementType(AndroidIdlFileType.ourFileType.getLanguage()) {
    public ASTNode parseContents(ASTNode chameleon) {
      return ASTFactory.leaf(AIDL_TEXT, chameleon.getChars());
    }
  };

  @NotNull
  public Lexer createLexer(Project project) {
    return new EmptyLexer();
  }

  public PsiParser createParser(Project project) {
    throw new UnsupportedOperationException();
  }

  public IFileElementType getFileNodeType() {
    return AIDL_FILE_ELEMENT_TYPE;
  }

  @NotNull
  public TokenSet getWhitespaceTokens() {
    return TokenSet.EMPTY;
  }

  @NotNull
  public TokenSet getCommentTokens() {
    return TokenSet.EMPTY;
  }

  @NotNull
  public TokenSet getStringLiteralElements() {
    return TokenSet.EMPTY;
  }

  @NotNull
  public PsiElement createElement(ASTNode node) {
    return PsiUtilBase.NULL_PSI_ELEMENT;
  }

  public PsiFile createFile(FileViewProvider viewProvider) {
    return new AndroidIdlFileImpl(viewProvider);
  }

  public SpaceRequirements spaceExistanceTypeBetweenTokens(ASTNode left, ASTNode right) {
    return ParserDefinition.SpaceRequirements.MAY;
  }
}
