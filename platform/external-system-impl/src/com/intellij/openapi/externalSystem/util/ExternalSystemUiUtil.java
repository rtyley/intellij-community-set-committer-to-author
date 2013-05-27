/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.model.serialization.ExternalProjectPojo;
import com.intellij.openapi.externalSystem.model.serialization.ExternalTaskPojo;
import com.intellij.openapi.externalSystem.service.task.ui.ExternalSystemTasksTreeModel;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemLocalSettings;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import java.awt.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Denis Zhdanov
 * @since 4/8/13 7:29 PM
 */
public class ExternalSystemUiUtil {

  public static final int INSETS = 7;

  private ExternalSystemUiUtil() {
  }

  /**
   * Asks to show balloon that contains information related to the given component.
   *
   * @param component    component for which we want to show information
   * @param messageType  balloon message type
   * @param message      message to show
   */
  public static void showBalloon(@NotNull JComponent component, @NotNull MessageType messageType, @NotNull String message) {
    final BalloonBuilder builder = JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(message, messageType, null)
      .setDisposable(ApplicationManager.getApplication())
      .setFadeoutTime(TimeUnit.SECONDS.toMillis(1));
    Balloon balloon = builder.createBalloon();
    Dimension size = component.getSize();
    Balloon.Position position;
    int x;
    int y;
    if (size == null) {
      x = y = 0;
      position = Balloon.Position.above;
    }
    else {
      x = Math.min(10, size.width / 2);
      y = size.height;
      position = Balloon.Position.below;
    }
    balloon.show(new RelativePoint(component, new Point(x, y)), position);
  }

  @NotNull
  public static GridBag getLabelConstraints(int indentLevel) {
    Insets insets = new Insets(INSETS, INSETS + INSETS * indentLevel, 0, INSETS);
    return new GridBag().anchor(GridBagConstraints.WEST).weightx(0).insets(insets);
  }

  @NotNull
  public static GridBag getFillLineConstraints(int indentLevel) {
    Insets insets = new Insets(INSETS, INSETS + INSETS * indentLevel, 0, INSETS);
    return new GridBag().weightx(1).coverLine().fillCellHorizontally().anchor(GridBagConstraints.WEST).insets(insets);
  }

  public static void fillBottom(@NotNull JComponent component) {
    component.add(Box.createVerticalGlue(), new GridBag().weightx(1).weighty(1).fillCell().coverLine());
  }

  public static void sort(@NotNull DefaultMutableTreeNode parent,
                          @NotNull DefaultTreeModel model,
                          @NotNull Comparator<TreeNode> comparator)
  {
    List<MutableTreeNode> children = ContainerUtilRt.newArrayList();
    THashMap<TreeNode, Integer> initialOrder = ContainerUtil.newIdentityTroveMap();
    for (int i = 0; i < parent.getChildCount(); i++) {
      MutableTreeNode child = (MutableTreeNode)parent.getChildAt(i);
      children.add(child);
      initialOrder.put(child, i);
    }
    Collections.sort(children, comparator);
    parent.removeAllChildren();

    for (MutableTreeNode child : children) {
      parent.add(child);
    }
    for (int i = 0; i < children.size(); i++) {
      MutableTreeNode child = children.get(i);
      Integer initialIndex = initialOrder.get(child);
      if (initialIndex != i) {
        model.nodeChanged(child);
      }
    }
  }

  /**
   * Applies data from the given settings object to the given model.
   * 
   * @param settings  target settings to use
   * @param model     UI model to be synced with the given settings
   */
  public static void apply(@NotNull final AbstractExternalSystemLocalSettings settings, @NotNull final ExternalSystemTasksTreeModel model) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        Map<ExternalProjectPojo,Collection<ExternalProjectPojo>> projects = settings.getAvailableProjects();
        for (Map.Entry<ExternalProjectPojo, Collection<ExternalProjectPojo>> entry : projects.entrySet()) {
          model.ensureSubProjectsStructure(entry.getKey(), entry.getValue());
        }
        Map<String, Collection<ExternalTaskPojo>> tasks = settings.getAvailableTasks();
        for (Map.Entry<String, Collection<ExternalTaskPojo>> entry : tasks.entrySet()) {
          model.ensureTasks(entry.getKey(), entry.getValue());
        } 
      }
    });
  }

  public static void showUi(@NotNull Object o, boolean show) {
    for (Field field : o.getClass().getFields()) {
      field.setAccessible(true);
      try {
        Object v = field.get(o);
        if (v instanceof JComponent) {
          ((JComponent)v).setVisible(show);
        }
      }
      catch (IllegalAccessException e) {
        // Ignore
      }
    }
  }

  public static void disposeUi(@NotNull Object o) {
    for (Field field : o.getClass().getFields()) {
      field.setAccessible(true);
      try {
        Object v = field.get(o);
        if (v instanceof JComponent) {
          field.set(o, null);
        }
      }
      catch (IllegalAccessException e) {
        // Ignore
      }
    }
  }
}
