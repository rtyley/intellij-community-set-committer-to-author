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
package com.intellij.xml.index;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.ID;
import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
public class XmlNamespaceIndex extends XmlIndex<String>{

  @Nullable
  public static String getNamespace(VirtualFile file, final Project project) {
    final List<String> list = FileBasedIndex.getInstance().getValues(NAME, file.getUrl(), createFilter(project));
    return list.size() == 0 ? null : list.get(0);
  }

  public static Collection<VirtualFile> getFilesByNamespace(String namespace, final Project project) {
    return FileBasedIndex.getInstance().getContainingFiles(NAME, namespace, createFilter(project));
  }
  
  static void requestRebuild() {
    FileBasedIndex.getInstance().requestRebuild(NAME);
  }

  private static final ID<String,String> NAME = ID.create("XmlNamespaces");

  public ID<String, String> getName() {
    return NAME;
  }

  public DataIndexer<String, String, FileContent> getIndexer() {
    return new DataIndexer<String, String, FileContent>() {
      @NotNull
      public Map<String, String> map(final FileContent inputData) {
        final String ns = XsdNamespaceBuilder.computeNamespace(new ByteArrayInputStream(inputData.getContent()));
        final HashMap<String, String> map = new HashMap<String, String>(2);
        if (ns != null) {
          map.put(ns, "");
        }
        map.put(inputData.getFile().getUrl(), ns == null ? "" : ns);
        return map;
      }
    };
  }

  public DataExternalizer<String> getValueExternalizer() {
    return KEY_DESCRIPTOR;
  }
}
