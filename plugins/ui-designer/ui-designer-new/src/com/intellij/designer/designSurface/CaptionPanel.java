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
package com.intellij.designer.designSurface;

import com.intellij.designer.actions.CommonEditActionsProvider;
import com.intellij.designer.designSurface.tools.InputTool;
import com.intellij.designer.model.FindComponentVisitor;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.model.RadVisualComponent;
import com.intellij.ide.DeleteProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.Collections;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class CaptionPanel extends JLayeredPane implements DataProvider, DeleteProvider {
  private final boolean myHorizontal;
  private final EditableArea myMainArea;
  private final EditableArea myArea;
  private final DecorationLayer myDecorationLayer;
  private final FeedbackLayer myFeedbackLayer;
  private final CommonEditActionsProvider myActionsProvider;
  private final RadVisualComponent myRootComponent;
  private List<RadComponent> myRootChildren = Collections.emptyList();

  public CaptionPanel(DesignerEditorPanel designer, boolean horizontal) {
    setBorder(IdeBorderFactory.createBorder(horizontal ? SideBorder.BOTTOM : SideBorder.RIGHT));
    setFocusable(true);

    myHorizontal = horizontal;
    myMainArea = designer.getSurfaceArea();

    myRootComponent = new RadVisualComponent() {
      @Override
      public List<RadComponent> getChildren() {
        return myRootChildren;
      }

      @Override
      public boolean canDelete() {
        return false;
      }
    };
    myRootComponent.setNativeComponent(this);
    myRootComponent.setBounds(0, 0, 100000, 100000);

    myArea = new ComponentEditableArea(this) {
      @Override
      protected void fireSelectionChanged() {
        super.fireSelectionChanged();
        revalidate();
        repaint();
      }

      @Override
      public RadComponent findTarget(int x, int y, @Nullable ComponentTargetFilter filter) {
        FindComponentVisitor visitor = new FindComponentVisitor(CaptionPanel.this, filter, x, y);
        myRootComponent.accept(visitor, false);
        return visitor.getResult();
      }

      @Override
      public InputTool findTargetTool(int x, int y) {
        return myDecorationLayer.findTargetTool(x, y);
      }

      @Override
      public void showSelection(boolean value) {
        myDecorationLayer.showSelection(value);
      }

      @Override
      public ComponentDecorator getRootSelectionDecorator() {
        return EmptyComponentDecorator.INSTANCE;
      }

      @Override
      public EditOperation processRootOperation(OperationContext context) {
        return null;
      }

      @Override
      public FeedbackLayer getFeedbackLayer() {
        return myFeedbackLayer;
      }

      @Override
      public RadComponent getRootComponent() {
        return myRootComponent;
      }
    };

    add(new GlassLayer(designer.getToolProvider(), myArea), DesignerEditorPanel.LAYER_GLASS);

    myDecorationLayer = new DecorationLayer(myArea);
    add(myDecorationLayer, DesignerEditorPanel.LAYER_DECORATION);

    myFeedbackLayer = new FeedbackLayer();
    add(myFeedbackLayer, DesignerEditorPanel.LAYER_FEEDBACK);

    myActionsProvider = new CommonEditActionsProvider(designer) {
      @Override
      protected EditableArea getArea() {
        return myArea;
      }
    };

    myMainArea.addSelectionListener(new ComponentSelectionListener() {
      @Override
      public void selectionChanged(EditableArea area) {
        update();
      }
    });
  }

  public void attachToScrollPane(JScrollPane scrollPane) {
    scrollPane.getViewport().addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        repaint();
      }
    });
  }

  public void doLayout() {
    for (int i = getComponentCount() - 1; i >= 0; i--) {
      Component component = getComponent(i);
      component.setBounds(0, 0, getWidth(), getHeight());
    }
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(16, 16);
  }

  @Override
  public Dimension getMinimumSize() {
    return getPreferredSize();
  }

  @Override
  public Object getData(@NonNls String dataId) {
    if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId)) {
      return this;
    }
    return null;
  }

  @Override
  public boolean canDeleteElement(@NotNull DataContext dataContext) {
    return myActionsProvider.canDeleteElement(dataContext);
  }

  @Override
  public void deleteElement(@NotNull DataContext dataContext) {
    myActionsProvider.deleteElement(dataContext);
  }

  public void update() {
    List<RadComponent> selection = myMainArea.getSelection();
    if (selection.size() != 1) {
      return;
    }

    boolean update = !myRootChildren.isEmpty();

    myRootComponent.setLayout(null);

    ICaption caption = null;
    RadComponent component = selection.get(0);
    RadComponent parent = component.getParent();

    if (parent != null) {
      caption = parent.getLayout().getCaption(component);
    }
    if (caption == null) {
      caption = component.getCaption();
    }

    if (caption == null) {
      myRootChildren = Collections.emptyList();
    }
    else {
      myRootComponent.setLayout(caption.getCaptionLayout(myMainArea, myHorizontal));
      myRootChildren = caption.getCaptionChildren(myMainArea, myHorizontal);
      update |= !myRootChildren.isEmpty();
    }

    if (update) {
      revalidate();
      repaint();
    }
  }
}