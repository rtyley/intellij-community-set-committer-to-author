/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.refactoring;

import com.intellij.codeInsight.unwrap.ScopeHighlighter;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.popup.JBPopupAdapter;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.Pass;
import com.intellij.psi.PsiElement;
import com.intellij.util.Function;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class IntroduceTargetChooser {
  private IntroduceTargetChooser() {
  }

  public static <T extends PsiElement> void showChooser(final Editor editor, final List<T> expressions, final Pass<T> callback,
                                                        final Function<T, String> renderer) {
    final ScopeHighlighter highlighter = new ScopeHighlighter(editor);
    final DefaultListModel model = new DefaultListModel();
    for (T expr : expressions) {
      model.addElement(expr);
    }
    final JList list = new JList(model);
    list.setCellRenderer(new DefaultListCellRenderer() {

      @Override
      public Component getListCellRendererComponent(final JList list,
                                                    final Object value,
                                                    final int index,
                                                    final boolean isSelected,
                                                    final boolean cellHasFocus) {
        final Component rendererComponent = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

        final T expr = (T)value;
        if (expr.isValid()) {
          setText(renderer.fun(expr));
        }
        return rendererComponent;
      }
    });

    list.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(final ListSelectionEvent e) {
        highlighter.dropHighlight();
        final int index = list.getSelectedIndex();
        if (index < 0 ) return;
        final T expr = (T)model.get(index);
        final ArrayList<PsiElement> toExtract = new ArrayList<PsiElement>();
        toExtract.add(expr);
        highlighter.highlight(expr, toExtract);
      }
    });

    JBPopupFactory.getInstance().createListPopupBuilder(list)
          .setTitle("Expressions")
          .setMovable(false)
          .setResizable(false)
          .setRequestFocus(true)
          .setItemChoosenCallback(new Runnable() {
                                    public void run() {
                                      callback.pass((T)list.getSelectedValue());
                                    }
                                  })
          .addListener(new JBPopupAdapter() {
                          @Override
                          public void onClosed(LightweightWindowEvent event) {
                            highlighter.dropHighlight();
                          }
                       })
          .createPopup().showInBestPositionFor(editor);
  }
}
