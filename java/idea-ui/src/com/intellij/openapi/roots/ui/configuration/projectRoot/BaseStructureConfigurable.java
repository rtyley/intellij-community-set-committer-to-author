/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.facet.Facet;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.TreeExpander;
import com.intellij.ide.impl.convert.ProjectFileVersion;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Condition;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.TreeToolTipHandler;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.navigation.Place;
import com.intellij.util.Icons;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.packaging.artifacts.Artifact;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.*;
import java.util.List;

public abstract class BaseStructureConfigurable extends MasterDetailsComponent implements SearchableConfigurable, Disposable, Configurable.Assistant, Place.Navigator {

  protected StructureConfigurableContext myContext;

  protected final Project myProject;

  protected boolean myUiDisposed = true;

  private boolean myWasTreeInitialized;

  protected boolean myAutoScrollEnabled = true;

  protected BaseStructureConfigurable(final Project project) {
    myProject = project;
  }

  public void init(StructureConfigurableContext context) {
    myContext = context;
    myContext.addCacheUpdateListener(new Runnable() {
      public void run() {
        if (!myTree.isShowing()) return;

        myTree.revalidate();
        myTree.repaint();
      }
    });
  }


  public ActionCallback navigateTo(@Nullable final Place place, final boolean requestFocus) {
    if (place == null) return new ActionCallback.Done();

    final Object object = place.getPath(TREE_OBJECT);
    final String byName = (String)place.getPath(TREE_NAME);

    if (object == null && byName == null) return new ActionCallback.Done();

    final MyNode node = object == null ? null : findNodeByObject(myRoot, object);
    final MyNode nodeByName = byName == null ? null : findNodeByName(myRoot, byName);

    if (node == null && nodeByName == null) return new ActionCallback.Done();

    final NamedConfigurable config;
    if (node != null) {
      config = node.getConfigurable();
    } else {
      config = nodeByName.getConfigurable();
    }

    final ActionCallback result = new ActionCallback().doWhenDone(new Runnable() {
      public void run() {
        myAutoScrollEnabled = true;
      }
    });

    myAutoScrollEnabled = false;
    myAutoScrollHandler.cancelAllRequests();
    final MyNode nodeToSelect = node != null ? node : nodeByName;
    selectNodeInTree(nodeToSelect, requestFocus).doWhenDone(new Runnable() {
      public void run() {
        setSelectedNode(nodeToSelect);
        Place.goFurther(config, place, requestFocus).notifyWhenDone(result);
      }
    });

    return result;
  }


  public void queryPlace(@NotNull final Place place) {
    if (myCurrentConfigurable != null) {
      place.putPath(TREE_OBJECT, myCurrentConfigurable.getEditableObject());
      Place.queryFurther(myCurrentConfigurable, place);
    }
  }

