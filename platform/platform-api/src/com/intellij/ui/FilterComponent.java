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
package com.intellij.ui;

import com.intellij.openapi.application.ModalityState;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;

/**
 * User: anna
 * Date: 16-Dec-2005
 */
public abstract class FilterComponent extends JPanel {
  private final SearchTextFieldWithStoredHistory myFilter;
  private final Alarm myUpdateAlarm = new Alarm();

  public FilterComponent(@NonNls String propertyName, int historySize) {
    super(new BorderLayout());
    myFilter = new SearchTextFieldWithStoredHistory(propertyName);
    myFilter.getTextEditor().addKeyListener(new KeyAdapter() {
      //to consume enter in combo box - do not process this event by default button from DialogWrapper
      public void keyPressed(final KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
          e.consume();
          myFilter.addCurrentTextToHistory();
          filter();
        }
      }
    });

    myFilter.addDocumentListener(new DocumentListener() {
      public void insertUpdate(DocumentEvent e) {
        onChange();
      }

      public void removeUpdate(DocumentEvent e) {
        onChange();
      }

      public void changedUpdate(DocumentEvent e) {
        onChange();
      }

      public void onChange() {
        myUpdateAlarm.cancelAllRequests();
        myUpdateAlarm.addRequest(new Runnable(){
          public void run() {
            onlineFilter();
          }
        }, 100, ModalityState.stateForComponent(myFilter));
      }
    });

    myFilter.setHistorySize(historySize);
    add(myFilter, BorderLayout.CENTER);    
  }

  public void setHistorySize(int historySize){
    myFilter.setHistorySize(historySize);
  }

  public void reset(){
    myFilter.reset();
  }

  public String getFilter(){
    return myFilter.getText();
  }

  public void setSelectedItem(final String filter) {
    myFilter.setSelectedItem(filter);
  }

  public void setFilter(final String filter){
    myFilter.setText(filter);
  }

  public void selectText(){
    myFilter.selectText();
  }

  public boolean requestFocusInWindow() {
    return myFilter.requestFocusInWindow();
  }

  public abstract void filter();

  protected void onlineFilter(){
    filter();
  }

  public void dispose() {
    myUpdateAlarm.cancelAllRequests();
  }

  protected void setHistory(List<String> strings) {
    myFilter.setHistory(strings);
  }
}
