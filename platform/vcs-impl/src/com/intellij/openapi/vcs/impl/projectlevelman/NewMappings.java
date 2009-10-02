package com.intellij.openapi.vcs.impl.projectlevelman;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.impl.DefaultVcsRootPolicy;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class NewMappings {
  private final static Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.impl.projectlevelman.NewMappings");
  private final Object myLock;

  // vcs to mappings
  private final Map<String, List<VcsDirectoryMapping>> myVcsToPaths;
  private AbstractVcs[] myActiveVcses;
  private VcsDirectoryMapping[] mySortedMappings;
  private final Map<VcsDirectoryMapping, LocalFileSystem.WatchRequest> myDirectoryMappingWatches;
  
  private final DefaultVcsRootPolicy myDefaultVcsRootPolicy;
  private final EventDispatcher<VcsListener> myEventDispatcher;
  private final Project myProject;

  public NewMappings(final Project project, final EventDispatcher<VcsListener> eventDispatcher) {
    myProject = project;
    myLock = new Object();
    myVcsToPaths = new HashMap<String, List<VcsDirectoryMapping>>();
    myDirectoryMappingWatches = new HashMap<VcsDirectoryMapping, LocalFileSystem.WatchRequest>();
    myDefaultVcsRootPolicy = DefaultVcsRootPolicy.getInstance(project);
    myActiveVcses = new AbstractVcs[0];
    myEventDispatcher = eventDispatcher;

    final ArrayList<VcsDirectoryMapping> listStr = new ArrayList<VcsDirectoryMapping>();
    final VcsDirectoryMapping mapping = new VcsDirectoryMapping("", "");
    listStr.add(mapping);
    myVcsToPaths.put("", listStr);
    mySortedMappings = new VcsDirectoryMapping[] {mapping};
  }

  public AbstractVcs[] getActiveVcses() {
    synchronized (myLock) {
      final AbstractVcs[] result = new AbstractVcs[myActiveVcses.length];
      System.arraycopy(myActiveVcses, 0, result, 0, myActiveVcses.length);
      return result;
    }
  }

  @Modification
  public void setMapping(final String path, final String activeVcsName) {
    LOG.debug("setMapping path = '" + path + "' vcs = " + activeVcsName);
    final VcsDirectoryMapping newMapping = new VcsDirectoryMapping(path, activeVcsName);
    final LocalFileSystem.WatchRequest request = addWatchRequest(newMapping);

    final Ref<Boolean> switched = new Ref<Boolean>(Boolean.FALSE);
    keepActiveVcs(new Runnable() {
      public void run() {
        // sorted -> map. sorted mappings are NOT changed;
        switched.set(trySwitchVcs(path, activeVcsName));
        if (! switched.get().booleanValue()) {
          final List<VcsDirectoryMapping> newList = listForVcsFromMap(newMapping.getVcs());
          newList.add(newMapping);
          sortedMappingsByMap();

          if (request != null) {
            myDirectoryMappingWatches.put(newMapping, request);
          }
        }
      }
    });

    if (switched.get().booleanValue() && (request != null)) {
      LocalFileSystem.getInstance().removeWatchedRoot(request);
    }

    mappingsChanged();
  }

  private void keepActiveVcs(final Runnable runnable) {
    final MyVcsActivator activator;
    synchronized (myLock) {
      activator = new MyVcsActivator(new HashSet<String>(myVcsToPaths.keySet()));
      runnable.run();
      restoreActiveVcses();
    }
    activator.activate(myVcsToPaths.keySet(), AllVcses.getInstance(myProject));
  }

  private void restoreActiveVcses() {
    synchronized (myLock) {
      final Set<String> set = myVcsToPaths.keySet();
      final List<AbstractVcs> list = new ArrayList<AbstractVcs>(set.size());
      for (String s : set) {
        if (s.trim().length() == 0) continue;
        final AbstractVcs vcs = AllVcses.getInstance(myProject).getByName(s);
        if (vcs != null) {
          list.add(vcs);
        }
      }
      myActiveVcses = list.toArray(new AbstractVcs[list.size()]);
    }
  }

  public void mappingsChanged() {
    myEventDispatcher.getMulticaster().directoryMappingChanged();
  }

  @Modification
  public void setDirectoryMappings(final List<VcsDirectoryMapping> items) {
    LOG.debug("setDirectoryMappings, size: " + items.size());
    MySetMappingsPreProcessor setMappingsPreProcessor = new MySetMappingsPreProcessor(items);
    setMappingsPreProcessor.invoke();
    final List<VcsDirectoryMapping> itemsCopy = setMappingsPreProcessor.getItemsCopy();
    final Map<VcsDirectoryMapping, LocalFileSystem.WatchRequest> requests = setMappingsPreProcessor.getRequests();

    final Collection<LocalFileSystem.WatchRequest> toRemove = new ArrayList<LocalFileSystem.WatchRequest>();

    keepActiveVcs(new Runnable() {
      public void run() {
        // a copy!
        toRemove.addAll(myDirectoryMappingWatches.values());
        myDirectoryMappingWatches.clear();

        myVcsToPaths.clear();
        for (VcsDirectoryMapping mapping : itemsCopy) {
          listForVcsFromMap(mapping.getVcs()).add(mapping);
        }
        sortedMappingsByMap();
      }
    });

    // do not remove what added and is active
    toRemove.removeAll(requests.values());
    // tracked by request object reference so OK to first add and then remove
    LocalFileSystem.getInstance().removeWatchedRoots(toRemove);

    mappingsChanged();
  }

  @Nullable
  public VcsDirectoryMapping getMappingFor(VirtualFile file) {
    if (file == null) return null;
    if (! file.isInLocalFileSystem()) {
      return null;
    }

    return getMappingFor(file, myDefaultVcsRootPolicy.getMatchContext(file));
  }

  @Nullable
  public VcsDirectoryMapping getMappingFor(final VirtualFile file, final Object matchContext) {
    // performance: calculate file path just once, rather than once per mapping
    String path = file.getPath();

    synchronized (myLock) {
      for (int i = mySortedMappings.length - 1; i >= 0; -- i) {
        final VcsDirectoryMapping mapping = mySortedMappings[i];
        final String systemIndependPath = FileUtil.toSystemIndependentName((file.isDirectory() && (! path.endsWith("/"))) ? (path + "/") : path);
        if (fileMatchesMapping(file, matchContext, systemIndependPath, mapping)) {
          return mapping;
        }
      }
      return null;
    }
  }

  @Nullable
  public String getVcsFor(@NotNull VirtualFile file) {
    VcsDirectoryMapping mapping = getMappingFor(file);
    if (mapping == null) {
      return null;
    }
    return mapping.getVcs();
  }

  private boolean fileMatchesMapping(final VirtualFile file, final Object matchContext, final String systemIndependPath, final VcsDirectoryMapping mapping) {
    if (mapping.getDirectory().length() == 0) {
      return myDefaultVcsRootPolicy.matchesDefaultMapping(file, matchContext);
    }
    return FileUtil.startsWith(systemIndependPath, mapping.systemIndependentPath());
  }

  List<VirtualFile> getMappingsAsFilesUnderVcs(final AbstractVcs vcs) {
    final List<VirtualFile> result = new ArrayList<VirtualFile>();
    final String vcsName = vcs.getName();

    final List<VcsDirectoryMapping> mappings;
    synchronized (myLock) {
      final List<VcsDirectoryMapping> vcsMappings = myVcsToPaths.get(vcsName);
      if (vcsMappings == null) return result;
      mappings = new ArrayList<VcsDirectoryMapping>(vcsMappings);
    }

    for (VcsDirectoryMapping mapping : mappings) {
      if (mapping.isDefaultMapping()) {
        // todo callback here; don't like it
        myDefaultVcsRootPolicy.addDefaultVcsRoots(this, vcs, result);
      } else {
        final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(mapping.getDirectory());
        if (file != null) {
          result.add(file);
        }
      }
    }
    return result;
  }

  @Modification
  public void disposeMe() {
    LOG.debug("dipose me");
    clearImpl();
  }

  @Modification
  public void clear() {
    LOG.debug("clear");
    clearImpl();

    mappingsChanged();
  }

  private void clearImpl() {
    // if vcses were not mapped, there's nothing to clear
    if ((myActiveVcses ==  null) || (myActiveVcses.length == 0)) return;

    final Collection<LocalFileSystem.WatchRequest> toRemove = new ArrayList<LocalFileSystem.WatchRequest>();
    keepActiveVcs(new Runnable() {
      public void run() {
        // a copy!
        toRemove.addAll(myDirectoryMappingWatches.values());
        myDirectoryMappingWatches.clear();

        myVcsToPaths.clear();
        myActiveVcses = new AbstractVcs[0];
        mySortedMappings = new VcsDirectoryMapping[0];
      }
    });
    if (! toRemove.isEmpty()) {
      LocalFileSystem.getInstance().removeWatchedRoots(toRemove);
    }
  }

  public List<VcsDirectoryMapping> getDirectoryMappings() {
    synchronized (myLock) {
      return Arrays.asList(mySortedMappings);
    }
  }

  public List<VcsDirectoryMapping> getDirectoryMappings(String vcsName) {
    synchronized (myLock) {
      final List<VcsDirectoryMapping> mappings = myVcsToPaths.get(vcsName);
      return mappings == null ? new ArrayList<VcsDirectoryMapping>() : new ArrayList<VcsDirectoryMapping>(mappings);
    }
  }

  public void cleanupMappings() {
    final List<LocalFileSystem.WatchRequest> watchRequestList;
    synchronized (myLock) {
      watchRequestList = removeRedundantMappings();
    }
    LocalFileSystem.getInstance().removeWatchedRoots(watchRequestList);
  }

  @Nullable
  public String haveDefaultMapping() {
    synchronized (myLock) {
      // empty mapping MUST be first
      if (mySortedMappings.length == 0) return null;
      return mySortedMappings[0].isDefaultMapping() ? mySortedMappings[0].getVcs() : null;
    }
  }

  public boolean isEmpty() {
    synchronized (myLock) {
      return mySortedMappings.length == 0;
    }
  }

  @Modification
  public void removeDirectoryMapping(final VcsDirectoryMapping mapping) {
    LOG.debug("remove mapping: " + mapping.getDirectory());
    final Ref<LocalFileSystem.WatchRequest> request = new Ref<LocalFileSystem.WatchRequest>();

    keepActiveVcs(new Runnable() {
      public void run() {
        if (removeVcsFromMap(mapping, mapping.getVcs())) {
          sortedMappingsByMap();
          request.set(myDirectoryMappingWatches.remove(mapping));
        }
      }
    });

    if (! request.isNull()) {
      LocalFileSystem.getInstance().removeWatchedRoot(request.get());
    }

    mappingsChanged();
  }

  private class MyMappingsFilter extends AbstractFilterChildren<VcsDirectoryMapping> {
    private final List<LocalFileSystem.WatchRequest> myRemovedRequests;

    private MyMappingsFilter() {
      myRemovedRequests = new ArrayList<LocalFileSystem.WatchRequest>();
    }

    protected void sortAscending(List<VcsDirectoryMapping> vcsDirectoryMappings) {
      // todo ordering is actually here
      Collections.sort(vcsDirectoryMappings, MyMappingsComparator.getInstance());
    }

    @Override
    protected void onRemove(final VcsDirectoryMapping vcsDirectoryMapping) {
      final LocalFileSystem.WatchRequest request = myDirectoryMappingWatches.remove(vcsDirectoryMapping);
      if (request != null) {
        myRemovedRequests.add(request);
      }
    }

    protected boolean isAncestor(VcsDirectoryMapping parent, VcsDirectoryMapping child) {
      if (! parent.getVcs().equals(child.getVcs())) return false;

      final String parentPath = parent.systemIndependentPath();
      final String fixedParentPath = (parentPath.endsWith("/")) ? parentPath : (parentPath + "/");

      if (child.systemIndependentPath().length() < fixedParentPath.length()) {
        return child.systemIndependentPath().equals(parentPath);
      }
      return child.systemIndependentPath().startsWith(fixedParentPath);
    }

    public List<LocalFileSystem.WatchRequest> getRemovedRequests() {
      return myRemovedRequests;
    }
  }

  // todo area for optimization
  private List<LocalFileSystem.WatchRequest> removeRedundantMappings() {
    final Set<Map.Entry<String, List<VcsDirectoryMapping>>> entries = myVcsToPaths.entrySet();

    final LocalFileSystem lfs = LocalFileSystem.getInstance();
    final AllVcsesI allVcses = AllVcses.getInstance(myProject);

    final List<LocalFileSystem.WatchRequest> removedRequests = new LinkedList<LocalFileSystem.WatchRequest>();

    for (Iterator<String> iterator = myVcsToPaths.keySet().iterator(); iterator.hasNext();) {
      final String vcsName = iterator.next();
      final List<VcsDirectoryMapping> mappings = myVcsToPaths.get(vcsName);

      final List<Pair<VirtualFile, VcsDirectoryMapping>> objects = ObjectsConvertor.convert(mappings,
        new Convertor<VcsDirectoryMapping, Pair<VirtualFile, VcsDirectoryMapping>>() {
        public Pair<VirtualFile, VcsDirectoryMapping> convert(final VcsDirectoryMapping dm) {
          VirtualFile vf = lfs.findFileByPath(dm.getDirectory());
          if (vf == null) {
            vf = lfs.refreshAndFindFileByPath(dm.getDirectory());
          }
          return vf == null ? null : new Pair<VirtualFile, VcsDirectoryMapping>(vf, dm);
        }
      }, ObjectsConvertor.NOT_NULL);

      final List<Pair<VirtualFile, VcsDirectoryMapping>> filteredFiles;
      // todo static
      final Convertor<Pair<VirtualFile, VcsDirectoryMapping>, VirtualFile> fileConvertor =
        new Convertor<Pair<VirtualFile, VcsDirectoryMapping>, VirtualFile>() {
          public VirtualFile convert(Pair<VirtualFile, VcsDirectoryMapping> o) {
            return o.getFirst();
          }
        };
      if (StringUtil.isEmptyOrSpaces(vcsName)) {
        filteredFiles = AbstractVcs.filterUniqueRootsDefault(objects, fileConvertor);
      } else {
        final AbstractVcs vcs = allVcses.getByName(vcsName);
        filteredFiles = vcs.filterUniqueRoots(objects, fileConvertor);
      }

      final List<VcsDirectoryMapping> filteredMappings =
        ObjectsConvertor.convert(filteredFiles, new Convertor<Pair<VirtualFile, VcsDirectoryMapping>, VcsDirectoryMapping>() {
          public VcsDirectoryMapping convert(final Pair<VirtualFile, VcsDirectoryMapping> o) {
            return o.getSecond();
          }
        });

      // to calculate what had been removed
      mappings.removeAll(filteredMappings);
      for (VcsDirectoryMapping mapping : mappings) {
        removedRequests.add(myDirectoryMappingWatches.remove(mapping));
      }

      if (filteredMappings.isEmpty()) {
        iterator.remove();
      } else {
        mappings.clear();
        mappings.addAll(filteredMappings);
      }
    }

    sortedMappingsByMap();
    return removedRequests;
  }

  private boolean trySwitchVcs(final String path, final String activeVcsName) {
    final String fixedPath = FileUtil.toSystemIndependentName(path);
    for (VcsDirectoryMapping mapping : mySortedMappings) {
      if (mapping.systemIndependentPath().equals(fixedPath)) {
        final String oldVcs = mapping.getVcs();
        if (! oldVcs.equals(activeVcsName)) {
          migrateVcs(activeVcsName, mapping, oldVcs);
        }
        return true;
      }
    }
    return false;
  }

  private void sortedMappingsByMap() {
    final List<VcsDirectoryMapping> list = new ArrayList<VcsDirectoryMapping>();
    for (List<VcsDirectoryMapping> mappingList : myVcsToPaths.values()) {
      list.addAll(mappingList);
    }
    mySortedMappings = list.toArray(new VcsDirectoryMapping[list.size()]);
    Arrays.sort(mySortedMappings, MyMappingsComparator.getInstance());
  }

  private void migrateVcs(String activeVcsName, VcsDirectoryMapping mapping, String oldVcs) {
    mapping.setVcs(activeVcsName);

    removeVcsFromMap(mapping, oldVcs);

    final List<VcsDirectoryMapping> newList = listForVcsFromMap(activeVcsName);
    newList.add(mapping);
  }

  private boolean removeVcsFromMap(VcsDirectoryMapping mapping, String oldVcs) {
    final List<VcsDirectoryMapping> oldList = myVcsToPaths.get(oldVcs);
    if (oldList == null) return false;

    final boolean result = oldList.remove(mapping);
    if (oldList.isEmpty()) {
      myVcsToPaths.remove(oldVcs);
    }
    return result;
  }

  // todo don't like it
  private List<VcsDirectoryMapping> listForVcsFromMap(String activeVcsName) {
    List<VcsDirectoryMapping> newList = myVcsToPaths.get(activeVcsName);
    if (newList == null) {
      newList = new ArrayList<VcsDirectoryMapping>();
      myVcsToPaths.put(activeVcsName, newList);
    }
    return newList;
  }

  @Nullable
  private static LocalFileSystem.WatchRequest addWatchRequest(final VcsDirectoryMapping mapping) {
    if (! mapping.isDefaultMapping()) {
      return LocalFileSystem.getInstance().addRootToWatch(mapping.getDirectory(), true);
    }
    return null;
  }

  private static class MyMappingsComparator implements Comparator<VcsDirectoryMapping> {
    private static final MyMappingsComparator ourInstance = new MyMappingsComparator();

    public static MyMappingsComparator getInstance() {
      return ourInstance;
    }

    public int compare(VcsDirectoryMapping m1, VcsDirectoryMapping m2) {
      return m1.getDirectory().compareTo(m2.getDirectory());
    }
  }

  private static class MyVcsActivator {
    private final Set<String> myOld;

    public MyVcsActivator(final Set<String> old) {
      myOld = old;
    }

    public void activate(final Set<String> newOne, final AllVcsesI vcsesI) {
      final Set<String> toAdd = notInBottom(newOne, myOld);
      final Set<String> toRemove = notInBottom(myOld, newOne);
      if (toAdd != null) {
        for (String s : toAdd) {
          final AbstractVcs vcs = vcsesI.getByName(s);
          if (vcs != null) {
            try {
              vcs.doActivate();
            }
            catch (VcsException e) {
              // actually is not thrown (AbstractVcs#actualActivate())
            }
          } else {
            LOG.info("Error: activating non existing vcs");
          }
        }
      }
      if (toRemove != null) {
        for (String s : toRemove) {
          final AbstractVcs vcs = vcsesI.getByName(s);
          if (vcs != null) {
            try {
              vcs.doDeactivate();
            }
            catch (VcsException e) {
              // actually is not thrown (AbstractVcs#actualDeactivate())
            }
          } else {
            LOG.info("Error: removing non existing vcs");
          }
        }
      }
    }

    @Nullable
    private Set<String> notInBottom(final Set<String> top, final Set<String> bottom) {
      Set<String> notInBottom = null;
      for (String topItem : top) {
        // omit empty vcs: not a vcs
        if (topItem.trim().length() == 0) continue;

        if (! bottom.contains(topItem)) {
          if (notInBottom == null) {
            notInBottom = new HashSet<String>();
          }
          notInBottom.add(topItem);
        }
      }
      return notInBottom;
    }
  }

  public boolean haveActiveVcs(final String name) {
    synchronized (myLock) {
      return myVcsToPaths.containsKey(name);
    }
  }

  @Modification
  public void beingUnregistered(final String name) {
    synchronized (myLock) {
      keepActiveVcs(new Runnable() {
        public void run() {
          final List<VcsDirectoryMapping> removed = myVcsToPaths.remove(name);
          sortedMappingsByMap();
        }
      });
    }

    mappingsChanged();
  }

  private static class MySetMappingsPreProcessor {
    private List<VcsDirectoryMapping> myItems;
    private List<VcsDirectoryMapping> myItemsCopy;
    private Map<VcsDirectoryMapping, LocalFileSystem.WatchRequest> myRequests;

    public MySetMappingsPreProcessor(final List<VcsDirectoryMapping> items) {
      myItems = items;
    }

    public List<VcsDirectoryMapping> getItemsCopy() {
      return myItemsCopy;
    }

    public Map<VcsDirectoryMapping, LocalFileSystem.WatchRequest> getRequests() {
      return myRequests;
    }

    public void invoke() {
      if (myItems.isEmpty()) {
        myItemsCopy = Collections.singletonList(new VcsDirectoryMapping("", ""));
        myRequests = Collections.emptyMap();
      } else {
        myRequests = new HashMap<VcsDirectoryMapping, LocalFileSystem.WatchRequest>();

        for (VcsDirectoryMapping item : myItems) {
          final LocalFileSystem.WatchRequest request = addWatchRequest(item);
          if (request != null) {
            myRequests.put(item, request);
          }
        }
        myItemsCopy = myItems;
      }
    }
  }

  private @interface Modification {
  }
}