  protected void initTree() {
    if (myWasTreeInitialized) return;
    myWasTreeInitialized = true;

    super.initTree();
    new TreeSpeedSearch(myTree, new Convertor<TreePath, String>() {
      public String convert(final TreePath treePath) {
        return ((MyNode)treePath.getLastPathComponent()).getDisplayName();
      }
    }, true);
    TreeToolTipHandler.install(myTree);
    ToolTipManager.sharedInstance().registerComponent(myTree);
    myTree.setCellRenderer(new ColoredTreeCellRenderer(){
      public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        if (value instanceof MyNode) {
          final MyNode node = (MyNode)value;

          if (node.getConfigurable() == null) {
            return;
          }

          final String displayName = node.getDisplayName();
          final Icon icon = node.getConfigurable().getIcon(expanded);
          setIcon(icon);
          setToolTipText(null);
          setFont(UIUtil.getTreeFont());
          if (node.isDisplayInBold()){
            append(displayName, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
          } else {
            final Object object = node.getConfigurable().getEditableObject();
            final boolean unused = myContext.isUnused(object);
            final StructureConfigurableContext.ValidityLevel level = myContext.isInvalid(object);
            final boolean invalid = level != StructureConfigurableContext.ValidityLevel.VALID;
            if (unused || invalid){
              Color fg = unused
                         ? UIUtil.getTextInactiveTextColor()
                         : selected && hasFocus ? UIUtil.getTreeSelectionForeground() : UIUtil.getTreeForeground();
              append(displayName, new SimpleTextAttributes(invalid ? SimpleTextAttributes.STYLE_WAVED : SimpleTextAttributes.STYLE_PLAIN,
                                                           fg,
                                                           level == StructureConfigurableContext.ValidityLevel.ERROR ? Color.RED : Color.GRAY));
              setToolTipText(composeTooltipMessage(invalid, object, displayName, unused));
            }
            else {
              append(displayName, selected && hasFocus ? SimpleTextAttributes.SELECTED_SIMPLE_CELL_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
            }
          }
        }
      }
    });

  }


  private String composeTooltipMessage(final boolean invalid, final Object object, final String displayName, final boolean unused) {
    final StringBuilder buf = StringBuilderSpinAllocator.alloc();
    try {
      if (invalid) {
        if (object instanceof Module) {
          final Module module = (Module)object;
          final Map<String, Set<String>> problems = myContext.myValidityCache.get(module);

          if (problems.containsKey(StructureConfigurableContext.DUPLICATE_MODULE_NAME)) {
            buf.append(StructureConfigurableContext.DUPLICATE_MODULE_NAME).append("\n");
          }

          if (problems.containsKey(StructureConfigurableContext.NO_JDK)){
            buf.append(StructureConfigurableContext.NO_JDK).append("\n");
          }
          final Set<String> deletedLibraries = problems.get(StructureConfigurableContext.DELETED_LIBRARIES);
          if (deletedLibraries != null) {
            buf.append(ProjectBundle.message("project.roots.library.problem.message", deletedLibraries.size()));
            for (String problem : deletedLibraries) {
              if (deletedLibraries.size() > 1) {
                buf.append(" - ");
              }
              buf.append("\'").append(problem).append("\'").append("\n");
            }
          }
        } else {
          buf.append(ProjectBundle.message("project.roots.tooltip.library.misconfigured", displayName)).append("\n");
        }
      }
      if (unused) {
        buf.append(ProjectBundle.message("project.roots.tooltip.unused", displayName));
      }
      return buf.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(buf);
    }
  }

  public void disposeUIResources() {
    if (myUiDisposed) return;

    super.disposeUIResources();

    myUiDisposed = true;

    myAutoScrollHandler.cancelAllRequests();
    myContext.myUpdateDependenciesAlarm.cancelAllRequests();
    myContext.myUpdateDependenciesAlarm.addRequest(new Runnable(){
      public void run() {
        SwingUtilities.invokeLater(new Runnable(){
          public void run() {
            dispose();
          }
        });
      }
    }, 0);
  }

  protected void addCollapseExpandActions(final List<AnAction> result) {
    final TreeExpander expander = new TreeExpander() {
      public void expandAll() {
        TreeUtil.expandAll(myTree);
      }

      public boolean canExpand() {
        return true;
      }

      public void collapseAll() {
        TreeUtil.collapseAll(myTree, 0);
      }

      public boolean canCollapse() {
        return true;
      }
    };
    final CommonActionsManager actionsManager = CommonActionsManager.getInstance();
    result.add(actionsManager.createExpandAllAction(expander, myTree));
    result.add(actionsManager.createCollapseAllAction(expander, myTree));
  }

  private class MyFindUsagesAction extends FindUsagesInProjectStructureActionBase {

    public MyFindUsagesAction(JComponent parentComponent) {
      super(parentComponent, myProject);
    }

    protected boolean isEnabled() {
      final TreePath selectionPath = myTree.getSelectionPath();
      if (selectionPath != null){
        final MyNode node = (MyNode)selectionPath.getLastPathComponent();
        return !node.isDisplayInBold();
      } else {
        return false;
      }
    }

    protected StructureConfigurableContext getContext() {
      return myContext;
    }

    protected Object getSelectedObject() {
      return BaseStructureConfigurable.this.getSelectedObject();
    }

    protected RelativePoint getPointToShowResults() {
      final int selectedRow = myTree.getSelectionRows()[0];
      final Rectangle rowBounds = myTree.getRowBounds(selectedRow);
      final Point location = rowBounds.getLocation();
      location.x += rowBounds.width;
      return new RelativePoint(myTree, location);
    }
  }


  public void reset() {
    myUiDisposed = false;

    if (!myWasTreeInitialized) {
      initTree();
      myTree.setShowsRootHandles(false);
      loadTree();
    } else {
      super.disposeUIResources();
      myTree.setShowsRootHandles(false);
      loadTree();
    }

    super.reset();
  }

  protected abstract void loadTree();
 

  @NotNull
  protected ArrayList<AnAction> createActions(final boolean fromPopup) {
    final ArrayList<AnAction> result = new ArrayList<AnAction>();
    AbstractAddGroup addAction = createAddAction();
    if (addAction != null) {
      result.add(addAction);
    }
    result.add(new MyRemoveAction());

    final AnAction copyAction = createCopyAction();
    if (copyAction != null) {
      result.add(copyAction);
    }
    result.add(Separator.getInstance());

    result.add(new MyFindUsagesAction(myTree));


    return result;
  }

  @Nullable
  protected AnAction createCopyAction() {
    return null;
  }

  @Nullable
  protected abstract AbstractAddGroup createAddAction();

  protected class MyRemoveAction extends MyDeleteAction {
    public MyRemoveAction() {
      super(new Condition<Object>() {
        public boolean value(final Object object) {
          if (object instanceof MyNode) {
            final NamedConfigurable namedConfigurable = ((MyNode)object).getConfigurable();
            if (namedConfigurable != null) {
              final Object editableObject = namedConfigurable.getEditableObject();
              if (editableObject instanceof Sdk || editableObject instanceof Module || editableObject instanceof Facet || editableObject instanceof Artifact) return true;
              if (editableObject instanceof Library) {
                final LibraryTable table = ((Library)editableObject).getTable();
                return table == null || table.isEditable();
              }
            }
          }
          return false;
        }
      });
    }

    public void actionPerformed(AnActionEvent e) {
      final TreePath[] paths = myTree.getSelectionPaths();
      if (paths == null) return;

      final Set<TreePath> pathsToRemove = new HashSet<TreePath>();
      for (TreePath path : paths) {
        if (removeFromModel(path)) {
          pathsToRemove.add(path);
        }
      }
      removePaths(pathsToRemove.toArray(new TreePath[pathsToRemove.size()]));
    }

    private boolean removeFromModel(final TreePath selectionPath) {
      final Object last = selectionPath.getLastPathComponent();

      if (!(last instanceof MyNode)) return false;

      final MyNode node = (MyNode)last;
      final NamedConfigurable configurable = node.getConfigurable();
      final Object editableObject = configurable.getEditableObject();
      if (editableObject instanceof Sdk) {
        removeJdk((Sdk)editableObject);
      }
      else if (editableObject instanceof Module) {
        if (!removeModule((Module)editableObject)) return false;
      }
      else if (editableObject instanceof Facet) {
        if (removeFacet((Facet)editableObject).isEmpty()) return false;
      }
      else if (editableObject instanceof Library) {
        removeLibrary((Library)editableObject);
      }
      else if (editableObject instanceof Artifact) {
        removeArtifact((Artifact)editableObject);
      }
      return true;
    }
  }

  protected void removeArtifact(Artifact artifact) {
  }


  protected void removeLibrary(Library library) {

  }

  protected void removeFacetNodes(@NotNull List<Facet> facets) {
    for (Facet facet : facets) {
      MyNode node = findNodeByObject(myRoot, facet);
      if (node != null) {
        removePaths(TreeUtil.getPathFromRoot(node));
      }
    }
  }

  protected List<Facet> removeFacet(final Facet facet) {
    return myContext.myModulesConfigurator.getFacetsConfigurator().removeFacet(facet);
  }

  protected boolean removeModule(final Module module) {
    return true;
  }

  protected void removeJdk(final Sdk editableObject) {
  }

  protected abstract static class AbstractAddGroup extends ActionGroup implements ActionGroupWithPreselection {

    protected AbstractAddGroup(String text, Icon icon) {
      super(text, true);

      final Presentation presentation = getTemplatePresentation();
      presentation.setIcon(icon);

      final Keymap active = KeymapManager.getInstance().getActiveKeymap();
      if (active != null) {
        final Shortcut[] shortcuts = active.getShortcuts("NewElement");
        setShortcutSet(new CustomShortcutSet(shortcuts));
      }
    }

    public AbstractAddGroup(String text) {
      this(text, Icons.ADD_ICON);
    }

    public ActionGroup getActionGroup() {
      return this;
    }

    public int getDefaultIndex() {
        return 0;
      }
  }
  
}
