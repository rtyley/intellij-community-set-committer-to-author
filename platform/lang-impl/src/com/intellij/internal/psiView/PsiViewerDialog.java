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

/**
 * class PsiViewerDialog
 * created Aug 25, 2001
 * @author Jeka
 */
package com.intellij.internal.psiView;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.impl.AbstractFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.SortedComboBoxModel;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

public class PsiViewerDialog extends DialogWrapper {
  private static final Object EXTENSION_KEY = new Object();

  private final Project myProject;

  private final Tree myTree;
  private final ViewerTreeBuilder myTreeBuilder;

  private final JList myRefs;

  private Editor myEditor;
  private String myLastParsedText = null;

  private List<JRadioButton> myExtensionButtons = new ArrayList<JRadioButton>();
  private JRadioButton[] myFileTypeButtons;
  private FileType[] myFileTypes;

  private JCheckBox myShowWhiteSpacesBox;
  private JPanel myStructureTreePanel;
  private JPanel myTextPanel;
  private JPanel myPanel;
  private JPanel myChoicesPanel;
  private JCheckBox myShowTreeNodesCheckBox;
  private JComboBox myDialectsComboBox;
  private JPanel myReferencesPanel;

  public PsiViewerDialog(Project project,boolean modal) {
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
    final GoToListener listener = new GoToListener(myRefs, project);
    myRefs.addKeyListener(listener);
    myRefs.addMouseListener(listener);

    setModal(modal);
    setOKButtonText("&Build PSI Tree");
    init();
  }

  protected String getDimensionServiceKey(){
    return "#com.intellij.internal.psiView.PsiViewerDialog";
  }

  public JComponent getPreferredFocusedComponent() {
    return myEditor.getContentComponent();
  }

