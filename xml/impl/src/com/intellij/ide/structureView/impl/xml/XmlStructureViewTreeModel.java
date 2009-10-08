/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.ide.structureView.impl.xml;

import com.intellij.ide.structureView.StructureViewExtension;
import com.intellij.ide.structureView.StructureViewFactoryEx;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.TextEditorBasedStructureViewModel;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.lang.dtd.DTDLanguage;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.*;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class XmlStructureViewTreeModel extends TextEditorBasedStructureViewModel{
  private final XmlFile myFile;
  private static final Class[] myClasses = new Class[]{XmlTag.class, XmlFile.class, XmlEntityDecl.class, XmlElementDecl.class, XmlAttlistDecl.class, XmlConditionalSection.class};
  private static Sorter[] mySorters = {Sorter.ALPHA_SORTER};

  public XmlStructureViewTreeModel(XmlFile file) {
    super(file);
    myFile = file;
  }

  @NotNull
  public StructureViewTreeElement getRoot() {
    if (myFile.getLanguage() == DTDLanguage.INSTANCE) return new DtdFileTreeElement(myFile);
    return new XmlFileTreeElement(myFile);
  }

  public boolean shouldEnterElement(final Object element) {
    return element instanceof XmlTag && ((XmlTag)element).getSubTags().length > 0;
  }

  protected PsiFile getPsiFile() {
    return myFile;
  }

  @NotNull
  protected Class[] getSuitableClasses() {
    return myClasses;
  }

  public Object getCurrentEditorElement() {
    final Object editorElement = super.getCurrentEditorElement();
    if (editorElement instanceof XmlTag) {
      final Collection<StructureViewExtension> structureViewExtensions =
          StructureViewFactoryEx.getInstanceEx(myFile.getProject()).getAllExtensions(XmlTag.class);
      for(StructureViewExtension extension:structureViewExtensions) {
        final Object element = extension.getCurrentEditorElement(getEditor(), (PsiElement)editorElement);
        if (element != null) return element;
      }
    }
    return editorElement;
  }

  @NotNull
  public Sorter[] getSorters() {
    return mySorters;
  }
}
