/*
 * Copyright 2000-2010 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.history.wholeTree;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.actions.SelectedBlockHistoryAction;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Consumer;
import com.intellij.util.ui.UIUtil;
import git4idea.GitBranch;
import git4idea.history.browser.SymbolicRefs;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.border.CompoundBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.TreeSet;

/**
 * @author irengrig
 */
public class BranchSelectorAction extends BasePopupAction {
  private SymbolicRefs mySymbolicRefs;
  private final Consumer<String> myConsumer;

  public BranchSelectorAction(final Project project, Consumer<String> consumer) {
    super(project, "Branch:");
    myConsumer = consumer;
    myLabel.setText(getText("All"));
  }

  private String getText(final String branch) {
    return minusRefs(branch);
    //return "Show branch: " + (branch.startsWith("refs/") ? branch.substring(5) : branch);
  }

  private String minusRefs(final String branch) {
    if (branch.startsWith("refs/heads/")) {
      return branch.substring("refs/heads/".length());
    }
    else {
      return (branch.startsWith("refs/") ? branch.substring("refs/".length()) : branch);
    }
  }

  public void setSymbolicRefs(SymbolicRefs symbolicRefs) {
    mySymbolicRefs = symbolicRefs;
  }

  protected DefaultActionGroup createActionGroup() {
    final DefaultActionGroup group = new DefaultActionGroup();

    group.add(new SelectBranchAction("All", null));
    final GitBranch current = mySymbolicRefs.getCurrent();
    if (current != null) {
      group.add(new SelectBranchAction("*" + minusRefs(current.getFullName()), current.getFullName()));
    }
    final TreeSet<String> locals = mySymbolicRefs.getLocalBranches();
    if (locals != null && (! locals.isEmpty())) {
      final DefaultActionGroup local = new DefaultActionGroup("Local", true);
      group.add(local);
      for (String s : locals) {
        final String presentation = s.equals(current.getName()) ? ("*" + s) : s;
        local.add(new SelectBranchAction(presentation, s));
      }
    }
    final TreeSet<String> remotes = mySymbolicRefs.getRemoteBranches();
    if (remotes != null && (! remotes.isEmpty())) {
      final DefaultActionGroup remote = new DefaultActionGroup("Remote", true);
      group.add(remote);
      for (String s : remotes) {
        final String presentation = s.equals(current.getName()) ? ("*" + s) : s;
        remote.add(new SelectBranchAction(presentation, GitBranch.REFS_REMOTES_PREFIX + s));
      }
    }
    return group;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    doAction(null);
  }

  private class SelectBranchAction extends AnAction {
    private final String myValue;

    private SelectBranchAction(String text, String value) {
      super(text);
      myValue = value;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myConsumer.consume(myValue);
      myLabel.setText(myValue == null ? getTemplatePresentation().getText() : getText(myValue));
    }
  }
}
