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
package org.jetbrains.idea.svn.dialogs.browser;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.dialogs.RepositoryBrowserComponent;
import org.jetbrains.idea.svn.dialogs.RepositoryTreeNode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreeNode;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;

public class CopyOptionsDialog extends DialogWrapper {

  private final SVNURL myURL;
  private JTextArea myCommitMessage;
  private final Project myProject;
  private JTextField myNameField;
  private JLabel myURLLabel;
  private RepositoryBrowserComponent myBrowser;
  private JLabel myTargetURL;
  private JComboBox myMessagesBox;
  private JPanel myMainPanel;

  public CopyOptionsDialog(String title, Project project, final RepositoryTreeNode root, final RepositoryTreeNode node) {
    super(project, true);
    myProject = project;
    myURL = node.getURL();

    myURLLabel.setText(myURL.toString());

    final TreeNode[] path = node.getSelfPath();
    final TreeNode[] subPath = new TreeNode[path.length - 1];
    System.arraycopy(path, 1, subPath, 0, path.length - 1);

    myBrowser.setRepositoryURL(root.getURL(), false, 
        new OpeningExpander.Factory(subPath, (RepositoryTreeNode)((node.getParent() instanceof RepositoryTreeNode) ? node.getParent() : null)));
    myBrowser.addChangeListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        update();
      }
    });

    myNameField.setText(SVNPathUtil.tail(myURL.getPath()));
    myNameField.selectAll();
    myNameField.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        update();
      }
    });

    ArrayList<String> messages = VcsConfiguration.getInstance(myProject).getRecentMessages();
    Collections.reverse(messages);
    Object[] model = messages.toArray();
    myMessagesBox.setModel(new DefaultComboBoxModel(model));
    myMessagesBox.setRenderer(new MessageBoxCellRenderer());

    String lastMessage = VcsConfiguration.getInstance(myProject).getLastNonEmptyCommitMessage();
    if (lastMessage != null) {
      myCommitMessage.setText(lastMessage);
      myCommitMessage.selectAll();
    }
    myMessagesBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final Object item = myMessagesBox.getSelectedItem();
        if (item != null) {
          myCommitMessage.setText(item.toString());
          myCommitMessage.selectAll();
        }
      }
    });

    setTitle(title);
    init();
    update();
    
  }

  @Override
  protected void dispose() {
    super.dispose();
    Disposer.dispose(myBrowser);
  }

  @NonNls
  protected String getDimensionServiceKey() {
    return "svn4idea.copy.options";
  }

  public String getCommitMessage() {
    return myCommitMessage.getText();
  }

  public SVNURL getSourceURL() {
    return myURL;
  }

  public String getName() {
    return myNameField.getText();
  }

  @Nullable
  public SVNURL getTargetURL() {
    if (getOKAction().isEnabled()) {
      try {
        return SVNURL.parseURIEncoded(myTargetURL.getText());
      } catch (SVNException e) {
        //
      }
    }
    return null;
  }

  @Nullable
  public RepositoryTreeNode getTargetParentNode() {
    return myBrowser.getSelectedNode();
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  private void createUIComponents() {
    myBrowser = new RepositoryBrowserComponent(SvnVcs.getInstance(myProject));
  }

  private void update() {
    RepositoryTreeNode baseNode = myBrowser.getSelectedNode();
    if (baseNode == null) {
      myTargetURL.setText("");
      getOKAction().setEnabled(false);
      return;
    }
    SVNURL baseURL = baseNode.getURL();
    String name = myNameField.getText();
    if (name == null || "".equals(name)) {
      getOKAction().setEnabled(false);
      return;
    }
    try {
      baseURL = baseURL.appendPath(myNameField.getText(), false);
    } catch (SVNException e) {
      //
      getOKAction().setEnabled(false);
      return;
    }
    myTargetURL.setText(baseURL.toString());
    getOKAction().setEnabled(!myURL.toString().equals(myTargetURL.getText()));
  }


  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }
}
