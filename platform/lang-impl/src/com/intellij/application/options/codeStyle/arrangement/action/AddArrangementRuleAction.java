/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.application.options.codeStyle.arrangement.action;

import com.intellij.application.options.codeStyle.arrangement.ArrangementConstants;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationBundle;

/**
 * @author Denis Zhdanov
 * @since 8/24/12 1:54 PM
 */
public class AddArrangementRuleAction extends AnAction {
  
  public AddArrangementRuleAction() {
    getTemplatePresentation().setIcon(AllIcons.General.Add);
    getTemplatePresentation().setText(ApplicationBundle.message("arrangement.action.condition.add.text"));
    getTemplatePresentation().setDescription(ApplicationBundle.message("arrangement.action.rule.add.description"));
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Runnable function = ArrangementConstants.NEW_RULE_FUNCTION_KEY.getData(e.getDataContext());
    if (function != null) {
      function.run();
    }
  }
}
