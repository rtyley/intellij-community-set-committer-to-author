package com.intellij.util.ui.classpath;

import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.TreeExpander;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.impl.libraries.LibraryTableImplUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.util.OrderEntryCellAppearanceUtils;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.ui.treeStructure.WeightBasedComparator;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Icons;
import com.intellij.util.Processor;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

/**
 * @author Gregory.Shrago
 */

public class ChooseLibrariesDialog extends DialogWrapper{

  private SimpleTree myTree = new SimpleTree();
  private AbstractTreeBuilder myBuilder;

  private List<Library> myResult;
  private Map<Object, Object> myLibraryMap = new THashMap<Object, Object>();

  protected ChooseLibrariesDialog(final Project project, final String title) {
    super(project, false);
    setTitle(title);
    init();
    updateOKAction();
  }

  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.javaee.module.view.dataSource.ChooseLibrariesDialog";
  }

  @Override
  protected void doOKAction() {
    processSelection(new CommonProcessors.CollectProcessor<Library>(myResult = new ArrayList<Library>()));
    super.doOKAction();
  }

  private void updateOKAction() {
    setOKActionEnabled(!processSelection(new CommonProcessors.FindFirstProcessor<Library>()));
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTree;
  }

  @NotNull
  public List<Library> getSelectedLibraries() {
    return myResult == null? Collections.<Library>emptyList() : myResult;
  }

  private boolean processSelection(final Processor<Library> processor) {
    for (Object element : myBuilder.getSelectedElements()) {
      if (element instanceof Library) {
        if (!processor.process((Library)element)) return false;
      }
    }
    return true;
  }

  protected boolean acceptsElement(final Object element) {
    return true;
  }

  @Override
  protected JComponent createNorthPanel() {
    final DefaultActionGroup group = new DefaultActionGroup();
    final TreeExpander expander = new DefaultTreeExpander(myTree);
    final CommonActionsManager actionsManager = CommonActionsManager.getInstance();
    group.add(actionsManager.createExpandAllAction(expander, myTree));
    group.add(actionsManager.createCollapseAllAction(expander, myTree));
    final JComponent component = ActionManager.getInstance().createActionToolbar(ActionPlaces.PROJECT_VIEW_TOOLBAR, group, true).getComponent();
    component.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.darkGray), component.getBorder()));
    return component;
  }

  @Nullable
  protected JComponent createCenterPanel() {
    myBuilder = new AbstractTreeBuilder(myTree, new DefaultTreeModel(new DefaultMutableTreeNode()),
                                        new MyStructure(ProjectManager.getInstance().getDefaultProject()),
                                        WeightBasedComparator.FULL_INSTANCE);
    myBuilder.initRootNode();

    myTree.setDragEnabled(false);
    myTree.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myTree.setRowHeight(Icons.CLASS_ICON.getIconHeight());

    myTree.setShowsRootHandles(true);
    UIUtil.setLineStyleAngled(myTree);

    myTree.setRootVisible(false);
    myTree.getSelectionModel().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    myTree.addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(final TreeSelectionEvent e) {
        updateOKAction();
      }
    });
    myTree.addMouseListener(new MouseAdapter() {
      public void mouseClicked(final MouseEvent e) {
        if (e.getClickCount() != 2) return;
        if (isOKActionEnabled()) {
          doOKAction();
        }
      }
    });
    myTree.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "ENTER");
    myTree.getActionMap().put("ENTER", getOKAction());
    return ScrollPaneFactory.createScrollPane(myTree);
  }

  protected void dispose() {
    Disposer.dispose(myBuilder);
    super.dispose();
  }

  private static class MyNode<T> extends SimpleNode {
    private final T myElement;

    private MyNode(Project project, NodeDescriptor parentDescriptor, T element) {
      super(project, parentDescriptor);
      myElement = element;
    }

    public T getElement() {
      return myElement;
    }

    @Override
    public SimpleNode[] getChildren() {
      return NO_CHILDREN;
    }

    @Override
    public int getWeight() {
      return 0;
    }

    @Override
    public Object[] getEqualityObjects() {
      return new Object[] {myElement};
    }
  }

  private static class RootDescriptor extends MyNode<Object> {
    protected RootDescriptor(final Project project) {
      super(project, null, ApplicationManager.getApplication());
    }
  }

  private static class ProjectDescriptor extends MyNode<Project> {
    protected ProjectDescriptor(final Project project, final Project element) {
      super(project, null, element);
    }

    @Override
    protected void doUpdate() {
      setIcons(Icons.PROJECT_ICON, Icons.PROJECT_ICON);
      final String nodeText = getElement().getName();
      setNodeText(StringUtil.isNotEmpty(nodeText) ? nodeText : "<unnamed>", null, false);
    }
  }

  private static class ModuleDescriptor extends MyNode<Module> {
    protected ModuleDescriptor(final Project project, final NodeDescriptor parentDescriptor, final Module element) {
      super(project, parentDescriptor, element);
    }

    @Override
    protected void doUpdate() {
      setIcons(getElement().getModuleType().getNodeIcon(false), getElement().getModuleType().getNodeIcon(true));
      final String nodeText = getElement().getName();
      setNodeText(StringUtil.isNotEmpty(nodeText) ? nodeText : "<unnamed>", null, false);
    }

    @Override
    public int getWeight() {
      return 1;
    }
  }

  private static class LibraryDescriptor extends MyNode<Library> {
    private final Icon myIcon;

    protected LibraryDescriptor(final Project project, final NodeDescriptor parentDescriptor, final Library element) {
      super(project, parentDescriptor, element);
      final SimpleColoredComponent coloredComponent = new SimpleColoredComponent();
      OrderEntryCellAppearanceUtils.forLibrary(getElement()).customize(coloredComponent);
      myIcon = coloredComponent.getIcon();
    }

    @Override
    protected void doUpdate() {
      setIcons(myIcon, myIcon);
      final String nodeText = OrderEntryCellAppearanceUtils.forLibrary(getElement()).getText();
      setNodeText(StringUtil.isNotEmpty(nodeText) ? nodeText : "<unnamed>", null, false);
    }
  }

  private static class NamedDescriptor extends MyNode<LibraryTable> {
    private final int myWeight;

    protected NamedDescriptor(final Project project, final NodeDescriptor parentDescriptor, final LibraryTable table, final int weight) {
      super(project, parentDescriptor, table);
      myWeight = weight;
    }

    @Override
    protected void doUpdate() {
      setIcons(Icons.DIRECTORY_CLOSED_ICON, Icons.DIRECTORY_OPEN_ICON);
      final String nodeText = getElement().getPresentation().getDisplayName(true);
      setNodeText(StringUtil.isNotEmpty(nodeText) ? nodeText : "<unnamed>", null, false);
    }

    @Override
    public int getWeight() {
      return myWeight;
    }
  }

  private class MyStructure extends AbstractTreeStructure {
    private final Project myProject;

    public MyStructure(Project project) {
      myProject = project;
    }

    @Override
    public Object getRootElement() {
      return ApplicationManager.getApplication();
    }

    @Override
    public Object[] getChildElements(Object element) {
      final ArrayList<Object> result = new ArrayList<Object>();
      if (element instanceof Application) {
        Collections.addAll(result, ProjectManager.getInstance().getOpenProjects());
        final LibraryTablesRegistrar instance = LibraryTablesRegistrar.getInstance();
        result.add(instance.getLibraryTable()); //1
        result.addAll(instance.getCustomLibraryTables()); //2
      }
      else if (element instanceof Project) {
        Collections.addAll(result, ModuleManager.getInstance((Project)element).getModules());
        result.add(LibraryTablesRegistrar.getInstance().getLibraryTable((Project)element));
      }
      else if (element instanceof LibraryTable) {
        Collections.addAll(result, ((LibraryTable)element).getLibraries());
      }
      else if (element instanceof Module) {
        for (OrderEntry entry : ModuleRootManager.getInstance((Module)element).getOrderEntries()) {
          if (entry instanceof LibraryOrderEntry) {
            final LibraryOrderEntry libraryOrderEntry = (LibraryOrderEntry)entry;
            if (LibraryTableImplUtil.MODULE_LEVEL.equals(libraryOrderEntry.getLibraryLevel())) {
              final Library library = libraryOrderEntry.getLibrary();
              result.add(library);
            }
          }
        }
      }
      final Iterator<Object> it = result.iterator();
      while (it.hasNext()) {
        if (!acceptsElement(it.next())) it.remove();
      }
      for (Object o : result) {
        myLibraryMap.put(o, element);
      }
      return result.toArray();
    }

    @Override
    public Object getParentElement(Object element) {
      if (element instanceof Application) return null;
      if (element instanceof Project) return ApplicationManager.getApplication();
      if (element instanceof Module) return ((Module)element).getProject();
      if (element instanceof LibraryTable) return myLibraryMap.get(element);
      if (element instanceof Library) return myLibraryMap.get(element);
      throw new AssertionError();
    }

    @NotNull
    @Override
    public NodeDescriptor createDescriptor(Object element, NodeDescriptor parentDescriptor) {
      if (element instanceof Application) return new RootDescriptor(myProject);
      if (element instanceof Project) return new ProjectDescriptor(myProject, (Project)element);
      if (element instanceof Module) return new ModuleDescriptor(myProject, parentDescriptor, (Module)element);
      if (element instanceof LibraryTable) return new NamedDescriptor(myProject, parentDescriptor, (LibraryTable)element, 0);
      if (element instanceof Library) return new LibraryDescriptor(myProject, parentDescriptor, (Library)element);
      throw new AssertionError();
    }

    @Override
    public void commit() {
    }

    @Override
    public boolean hasSomethingToCommit() {
      return false;
    }
  }
}
