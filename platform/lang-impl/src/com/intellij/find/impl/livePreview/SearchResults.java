package com.intellij.find.impl.livePreview;


import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.find.FindResult;
import com.intellij.find.FindUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;

public class SearchResults {

  public enum Direction {UP, DOWN;}

  private int myActualFound = 0;

  private List<SearchResultsListener> myListeners = new ArrayList<SearchResultsListener>();

  private LiveOccurrence myCursor;

  private List<LiveOccurrence> myOccurrences = new ArrayList<LiveOccurrence>();

  private Set<LiveOccurrence> myExcluded = new HashSet<LiveOccurrence>();

  private Editor myEditor;
  private FindModel myFindModel;

  private int myMatchesLimit = 100;

  private boolean myNotFoundState = false;

  private boolean myDisposed = false;
  public SearchResults(Editor editor) {
    myEditor = editor;
  }

  public void setNotFoundState(boolean isForward) {
    myNotFoundState = true;
    FindModel findModel = new FindModel();
    findModel.copyFrom(myFindModel);
    findModel.setForward(isForward);
    FindUtil.processNotFound(myEditor, findModel.getStringToFind(), findModel, getProject());
  }

  public int getActualFound() {
    return myActualFound;
  }

  public boolean hasMatches() {
    return !getOccurrences().isEmpty();
  }

  public FindModel getFindModel() {
    return myFindModel;
  }

  public boolean isExcluded(LiveOccurrence occurrence) {
    return myExcluded.contains(occurrence);
  }

  public void exclude(LiveOccurrence occurrence) {
    if (myExcluded.contains(occurrence)) {
      myExcluded.remove(occurrence);
    } else {
      myExcluded.add(occurrence);
    }
    notifyChanged();
  }

  public Set<LiveOccurrence> getExcluded() {
    return myExcluded;
  }

  public interface SearchResultsListener {

    void searchResultsUpdated(SearchResults sr);
    void editorChanged(SearchResults sr, Editor oldEditor);
    void cursorMoved(boolean toChangeSelection);

  }
  public void addListener(SearchResultsListener srl) {
    myListeners.add(srl);
  }

  public void removeListener(SearchResultsListener srl) {
    myListeners.remove(srl);
  }

  public int getMatchesLimit() {
    return myMatchesLimit;
  }

  public void setMatchesLimit(int matchesLimit) {
    myMatchesLimit = matchesLimit;
  }

  public LiveOccurrence getCursor() {
    return myCursor;
  }

  public List<LiveOccurrence> getOccurrences() {
    return myOccurrences;
  }

  public Project getProject() {
    return myEditor.getProject();
  }

  public synchronized void setEditor(Editor editor) {
    Editor oldOne = myEditor;
    myEditor = editor;
    notifyEditorChanged(oldOne);
  }

  private void notifyEditorChanged(Editor oldOne) {
    for (SearchResultsListener listener : myListeners) {
      listener.editorChanged(this, oldOne);
    }
  }

  public synchronized Editor getEditor() {
    return myEditor;
  }

  private static void findResultsToOccurrences(ArrayList<FindResult> results, Collection<LiveOccurrence> occurrences) {
    for (FindResult r : results) {
      LiveOccurrence occurrence = new LiveOccurrence();
      occurrence.setPrimaryRange(r);
      occurrences.add(occurrence);
    }
  }

  public void clear() {
    searchCompleted(new ArrayList<LiveOccurrence>(), 0, getEditor(), null, false);
  }

