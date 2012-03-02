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

import com.intellij.designer.model.RadComponent;
import com.intellij.designer.propertyTable.Property;
import com.intellij.designer.propertyTable.PropertyEditor;
import com.intellij.designer.propertyTable.PropertyRenderer;
import com.intellij.designer.propertyTable.editors.AbstractTextFieldEditor;
import com.intellij.designer.propertyTable.renderers.LabelPropertyRenderer;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Alexander Lobas
 */
public class AttributeProperty extends Property {
  private final LabelPropertyRenderer myRenderer = new LabelPropertyRenderer(null);
  private final PropertyEditor myEditor = new AbstractTextFieldEditor() {
    @Override
    public Object getValue() throws Exception {
      return myTextField.getText();
    }
  };
  @NotNull private final String myAttribute;

  public AttributeProperty(Property parent, @NotNull @NonNls String name, @NotNull @NonNls String attribute) {
    super(parent, name);
    myAttribute = attribute;
  }

  @Override
  public Object getValue(RadComponent component) throws Exception {
    Object value = component.getClientProperty(myAttribute);
    if (value != null) {
      return value;
    }

    XmlTag tag = ((RadViewComponent)component).getTag();
    return tag == null ? null : tag.getAttributeValue(myAttribute);
  }

  @Override
  public void setValue(RadComponent component, Object value) throws Exception {
    component.putClientProperty(myAttribute, value);
  }

  @Override
  public boolean isDefaultValue(RadComponent component) throws Exception {
    XmlTag tag = ((RadViewComponent)component).getTag();
    Object value = component.getClientProperty(myAttribute);
    if (tag != null && value != null) {
      return value.equals(tag.getAttributeValue(myAttribute));
    }
    return true;
  }

  @Override
  public void setDefaultValue(RadComponent component) throws Exception {
    component.putClientProperty(myAttribute, null);
  }

  @NotNull
  @Override
  public PropertyRenderer getRenderer() {
    return myRenderer;
  }

  @Override
  public PropertyEditor getEditor() {
    return myEditor;
  }
}