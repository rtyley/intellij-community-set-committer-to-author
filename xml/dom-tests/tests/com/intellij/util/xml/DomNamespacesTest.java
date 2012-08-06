/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml;

import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.impl.DomFileElementImpl;
import com.intellij.util.xml.reflect.DomGenericInfo;

import java.util.List;

/**
 * @author peter
 */
public class DomNamespacesTest extends DomTestCase {

  public void testUseExistingNamespace() throws Throwable {
    final MyElement element = createElement("<a xmlns=\"foo\" xmlns:bar=\"bar\"/>", MyElement.class);
    registerNamespacePolicies(element);

    final XmlTag fooChildTag = element.getFooChild().ensureTagExists();
    assertEquals("foo-child", fooChildTag.getName());
    assertEquals("foo", fooChildTag.getNamespace());
    assertEquals("", fooChildTag.getNamespacePrefix());

    final XmlTag barChildTag = element.getBarChild().ensureTagExists();
    assertEquals("bar:bar-child", barChildTag.getName());
    assertEquals("bar", barChildTag.getNamespace());
    assertEquals("bar", barChildTag.getNamespacePrefix());
  }

  public void testDefineNewNamespace() throws Throwable {
    final MyElement element = createElement("<a/>", MyElement.class);
    registerNamespacePolicies(element);

    final XmlTag fooChildTag = element.getFooChild().ensureTagExists();
    assertEquals("foo-child", fooChildTag.getName());
    assertEquals("foo", fooChildTag.getNamespace());
    assertEquals("", fooChildTag.getNamespacePrefix());
    assertEquals("foo", fooChildTag.getAttributeValue("xmlns"));

    final XmlTag barChildTag = element.getBarChild().ensureTagExists();
    assertEquals("bar-child", barChildTag.getName());
    assertEquals("bar", barChildTag.getNamespace());
    assertEquals("", barChildTag.getNamespacePrefix());
    assertEquals("bar", barChildTag.getAttributeValue("xmlns"));
  }

  public void testCollectionChildNamespace() throws Throwable {
    final MyElement element = createElement("<a xmlns:foo=\"foo\"/>", MyElement.class);
    registerNamespacePolicies(element);

    final XmlTag fooChildTag = element.addFooElement().getXmlTag();
    assertEquals("foo:foo-element", fooChildTag.getName());
    assertEquals("foo", fooChildTag.getNamespace());
    assertEquals("foo", fooChildTag.getNamespacePrefix());
    assertNull(fooChildTag.getAttributeValue("xmlns"));
  }

  public void testNoNamespaceForFixedChild() throws Throwable {
    final MyElement element = createElement("<a xmlns:foo=\"foo\"/>", MyElement.class);
    registerNamespacePolicies(element);

    final XmlTag childTag = element.getSomeChild().ensureTagExists();
    assertEquals("some-child", childTag.getName());
    assertEquals("", childTag.getNamespace());
    assertEquals("", childTag.getNamespacePrefix());
    assertNull(childTag.getAttributeValue("xmlns"));
  }

  public void testNoNamespaceForCollectionChild() throws Throwable {
    final MyElement element = createElement("<a xmlns:foo=\"foo\"/>", MyElement.class);
    registerNamespacePolicies(element);

    final XmlTag childTag = element.addChild().getXmlTag();
    assertEquals("child", childTag.getName());
    assertEquals("", childTag.getNamespace());
    assertEquals("", childTag.getNamespacePrefix());
    assertNull(childTag.getAttributeValue("xmlns"));
  }

  public void testNamespaceEqualToParent() throws Throwable {
    final MyElement element = createElement("<a xmlns=\"foo\"/>", MyElement.class);
    registerNamespacePolicies(element);

    final XmlTag childTag = element.addChild().getXmlTag();
    assertEquals("child", childTag.getName());
    assertEquals("foo", childTag.getNamespace());
    assertEquals("", childTag.getNamespacePrefix());
    assertNull(childTag.getAttributeValue("xmlns"));
  }

  public void testNamespaceEqualToParent2() throws Throwable {
    final MyElement root = createElement("<a xmlns=\"foo\"/>", MyElement.class);
    registerNamespacePolicies(root);
    final MyFooElement element = root.addFooElement();

    final MyElement child = element.addChild();
    final XmlTag childTag = child.getXmlTag();
    assertEquals("child", childTag.getName());
    assertEquals("foo", childTag.getNamespace());
    assertEquals("", childTag.getNamespacePrefix());
    assertNull(childTag.getAttributeValue("xmlns"));

    assertEquals("foo", element.getXmlElementNamespaceKey());
    assertNull(child.getXmlElementNamespaceKey());
  }

