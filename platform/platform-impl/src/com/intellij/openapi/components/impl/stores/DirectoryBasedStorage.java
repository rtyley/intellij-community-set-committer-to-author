package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.StateSplitter;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.tracker.VirtualFileTracker;
import com.intellij.util.PairConsumer;
import static com.intellij.util.io.fs.FileSystem.FILE_SYSTEM;
import com.intellij.util.io.fs.IFile;
import com.intellij.util.messages.MessageBus;
import gnu.trove.THashMap;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.PicoContainer;

import java.io.File;
import java.io.IOException;
import java.util.*;

//todo: support missing plugins

//todo: support storage data
public class DirectoryBasedStorage implements StateStorage, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.components.impl.stores.DirectoryBasedStorage");

  private final TrackingPathMacroSubstitutor myPathMacroSubstitutor;
  private final IFile myDir;
  private final StateSplitter mySplitter;

  private Object mySession;
  private MyStorageData myStorageData = null;
  @NonNls private static final String COMPONENT = "component";
  @NonNls private static final String NAME = "name";

  private static final IFile[] EMPTY_FILES = new IFile[0];

  private final FileTypeManager myFileTypeManager;

  public DirectoryBasedStorage(final TrackingPathMacroSubstitutor pathMacroSubstitutor,
                               final String dir,
                               final StateSplitter splitter,
                               Disposable parentDisposable,
                               final PicoContainer picoContainer) {
    assert dir.indexOf("$") < 0;
    myPathMacroSubstitutor = pathMacroSubstitutor;
    myDir = FILE_SYSTEM.createFile(dir);
    mySplitter = splitter;
    Disposer.register(parentDisposable, this);

    VirtualFileTracker virtualFileTracker = (VirtualFileTracker)picoContainer.getComponentInstanceOfType(VirtualFileTracker.class);
    MessageBus messageBus = (MessageBus)picoContainer.getComponentInstanceOfType(MessageBus.class);


    if (virtualFileTracker != null && messageBus != null) {
      final String path = myDir.getAbsolutePath();
      final String fileUrl = LocalFileSystem.PROTOCOL_PREFIX + path.replace(File.separatorChar, '/');


      final Listener listener = messageBus.syncPublisher(STORAGE_TOPIC);
      virtualFileTracker.addTracker(fileUrl, new VirtualFileAdapter() {
        public void contentsChanged(final VirtualFileEvent event) {
          if (!StringUtil.endsWithIgnoreCase(event.getFile().getName(), ".xml")) return;
          listener.storageFileChanged(event, DirectoryBasedStorage.this);
        }

        public void fileDeleted(final VirtualFileEvent event) {
          if (!StringUtil.endsWithIgnoreCase(event.getFile().getName(), ".xml")) return;
          listener.storageFileChanged(event, DirectoryBasedStorage.this);
        }

        public void fileCreated(final VirtualFileEvent event) {
          if (!StringUtil.endsWithIgnoreCase(event.getFile().getName(), ".xml")) return;
          listener.storageFileChanged(event, DirectoryBasedStorage.this);
        }
      }, false, this);
    }

    myFileTypeManager = FileTypeManager.getInstance();
  }

  @Nullable
  public <T> T getState(final Object component, final String componentName, Class<T> stateClass, @Nullable T mergeInto)
    throws StateStorageException {
    if (myStorageData == null) myStorageData = loadState();


    if (!myStorageData.containsComponent(componentName)) {
      return DefaultStateSerializer.deserializeState(new Element(COMPONENT), stateClass, mergeInto);
    }

    final List<Element> subElements = new ArrayList<Element>();
    myStorageData.processComponent(componentName, new PairConsumer<IFile, Element>() {
      public void consume(final IFile iFile, final Element element) {
        final List children = element.getChildren();
        assert children.size() == 1;
        final Element subElement = (Element)children.get(0);
        subElement.detach();
        subElements.add(subElement);
      }
    });

    final Element state = new Element(COMPONENT);
    mySplitter.mergeStatesInto(state, subElements.toArray(new Element[subElements.size()]));
    myStorageData.removeComponent(componentName);

    return DefaultStateSerializer.deserializeState(state, stateClass, mergeInto);
  }

  private MyStorageData loadState() throws StateStorageException {
    MyStorageData storageData = new MyStorageData();
    if (!myDir.exists()) {
      return storageData;
    }
    try {
      final IFile[] files = myDir.listFiles();

      for (IFile file : files) {
        if (!StringUtil.endsWithIgnoreCase(file.getName(), ".xml")) {
          //do not load system files like .DS_Store on Mac
          continue;
        }
        final Document document = JDOMUtil.loadDocument(file);
        final Element element = document.getRootElement();
        assert element.getName().equals(COMPONENT);

        String componentName = element.getAttributeValue(NAME);
        assert componentName != null;

        if (myPathMacroSubstitutor != null) {
          myPathMacroSubstitutor.expandPaths(element);
        }
        
        storageData.put(componentName, file, element, true);
      }
    }
    catch (IOException e) {
      throw new StateStorageException(e);
    }
    catch (JDOMException e) {
      throw new StateStorageException(e);
    }

    return storageData;
  }


  public boolean hasState(final Object component, final String componentName, final Class<?> aClass) throws StateStorageException {
    if (!myDir.exists()) return false;
    return true;
  }

  @NotNull
  public ExternalizationSession startExternalization() {
    if (myStorageData == null) {
      try {
        myStorageData = loadState();
      }
      catch (StateStorageException e) {
        LOG.error(e);
      }
    }
    final ExternalizationSession session = new MyExternalizationSession(myPathMacroSubstitutor, myStorageData.clone());

    mySession = session;
    return session;
  }

  @NotNull
  public SaveSession startSave(final ExternalizationSession externalizationSession) {
    assert mySession == externalizationSession;

    final MySaveSession session =
      new MySaveSession(((MyExternalizationSession)externalizationSession).myStorageData, myPathMacroSubstitutor);
    mySession = session;
    return session;
  }

  public void finishSave(final SaveSession saveSession) {
    try {
      LOG.assertTrue(mySession == saveSession);
    } finally {
      mySession = null;
    }
  }

  public void reload(final Set<String> changedComponents) throws StateStorageException {
    myStorageData = loadState();
  }

  public void dispose() {
  }

  private class MySaveSession implements SaveSession {
    private final MyStorageData myStorageData;
    private final TrackingPathMacroSubstitutor myPathMacroSubstitutor;
    private Set<String> myUsedMacros;

    private MySaveSession(final MyStorageData storageData, final TrackingPathMacroSubstitutor pathMacroSubstitutor) {
      myStorageData = storageData;
      myPathMacroSubstitutor = pathMacroSubstitutor;
    }

    public Set<String> getUsedMacros() {
      if (myUsedMacros == null) {
        if (myPathMacroSubstitutor != null) {
          myPathMacroSubstitutor.reset();

          final Map<String, Map<IFile, Element>> states = myStorageData.myStates;
          for (Map<IFile, Element> map : states.values()) {
            for (Element e : map.values()) {
              myPathMacroSubstitutor.collapsePaths((Element)e.clone());
            }
          }

          myUsedMacros = new HashSet<String>(myPathMacroSubstitutor.getUsedMacros());
        }
        else {
          myUsedMacros = new HashSet<String>();
        }
      }

      return myUsedMacros;
    }

    public void save() throws StateStorageException {
      assert mySession == this;
      final Set<String> currentNames = new HashSet<String>();

      IFile[] children = myDir.exists() ? myDir.listFiles() : EMPTY_FILES;
      for (IFile child : children) {
        final String fileName = child.getName();
        if (!myFileTypeManager.isFileIgnored(fileName)) {
          currentNames.add(fileName);
        }
      }

      myStorageData.process(new StorageDataProcessor() {
        public void process(final String componentName, final IFile file, final Element element) {
          currentNames.remove(file.getName());

          if (myPathMacroSubstitutor != null) {
            myPathMacroSubstitutor.collapsePaths(element);
          }

          if (file.getTimeStamp() <= myStorageData.getLastTimeStamp()) {
            if (!myDir.exists()) {
              myDir.createParentDirs();
              myDir.mkDir();
            }

            StorageUtil.save(file, element);
            myStorageData.updateLastTimestamp(file);
          }
        }
      });

      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          if (myDir.exists()) {
            for (String name : currentNames) {
              IFile child = myDir.getChild(name);

              if (child.getTimeStamp() > myStorageData.getLastTimeStamp()) {
                // do not touch new files during VC update (which aren't read yet)
                // now got an opposite problem: file is recreated if was removed by VC during update.
                return;
              }

              final VirtualFile virtualFile = StorageUtil.getVirtualFile(child);
              if (virtualFile != null) {
                try {
                  LOG.debug("Removing configuration file: " + virtualFile.getPresentableUrl());
                  virtualFile.delete(DirectoryBasedStorage.this);
                }
                catch (IOException e) {
                  LOG.error(e);
                }
              }
            }
          }
        }
      });

      myStorageData.clear();
    }

    @Nullable
    public Set<String> analyzeExternalChanges(final Set<Pair<VirtualFile, StateStorage>> changedFiles) {
      boolean containsSelf = false;

      for (Pair<VirtualFile, StateStorage> pair : changedFiles) {
        if (pair.second == DirectoryBasedStorage.this) {
          VirtualFile file = pair.first;
          if ("xml".equalsIgnoreCase(file.getExtension())) {
            containsSelf = true;
            break;
          }
        }
      }

      if (!containsSelf) return Collections.emptySet();

      if (myStorageData.getComponentNames().size() == 0) {
        // no state yet, so try to initialize it now
        final MyStorageData storageData = loadState();
        return new HashSet<String>(storageData.getComponentNames());
      }

      return new HashSet<String>(myStorageData.getComponentNames());
    }

    public Collection<IFile> getStorageFilesToSave() throws StateStorageException {
      assert mySession == this;

      if (!myDir.exists()) return getAllStorageFiles();
      assert myDir.isDirectory();

      final List<IFile> filesToSave = new ArrayList<IFile>();

      IFile[] children = myDir.listFiles();
      final Set<String> currentChildNames = new HashSet<String>();
      for (IFile child : children) {
        if (!myFileTypeManager.isFileIgnored(child.getName())) currentChildNames.add(child.getName());
      }

      myStorageData.process(new StorageDataProcessor() {
        public void process(final String componentName, final IFile file, final Element element) {
          if (currentChildNames.contains(file.getName())) {
            currentChildNames.remove(file.getName());

            if (myPathMacroSubstitutor != null) {
              myPathMacroSubstitutor.collapsePaths(element);
            }

            if (!StorageUtil.contentEquals(element, file)) {
              filesToSave.add(file);
            }
          }

        }
      });

      for (String childName : currentChildNames) {
        final IFile child = myDir.getChild(childName);
        filesToSave.add(child);
      }

      return filesToSave;
    }

    public List<IFile> getAllStorageFiles() {
      return new ArrayList<IFile>(myStorageData.getAllStorageFiles().keySet());
    }
  }

  private interface StorageDataProcessor {
    void process(String componentName, IFile file, Element element);
  }

  private static class MyStorageData {
    private Map<String, Map<IFile, Element>> myStates = new HashMap<String, Map<IFile, Element>>();
    private long myLastTimestamp = 0;

    public Set<String> getComponentNames() {
      return myStates.keySet();
    }

    public void put(final String componentName, final IFile file, final Element element, final boolean updateTimestamp) {
      LOG.assertTrue(componentName != null, String.format("Component name should not be null for file: %s", file == null ? "NULL!" : file.getPath()));
      
      Map<IFile, Element> stateMap = myStates.get(componentName);
      if (stateMap == null) {
        stateMap = new HashMap<IFile, Element>();
        myStates.put(componentName, stateMap);
      }

      stateMap.put(file, element);
      if (updateTimestamp) updateLastTimestamp(file);
    }

    public void updateLastTimestamp(final IFile file) {
      myLastTimestamp = Math.max(myLastTimestamp, file.getTimeStamp());
    }

    public long getLastTimeStamp() {
      return myLastTimestamp;
    }

    public Map<IFile, Long> getAllStorageFiles() {
      final Map<IFile, Long> allStorageFiles = new THashMap<IFile, Long>();
      process(new StorageDataProcessor() {
        public void process(final String componentName, final IFile file, final Element element) {
          allStorageFiles.put(file, file.getTimeStamp());
        }
      });

      return allStorageFiles;
    }

    public void processComponent(@NotNull final String componentName, @NotNull final PairConsumer<IFile, Element> consumer) {
      final Map<IFile, Element> map = myStates.get(componentName);
      if (map != null) {
        for (IFile file : map.keySet()) {
          consumer.consume(file, map.get(file));
        }
      }
    }

    public void process(@NotNull final StorageDataProcessor processor) {
      for (final String componentName : myStates.keySet()) {
        processComponent(componentName, new PairConsumer<IFile, Element>() {
          public void consume(final IFile iFile, final Element element) {
            processor.process(componentName, iFile, element);
          }
        });
      }
    }

    protected MyStorageData clone() {
      final MyStorageData result = new MyStorageData();
      result.myStates = new HashMap<String, Map<IFile, Element>>(myStates);
      result.myLastTimestamp = myLastTimestamp;
      return result;
    }

    public void clear() {
      myStates.clear();
    }

    public boolean containsComponent(final String componentName) {
      return myStates.get(componentName) != null;
    }

    public void removeComponent(final String componentName) {
      myStates.remove(componentName);
    }
  }

  private class MyExternalizationSession implements ExternalizationSession {
    private final TrackingPathMacroSubstitutor myPathMacroSubstitutor;
    private final MyStorageData myStorageData;

    private MyExternalizationSession(final TrackingPathMacroSubstitutor pathMacroSubstitutor, final MyStorageData storageData) {
      myStorageData = storageData;
      myPathMacroSubstitutor = pathMacroSubstitutor;
      myPathMacroSubstitutor.reset();
    }

    public void setState(final Object component, final String componentName, final Object state, final Storage storageSpec)
      throws StateStorageException {
      assert mySession == this;
      setState(componentName, state, storageSpec);
    }

    private void setState(final String componentName, Object state, final Storage storageSpec) throws StateStorageException {
      try {
        final Element element = DefaultStateSerializer.serializeState(state, storageSpec);

        final List<Pair<Element, String>> states = mySplitter.splitState(element);
        for (Pair<Element, String> pair : states) {
          Element e = pair.first;
          String name = pair.second;

          Element statePart = new Element(COMPONENT);
          statePart.setAttribute(NAME, componentName);
          e.detach();
          statePart.addContent(e);

          myStorageData.put(componentName, myDir.getChild(name), statePart, false);
        }
      }
      catch (WriteExternalException e) {
        throw new StateStorageException(e);
      }
    }
  }
}