  public void updateThreadSafe(final FindModel findModel, final boolean toChangeSelection) {
    if (myDisposed) return;
    final ArrayList<LiveOccurrence> occurrences = new ArrayList<LiveOccurrence>();
    final Editor editor = getEditor();

    final ArrayList<FindResult> results = new ArrayList<FindResult>();
    if (findModel != null) {

      ApplicationManager.getApplication().runReadAction(new Runnable() {
        @Override
        public void run() {
          TextRange selection = new TextRange(editor.getSelectionModel().getSelectionStart(),
                                          editor.getSelectionModel().getSelectionEnd());
          TextRange r = findModel.isGlobal() ? new TextRange(0, Integer.MAX_VALUE) : selection;
          if (r.getLength() == 0) {
            r = new TextRange(0, Integer.MAX_VALUE);
          }
          int offset = r.getStartOffset();
          VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(editor.getDocument());

          while (true) {
            FindManager findManager = FindManager.getInstance(editor.getProject());
            FindResult result = findManager.findString(editor.getDocument().getCharsSequence(), offset, findModel, virtualFile);
            if (!result.isStringFound()) break;
            int newOffset = result.getEndOffset();
            if (offset == newOffset || result.getEndOffset() > r.getEndOffset()) break;
            offset = newOffset;
            results.add(result);

            if (results.size() > myMatchesLimit) break;
          }
          if (results.size() < myMatchesLimit) {

            findResultsToOccurrences(results, occurrences);
          }

          final Runnable searchCompletedRunnable = new Runnable() {
            @Override
            public void run() {
              searchCompleted(occurrences, results.size(), editor, findModel, toChangeSelection);
            }
          };

          if (!ApplicationManager.getApplication().isUnitTestMode()) {
            ApplicationManager.getApplication().invokeLater(searchCompletedRunnable);
          } else {
            searchCompletedRunnable.run();
          }
        }
      });
    }
  }

  public void dispose() {
    myDisposed = true;
  }

  private void searchCompleted(List<LiveOccurrence> occurrences, int size, Editor editor, FindModel findModel, boolean toChangeSelection) {
    if (editor == getEditor() && !myDisposed) {
      myOccurrences = occurrences;
      final TextRange oldCursorRange = myCursor != null ? myCursor.getPrimaryRange() : null;
      Collections.sort(myOccurrences, new Comparator<LiveOccurrence>() {
        @Override
        public int compare(LiveOccurrence liveOccurrence, LiveOccurrence liveOccurrence1) {
          return liveOccurrence.getPrimaryRange().getStartOffset() - liveOccurrence1.getPrimaryRange().getStartOffset();
        }
      });

      myFindModel = findModel;
      updateCursor(oldCursorRange);
      myActualFound = size;
      notifyChanged();
      if (oldCursorRange == null || myCursor == null || !myCursor.getPrimaryRange().equals(oldCursorRange)) {
        notifyCursorMoved(toChangeSelection);
      }
    }
  }

  private void updateCursor(TextRange oldCursorRange) {
    if (!tryToRepairOldCursor(oldCursorRange)) {
      if (myFindModel != null) {
        if(oldCursorRange != null && !myFindModel.isGlobal()) {
          myCursor = firstOccurrenceAfterOffset(oldCursorRange.getEndOffset());
        } else {
          LiveOccurrence afterCaret = oldCursorRange == null ? firstOccurrenceAtOrAfterCaret() : firstOccurrenceAfterCaret();
          if (afterCaret != null) {
            myCursor = afterCaret;
          } else {
            myCursor = null;
          }
        }
      } else {
        myCursor = null;
      }
    }
  }

  @Nullable
  private LiveOccurrence firstOccurrenceAtOrAfterCaret() {
    int offset = getEditor().getCaretModel().getOffset();
    for (LiveOccurrence occurrence : myOccurrences) {
      if (offset <= occurrence.getPrimaryRange().getEndOffset() && offset >= occurrence.getPrimaryRange().getStartOffset()) {
        return occurrence;
      }
    }
    return firstOccurrenceAfterCaret();
  }

  private void notifyChanged() {
    for (SearchResultsListener listener : myListeners) {
      listener.searchResultsUpdated(this);
    }
  }

  static boolean insideVisibleArea(Editor e, TextRange r) {
    Rectangle visibleArea = e.getScrollingModel().getVisibleArea();
    Point point = e.logicalPositionToXY(e.offsetToLogicalPosition(r.getStartOffset()));

    return visibleArea.contains(point);
  }

  @Nullable
  private LiveOccurrence firstVisibleOccurrence() {
    int offset = Integer.MAX_VALUE;
    LiveOccurrence firstOccurrence = null;
    LiveOccurrence firstVisibleOccurrence = null;
    for (LiveOccurrence o : getOccurrences()) {
      if (insideVisibleArea(myEditor, o.getPrimaryRange())) {
        if (firstVisibleOccurrence == null || o.getPrimaryRange().getStartOffset() < firstVisibleOccurrence.getPrimaryRange().getStartOffset()) {
          firstVisibleOccurrence = o;
        }
      }
      if (o.getPrimaryRange().getStartOffset() < offset) {
        offset = o.getPrimaryRange().getStartOffset();
        firstOccurrence = o;
      }
    }
    return firstVisibleOccurrence != null ? firstVisibleOccurrence : firstOccurrence;
  }

