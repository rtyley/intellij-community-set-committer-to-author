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
package org.jetbrains.idea.svn.annotate;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.annotate.AnnotationProvider;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.svn.*;
import org.jetbrains.idea.svn.history.SvnFileRevision;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.wc.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Date;

public class SvnAnnotationProvider implements AnnotationProvider {
  private final SvnVcs myVcs;

  public SvnAnnotationProvider(final SvnVcs vcs) {
    myVcs = vcs;
  }

  public FileAnnotation annotate(final VirtualFile file) throws VcsException {
    return annotate(file, new SvnFileRevision(myVcs, SVNRevision.WORKING, SVNRevision.WORKING, null, null, null, null, null), true);
  }

  public FileAnnotation annotate(final VirtualFile file, final VcsFileRevision revision) throws VcsException {
    return annotate(file, revision, false);
  }

  private FileAnnotation annotate(final VirtualFile file, final VcsFileRevision revision, final boolean loadExternally) throws VcsException {
    if (file.isDirectory()) {
      throw new VcsException(SvnBundle.message("exception.text.cannot.annotate.directory"));
    }
    final FileAnnotation[] annotation = new FileAnnotation[1];
    final VcsException[] exception = new VcsException[1];

    Runnable command = new Runnable() {
      public void run() {
        final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
        try {
          final File ioFile = new File(file.getPath()).getAbsoluteFile();

          final String contents;
          if (loadExternally) {
            final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            myVcs.createWCClient().doGetFileContents(ioFile, SVNRevision.UNDEFINED, SVNRevision.BASE, true, buffer);
            contents = LoadTextUtil.getTextByBinaryPresentation(buffer.toByteArray(), file, false).toString();
          } else {
            revision.loadContent();
            contents = LoadTextUtil.getTextByBinaryPresentation(revision.getContent(), file, false).toString();
          }

          final SvnFileAnnotation result = new SvnFileAnnotation(myVcs, file, contents);

          SVNWCClient wcClient = myVcs.createWCClient();
          SVNInfo info = wcClient.doInfo(ioFile, SVNRevision.WORKING);
          if (info == null) {
              exception[0] = new VcsException(new SVNException(SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "File ''{0}'' is not under version control", ioFile)));
              return;
          }
          final String url = info.getURL() == null ? null : info.getURL().toString();

          SVNLogClient client = myVcs.createLogClient();
          setLogClientOptions(client);
          SVNRevision endRevision = ((SvnFileRevision) revision).getRevision();
          if (SVNRevision.WORKING.equals(endRevision)) {
            endRevision = info.getRevision();
          }
          if (progress != null) {
            progress.setText(SvnBundle.message("progress.text.computing.annotation", file.getName()));
          }

          // ignore mime type=true : IDEA-19562
          final ISVNAnnotateHandler annotateHandler = new ISVNAnnotateHandler() {
            public void handleLine(Date date, long revision, String author, String line) {
              if (progress != null) {
                progress.checkCanceled();
              }
              result.appendLineInfo(date, revision, author);
            }

            public void handleLine(final Date date,
                                   final long revision,
                                   final String author,
                                   final String line,
                                   final Date mergedDate,
                                   final long mergedRevision,
                                   final String mergedAuthor,
                                   final String mergedPath,
                                   final int lineNumber) throws SVNException {
              if (progress != null) {
                progress.checkCanceled();
              }
              if ((mergedDate != null) && (revision > mergedRevision)) {
                // !!! merged date = date of merge, i.e. date -> date of original change etc.
                result.appendLineInfo(date, revision, author, mergedDate, mergedRevision, mergedAuthor);
              }
              else {
                result.appendLineInfo(date, revision, author);
              }
            }

            public boolean handleRevision(final Date date, final long revision, final String author, final File contents)
              throws SVNException {
              if (progress != null) {
                progress.checkCanceled();
              }
              // todo check that false is ok
              return false;
            }

            public void handleEOF() {

            }
          };

          final boolean supportsMergeinfo = SvnUtil.checkRepositoryVersion15(myVcs, url);
          final SVNRevision svnRevision = ((SvnRevisionNumber)revision.getRevisionNumber()).getRevision();
          client.doAnnotate(ioFile, svnRevision, SVNRevision.create(0), endRevision, true, supportsMergeinfo, annotateHandler, null);

          client.doLog(new File[]{ioFile}, endRevision, SVNRevision.create(1), SVNRevision.UNDEFINED, false, false, supportsMergeinfo, 0, null,
                       new ISVNLogEntryHandler() {
                         public void handleLogEntry(SVNLogEntry logEntry) {
                           if (progress != null) {
                             progress.checkCanceled();
                             progress.setText2(SvnBundle.message("progress.text2.revision.processed", logEntry.getRevision()));
                           }
                           result.setRevision(logEntry.getRevision(), new SvnFileRevision(myVcs, SVNRevision.UNDEFINED, logEntry, url, ""));
                         }
                       });

          annotation[0] = result;
        }
        catch (SVNException e) {
          exception[0] = new VcsException(e);
        } catch (IOException e) {
          exception[0] = new VcsException(e);
        } catch (VcsException e) {
          exception[0] = e;
        }
      }
    };
    if (ApplicationManager.getApplication().isDispatchThread()) {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(command, SvnBundle.message("action.text.annotate"), false, myVcs.getProject());
    }
    else {
      command.run();
    }
    if (exception[0] != null) {
      throw new VcsException(exception[0]);
    }
    return annotation[0];
  }

  public boolean isAnnotationValid( VcsFileRevision rev ){
    return true;
  }

  private void setLogClientOptions(final SVNLogClient client) {
    if (SvnConfiguration.getInstance(myVcs.getProject()).IGNORE_SPACES_IN_ANNOTATE) {
      client.setDiffOptions(new SVNDiffOptions(true, true, true));
    }
  }
}
