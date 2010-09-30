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
package com.intellij.facet.impl.ui.libraries;

import com.intellij.facet.ui.libraries.LibraryInfo;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureDialogCellAppearanceUtils;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.ExistingLibraryEditor;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.NewLibraryEditor;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SortedComboBoxModel;
import com.intellij.util.Icons;
import com.intellij.util.PathUtil;
import com.intellij.util.ui.RadioButtonEnumModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class LibraryOptionsPanel {
  private JLabel myMessageLabel;
  private JPanel myPanel;
  private JButton myConfigureButton;
  private JComboBox myExistingLibraryComboBox;
  private JRadioButton myDoNotCreateRadioButton;
  private JPanel myConfigurationPanel;
  private JButton myCreateButton;
  private ButtonGroup myButtonGroup;

  private final LibraryCompositionSettings mySettings;
  private final LibrariesContainer myLibrariesContainer;
  private final SortedComboBoxModel<LibraryEditor> myLibraryComboBoxModel;

  private enum Choice {
    USE_LIBRARY,
    DOWNLOAD,
    SETUP_LIBRARY_LATER
  }

  private RadioButtonEnumModel<Choice> myButtonEnumModel;

  public LibraryOptionsPanel(@NotNull LibraryCompositionSettings settings,
                             @NotNull final LibrariesContainer librariesContainer,
                             final boolean showDoNotCreateOption) {
    mySettings = settings;
    myLibrariesContainer = librariesContainer;
    List<Library> libraries = calculateSuitableLibraries();

    myButtonEnumModel = RadioButtonEnumModel.bindEnum(Choice.class, myButtonGroup);
    myButtonEnumModel.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateState();
      }
    });

    myDoNotCreateRadioButton.setVisible(showDoNotCreateOption);
    myLibraryComboBoxModel = new SortedComboBoxModel<LibraryEditor>(new Comparator<LibraryEditor>() {
      @Override
      public int compare(LibraryEditor o1, LibraryEditor o2) {
        final String name1 = o1.getName();
        final String name2 = o2.getName();
        return StringUtil.notNullize(name1).compareToIgnoreCase(StringUtil.notNullize(name2));
      }
    });
    for (Library library : libraries) {
      ExistingLibraryEditor libraryEditor = librariesContainer.getLibraryEditor(library);
      if (libraryEditor == null) {
        libraryEditor = mySettings.getOrCreateEditor(library);
      }
      myLibraryComboBoxModel.add(libraryEditor);
    }
    myExistingLibraryComboBox.setModel(myLibraryComboBoxModel);
    if (libraries.isEmpty()) {
      myLibraryComboBoxModel.add(null);
    }
    myExistingLibraryComboBox.setSelectedIndex(0);
    myExistingLibraryComboBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED && e.getItem() != null) {
          myButtonEnumModel.setSelected(Choice.USE_LIBRARY);
        }
        updateState();
      }
    });
    myExistingLibraryComboBox.setRenderer(new ColoredListCellRenderer() {
      @Override
      protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (value == null) {
          append("[No library selected]");
        }
        else if (value instanceof ExistingLibraryEditor) {
          ProjectStructureDialogCellAppearanceUtils.forLibrary(((ExistingLibraryEditor)value).getLibrary(), null).customize(this);
        }
        else if (value instanceof NewLibraryEditor) {
          setIcon(Icons.LIBRARY_ICON);
          final String name = ((NewLibraryEditor)value).getName();
          append(name != null ? name : "<unnamed>");
        }
      }
    });
    myButtonEnumModel.setSelected(libraries.isEmpty() ? Choice.DOWNLOAD : Choice.USE_LIBRARY);

    myCreateButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final VirtualFile[] roots = showFileChooser();
        if (roots.length > 0) {
          final NewLibraryEditor libraryEditor = new NewLibraryEditor();
          libraryEditor.setName(librariesContainer.suggestUniqueLibraryName(mySettings.getDefaultLibraryName()));
          for (VirtualFile root : roots) {
            libraryEditor.addRoot(root, OrderRootType.CLASSES);
          }
          if (myLibraryComboBoxModel.get(0) == null) {
            myLibraryComboBoxModel.remove(0);
          }
          myLibraryComboBoxModel.add(libraryEditor);
          myLibraryComboBoxModel.setSelectedItem(libraryEditor);
          myButtonEnumModel.setSelected(Choice.USE_LIBRARY);
        }
      }
    });
    myConfigureButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        switch (myButtonEnumModel.getSelected()) {
          case DOWNLOAD:
            new DownloadingOptionsDialog(myPanel, mySettings).show();
            break;
          case USE_LIBRARY:
            final Object item = myExistingLibraryComboBox.getSelectedItem();
            if (item instanceof LibraryEditor) {
              EditLibraryDialog dialog = new EditLibraryDialog(myPanel, mySettings, (LibraryEditor)item);
              dialog.show();
              if (item instanceof ExistingLibraryEditor) {
                new WriteAction() {
                  protected void run(final Result result) {
                    ((ExistingLibraryEditor)item).commit();
                  }
                }.execute();
              }
            }
            break;
          default:
            break;
        }
        updateState();
      }
    });

    updateState();
  }

  private List<Library> calculateSuitableLibraries() {
    LibraryInfo[] libraryInfos = mySettings.getLibraryInfos();
    RequiredLibrariesInfo requiredLibraries = new RequiredLibrariesInfo(libraryInfos);
    List<Library> suitableLibraries = new ArrayList<Library>();
    Library[] libraries = myLibrariesContainer.getAllLibraries();
    for (Library library : libraries) {
      RequiredLibrariesInfo.RequiredClassesNotFoundInfo info =
        requiredLibraries.checkLibraries(myLibrariesContainer.getLibraryFiles(library, OrderRootType.CLASSES), false);
      if (info == null) {
        suitableLibraries.add(library);
      }
    }
    return suitableLibraries;
  }

  private VirtualFile[] showFileChooser() {
    final FileChooserDescriptor descriptor = new FileChooserDescriptor(false, false, true, false, false, true);
    descriptor.setTitle(IdeBundle.message("file.chooser.select.paths.title"));
    descriptor.setDescription(IdeBundle.message("file.chooser.multiselect.description"));
    return FileChooser.chooseFiles(myPanel, descriptor, getBaseDirectory());
  }

  @Nullable
  private VirtualFile getBaseDirectory() {
    String path = mySettings.getBaseDirectoryForDownloadedFiles();
    VirtualFile dir = LocalFileSystem.getInstance().findFileByPath(path);
    if (dir == null) {
      path = path.substring(0, path.lastIndexOf('/'));
      dir = LocalFileSystem.getInstance().findFileByPath(path);
    }
    return dir;
  }

  private void updateState() {
    myMessageLabel.setIcon(null);
    String message = "";
    boolean showConfigurePanel = true;
    switch (myButtonEnumModel.getSelected()) {
      case DOWNLOAD:
        message = getDownloadFilesMessage();
        break;
      case USE_LIBRARY:
        final Object item = myExistingLibraryComboBox.getSelectedItem();
        if (item == null) {
          myMessageLabel.setIcon(IconLoader.getIcon("/runConfigurations/configurationWarning.png"));
          message = "<b>Error:</b> library is not specified";
        }
        else if (item instanceof NewLibraryEditor) {
          final LibraryEditor libraryEditor = (LibraryEditor)item;
          message = MessageFormat.format("{0} level library <b>{1}</b>" +
                                         " with {2} file(s) will be created",
                                         mySettings.getLibraryLevel(),
                                         libraryEditor.getName(),
                                         libraryEditor.getFiles(OrderRootType.CLASSES).length);
        }
        else {
          message = MessageFormat.format("<b>{0}</b> library will be used", ((ExistingLibraryEditor)item).getName());
        }
        break;
      default:
        showConfigurePanel = false;
    }

    if (!showConfigurePanel) {
        //show the longest message on the hidden card to ensure that dialog won't jump if user selects another option
        message = getDownloadFilesMessage();
    }
    ((CardLayout)myConfigurationPanel.getLayout()).show(myConfigurationPanel, showConfigurePanel ? "configure" : "empty");
    myMessageLabel.setText("<html>" + message + "</html>");
  }

  private String getDownloadFilesMessage() {
    final String downloadPath = mySettings.getDirectoryForDownloadedLibrariesPath();
    final String basePath = mySettings.getBaseDirectoryForDownloadedFiles();
    String path;
    if (FileUtil.startsWith(downloadPath, basePath)) {
      path = FileUtil.getRelativePath(basePath, downloadPath, File.separatorChar);
    }
    else {
      path = PathUtil.getFileName(downloadPath);
    }
    return MessageFormat.format("{0} jar(s) will be downloaded into <b>{1}</b> directory <br>" +
                                   "{2} library <b>{3}</b> will be created",
                                   mySettings.getLibraryInfos().length,
                                   path,
                                   mySettings.getLibraryLevel(),
                                   mySettings.getDownloadedLibraryName());
  }

  public LibraryCompositionSettings getSettings() {
    return mySettings;
  }

  public void apply() {
    final Object item = myExistingLibraryComboBox.getSelectedItem();
    if (item instanceof ExistingLibraryEditor) {
      mySettings.setSelectedExistingLibrary(((ExistingLibraryEditor)item).getLibrary());
    }
    else if (item instanceof NewLibraryEditor) {
      mySettings.setNewLibraryEditor((NewLibraryEditor)item);
    }
    mySettings.setDownloadLibraries(myButtonEnumModel.getSelected() == Choice.DOWNLOAD);
  }

  public JComponent getMainPanel() {
    return myPanel;
  }
}
