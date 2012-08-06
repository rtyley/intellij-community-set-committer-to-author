/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.xml.events.DomEvent;
import com.intellij.util.xml.impl.DomFileElementImpl;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class DomVirtualFileEventsTest extends DomHardCoreTestCase{

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    getDomManager().registerFileDescription(new DomFileDescription(MyElement.class, "a") {

      @Override
      public boolean isMyFile(@NotNull final XmlFile file, final Module module) {
        return super.isMyFile(file, module) && file.getName().contains("a");
      }
    }, getTestRootDisposable());
  }

  public void testCreateFile() throws Throwable {
    new WriteCommandAction.Simple(myProject) {
      @Override
      protected void run() throws Throwable {
        final VirtualFile dir = getVirtualFile(createTempDirectory());
        PsiTestUtil.addSourceContentToRoots(getModule(), dir);
        final VirtualFile childData = dir.createChildData(this, "abc.xml");
        System.gc();
        System.gc();
        System.gc();
        System.gc();
        assertResultsAndClear();
        VfsUtil.saveText(childData, "<a/>");
        assertEventCount(0);
        assertResultsAndClear();
      }
    }.execute().throwException();
  }

  public void testDeleteFile() throws Throwable {
    new WriteCommandAction.Simple(getProject()) {
      @Override
      protected void run() throws Throwable {
        final VirtualFile dir = getVirtualFile(createTempDirectory());
        PsiTestUtil.addSourceContentToRoots(getModule(), dir);
        final VirtualFile childData = dir.createChildData(this, "abc.xml");
        assertResultsAndClear();
        VfsUtil.saveText(childData, "<a/>");
        final DomFileElementImpl<DomElement> fileElement = getFileElement(childData);
        assertResultsAndClear();

        childData.delete(this);
        assertEventCount(1);
        putExpected(new DomEvent(fileElement, false));
        assertResultsAndClear();
        assertFalse(fileElement.isValid());
      }
    }.execute().throwException();
  }

  public void testRenameFile() throws Throwable {
    new WriteCommandAction.Simple(getProject()) {
      @Override
      protected void run() throws Throwable {
        final VirtualFile dir = getVirtualFile(createTempDirectory());
        PsiTestUtil.addSourceContentToRoots(getModule(), dir);
        final VirtualFile data = dir.createChildData(this, "abc.xml");
        VfsUtil.saveText(data, "<a/>");
        final DomFileElementImpl<DomElement> fileElement = getFileElement(data);
        assertEventCount(0);
        assertResultsAndClear();

        data.rename(this, "deaf.xml");
        assertEventCount(1);
        putExpected(new DomEvent(fileElement, false));
        assertResultsAndClear();
        assertEquals(fileElement, getFileElement(data));
        assertTrue(fileElement.isValid());

        data.rename(this, "fff.xml");
        assertEventCount(1);
        putExpected(new DomEvent(fileElement, false));
        assertResultsAndClear();
        assertNull(getFileElement(data));
        assertFalse(fileElement.isValid());
      }
    }.execute().throwException();
  }

  private DomFileElementImpl<DomElement> getFileElement(final VirtualFile file) {
    return getDomManager().getFileElement((XmlFile)getPsiManager().findFile(file));
  }

  public interface MyElement extends DomElement {
  }

}
