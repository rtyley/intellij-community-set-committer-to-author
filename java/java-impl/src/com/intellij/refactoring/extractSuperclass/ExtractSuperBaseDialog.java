package com.intellij.refactoring.extractSuperclass;

import com.intellij.ide.util.PackageUtil;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.DocCommentPanel;
import com.intellij.refactoring.ui.PackageNameReferenceEditorCombo;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.util.IncorrectOperationException;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.List;

/**
 * @author dsl
 */
public abstract class ExtractSuperBaseDialog extends RefactoringDialog {
  protected String myRefactoringName;
  protected PsiClass mySourceClass;
  protected PsiDirectory myTargetDirectory;
  protected List<MemberInfo> myMemberInfos;

  protected JRadioButton myRbExtractSuperclass;
  protected JRadioButton myRbExtractSubclass;

  protected JTextField mySourceClassField;
  protected JTextField myExtractedSuperNameField;
  protected PackageNameReferenceEditorCombo myPackageNameField;
  protected DocCommentPanel myJavaDocPanel;


  public ExtractSuperBaseDialog(Project project, PsiClass sourceClass, List<MemberInfo> members, String refactoringName) {
    super(project, true);
    myRefactoringName = refactoringName;

    mySourceClass = sourceClass;
    myMemberInfos = members;
    myTargetDirectory = mySourceClass.getContainingFile().getContainingDirectory();
  }

  @Override
  protected void init() {
    setTitle(myRefactoringName);

    initPackageNameField();
    initSourceClassField();
    myExtractedSuperNameField = new JTextField();

    myJavaDocPanel = new DocCommentPanel(getJavaDocPanelName());
    myJavaDocPanel.setPolicy(getJavaDocPolicySetting());

    super.init();
    updateDialogForExtractSuperclass();
  }

  private void initPackageNameField() {
    String name = "";
    PsiFile file = mySourceClass.getContainingFile();
    if (file instanceof PsiJavaFile) {
      name = ((PsiJavaFile)file).getPackageName();
    }
    myPackageNameField = new PackageNameReferenceEditorCombo(name, myProject, "ExtractSuperBase.RECENT_KEYS", RefactoringBundle.message("choose.destination.package"));
  }

  private void initSourceClassField() {
    mySourceClassField = new JTextField();
    mySourceClassField.setEditable(false);
    mySourceClassField.setText(mySourceClass.getQualifiedName());
  }

