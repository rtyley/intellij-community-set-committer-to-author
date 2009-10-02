/*
 * @author max
 */
package com.intellij.ide.bookmarks.actions;

import com.intellij.ide.bookmarks.Bookmark;
import com.intellij.ide.bookmarks.BookmarkManager;
import com.intellij.ide.bookmarks.EditorBookmarksDialog;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.LightColors;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;

// TODO: remove duplication with BaseShowRecentFilesAction, there's quite a bit of it

public class BookmarksAction extends AnAction implements DumbAware {
  private static final Color BORDER_COLOR = new Color(0x87, 0x87, 0x87);

  @Override
  public void update(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    e.getPresentation().setEnabled(project != null &&
                                   (ToolWindowManager.getInstance(project).isEditorComponentActive() &&
                                    PlatformDataKeys.EDITOR.getData(dataContext) != null ||
                                    PlatformDataKeys.VIRTUAL_FILE.getData(dataContext) != null));
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) return;


    BookmarkInContextInfo bookmarkInContextInfo = new BookmarkInContextInfo(dataContext, project).invoke();
    VirtualFile file = bookmarkInContextInfo.getFile();
    Bookmark bookmarkAtPlace = bookmarkInContextInfo.getBookmarkAtPlace();
    int line = bookmarkInContextInfo.getLine();

    if (file == null) return;

    final DefaultListModel model = buildModel(project, bookmarkAtPlace, file, line);


    final JLabel pathLabel = new JLabel(" ");
    pathLabel.setHorizontalAlignment(SwingConstants.RIGHT);

    final Font font = pathLabel.getFont();
    pathLabel.setFont(font.deriveFont((float)10));

