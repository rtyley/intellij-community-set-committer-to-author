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
package com.intellij.ide.util;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 28, 2003
 */
public class BrowseFilesListener implements ActionListener {
  private final JTextField myTextField;
  private final String myTitle;
  private final String myDescription;
  private final FileChooserDescriptor myChooserDescriptor;
  public static final FileChooserDescriptor SINGLE_DIRECTORY_DESCRIPTOR = new FileChooserDescriptor(false, true, false, false, false, false);
  public static final FileChooserDescriptor SINGLE_FILE_DESCRIPTOR = new FileChooserDescriptor(true, false, false, false, false, false);

  public BrowseFilesListener(JTextField textField, final String title, final String description, final FileChooserDescriptor chooserDescriptor) {
    myTextField = textField;
    myTitle = title;
    myDescription = description;
    myChooserDescriptor = chooserDescriptor;
  }

  protected VirtualFile getFileToSelect() {
    final String path = myTextField.getText().trim().replace(File.separatorChar, '/');
    if (path.length() > 0) {
      File file = new File(path);
      while (file != null && !file.exists()) {
        file = file.getParentFile();
      }
      if (file != null) {
        return LocalFileSystem.getInstance().findFileByIoFile(file);
      }
    }
    return null;
  }

  public void actionPerformed( ActionEvent e ) {
    final VirtualFile fileToSelect = getFileToSelect();
    myChooserDescriptor.setTitle(myTitle); // important to set title and description here because a shared descriptor instance can be used
    myChooserDescriptor.setDescription(myDescription);
    VirtualFile[] files = (fileToSelect != null)?
                          FileChooser.chooseFiles(myTextField, myChooserDescriptor, fileToSelect) :
                          FileChooser.chooseFiles(myTextField, myChooserDescriptor);
    if (files != null && files.length > 0) {
      myTextField.setText(files[0].getPath().replace('/', File.separatorChar));
    }
  }
}
