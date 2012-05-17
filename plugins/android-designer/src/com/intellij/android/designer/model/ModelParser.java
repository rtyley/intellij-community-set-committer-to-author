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
package com.intellij.android.designer.model;

import com.android.ide.common.rendering.api.RenderSession;
import com.android.ide.common.rendering.api.ViewInfo;
import com.intellij.android.designer.designSurface.AndroidPasteFactory;
import com.intellij.android.designer.designSurface.RootView;
import com.intellij.designer.model.MetaManager;
import com.intellij.designer.model.MetaModel;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.model.RadLayout;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.util.XmlUtil;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Alexander Lobas
 */
public class ModelParser extends XmlRecursiveElementVisitor {
  public static final String NO_ROOT_CONTENT =
    "<?xml version=\"1.0\" encoding=\"utf-8\"?><LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\" android:layout_width=\"fill_parent\" android:layout_height=\"fill_parent\" android:orientation=\"vertical\"></LinearLayout>";

  public static final String XML_FILE_KEY = "XML_FILE";

  private static final int EMPTY_COMPONENT_SIZE = 5;

  private final IdManager myIdManager = new IdManager();

  private final MetaManager myMetaManager;
  private final XmlFile myXmlFile;
  private RadViewComponent myRootComponent;
  private RadViewComponent myComponent;
  private String myLayoutXmlText;