  protected void init() {
    EditorFactory editorFactory = EditorFactory.getInstance();
    Document document = editorFactory.createDocument("");
    myEditor = editorFactory.createEditor(document, myProject);
    myEditor.getSettings().setFoldingOutlineShown(false);

    for(PsiViewerExtension extension: Extensions.getExtensions(PsiViewerExtension.EP_NAME)) {
      JRadioButton button = new JRadioButton(extension.getName());
      button.putClientProperty(EXTENSION_KEY, extension);
      myExtensionButtons.add(button);
    }


    FileType[] fileTypes = FileTypeManager.getInstance().getRegisteredFileTypes();
    Arrays.sort(fileTypes,new Comparator<FileType>() {
      public int compare(final FileType o1, final FileType o2) {
        return o1.getName().compareTo(o2.getName());
      }
    });

    List<FileType> customFileTypes = new ArrayList<FileType>();

    for (FileType fileType : fileTypes) {
      if (fileType != StdFileTypes.GUI_DESIGNER_FORM && fileType != StdFileTypes.IDEA_MODULE && fileType != StdFileTypes.IDEA_PROJECT &&
          fileType != StdFileTypes.IDEA_WORKSPACE && fileType != FileTypes.ARCHIVE && fileType != FileTypes.UNKNOWN &&
          fileType != FileTypes.PLAIN_TEXT && !(fileType instanceof AbstractFileType) && !fileType.isBinary() && !fileType.isReadOnly()) {
        customFileTypes.add(fileType);
      }
    }

    myFileTypes = customFileTypes.toArray(new FileType[customFileTypes.size()]);
    myFileTypeButtons = new JRadioButton[myFileTypes.length];

    ButtonGroup bg = new ButtonGroup();
    for (JRadioButton button : myExtensionButtons) {
      bg.add(button);
    }

    final int rows = 1 + myFileTypes.length / 7;
    JPanel choicesBox = new JPanel(new GridLayout(rows, 7));

    for (JRadioButton extensionButton : myExtensionButtons) {
      choicesBox.add(extensionButton);
    }

    for (int i = 0; i < myFileTypes.length; i++) {
      FileType fileType = myFileTypes[i];
      JRadioButton button = new JRadioButton(fileType.getName()+" file");
      bg.add(button);
      choicesBox.add(button);
      myFileTypeButtons[i] = button;
    }

    final ActionListener updateDialectsListener = new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        updateDialectsCombo();
      }
    };
    final Enumeration<AbstractButton> buttonEnum = bg.getElements();
    while (buttonEnum.hasMoreElements()) {
      buttonEnum.nextElement().addActionListener(updateDialectsListener);
    }
    updateDialectsCombo();
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

    if (myExtensionButtons.size() > 0) {
      myExtensionButtons.get(0).setSelected(true);
    }
    else {
      myFileTypeButtons [0].setSelected(true);
    }

    myChoicesPanel.setLayout(new BorderLayout());
    myChoicesPanel.add(choicesBox, BorderLayout.CENTER);

    final ViewerTreeStructure treeStructure = (ViewerTreeStructure)myTreeBuilder.getTreeStructure();
    myShowWhiteSpacesBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        treeStructure.setShowWhiteSpaces(myShowWhiteSpacesBox.isSelected());
        myTreeBuilder.updateFromRoot();
      }
    });
    myShowTreeNodesCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        treeStructure.setShowTreeNodes(myShowTreeNodesCheckBox.isSelected());
        myTreeBuilder.updateFromRoot();
      }
    });
    myTextPanel.setLayout(new BorderLayout());
    myTextPanel.add(myEditor.getComponent(), BorderLayout.CENTER);

    super.init();
  }

  private void updateDialectsCombo() {
    final SortedComboBoxModel<Language> model = new SortedComboBoxModel<Language>(new Comparator<Language>() {
      public int compare(final Language o1, final Language o2) {
        if (o1 == null) return o2 == null? 0 : -1;
        if (o2 == null) return 1;
        return o1.getID().compareTo(o2.getID());
      }
    });
    for (int i = 0; i < myFileTypeButtons.length; i++) {
      JRadioButton fileTypeButton = myFileTypeButtons[i];
      if (fileTypeButton.isSelected()) {
        final FileType type = myFileTypes[i];
        if (type instanceof LanguageFileType) {
          final Language baseLang = ((LanguageFileType)type).getLanguage();
          model.setAll(LanguageUtil.getLanguageDialects(baseLang));
          model.add(null);
        }
        break;
      }
    }
    myDialectsComboBox.setModel(model);
    myDialectsComboBox.setVisible(model.getSize() > 1);
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  protected void doOKAction() {
    final String text = myEditor.getDocument().getText();
    if ("".equals(text.trim())) {
      return;
    }
    myLastParsedText = text;
    PsiElement rootElement = null;
    try {
      for (JRadioButton button : myExtensionButtons) {
        if (button.isSelected()) {
          PsiViewerExtension ext = (PsiViewerExtension) button.getClientProperty(EXTENSION_KEY);
          rootElement = ext.createElement(myProject, text);
        }
      }
      if (rootElement == null) {
        for (int i = 0; i < myFileTypeButtons.length; i++) {
          JRadioButton fileTypeButton = myFileTypeButtons[i];

          if (fileTypeButton.isSelected()) {
            final FileType type = myFileTypes[i];
            if (type instanceof LanguageFileType) {
              final Language language = ((LanguageFileType)type).getLanguage();
              final Language dialect = (Language)myDialectsComboBox.getSelectedItem();
              rootElement = PsiFileFactory.getInstance(myProject).createFileFromText("Dummy." + type.getDefaultExtension(), dialect == null? language : dialect, text);
            }
            else {
              rootElement = PsiFileFactory.getInstance(myProject).createFileFromText("Dummy." + type.getDefaultExtension(), text);
            }
          }
        }
      }
    }
    catch (IncorrectOperationException e1) {
      rootElement = null;
      Messages.showMessageDialog(
        myProject,
        e1.getMessage(),
        "Error",
        Messages.getErrorIcon()
      );
    }
    ViewerTreeStructure structure = (ViewerTreeStructure)myTreeBuilder.getTreeStructure();
    structure.setRootPsiElement(rootElement);

    myTreeBuilder.updateFromRoot();
    myTree.setRootVisible(true);
    myTree.expandRow(0);
    myTree.setRootVisible(false);
  }

  private class MyTreeSelectionListener implements TreeSelectionListener {
    private final TextAttributes myAttributes;
    private RangeHighlighter myHighlighter;

    public MyTreeSelectionListener() {
      myAttributes = new TextAttributes();
      myAttributes.setBackgroundColor(new Color(0, 0, 128));
      myAttributes.setForegroundColor(Color.white);
    }

    public void valueChanged(TreeSelectionEvent e) {
      if (!myEditor.getDocument().getText().equals(myLastParsedText)) return;
      TreePath path = myTree.getSelectionPath();
      if (path == null){
        clearSelection();
      }
      else{
        clearSelection();
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
        if (!(node.getUserObject() instanceof ViewerNodeDescriptor)) return;
        ViewerNodeDescriptor descriptor = (ViewerNodeDescriptor)node.getUserObject();
        Object elementObject = descriptor.getElement();
        final PsiElement element = elementObject instanceof PsiElement? (PsiElement)elementObject :
                                   elementObject instanceof ASTNode ? ((ASTNode)elementObject).getPsi() : null;
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
            myHighlighter = myEditor.getMarkupModel().addRangeHighlighter(start, end, HighlighterLayer.FIRST + 1, myAttributes, HighlighterTargetArea.EXACT_RANGE);
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
      if (myHighlighter != null) {
        myEditor.getMarkupModel().removeHighlighter(myHighlighter);
        myHighlighter = null;
      }
    }
  }

  public void dispose() {
    Disposer.dispose(myTreeBuilder);
    EditorFactory.getInstance().releaseEditor(myEditor);

    super.dispose();
  }

  private static class GoToListener implements KeyListener, MouseListener {
    private final JList myList;
    private final Project myProject;

    public GoToListener(JList list, Project project) {
      myList = list;
      myProject = project;
    }

    private void navigate() {
      final Object value = myList.getSelectedValue();
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

    public void keyTyped(KeyEvent e) {}
    public void keyReleased(KeyEvent e) {}
    public void mousePressed(MouseEvent e) {}
    public void mouseReleased(MouseEvent e) {}
    public void mouseEntered(MouseEvent e) {}
    public void mouseExited(MouseEvent e) {}
  }
}