    final JList list = new JList(model);
    list.addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_DELETE) {
          int index = list.getSelectedIndex();
          if (index == -1 || index >= list.getModel().getSize()) {
            return;
          }
          Object[] values = list.getSelectedValues();
          for (Object value : values) {
            if (value instanceof BookmarkItem) {
              BookmarkItem item = (BookmarkItem)value;
              model.removeElement(item);
              if (model.getSize() > 0) {
                if (model.getSize() == index) {
                  list.setSelectedIndex(model.getSize() - 1);
                }
                else if (model.getSize() > index) {
                  list.setSelectedIndex(index);
                }
              }
              else {
                list.clearSelection();
              }
              BookmarkManager.getInstance(project).removeBookmark(item.myBookmark);
            }
          }
        }
      }
    });

    final PreviewPanel previewPanel = new PreviewPanel(project);

    list.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      private String getTitle2Text(String fullText) {
        int labelWidth = pathLabel.getWidth();
        if (fullText == null || fullText.length() == 0) return " ";
        while (pathLabel.getFontMetrics(pathLabel.getFont()).stringWidth(fullText) > labelWidth) {
          int sep = fullText.indexOf(File.separatorChar, 4);
          if (sep < 0) return fullText;
          fullText = "..." + fullText.substring(sep);
        }

        return fullText;
      }

      public void valueChanged(final ListSelectionEvent e) {
        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            updatePathLabel();
          }
        });
      }

      private void updatePathLabel() {
        final Object[] values = list.getSelectedValues();
        if (values != null && values.length == 1) {
          ItemWrapper wrapper = (ItemWrapper)values[0];
          pathLabel.setText(getTitle2Text(wrapper.footerText()));
          previewPanel.updateWithItem(wrapper);
        }
        else {
          previewPanel.updateWithItem(null);
          pathLabel.setText(" ");
        }
      }
    });

    Runnable runnable = new Runnable() {
      public void run() {
        IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(new Runnable() {
          public void run() {
            Object[] values = list.getSelectedValues();

            if (values.length == 1) {
              ((ItemWrapper)values[0]).execute(project);
            }
            else {
              for (Object value : values) {
                if (value instanceof BookmarkItem) {
                  ((BookmarkItem)value).execute(project);
                }
              }
            }
          }
        });
      }
    };

    if (list.getModel().getSize() == 0) {
      list.clearSelection();
    }

    new ListSpeedSearch(list) {
      @Override
      protected String getElementText(Object element) {
        return ((ItemWrapper)element).speedSearchText();
      }
    };

    list.setCellRenderer(new ItemRenderer(project));

    JPanel footerPanel = new JPanel(new BorderLayout()) {
      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.setColor(BORDER_COLOR);
        g.drawLine(0, 0, getWidth(), 0);
      }
    };

    footerPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
    footerPanel.add(pathLabel);

    new PopupChooserBuilder(list).
      setTitle("Bookmarks").
      setMovable(true).
      setSouthComponent(footerPanel).
      setEastComponent(previewPanel).
      setItemChoosenCallback(runnable).
      createPopup().showCenteredInCurrentWindow(project);
  }

  private static DefaultListModel buildModel(Project project, Bookmark bookmarkAtPlace, VirtualFile file, int line) {
    final DefaultListModel model = new DefaultListModel();

    for (Bookmark bookmark : BookmarkManager.getInstance(project).getValidBookmarks()) {
      model.insertElementAt(new BookmarkItem(bookmark), 0);
    }

    if (bookmarkAtPlace != null) {
      model.insertElementAt(new RemoveBookmarkItem(bookmarkAtPlace), 0);
    }
    else {
      model.insertElementAt(new SetBookmarkItem(file, line), 0);
    }

    model.addElement(new ManageBookmarksItem(bookmarkAtPlace));
    return model;
  }

  private static void selectBookmark(Bookmark bookmark, JList list) {
    ListModel model = list.getModel();
    for (int i = 0; i < model.getSize(); i++) {
      Object elt = model.getElementAt(i);
      if (elt instanceof BookmarkItem && ((BookmarkItem)elt).myBookmark == bookmark) {
        list.getSelectionModel().setSelectionInterval(i, i);
        break;
      }
    }
  }

  private interface ItemWrapper {
    void setupRenderer(ColoredListCellRenderer renderer, Project project, boolean selected);

    void execute(Project project);

    String speedSearchText();

    @Nullable
    String footerText();
  }

  protected static class SetBookmarkItem implements ItemWrapper {
    private final VirtualFile myFile;
    private final int myLine;

    public SetBookmarkItem(VirtualFile file, int line) {
      myFile = file;
      myLine = line;
    }

    public void setupRenderer(ColoredListCellRenderer renderer, Project project, boolean selected) {
      renderer.append(speedSearchText());
      renderer.setIcon(Bookmark.TICK);
    }

    public String speedSearchText() {
      return "Bookmark this place";
    }

    public void execute(Project project) {
      BookmarkManager.getInstance(project).addTextBookmark(myFile, myLine, "");
    }

    @Nullable
    public String footerText() {
      return null;
    }
  }

  protected static class RemoveBookmarkItem implements ItemWrapper {
    private final Bookmark myBookmark;

    public RemoveBookmarkItem(Bookmark bookmark) {
      myBookmark = bookmark;
    }

    public void setupRenderer(ColoredListCellRenderer renderer, Project project, boolean selected) {
      renderer.append(speedSearchText());
      renderer.setIcon(Bookmark.TICK);
    }

    public String speedSearchText() {
      return "Remove Bookmark";
    }

    public void execute(Project project) {
      BookmarkManager.getInstance(project).removeBookmark(myBookmark);
    }

    @Nullable
    public String footerText() {
      return null;
    }
  }

  private static class ManageBookmarksItem implements ItemWrapper {
    private final Bookmark myCurrentBookmark;

    private ManageBookmarksItem(Bookmark currentBookmark) {
      myCurrentBookmark = currentBookmark;
    }

    public void setupRenderer(ColoredListCellRenderer renderer, Project project, boolean selected) {
      renderer.append(speedSearchText());
    }

    public String speedSearchText() {
      return "Manage Bookmarks";
    }

    public void execute(Project project) {
      EditorBookmarksDialog.execute(BookmarkManager.getInstance(project), myCurrentBookmark);
    }

    public String footerText() {
      return null;
    }
  }

  private static class BookmarkItem implements ItemWrapper {
    private final Bookmark myBookmark;

    private BookmarkItem(Bookmark bookmark) {
      myBookmark = bookmark;
    }

    public void setupRenderer(ColoredListCellRenderer renderer, Project project, boolean selected) {
      VirtualFile file = myBookmark.getFile();

      PsiManager psiManager = PsiManager.getInstance(project);

      PsiElement fileOrDir = file.isDirectory() ? psiManager.findDirectory(file) : psiManager.findFile(file);
      if (fileOrDir != null) {
        renderer.setIcon(fileOrDir.getIcon(Iconable.ICON_FLAG_CLOSED));
      }

      FileStatus fileStatus = FileStatusManager.getInstance(project).getStatus(file);
      TextAttributes attributes = new TextAttributes(fileStatus.getColor(), null, null, EffectType.LINE_UNDERSCORE, Font.PLAIN);
      renderer.append(file.getName(), SimpleTextAttributes.fromTextAttributes(attributes));
      if (myBookmark.getLine() >= 0) {
        renderer.append(":", SimpleTextAttributes.GRAYED_ATTRIBUTES);
        renderer.append(String.valueOf(myBookmark.getLine() + 1), SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }

      if (!selected && FileEditorManager.getInstance(project).isFileOpen(file)) {
        renderer.setBackground(LightColors.SLIGHTLY_GREEN);
      }

      String description = myBookmark.getDescription();
      if (description != null) {
        renderer.append(" " + description, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      }
    }

    public String speedSearchText() {
      return myBookmark.getFile().getName() + " " + myBookmark.getDescription();
    }

    public void execute(Project project) {
      myBookmark.navigate();
    }

    public String footerText() {
      VirtualFile file = myBookmark.getFile();
      if (file.isDirectory()) {
        return file.getPresentableUrl();
      }

      return file.getParent().getPresentableUrl();
    }
  }

  private static class ItemRenderer extends ColoredListCellRenderer {
    private final Project myProject;

    private ItemRenderer(Project project) {
      myProject = project;
    }

    @Override
    protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
      if (value instanceof ItemWrapper) {
        ((ItemWrapper)value).setupRenderer(this, myProject, selected);
      }
    }
  }

  protected static class BookmarkInContextInfo {
    private DataContext myDataContext;
    private Project myProject;
    private Bookmark myBookmarkAtPlace;
    private VirtualFile myFile;
    private int myLine;

    public BookmarkInContextInfo(DataContext dataContext, Project project) {
      myDataContext = dataContext;
      myProject = project;
    }

    public Bookmark getBookmarkAtPlace() {
      return myBookmarkAtPlace;
    }

    public VirtualFile getFile() {
      return myFile;
    }

    public int getLine() {
      return myLine;
    }

    public BookmarkInContextInfo invoke() {
      myBookmarkAtPlace = null;
      myFile = null;
      myLine = -1;


      BookmarkManager bookmarkManager = BookmarkManager.getInstance(myProject);
      if (ToolWindowManager.getInstance(myProject).isEditorComponentActive()) {
        Editor editor = PlatformDataKeys.EDITOR.getData(myDataContext);
        if (editor != null) {
          Document document = editor.getDocument();
          myLine = editor.getCaretModel().getLogicalPosition().line;
          myFile = FileDocumentManager.getInstance().getFile(document);
          myBookmarkAtPlace = bookmarkManager.findEditorBookmark(document, myLine);
        }
      }

      if (myFile == null) {
        myFile = PlatformDataKeys.VIRTUAL_FILE.getData(myDataContext);
        myLine = -1;

        if (myBookmarkAtPlace == null) {
          myBookmarkAtPlace = bookmarkManager.findFileBookmark(myFile);
        }
      }
      return this;
    }
  }

  private static class PreviewPanel extends JPanel {
    private final Project myProject;
    private Editor myEditor;
    private ItemWrapper myWrapper;

    public PreviewPanel(Project project) {
      super(new BorderLayout());
      myProject = project;
      setPreferredSize(new Dimension(600, 400));
    }

    public void updateWithItem(ItemWrapper wrapper) {
      if (myWrapper != wrapper) {
        myWrapper = wrapper;

        if (wrapper instanceof BookmarkItem) {
          final Bookmark bookmark = ((BookmarkItem)wrapper).myBookmark;
          VirtualFile file = bookmark.getFile();
          Document document = FileDocumentManager.getInstance().getDocument(file);

          if (document != null) {
            if (myEditor == null || myEditor.getDocument() != document) {
              cleanup();
              myEditor = EditorFactory.getInstance().createViewer(document, myProject);
              EditorHighlighter highlighter = EditorHighlighterFactory.getInstance()
                .createEditorHighlighter(file, EditorColorsManager.getInstance().getGlobalScheme(), myProject);
              ((EditorEx)myEditor).setHighlighter(highlighter);
              ((EditorEx)myEditor).setFile(file);

              myEditor.getSettings().setAnimatedScrolling(false);
              myEditor.getSettings().setLineNumbersShown(true);
              myEditor.getSettings().setFoldingOutlineShown(false);
              add(myEditor.getComponent(), BorderLayout.CENTER);
            }

            myEditor.getCaretModel().moveToLogicalPosition(new LogicalPosition(bookmark.getLine(), 0));
            myEditor.getScrollingModel().scrollToCaret(ScrollType.CENTER_UP);
          }
          else {
            cleanup();
          }
        }
        else {
          cleanup();
          add(new JLabel("select file in the list"));
        }
      }
    }

    private void cleanup() {
      removeAll();
      if (myEditor != null) {
        EditorFactory.getInstance().releaseEditor(myEditor);
        myEditor = null;
      }
    }

    @Override
    public void removeNotify() {
      super.removeNotify();
      cleanup();
    }
  }

}