  public ModelParser(Project project, XmlFile xmlFile) {
    myMetaManager = ViewsMetaManager.getInstance(project);
    myXmlFile = xmlFile;
    parse();
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  //
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  private void parse() {
    myLayoutXmlText = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Override
      public String compute() {
        XmlTag root = myXmlFile.getRootTag();
        if (checkTag(root)) {
          root.accept(ModelParser.this);
          return myXmlFile.getText();
        }
        return NO_ROOT_CONTENT;
      }
    });
  }

  @Override
  public void visitXmlTag(XmlTag tag) {
    try {
      MetaModel metaModel = myMetaManager.getModelByTag(tag.getName());
      if (metaModel == null) {
        metaModel = myMetaManager.getModelByTag("<unknown>");
      }

      RadViewComponent component = createComponent(tag, metaModel);
      myIdManager.addComponent(component);

      if (myRootComponent == null) {
        myRootComponent = component;
      }
      if (myComponent != null) {
        myComponent.add(component, null);
      }
      myComponent = component;
      super.visitXmlTag(tag);
      myComponent = (RadViewComponent)component.getParent();
    }
    catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  //
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public static RadViewComponent createComponent(@Nullable XmlTag tag, MetaModel metaModel) throws Exception {
    RadViewComponent component = (RadViewComponent)metaModel.getModel().newInstance();
    component.setMetaModel(metaModel);
    component.setTag(tag);

    Class<RadLayout> layout = metaModel.getLayout();
    if (layout == null) {
      component.setLayout(RadViewLayout.INSTANCE);
    }
    else {
      component.setLayout(layout.newInstance());
    }

    return component;
  }

  public static void moveComponent(final RadViewComponent container,
                                   final RadViewComponent movedComponent,
                                   @Nullable final RadViewComponent insertBefore)
    throws Exception {
    movedComponent.removeFromParent();
    container.add(movedComponent, insertBefore);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        XmlTag xmlTag = movedComponent.getTag();

        XmlTag parentTag = container.getTag();
        XmlTag nextTag = insertBefore == null ? null : insertBefore.getTag();
        XmlTag newXmlTag;
        if (nextTag == null) {
          newXmlTag = parentTag.addSubTag(xmlTag, false);
        }
        else {
          newXmlTag = (XmlTag)parentTag.addBefore(xmlTag, nextTag);
        }

        xmlTag.delete();
        movedComponent.updateTag(newXmlTag);
      }
    });

    XmlFile xmlFile = container.getRoot().getClientProperty(XML_FILE_KEY);
    PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(xmlFile.getProject());
    psiDocumentManager.commitDocument(psiDocumentManager.getDocument(xmlFile));

    PropertyParser propertyParser = container.getRoot().getClientProperty(PropertyParser.KEY);
    propertyParser.load(movedComponent);
  }

  public static void addComponent(RadViewComponent container, final RadViewComponent newComponent, @Nullable RadViewComponent insertBefore)
    throws Exception {
    container.add(newComponent, insertBefore);

    addComponentTag(container.getTag(), newComponent, insertBefore == null ? null : insertBefore.getTag(), new Computable<String>() {
      @Override
      public String compute() {
        return newComponent.getMetaModel().getCreation();
      }
    });

    PropertyParser propertyParser = container.getRoot().getClientProperty(PropertyParser.KEY);
    propertyParser.load(newComponent);

    if (!newComponent.getTag().isEmpty()) {
      addComponent(newComponent, ViewsMetaManager.getInstance(newComponent.getTag().getProject()), propertyParser);
    }

    IdManager.get(container).ensureIds(newComponent);
  }

  private static void addComponent(RadViewComponent parentComponent,
                                   MetaManager metaManager,
                                   PropertyParser propertyParser) throws Exception {
    for (XmlTag tag : parentComponent.getTag().getSubTags()) {
      MetaModel metaModel = metaManager.getModelByTag(tag.getName());
      if (metaModel == null) {
        metaModel = metaManager.getModelByTag("<unknown>");
      }

      RadViewComponent component = createComponent(tag, metaModel);

      parentComponent.add(component, null);
      propertyParser.load(component);

      addComponent(component, metaManager, propertyParser);
    }
  }

  public static void pasteComponent(RadViewComponent container, RadViewComponent newComponent, @Nullable RadViewComponent insertBefore)
    throws Exception {
    container.add(newComponent, insertBefore);

    PropertyParser propertyParser = container.getRoot().getClientProperty(PropertyParser.KEY);
    pasteComponent(newComponent, container.getTag(), insertBefore == null ? null : insertBefore.getTag(), propertyParser);

    IdManager.get(container).ensureIds(newComponent);
  }

  private static void pasteComponent(final RadViewComponent component,
                                     XmlTag parentTag,
                                     XmlTag nextTag,
                                     PropertyParser propertyParser) throws Exception {
    addComponentTag(parentTag, component, nextTag, new Computable<String>() {
      @Override
      public String compute() {
        Element pasteProperties = component.extractClientProperty(AndroidPasteFactory.KEY);

        if (pasteProperties == null) {
          return component.getMetaModel().getCreation();
        }

        StringBuilder builder = new StringBuilder();
        builder.append("<").append(component.getMetaModel().getTag());

        for (Object object : pasteProperties.getAttributes()) {
          Attribute attribute = (Attribute)object;
          builder.append(" ").append(attribute.getName()).append("=\"").append(attribute.getValue()).append("\"");
        }

        for (Object object : pasteProperties.getChildren()) {
          Element element = (Element)object;
          String namespace = element.getName();

          for (Object child : element.getAttributes()) {
            Attribute attribute = (Attribute)child;
            builder.append(" ").append(namespace).append(":").append(attribute.getName()).append("=\"").append(attribute.getValue()).append(
              "\"");
          }
        }

        if (builder.indexOf("android:layout_width=\"") == -1) {
          builder.append(" android:layout_width=\"wrap_content\"");
        }
        if (builder.indexOf("android:layout_height=\"") == -1) {
          builder.append(" android:layout_height=\"wrap_content\"");
        }

        return builder.append("/>").toString();
      }
    });

    XmlTag xmlTag = component.getTag();
    List<RadComponent> children = component.getChildren();
    int size = children.size();
    for (int i = 0; i < size; i++) {
      RadViewComponent child = (RadViewComponent)children.get(i);

      XmlTag nextChildTag = null;
      if (i + 1 < size) {
        nextChildTag = ((RadViewComponent)children.get(i + 1)).getTag();
      }

      pasteComponent(child, xmlTag, nextChildTag, propertyParser);
    }

    propertyParser.load(component);
  }

  public static void addComponentTag(final XmlTag parentTag,
                                     final RadViewComponent component,
                                     final XmlTag nextTag,
                                     final Computable<String> tagBuilder) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        Project project;
        RadViewComponent root = null;
        XmlFile xmlFile = null;

        if (!checkTag(parentTag) && component.getParent() == component.getRoot()) {
          root = (RadViewComponent)component.getParent();
          xmlFile = root.getClientProperty(XML_FILE_KEY);
          project = xmlFile.getProject();
        }
        else {
          project = parentTag.getProject();
        }

        Language language = StdFileTypes.XML.getLanguage();
        XmlTag xmlTag =
          XmlElementFactory.getInstance(project).createTagFromText("\n" + tagBuilder.compute(), language);

        if (checkTag(parentTag)) {
          if (nextTag == null) {
            xmlTag = parentTag.addSubTag(xmlTag, false);
          }
          else {
            xmlTag = (XmlTag)parentTag.addBefore(xmlTag, nextTag);
          }
        }
        else {
          xmlTag.setAttribute("xmlns:android", "http://schemas.android.com/apk/res/android");
          xmlTag = (XmlTag)xmlFile.getDocument().add(xmlTag);
          XmlUtil.expandTag(xmlTag);

          root.setTag(xmlFile.getDocument().getRootTag());
        }

        component.setTag(xmlTag);
      }
    });
  }

  public static boolean checkTag(XmlTag tag) {
    try {
      return tag != null && tag.getFirstChild() != null && !(tag.getFirstChild() instanceof PsiErrorElement) && tag.getProject() != null;
    }
    catch (Throwable e) {
      return false;
    }
  }

  public static void deleteAttribute(RadComponent component, String name) {
    deleteAttribute(((RadViewComponent)component).getTag(), name);
  }

  public static void deleteAttribute(XmlTag tag, String name) {
    XmlAttribute attribute = tag.getAttribute(name);
    if (attribute != null) {
      attribute.delete();
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  //
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public RadViewComponent getRootComponent() {
    return myRootComponent;
  }

  public String getLayoutXmlText() {
    return myLayoutXmlText;
  }

  public void updateRootComponent(RenderSession session, RootView nativeComponent) throws Exception {
    if (myRootComponent == null) {
      myRootComponent = createComponent(myXmlFile.getRootTag(), myMetaManager.getModelByTag("<root>"));
    }
    else if (session.getRootViews().size() == 1) {
      RadViewComponent rootComponent = myRootComponent;
      myRootComponent = createComponent(myXmlFile.getRootTag(), myMetaManager.getModelByTag("<root>"));
      myRootComponent.add(rootComponent, null);
    }

    updateRootComponent(myRootComponent, session, nativeComponent);
    myRootComponent.setClientProperty(IdManager.KEY, myIdManager);
  }

  public static void updateRootComponent(RadViewComponent rootComponent,
                                         RenderSession session,
                                         RootView nativeComponent) {
    List<ViewInfo> views = session.getRootViews();
    List<RadComponent> children = rootComponent.getChildren();
    int size = children.size();
    for (int i = 0; i < size; i++) {
      updateComponent((RadViewComponent)children.get(i), views.get(i), nativeComponent, 0, 0);
    }

    rootComponent.setNativeComponent(nativeComponent);
    rootComponent.setBounds(0, 0, nativeComponent.getWidth(), nativeComponent.getHeight());
  }

  private static void updateComponent(RadViewComponent component,
                                      ViewInfo view,
                                      RootView nativeComponent,
                                      int parentX,
                                      int parentY) {
    component.setViewInfo(view);
    component.setNativeComponent(nativeComponent);

    int left = parentX + view.getLeft();
    int top = parentY + view.getTop();
    int width = view.getRight() - view.getLeft();
    int height = view.getBottom() - view.getTop();

    if (width < EMPTY_COMPONENT_SIZE && height < EMPTY_COMPONENT_SIZE) {
      nativeComponent.addEmptyRegion(left, top, EMPTY_COMPONENT_SIZE, EMPTY_COMPONENT_SIZE);
    }

    component.setBounds(left, top, Math.max(width, EMPTY_COMPONENT_SIZE), Math.max(height, EMPTY_COMPONENT_SIZE));

    List<ViewInfo> views = view.getChildren();
    List<RadComponent> children = component.getChildren();
    int size = children.size();

    for (int i = 0; i < size; i++) {
      updateComponent((RadViewComponent)children.get(i), views.get(i), nativeComponent, left, top);
    }
  }
}