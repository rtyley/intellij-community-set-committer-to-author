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
package com.intellij.openapi.util;


import com.intellij.util.concurrency.AtomicFieldUpdater;
import com.intellij.util.keyFMap.KeyFMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public class UserDataHolderBase implements UserDataHolderEx, Cloneable {
  public static final Key<KeyFMap> COPYABLE_USER_MAP_KEY = Key.create("COPYABLE_USER_MAP_KEY");

  /**
   * Concurrent writes to this field are via CASes only, using the {@link #updater}
   */
  @NotNull private volatile KeyFMap myUserMap = KeyFMap.EMPTY_MAP;

  @Override
  protected Object clone() {
    try {
      UserDataHolderBase clone = (UserDataHolderBase)super.clone();
      clone.myUserMap = KeyFMap.EMPTY_MAP;
      copyCopyableDataTo(clone);
      return clone;
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  @TestOnly
  public String getUserDataString() {
    final KeyFMap userMap = myUserMap;
    final KeyFMap copyableMap = getUserData(COPYABLE_USER_MAP_KEY);
    return userMap.toString() + (copyableMap == null ? "" : copyableMap.toString());
  }

  public void copyUserDataTo(UserDataHolderBase other) {
    other.myUserMap = myUserMap;
  }

  @Override
  public <T> T getUserData(@NotNull Key<T> key) {
    //noinspection unchecked
    return myUserMap.get(key);
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    while (true) {
      KeyFMap map = myUserMap;
      KeyFMap newMap = value == null ? map.minus(key) : map.plus(key, value);
      if (newMap == map || updater.compareAndSet(this, map, newMap)) {
        break;
      }
    }
  }

  public <T> T getCopyableUserData(Key<T> key) {
    KeyFMap map = getUserData(COPYABLE_USER_MAP_KEY);
    //noinspection unchecked,ConstantConditions
    return map == null ? null : map.get(key);
  }

  public <T> void putCopyableUserData(Key<T> key, T value) {
    while (true) {
      KeyFMap map = myUserMap;
      KeyFMap copyableMap = map.get(COPYABLE_USER_MAP_KEY);
      if (copyableMap == null) {
        copyableMap = KeyFMap.EMPTY_MAP;
      }
      KeyFMap newCopyableMap = value == null ? copyableMap.minus(key) : copyableMap.plus(key, value);
      KeyFMap newMap = newCopyableMap.isEmpty() ? map.minus(COPYABLE_USER_MAP_KEY) : map.plus(COPYABLE_USER_MAP_KEY, newCopyableMap);
      if (newMap == map || updater.compareAndSet(this, map, newMap)) {
        return;
      }
    }
  }

  @Override
  public <T> boolean replace(@NotNull Key<T> key, @Nullable T oldValue, @Nullable T newValue) {
    while (true) {
      KeyFMap map = myUserMap;
      if (map.get(key) != oldValue) {
        return false;
      }
      KeyFMap newMap = newValue == null ? map.minus(key) : map.plus(key, newValue);
      if (newMap == map || updater.compareAndSet(this, map, newMap)) {
        return true;
      }
    }
  }
                                                            
  @Override
  @NotNull
  public <T> T putUserDataIfAbsent(@NotNull final Key<T> key, @NotNull final T value) {
    while (true) {
      KeyFMap map = myUserMap;
      T oldValue = map.get(key);
      if (oldValue != null) {
        return oldValue;
      }
      KeyFMap newMap = map.plus(key, value);
      if (newMap == map || updater.compareAndSet(this, map, newMap)) {
        return value;
      }
    }
  }

  public void copyCopyableDataTo(@NotNull UserDataHolderBase clone) {
    clone.putUserData(COPYABLE_USER_MAP_KEY, getUserData(COPYABLE_USER_MAP_KEY));
  }

  protected void clearUserData() {
    myUserMap = KeyFMap.EMPTY_MAP;
  }

  public boolean isUserDataEmpty() {
    return myUserMap.isEmpty();
  }

  private static final AtomicFieldUpdater<UserDataHolderBase, KeyFMap> updater = AtomicFieldUpdater.forFieldOfType(UserDataHolderBase.class, KeyFMap.class);
}
