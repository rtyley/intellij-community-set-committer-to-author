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
package com.intellij.cvsSupport2.cvsoperations.cvsAdd;

import com.intellij.cvsSupport2.application.CvsStorageComponent;
import com.intellij.cvsSupport2.application.CvsStorageSupportingDeletionComponent;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.keywordSubstitution.KeywordSubstitutionListWithSelection;
import com.intellij.cvsSupport2.keywordSubstitution.KeywordSubstitutionWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.impl.VcsPathPresenter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.IconUtil;
import com.intellij.util.ListWithSelection;
import org.jetbrains.annotations.NotNull;
import org.netbeans.lib.cvsclient.command.KeywordSubstitution;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.io.File;
import java.util.*;

/**
 * author: lesya
 */
public class AddedFileInfo extends DefaultMutableTreeNode {
  private final VirtualFile myAddedFile;
  private ListWithSelection mySubstitution = new ListWithSelection();
  private boolean myIncluded = true;
  private AddedFileInfo myParent;
  private final MyComparator myComparator = new MyComparator();
  @NotNull private final Project myProject;
  private final MyObservable myExcludedObservable = new MyObservable();
  private static final Icon OPEN_ICON = IconLoader.getIcon("/nodes/folderOpen.png");
  private static final Icon COLLAPSED_ICON = IconLoader.getIcon("/nodes/folder.png");

  public AddedFileInfo(VirtualFile addedFile, @NotNull Project project, CvsConfiguration config) {
    myAddedFile = addedFile;
    mySubstitution = KeywordSubstitutionListWithSelection.createOnFileName(myAddedFile.getName(), config);
    myProject = project;
  }

  public KeywordSubstitution getKeywordSubstitution() {
    if (myAddedFile.isDirectory()) {
      return null;
    }
    else {
      return ((KeywordSubstitutionWrapper)mySubstitution.getSelection()).getSubstitution();
    }
  }

  public VirtualFile getFile() {
    return myAddedFile;
  }

  public boolean included() {
    return myIncluded;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  public void setIncluded(boolean aBoolean) {

    if (myIncluded == aBoolean) return;

    myIncluded = aBoolean;

    if (!myIncluded) {
      excludeAllChildren();
    }
    else {
      encludeAllParents();
    }

    myExcludedObservable.setChanged();
    myExcludedObservable.notifyObservers();
  }

  private void encludeAllParents() {
    if (myParent != null) myParent.setIncluded(true);
  }

  private void excludeAllChildren() {
    for (int i = 0; i < getChildCount(); i++) {
      ((AddedFileInfo)getChildAt(i)).setIncluded(false);
    }
  }

  public void setParent(AddedFileInfo parent) {
    myParent = parent;
    myParent.add(this);
  }

  public String getPresentableText() {
    if (myParent == null) {
      return VcsPathPresenter.getInstance(myProject).getPresentableRelativePathFor(myAddedFile);
    }
    else {
      return myAddedFile.getName();
    }
  }

  public Icon getIcon(boolean expanded) {
    if (myAddedFile.isDirectory()) {
      return expanded ? OPEN_ICON : COLLAPSED_ICON;
    }
    else {
      return IconUtil.getIcon(myAddedFile, 0, myProject);
    }
  }

  public Collection<AddedFileInfo> collectAllIncludedFiles() {
    ArrayList<AddedFileInfo> result = new ArrayList<AddedFileInfo>();
    if (!myIncluded) return result;
    result.add(this);

    for (int i = 0; i < getChildCount(); i++) {
      result.addAll(((AddedFileInfo)getChildAt(i)).collectAllIncludedFiles());
    }

    return result;
  }

  public void sort() {
    if (children == null) return;
    Collections.sort(children, myComparator);
    for (Iterator each = children.iterator(); each.hasNext();) {
      ((AddedFileInfo)each.next()).sort();
    }
  }

  public ListWithSelection getKeywordSubstitutionsWithSelection() {
    return mySubstitution;
  }

  public void setKeywordSubstitution(KeywordSubstitution s) {
    mySubstitution.select(KeywordSubstitutionWrapper.getValue(s));
  }

  public File getPresentableFile() {
    return new File(getPresentableText());
  }

  private static class MyComparator implements Comparator {
    public int compare(Object o1, Object o2) {
      AddedFileInfo info1 = (AddedFileInfo)o1;
      AddedFileInfo info2 = (AddedFileInfo)o2;
      if (info1.getFile().isDirectory() && !info2.getFile().isDirectory()) return -1;
      if (!info1.getFile().isDirectory() && info2.getFile().isDirectory()) return 1;
      return info1.getPresentableText().compareTo(info2.getPresentableText());
    }
  }

  private static class MyObservable extends Observable {
    public synchronized void setChanged() {
      super.setChanged();
    }
  }

  public void addIncludedObserver(Observer observer) {
    myExcludedObservable.addObserver(observer);
    for (int i = 0; i < getChildCount(); i++) {
      ((AddedFileInfo)getChildAt(i)).addIncludedObserver(observer);
    }
  }

  public void removeIncludedObserver(Observer observer) {
    myExcludedObservable.deleteObserver(observer);
    for (int i = 0; i < getChildCount(); i++) {
      ((AddedFileInfo)getChildAt(i)).removeIncludedObserver(observer);
    }

  }

  public boolean hasIncludedNodes() {
    if (myIncluded) return true;
    for (int i = 0; i < getChildCount(); i++) {
      AddedFileInfo child = ((AddedFileInfo)getChildAt(i));
      if (child.hasIncludedNodes()) return true;
    }
    return false;
  }

  public void clearAllCvsAdminDirectoriesInIncludedDirectories() {
    if (!myIncluded) return;
    if (!myAddedFile.isDirectory()) return;
    CvsStorageComponent cvsStorageComponent = CvsStorageSupportingDeletionComponent.getInstance(myProject);
    cvsStorageComponent.deleteIfAdminDirCreated(myAddedFile);
  }

}
