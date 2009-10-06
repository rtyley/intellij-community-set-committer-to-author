package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Set;

public interface IProjectStore extends IComponentStore {


  boolean checkVersion();

  void setProjectFilePath(final String filePath);

  @Nullable
  VirtualFile getProjectBaseDir();


  void setStorageFormat(StorageFormat storageFormat);

  String getLocation();

  @NotNull
  String getProjectName();

  TrackingPathMacroSubstitutor[] getSubstitutors();

  @NotNull
  StorageScheme getStorageScheme();

  @Nullable
  String getPresentableUrl();

  boolean reload(final Set<Pair<VirtualFile,StateStorage>> changedFiles) throws StateStorage.StateStorageException, IOException;

  enum StorageFormat {
    FILE_BASED, DIRECTORY_BASED
  }

  //------ This methods should be got rid of
  void loadProject() throws IOException, JDOMException, InvalidDataException, StateStorage.StateStorageException;

  @Nullable
  VirtualFile getProjectFile();

  @Nullable
  VirtualFile getWorkspaceFile();

  void loadProjectFromTemplate(ProjectImpl project);

  @NotNull
  String getProjectFileName();

  @NotNull
  String getProjectFilePath();
}
