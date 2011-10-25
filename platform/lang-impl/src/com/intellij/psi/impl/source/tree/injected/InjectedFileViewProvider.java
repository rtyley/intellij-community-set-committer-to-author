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

package com.intellij.psi.impl.source.tree.injected;

import com.intellij.injected.editor.DocumentWindow;
import com.intellij.injected.editor.DocumentWindowImpl;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.FreeThreadedFileViewProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author cdr
*/
public class InjectedFileViewProvider extends SingleRootFileViewProvider implements FreeThreadedFileViewProvider {
  private Place myShreds;
  private Project myProject;
  private final Object myLock = new Object();
  private final DocumentWindow myDocumentWindow;
  private static final ThreadLocal<Boolean> disabledTemporarily = new ThreadLocal<Boolean>(){
    @Override
    protected Boolean initialValue() {
      return false;
    }
  };
  private boolean myPatchingLeaves;

  InjectedFileViewProvider(@NotNull PsiManager psiManager,
                           @NotNull VirtualFileWindow virtualFile,
                           @NotNull Place shreds,
                           @NotNull DocumentWindow documentWindow,
                           @NotNull Language language) {
    super(psiManager, (VirtualFile)virtualFile, true, language);
    myDocumentWindow = documentWindow;
    synchronized (myLock) {
      myShreds = shreds;
      myProject = myShreds.get(0).host.getProject();
    }
  }

  @Override
  public void rootChanged(PsiFile psiFile) {
    super.rootChanged(psiFile);
    if (!isPhysical()) return; // injected PSI change happened inside reparse; ignore
    if (myPatchingLeaves) return;

    List<PsiLanguageInjectionHost.Shred> shreds;
    synchronized (myLock) {
      shreds = myShreds;
    }
    DocumentWindowImpl documentWindow = (DocumentWindowImpl)myDocumentWindow;
    assert documentWindow.getHostRanges().length == shreds.size();
    String[] changes = documentWindow.calculateMinEditSequence(psiFile.getNode().getText());
    assert changes.length == shreds.size();
    for (int i = 0; i < changes.length; i++) {
      String change = changes[i];
      if (change != null) {
        PsiLanguageInjectionHost.Shred shred = shreds.get(i);
        PsiLanguageInjectionHost host = shred.host;
        TextRange rangeInsideHost = shred.getRangeInsideHost();
        String newHostText = StringUtil.replaceSubstring(host.getText(), rangeInsideHost, change);
        shred.host = host.updateText(newHostText);
      }
    }
  }

  @Override
  public FileViewProvider clone() {
    final DocumentWindow oldDocumentWindow = ((VirtualFileWindow)getVirtualFile()).getDocumentWindow();
    Document hostDocument = oldDocumentWindow.getDelegate();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getManager().getProject());
    PsiFile hostFile = documentManager.getPsiFile(hostDocument);
    final Language hostFileLanguage = getPsi(getBaseLanguage()).getContext().getContainingFile().getLanguage();
    PsiFile hostPsiFileCopy = (PsiFile)hostFile.copy();
    RangeMarker firstTextRange = oldDocumentWindow.getHostRanges()[0];
    PsiElement elementCopy = hostPsiFileCopy.getViewProvider().findElementAt(firstTextRange.getStartOffset(), hostFileLanguage);
    assert elementCopy != null;
    final Ref<FileViewProvider> provider = new Ref<FileViewProvider>();
    InjectedLanguageUtil.enumerate(elementCopy, hostPsiFileCopy, true, new PsiLanguageInjectionHost.InjectedPsiVisitor() {
      @Override
      public void visit(@NotNull PsiFile injectedPsi, @NotNull List<PsiLanguageInjectionHost.Shred> places) {
        Document document = documentManager.getCachedDocument(injectedPsi);
        if (document instanceof DocumentWindowImpl && oldDocumentWindow.areRangesEqual((DocumentWindowImpl)document)) {
          provider.set(injectedPsi.getViewProvider());
        }
      }
    });
    return provider.get();
  }

  @Override
  @Nullable
  protected PsiFile getPsiInner(Language target) {
    // when FileManager rebuilds file map, all files temporarily become invalid, so this check is doomed
    PsiFile file = super.getPsiInner(target);
    //if (file == null || file.getContext() == null) return null;
    return file;
  }

  void setShreds(Place shreds) {
    synchronized (myLock) {
      myShreds = new Place(shreds);
      myProject = shreds.get(0).host.getProject();
      ((DocumentWindowImpl)myDocumentWindow).setShreds(myShreds);
    }
  }

  boolean isValid() {
    return getShreds().isValid();
  }

  boolean isDisposed() {
    synchronized (myLock) {
      return myProject.isDisposed();
    }
  }

  Place getShreds() {
    synchronized (myLock) {
      return myShreds;
    }
  }

  @Override
  public DocumentWindow getDocument() {
    return myDocumentWindow;
  }

  @Override
  public boolean isEventSystemEnabled() {
    if (myLock == null) return true; // hack to avoid NPE when this method called from super class constructor
    return !disabledTemporarily.get();
  }

  @Override
  public boolean isPhysical() {
    return isEventSystemEnabled();
  }

  public void performNonPhysically(Runnable runnable) {
    synchronized (myLock) {
      disabledTemporarily.set(true);
      try {
        runnable.run();
      }
      finally {
        disabledTemporarily.set(false);
      }
    }
  }

  @Override
  public String toString() {
    return "Injected file '"+getVirtualFile().getName()+"' " + (isValid() ? "" : " invalid") + (isPhysical() ? "" : " nonphysical");
  }

  public void setPatchingLeaves(boolean patchingLeaves) {
    myPatchingLeaves = patchingLeaves;
  }
}
