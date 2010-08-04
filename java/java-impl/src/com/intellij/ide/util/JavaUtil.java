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
package com.intellij.ide.util;

import com.intellij.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.util.text.CharArrayCharSequence;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class JavaUtil {

  public static List<Pair<File,String>> suggestRoots(File dir) {
    ArrayList<Pair<File,String>> foundDirectories = new ArrayList<Pair<File, String>>();
    try{
      suggestRootsImpl(dir, dir, foundDirectories);
    }
    catch(PathFound found){
    }
    return foundDirectories;
  }

  private static class PathFound extends Exception {
    public File myDirectory;

    public PathFound(File directory) {
      myDirectory = directory;
    }
  }

  private static void suggestRootsImpl(File base, File dir, ArrayList<? super Pair<File, String>> foundDirectories) throws PathFound {
    if (!dir.isDirectory()) {
      return;
    }
    FileTypeManager typeManager = FileTypeManager.getInstance();
    if (typeManager.isFileIgnored(dir.getName())) {
      return;
    }
    final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
    if (progressIndicator != null) {
      if (progressIndicator.isCanceled()) {
        return;
      }
      progressIndicator.setText2(dir.getPath());
    }

    File[] list = dir.listFiles();
    if (list == null || list.length == 0) {
      return;
    }
    for (File child : list) {
      if (child.isFile()) {
        FileType type = typeManager.getFileTypeByFileName(child.getName());
        if (StdFileTypes.JAVA == type) {
          if (progressIndicator != null && progressIndicator.isCanceled()) {
            return;
          }
          Pair<File, String> root = suggestRootForJavaFile(child);
          if (root != null) {
            String packagePrefix = getPackagePrefix(base, root);
            if (packagePrefix == null) {
              foundDirectories.add(root);
            }
            else {
              foundDirectories.add(Pair.create(base, packagePrefix));
            }
            throw new PathFound(root.getFirst());
          }
          else {
            return;
          }
        }
      }
    }

    for (File child : list) {
      if (child.isDirectory()) {
        try {
          suggestRootsImpl(base, child, foundDirectories);
        }
        catch (PathFound found) {
          if (!found.myDirectory.equals(child)) {
            throw found;
          }
        }
      }
    }
  }

  private static String getPackagePrefix(File base, Pair<File,String> root) {
    String result = "";
    for (File parent = base; parent != null; parent = parent.getParentFile()) {
      if (parent.equals(root.getFirst())) {
        return root.getSecond() + (root.getSecond().length() > 0 && result.length() > 0 ? "." : "") + result;
      }
      result = parent.getName() + (result.length() > 0 ? "." : "") + result;
    }
    return null;
  }


  private static Pair<File,String> suggestRootForJavaFile(File javaFile) {
    if (!javaFile.isFile()) {
      return null;
    }

    try {
      final CharSequence chars = new CharArrayCharSequence(FileUtil.loadFileText(javaFile));

      String packageName = getPackageStatement(chars);
      if (packageName != null) {
        File root = javaFile.getParentFile();
        int index = packageName.length();
        while (index > 0) {
          int index1 = packageName.lastIndexOf('.', index - 1);
          String token = packageName.substring(index1 + 1, index);
          String dirName = root.getName();
          final boolean equalsToToken = SystemInfo.isFileSystemCaseSensitive ? dirName.equals(token) : dirName.equalsIgnoreCase(token);
          if (!equalsToToken) {
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
    }
    catch(IOException e){
      return null;
    }

    return null;
  }

  private static String getPackageStatement(CharSequence text){
    Lexer lexer = new JavaLexer(LanguageLevel.JDK_1_3);
    lexer.start(text);
    skipWhiteSpaceAndComments(lexer);
    if (lexer.getTokenType() != JavaTokenType.PACKAGE_KEYWORD) return "";
    lexer.advance();
    skipWhiteSpaceAndComments(lexer);
    StringBuilder buffer = new StringBuilder();
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

  private static void skipWhiteSpaceAndComments(Lexer lexer){
    while(ElementType.JAVA_COMMENT_OR_WHITESPACE_BIT_SET.contains(lexer.getTokenType())) {
      lexer.advance();
    }
  }
}
