package com.intellij.find.editorHeaderActions;

import com.intellij.find.EditorSearchComponent;
import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.find.FindUtil;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;

/**
* Created by IntelliJ IDEA.
* User: zajac
* Date: 05.03.11
* Time: 10:53
* To change this template use File | Settings | File Templates.
*/
public class FindAllAction extends EditorHeaderAction implements DumbAware {
  public FindAllAction(EditorSearchComponent editorSearchComponent) {
    super(editorSearchComponent);
    getTemplatePresentation().setIcon(IconLoader.getIcon("/actions/export.png"));
    getTemplatePresentation().setDescription("Export matches to Find tool window");
    getTemplatePresentation().setText("Find All");
    registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_USAGES).getShortcutSet(),
                              editorSearchComponent.getSearchField());
  }

  public void update(final AnActionEvent e) {
    super.update(e);
    Project project = e.getData(PlatformDataKeys.PROJECT);
    Editor editor = e.getData(PlatformDataKeys.EDITOR);
    if (project != null && editor != null) {
      e.getPresentation().setEnabled(getEditorSearchComponent().hasMatches() &&
                                     PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument()) != null);
    }
  }

  public void actionPerformed(final AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    Editor editor = e.getData(PlatformDataKeys.EDITOR);
    final FindModel model = FindManager.getInstance(project).getFindInFileModel();
    final FindModel realModel = (FindModel)model.clone();
    String text = getEditorSearchComponent().getTextInField();
    if (StringUtil.isEmpty(text)) return;
    realModel.setStringToFind(text);
    FindUtil.findAll(project, editor, realModel);
  }
}
