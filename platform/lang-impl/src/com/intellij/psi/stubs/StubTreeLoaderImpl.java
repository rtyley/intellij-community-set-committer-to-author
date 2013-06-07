/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.stubs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.*;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

/**
 * @author yole
 */
public class StubTreeLoaderImpl extends StubTreeLoader {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.stubs.StubTreeLoaderImpl");

  @Override
  @Nullable
  public ObjectStubTree readOrBuild(Project project, final VirtualFile vFile, @Nullable PsiFile psiFile) {
    final ObjectStubTree fromIndices = readFromVFile(project, vFile);
    if (fromIndices != null) {
      return fromIndices;
    }

    if (!canHaveStub(vFile)) {
      return null;
    }

    try {
      final FileContent fc = new FileContentImpl(vFile, vFile.contentsToByteArray());
      fc.putUserData(IndexingDataKeys.PROJECT, project);
      if (psiFile != null) {
        fc.putUserData(IndexingDataKeys.PSI_FILE, psiFile);
        if (!vFile.getFileType().isBinary()) {
          fc.putUserData(IndexingDataKeys.FILE_TEXT_CONTENT_KEY, psiFile.getViewProvider().getContents());
        }
        psiFile.putUserData(PsiFileImpl.BUILDING_STUB, true);
      }
      Stub element;
      try {
        element = StubTreeBuilder.buildStubTree(fc);
      }
      finally {
        if (psiFile != null) {
          psiFile.putUserData(PsiFileImpl.BUILDING_STUB, null);
        }
      }
      if (element instanceof PsiFileStub) {
        StubTree tree = new StubTree((PsiFileStub)element);
        tree.setDebugInfo("created from file content, timestamp=" + vFile.getTimeStamp());
        return tree;
      }
    }
    catch (IOException e) {
      throw new RuntimeException("Corrupted file '" + vFile.getPath() + "': " + e.getMessage(), e);
    }

    return null;
  }

  @Override
  @Nullable
  public ObjectStubTree readFromVFile(Project project, final VirtualFile vFile) {
    if (DumbService.getInstance(project).isDumb()) {
      return null;
    }

    final int id = Math.abs(FileBasedIndex.getFileId(vFile));
    if (id <= 0) {
      return null;
    }

    final List<SerializedStubTree> datas = FileBasedIndex.getInstance().getValues(StubUpdatingIndex.INDEX_ID, id, GlobalSearchScope
        .fileScope(project, vFile));
    final int size = datas.size();

    if (size == 1) {
      Stub stub;
      try {
        stub = datas.get(0).getStub(false);
      }
      catch (SerializerNotFoundException e) {
        return processError(vFile, "No stub serializer: " + vFile.getPresentableUrl() + ": " + e.getMessage(), e);
      }
      ObjectStubTree tree = stub instanceof PsiFileStub ? new StubTree((PsiFileStub)stub) : new ObjectStubTree((ObjectStubBase)stub, true);
      tree.setDebugInfo("created from index, index creation stamp=" + IndexInfrastructure.getIndexCreationStamp(StubUpdatingIndex.INDEX_ID) + "; index stamp=" + IndexingStamp.getIndexStamp(vFile, StubUpdatingIndex.INDEX_ID));
      return tree;
    }
    else if (size != 0) {
      return processError(vFile, "Twin stubs: " + vFile.getPresentableUrl() + " has " + size + " stub versions. Should only have one. id=" + id,
                          null);
    }

    return null;
  }

  private static ObjectStubTree processError(final VirtualFile vFile, String message, @Nullable Exception e) {
    LOG.error(message, e);

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        final Document doc = FileDocumentManager.getInstance().getCachedDocument(vFile);
        if (doc != null) {
          FileDocumentManager.getInstance().saveDocument(doc);
        }
      }
    }, ModalityState.NON_MODAL);

    FileBasedIndex.getInstance().requestReindex(vFile);
    return null;
  }

  @Override
  public void rebuildStubTree(VirtualFile virtualFile) {
    FileBasedIndex.getInstance().requestReindex(virtualFile);
  }

  @Override
  public long getStubTreeTimestamp(VirtualFile vFile) {
    return IndexingStamp.getIndexStamp(vFile, StubUpdatingIndex.INDEX_ID);
  }

  @Override
  public boolean canHaveStub(VirtualFile file) {
    return StubUpdatingIndex.canHaveStub(file);
  }
}
