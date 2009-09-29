package com.intellij.history.integration.revertion;

import com.intellij.history.core.Paths;
import com.intellij.history.core.changes.*;
import com.intellij.history.core.storage.Content;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashSet;
import com.intellij.util.io.ReadOnlyAttributeUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ChangeRevertionVisitor extends ChangeVisitor {
  private final IdeaGateway myGateway;
  private final Set<DelayedApply> myDelayedApplies = new HashSet<DelayedApply>();

  public ChangeRevertionVisitor(IdeaGateway gw) {
    myGateway = gw;
  }

  @Override
  public void visit(CreateEntryChange c) throws IOException, StopVisitingException {
    if (shouldRevert(c)) {
      Entry e = getAffectedEntry(c);
      VirtualFile f = myGateway.findVirtualFile(e.getPath());
      if (f != null) {
        unregisterDelayedApplies(f);
        f.delete(this);
      }
    }

    c.revertOn(myRoot);

    checkShouldStop(c);
  }

  @Override
  public void visit(ContentChange c) throws IOException, StopVisitingException {
    c.revertOn(myRoot);

    if (shouldRevert(c)) {
      Entry e = getAffectedEntry(c);
      VirtualFile f = myGateway.findVirtualFile(e.getPath());
      registerDelayedContentApply(f, e);
    }

    checkShouldStop(c);
  }

  @Override
  public void visit(RenameChange c) throws IOException, StopVisitingException {
    if (shouldRevert(c)) {
      Entry e = getAffectedEntry(c);
      VirtualFile f = myGateway.findOrCreateFileSafely(e.getPath(), e.isDirectory());
      c.revertOn(myRoot);

      String newName = getName(e);
      VirtualFile existing = f.getParent().findChild(newName);
      if (existing != null && existing != f) {
        existing.delete(this);
      }
      f.rename(this, newName);
    }
    else {
      c.revertOn(myRoot);
    }

    checkShouldStop(c);
  }

  @Override
  public void visit(ROStatusChange c) throws IOException, StopVisitingException {
    if (shouldRevert(c)) {
      Entry e = getAffectedEntry(c);
      VirtualFile f = myGateway.findOrCreateFileSafely(e.getPath(), e.isDirectory());
      c.revertOn(myRoot);

      registerDelayedROStatusApply(f, e);
    }
    else {
      c.revertOn(myRoot);
    }

    checkShouldStop(c);
  }

  @Override
  public void visit(MoveChange c) throws IOException, StopVisitingException {
    if (shouldRevert(c)) {
      Entry e = getAffectedEntry(c, 1);
      VirtualFile f = myGateway.findOrCreateFileSafely(e.getPath(), e.isDirectory());

      c.revertOn(myRoot);
      String parentPath = getParentPath(getAffectedEntry(c));
      VirtualFile parent = myGateway.findOrCreateFileSafely(parentPath, true);

      VirtualFile existing = parent.findChild(e.getName());
      if (existing != null) existing.delete(this);
      f.move(this, parent);
    }
    else {
      c.revertOn(myRoot);
    }

    checkShouldStop(c);
  }

  @Override
  public void visit(DeleteChange c) throws IOException, StopVisitingException {
    c.revertOn(myRoot);

    if (shouldRevert(c)) {
      Entry e = getAffectedEntry(c);

      revertDeletion(e);
    }

    checkShouldStop(c);
  }

  protected boolean shouldRevert(Change c) {
    return true;
  }

  protected void checkShouldStop(Change c) throws StopVisitingException {
  }

  private void revertDeletion(Entry e) throws IOException {
    VirtualFile f = myGateway.findOrCreateFileSafely(e, e.getPath(), e.isDirectory());
    if (e.isDirectory()) {
      for (Entry child : e.getChildren()) revertDeletion(child);
    }
    else {
      registerDelayedContentApply(f, e);
      registerDelayedROStatusApply(f, e);
    }
  }

  private void registerDelayedContentApply(VirtualFile f, Entry e) {
    registerDelayedApply(new DelayedContentApply(f, e));
  }

  private void registerDelayedROStatusApply(VirtualFile f, Entry e) {
    registerDelayedApply(new DelayedROStatusApply(f, e));
  }

  private void registerDelayedApply(DelayedApply a) {
    myDelayedApplies.remove(a);
    myDelayedApplies.add(a);
  }

  private void unregisterDelayedApplies(VirtualFile fileOrDir) {
    List<DelayedApply> toRemove = new ArrayList<DelayedApply>();

    for (DelayedApply a : myDelayedApplies) {
      if (VfsUtil.isAncestor(fileOrDir, a.getFile(), false)) {
        toRemove.add(a);
      }
    }

    for (DelayedApply a : toRemove) {
      myDelayedApplies.remove(a);
    }
  }

  @Override
  public void finished() throws IOException {
    for (DelayedApply a : myDelayedApplies) a.apply();
  }

  protected Entry getAffectedEntry(StructuralChange c) {
    return getAffectedEntry(c, 0);
  }

  private Entry getAffectedEntry(StructuralChange c, int i) {
    return myRoot.getEntry(c.getAffectedIdPaths()[i]);
  }

  private String getParentPath(Entry e) {
    return Paths.getParentOf(e.getPath());
  }

  private String getName(Entry e) {
    return Paths.getNameOf(e.getPath());
  }

  private static abstract class DelayedApply {
    protected VirtualFile myFile;

    protected DelayedApply(VirtualFile f) {
      myFile = f;
    }

    public VirtualFile getFile() {
      return myFile;
    }

    public abstract void apply() throws IOException;

    @Override
    public boolean equals(Object o) {
      if (!getClass().equals(o.getClass())) return false;
      return myFile.equals(((DelayedApply)o).myFile);
    }

    @Override
    public int hashCode() {
      return getClass().hashCode() + 32 * myFile.hashCode();
    }
  }

  private static class DelayedContentApply extends DelayedApply {
    private final Content myContent;
    private final long myTimestamp;

    public DelayedContentApply(VirtualFile f, Entry e) {
      super(f);
      myContent = e.getContent();
      myTimestamp = e.getTimestamp();
    }

    @Override
    public void apply() throws IOException {
      if (!myContent.isAvailable()) return;

      boolean isReadOnly = !myFile.isWritable();
      ReadOnlyAttributeUtil.setReadOnlyAttribute(myFile, false);

      myFile.setBinaryContent(myContent.getBytes(), -1, myTimestamp);
      
      ReadOnlyAttributeUtil.setReadOnlyAttribute(myFile, isReadOnly);
    }
  }

  private static class DelayedROStatusApply extends DelayedApply {
    private final boolean isReadOnly;

    private DelayedROStatusApply(VirtualFile f, Entry e) {
      super(f);
      isReadOnly = e.isReadOnly();
    }

    public void apply() throws IOException {
      ReadOnlyAttributeUtil.setReadOnlyAttribute(myFile, isReadOnly);
    }
  }
}
