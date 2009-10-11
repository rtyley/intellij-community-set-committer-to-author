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

package com.intellij.history.core.storage;

import com.intellij.history.core.LocalVcs;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;

import java.io.*;
import java.util.List;

public class Storage {
  private static final Logger LOG = Logger.getInstance("#" + Storage.class.getName());

  private static final int VERSION = 19;
  private static final String BROKEN_MARK_FILE = ".broken";
  private static final String VERSION_FILE = "version";
  private static final String STORAGE_FILE = "storage";
  private static final String CONTENTS_FILE = "contents";

  private final File myDir;
  private IContentStorage myContentStorage;

  private boolean isBroken = false;

  public Storage(File dir) {
    myDir = dir;
    initStorage();
  }

  protected void initStorage() {
    initContentStorage();
    validate();
  }

  private void validate() {
    if (wasMarkedAsBroken() || !isValidVersion()) {
      deleteStorage();
      recreateStorage();
    }
  }

  private void deleteStorage() {
    close();
    FileUtil.delete(myDir);
  }

  private void recreateStorage() {
    myDir.mkdirs();
    initContentStorage();
    storeVersion();
  }

  private boolean wasMarkedAsBroken() {
    return new File(myDir, BROKEN_MARK_FILE).exists();
  }

  private boolean isValidVersion() {
    int storageVersion = load(VERSION_FILE, -1, new Loader<Integer>() {
      public Integer load(Stream s) throws IOException {
        return s.readInteger();
      }
    });

    int contentVersion = myContentStorage.getVersion();

    return storageVersion == getVersion() && contentVersion == getVersion();
  }

  private void storeVersion() {
    store(VERSION_FILE, new Storer() {
      public void store(Stream s) throws IOException {
        s.writeInteger(getVersion());
      }
    });

    myContentStorage.setVersion(getVersion());
  }

  private void initContentStorage() {
    try {
      myContentStorage = createContentStorage();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected IContentStorage createContentStorage() throws IOException {
    return ContentStorage.createContentStorage(new File(myDir, CONTENTS_FILE));
  }

  protected IContentStorage getContentStorage() {
    /// for StorageChecker only
    return myContentStorage;
  }

  public LocalVcs.Memento load() {
    return load(STORAGE_FILE, new LocalVcs.Memento(), new Loader<LocalVcs.Memento>() {
      public LocalVcs.Memento load(Stream s) throws IOException {
        LocalVcs.Memento m = new LocalVcs.Memento();
        m.myRoot = s.readEntry();
        m.myEntryCounter = s.readInteger();
        m.myChangeList = s.readChangeList();
        return m;
      }
    });
  }

  public void saveState(final LocalVcs.Memento m) {
    store(STORAGE_FILE, new Storer() {
      public void store(Stream s) throws IOException {
        s.writeEntry(m.myRoot);
        s.writeInteger(m.myEntryCounter);
        s.writeChangeList(m.myChangeList);
      }
    });
  }

  protected int getVersion() {
    return VERSION;
  }

  public void saveContents() {
    myContentStorage.save();
  }

  public void close() {
    myContentStorage.close();
  }

  private <T> T load(String fileName, T def, Loader<T> loader) {
    File f = new File(myDir, fileName);
    if (!f.exists()) return def;

    try {
      InputStream fs = new BufferedInputStream(new FileInputStream(f));
      try {
        return loader.load(new Stream(fs, this));
      }
      finally {
        fs.close();
      }
    }
    catch (IOException e) {
      deleteStorage();
      initStorage();
      return def;
    }
  }

  private void store(String fileName, Storer storer) {
    File f = new File(myDir, fileName);
    try {
      FileUtil.createParentDirs(f);
      OutputStream fs = new BufferedOutputStream(new FileOutputStream(f));
      try {
        storer.store(new Stream(fs));
      }
      finally {
        fs.close();
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public Content storeContent(byte[] bytes) {
    if (isBroken) return new UnavailableContent();

    try {
      int id = myContentStorage.store(bytes);
      return new StoredContent(this, id);
    }
    catch (BrokenStorageException e) {
      markAsBroken(e);
      return new UnavailableContent();
    }
  }

  protected byte[] loadContentData(int id) throws BrokenStorageException {
    if (isBroken) throw new BrokenStorageException();
    try {
      return myContentStorage.load(id);
    }
    catch (BrokenStorageException e) {
      markAsBroken(e);
      throw e;
    }
  }

  private void markAsBroken(BrokenStorageException cause) {
    LOG.warn("Local History storage is broken. It will be rebuilt on project reopen.", cause);
    isBroken = true;
    FileUtil.createIfDoesntExist(new File(myDir, BROKEN_MARK_FILE));
  }

  public void purgeContents(List<Content> contents) {
    for (Content c : contents) c.purge();
  }

  protected void purgeContent(StoredContent c) {
    myContentStorage.remove(c.getId());
  }

  private static interface Loader<T> {
    T load(Stream s) throws IOException;
  }

  private static interface Storer {
    void store(Stream s) throws IOException;
  }
}