  protected JComponent createActionComponent() {
    Box box = Box.createHorizontalBox();
    final String s = StringUtil.decapitalize(getEntityName());
    myRbExtractSuperclass = new JRadioButton();
    myRbExtractSuperclass.setText(RefactoringBundle.message("extractSuper.extract", s));
    myRbExtractSubclass = new JRadioButton();
    myRbExtractSubclass.setText(RefactoringBundle.message("extractSuper.rename.original.class", s));
    box.add(myRbExtractSuperclass);
    box.add(myRbExtractSubclass);
    box.add(Box.createHorizontalGlue());
    final ButtonGroup buttonGroup = new ButtonGroup();
    buttonGroup.add(myRbExtractSuperclass);
    buttonGroup.add(myRbExtractSubclass);
    myRbExtractSuperclass.setSelected(true);
    myRbExtractSuperclass.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        updateDialogForExtractSuperclass();
      }
    });

    myRbExtractSubclass.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        updateDialogForExtractSubclass();
      }
    });
    return box;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myExtractedSuperNameField;
  }

  protected void updateDialogForExtractSubclass() {
    getClassNameLabel().setText(RefactoringBundle.message("extractSuper.rename.original.class.to"));
    getPreviewAction().setEnabled(true);
  }

  protected void updateDialogForExtractSuperclass() {
    getClassNameLabel().setText(getClassNameLabelText());
    getPreviewAction().setEnabled(false);
  }

  public String getExtractedSuperName() {
    return myExtractedSuperNameField.getText().trim();
  }

  protected String getTargetPackageName() {
    return myPackageNameField.getText().trim();
  }

  public PsiDirectory getTargetDirectory() {
    return myTargetDirectory;
  }

  protected abstract String getClassNameLabelText();

  protected abstract JLabel getClassNameLabel();

  protected abstract JLabel getPackageNameLabel();

  protected abstract String getEntityName();

  public int getJavaDocPolicy() {
    return myJavaDocPanel.getPolicy();
  }

  public boolean isExtractSuperclass() {
    return myRbExtractSuperclass.isSelected();
  }

  protected void doAction() {
      final String[] errorString = new String[]{null};
      final String extractedSuperName = getExtractedSuperName();
      final String packageName = getTargetPackageName();
      final PsiManager manager = PsiManager.getInstance(myProject);

      if ("".equals(extractedSuperName)) {
        errorString[0] = getExtractedSuperNameNotSpecifiedKey();
        myExtractedSuperNameField.requestFocusInWindow();
      }
      else {
        if (!JavaPsiFacade.getInstance(manager.getProject()).getNameHelper().isIdentifier(extractedSuperName)) {
          errorString[0] = RefactoringMessageUtil.getIncorrectIdentifierMessage(extractedSuperName);
          myExtractedSuperNameField.requestFocusInWindow();
        }
        else {
          CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
            public void run() {
              try {
                final PsiPackage aPackage = JavaPsiFacade.getInstance(manager.getProject()).findPackage(packageName);
                if (aPackage != null) {
                  final PsiDirectory[] directories = aPackage.getDirectories(mySourceClass.getResolveScope());
                  if (directories.length >= 1) {
                    myTargetDirectory = getDirUnderSameSourceRoot(directories);
                  }
                }
                myTargetDirectory
                  = PackageUtil.findOrCreateDirectoryForPackage(myProject, packageName, myTargetDirectory, true);
                if (myTargetDirectory == null) {
                  errorString[0] = ""; // message already reported by PackageUtil
                  return;
                }
                errorString[0] = RefactoringMessageUtil.checkCanCreateClass(myTargetDirectory, extractedSuperName);
              }
              catch (IncorrectOperationException e) {
                errorString[0] = e.getMessage();
                myPackageNameField.requestFocusInWindow();
              }
            }
          }, RefactoringBundle.message("create.directory"), null);
        }
      }
      if (errorString[0] != null) {
        if (errorString[0].length() > 0) {
          CommonRefactoringUtil.showErrorMessage(myRefactoringName, errorString[0], getHelpId(), myProject);
        }
        return;
      }

      if (!checkConflicts()) return;

      if (!isExtractSuperclass()) {
        invokeRefactoring(createProcessor());
      }
      setJavaDocPolicySetting(getJavaDocPolicy());
      closeOKAction();
    }

  private PsiDirectory getDirUnderSameSourceRoot(final PsiDirectory[] directories) {
    final VirtualFile sourceFile = mySourceClass.getContainingFile().getVirtualFile();
    if (sourceFile != null) {
      final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
      final VirtualFile sourceRoot = fileIndex.getSourceRootForFile(sourceFile);
      if (sourceRoot != null) {
        for(PsiDirectory dir: directories) {
          if (fileIndex.getSourceRootForFile(dir.getVirtualFile()) == sourceRoot) {
            return dir;
          }
        }
      }
    }
    return directories[0];
  }

  protected abstract String getJavaDocPanelName();

  protected abstract String getExtractedSuperNameNotSpecifiedKey();
  protected boolean checkConflicts() { return true; }
  protected abstract ExtractSuperBaseProcessor createProcessor();

  protected abstract int getJavaDocPolicySetting();
  protected abstract void setJavaDocPolicySetting(int policy);

  @Override
  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(getHelpId());
  }

  protected abstract String getHelpId();
}
