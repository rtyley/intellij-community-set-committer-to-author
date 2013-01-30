/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package hg4idea.test;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgRepositoryManager;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Nadya Zabrodina
 */
public class HgTestRepositoryManager implements HgRepositoryManager {

  private final List<VirtualFile> myRepositories = new ArrayList<VirtualFile>();

  public void add(VirtualFile repository) {
    myRepositories.add(repository);
  }

  @NotNull
  @Override
  public List<VirtualFile> getRepositories() {
    return myRepositories;
  }
}
