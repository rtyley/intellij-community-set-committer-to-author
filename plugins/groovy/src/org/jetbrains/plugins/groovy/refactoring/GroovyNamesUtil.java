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

package org.jetbrains.plugins.groovy.refactoring;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.Function;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyLexer;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author ven
 */
public class GroovyNamesUtil {

  public static boolean isIdentifier(String text) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    if (text == null) return false;

    Lexer lexer = new GroovyLexer();
    lexer.start(text);
    if (lexer.getTokenType() != GroovyTokenTypes.mIDENT) return false;
    lexer.advance();
    return lexer.getTokenType() == null;
  }

  public static boolean isKeyword(String text) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    Lexer lexer = new GroovyLexer();
    lexer.start(text);
    if (lexer.getTokenType() == null || !GroovyTokenTypes.KEYWORDS.contains(lexer.getTokenType())) return false;
    lexer.advance();
    return lexer.getTokenType() == null;
  }


  public static ArrayList<String> camelizeString(String str) {
    String tempString = str;
    tempString = deleteNonLetterFromString(tempString);
    ArrayList<String> camelizedTokens = new ArrayList<String>();
    if (!isIdentifier(tempString)) {
      return camelizedTokens;
    }
    String result = fromLowerLetter(tempString);
    while (!result.equals("")) {
      result = fromLowerLetter(result);
      String temp = "";
      while (!(result.length() == 0) && !result.substring(0, 1).toUpperCase().equals(result.substring(0, 1))) {
        temp += result.substring(0, 1);
        result = result.substring(1);
      }
      camelizedTokens.add(temp);
    }
    return camelizedTokens;
  }

  static String deleteNonLetterFromString(String tempString) {
    Pattern pattern = Pattern.compile("[^a-zA-Z]");
    Matcher matcher = pattern.matcher(tempString);
    return matcher.replaceAll("");
  }

  static String fromLowerLetter(String str) {
    if (str.length() == 0) return "";
    if (str.length() == 1) return str.toLowerCase();
    return str.substring(0, 1).toLowerCase() + str.substring(1);
  }

  public static String camelToSnake(final String string) {
    ArrayList<String> tokens = camelizeString(string);
    return StringUtil.join(ContainerUtil.map2Array(tokens, String.class, new Function<String, String>() {
      public String fun(final String s) {
        return StringUtil.decapitalize(s);
      }
    }), "-");
  }
}
