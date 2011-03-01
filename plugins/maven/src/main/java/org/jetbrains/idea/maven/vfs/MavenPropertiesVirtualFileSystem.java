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
package org.jetbrains.idea.maven.vfs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.dummy.DummyFileSystem;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.Map;

public class MavenPropertiesVirtualFileSystem extends DummyFileSystem implements ApplicationComponent {
  @NonNls public static final String PROTOCOL = "maven-properties";

  @NonNls public static final String SYSTEM_PROPERTIES_FILE = "System.properties";
  @NonNls public static final String ENV_PROPERTIES_FILE = "Environment.properties";

  public static final String[] PROPERTIES_FILES = new String[]{SYSTEM_PROPERTIES_FILE, ENV_PROPERTIES_FILE};

  private final Map<String, VirtualFile> myFiles = new THashMap<String, VirtualFile>();

  public static MavenPropertiesVirtualFileSystem getInstance() {
    return ApplicationManager.getApplication().getComponent(MavenPropertiesVirtualFileSystem.class);
  }

  @NotNull
  public String getComponentName() {
    return MavenPropertiesVirtualFileSystem.class.getName();
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  @NotNull
  public String getProtocol() {
    return PROTOCOL;
  }

  //@Override
  //public boolean isPhysical() {
  //  return false;
  //}

  public synchronized VirtualFile findFileByPath(@NotNull @NonNls String path) {
    VirtualFile result = myFiles.get(path);
    if (result != null) return result;

    result = createFile(path);
    if (result != null) {
      myFiles.put(path, result);
    }
    return result;
  }

  @Nullable
  private VirtualFile createFile(String path) {
    if (SYSTEM_PROPERTIES_FILE.equals(path)) return new MavenPropertiesVirtualFile(path, MavenUtil.getSystemProperties(), this);
    if (ENV_PROPERTIES_FILE.equals(path)) return new MavenPropertiesVirtualFile(path, MavenUtil.getEnvProperties(), this);
    return null;
  }


  public void refresh(boolean asynchronous) {
  }

  public VirtualFile refreshAndFindFileByPath(@NotNull String path) {
    return findFileByPath(path);
  }

  //protected void deleteFile(Object requestor, VirtualFile vFile) throws IOException {
  //  throw new UnsupportedOperationException();
  //}
  //
  //protected void moveFile(Object requestor, VirtualFile vFile, VirtualFile newParent) throws IOException {
  //  throw new UnsupportedOperationException();
  //}
  //
  //protected void renameFile(Object requestor, VirtualFile vFile, String newName) throws IOException {
  //  throw new UnsupportedOperationException();
  //}
  //
  //protected VirtualFile createChildFile(Object requestor, VirtualFile vDir, String fileName) throws IOException {
  //  throw new UnsupportedOperationException();
  //}
  //
  //protected VirtualFile createChildDirectory(Object requestor, VirtualFile vDir, String dirName) throws IOException {
  //  throw new UnsupportedOperationException();
  //}
  //
  //protected VirtualFile copyFile(Object requestor, VirtualFile virtualFile, VirtualFile newParent, String copyName)
  //  throws IOException {
  //  throw new UnsupportedOperationException();
  //}
}
