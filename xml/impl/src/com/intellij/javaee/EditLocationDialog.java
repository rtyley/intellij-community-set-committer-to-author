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
package com.intellij.javaee;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xml.XmlBundle;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

public class EditLocationDialog extends DialogWrapper {

  private JTextField myTfUrl;
  private JTextField myTfPath;
  private FixedSizeButton myBtnBrowseLocalPath;
  private final Project myProject;
  private final boolean myShowPath;

  private final String myTitle;
  private final String myName;
  private final String myLocation;
  private boolean myTfShared = true;

  public EditLocationDialog(Project project, boolean showPath) {
    super(project, true);
    myProject = project;
    myShowPath = showPath;
    myTitle = XmlBundle.message("dialog.title.external.resource");
    myName = XmlBundle.message("label.edit.external.resource.uri");
    myLocation = XmlBundle.message("label.edit.external.resource.path");
    init();
  }

  public EditLocationDialog(Project project, boolean showPath, String title, String name, String location) {
    super(project, true);
    myProject = project;
    myShowPath = showPath;
    myTitle = title;
    myName = name;
    myLocation = location;
    init();
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new GridBagLayout());

    panel.add(
        new JLabel(myName),
        new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 5, 3, 5), 0, 0)
    );
    panel.add(
        myTfUrl,
        new GridBagConstraints(0, 1, 2, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(0, 5, 5, 5), 0, 0)
    );

    myTfUrl.setPreferredSize(new Dimension(350, myTfUrl.getPreferredSize().height));

  if (myShowPath) {
      panel.add(
          new JLabel(myLocation),
          new GridBagConstraints(0, 2, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 5, 3, 5), 0, 0)
      );
      panel.add(
          myTfPath,
          new GridBagConstraints(0, 3, 1, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(0, 5, 10, 0), 0, 0)
      );
      panel.add(
          myBtnBrowseLocalPath,
          new GridBagConstraints(1, 3, 1, 1, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(0, 0, 10, 5), 0, 0)
      );

      //
    TextFieldWithBrowseButton.MyDoClickAction.addTo(myBtnBrowseLocalPath, myTfPath);
    myBtnBrowseLocalPath.addActionListener(
          new ActionListener() {
            public void actionPerformed(ActionEvent ignored) {
              FileChooserDescriptor descriptor = getChooserDescriptor();
              VirtualFile[] files = FileChooser.chooseFiles(myProject, descriptor);
              if (files.length != 0) {
                myTfPath.setText(files[0].getPath().replace('/', File.separatorChar));
              }
            }
          }
      );
    }

    //

    return panel;
  }

  public JComponent getPreferredFocusedComponent() {
    return myTfUrl;
  }

  public NameLocationPair getPair() {
    String path = myTfPath.getText().trim();
    String url = myTfUrl.getText().trim();
    return new NameLocationPair(url, path, myTfShared);
  }

  protected FileChooserDescriptor getChooserDescriptor(){
    return new FileChooserDescriptor(true, false, false, false, true, false);
  }

  protected void init() {
    setTitle(myTitle);
    myTfUrl = new JTextField();
    myTfPath = new JTextField();
    myBtnBrowseLocalPath = new FixedSizeButton(myTfPath);
    super.init();
  }

  /**
   * Initializes editor with the passed data.
   */
  public void init(NameLocationPair origin) {
    myTfUrl.setText(origin.myName);
    myTfPath.setText(origin.myLocation);
    myTfShared = origin.myShared;
  }

  public static class NameLocationPair implements Comparable {
    String myName;
    String myLocation;
    boolean myShared;

    public NameLocationPair(String name, String location, boolean shared) {
      myName = name;
      myLocation = location;
      myShared = shared;
    }

    public int compareTo(Object o) {
      return myName.compareTo(((NameLocationPair)o).myName);
    }

    public boolean equals(Object obj) {
      if (! (obj instanceof NameLocationPair)) return false;
      return compareTo(obj) == 0;
    }

    public int hashCode() {
      return myName.hashCode();
    }

    public String getName() {
      return myName;
    }

    public String getLocation(){
      return myLocation;
    }
  }
}
