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

package com.intellij.history.integration.ui.views;

import com.intellij.CommonBundle;
import com.intellij.util.Consumer;
import com.intellij.history.LocalHistoryConfiguration;
import com.intellij.history.core.LocalVcs;
import com.intellij.history.integration.IdeaGateway;
import static com.intellij.history.integration.LocalHistoryBundle.message;
import com.intellij.history.integration.LocalHistoryComponent;
import com.intellij.history.integration.revertion.Reverter;
import com.intellij.history.integration.ui.models.FileDifferenceModel;
import com.intellij.history.integration.ui.models.HistoryDialogModel;
import com.intellij.history.integration.ui.models.RevisionProcessingProgress;
import com.intellij.history.integration.ui.views.table.RevisionsTable;
import com.intellij.ide.ui.SplitterProportionsDataImpl;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.SimpleDiffRequest;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.SplitterProportionsData;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.patch.CreatePatchConfigurationPanel;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.List;

public abstract class HistoryDialog<T extends HistoryDialogModel> extends DialogWrapper {
  protected IdeaGateway myGateway;
  protected VirtualFile myFile;
  private RevisionsTable myRevisionsTable;
  protected Splitter mySplitter;
  protected T myModel;

  protected HistoryDialog(IdeaGateway gw, VirtualFile f, boolean doInit) {
    super(gw.getProject(), true);
    myGateway = gw;
    myFile = f;
    if (doInit) init();
  }

  @Override
  protected void init() {
    initModel();
    super.init();
  }

  private void initModel() {
    LocalVcs vcs = LocalHistoryComponent.getLocalVcsFor(getProject());
    myModel = createModel(vcs);
    restoreShowChangesOnlyOption();
  }

  protected abstract T createModel(LocalVcs vcs);

  @Override
  protected JComponent createCenterPanel() {
    JComponent diff = createDiffPanel();
    JComponent revisions = createRevisionsList();

    mySplitter = new Splitter(true, 0.7f);
    mySplitter.setFirstComponent(diff);
    mySplitter.setSecondComponent(revisions);

    mySplitter.setPreferredSize(getInitialSize());
    restoreSplitterProportion();

    return mySplitter;
  }

  protected abstract Dimension getInitialSize();

  @Override
  protected void dispose() {
    saveShowChangesOnlyOption();
    saveSplitterProportion();
    super.dispose();
  }

  protected abstract JComponent createDiffPanel();

  private JComponent createRevisionsList() {
    ActionGroup actions = createRevisionsActions();

    ActionToolbar tb = createRevisionsToolbar(actions);
    JComponent t = createRevisionsTable(actions);

    JPanel result = new JPanel(new BorderLayout());
    result.add(tb.getComponent(), BorderLayout.NORTH);
    result.add(t, BorderLayout.CENTER);

    return result;
  }

  private ActionToolbar createRevisionsToolbar(ActionGroup actions) {
    ActionManager am = ActionManager.getInstance();
    return am.createActionToolbar(ActionPlaces.UNKNOWN, actions, true);
  }

  private ActionGroup createRevisionsActions() {
    DefaultActionGroup result = new DefaultActionGroup();
    result.add(new RevertAction());
    result.add(new CreatePatchAction());
    result.add(Separator.getInstance());
    result.add(new ShowChangesOnlyAction());
    result.add(Separator.getInstance());
    result.add(new HelpAction());
    return result;
  }

  private JComponent createRevisionsTable(ActionGroup actions) {
    myRevisionsTable = new RevisionsTable(myModel, new RevisionsTable.SelectionListener() {
      public void changesSelected(int first, int last) {
        myModel.selectChanges(first, last);
        updateDiffs();
      }

      public void revisionsSelected(int first, int last) {
        myModel.selectRevisions(first, last);
        updateDiffs();
      }
    });
    addPopupMenuToComponent(myRevisionsTable, actions);

    return ScrollPaneFactory.createScrollPane(myRevisionsTable);
  }

