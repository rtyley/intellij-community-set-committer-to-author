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
package com.intellij.openapi.fileEditor.impl;

import com.intellij.AppTopics;
import com.intellij.codeStyle.CodeStyleFacade;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffManager;
import com.intellij.openapi.diff.DocumentContent;
import com.intellij.openapi.diff.SimpleContent;
import com.intellij.openapi.diff.SimpleDiffRequest;
import com.intellij.openapi.diff.ex.DiffPanelOptions;
import com.intellij.openapi.diff.impl.DiffPanelImpl;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.DocumentRunnable;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileTypes.BinaryFileTypeDecompilers;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.dummy.DummyFileSystem;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.psi.ExternalChangeAction;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.UIBundle;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.ConcurrentHashSet;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.io.Writer;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.*;

public class FileDocumentManagerImpl extends FileDocumentManager implements ApplicationComponent, VirtualFileListener, SafeWriteRequestor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl");

  private static final Key<String> LINE_SEPARATOR_KEY = Key.create("LINE_SEPARATOR_KEY");
  public static final Key<Reference<Document>> DOCUMENT_KEY = Key.create("DOCUMENT_KEY");
  private static final Key<VirtualFile> FILE_KEY = Key.create("FILE_KEY");

  private final Set<Document> myUnsavedDocuments = new ConcurrentHashSet<Document>();

  private final EventDispatcher<FileDocumentSynchronizationVetoListener> myVetoDispatcher = EventDispatcher.create(FileDocumentSynchronizationVetoListener.class);

  private final VirtualFileManager myVirtualFileManager;
  private final MessageBus myBus;

  private static final Object lock = new Object();

  public FileDocumentManagerImpl(VirtualFileManager virtualFileManager) {
    myVirtualFileManager = virtualFileManager;

    myVirtualFileManager.addVirtualFileListener(this);

    myBus = ApplicationManager.getApplication().getMessageBus();
  }

  @NotNull
  public String getComponentName() {
    return "FileDocumentManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  @Nullable
  public Document getDocument(@NotNull final VirtualFile file) {
    DocumentEx document = (DocumentEx)getCachedDocument(file);
    if (document == null) {
      if (file.isDirectory() || isBinaryWithoutDecompiler(file)) return null;
      final CharSequence text = LoadTextUtil.loadText(file);

      synchronized (lock) {
        document = (DocumentEx)getCachedDocument(file);
        if (document != null) return document; // Double checking

        document = (DocumentEx)createDocument(text);
        document.setModificationStamp(file.getModificationStamp());
        final FileType fileType = file.getFileType();
        document.setReadOnly(!file.isWritable() || fileType.isBinary());
        file.putUserData(DOCUMENT_KEY, new WeakReference<Document>(document));
        document.putUserData(FILE_KEY, file);

        if (!(file instanceof LightVirtualFile || file.getFileSystem() instanceof DummyFileSystem)) {
          document.addDocumentListener(
            new DocumentAdapter() {
              public void documentChanged(DocumentEvent e) {
                final Document document = e.getDocument();
                myUnsavedDocuments.add(document);
                final Runnable currentCommand = CommandProcessor.getInstance().getCurrentCommand();
                Project project = currentCommand == null ? null : CommandProcessor.getInstance().getCurrentCommandProject();
                String lineSeparator = CodeStyleFacade.getInstance(project).getLineSeparator();
                document.putUserData(LINE_SEPARATOR_KEY, lineSeparator);

                // avoid documents piling up during batch processing
                if (areTooManyDocumentsInTheQueue(myUnsavedDocuments)) {
                  saveAllDocumentsLater();
                }
              }
            }
          );
        }
      }

      fireFileContentLoaded(file, document);
    }

    return document;
  }

  public static boolean areTooManyDocumentsInTheQueue(Collection<Document> documents) {
    if (documents.size() > 100) return true;
    int totalSize = 0;
    for (Document document : documents) {
      totalSize += document.getTextLength();
      if (totalSize > 10 * FileUtil.MEGABYTE) return true;
    }
    return false;
  }

  private static Document createDocument(final CharSequence text) {
    return EditorFactory.getInstance().createDocument(text);
  }

  @Nullable
  public Document getCachedDocument(@NotNull VirtualFile file) {
    Reference<Document> reference = file.getUserData(DOCUMENT_KEY);
    Document document = reference != null ? reference.get() : null;

    if (document != null && isBinaryWithoutDecompiler(file)) {
      file.putUserData(DOCUMENT_KEY, null);
      document.putUserData(FILE_KEY, null);
      return null;
    }

    return document;
  }

  public static void registerDocument(final Document document, VirtualFile virtualFile) {
    synchronized (lock) {
      virtualFile.putUserData(DOCUMENT_KEY, new SoftReference<Document>(document) {
        public Document get() {
          return document;
        }
      });
      document.putUserData(FILE_KEY, virtualFile);
    }
  }

  public VirtualFile getFile(@NotNull Document document) {
    return document.getUserData(FILE_KEY);
  }

  @TestOnly
  public void dropAllUnsavedDocuments() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      throw new RuntimeException("This method is only for test mode!");
    }
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    if (!myUnsavedDocuments.isEmpty()) {
      myUnsavedDocuments.clear();
      fireUnsavedDocumentsDropped();
    }
  }

  private void saveAllDocumentsLater() {
    // later because some document might have been blocked by PSI right now
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        if (!ApplicationManager.getApplication().isDisposed()) {
          saveAllDocuments();
        }
      }
    });
  }

  public void saveAllDocuments() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    myBus.syncPublisher(AppTopics.FILE_DOCUMENT_SYNC).beforeAllDocumentsSaving();
    for (FileDocumentManagerListener listener : getListeners()) {
      listener.beforeAllDocumentsSaving();
    }

    if (myUnsavedDocuments.isEmpty()) return;

    Set<Document> failedToSave = new THashSet<Document>();
    while (true) {
      final Document[] unsavedDocuments = getUnsavedDocuments();

      int count = 0;
      for (Document document : unsavedDocuments) {
        if (failedToSave.contains(document)) continue;
        saveDocument(document);
        count++;
        if (myUnsavedDocuments.contains(document)) {
          failedToSave.add(document);
        }
      }

      if (count == 0) break;
    }
  }

  public void saveDocument(@NotNull final Document document) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (!myUnsavedDocuments.contains(document)) return;

    ApplicationManager.getApplication().runWriteAction(new DocumentRunnable(document, null) {
      public void run() {
        _saveDocument(document);
      }
    });
  }

  @Override
  public void saveDocumentAsIs(@NotNull Document document) {
    EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();

    String trailer = editorSettings.getStripTrailingSpaces();
    boolean ensureEOLonEOF = editorSettings.isEnsureNewLineAtEOF();
    editorSettings.setStripTrailingSpaces(EditorSettingsExternalizable.STRIP_TRAILING_SPACES_NONE);
    editorSettings.setEnsureNewLineAtEOF(false);
    try {
      saveDocument(document);
    }
    finally {
      editorSettings.setStripTrailingSpaces(trailer);
      editorSettings.setEnsureNewLineAtEOF(ensureEOLonEOF);
    }
  }

  private void _saveDocument(@NotNull final Document document) {
    boolean committed = false;
    try {
      VirtualFile file = getFile(document);

      if (file == null || !file.isValid() || file instanceof LightVirtualFile || !isFileModified(file)) {
        myUnsavedDocuments.remove(document);
        fireUnsavedDocumentsDropped();
        LOG.assertTrue(!myUnsavedDocuments.contains(document));
        return;
      }

      if (needsRefresh(file)) {
        file.refresh(false, false);
        if (!myUnsavedDocuments.contains(document)) return;
        if (!file.isValid()) return;
      }

      try {
        for (FileDocumentSynchronizationVetoListener listener : myVetoDispatcher.getListeners()) {
          listener.beforeDocumentSaving(document);
        }
      }
      catch (VetoDocumentSavingException e) {
        return;
      }

      try {
        myBus.syncPublisher(AppTopics.FILE_DOCUMENT_SYNC).beforeDocumentSaving(document);
      }
      catch (Exception e) {
        LOG.error(e);
      }

      // Allows pre-save document modification, e.g. stripping trailing spaces.
      for (FileDocumentManagerListener listener : getListeners()) {
        listener.beforeDocumentSaving(document);
      }

      LOG.assertTrue(file.isValid());

      String text = document.getText();
      String lineSeparator = getLineSeparator(document, file);
      if (!lineSeparator.equals("\n")) {
        text = StringUtil.convertLineSeparators(text, lineSeparator);
      }
      Project project = ProjectLocator.getInstance().guessProjectForFile(file);

      Writer writer = null;
      try {
        writer = LoadTextUtil.getWriter(project, file, this, text, document.getModificationStamp());
        writer.write(text);
      }
      finally {
        if (writer != null) {
          writer.close();
        }
      }
      committed = true;
    }
    catch (IOException e) {
      reportErrorOnSave(e);
      committed = false;
    }
    finally {
      if (committed) {
        myUnsavedDocuments.remove(document);
        LOG.assertTrue(!myUnsavedDocuments.contains(document));
        ((DocumentEx)document).clearLineModificationFlags();
      }
    }
  }

  private static boolean needsRefresh(final VirtualFile file) {
    final VirtualFileSystem fs = file.getFileSystem();
    return fs instanceof NewVirtualFileSystem && file.getTimeStamp() != ((NewVirtualFileSystem)fs).getTimeStamp(file);
  }

  private static String getLineSeparator(Document document, VirtualFile file) {
    String lineSeparator = file.getUserData(LoadTextUtil.DETECTED_LINE_SEPARATOR_KEY);
    if (lineSeparator == null) {
      lineSeparator = document.getUserData(LINE_SEPARATOR_KEY);
    }
    return lineSeparator;
  }

  @NotNull
  public String getLineSeparator(@Nullable VirtualFile file, @Nullable Project project) {
    String lineSeparator = file != null ? file.getUserData(LoadTextUtil.DETECTED_LINE_SEPARATOR_KEY) : null;
    if (lineSeparator == null) {
      CodeStyleFacade settingsManager = project == null
                                        ? CodeStyleFacade.getInstance()
                                        : CodeStyleFacade.getInstance(project);
      return settingsManager.getLineSeparator();
    }
    else {
      return lineSeparator;
    }
  }

  @Override
  public boolean requestWriting(@NotNull Document document, Project project) {
    final VirtualFile file = getInstance().getFile(document);
    if (project != null && file != null && file.isValid()) {
      return ReadonlyStatusHandler.ensureFilesWritable(project, file);
    }
    if (document.isWritable()) {
      return true;
    }
    document.fireReadOnlyModificationAttempt();
    return false;
  }

  public void reloadFiles(final VirtualFile... files) {
    for (VirtualFile file : files) {
      if (file.exists()) {
        final Document doc = getCachedDocument(file);
        if (doc != null) {
          reloadFromDisk(doc);
        }
      }
    }
  }

  @NotNull
  public Document[] getUnsavedDocuments() {
    if (myUnsavedDocuments.isEmpty()) {
      return Document.EMPTY_ARRAY;
    }

    List<Document> list = new ArrayList<Document>(myUnsavedDocuments);
    return list.toArray(new Document[list.size()]);
  }

  public boolean isDocumentUnsaved(@NotNull Document document) {
    return myUnsavedDocuments.contains(document);
  }

  public boolean isFileModified(@NotNull VirtualFile file) {
    final Document doc = getCachedDocument(file);
    return doc != null && isDocumentUnsaved(doc) && doc.getModificationStamp() != file.getModificationStamp();
  }

  public void addFileDocumentSynchronizationVetoer(@NotNull FileDocumentSynchronizationVetoListener vetoer) {
    myVetoDispatcher.addListener(vetoer);
  }

  public void removeFileDocumentSynchronizationVetoer(@NotNull FileDocumentSynchronizationVetoListener vetoer) {
    myVetoDispatcher.removeListener(vetoer);
  }

  private final Map<FileDocumentManagerListener, MessageBusConnection> myAdapters
    = new HashMap<FileDocumentManagerListener, MessageBusConnection>();

  public void propertyChanged(final VirtualFilePropertyEvent event) {
    if (VirtualFile.PROP_WRITABLE.equals(event.getPropertyName())) {
      final VirtualFile file = event.getFile();
      final Document document = getCachedDocument(file);
      if (document == null) return;

      ApplicationManager.getApplication().runWriteAction(
        new ExternalChangeAction() {
          public void run() {
            document.setReadOnly(!event.getFile().isWritable());
          }
        }
      );
      //myUnsavedDocuments.remove(document); //?
    }
  }

  private static boolean isBinaryWithDecompiler(VirtualFile file) {
    final FileType ft = file.getFileType();
    return ft.isBinary() && BinaryFileTypeDecompilers.INSTANCE.forFileType(ft) != null;
  }

  private static boolean isBinaryWithoutDecompiler(VirtualFile file) {
    final FileType ft = file.getFileType();
    return ft.isBinary() && BinaryFileTypeDecompilers.INSTANCE.forFileType(ft) == null;
  }

  public void contentsChanged(VirtualFileEvent event) {
    if (event.isFromSave()) return;
    final VirtualFile file = event.getFile();
    final Document document = getCachedDocument(file);
    if (document == null) {
      fireFileWithNoDocumentChanged(file);
      return;
    }

    if (isBinaryWithDecompiler(file)) {
      fireFileWithNoDocumentChanged(file); // This will generate PSI event at FileManagerImpl
    }

    long documentStamp = document.getModificationStamp();
    long oldFileStamp = event.getOldModificationStamp();
    if (documentStamp != oldFileStamp) {
      LOG.info("reaload from disk?");
      LOG.info("  documentStamp:" + documentStamp);
      LOG.info("  oldFileStamp:" + oldFileStamp);

      Runnable askReloadRunnable = new Runnable() {
        public void run() {
          if (!file.isValid()) return;
          if (askReloadFromDisk(file, document)) {
            reloadFromDisk(document);
          }
        }
      };

      askReloadRunnable.run();
    }
    else {
      reloadFromDisk(document);
    }
  }

  private void fireFileWithNoDocumentChanged(final VirtualFile file) {
    myBus.syncPublisher(AppTopics.FILE_DOCUMENT_SYNC).fileWithNoDocumentChanged(file);
    for (FileDocumentManagerListener listener : getListeners()) {
      listener.fileWithNoDocumentChanged(file);
    }
  }

  public void reloadFromDisk(@NotNull final Document document) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    final VirtualFile file = getFile(document);
    try {
      fireBeforeFileContentReload(file, document);
    }
    catch (VetoDocumentReloadException e) {
      return;
    }
    catch (Exception e) {
      LOG.error(e);
    }

    final Project project = ProjectLocator.getInstance().guessProjectForFile(file);
    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(
          new ExternalChangeAction.ExternalDocumentChange(document, project) {
            public void run() {
              boolean wasWritable = document.isWritable();
              DocumentEx documentEx = (DocumentEx)document;
              documentEx.setReadOnly(false);
              documentEx.replaceText(LoadTextUtil.loadText(file), file.getModificationStamp());
              documentEx.setReadOnly(!wasWritable);
            }
          }
        );
      }
    }, UIBundle.message("file.cache.conflict.action"), null);

    myUnsavedDocuments.remove(document);

    try {
      fireFileContentReloaded(file, document);
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  protected boolean askReloadFromDisk(final VirtualFile file, final Document document) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (!isDocumentUnsaved(document)) return true;

    String message = UIBundle.message("file.cache.conflict.message.text", file.getPresentableUrl());
    if (ApplicationManager.getApplication().isUnitTestMode()) throw new RuntimeException(message);
    final DialogBuilder builder = new DialogBuilder((Project)null);
    builder.setCenterPanel(new JLabel(message, Messages.getQuestionIcon(), SwingConstants.CENTER));
    builder.addOkAction().setText(UIBundle.message("file.cache.conflict.load.fs.changes.button"));
    builder.addCancelAction().setText(UIBundle.message("file.cache.conflict.keep.memory.changes.button"));
    builder.addAction(new AbstractAction(UIBundle.message("file.cache.conflict.show.difference.button")) {
      public void actionPerformed(ActionEvent e) {
        String windowtitle = UIBundle.message("file.cache.conflict.for.file.dialog.title", file.getPresentableUrl());
        final ProjectEx project = (ProjectEx)ProjectLocator.getInstance().guessProjectForFile(file);

        SimpleDiffRequest request = new SimpleDiffRequest(project, windowtitle);
        FileType fileType = file.getFileType();
        String fsContent = LoadTextUtil.loadText(file).toString();
        request.setContents(new SimpleContent(fsContent, fileType),
                            new DocumentContent(project, document, fileType));
        request.setContentTitles(UIBundle.message("file.cache.conflict.diff.content.file.system.content"),
                                 UIBundle.message("file.cache.conflict.diff.content.memory.content"));
        DialogBuilder diffBuilder = new DialogBuilder(project);
        DiffPanelImpl diffPanel = (DiffPanelImpl)DiffManager.getInstance().createDiffPanel(diffBuilder.getWindow(), project);
        diffPanel.getOptions().setShowSourcePolicy(DiffPanelOptions.ShowSourcePolicy.DONT_SHOW);
        diffBuilder.setCenterPanel(diffPanel.getComponent());
        diffPanel.setDiffRequest(request);
        diffBuilder.addOkAction().setText(UIBundle.message("file.cache.conflict.save.changes.button"));
        diffBuilder.addCancelAction();
        diffBuilder.setTitle(windowtitle);
        diffBuilder.addDisposable(diffPanel);
        if (diffBuilder.show() == DialogWrapper.OK_EXIT_CODE) {
          builder.getDialogWrapper().close(DialogWrapper.CANCEL_EXIT_CODE);
        }
      }
    });
    builder.setTitle(UIBundle.message("file.cache.conflict.dialog.title"));
    builder.setButtonsAlignment(SwingConstants.CENTER);
    builder.setHelpId("reference.dialogs.fileCacheConflict");
    return builder.show() == 0;
  }

  protected void reportErrorOnSave(final IOException e) {
    // invokeLater here prevents attempt to show dialog in write action
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        Messages.showMessageDialog(
          UIBundle.message("cannot.save.file.with.error.error.message", e.getMessage()),
          UIBundle.message("cannot.save.file.dialog.title"),
          Messages.getErrorIcon()
        );
      }
    });
  }

  public void fileCreated(VirtualFileEvent event) {
  }

  public void fileDeleted(VirtualFileEvent event) {
    //todo clear document/file correspondence?
  }

  public void fileMoved(VirtualFileMoveEvent event) {
  }

  public void fileCopied(VirtualFileCopyEvent event) {
    fileCreated(event);
  }

  public void beforePropertyChange(VirtualFilePropertyEvent event) {
  }

  public void beforeContentsChange(VirtualFileEvent event) {
  }

  public void beforeFileDeletion(VirtualFileEvent event) {
  }

  public void beforeFileMovement(VirtualFileMoveEvent event) {
  }

  private void fireFileContentReloaded(final VirtualFile file, final Document document) {
    myBus.syncPublisher(AppTopics.FILE_DOCUMENT_SYNC).fileContentReloaded(file, document);
    for (FileDocumentManagerListener listener : getListeners()) {
      listener.fileContentReloaded(file, document);
    }
  }

  private void fireUnsavedDocumentsDropped() {
    myBus.syncPublisher(AppTopics.FILE_DOCUMENT_SYNC).unsavedDocumentsDropped();
    for (FileDocumentManagerListener listener : getListeners()) {
      listener.unsavedDocumentsDropped();
    }
  }

  private void fireBeforeFileContentReload(final VirtualFile file, final Document document) throws VetoDocumentReloadException {
    List<FileDocumentSynchronizationVetoListener> listeners = myVetoDispatcher.getListeners();
    for (FileDocumentSynchronizationVetoListener listener : listeners) {
      try {
        listener.beforeFileContentReload(file, document);
      }
      catch (AbstractMethodError e) {
        // Do nothing. Some listener just does not implement this method yet.
      }
    }

    myBus.syncPublisher(AppTopics.FILE_DOCUMENT_SYNC).beforeFileContentReload(file, document);
    for (FileDocumentManagerListener listener : getListeners()) {
      listener.beforeFileContentReload(file, document);
    }
  }

  private void fireFileContentLoaded(final VirtualFile file, final DocumentEx document) {
    myBus.syncPublisher(AppTopics.FILE_DOCUMENT_SYNC).fileContentLoaded(file, document);
    for (FileDocumentManagerListener listener : getListeners()) {
      listener.fileContentLoaded(file, document);
    }
  }

  @NotNull
  protected FileDocumentManagerListener[] getListeners() {
    return FileDocumentManagerListener.EP_NAME.getExtensions();
  }
}
