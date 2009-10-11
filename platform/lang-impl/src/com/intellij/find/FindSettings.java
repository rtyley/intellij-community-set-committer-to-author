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

package com.intellij.find;

import com.intellij.openapi.components.ServiceManager;

import java.util.ArrayList;

public abstract class FindSettings{

  public static FindSettings getInstance() {
    return ServiceManager.getService(FindSettings.class);
  }

  public abstract boolean isSkipResultsWithOneUsage();

  public abstract void setSkipResultsWithOneUsage(boolean skip);

  public abstract boolean isSearchForTextOccurences();

  public abstract void setSearchForTextOccurences(boolean search);

  public abstract String getDefaultScopeName();

  public abstract void setDefaultScopeName(String scope);

  public abstract boolean isSearchOverloadedMethods();

  public abstract void setSearchOverloadedMethods (boolean search);

  public abstract boolean isForward();

  public abstract void setForward(boolean findDirectionForward);

  public abstract boolean isFromCursor();

  public abstract void setFromCursor(boolean findFromCursor);

  public abstract boolean isGlobal();

  public abstract void setGlobal(boolean findGlobalScope);

  public abstract boolean isCaseSensitive();

  public abstract void setCaseSensitive(boolean caseSensitiveSearch);

  public abstract boolean isLocalCaseSensitive();

  public abstract void setLocalCaseSensitive(boolean caseSensitiveSearch);

  public abstract boolean isPreserveCase();

  public abstract void setPreserveCase(boolean preserveCase);

  public abstract boolean isWholeWordsOnly();

  public abstract void setWholeWordsOnly(boolean wholeWordsOnly);

  public abstract boolean isLocalWholeWordsOnly();

  public abstract void setLocalWholeWordsOnly(boolean wholeWordsOnly);

  public abstract boolean isRegularExpressions();

  public abstract void setRegularExpressions(boolean regularExpressions);

  public abstract void addStringToFind(String s);

  public abstract void addStringToReplace(String s);

  public abstract void addDirectory(String s);

  public abstract String[] getRecentFindStrings();

  public abstract String[] getRecentReplaceStrings();

  /**
   * Returns the list of file masks used by the user in the "File name filter"
   * group box.
   *
   * @return the recent file masks list
   * @since 5.0.2
   */
  public abstract String[] getRecentFileMasks();

  public abstract ArrayList<String> getRecentDirectories();

  public abstract void setWithSubdirectories(boolean b);

  public abstract void initModelBySetings(FindModel model);

  public abstract String getFileMask();

  public abstract void setFileMask(String fileMask);
  public abstract void setCustomScope(String scopeName);
  public abstract String getCustomScope();

  public abstract boolean isInStringLiteralsOnly();
  public abstract void setInStringLiteralsOnly(boolean selected);

  public abstract boolean isInCommentsOnly();
  public abstract void setInCommentsOnly(boolean selected);
}
