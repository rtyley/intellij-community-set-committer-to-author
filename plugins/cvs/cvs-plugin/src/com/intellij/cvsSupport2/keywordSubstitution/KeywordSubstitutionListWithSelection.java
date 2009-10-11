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
package com.intellij.cvsSupport2.keywordSubstitution;

import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.util.ListWithSelection;

/**
 * author: lesya
 */
public class KeywordSubstitutionListWithSelection extends ListWithSelection<KeywordSubstitutionWrapper> {

  public KeywordSubstitutionListWithSelection() {

    add(KeywordSubstitutionWrapper.BINARY);
    add(KeywordSubstitutionWrapper.KEYWORD_COMPRESSION);
    add(KeywordSubstitutionWrapper.KEYWORD_EXPANSION);
    add(KeywordSubstitutionWrapper.KEYWORD_EXPANSION_LOCKER);
    add(KeywordSubstitutionWrapper.KEYWORD_REPLACEMENT);
    add(KeywordSubstitutionWrapper.NO_SUBSTITUTION);
  }

  public static KeywordSubstitutionListWithSelection createOnFileName(String fileName,
                                                                      CvsConfiguration config){
    KeywordSubstitutionListWithSelection result = new KeywordSubstitutionListWithSelection();
    boolean binary = FileTypeManager.getInstance().getFileTypeByFileName(fileName).isBinary();
    result.select(binary ? KeywordSubstitutionWrapper.BINARY :
        KeywordSubstitutionWrapper.getValue(config.DEFAULT_TEXT_FILE_SUBSTITUTION));
    return result;
    
  }
  
  public static KeywordSubstitutionListWithSelection createOnExtension(String extension){
    KeywordSubstitutionListWithSelection result = new KeywordSubstitutionListWithSelection();
    boolean binary = FileTypeManager.getInstance().getFileTypeByExtension(extension).isBinary();
    result.select(binary ? KeywordSubstitutionWrapper.BINARY : KeywordSubstitutionWrapper.KEYWORD_EXPANSION);
    return result;

  }

}
