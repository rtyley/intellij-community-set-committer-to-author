package org.zmlx.hg4idea.ui;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import org.zmlx.hg4idea.HgVcsMessages;
import org.zmlx.hg4idea.command.HgVersionCommand;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashSet;
import java.util.Set;

/**
 * HgSetExecutablePathPanel is a {@link com.intellij.openapi.ui.TextFieldWithBrowseButton}, which opens a file chooser for hg executable
 * and checks validity of the selected file to be an hg executable.
 */
class HgSetExecutablePathPanel extends TextFieldWithBrowseButton {

  private Set<ActionListener> okListeners = new HashSet<ActionListener>();

  HgSetExecutablePathPanel() {
    FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, false) {
      public void validateSelectedFiles(VirtualFile[] files) throws Exception {
        HgVersionCommand command = new HgVersionCommand();
        String path = files[0].getPath();
        if (!command.isValid(path)) {
          throw new ConfigurationException(HgVcsMessages.message("hg4idea.configuration.executable.error", path));
        }
        for (ActionListener okListener : okListeners) {
          okListener.actionPerformed(null);
        }
      }
    };
    addBrowseFolderListener(HgVcsMessages.message("hg4idea.configuration.title"), HgVcsMessages.message("hg4idea.configuration.description"), null, descriptor);
  }

  void addOKListener(ActionListener listener) {
    okListeners.add(listener);
  }

}
