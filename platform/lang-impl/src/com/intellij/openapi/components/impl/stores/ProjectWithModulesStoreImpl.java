package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.ModuleImpl;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.fs.IFile;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

/**
 * @author yole
 */
public class ProjectWithModulesStoreImpl extends ProjectStoreImpl {
  public ProjectWithModulesStoreImpl(final ProjectEx project) {
    super(project);
  }

  public void reinitComponents(final Set<String> componentNames, final boolean reloadData) {
    super.reinitComponents(componentNames, reloadData);

    for (Module module : getPersistentModules()) {
      ((ModuleImpl)module).getStateStore().reinitComponents(componentNames, reloadData);
    }
  }

  public TrackingPathMacroSubstitutor[] getSubstitutors() {
    final List<TrackingPathMacroSubstitutor> result = new ArrayList<TrackingPathMacroSubstitutor>();
    result.add(getStateStorageManager().getMacroSubstitutor());

    for (Module module : getPersistentModules()) {
      result.add(((ModuleImpl)module).getStateStore().getStateStorageManager().getMacroSubstitutor());
    }

    return result.toArray(new TrackingPathMacroSubstitutor[result.size()]);
  }

  public boolean isReloadPossible(final Set<String> componentNames) {
    if (!super.isReloadPossible(componentNames)) return false;

    for (Module module : getPersistentModules()) {
      if (!((ModuleImpl)module).getStateStore().isReloadPossible(componentNames)) return false;
    }

    return true;
  }

  protected Module[] getPersistentModules() {
    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    if (moduleManager == null) return Module.EMPTY_ARRAY;

    return moduleManager.getModules();
  }

  protected SaveSessionImpl createSaveSession() throws StateStorage.StateStorageException {
    return new ProjectWithModulesSaveSession();
  }

  private class ProjectWithModulesSaveSession extends ProjectSaveSession {
    List<SaveSession> myModuleSaveSessions = new ArrayList<SaveSession>();

    public ProjectWithModulesSaveSession() throws StateStorage.StateStorageException {
      try {
        for (Module module : getPersistentModules()) {
          myModuleSaveSessions.add(((ModuleImpl)module).getStateStore().startSave());
        }
      }
      catch (IOException e) {
        throw new StateStorage.StateStorageException(e.getMessage());
      }
    }

    public List<IFile> getAllStorageFiles(final boolean includingSubStructures) {
      final List<IFile> result = super.getAllStorageFiles(includingSubStructures);

      if (includingSubStructures) {
        for (SaveSession moduleSaveSession : myModuleSaveSessions) {
          result.addAll(moduleSaveSession.getAllStorageFiles(true));
        }
      }

      return result;
    }

    @Nullable
    public Set<String> analyzeExternalChanges(final Set<Pair<VirtualFile,StateStorage>> changedFiles) {
      final Set<String> result = super.analyzeExternalChanges(changedFiles);
      if (result == null) return null;

      for (SaveSession moduleSaveSession : myModuleSaveSessions) {
        final Set<String> s = moduleSaveSession.analyzeExternalChanges(changedFiles);
        if (s == null) return null;
        result.addAll(s);
      }

      return result;
    }

    public void finishSave() {
      try {
        Throwable first = null;
        for (SaveSession moduleSaveSession : myModuleSaveSessions) {
          try {
            moduleSaveSession.finishSave();
          }
          catch(Throwable e) {
            if (first == null) first = e;
          }
        }

        if (first != null) {
          throw new RuntimeException(first);
        }
      }
      finally {
        super.finishSave();
      }
    }

    public void reset() {
      try {
        for (SaveSession moduleSaveSession : myModuleSaveSessions) {
          moduleSaveSession.reset();
        }
      }
      finally {
        super.reset();
      }
    }

    protected void beforeSave() throws IOException {
      super.beforeSave();
      for (SaveSession moduleSaveSession : myModuleSaveSessions) {
        moduleSaveSession.save();
      }
    }

    protected void collectSubfilesToSave(final List<IFile> result) throws IOException {
      for (SaveSession moduleSaveSession : myModuleSaveSessions) {
        final List<IFile> moduleFiles = moduleSaveSession.getAllStorageFilesToSave(true);
        result.addAll(moduleFiles);
      }
    }
  }
}
