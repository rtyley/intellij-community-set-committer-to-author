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
package org.jetbrains.idea.maven.utils;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.Icons;
import com.intellij.util.PairProcessor;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.AsyncProcessIcon;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenArtifactInfo;
import org.jetbrains.idea.maven.model.MavenRepositoryInfo;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.*;
import java.util.List;

public class RepositoryAttachDialog extends DialogWrapper {
  private static final String DEFAULT_REPOSITORY = "<default>";
  private final JLabel myInfoLabel;
  private final Project myProject;
  private final boolean myManaged;
  private final AsyncProcessIcon myProgressIcon;
  private final THashMap<String, MavenArtifactInfo> myCoordinates = new THashMap<String, MavenArtifactInfo>();
  private final Map<String, MavenRepositoryInfo> myRepositories = new TreeMap<String, MavenRepositoryInfo>();
  private final ArrayList<String> myShownItems = new ArrayList<String>();
  private final JComboBox myCombobox = new JComboBox(new CollectionComboBoxModel(myShownItems, null));

  private TextFieldWithBrowseButton myDirectoryField;
  private String myFilterString;
  private JComboBox myRepositoryUrl;

  public RepositoryAttachDialog(Project project, boolean managed) {
    super(project, true);
    myProject = project;
    myManaged = managed;
    myProgressIcon = new AsyncProcessIcon("Progress");
    myProgressIcon.setVisible(false);
    myProgressIcon.suspend();
    myInfoLabel = new JLabel("");
    myCombobox.setEditable(true);
    final JTextField textField = (JTextField)myCombobox.getEditor().getEditorComponent();
    textField.setColumns(50);
    textField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            if (myProgressIcon.isDisposed()) return;
            updateComboboxSelection(false);
          }
        });
      }
    });
    textField.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        final boolean popupVisible = myCombobox.isPopupVisible();
        if (e.getKeyCode() == KeyEvent.VK_ENTER && e.getModifiers() == 0) {
          //if (true) return;
          if (popupVisible && !myCoordinates.isEmpty()) {
            final String item = (String)myCombobox.getSelectedItem();
            if (StringUtil.isNotEmpty(item)) {
              ((JTextField)myCombobox.getEditor().getEditorComponent()).setText(item);
            }
          }
          else if (!popupVisible || myCoordinates.isEmpty()) {
            if (performSearch()) {
              e.consume();
            }
          }
        }
      }
    });
    updateInfoLabel();
    init();
  }

  public String getDirectoryPath() {
    return myDirectoryField.getText();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myCombobox;
  }

  private void updateComboboxSelection(boolean force) {
    final String prevFilter = myFilterString;
    final JTextComponent field = (JTextComponent)myCombobox.getEditor().getEditorComponent();
    final String prefix = field.getText();
    final int caret = field.getCaretPosition();
    myFilterString = prefix.toUpperCase();

    if (!force && myFilterString.equals(prevFilter)) return;
    myShownItems.clear();
    final boolean itemSelected = Comparing.equal(myCombobox.getSelectedItem(), prefix);
    if (itemSelected) {
      myShownItems.addAll(myCoordinates.keySet());
    }
    else {
      final String[] parts = myFilterString.split(" ");
      main:
      for (String coordinate : myCoordinates.keySet()) {
        final String candidate = coordinate.toUpperCase();
        for (String part : parts) {
          if (!candidate.contains(part)) continue main;
        }
        myShownItems.add(coordinate);
      }
      if (myShownItems.isEmpty()) {
        myShownItems.addAll(myCoordinates.keySet());
      }
      myCombobox.setSelectedItem(null);
    }
    Collections.sort(myShownItems);
    ((CollectionComboBoxModel)myCombobox.getModel()).update();
    field.setText(prefix);
    field.setCaretPosition(caret);
    updateInfoLabel();
    if (myCombobox.getEditor().getEditorComponent().hasFocus()) {
      myCombobox.setPopupVisible(!myShownItems.isEmpty() && !itemSelected);
    }
  }

  private boolean performSearch() {
    final String text = getCoordinateText();
    if (myCoordinates.contains(text)) return false;
    if (myProgressIcon.isVisible()) return false;
    myProgressIcon.setVisible(true);
    myProgressIcon.resume();
    RepositoryAttachHandler.searchArtifacts(myProject, text, new PairProcessor<Collection<MavenArtifactInfo>, Boolean>() {
      public boolean process(Collection<MavenArtifactInfo> artifacts, Boolean tooMany) {
        if (myProgressIcon.isDisposed()) return true;
        myProgressIcon.suspend();
        myProgressIcon.setVisible(false);
        final int prevSize = myCoordinates.size();
        for (MavenArtifactInfo each : artifacts) {
          myCoordinates.put(each.getGroupId() + ":" + each.getArtifactId() + ":" + each.getVersion(), each);
        }

        myRepositoryUrl.setModel(new CollectionComboBoxModel(new ArrayList<String>(myRepositories.keySet()), myRepositoryUrl.getEditor().getItem()));
        if (Boolean.TRUE.equals(tooMany)) {
          final Point point = new Point(myCombobox.getWidth() / 2, 0);
          JBPopupFactory.getInstance().createHtmlTextBalloonBuilder("Too many results found, please refine your query.", MessageType.WARNING, null).
            setHideOnClickOutside(true).
            createBalloon().show(new RelativePoint(myCombobox, point), Balloon.Position.above);
        }
        updateComboboxSelection(prevSize != myCoordinates.size());
        return true;
      }
    }, new Processor<Collection<MavenRepositoryInfo>>() {
      public boolean process(Collection<MavenRepositoryInfo> repos) {
        for (MavenRepositoryInfo each : repos) {
          if (!myRepositories.containsKey(each.getUrl())) {
            myRepositories.put(each.getUrl(), each);
          }
        }
        return true;
      }
    });
    return true;
  }

  private void updateInfoLabel() {
    myInfoLabel.setText(myCombobox.getModel().getSize() +"/" + myCoordinates.size());
  }

  @Override
  public boolean isOKActionEnabled() {
    return true;
  }

  @Override
  protected void doOKAction() {
    if (!isOKActionEnabled()) return;
    if (!isValidCoordinateSelected()) {
      IdeFocusManager.findInstance().requestFocus(myCombobox, true);
      Messages.showErrorDialog("Please enter valid coordinate or select one from the list",
                               "Coordinate not specified");
      return;
    }
    if (!myManaged) {
      final File dir = new File(myDirectoryField.getText());
      if (!dir.exists() && !dir.mkdirs() || !dir.isDirectory()) {
        IdeFocusManager.findInstance().requestFocus(myDirectoryField.getChildComponent(), true);
        Messages.showErrorDialog("Please enter valid library files path",
                                 "Library files path not specified");
        return;
      }
    }
    super.doOKAction();
  }

  @Override
  protected JComponent createCenterPanel() {
    return null;
  }

  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new BorderLayout(15, 0));
    {
      JLabel iconLabel = new JLabel(Messages.getQuestionIcon());
      Container container = new Container();
      container.setLayout(new BorderLayout());
      container.add(iconLabel, BorderLayout.NORTH);
      panel.add(container, BorderLayout.WEST);
    }

    final ArrayList<JComponent> gridComponents = new ArrayList<JComponent>();
    {
      JPanel caption = new JPanel(new BorderLayout(15, 0));
      JLabel textLabel = new JLabel("Enter keywords to search by, class name or Maven coordinates: \ni.e. 'spring', 'jsf' or 'org.hibernate:hibernate-core:3.3.0.GA'");
      textLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
      textLabel.setUI(new MultiLineLabelUI());
      caption.add(textLabel, BorderLayout.WEST);
      final JPanel infoPanel = new JPanel(new BorderLayout());
      infoPanel.add(myInfoLabel, BorderLayout.WEST);
      infoPanel.add(myProgressIcon, BorderLayout.EAST);
      caption.add(infoPanel, BorderLayout.EAST);
      gridComponents.add(caption);

      final ComponentWithBrowseButton<JComboBox> coordComponent = new ComponentWithBrowseButton<JComboBox>(myCombobox, new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          performSearch();
        }
      });
      coordComponent.setButtonIcon(Icons.SYNCHRONIZE_ICON);
      gridComponents.add(coordComponent);

      final LabeledComponent<JComboBox> repository = new LabeledComponent<JComboBox>();
      repository.getLabel().setText("Repository URL to Download From:");
      myRepositories.put(DEFAULT_REPOSITORY, null);
      for (MavenRepositoryInfo repo : RepositoryAttachHandler.getDefaultRepositories()) {
        myRepositories.put(repo.getUrl(), repo);
      }
      myRepositoryUrl = new JComboBox(new CollectionComboBoxModel(new ArrayList<String>(myRepositories.keySet()), DEFAULT_REPOSITORY));
      myRepositoryUrl.setEditable(true);
      repository.setComponent(myRepositoryUrl);
      gridComponents.add(repository);

      if (!myManaged) {
        myDirectoryField = new TextFieldWithBrowseButton();
        if (myProject != null && !myProject.isDefault()) {
          final VirtualFile baseDir = myProject.getBaseDir();
          if (baseDir != null) {
            myDirectoryField.setText(FileUtil.toSystemDependentName(baseDir.getPath()+"/lib"));
          }
        }
        myDirectoryField.addBrowseFolderListener(ProjectBundle.message("file.chooser.directory.for.downloaded.libraries.title"),
                                   ProjectBundle.message("file.chooser.directory.for.downloaded.libraries.description"), null,
                                   FileChooserDescriptorFactory.createSingleFolderDescriptor());

        final LabeledComponent<TextFieldWithBrowseButton> dirComponent = new LabeledComponent<TextFieldWithBrowseButton>();
        dirComponent.getLabel().setText("Store Library Files in: ");
        dirComponent.setComponent(myDirectoryField);
        gridComponents.add(dirComponent);
      }
    }
    JPanel messagePanel = new JPanel(new GridLayoutManager(gridComponents.size(), 1));
    for (int i = 0, gridComponentsSize = gridComponents.size(); i < gridComponentsSize; i++) {
      messagePanel.add(gridComponents.get(i), new GridConstraints(i, 0, 1, 1, GridConstraints.ANCHOR_NORTHWEST, GridConstraints.FILL_HORIZONTAL,
                                                          GridConstraints.SIZEPOLICY_CAN_GROW | GridConstraints.SIZEPOLICY_CAN_SHRINK, 0,
                                                          null, null, null));
    }
    panel.add(messagePanel, BorderLayout.CENTER);

    return panel;
  }


  @Override
  protected void dispose() {
    Disposer.dispose(myProgressIcon);
    super.dispose();
  }

  @Override
  protected String getDimensionServiceKey() {
    return RepositoryAttachDialog.class.getName();
  }

  @NotNull
  public List<MavenRepositoryInfo> getRepositories() {
    final String selectedRepository = (String)myRepositoryUrl.getEditor().getItem();
    if (StringUtil.isNotEmpty(selectedRepository) && !DEFAULT_REPOSITORY.equals(selectedRepository) && !myRepositories.containsKey(selectedRepository)) {
      return Collections.singletonList(new MavenRepositoryInfo("custom", null, selectedRepository));
    }
    else {
      final MavenArtifactInfo artifact = myCoordinates.get(getCoordinateText());
      final MavenRepositoryInfo repository =
        artifact != null ? findRepositoryFor(artifact) : null;
      return repository != null? Collections.singletonList(repository) : ContainerUtil.findAll(myRepositories.values(), Condition.NOT_NULL);
    }
  }

  private MavenRepositoryInfo findRepositoryFor(MavenArtifactInfo artifact) {
    String soughtFor = artifact.getRepositoryId();
    if (soughtFor == null) return null;
    
    for (MavenRepositoryInfo each : myRepositories.values()) {
      if (each.getId().equals(soughtFor)) return each;
    }
    return null;
  }

  private boolean isValidCoordinateSelected() {
    final String text = getCoordinateText();
    if (myCombobox.getModel().getSelectedItem() == null) return false;
    return text.split(":").length == 3;
  }

  public String getCoordinateText() {
    final JTextField field = (JTextField)myCombobox.getEditor().getEditorComponent();
    return field.getText();
  }

}
