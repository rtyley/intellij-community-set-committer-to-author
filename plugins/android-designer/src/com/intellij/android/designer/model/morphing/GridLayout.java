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
package com.intellij.android.designer.model.morphing;

import com.intellij.android.designer.model.*;
import com.intellij.android.designer.model.layout.grid.RadGridLayoutComponent;
import com.intellij.android.designer.model.layout.table.RadTableLayout;
import com.intellij.android.designer.model.layout.table.RadTableLayoutComponent;
import com.intellij.designer.model.MetaModel;
import com.intellij.designer.model.RadComponent;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;

/**
 * @author Alexander Lobas
 */
public class GridLayout {
  public static RadViewComponent TableLayout(RadViewComponent component, MetaModel target) throws Exception {
    final RadComponent[][] components = ((RadGridLayoutComponent)component).getGridComponents(false);
    final MetaModel tableRowModel = ViewsMetaManager.getInstance(component.getTag().getProject()).getModelByTag("TableRow");

    return new ComponentMorphingTool(component, new RadTableLayoutComponent(), target, new RadTableLayout()) {
      @Override
      protected void convertChildren() throws Exception {
        for (RadComponent[] rowComponents : components) {
          RadViewComponent newRowComponent = ModelParser.createComponent(null, tableRowModel);
          ModelParser.addComponent(myNewComponent, newRowComponent, null);

          for (RadComponent cellComponent : rowComponents) {
            if (cellComponent != null) {
              ModelParser.moveComponent(newRowComponent, (RadViewComponent)cellComponent, null);
            }
          }
        }
      }

      @Override
      protected void convertTag() {
        XmlTag tag = myNewComponent.getTag();
        ModelParser.deleteAttribute(tag, "android:rowCount");
        ModelParser.deleteAttribute(tag, "android:columnCount");

        for (RadComponent rowComponent : myNewComponent.getChildren()) {
          for (RadComponent cellComponent : rowComponent.getChildren()) {
            XmlTag cellTag = ((RadViewComponent)cellComponent).getTag();
            ModelParser.deleteAttribute(cellTag, "android:layout_row");
            ModelParser.deleteAttribute(cellTag, "android:layout_rowSpan");

            XmlAttribute attribute = cellTag.getAttribute("android:layout_columnSpan");
            if (attribute != null) {
              attribute.setName("android:layout_span");
            }
          }
        }
      }

      @Override
      protected void loadChildProperties(PropertyParser propertyParser) throws Exception {
      }
    }.result();
  }
}