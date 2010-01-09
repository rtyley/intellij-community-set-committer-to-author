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
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.ide.util.DirectoryUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.refactoring.JavaRefactoringFactory;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.MoveDestination;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.move.MoveHandler;
import com.intellij.ui.DocumentAdapter;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author ven
 */
public class MoveClassesOrPackagesToNewDirectoryDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance("com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesToNewDirectoryDialog");

  private final PsiDirectory myDirectory;
  private final PsiElement[] myElementsToMove;
  private final MoveCallback myMoveCallback;

  public MoveClassesOrPackagesToNewDirectoryDialog(@NotNull final PsiDirectory directory, PsiElement[] elementsToMove,
                                                   final MoveCallback moveCallback) {
    super(false);
    setTitle(MoveHandler.REFACTORING_NAME);
    myDirectory = directory;
    myElementsToMove = elementsToMove;
    myMoveCallback = moveCallback;
    myDestDirectoryField.setText(FileUtil.toSystemDependentName(directory.getVirtualFile().getPath()));
    final FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false);
    myDestDirectoryField.getButton().addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final VirtualFile[] files = FileChooser.chooseFiles(myDirectory.getProject(), descriptor, directory.getVirtualFile());
        if (files.length == 1) {
          myDestDirectoryField.setText(FileUtil.toSystemDependentName(files[0].getPath()));
        }
      }
    });
    if (elementsToMove.length == 1) {
      PsiElement firstElement = elementsToMove[0];
      myNameLabel.setText(RefactoringBundle.message("move.single.class.or.package.name.label", UsageViewUtil.getType(firstElement),
                                                    UsageViewUtil.getLongName(firstElement)));
    }
    else if (elementsToMove.length > 1) {
      myNameLabel.setText(elementsToMove[0] instanceof PsiClass
                          ? RefactoringBundle.message("move.specified.classes")
                          : RefactoringBundle.message("move.specified.packages"));
    }
    final JavaRefactoringSettings refactoringSettings = JavaRefactoringSettings.getInstance();
    mySearchInCommentsAndStringsCheckBox.setSelected(refactoringSettings.MOVE_SEARCH_IN_COMMENTS);
    mySearchForTextOccurrencesCheckBox.setSelected(refactoringSettings.MOVE_SEARCH_FOR_TEXT);

    myDestDirectoryField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      public void textChanged(DocumentEvent event) {
        setOKActionEnabled(myDestDirectoryField.getText().length() > 0);
      }
    });

    init();
  }

  private TextFieldWithBrowseButton myDestDirectoryField;
  private JCheckBox mySearchForTextOccurrencesCheckBox;
  private JCheckBox mySearchInCommentsAndStringsCheckBox;
  private JPanel myRootPanel;
  private JLabel myNameLabel;

  private boolean isSearchInNonJavaFiles() {
    return mySearchForTextOccurrencesCheckBox.isSelected();
  }

  private boolean isSearchInComments() {
    return mySearchInCommentsAndStringsCheckBox.isSelected();
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myRootPanel;
  }

  protected void doOKAction() {
    final String path = FileUtil.toSystemIndependentName(myDestDirectoryField.getText());
    final Project project = myDirectory.getProject();
    PsiDirectory directory = ApplicationManager.getApplication().runWriteAction(new Computable<PsiDirectory>() {
      public PsiDirectory compute() {
        try {
          return DirectoryUtil.mkdirs(PsiManager.getInstance(project), path);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
          return null;
        }
      }
    });
    if (directory == null) {
      Messages.showErrorDialog(project, RefactoringBundle.message("cannot.find.or.create.destination.directory"),
                               RefactoringBundle.message("cannot.move"));
      return;
    }


    super.doOKAction();
    final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
    if (aPackage == null) {
      Messages.showErrorDialog(project, RefactoringBundle.message("destination.directory.does.not.correspond.to.any.package"),
                               RefactoringBundle.message("cannot.move"));
      return;
    }

    final JavaRefactoringSettings refactoringSettings = JavaRefactoringSettings.getInstance();
    final boolean searchInComments = isSearchInComments();
    final boolean searchForTextOccurences = isSearchInNonJavaFiles();
    refactoringSettings.MOVE_SEARCH_IN_COMMENTS = searchInComments;
    refactoringSettings.MOVE_SEARCH_FOR_TEXT = searchForTextOccurences;

    performRefactoring(project, directory, aPackage, searchInComments, searchForTextOccurences);
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myDestDirectoryField.getTextField();
  }

  protected void performRefactoring(Project project, PsiDirectory directory, PsiPackage aPackage,
                                    boolean searchInComments,
                                    boolean searchForTextOccurences) {
    final VirtualFile sourceRoot = ProjectRootManager.getInstance(project).getFileIndex().getSourceRootForFile(directory.getVirtualFile());
    if (sourceRoot == null) {
      Messages.showErrorDialog(project, RefactoringBundle.message("destination.directory.does.not.correspond.to.any.package"),
                               RefactoringBundle.message("cannot.move"));
      return;
    }
    final JavaRefactoringFactory factory = JavaRefactoringFactory.getInstance(project);
    final MoveDestination destination = factory.createSourceRootMoveDestination(aPackage.getQualifiedName(), sourceRoot);

    MoveClassesOrPackagesProcessor processor = new MoveClassesOrPackagesProcessor(myDirectory.getProject(), myElementsToMove, destination,
                                                                                  searchInComments, searchForTextOccurences,
                                                                                  myMoveCallback);
    if (processor.verifyValidPackageName()) {
      processor.run();
    }
  }
}



