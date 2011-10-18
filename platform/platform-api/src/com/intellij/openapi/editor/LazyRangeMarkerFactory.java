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
package com.intellij.openapi.editor;

import com.intellij.codeStyle.CodeStyleFacade;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.WeakList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Date: Mar 17, 2009
 */
public class LazyRangeMarkerFactory extends AbstractProjectComponent {
  private final WeakList<LazyMarker> myMarkers = new WeakList<LazyMarker>();

  public LazyRangeMarkerFactory(@NotNull Project project, @NotNull final FileDocumentManager fileDocumentManager) {
    super(project);
    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(new DocumentAdapter() {
      public void beforeDocumentChange(DocumentEvent e) {
        List<LazyMarker> markers = myMarkers.toStrongList();
        List<LazyMarker> markersToRemove = new ArrayList<LazyMarker>();
        for (final LazyMarker marker : markers) {
          final VirtualFile docFile = fileDocumentManager.getFile(e.getDocument());
          if (marker.getFile() == docFile) {
            marker.ensureDelegate();
            markersToRemove.add(marker);
          }
        }
        myMarkers.removeAll(markersToRemove);
      }
    }, project);
  }

  public static LazyRangeMarkerFactory getInstance(Project project) {
    return project.getComponent(LazyRangeMarkerFactory.class);
  }

  @NotNull
  public RangeMarker createRangeMarker(@NotNull VirtualFile file, int offset) {
    FileDocumentManager fdm = FileDocumentManager.getInstance();
    final Document document = fdm.getCachedDocument(file);
    if (document != null) {
      int _offset = Math.min(offset, document.getTextLength());
      return document.createRangeMarker(_offset, _offset);
    }

    final LazyMarker marker = new OffsetLazyMarker(file, offset);
    myMarkers.add(marker);
    return marker;
  }

  @NotNull
  public RangeMarker createRangeMarker(@NotNull VirtualFile file, int line, int column, boolean persistent) {
    FileDocumentManager fdm = FileDocumentManager.getInstance();
    final Document document = fdm.getCachedDocument(file);
    if (document != null) {
      final int offset = calculateOffset(myProject, file, document, line, column);
      return document.createRangeMarker(offset, offset, persistent);
    }

    final LazyMarker marker = new LineColumnLazyMarker(file, line, column);
    myMarkers.add(marker);
    return marker;
  }

  private abstract static class LazyMarker extends UserDataHolderBase implements RangeMarker{
    private RangeMarker myDelegate = null;
    private final VirtualFile myFile;
    protected final int myInitialOffset;

    private LazyMarker(@NotNull VirtualFile file, int offset) {
      myFile = file;
      myInitialOffset = offset;
    }

    @NotNull
    public VirtualFile getFile() {
      return myFile;
    }

    @NotNull
    public final RangeMarker ensureDelegate() {
      if (myDelegate == null) {
        Document document = FileDocumentManager.getInstance().getDocument(myFile);
        myDelegate = createDelegate(myFile, document);
      }
      return myDelegate;
    }

    @NotNull
    protected abstract RangeMarker createDelegate(@NotNull VirtualFile file, @NotNull Document document);

    @NotNull
    public Document getDocument() {
      return ensureDelegate().getDocument();
    }

    public int getStartOffset() {
      return myDelegate != null ? myDelegate.getStartOffset() : myInitialOffset;
    }


    public int getEndOffset() {
      return myDelegate != null ? myDelegate.getEndOffset() : myInitialOffset;
    }

    public boolean isValid() {
      return myDelegate != null ? myDelegate.isValid() : myFile.isValid();
    }

    public void setGreedyToLeft(boolean greedy) {
      ensureDelegate().setGreedyToLeft(greedy);
    }

    public void setGreedyToRight(boolean greedy) {
      ensureDelegate().setGreedyToRight(greedy);
    }

    public boolean isGreedyToRight() {
      return ensureDelegate().isGreedyToRight();
    }

    public boolean isGreedyToLeft() {
      return ensureDelegate().isGreedyToLeft();
    }

    @Override
    public void dispose() {
      ensureDelegate().dispose();
    }
  }

  private static class OffsetLazyMarker extends LazyMarker {
    private OffsetLazyMarker(@NotNull VirtualFile file, int offset) {
      super(file, offset);
    }

    @NotNull
    public RangeMarker createDelegate(@NotNull VirtualFile file, @NotNull final Document document) {
      final int offset = Math.min(myInitialOffset, document.getTextLength());
      return document.createRangeMarker(offset, offset);
    }
  }

  private class LineColumnLazyMarker extends LazyMarker {
    private final int myLine;
    private final int myColumn;

    private LineColumnLazyMarker(@NotNull VirtualFile file, int line, int column) {
      super(file, -1);
      myLine = line;
      myColumn = column;
    }

    @NotNull
    public RangeMarker createDelegate(@NotNull VirtualFile file, @NotNull final Document document) {
      int offset = calculateOffset(myProject, file, document, myLine, myColumn);

      return document.createRangeMarker(offset, offset);
    }
  }

  private static int calculateOffset(@NotNull Project project, @NotNull VirtualFile file, @NotNull Document document, final int line, final int column) {
    int offset;
    if (line < document.getLineCount()) {
      final int lineStart = document.getLineStartOffset(line);
      final int lineEnd = document.getLineEndOffset(line);
      final CharSequence docText = document.getCharsSequence();
      final int tabSize = CodeStyleFacade.getInstance(project).getTabSize(FileTypeManager.getInstance().getFileTypeByFile(file));

      offset = lineStart;
      int col = 0;
      while (offset < lineEnd && col < column) {
        col += docText.charAt(offset) == '\t' ? tabSize : 1;
        offset++;
      }
    }
    else {
      offset = document.getTextLength();
    }
    return offset;
  }

}
