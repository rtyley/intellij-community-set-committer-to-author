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
package com.intellij.openapi.vfs.impl.http;

import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.diagnostic.Logger;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Map;

/**
 * @author nik
 */
public class RemoteFileManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.impl.http.RemoteFileManager");
  private final LocalFileStorage myStorage;
  private final HttpFileSystemImpl myHttpFileSystem;
  private final Map<Trinity<Boolean, RemoteContentProvider, String>, VirtualFileImpl> myRemoteFiles;

  public RemoteFileManager(final HttpFileSystemImpl httpFileSystem) {
    myHttpFileSystem = httpFileSystem;
    myStorage = new LocalFileStorage();
    myRemoteFiles = new THashMap<Trinity<Boolean, RemoteContentProvider, String>, VirtualFileImpl>();
  }


  public synchronized VirtualFileImpl getOrCreateFile(final @NotNull String url, final @NotNull String path, final boolean directory,
                                                      final @NotNull RemoteContentProvider provider) throws IOException {
    Trinity<Boolean, RemoteContentProvider, String> key = Trinity.create(directory, provider, url);
    VirtualFileImpl file = myRemoteFiles.get(key);

    if (file == null) {
      if (!directory) {
        RemoteFileInfo fileInfo = new RemoteFileInfo(url, this, provider);
        file = new VirtualFileImpl(myHttpFileSystem, path, fileInfo);
        fileInfo.addDownloadingListener(new MyDownloadingListener(myHttpFileSystem, file));
      }
      else {
        file = new VirtualFileImpl(myHttpFileSystem, path, null);
      }
      myRemoteFiles.put(key, file);
    }
    return file;
  }

  public LocalFileStorage getStorage() {
    return myStorage;
  }


  private static class MyDownloadingListener extends FileDownloadingAdapter {
    private final HttpFileSystemImpl myHttpFileSystem;
    private final VirtualFileImpl myFile;

    public MyDownloadingListener(final HttpFileSystemImpl httpFileSystem, final VirtualFileImpl file) {
      myHttpFileSystem = httpFileSystem;
      myFile = file;
    }

    public void fileDownloaded(final VirtualFile localFile) {
      myHttpFileSystem.fireFileDownloaded(myFile);
    }
  }
}
