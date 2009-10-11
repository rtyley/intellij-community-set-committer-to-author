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

/**
 * @author cdr
 */
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.roots.RootProvider;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;

import org.jetbrains.annotations.NotNull;

/**
 * used to override JdkHome location in order to provide correct paths
 */
public final class MockJdkWrapper implements Sdk {
  private final String myHomePath;
  private final Sdk myDelegate;

  public MockJdkWrapper(String homePath, @NotNull Sdk delegate) {
    myHomePath = homePath;
    myDelegate = delegate;
  }

  public VirtualFile getHomeDirectory() {
    return LocalFileSystem.getInstance().findFileByIoFile(new File(getHomePath()));
  }

  public String getHomePath() {
    final String homePath = FileUtil.toSystemDependentName(myHomePath == null ? myDelegate.getHomePath() : myHomePath);
    return StringUtil.trimEnd(homePath, File.separator);
  }

  public SdkType getSdkType() {
    return myDelegate.getSdkType();
  }

  public String getName() {
    return myDelegate.getName();
  }

  public String getVersionString() {
    return myDelegate.getVersionString();
  }

  public RootProvider getRootProvider() {
    return myDelegate.getRootProvider();
  }

  public Object clone() throws CloneNotSupportedException {
    throw new CloneNotSupportedException();
  }

  public SdkAdditionalData getSdkAdditionalData() {
    return null;
  }

  public SdkModificator getSdkModificator() {
    return null;
  }
}