  @Nullable
  private LiveOccurrence firstOccurrenceBeforeCaret() {
    int offset = getEditor().getCaretModel().getOffset();
    return firstOccurrenceBeforeOffset(offset);
  }

  private LiveOccurrence firstOccurrenceBeforeOffset(int offset) {
    for (int i = getOccurrences().size()-1; i >= 0; --i) {
      if (getOccurrences().get(i).getPrimaryRange().getEndOffset() < offset) {
        return getOccurrences().get(i);
      }
    }
    return null;
  }

  @Nullable
  private LiveOccurrence firstOccurrenceAfterCaret() {
    int caret = myEditor.getCaretModel().getOffset();
    return firstOccurrenceAfterOffset(caret);
  }

  private LiveOccurrence firstOccurrenceAfterOffset(int offset) {
    LiveOccurrence afterCaret = null;
    for (LiveOccurrence occurrence : getOccurrences()) {
      if (occurrence.getPrimaryRange().getStartOffset() >= offset) {
        if (afterCaret == null || occurrence.getPrimaryRange().getStartOffset() < afterCaret.getPrimaryRange().getStartOffset() ) {
          afterCaret = occurrence;
        }
      }
    }
    return afterCaret;
  }

  private boolean tryToRepairOldCursor(TextRange oldCursorRange) {
    if (oldCursorRange == null) return false;
    LiveOccurrence mayBeOldCursor = null;
    for (LiveOccurrence searchResult : getOccurrences()) {
      if (searchResult.getPrimaryRange().intersects(oldCursorRange)) {
        mayBeOldCursor = searchResult;
      }
      if (searchResult.getPrimaryRange().equals(oldCursorRange)) {
        break;
      }
    }
    if (mayBeOldCursor != null) {
      myCursor = mayBeOldCursor;
      return true;
    }
    return false;
  }

  @Nullable
  private LiveOccurrence prevOccurrence(LiveOccurrence o) {
    for (int i = 0; i < getOccurrences().size(); ++i) {
      if (getOccurrences().get(i).equals(o))  {
        if (i > 0) {
          return getOccurrences().get(i - 1);
        }
      }
    }
    return null;
  }

  @Nullable
  private LiveOccurrence nextOccurrence(LiveOccurrence o) {
    boolean found = false;
    for (LiveOccurrence occurrence : getOccurrences()) {
      if (found) {
        return occurrence;
      }
      if (occurrence.equals(o)) {
        found = true;
      }
    }
    return null;
  }

  public void prevOccurrence() {
    LiveOccurrence next = null;
    if (myFindModel == null) return;
    boolean processFromTheBeginning = false;
    if (myNotFoundState) {
      myNotFoundState = false;
      processFromTheBeginning = true;
    }
    if (!myFindModel.isGlobal()) {
      next = prevOccurrence(myCursor);
    } else {
      next = firstOccurrenceBeforeCaret();
    }
    if (next == null) {
      if (processFromTheBeginning) {
        if (hasMatches()) {
          next = getOccurrences().get(getOccurrences().size()-1);
        }
      } else {
        setNotFoundState(false);
      }
    }

    moveCursorTo(next);
  }

  public void nextOccurrence() {
    LiveOccurrence next = null;
    if (myFindModel == null) return;
    boolean processFromTheBeginning = false;
    if (myNotFoundState) {
      myNotFoundState = false;
      processFromTheBeginning = true;
    }
    if (!myFindModel.isGlobal()) {
      next = nextOccurrence(myCursor);
    } else {
      next = firstOccurrenceAfterCaret();
    }
    if (next == null) {
      if (processFromTheBeginning) {
        if (hasMatches()) {
          next = getOccurrences().get(0);
        }
      } else {
        setNotFoundState(true);
      }
    }

    moveCursorTo(next);
  }

  public void moveCursorTo(LiveOccurrence next) {
    if (next != null) {
      myCursor = next;
      notifyCursorMoved(true);
    }
  }

  private void notifyCursorMoved(boolean toChangeSelection) {
    for (SearchResultsListener listener : myListeners) {
      listener.cursorMoved(toChangeSelection);
    }
  }
}
