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
package com.intellij.internal.psiView;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.impl.AbstractFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.SortedComboBoxModel;
import com.intellij.ui.TitledBorderWithMnemonic;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class PsiViewerDialog extends DialogWrapper {
  private final Project myProject;

  private final Tree myTree;
  private final ViewerTreeBuilder myTreeBuilder;

  private final JList myRefs;

  private Editor myEditor;
  private String myLastParsedText = null;

  private JCheckBox myShowWhiteSpacesBox;
  private JPanel myStructureTreePanel;
  private JPanel myTextPanel;
  private JPanel myPanel;
  private JCheckBox myShowTreeNodesCheckBox;
  private JComboBox myDialectsComboBox;
  private JPanel myReferencesPanel;
  private JPanel myButtonPanel;
  private Presentation myPresentation = new Presentation();
  private Map<String, Object> handlers = new HashMap<String, Object>();
  private DefaultActionGroup myGroup;

  public PsiViewerDialog(Project project, boolean modal) {
    super(project, true);
    setTitle("PSI Viewer");
    myProject = project;
    myTree = new Tree(new DefaultTreeModel(new DefaultMutableTreeNode()));
    UIUtil.setLineStyleAngled(myTree);
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    myTree.updateUI();
    ToolTipManager.sharedInstance().registerComponent(myTree);
    TreeUtil.installActions(myTree);
    new TreeSpeedSearch(myTree);
    myTreeBuilder = new ViewerTreeBuilder(project, myTree);

    myTree.addTreeSelectionListener(new MyTreeSelectionListener());

    JScrollPane scrollPane = new JScrollPane(myTree);
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(scrollPane, BorderLayout.CENTER);
    myStructureTreePanel.setLayout(new BorderLayout());
    myStructureTreePanel.add(panel, BorderLayout.CENTER);

    myRefs = new JList(new DefaultListModel());
    JScrollPane refScrollPane = new JScrollPane(myRefs);
    JPanel refPanel = new JPanel(new BorderLayout());
    refPanel.add(refScrollPane, BorderLayout.CENTER);
    myReferencesPanel.setLayout(new BorderLayout());
    myReferencesPanel.add(refPanel, BorderLayout.CENTER);
    final GoToListener listener = new GoToListener();
    myRefs.addKeyListener(listener);
    myRefs.addMouseListener(listener);
    myRefs.getSelectionModel().addListSelectionListener(listener);

    setModal(modal);
    setOKButtonText("&Build PSI Tree");
    init();
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.internal.psiView.PsiViewerDialog";
  }

  public JComponent getPreferredFocusedComponent() {
    return myEditor.getContentComponent();
  }

  private void updatePresentation(Presentation p) {
    myPresentation.setText(p.getText());
    myPresentation.setIcon(p.getIcon());
  }

  protected void init() {
    initBorders();
    final List<Presentation> items = new ArrayList<Presentation>();
    final EditorFactory editorFactory = EditorFactory.getInstance();
    final Document document = editorFactory.createDocument("");
    myEditor = editorFactory.createEditor(document, myProject);
    myEditor.getSettings().setFoldingOutlineShown(false);

    for (PsiViewerExtension extension : Extensions.getExtensions(PsiViewerExtension.EP_NAME)) {
      final Presentation p = new Presentation(extension.getName());
      p.setIcon(extension.getIcon());
      handlers.put(p.getText(), extension);
      items.add(p);
    }

    for (FileType fileType : FileTypeManager.getInstance().getRegisteredFileTypes()) {
      if (fileType != StdFileTypes.GUI_DESIGNER_FORM &&
          fileType != StdFileTypes.IDEA_MODULE &&
          fileType != StdFileTypes.IDEA_PROJECT &&
          fileType != StdFileTypes.IDEA_WORKSPACE &&
          fileType != FileTypes.ARCHIVE &&
          fileType != FileTypes.UNKNOWN &&
          fileType != FileTypes.PLAIN_TEXT &&
          !(fileType instanceof AbstractFileType) &&
          !fileType.isBinary() &&
          !fileType.isReadOnly()) {
        final Presentation p = new Presentation(fileType.getName() + " file");
        p.setIcon(fileType.getIcon());
        handlers.put(p.getText(), fileType);
        items.add(p);
      }
    }

    final Presentation[] popupItems = items.toArray(new Presentation[items.size()]);
    Arrays.sort(popupItems, new Comparator<Presentation>() {
      public int compare(Presentation p1, Presentation p2) {
        return p1.getText().toUpperCase().compareTo(p2.getText().toUpperCase());
      }
    });

    myDialectsComboBox.setRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(final JList list,
                                                    final Object value,
                                                    final int index,
                                                    final boolean isSelected,
                                                    final boolean cellHasFocus) {
        final Component result = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value == null) setText("<no dialect>");
        return result;
      }
    });

    final ViewerTreeStructure treeStructure = (ViewerTreeStructure)myTreeBuilder.getTreeStructure();
    myShowWhiteSpacesBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        treeStructure.setShowWhiteSpaces(myShowWhiteSpacesBox.isSelected());
        myTreeBuilder.queueUpdate();
      }
    });
    myShowTreeNodesCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        treeStructure.setShowTreeNodes(myShowTreeNodesCheckBox.isSelected());
        myTreeBuilder.queueUpdate();
      }
    });
    myTextPanel.setLayout(new BorderLayout());
    myTextPanel.add(myEditor.getComponent(), BorderLayout.CENTER);

    myGroup = new DefaultActionGroup();
    for (final Presentation popupItem : popupItems) {
      myGroup.add(new AnAction(popupItem.getText(), popupItem.getText(), popupItem.getIcon()) {
        public void actionPerformed(AnActionEvent e) {
          updatePresentation(e.getPresentation());
        }
      });
    }

    final PsiViewerSettings settings = PsiViewerSettings.getSettings();
    final String type = settings.type;
    for (Presentation popupItem : popupItems) {
      if (popupItem.getText().equals(type)) {
        updatePresentation(popupItem);
        break;
      }
    }

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        myEditor.getDocument().setText(settings.text);
      }
    });

    myShowWhiteSpacesBox.setSelected(settings.showWhiteSpaces);
    myShowTreeNodesCheckBox.setSelected(settings.showTreeNodes);

    final ChoosePsiTypeButton typeButton = new ChoosePsiTypeButton();
    myButtonPanel.add(typeButton.createCustomComponent(myPresentation), BorderLayout.CENTER);

    updateDialectsCombo();

    final Component component = myButtonPanel.getComponents()[0];
    if (component instanceof JComponent) {
      final Component button = ((JComponent)component).getComponents()[0];
      if (button instanceof JButton) {
        final JButton jButton = (JButton)button;
        final int mask = SystemInfo.isMac ? KeyEvent.META_DOWN_MASK : KeyEvent.ALT_DOWN_MASK;
        getRootPane().registerKeyboardAction(new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            jButton.doClick();
          }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_P, mask), JComponent.WHEN_IN_FOCUSED_WINDOW);

        getRootPane().registerKeyboardAction(new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            IdeFocusManager.getInstance(myProject).requestFocus(myEditor.getContentComponent(), true);
          }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_T, mask), JComponent.WHEN_IN_FOCUSED_WINDOW);

        getRootPane().registerKeyboardAction(new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            IdeFocusManager.getInstance(myProject).requestFocus(myTree, true);
          }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_S, mask), JComponent.WHEN_IN_FOCUSED_WINDOW);

        getRootPane().registerKeyboardAction(new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            IdeFocusManager.getInstance(myProject).requestFocus(myRefs, true);
            if (myRefs.getModel().getSize() > 0) {
              if (myRefs.getSelectedIndex() == -1) {
                myRefs.setSelectedIndex(0);
              }
            }
          }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_R, mask), JComponent.WHEN_IN_FOCUSED_WINDOW);


      }
    }
    super.init();
  }

  private void initBorders() {
    myTextPanel.setBorder(new TitledBorderWithMnemonic("&Text"));
    myStructureTreePanel.setBorder(new TitledBorderWithMnemonic("PSI &Structure"));
    myReferencesPanel.setBorder(new TitledBorderWithMnemonic("&References"));
  }

  @Nullable
  private PsiElement getPsiElement() {
    TreePath path = myTree.getSelectionPath();
    if (path != null) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      if (node.getUserObject() instanceof ViewerNodeDescriptor) {
        ViewerNodeDescriptor descriptor = (ViewerNodeDescriptor)node.getUserObject();
        Object elementObject = descriptor.getElement();
        return elementObject instanceof PsiElement
               ? (PsiElement)elementObject
               : elementObject instanceof ASTNode ? ((ASTNode)elementObject).getPsi() : null;
      }
    }
    return null;
  }

  private void updateDialectsCombo() {
    final SortedComboBoxModel<Language> model = new SortedComboBoxModel<Language>(new Comparator<Language>() {
      public int compare(final Language o1, final Language o2) {
        if (o1 == null) return o2 == null ? 0 : -1;
        if (o2 == null) return 1;
        return o1.getID().compareTo(o2.getID());
      }
    });
    final Object handler = getHandler();
    if (handler instanceof LanguageFileType) {
      final Language baseLang = ((LanguageFileType)handler).getLanguage();
      model.setAll(LanguageUtil.getLanguageDialects(baseLang));
      model.add(null);
    }
    myDialectsComboBox.setModel(model);
    myDialectsComboBox.setVisible(model.getSize() > 1);
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  private Object getHandler() {
    return handlers.get(myPresentation.getText());
  }

  protected void doOKAction() {
    final String text = myEditor.getDocument().getText();
    if (text.trim().length() == 0) return;

    myLastParsedText = text;
    PsiElement rootElement = null;
    final Object handler = getHandler();

    try {
      if (handler instanceof PsiViewerExtension) {
        final PsiViewerExtension ext = (PsiViewerExtension)handler;
        rootElement = ext.createElement(myProject, text);
      }
      else if (handler instanceof FileType) {
        final FileType type = (FileType)handler;
        if (type instanceof LanguageFileType) {
          final Language language = ((LanguageFileType)type).getLanguage();
          final Language dialect = (Language)myDialectsComboBox.getSelectedItem();
          rootElement = PsiFileFactory.getInstance(myProject)
            .createFileFromText("Dummy." + type.getDefaultExtension(), dialect == null ? language : dialect, text);
        }
        else {
          rootElement = PsiFileFactory.getInstance(myProject).createFileFromText("Dummy." + type.getDefaultExtension(), text);
        }
      }
      IdeFocusManager.getInstance(myProject).requestFocus(myTree, true);
    }
    catch (IncorrectOperationException e1) {
      rootElement = null;
      Messages.showMessageDialog(myProject, e1.getMessage(), "Error", Messages.getErrorIcon());
    }
    ViewerTreeStructure structure = (ViewerTreeStructure)myTreeBuilder.getTreeStructure();
    structure.setRootPsiElement(rootElement);

    myTreeBuilder.queueUpdate();
    myTree.setRootVisible(true);
    myTree.expandRow(0);
    myTree.setRootVisible(false);
  }

  private class MyTreeSelectionListener implements TreeSelectionListener {
    private final TextAttributes myAttributes;

    public MyTreeSelectionListener() {
      myAttributes = new TextAttributes();
      myAttributes.setBackgroundColor(new Color(0, 0, 128));
      myAttributes.setForegroundColor(Color.white);
    }

    public void valueChanged(TreeSelectionEvent e) {
      if (!myEditor.getDocument().getText().equals(myLastParsedText)) return;
      TreePath path = myTree.getSelectionPath();
      if (path == null) {
        clearSelection();
      }
      else {
        clearSelection();
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
        if (!(node.getUserObject() instanceof ViewerNodeDescriptor)) return;
        ViewerNodeDescriptor descriptor = (ViewerNodeDescriptor)node.getUserObject();
        Object elementObject = descriptor.getElement();
        final PsiElement element = elementObject instanceof PsiElement
                                   ? (PsiElement)elementObject
                                   : elementObject instanceof ASTNode ? ((ASTNode)elementObject).getPsi() : null;
        if (element != null) {
          TextRange range = element.getTextRange();
          int start = range.getStartOffset();
          int end = range.getEndOffset();
          final ViewerTreeStructure treeStructure = (ViewerTreeStructure)myTreeBuilder.getTreeStructure();
          PsiElement rootPsiElement = treeStructure.getRootPsiElement();
          if (rootPsiElement != null) {
            int baseOffset = rootPsiElement.getTextRange().getStartOffset();
            start -= baseOffset;
            end -= baseOffset;
          }

          final int textLength = myEditor.getDocument().getTextLength();
          if (end <= textLength) {
            myEditor.getMarkupModel()
              .addRangeHighlighter(start, end, HighlighterLayer.FIRST + 1, myAttributes, HighlighterTargetArea.EXACT_RANGE);
            myEditor.getCaretModel().moveToOffset(start);
            myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
          }
          updateReferences(element);
        }
      }
    }

    public void updateReferences(PsiElement element) {
      final DefaultListModel model = (DefaultListModel)myRefs.getModel();
      model.clear();
      if (element != null) {
        for (PsiReference reference : element.getReferences()) {
          model.addElement(reference.getClass().getName());
        }
      }
    }

    private void clearSelection() {
      myEditor.getMarkupModel().removeAllHighlighters();
    }
  }

  public void doCancelAction() {
    final PsiViewerSettings settings = PsiViewerSettings.getSettings();
    settings.type = myPresentation.getText();
    settings.text = myEditor.getDocument().getText();
    settings.showTreeNodes = myShowTreeNodesCheckBox.isSelected();
    settings.showWhiteSpaces = myShowWhiteSpacesBox.isSelected();
    super.doCancelAction();
  }

  public void dispose() {
    Disposer.dispose(myTreeBuilder);
    EditorFactory.getInstance().releaseEditor(myEditor);

    super.dispose();
  }

  private class ChoosePsiTypeButton extends ComboBoxAction {
    protected int getMaxRows() {
      return 15;
    }

    protected int getMinWidth() {
      return 150;
    }

    protected int getMinHeight() {
      return 200;
    }

    @NotNull
    protected DefaultActionGroup createPopupActionGroup(JComponent button) {
      return myGroup;
    }
  }

  private class GoToListener implements KeyListener, MouseListener, ListSelectionListener {
    private RangeHighlighter myHighlighter;
    private final TextAttributes myAttributes =
      new TextAttributes(Color.white, new Color(0, 0, 128), Color.red, EffectType.BOXED, Font.PLAIN);

    private void navigate() {
      clearSelection();
      final Object value = myRefs.getSelectedValue();
      if (value instanceof String) {
        final String fqn = (String)value;
        String filename = fqn;
        if (fqn.contains(".")) {
          filename = fqn.substring(fqn.lastIndexOf('.') + 1);
        }
        if (filename.contains("$")) {
          filename = filename.substring(0, filename.indexOf('$'));
        }
        filename += ".java";
        final PsiFile[] files = FilenameIndex.getFilesByName(myProject, filename, GlobalSearchScope.allScope(myProject));
        if (files != null && files.length > 0) {
          files[0].navigate(true);
        }
      }
    }

    public void keyPressed(KeyEvent e) {
      if (e.getKeyCode() == KeyEvent.VK_ENTER) {
        navigate();
      }
    }

    public void mouseClicked(MouseEvent e) {
      if (e.getClickCount() > 1) {
        navigate();
      }
    }

    public void valueChanged(ListSelectionEvent e) {
      clearSelection();
      updateDialectsCombo();
      final int ind = myRefs.getSelectedIndex();
      final PsiElement element = getPsiElement();
      if (ind > -1 && element != null) {
        final PsiReference[] references = element.getReferences();
        if (ind < references.length) {
          final TextRange textRange = references[ind].getRangeInElement();
          TextRange range = element.getTextRange();
          int start = range.getStartOffset();
          int end = range.getEndOffset();
          final ViewerTreeStructure treeStructure = (ViewerTreeStructure)myTreeBuilder.getTreeStructure();
          PsiElement rootPsiElement = treeStructure.getRootPsiElement();
          if (rootPsiElement != null) {
            int baseOffset = rootPsiElement.getTextRange().getStartOffset();
            start -= baseOffset;
            end -= baseOffset;
          }

          start += textRange.getStartOffset();
          end = start + textRange.getLength();
          myHighlighter = myEditor.getMarkupModel()
            .addRangeHighlighter(start, end, HighlighterLayer.FIRST + 1, myAttributes, HighlighterTargetArea.EXACT_RANGE);
        }
      }
    }

    public void clearSelection() {
      if (myHighlighter != null && Arrays.asList(myEditor.getMarkupModel().getAllHighlighters()).contains(myHighlighter)) {
        myEditor.getMarkupModel().removeHighlighter(myHighlighter);
        myHighlighter = null;
      }
    }

    public void keyTyped(KeyEvent e) {
    }

    public void keyReleased(KeyEvent e) {
    }

    public void mousePressed(MouseEvent e) {
    }

    public void mouseReleased(MouseEvent e) {
    }

    public void mouseEntered(MouseEvent e) {
    }

    public void mouseExited(MouseEvent e) {
    }
  }
}