  private void addPopupMenuToComponent(JComponent comp, final ActionGroup ag) {
    comp.addMouseListener(new PopupHandler() {
      public void invokePopup(Component c, int x, int y) {
        ActionPopupMenu m = createPopupMenu(ag);
        m.getComponent().show(c, x, y);
      }
    });
  }

  private ActionPopupMenu createPopupMenu(ActionGroup ag) {
    ActionManager m = ActionManager.getInstance();
    return m.createActionPopupMenu(ActionPlaces.UNKNOWN, ag);
  }

  protected abstract void updateDiffs();

  protected SimpleDiffRequest createDifference(final FileDifferenceModel m) {
    final SimpleDiffRequest r = new SimpleDiffRequest(getProject(), m.getTitle());

    processRevisions(new RevisionProcessingTask() {
      public void run(RevisionProcessingProgress p) {
        p.processingLeftRevision();
        DiffContent left = m.getLeftDiffContent(p);

        p.processingRightRevision();
        DiffContent right = m.getRightDiffContent(p);

        r.setContents(left, right);
        r.setContentTitles(m.getLeftTitle(p), m.getRightTitle(p));
      }
    });

    return r;
  }

  private void restoreShowChangesOnlyOption() {
    myModel.showChangesOnly(LocalHistoryConfiguration.getInstance().SHOW_CHANGES_ONLY);
  }

  private void saveShowChangesOnlyOption() {
    boolean value = myModel.doesShowChangesOnly();
    LocalHistoryConfiguration.getInstance().SHOW_CHANGES_ONLY = value;
  }

  private void restoreSplitterProportion() {
    SplitterProportionsData d = createSplitterData();
    d.externalizeFromDimensionService(getDimensionServiceKey());
    d.restoreSplitterProportions(mySplitter);
  }

  private void saveSplitterProportion() {
    SplitterProportionsData d = createSplitterData();
    d.saveSplitterProportions(mySplitter);
    d.externalizeToDimensionService(getDimensionServiceKey());
  }

  private SplitterProportionsData createSplitterData() {
    return new SplitterProportionsDataImpl();
  }

  @Override
  protected String getDimensionServiceKey() {
    // enables size auto-save
    return getClass().getName();
  }

  @Override
  protected Action[] createActions() {
    // removes ok/cancel buttons
    return new Action[0];
  }

  protected abstract String getHelpId();