  public void testHardcodedNamespacePrefix() throws Throwable {
    final XmlFile xmlFile = createXmlFile("<a xmlns:sys=\"\"/>");
    final MyElement element = getDomManager().getFileElement(xmlFile, MyElement.class, "a").getRootElement();
    final MyElement hardcodedElement = element.getHardcodedElement();
    new WriteCommandAction(getProject()) {
      @Override
      protected void run(Result result) throws Throwable {
        hardcodedElement.ensureTagExists();
      }
    }.execute();
    assertTrue(element.isValid());
    assertTrue(hardcodedElement.isValid());
    assertNotNull(hardcodedElement.getXmlElement());
    assertEquals("<sys:aaa/>", hardcodedElement.getXmlElement().getText());
    assertEquals("sys:aaa", hardcodedElement.getXmlTag().getName());

    new WriteCommandAction(getProject()) {
      @Override
      protected void run(Result result) throws Throwable {
        hardcodedElement.getHardcodedElement().getHardcodedElement().ensureTagExists();
      }
    }.execute();

    assertTrue(element.isValid());
    assertTrue(hardcodedElement.isValid());
    assertNotNull(hardcodedElement.getXmlElement());
    assertEquals("sys:aaa", hardcodedElement.getHardcodedElement().getHardcodedElement().getXmlTag().getName());

    assertEquals(1, element.getXmlTag().getSubTags().length);
  }

  public void testAutoChooseNamespaceIfPresent() throws Throwable {
    final MyElement root = createElement("<a xmlns=\"foo\"/>", MyElement.class);
    getDomManager().getDomFileDescription(root.getXmlElement()).registerNamespacePolicy("foo", "bar", "foo");

    final XmlTag fooChildTag = root.getFooChild().ensureTagExists();
    assertEquals("foo-child", fooChildTag.getName());
    assertEquals("foo", fooChildTag.getNamespace());
    assertEquals("", fooChildTag.getNamespacePrefix());
    assertEquals(0, fooChildTag.getAttributes().length);
  }

  public void testNonemptyRootTagPrefix() throws Throwable {
    getDomManager().registerFileDescription(new DomFileDescription<MyFooElement>(MyFooElement.class, "a", "foons") {

      @Override
      protected void initializeFileDescription() {
        super.initializeFileDescription();
        registerNamespacePolicy("foo", "foons");
      }
    }, getTestRootDisposable());

    final XmlFile psiFile = createXmlFile("<f:a xmlns:f=\"foons\"/>");
    final DomFileElementImpl<MyFooElement> element = getDomManager().getFileElement(psiFile, MyFooElement.class);
    assertNotNull(element);

    final MyFooElement root = element.getRootElement();
    assertNotNull(root);
    assertSame(psiFile.getDocument().getRootTag(), root.getXmlElement());
  }

  public void testSpringAopLike() throws Throwable {
    getDomManager().registerFileDescription(new DomFileDescription<MyBeans>(MyBeans.class, "beans", "beans", "aop") {

      @Override
      protected void initializeFileDescription() {
        super.initializeFileDescription();
        registerNamespacePolicy("beans", "beans");
        registerNamespacePolicy("aop", "aop");
      }
    }, getTestRootDisposable());

    final XmlFile psiFile = createXmlFile("<beans xmlns=\"beans\" xmlns:aop=\"aop\">" +
                                          "<aop:config>" +
                                          "<aop:pointcut/>" +
                                          "</aop:config>" +
                                          "</beans>");
    final MyBeans beans = getDomManager().getFileElement(psiFile, MyBeans.class).getRootElement();
    final DomElement pointcut =
      getDomManager().getDomElement(beans.getXmlTag().findFirstSubTag("aop:config").findFirstSubTag("aop:pointcut"));
    assertNotNull(pointcut);
    final MyAopConfig aopConfig = beans.getConfig();
    assertEquals(assertOneElement(aopConfig.getPointcuts()), pointcut);
  }

  public void testSpringUtilLike() throws Throwable {
    getDomManager().registerFileDescription(new DomFileDescription<MyBeans>(MyBeans.class, "beans", "beans", "util") {

      @Override
      protected void initializeFileDescription() {
        super.initializeFileDescription();
        registerNamespacePolicy("beans", "beans");
        registerNamespacePolicy("util", "util");
      }
    }, getTestRootDisposable());

    final XmlFile psiFile = createXmlFile("<beans xmlns=\"beans\" xmlns:util=\"util\">" +
                                          "<util:list>" +
                                          "<ref>aaa</ref>" +
                                          "<util:child>bbb</util:child>" +
                                          "</util:list></beans>");

    final MyList listOrSet = assertInstanceOf(getDomManager().getFileElement(psiFile, MyBeans.class).getRootElement().getList(), MyList.class);
    assertNotNull(listOrSet.getXmlTag());

    final XmlTag listTag = psiFile.getDocument().getRootTag().findFirstSubTag("util:list");
    assertNotNull(getDomManager().getDomElement(listTag.findFirstSubTag("ref")));
    assertNotNull(getDomManager().getDomElement(listTag.findFirstSubTag("util:child")));

    assertEquals("aaa", listOrSet.getRef().getValue());
    assertEquals("bbb", listOrSet.getChild().getValue());
  }

