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
package com.intellij.refactoring.extractSuperclass;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.classMembers.MemberInfoChange;
import com.intellij.refactoring.classMembers.MemberInfoModel;
import com.intellij.refactoring.memberPullUp.PullUpHelper;
import com.intellij.refactoring.ui.MemberSelectionPanel;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.classMembers.InterfaceContainmentVerifier;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.refactoring.util.classMembers.UsesAndInterfacesDependencyMemberInfoModel;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

class ExtractSuperclassDialog extends ExtractSuperBaseDialog {
  private final InterfaceContainmentVerifier myContainmentVerifier = new InterfaceContainmentVerifier() {
    public boolean checkedInterfacesContain(PsiMethod psiMethod) {
      return PullUpHelper.checkedInterfacesContain(myMemberInfos, psiMethod);
    }
  };

  public static interface Callback {
    boolean checkConflicts(ExtractSuperclassDialog dialog);
  }

  private JLabel myClassNameLabel;
  private JLabel myPackageLabel;
  private final Callback myCallback;

  public ExtractSuperclassDialog(Project project, PsiClass sourceClass, List<MemberInfo> selectedMembers, Callback callback) {
    super(project, sourceClass, selectedMembers, ExtractSuperclassHandler.REFACTORING_NAME);
    myCallback = callback;
    init();
  }

  public MemberInfo[] getSelectedMemberInfos() {
    ArrayList<MemberInfo> list = new ArrayList<MemberInfo>(myMemberInfos.size());
    for (MemberInfo info : myMemberInfos) {
      if (info.isChecked()) {
        list.add(info);
      }
    }
    return list.toArray(new MemberInfo[list.size()]);
  }

  InterfaceContainmentVerifier getContainmentVerifier() {
    return myContainmentVerifier;
  }

  protected JComponent createNorthPanel() {
    Box box = Box.createVerticalBox();

    JPanel _panel = new JPanel(new BorderLayout());
    _panel.add(new JLabel(RefactoringBundle.message("extract.superclass.from")), BorderLayout.NORTH);
    _panel.add(mySourceClassField, BorderLayout.CENTER);
    box.add(_panel);

    box.add(Box.createVerticalStrut(10));

    box.add(createActionComponent());

    box.add(Box.createVerticalStrut(10));

    myClassNameLabel = new JLabel();
    myClassNameLabel.setLabelFor(myExtractedSuperNameField);
    _panel = new JPanel(new BorderLayout());
    _panel.add(myClassNameLabel, BorderLayout.NORTH);
    _panel.add(myExtractedSuperNameField, BorderLayout.CENTER);
    box.add(_panel);
    box.add(Box.createVerticalStrut(5));


    _panel = new JPanel(new BorderLayout());
    myPackageLabel = new JLabel();
    myPackageLabel.setText(RefactoringBundle.message("package.for.new.superclass"));
    _panel.add(myPackageLabel, BorderLayout.NORTH);
    _panel.add(myPackageNameField, BorderLayout.CENTER);
    //_panel.add(myBtnPackageChooser, BorderLayout.EAST);
    box.add(_panel);
    box.add(Box.createVerticalStrut(10));

    JPanel panel = new JPanel(new BorderLayout());
    panel.add(box, BorderLayout.CENTER);
    return panel;
  }

  protected String getClassNameLabelText() {
    return RefactoringBundle.message("superclass.name");
  }

  protected JLabel getClassNameLabel() {
    return myClassNameLabel;
  }

  protected JLabel getPackageNameLabel() {
    return myPackageLabel;
  }

  protected String getEntityName() {
    return RefactoringBundle.message("ExtractSuperClass.superclass");
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    final MemberSelectionPanel memberSelectionPanel = new MemberSelectionPanel(RefactoringBundle.message("members.to.form.superclass"),
                                                                               myMemberInfos, RefactoringBundle.message("make.abstract"));
    panel.add(memberSelectionPanel, BorderLayout.CENTER);
    final MemberInfoModel<PsiMember, MemberInfo> memberInfoModel =
      new UsesAndInterfacesDependencyMemberInfoModel(mySourceClass, null, false, myContainmentVerifier) {
        public Boolean isFixedAbstract(MemberInfo member) {
          return Boolean.TRUE;
        }
      };
    memberInfoModel.memberInfoChanged(new MemberInfoChange<PsiMember, MemberInfo>(myMemberInfos));
    memberSelectionPanel.getTable().setMemberInfoModel(memberInfoModel);
    memberSelectionPanel.getTable().addMemberInfoChangeListener(memberInfoModel);

    panel.add(myJavaDocPanel, BorderLayout.EAST);

    return panel;
  }

  @Override
  protected String getJavaDocPanelName() {
    return RefactoringBundle.message("javadoc.for.abstracts");
  }

  @Override
  protected String getExtractedSuperNameNotSpecifiedKey() {
    return RefactoringBundle.message("no.superclass.name.specified");
  }

  @Override
  protected boolean checkConflicts() {
    return myCallback.checkConflicts(this);
  }

  @Override
  protected int getJavaDocPolicySetting() {
    return JavaRefactoringSettings.getInstance().EXTRACT_SUPERCLASS_JAVADOC;
  }

  @Override
  protected void setJavaDocPolicySetting(int policy) {
    JavaRefactoringSettings.getInstance().EXTRACT_SUPERCLASS_JAVADOC = policy;
  }


  @Override
  protected ExtractSuperBaseProcessor createProcessor() {
    return new ExtractSuperClassProcessor(myProject, getTargetDirectory(), getExtractedSuperName(),
                                          mySourceClass, getSelectedMemberInfos(), false,
                                          new DocCommentPolicy(getJavaDocPolicy()));
  }

  @Override
  protected String getHelpId() {
    return HelpID.EXTRACT_SUPERCLASS;
  }
}