  @Override
  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(getHelpId());
  }

  protected void revert() {
    revert(myModel.createReverter());
  }

  private boolean isRevertEnabled() {
    return myModel.isRevertEnabled();
  }

  protected void revert(Reverter r) {
    try {
      if (!askForProceeding(r)) return;

      List<String> errors = r.checkCanRevert();
      if (!errors.isEmpty()) {
        showRevertErrors(errors);
        return;
      }
      r.revert();
      close(0);
    }
    catch (IOException e) {
      myGateway.showError(message("message.error.during.revert", e));
    }
  }

  private boolean askForProceeding(Reverter r) throws IOException {
    List<String> questions = r.askUserForProceeding();
    if (questions.isEmpty()) return true;

    return myGateway.askForProceeding(message("message.do.you.want.to.proceed", formatQuestions(questions)));
  }

  private String formatQuestions(List<String> questions) {
    // format into something like this:
    // 1) message one
    // message one continued
    // 2) message two
    // message one continued
    // ...

    if (questions.size() == 1) return questions.get(0);

    String result = "";
    for (int i = 0; i < questions.size(); i++) {
      result += (i + 1) + ") " + questions.get(i) + "\n";
    }
    return result.substring(0, result.length() - 1);
  }

  private void showRevertErrors(List<String> errors) {
    myGateway.showError(message("message.cannot.revert.because", formatErrors(errors)));
  }

  private String formatErrors(List<String> errors) {
    if (errors.size() == 1) return errors.get(0);

    String result = "";
    for (String e : errors) {
      result += "\n    -" + e;
    }    
    return result;
  }

  private boolean isCreatePatchEnabled() {
    return myModel.isCreatePatchEnabled();
  }

  private void createPatch() {
    try {
      if (!myModel.canPerformCreatePatch()) {
        myGateway.showError(message("message.cannot.create.patch.because.of.unavailable.content"));
        return;
      }

      CreatePatchConfigurationPanel p = new CreatePatchConfigurationPanel();
      p.setFileName(getDefaultPatchFile());
      if (!showAsDialog(p)) return;
      myModel.createPatch(p.getFileName(), p.isReversePatch());
      close(0);
    }
    catch (VcsException e) {
      myGateway.showError(message("message.error.during.create.patch", e));
    }
    catch (IOException e) {
      myGateway.showError(message("message.error.during.create.patch", e));
    }
  }

  private File getDefaultPatchFile() {
    return FileUtil.findSequentNonexistentFile(new File(myGateway.getBaseDir()), "local_history", "patch");
  }

  private boolean showAsDialog(CreatePatchConfigurationPanel p) {
    final DialogBuilder b = new DialogBuilder(myGateway.getProject());
    b.setTitle(message("create.patch.dialog.title"));
    b.setCenterPanel(p.getPanel());
    p.installOkEnabledListener(new Consumer<Boolean>() {
      public void consume(final Boolean aBoolean) {
        b.setOkActionEnabled(aBoolean);
      }
    });
    return b.show() == OK_EXIT_CODE;
  }


  protected void showHelp() {
    HelpManager.getInstance().invokeHelp(getHelpId());
  }

  protected void processRevisions(final RevisionProcessingTask t) {
    new Task.Modal(getProject(), message("message.processing.revisions"), false) {
      public void run(@NotNull ProgressIndicator i) {
        t.run(new RevisionProcessingProgressAdapter(i));
      }
    }.queue();
  }

  protected Project getProject() {
    return myGateway.getProject();
  }

  private class RevertAction extends AnAction {
    public RevertAction() {
      super(message("action.revert"), null, IconLoader.getIcon("/actions/rollback.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      revert();
    }

    public void update(AnActionEvent e) {
      Presentation p = e.getPresentation();
      p.setEnabled(isRevertEnabled());
    }
  }

  private class CreatePatchAction extends AnAction {
    public CreatePatchAction() {
      super(message("action.create.patch"), null, IconLoader.getIcon("/actions/createPatch.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      createPatch();
    }

    public void update(AnActionEvent e) {
      Presentation p = e.getPresentation();
      p.setEnabled(isCreatePatchEnabled());
    }
  }

  private class ShowChangesOnlyAction extends ToggleAction {
    public ShowChangesOnlyAction() {
      super(message("action.show.changes.only"), null, IconLoader.getIcon("/actions/showChangesOnly.png"));
    }

    public boolean isSelected(AnActionEvent e) {
      return myModel.doesShowChangesOnly();
    }

    public void setSelected(AnActionEvent e, boolean state) {
      myModel.showChangesOnly(state);
      myRevisionsTable.updateData();
    }
  }

  private class HelpAction extends AnAction {
    public HelpAction() {
      super(CommonBundle.getHelpButtonText(), null, IconLoader.getIcon("/actions/help.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      doHelpAction();
    }
  }

  protected static interface RevisionProcessingTask {
    void run(RevisionProcessingProgress p);
  }

  private static class RevisionProcessingProgressAdapter implements RevisionProcessingProgress {
    private final ProgressIndicator myIndicator;

    public RevisionProcessingProgressAdapter(ProgressIndicator i) {
      myIndicator = i;
    }

    public void processingLeftRevision() {
      myIndicator.setText(message("message.processing.left.revision"));
    }

    public void processingRightRevision() {
      myIndicator.setText(message("message.processing.right.revision"));
    }

    public void processed(int percentage) {
      myIndicator.setFraction(percentage / 100.0);
    }
  }
}