  private void registerNamespacePolicies(final MyElement element) {
    registerNamespacePolicies(element, "foo", "bar");
  }

  private void registerNamespacePolicies(final MyElement element, final String foo, final String bar) {
    final DomFileDescription description = getDomManager().getDomFileDescription(element.getXmlElement());
    description.registerNamespacePolicy("foo", foo);
    description.registerNamespacePolicy("bar", bar);
  }

  public void testFindChildDescriptionWithoutNamespace() throws Throwable {
    final DomGenericInfo info = getDomManager().getGenericInfo(MyListOrSet.class);
    assertNotNull(info.getAttributeChildDescription("attr"));
    assertNotNull(info.getAttributeChildDescription("attr").getType());
    assertNotNull(info.getCollectionChildDescription("child"));
    assertNotNull(info.getCollectionChildDescription("child").getType());
    assertNotNull(info.getFixedChildDescription("ref"));
    assertNotNull(info.getFixedChildDescription("ref").getType());
  }

  public void testCopyFromHonorsNamespaces() throws Throwable {
    final MyElement element = createElement("<a xmlns=\"foo\" xmlns:bar=\"bar\"/>", MyElement.class);
    registerNamespacePolicies(element);

    final MyElement element2 = createElement("<f:a xmlns:f=\"foo1\" xmlns:b=\"bar1\" xmlns=\"foo1\">" +
                                             "<foo-child/>" +
                                             "<b:bar-child/>" +
                                             "<f:some-child/>" +
                                             "<f:foo-element attr-2=\"239\" attr=\"42\"/>" +
                                             "<f:foo-element/>" +
                                             "<f:bool/>" +
                                             "<sys:aaa/>" +
                                             "</f:a>", MyElement.class);
    registerNamespacePolicies(element2, "foo1", "bar1");
    new WriteCommandAction(getProject()) {
      @Override
      protected void run(Result result) throws Throwable {
        element.copyFrom(element2);
      }
    }.execute();

    assertEquals("<a xmlns=\"foo\" xmlns:bar=\"bar\">" +
                 "<bar:bar-child/>" +
                 "<bool/>" +
                 "<foo-child/>" +
                 "<some-child/>" +
                 "<sys:aaa/>" +
                 "<foo-element attr=\"42\" attr-2=\"239\"/>" +
                 "<foo-element/>" +
                 "</a>",
                 element.getXmlTag().getText());
  }

  public void testAttributeWithAnotherNamespace() throws Throwable {
    final MyElement element = createElement("<a xmlns=\"foo\" xmlns:bar=\"bar\"><foo-child bar:my-attribute=\"xxx\"/></a>", MyElement.class);
    registerNamespacePolicies(element);
    final MyFooElement fooElement = element.getFooChild();
    final MyAttribute myAttribute = fooElement.getMyAttribute();
    assertNotNull(myAttribute.getXmlAttribute());
    assertEquals("xxx", myAttribute.getStringValue());
  }

  public interface MyElement extends DomElement {
    MyFooElement getFooChild();
    MyBarElement getBarChild();

    MyElement getSomeChild();

    List<MyFooElement> getFooElements();
    MyFooElement addFooElement();

    List<MyElement> getChildren();
    MyElement addChild();

    GenericAttributeValue<String> getAttr();
    GenericAttributeValue<String> getAttr2();

    @SubTag(indicator = true)
    GenericDomValue<Boolean> getBool();

    @SubTag("sys:aaa")
    MyElement getHardcodedElement();
  }

  @Namespace("foo")
  public interface MyFooElement extends MyElement {
    MyAttribute getMyAttribute();
  }

  @Namespace("bar")
  public interface MyBarElement extends MyElement {

  }

  @Namespace("bar")
  public interface MyAttribute extends GenericAttributeValue<String> {

  }

  @Namespace("beans")
  public interface MyBeans extends DomElement {
    MyAopConfig getConfig();
    MyList getList();
  }

  @Namespace("aop")
  public interface MyAopConfig extends DomElement {
    List<MySpringPointcut> getPointcuts();
  }

  @Namespace("aop")
  public interface MySpringPointcut extends DomElement {

  }

  @Namespace("beans")
  public interface MyListOrSet extends DomElement {
    GenericDomValue<String> getRef();
    List<GenericDomValue<String>> getChildren();
    GenericAttributeValue<String> getAttr();
  }

  @Namespace("util")
  public interface MyList extends MyListOrSet {
    GenericDomValue<String> getChild();
  }


}
