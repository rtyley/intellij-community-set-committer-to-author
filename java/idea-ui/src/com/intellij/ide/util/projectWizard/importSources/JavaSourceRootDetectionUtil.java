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
package com.intellij.ide.util.projectWizard.importSources;

import com.intellij.ide.util.importProject.RootDetectionProcessor;
import com.intellij.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayCharSequence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class JavaSourceRootDetectionUtil {
  private static final TokenSet JAVA_FILE_FIRST_TOKEN_SET = TokenSet.orSet(
    ElementType.MODIFIER_BIT_SET,
    ElementType.CLASS_KEYWORD_BIT_SET,
    TokenSet.create(JavaTokenType.AT, JavaTokenType.IMPORT_KEYWORD)
  );

  private JavaSourceRootDetectionUtil() { }

  @NotNull
  public static Collection<JavaModuleSourceRoot> suggestRoots(@NotNull File dir) {
    final List<JavaSourceRootDetector> detectors = ContainerUtil.findAll(ProjectStructureDetector.EP_NAME.getExtensions(), JavaSourceRootDetector.class);
    final RootDetectionProcessor processor = new RootDetectionProcessor(dir, detectors.toArray(new JavaSourceRootDetector[detectors.size()]));
    final Map<ProjectStructureDetector,List<DetectedProjectRoot>> rootsMap = processor.findRoots();

    Map<File, JavaModuleSourceRoot> result = new HashMap<File, JavaModuleSourceRoot>();
    for (List<DetectedProjectRoot> roots : rootsMap.values()) {
      for (DetectedProjectRoot root : roots) {
        if (root instanceof JavaModuleSourceRoot) {
          final JavaModuleSourceRoot sourceRoot = (JavaModuleSourceRoot)root;
          final File directory = sourceRoot.getDirectory();
          final JavaModuleSourceRoot oldRoot = result.remove(directory);
          if (oldRoot != null) {
            result.put(directory, oldRoot.combineWith(sourceRoot));
          }
          else {
            result.put(directory, sourceRoot);
          }
        }
      }
    }
    return result.values();
  }


  @Nullable
  public static Pair<File,String> suggestRootForJavaFile(File javaFile, File topmostPossibleRoot) {
    if (!javaFile.isFile()) return null;

    final CharSequence chars;
    try {
      chars = new CharArrayCharSequence(FileUtil.loadFileText(javaFile));
    }
    catch(IOException e){
      return null;
    }

    String packageName = getPackageStatement(chars);
    if (packageName != null) {
      File root = javaFile.getParentFile();
      int index = packageName.length();
      while (index > 0) {
        int index1 = packageName.lastIndexOf('.', index - 1);
        String token = packageName.substring(index1 + 1, index);
        String dirName = root.getName();
        final boolean equalsToToken = SystemInfo.isFileSystemCaseSensitive ? dirName.equals(token) : dirName.equalsIgnoreCase(token);
        if (!equalsToToken || root.equals(topmostPossibleRoot)) {
          return Pair.create(root, packageName.substring(0, index));
        }
        String parent = root.getParent();
        if (parent == null) {
          return null;
        }
        root = new File(parent);
        index = index1;
      }
      return Pair.create(root, "");
    }

    return null;
  }

  @Nullable
  public static String getPackageStatement(CharSequence text) {
    Lexer lexer = new JavaLexer(LanguageLevel.JDK_1_3);
    lexer.start(text);
    skipWhiteSpaceAndComments(lexer);
    final IElementType firstToken = lexer.getTokenType();
    if (firstToken != JavaTokenType.PACKAGE_KEYWORD) {
      if (JAVA_FILE_FIRST_TOKEN_SET.contains(firstToken)) {
        return "";
      }
      return null;
    }
    lexer.advance();
    skipWhiteSpaceAndComments(lexer);

    final StringBuilder buffer = StringBuilderSpinAllocator.alloc();
    try {
      while(true){
        if (lexer.getTokenType() != JavaTokenType.IDENTIFIER) break;
        buffer.append(text, lexer.getTokenStart(), lexer.getTokenEnd());
        lexer.advance();
        skipWhiteSpaceAndComments(lexer);
        if (lexer.getTokenType() != JavaTokenType.DOT) break;
        buffer.append('.');
        lexer.advance();
        skipWhiteSpaceAndComments(lexer);
      }
      String packageName = buffer.toString();
      if (packageName.length() == 0 || StringUtil.endsWithChar(packageName, '.')) return null;
      return packageName;
    }
    finally {
      StringBuilderSpinAllocator.dispose(buffer);
    }
  }

  public static void skipWhiteSpaceAndComments(Lexer lexer){
    while(ElementType.JAVA_COMMENT_OR_WHITESPACE_BIT_SET.contains(lexer.getTokenType())) {
      lexer.advance();
    }
  }
}
