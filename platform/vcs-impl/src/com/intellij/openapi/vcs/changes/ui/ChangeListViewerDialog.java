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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 20.07.2006
 * Time: 21:07:50
 */
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkHtmlRenderer;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesBrowserUseCase;
import com.intellij.openapi.vcs.changes.committed.RepositoryChangesBrowser;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeListImpl;
import com.intellij.ui.SeparatorFactory;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.util.NotNullFunction;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;

/**
 * @author max
 */
public class ChangeListViewerDialog extends DialogWrapper implements DataProvider {
  private Project myProject;
  private CommittedChangeList myChangeList;
  private RepositoryChangesBrowser myChangesBrowser;
  private JEditorPane myCommitMessageArea;
  // do not related to local data/changes etc
  private final boolean myInAir;
  private Change[] myChanges;
  private NotNullFunction<Change, Change> myConvertor;

  public ChangeListViewerDialog(Project project, CommittedChangeList changeList) {
    super(project, true);
    myInAir = false;
    initCommitMessageArea(project, changeList);
    initDialog(project, changeList);
  }

  public ChangeListViewerDialog(Component parent, Project project, Collection<Change> changes, final boolean inAir) {
    super(parent, true);
    myInAir = inAir;
    initDialog(project, new CommittedChangeListImpl("", "", "", -1, new Date(0), changes));
  }

  public ChangeListViewerDialog(Project project, Collection<Change> changes, final boolean inAir) {
    super(project, true);
    myInAir = inAir;
    initDialog(project, new CommittedChangeListImpl("", "", "", -1, new Date(0), changes));
  }

  private void initDialog(final Project project, final CommittedChangeList changeList) {
    myProject = project;
    myChangeList = changeList;
    final Collection<Change> changes = myChangeList.getChanges();
    myChanges = changes.toArray(new Change[changes.size()]);

    setTitle(VcsBundle.message("dialog.title.changes.browser"));
    setCancelButtonText(CommonBundle.message("close.action.name"));
    setModal(false);

    init();
  }

  private void initCommitMessageArea(final Project project, final CommittedChangeList changeList) {
    myCommitMessageArea = new JEditorPane(UIUtil.HTML_MIME, "");
    myCommitMessageArea.setEditable(false);
    @NonNls final String text = IssueLinkHtmlRenderer.formatTextIntoHtml(project, changeList.getComment());
    myCommitMessageArea.setText(text);
    myCommitMessageArea.setBackground(UIUtil.getComboBoxDisabledBackground());
    myCommitMessageArea.addHyperlinkListener(new BrowserHyperlinkListener());
  }


  protected String getDimensionServiceKey() {
    return "VCS.ChangeListViewerDialog";
  }

  public Object getData(@NonNls final String dataId) {
    if (VcsDataKeys.CHANGES.getName().equals(dataId)) {
      return myChanges;
    }
    return null;
  }

  public void setConvertor(final NotNullFunction<Change, Change> convertor) {
    myConvertor = convertor;
  }

  public JComponent createCenterPanel() {
    final JPanel mainPanel = new JPanel();
    mainPanel.setLayout(new BorderLayout());
    myChangesBrowser = new RepositoryChangesBrowser(myProject, Collections.singletonList(myChangeList),
                                                    new ArrayList<Change>(myChangeList.getChanges()),
                                                    myChangeList) {
      @Override
      protected void showDiffForChanges(final Change[] changesArray, final int indexInSelection) {
        if (myInAir && (myConvertor != null)) {
          final Change[] convertedChanges = new Change[changesArray.length];
          for (int i = 0; i < changesArray.length; i++) {
            Change change = changesArray[i];
            convertedChanges[i] = myConvertor.fun(change);
          }
          super.showDiffForChanges(convertedChanges, indexInSelection);
        } else {
          super.showDiffForChanges(changesArray, indexInSelection);
        }
      }
    };
    myChangesBrowser.setUseCase(myInAir ? CommittedChangesBrowserUseCase.IN_AIR : null);
    mainPanel.add(myChangesBrowser, BorderLayout.CENTER);

    if (myCommitMessageArea != null) {
      JPanel commitPanel = new JPanel(new BorderLayout());
      JComponent separator = SeparatorFactory.createSeparator(VcsBundle.message("label.commit.comment"), myCommitMessageArea);
      commitPanel.add(separator, BorderLayout.NORTH);
      commitPanel.add(new JScrollPane(myCommitMessageArea), BorderLayout.CENTER);

      mainPanel.add(commitPanel, BorderLayout.SOUTH);
    }

    return mainPanel;
  }

  @Override
  protected void dispose() {
    myChangesBrowser.dispose();
    super.dispose();
  }

  @Override
  protected Action[] createActions() {
    return new Action[] { getCancelAction() };
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myChangesBrowser;
  }
}
