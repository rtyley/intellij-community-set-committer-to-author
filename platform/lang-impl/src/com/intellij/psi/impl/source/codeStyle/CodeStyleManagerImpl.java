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

package com.intellij.psi.impl.source.codeStyle;

import com.intellij.formatting.FormatTextRanges;
import com.intellij.formatting.FormatterEx;
import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.lang.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.Indent;
import com.intellij.psi.formatter.DocumentBasedFormattingModel;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CodeStyleManagerImpl extends CodeStyleManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.codeStyle.CodeStyleManagerImpl");
  private final Project myProject;
  @NonNls private static final String DUMMY_IDENTIFIER = "xxx";

  public CodeStyleManagerImpl(Project project) {
    myProject = project;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public PsiElement reformat(@NotNull PsiElement element) throws IncorrectOperationException {
    return reformat(element, false);
  }

  @NotNull
  public PsiElement reformat(@NotNull PsiElement element, boolean canChangeWhiteSpacesOnly) throws IncorrectOperationException {
    CheckUtil.checkWritable(element);
    if( !SourceTreeToPsiMap.hasTreeElement( element ) )
    {
      return element;
    }

    ASTNode treeElement = SourceTreeToPsiMap.psiElementToTree(element);
    PsiFileImpl file = (PsiFileImpl)element.getContainingFile();
    FileType fileType = StdFileTypes.JAVA;
    if (file != null) {
      fileType = file.getFileType();
    }
    Helper helper = HelperFactory.createHelper(fileType, myProject);
    final PsiElement formatted = SourceTreeToPsiMap.treeElementToPsi(
      new CodeFormatterFacade(getSettings(), helper).process(treeElement, -1));
    if (!canChangeWhiteSpacesOnly) {
      return postProcessElement(formatted);
    } else {
      return formatted;
    }

  }

  private PsiElement postProcessElement(final PsiElement formatted) {
    PsiElement result = formatted;
    for (PostFormatProcessor postFormatProcessor : Extensions.getExtensions(PostFormatProcessor.EP_NAME)) {
      result = postFormatProcessor.processElement(result, getSettings());
    }
    return result;
  }

  private void postProcessText(final PsiFile file, final TextRange textRange) {
    TextRange currentRange = textRange;
    for (final PostFormatProcessor myPostFormatProcessor : Extensions.getExtensions(PostFormatProcessor.EP_NAME)) {
      currentRange = myPostFormatProcessor.processText(file, currentRange, getSettings());
    }
  }

  public PsiElement reformatRange(@NotNull PsiElement element,
                                  int startOffset,
                                  int endOffset,
                                  boolean canChangeWhiteSpacesOnly) throws IncorrectOperationException {
    return reformatRangeImpl(element, startOffset, endOffset, canChangeWhiteSpacesOnly);
  }

  public PsiElement reformatRange(@NotNull PsiElement element, int startOffset, int endOffset)
    throws IncorrectOperationException {
    return reformatRangeImpl(element, startOffset, endOffset, false);

  }

  private static void transformAllChildren(final ASTNode file) {
    for (ASTNode child = file.getFirstChildNode(); child != null; child = child.getTreeNext()) {
      transformAllChildren(child);
    }
  }


  public void reformatText(@NotNull PsiFile file, int startOffset, int endOffset) throws IncorrectOperationException {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    CheckUtil.checkWritable(file);
    if (!SourceTreeToPsiMap.hasTreeElement(file)) {
      return;
    }

    ASTNode treeElement = SourceTreeToPsiMap.psiElementToTree(file);
    transformAllChildren(treeElement);

    FileType fileType = file.getFileType();
    Helper helper = HelperFactory.createHelper(fileType, myProject);
    final CodeFormatterFacade codeFormatter = new CodeFormatterFacade(getSettings(), helper);
    LOG.assertTrue(file.isValid());
    final PsiElement start = findElementInTreeWithFormatterEnabled(file, startOffset);
    final PsiElement end = findElementInTreeWithFormatterEnabled(file, endOffset);
    if (start != null && !start.isValid()) {
      LOG.error("start=" + start + "; file=" + file);
    }
    if (end != null && !end.isValid()) {
      LOG.error("end=" + start + "; end=" + file);
    }

    boolean formatFromStart = startOffset == 0;
    boolean formatToEnd = endOffset == file.getTextLength();

    final SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(getProject());
    final SmartPsiElementPointer startPointer = start == null ? null : smartPointerManager.createSmartPsiElementPointer(start);

    final SmartPsiElementPointer endPointer = end == null ? null : smartPointerManager.createSmartPsiElementPointer(end);

    codeFormatter.processTextWithPostponedFormatting(file, new FormatTextRanges(new TextRange(startOffset, endOffset), true));
    final PsiElement startElement = startPointer == null ? null : startPointer.getElement();
    final PsiElement endElement = endPointer == null ? null : endPointer.getElement();

    if ((startElement != null || formatFromStart) && (endElement != null || formatToEnd)) {
      postProcessText(file, new TextRange(formatFromStart ? 0 : startElement.getTextRange().getStartOffset(),
                                          formatToEnd ? file.getTextLength() : endElement.getTextRange().getEndOffset()));
    }
  }

  private PsiElement reformatRangeImpl(final PsiElement element,
                                       final int startOffset,
                                       final int endOffset,
                                       boolean canChangeWhiteSpacesOnly) throws IncorrectOperationException {
    LOG.assertTrue(element.isValid());
    CheckUtil.checkWritable(element);
    if( !SourceTreeToPsiMap.hasTreeElement( element ) )
    {
      return element;
    }

    ASTNode treeElement = SourceTreeToPsiMap.psiElementToTree(element);
    FileType fileType = StdFileTypes.JAVA;
    PsiFile file = element.getContainingFile();
    if (file != null) {
      fileType = file.getFileType();
    }
    Helper helper = HelperFactory.createHelper(fileType, myProject);
    final CodeFormatterFacade codeFormatter = new CodeFormatterFacade(getSettings(), helper);
    final PsiElement formatted = SourceTreeToPsiMap.treeElementToPsi(codeFormatter.processRange(treeElement, startOffset, endOffset));

    return canChangeWhiteSpacesOnly ? formatted : postProcessElement(formatted);
  }


  public void reformatNewlyAddedElement(@NotNull final ASTNode parent, @NotNull final ASTNode addedElement) throws IncorrectOperationException {

    LOG.assertTrue(addedElement.getTreeParent() == parent, "addedElement must be added to parent");

    final PsiElement psiElement = parent.getPsi();

    PsiFile containingFile = psiElement.getContainingFile();
    final FileViewProvider fileViewProvider = containingFile.getViewProvider();
    if (fileViewProvider instanceof MultiplePsiFilesPerDocumentFileViewProvider) {
      containingFile = fileViewProvider.getPsi(fileViewProvider.getBaseLanguage());
    }
    final FormattingModelBuilder builder = LanguageFormatting.INSTANCE.forContext(containingFile);

    if (builder != null) {
      final FormattingModel model = builder.createModel(containingFile, getSettings());
      FormatterEx.getInstanceEx().formatAroundRange(model, getSettings(), addedElement.getTextRange(), containingFile.getFileType());
    }

    adjustLineIndent(containingFile, addedElement.getTextRange());
  }

  public int adjustLineIndent(@NotNull final PsiFile file, final int offset) throws IncorrectOperationException {
    final Computable<Pair<Integer, IncorrectOperationException>> computable = new Computable<Pair<Integer, IncorrectOperationException>>() {
      public Pair<Integer, IncorrectOperationException> compute() {
        try {
          return new Pair<Integer, IncorrectOperationException>(adjustLineIndentInner(file, offset), null);
        }
        catch (IncorrectOperationException e) {
          return new Pair<Integer, IncorrectOperationException>(null, e);
        }
      }
    };
    final Pair<Integer, IncorrectOperationException> pair =
      PostprocessReformattingAspect.getInstance(file.getProject()).disablePostprocessFormattingInside(computable);
    if(pair.getSecond() != null) throw pair.getSecond();
    return pair.getFirst();
  }

  public int adjustLineIndentInner(PsiFile file, int offset) throws IncorrectOperationException {
    final PsiFile templateFile = PsiUtilBase.getTemplateLanguageFile(file);

    if (templateFile != null) {
      file = templateFile;
    }

    final PsiElement element = findElementInTreeWithFormatterEnabled(file, offset);
    if (element == null && offset != file.getTextLength()) {
      return offset;
    }
    if (element instanceof PsiComment && insideElement(element, offset)) {
      return CharArrayUtil.shiftForward(file.getViewProvider().getContents(), offset, " \t");
    }
    final FormattingModelBuilder builder = LanguageFormatting.INSTANCE.forContext(file);
    FormattingModelBuilder elementBuilder = builder;
    if (element != null) {
      elementBuilder = LanguageFormatting.INSTANCE.forContext(element);
    }
    if (builder != null && elementBuilder != null) {
      final CodeStyleSettings settings = getSettings();
      final CodeStyleSettings.IndentOptions indentOptions = settings.getIndentOptions(file.getFileType());
      final TextRange significantRange = getSignificantRange(file, offset);
      FormattingModel model = builder.createModel(file, settings);

      final Document doc = PsiDocumentManager.getInstance(myProject).getDocument(file);

      if (doc != null) {
        model = new DocumentBasedFormattingModel(model.getRootBlock(), doc, getProject(), settings, file.getFileType(), file);
      }

      return FormatterEx.getInstanceEx().adjustLineIndent(model, settings, indentOptions, offset, significantRange);
    }
    else {
      return offset;
    }
  }

  @Nullable
  public static PsiElement findElementInTreeWithFormatterEnabled(final PsiFile file, final int offset) {
    final PsiElement bottomost = file.findElementAt(offset);
    if (bottomost != null && LanguageFormatting.INSTANCE.forContext(bottomost) != null){
      return bottomost;
    }

    final Language fileLang = file.getLanguage();
    if (fileLang instanceof CompositeLanguage) {
      return file.getViewProvider().findElementAt(offset, fileLang);
    }

    return bottomost;
  }

  public int adjustLineIndent(@NotNull final Document document, final int offset) {
    return PostprocessReformattingAspect.getInstance(getProject()).disablePostprocessFormattingInside(new Computable<Integer>() {
      public Integer compute() {
        return adjustLineIndentInner(document, offset);
      }
    });
  }

  public int adjustLineIndentInner(Document document, int offset) {
    final PsiDocumentManager psiDocManager = PsiDocumentManager.getInstance(myProject);

    psiDocManager.commitDocument(document);

    PsiFile file = psiDocManager.getPsiFile(document);

    if (file == null) return offset;

    final PsiFile jspFile = PsiUtilBase.getTemplateLanguageFile(file);

    if (jspFile != null) {
      file = jspFile;
    }

    final PsiElement element = findElementInTreeWithFormatterEnabled(file, offset);
    if (element == null && offset != file.getTextLength()) {
      return offset;
    }
    if (element instanceof PsiComment && insideElement(element, offset)) {
      return CharArrayUtil.shiftForward(file.getViewProvider().getContents(), offset, " \t");
    }
    final FormattingModelBuilder builder = LanguageFormatting.INSTANCE.forContext(file);
    FormattingModelBuilder elementBuilder = builder;
    if (element != null) {
      elementBuilder = LanguageFormatting.INSTANCE.forContext(element);
    }
    if (builder != null && elementBuilder != null) {
      final CodeStyleSettings settings = getSettings();
      final CodeStyleSettings.IndentOptions indentOptions = settings.getIndentOptions(file.getFileType());
      final TextRange significantRange = getSignificantRange(file, offset);
      final FormattingModel model = builder.createModel(file, settings);

      final DocumentBasedFormattingModel documentBasedModel =
        new DocumentBasedFormattingModel(model.getRootBlock(), document, getProject(), settings, file.getFileType(), file);

      try {
        return FormatterEx.getInstanceEx().adjustLineIndent(documentBasedModel, settings, indentOptions, offset, significantRange);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
        return offset;
      }
    }
    else {
      return offset;
    }
  }

  public void adjustLineIndent(@NotNull PsiFile file, TextRange rangeToAdjust) throws IncorrectOperationException {
    final PsiFile templateFile = PsiUtilBase.getTemplateLanguageFile(file);

    if (templateFile != null) {
      file = templateFile;
    }
    final FormattingModelBuilder builder = LanguageFormatting.INSTANCE.forContext(file);
    if (builder != null) {
      final CodeStyleSettings settings = getSettings();
      final CodeStyleSettings.IndentOptions indentOptions = settings.getIndentOptions(file.getFileType());
      final FormattingModel model = builder.createModel(file, settings);

      final Document document = PsiDocumentManager.getInstance(getProject()).getDocument(file);

      FormatterEx.getInstanceEx().adjustLineIndentsForRange(new DocumentBasedFormattingModel(model.getRootBlock(), document, getProject(),
                                                                                             settings, file.getFileType(), file), 
                                                            settings,
                                                            indentOptions,
                                                            rangeToAdjust);
    }
  }

  @Nullable
  public String getLineIndent(@NotNull PsiFile file, int offset) {
    if (file instanceof PsiCompiledElement) {
      file = (PsiFile)((PsiCompiledElement)file).getMirror();
    }
    final PsiElement element = findElementInTreeWithFormatterEnabled(file, offset);
    if (element == null) {
      return null;
    }
    if (element instanceof PsiComment && insideElement(element, offset)) {
      return null;
    }
    final FormattingModelBuilder builder = LanguageFormatting.INSTANCE.forContext(file);
    final FormattingModelBuilder elementBuilder = LanguageFormatting.INSTANCE.forContext(element);
    if (builder != null && elementBuilder != null) {
      final CodeStyleSettings settings = getSettings();
      final CodeStyleSettings.IndentOptions indentOptions = settings.getIndentOptions(file.getFileType());
      final TextRange significantRange = getSignificantRange(file, offset);
      final FormattingModel model = builder.createModel(file, settings);

      return FormatterEx.getInstanceEx().getLineIndent(model, settings, indentOptions, offset, significantRange);
    }
    else {
      return null;
    }
  }

  @Nullable
  public String getLineIndent(@NotNull Editor editor) {
    Document doc = editor.getDocument();
    int offset = editor.getCaretModel().getOffset();
    if( offset >= doc.getTextLength() )
    {
      return "";
    }

    PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(doc);
    if (file == null) return "";

    return getLineIndent(file, offset);
  }

  private static boolean insideElement(final PsiElement element, final int offset) {
    final TextRange textRange = element.getTextRange();
    return textRange.getStartOffset() < offset && textRange.getEndOffset() >= offset;
  }

  private static TextRange getSignificantRange(final PsiFile file, final int offset) {
    final ASTNode elementAtOffset = SourceTreeToPsiMap.psiElementToTree(findElementInTreeWithFormatterEnabled(file, offset));
    if (elementAtOffset == null) {
      int significantRangeStart = CharArrayUtil.shiftBackward(file.getText(), offset - 1, "\r\t ");
      return new TextRange(significantRangeStart, offset);
    }

    final FormattingModelBuilder builder = LanguageFormatting.INSTANCE.forContext(file);
    final TextRange textRange = builder.getRangeAffectingIndent(file, offset, elementAtOffset);
    if (textRange != null) {
      return textRange;
    }

    return elementAtOffset.getTextRange();
  }

  public boolean isLineToBeIndented(@NotNull PsiFile file, int offset) {
    if (!SourceTreeToPsiMap.hasTreeElement(file)) {
      return false;
    }
    Helper helper = HelperFactory.createHelper(file.getFileType(), myProject);
    CharSequence chars = file.getViewProvider().getContents();
    int start = CharArrayUtil.shiftBackward(chars, offset - 1, " \t");
    if (start > 0 && chars.charAt(start) != '\n' && chars.charAt(start) != '\r') {
      return false;
    }
    int end = CharArrayUtil.shiftForward(chars, offset, " \t");
    if (end >= chars.length()) {
      return false;
    }
    ASTNode element = SourceTreeToPsiMap.psiElementToTree(findElementInTreeWithFormatterEnabled(file, end));
    if (element == null) {
      return false;
    }
    if (element.getElementType() == TokenType.WHITE_SPACE) {
      return false;
    }
    if (element.getElementType() == PlainTextTokenTypes.PLAIN_TEXT) {
      return false;
    }
    /*
    if( element.getElementType() instanceof IJspElementType )
    {
      return false;
    }
    */
    if (getSettings().KEEP_FIRST_COLUMN_COMMENT && isCommentToken(element)) {
      if (helper.getIndent(element, true) == 0) {
        return false;
      }
    }
    return true;
  }

  private static boolean isCommentToken(final ASTNode element) {
    final Language language = element.getElementType().getLanguage();
    final Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(language);
    if (commenter instanceof CodeDocumentationAwareCommenter) {
      final CodeDocumentationAwareCommenter documentationAwareCommenter = (CodeDocumentationAwareCommenter)commenter;
      return element.getElementType() == documentationAwareCommenter.getBlockCommentTokenType() ||
             element.getElementType() == documentationAwareCommenter.getLineCommentTokenType();
    }
    return false;
  }

  @Nullable
  public PsiElement insertNewLineIndentMarker(@NotNull PsiFile file, int offset) throws IncorrectOperationException {
    CheckUtil.checkWritable(file);
    final CharTable charTable = ((FileElement)SourceTreeToPsiMap.psiElementToTree(file)).getCharTable();
    PsiElement elementAt = findElementInTreeWithFormatterEnabled(file, offset);
    if( elementAt == null )
    {
      return null;
    }
    ASTNode element = SourceTreeToPsiMap.psiElementToTree(elementAt);
    ASTNode parent = element.getTreeParent();
    int elementStart = element.getTextRange().getStartOffset();
    if (element.getElementType() != TokenType.WHITE_SPACE) {
      /*
      if (elementStart < offset) return null;
      Element marker = Factory.createLeafElement(ElementType.NEW_LINE_INDENT, "###".toCharArray(), 0, "###".length());
      ChangeUtil.addChild(parent, marker, element);
      return marker;
      */
      return null;
    }

    ASTNode space1 = splitSpaceElement((TreeElement)element, offset - elementStart, charTable);
    ASTNode marker = Factory.createSingleLeafElement(TokenType.NEW_LINE_INDENT, DUMMY_IDENTIFIER, charTable, file.getManager());
    parent.addChild(marker, space1.getTreeNext());
    return SourceTreeToPsiMap.treeElementToPsi(marker);
  }

  public Indent getIndent(String text, FileType fileType) {
    int indent = HelperFactory.createHelper(fileType, myProject).getIndent(text, true);
    int indenLevel = indent / Helper.INDENT_FACTOR;
    int spaceCount = indent - indenLevel * Helper.INDENT_FACTOR;
    return new IndentImpl(getSettings(), indenLevel, spaceCount, fileType);
  }

  public String fillIndent(Indent indent, FileType fileType) {
    IndentImpl indent1 = (IndentImpl)indent;
    int indentLevel = indent1.getIndentLevel();
    int spaceCount = indent1.getSpaceCount();
    if (indentLevel < 0) {
      spaceCount += indentLevel * getSettings().getIndentSize(fileType);
      indentLevel = 0;
      if (spaceCount < 0) {
        spaceCount = 0;
      }
    }
    else {
      if (spaceCount < 0) {
        int v = (-spaceCount + getSettings().getIndentSize(fileType) - 1) / getSettings().getIndentSize(fileType);
        indentLevel -= v;
        spaceCount += v * getSettings().getIndentSize(fileType);
        if (indentLevel < 0) {
          indentLevel = 0;
        }
      }
    }
    return HelperFactory.createHelper(fileType, myProject).fillIndent(indentLevel * Helper.INDENT_FACTOR + spaceCount);
  }

  public Indent zeroIndent() {
    return new IndentImpl(getSettings(), 0, 0, null);
  }


  private static ASTNode splitSpaceElement(TreeElement space, int offset, CharTable charTable) {
    LOG.assertTrue(space.getElementType() == TokenType.WHITE_SPACE);
    CharSequence chars = space.getChars();
    LeafElement space1 = Factory.createSingleLeafElement(TokenType.WHITE_SPACE, chars, 0, offset, charTable, SharedImplUtil.getManagerByTree(space));
    LeafElement space2 = Factory.createSingleLeafElement(TokenType.WHITE_SPACE, chars, offset, chars.length(), charTable, SharedImplUtil.getManagerByTree(space));
    ASTNode parent = space.getTreeParent();
    parent.replaceChild(space, space1);
    parent.addChild(space2, space1.getTreeNext());
    return space1;
  }

  private CodeStyleSettings getSettings() {
    return CodeStyleSettingsManager.getSettings(myProject);
  }
}
