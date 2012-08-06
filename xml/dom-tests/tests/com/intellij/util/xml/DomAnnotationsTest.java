/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.highlighting.*;

import java.util.Arrays;

/**
 * @author peter
 */
public class DomAnnotationsTest extends DomTestCase{

  @Override
  protected <T extends DomElement> T createElement(final String xml, final Class<T> aClass) {
    final String name = "a.xml";
    final XmlFile file = (XmlFile)PsiFileFactory.getInstance(getProject()).createFileFromText(name, StdFileTypes.XML, xml, 0,
                                                                                                              true);
    final XmlTag tag = file.getDocument().getRootTag();
    final String rootTagName = tag != null ? tag.getName() : "root";
    final T element = getDomManager().getFileElement(file, aClass, rootTagName).getRootElement();
    assertNotNull(element);
    assertSame(tag, element.getXmlTag());
    return element;
  }

  public void testResolveProblemsAreReportedOnlyOnce() throws Throwable {
    final MyElement myElement = createElement("<a><my-class>abc</my-class></a>", MyElement.class);
    
    new MockDomInspection(MyElement.class).checkFile(DomUtil.getFile(myElement), InspectionManager.getInstance(getProject()), true);
    final DomElementsProblemsHolder holder = DomElementAnnotationsManager.getInstance(getProject()).getProblemHolder(myElement);

    final DomElement element = myElement.getMyClass();
    assertEquals(0, holder.getProblems(myElement).size());
    assertEquals(0, holder.getProblems(myElement).size());
    assertEquals(1, holder.getProblems(element).size());
    assertEquals(1, holder.getProblems(element).size());
    assertEquals(1, holder.getProblems(myElement, true, true).size());
    assertEquals(1, holder.getProblems(myElement, true, true).size());
  }

  public void testMinSeverity() throws Throwable {
    final MyElement element = createElement("<a/>", MyElement.class);
    final DomElementsProblemsHolderImpl holder = new DomElementsProblemsHolderImpl(DomUtil.getFileElement(element));
    final DomElementProblemDescriptorImpl error = new DomElementProblemDescriptorImpl(element, "abc", HighlightSeverity.ERROR);
    final DomElementProblemDescriptorImpl warning = new DomElementProblemDescriptorImpl(element, "abc", HighlightSeverity.WARNING);
    holder.addProblem(error, MockDomInspection.class);
    holder.addProblem(warning, MockDomInspection.class);
    assertEquals(Arrays.asList(error), holder.getProblems(element, true, true, HighlightSeverity.ERROR));
    assertEquals(Arrays.asList(error, warning), holder.getProblems(element, true, true, HighlightSeverity.WARNING));
  }

  public interface MyElement extends DomElement{
    GenericDomValue<PsiClass> getMyClass();
  }

}
