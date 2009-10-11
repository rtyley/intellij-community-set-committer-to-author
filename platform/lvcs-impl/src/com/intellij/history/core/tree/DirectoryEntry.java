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

package com.intellij.history.core.tree;

import com.intellij.history.core.Paths;
import com.intellij.history.core.revisions.Difference;
import com.intellij.history.core.storage.Stream;
import com.intellij.util.SmartList;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DirectoryEntry extends Entry {
  private final List<Entry> myChildren;

  public DirectoryEntry(int id, String name) {
    super(id, name);
    myChildren = new ArrayList<Entry>(3);
  }

  public DirectoryEntry(Stream s) throws IOException {
    super(s);
    int count = s.readInteger();
    myChildren = count == 0 ? new SmartList<Entry>() : new ArrayList<Entry>(count);
    while (count-- > 0) {
      unsafeAddChild(s.readEntry());
    }
  }

  @Override
  public void write(Stream s) throws IOException {
    super.write(s);
    s.writeInteger(myChildren.size());
    for (Entry child : myChildren) {
      s.writeEntry(child);
    }
  }

  protected String getPathAppendedWith(String name) {
    return Paths.appended(getPath(), name);
  }

  @Override
  public boolean isDirectory() {
    return true;
  }

  @Override
  public void addChild(Entry child) {
    checkDoesNotExist(child, child.getName());
    unsafeAddChild(child);
  }

  private void unsafeAddChild(Entry child) {
    myChildren.add(child);
    child.setParent(this);
  }

  protected void checkDoesNotExist(Entry e, String name) {
    Entry found = findChild(name);
    if (found == null || found == e) return;

    throw new RuntimeException(String.format("entry '%s' already exists in '%s'", name, getPath()));
  }

  @Override
  public void removeChild(Entry child) {
    myChildren.remove(child);
    child.setParent(null);
  }

  @Override
  public List<Entry> getChildren() {
    return myChildren;
  }

  @Override
  public boolean hasUnavailableContent(List<Entry> entriesWithUnavailableContent) {
    for (Entry e : myChildren) {
      e.hasUnavailableContent(entriesWithUnavailableContent);
    }
    return !entriesWithUnavailableContent.isEmpty();
  }

  @Override
  public DirectoryEntry copy() {
    DirectoryEntry result = copyEntry();
    for (Entry child : myChildren) {
      result.unsafeAddChild(child.copy());
    }
    return result;
  }

  protected DirectoryEntry copyEntry() {
    return new DirectoryEntry(myId, myName);
  }

  @Override
  public void collectDifferencesWith(Entry right, List<Difference> result) {
    DirectoryEntry e = (DirectoryEntry)right;

    if (!getPath().equals(e.getPath())) {
      result.add(new Difference(false, this, e));
    }

    addCreatedChildrenDifferences(e, result);
    addDeletedChildrenDifferences(e, result);
    addModifiedChildrenDifferences(e, result);
  }

  private void addCreatedChildrenDifferences(DirectoryEntry e, List<Difference> result) {
    for (Entry child : e.myChildren) {
      if (findDirectChild(child.getId()) == null) {
        child.collectCreatedDifferences(result);
      }
    }
  }

  private void addDeletedChildrenDifferences(DirectoryEntry e, List<Difference> result) {
    for (Entry child : myChildren) {
      if (e.findDirectChild(child.getId()) == null) {
        child.collectDeletedDifferences(result);
      }
    }
  }

  private void addModifiedChildrenDifferences(DirectoryEntry e, List<Difference> result) {
    for (Entry myChild : myChildren) {
      Entry itsChild = e.findDirectChild(myChild.getId());
      if (itsChild != null) {
        myChild.collectDifferencesWith(itsChild, result);
      }
    }
  }

  Entry findDirectChild(int id) {
    for (Entry child : getChildren()) {
      if (child.getId() == id) return child;
    }
    return null;
  }

  @Override
  protected void collectCreatedDifferences(List<Difference> result) {
    result.add(new Difference(false, null, this));

    for (Entry child : myChildren) {
      child.collectCreatedDifferences(result);
    }
  }

  @Override
  protected void collectDeletedDifferences(List<Difference> result) {
    result.add(new Difference(false, this, null));
    
    for (Entry child : myChildren) {
      child.collectDeletedDifferences(result);
    }
  }
}
